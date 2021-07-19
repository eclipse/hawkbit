/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.push;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.im.authentication.UserPrincipal;
import org.eclipse.hawkbit.repository.event.entity.EntityIdEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutUpdatedEvent;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayloadIdentifier;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.push.event.ActionChangedEvent;
import org.eclipse.hawkbit.ui.push.event.ParentIdAwareEvent;
import org.eclipse.hawkbit.ui.push.event.RolloutChangedEvent;
import org.eclipse.hawkbit.ui.push.event.RolloutGroupChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import com.vaadin.server.VaadinSession;
import com.vaadin.server.VaadinSession.State;
import com.vaadin.server.WrappedSession;
import com.vaadin.ui.UI;

/**
 * An {@link EventPushStrategy} implementation which retrieves events from
 * Spring internal application events bus and stores them first in a queue to be
 * dispatched every x (default is 2 and can be configured with the property)
 * seconds to the {@link EventBus} in a Vaadin access thread
 * {@link UI#access(Runnable)}.
 *
 * This strategy avoids blocking UIs when too many events are fired and
 * dispatched to the UI thread. The UI will freeze in the time. To avoid that
 * all events are collected first and same events are merged to a list of events
 * before they dispatched to the UI thread.
 *
 * The strategy also verifies the current tenant in the session with the tenant
 * in the event and only forwards event from the right tenant to the UI.
 *
 */
public class DelayedEventBusPushStrategy
        implements EventPushStrategy, ApplicationListener<ApplicationEvent>, Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(DelayedEventBusPushStrategy.class);

    private static final int BLOCK_SIZE = 10_000;
    private final transient BlockingDeque<EntityIdEvent> queue = new LinkedBlockingDeque<>(BLOCK_SIZE);

    private final transient ScheduledExecutorService executorService;
    private final transient UIEventBus eventBus;
    private final transient UIEventProvider eventProvider;
    private final transient UIEventPermissionChecker eventPermissionChecker;
    private final long delay;

    private transient ScheduledFuture<?> jobHandle;
    private UI vaadinUI;

    /**
     * Constructor.
     * 
     * @param executorService
     *            the general scheduler service
     * @param eventBus
     *            the ui event bus
     * @param eventProvider
     *            the event provider
     * @param eventPermissionChecker
     *            the event permission checker
     * @param delay
     *            the delay for the event forwarding. Every delay millisecond
     *            the events are forwarded by this strategy
     */
    public DelayedEventBusPushStrategy(final ScheduledExecutorService executorService, final UIEventBus eventBus,
            final UIEventProvider eventProvider, final UIEventPermissionChecker eventPermissionChecker,
            final long delay) {
        this.executorService = executorService;
        this.eventBus = eventBus;
        this.eventProvider = eventProvider;
        this.eventPermissionChecker = eventPermissionChecker;
        this.delay = delay;
    }

    @Override
    public void init(final UI vaadinUI) {
        this.vaadinUI = vaadinUI;
        LOG.debug("Initialize delayed event push strategy for UI {}", vaadinUI.getUIId());
        if (vaadinUI.getSession() == null) {
            LOG.error("Vaadin session of UI {} is null! Event push disabled!", vaadinUI.getUIId());
            return;
        }

        jobHandle = executorService.scheduleWithFixedDelay(new DispatchRunnable(vaadinUI, vaadinUI.getSession()),
                10_000, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void clean() {
        LOG.debug("Cleanup delayed event push strategy for UI {}", vaadinUI.getUIId());
        jobHandle.cancel(true);
        queue.clear();

        jobHandle = null;
        vaadinUI = null;
    }

    private final class DispatchRunnable implements Runnable {

        private final UI vaadinUI;
        private final VaadinSession vaadinSession;

        private DispatchRunnable(final UI ui, final VaadinSession session) {
            vaadinUI = ui;
            vaadinSession = session;
        }

        @Override
        public void run() {
            LOG.debug("UI EventBus aggregator started for UI {}", vaadinUI.getUIId());
            final long timestamp = System.currentTimeMillis();

            final int size = queue.size();
            if (size <= 0) {
                LOG.debug("UI EventBus aggregator for UI {} has nothing to do.", vaadinUI.getUIId());
                return;
            }

            final WrappedSession wrappedSession = vaadinSession.getSession();
            if (wrappedSession == null) {
                return;
            }

            final List<EntityIdEvent> events = new ArrayList<>(size);
            final int eventsSize = queue.drainTo(events);

            if (events.isEmpty()) {
                LOG.debug("UI EventBus aggregator for UI {} has nothing to do.", vaadinUI.getUIId());
                return;
            }

            LOG.debug("UI EventBus aggregator dispatches {} events for session {} for UI {}", eventsSize, vaadinSession,
                    vaadinUI.getUIId());

            doDispatch(events, wrappedSession);

            LOG.debug("UI EventBus aggregator done with sending {} events in {} ms for UI {}", eventsSize,
                    System.currentTimeMillis() - timestamp, vaadinUI.getUIId());

        }

        private void doDispatch(final List<EntityIdEvent> events, final WrappedSession wrappedSession) {
            final SecurityContext userContext = (SecurityContext) wrappedSession
                    .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            final SecurityContext oldContext = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(userContext);

                final List<EntityModifiedEventPayload> groupedEventPayloads = groupEvents(events, userContext);
                if (CollectionUtils.isEmpty(groupedEventPayloads)) {
                    return;
                }

                vaadinUI.access(() -> {
                    if (vaadinSession.getState() != State.OPEN) {
                        return;
                    }
                    LOG.debug("UI EventBus aggregator of UI {} got lock on session.", vaadinUI.getUIId());
                    groupedEventPayloads.forEach(eventPayload -> eventBus.publish(EventTopics.REMOTE_EVENT_RECEIVED,
                            vaadinUI, eventPayload));
                    LOG.debug("UI EventBus aggregator of UI {} left lock on session.", vaadinUI.getUIId());
                }).get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Wait for Vaadin session for UI {} interrupted!", vaadinUI.getUIId(), e);
                Thread.currentThread().interrupt();
            } finally {
                SecurityContextHolder.setContext(oldContext);
            }
        }

        private List<EntityModifiedEventPayload> groupEvents(final List<EntityIdEvent> events,
                final SecurityContext userContext) {
            return events.stream().filter(event -> eventTenantCheck(userContext, event))
                    .collect(Collectors.groupingBy(EntityIdEvent::getClass)).entrySet().stream()
                    .filter(entry -> eventPermissionChecker.isEventAllowed(entry.getKey())).flatMap(entry -> {
                        final EntityModifiedEventPayloadIdentifier eventPayloadIdentifier = eventProvider.getEvents()
                                .get(entry.getKey());

                        if (ParentIdAwareEvent.class.isAssignableFrom(entry.getKey())) {
                            return mapToEntityModifiedEventPayload(eventPayloadIdentifier,
                                    getParentAwareEventIds(entry.getValue()));

                        }

                        return mapToEntityModifiedEventPayload(eventPayloadIdentifier, getEventIds(entry.getValue()));
                    }).collect(Collectors.toList());
        }

        /**
         * Checks if the tenant within the event is equal with the current
         * tenant in the context.
         *
         * @param userContext
         *            the security context of the current session
         * @param event
         *            the event to dispatch to the UI
         * @return {@code true} if the event can be dispatched to the UI
         *         otherwise {@code false}
         */
        private boolean eventTenantCheck(final SecurityContext userContext, final EntityIdEvent event) {
            if (userContext == null || userContext.getAuthentication() == null) {
                return false;
            }

            final Authentication currentAuthentication = userContext.getAuthentication();
            final String eventTenant = event.getTenant();

            final Object tenantAuthenticationDetails = currentAuthentication.getDetails();
            if (tenantAuthenticationDetails instanceof TenantAwareAuthenticationDetails) {
                return ((TenantAwareAuthenticationDetails) tenantAuthenticationDetails).getTenant()
                        .equalsIgnoreCase(eventTenant);
            }

            final Object userPrincipalDetails = currentAuthentication.getPrincipal();
            if (userPrincipalDetails instanceof UserPrincipal) {
                return ((UserPrincipal) userPrincipalDetails).getTenant().equalsIgnoreCase(eventTenant);
            }

            return false;
        }

        private Map<Long, List<Long>> getParentAwareEventIds(final List<EntityIdEvent> events) {
            return events.stream().filter(event -> event instanceof ParentIdAwareEvent)
                    .collect(Collectors.groupingBy(event -> ((ParentIdAwareEvent) event).getParentEntityId(),
                            Collectors.mapping(EntityIdEvent::getEntityId, Collectors.toList())));
        }

        private Stream<EntityModifiedEventPayload> mapToEntityModifiedEventPayload(
                final EntityModifiedEventPayloadIdentifier eventPayloadIdentifier,
                final Map<Long, List<Long>> parentAwareEntityIds) {
            return parentAwareEntityIds.entrySet().stream().map(parentAwareEntry -> EntityModifiedEventPayload
                    .of(eventPayloadIdentifier, parentAwareEntry.getKey(), parentAwareEntry.getValue()));
        }

        private List<Long> getEventIds(final List<EntityIdEvent> events) {
            return events.stream().map(EntityIdEvent::getEntityId).collect(Collectors.toList());
        }

        private Stream<EntityModifiedEventPayload> mapToEntityModifiedEventPayload(
                final EntityModifiedEventPayloadIdentifier eventPayloadIdentifier, final List<Long> entityIds) {
            return Stream.of(EntityModifiedEventPayload.of(eventPayloadIdentifier, entityIds));
        }
    }

    /**
     * An application event publisher subscriber which subscribes
     * {@link EntityIdEvent} from the repository to dispatch these events to the
     * UI {@link UIEventBus} .
     * 
     * @param applicationEvent
     *            the entity event which has been published from the repository
     */
    @Override
    public void onApplicationEvent(final ApplicationEvent applicationEvent) {
        if (!(applicationEvent instanceof EntityIdEvent)) {
            return;
        }

        final EntityIdEvent event = (EntityIdEvent) applicationEvent;

        collectRolloutEvent(event);
        collectActionUpdatedEvent(event);

        // filter out non-relevant UI events
        if (!isEventProvided(event)) {
            LOG.trace("Event is not supported in the UI!!! Dropped event is {}", event);
            return;
        }

        offerEvent(event);
    }

    private void collectRolloutEvent(final EntityIdEvent event) {
        Long rolloutId;
        Long rolloutGroupId = null;
        if (event instanceof ActionCreatedEvent) {
            rolloutId = ((ActionCreatedEvent) event).getRolloutId();
            rolloutGroupId = ((ActionCreatedEvent) event).getRolloutGroupId();
        } else if (event instanceof ActionUpdatedEvent) {
            rolloutId = ((ActionUpdatedEvent) event).getRolloutId();
            rolloutGroupId = ((ActionUpdatedEvent) event).getRolloutGroupId();
        } else if (event instanceof RolloutUpdatedEvent) {
            rolloutId = ((RolloutUpdatedEvent) event).getEntityId();
        } else if (event instanceof RolloutGroupCreatedEvent) {
            rolloutId = ((RolloutGroupCreatedEvent) event).getRolloutId();
            rolloutGroupId = ((RolloutGroupCreatedEvent) event).getEntityId();
        } else if (event instanceof RolloutGroupUpdatedEvent) {
            rolloutId = ((RolloutGroupUpdatedEvent) event).getRolloutId();
            rolloutGroupId = ((RolloutGroupUpdatedEvent) event).getEntityId();
        } else {
            return;
        }

        if (rolloutId != null) {
            offerEventIfNotContains(new RolloutChangedEvent(event.getTenant(), rolloutId));
        }

        if (rolloutGroupId != null) {
            offerEventIfNotContains(new RolloutGroupChangedEvent(event.getTenant(), rolloutId, rolloutGroupId));
        }
    }

    private void offerEventIfNotContains(final EntityIdEvent event) {
        if (queue.contains(event)) {
            return;
        }
        offerEvent(event);
    }

    private void offerEvent(final EntityIdEvent event) {
        if (!queue.offer(event)) {
            LOG.trace("Deque limit is reached, cannot add more events!!! Dropped event is {}", event);
        }
    }

    private void collectActionUpdatedEvent(final EntityIdEvent event) {
        if (event instanceof ActionUpdatedEvent) {
            final Long actionId = ((ActionUpdatedEvent) event).getEntityId();
            final Long targetId = ((ActionUpdatedEvent) event).getTargetId();
            offerEventIfNotContains(new ActionChangedEvent(event.getTenant(), targetId, actionId));
        }
    }

    private boolean isEventProvided(final EntityIdEvent event) {
        return eventProvider.getEvents().containsKey(event.getClass());
    }
}
