/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import static org.eclipse.hawkbit.repository.model.Action.ActionType.DOWNLOAD_ONLY;
import static org.eclipse.hawkbit.repository.model.Action.Status.DOWNLOADED;
import static org.eclipse.hawkbit.repository.model.Action.Status.FINISHED;
import static org.eclipse.hawkbit.repository.model.Target.CONTROLLER_ATTRIBUTE_KEY_SIZE;
import static org.eclipse.hawkbit.repository.model.Target.CONTROLLER_ATTRIBUTE_VALUE_SIZE;

import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.MaintenanceScheduleHelper;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RepositoryConstants;
import org.eclipse.hawkbit.repository.RepositoryProperties;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.UpdateMode;
import org.eclipse.hawkbit.repository.builder.ActionStatusCreate;
import org.eclipse.hawkbit.repository.event.remote.TargetAttributesRequestedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetPollEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.CancelTargetAssignmentEvent;
import org.eclipse.hawkbit.repository.exception.CancelActionNotAllowedException;
import org.eclipse.hawkbit.repository.exception.EntityAlreadyExistsException;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.InvalidTargetAttributeException;
import org.eclipse.hawkbit.repository.jpa.builder.JpaActionStatusCreate;
import org.eclipse.hawkbit.repository.jpa.configuration.Constants;
import org.eclipse.hawkbit.repository.jpa.executor.AfterTransactionCommitExecutor;
import org.eclipse.hawkbit.repository.jpa.model.JpaAction;
import org.eclipse.hawkbit.repository.jpa.model.JpaActionStatus;
import org.eclipse.hawkbit.repository.jpa.model.JpaActionStatus_;
import org.eclipse.hawkbit.repository.jpa.model.JpaAction_;
import org.eclipse.hawkbit.repository.jpa.model.JpaDistributionSet;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget_;
import org.eclipse.hawkbit.repository.jpa.rsql.RsqlMatcher;
import org.eclipse.hawkbit.repository.jpa.specifications.ActionSpecifications;
import org.eclipse.hawkbit.repository.jpa.utils.DeploymentHelper;
import org.eclipse.hawkbit.repository.jpa.utils.QuotaHelper;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.ActionStatus;
import org.eclipse.hawkbit.repository.model.DeploymentRequest;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.SoftwareModuleMetadata;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.model.helper.EventPublisherHolder;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * JPA based {@link ControllerManagement} implementation.
 *
 */
@Transactional(readOnly = true)
@Validated
public class JpaControllerManagement extends JpaActionManagement implements ControllerManagement {
    private static final Logger LOG = LoggerFactory.getLogger(JpaControllerManagement.class);

    private final BlockingDeque<TargetPoll> queue;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TargetRepository targetRepository;

    @Autowired
    private SoftwareModuleRepository softwareModuleRepository;

    @Autowired
    private ActionStatusRepository actionStatusRepository;

    @Autowired
    private QuotaManagement quotaManagement;

    @Autowired
    private TenantConfigurationManagement tenantConfigurationManagement;

    @Autowired
    private SystemSecurityContext systemSecurityContext;

    @Autowired
    private SystemManagement systemManagement;

    @Autowired
    private LockRegistry lockRegistry;

    @Autowired
    private EntityFactory entityFactory;

    @Autowired
    private EventPublisherHolder eventPublisherHolder;

    @Autowired
    private AfterTransactionCommitExecutor afterCommit;

    @Autowired
    private SoftwareModuleMetadataRepository softwareModuleMetadataRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private TenantAware tenantAware;

    @Autowired
    private TargetFilterQueryManagement targetFilterQueryManagement;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DeploymentManagement deploymentManagement;

    @Autowired
    private TargetFieldExtractor fieldExtractor;

    JpaControllerManagement(final ScheduledExecutorService executorService,
            final RepositoryProperties repositoryProperties, final ActionRepository actionRepository) {
        super(actionRepository, repositoryProperties);

        if (!repositoryProperties.isEagerPollPersistence()) {
            executorService.scheduleWithFixedDelay(this::flushUpdateQueue,
                    repositoryProperties.getPollPersistenceFlushTime(),
                    repositoryProperties.getPollPersistenceFlushTime(), TimeUnit.MILLISECONDS);

            queue = new LinkedBlockingDeque<>(repositoryProperties.getPollPersistenceQueueSize());
        } else {
            queue = null;
        }
    }

    @Override
    public String getPollingTime() {
        return systemSecurityContext.runAsSystem(() -> tenantConfigurationManagement
                .getConfigurationValue(TenantConfigurationKey.POLLING_TIME_INTERVAL, String.class).getValue());
    }

    /**
     * Returns the configured minimum polling interval.
     *
     * @return current {@link TenantConfigurationKey#MIN_POLLING_TIME_INTERVAL}.
     */
    @Override
    public String getMinPollingTime() {
        return systemSecurityContext.runAsSystem(() -> tenantConfigurationManagement
                .getConfigurationValue(TenantConfigurationKey.MIN_POLLING_TIME_INTERVAL, String.class).getValue());
    }

    /**
     * Returns the count to be used for reducing polling interval while calling
     * {@link ControllerManagement#getPollingTimeForAction(long)}.
     *
     * @return configured value of
     *         {@link TenantConfigurationKey#MAINTENANCE_WINDOW_POLL_COUNT}.
     */
    @Override
    public int getMaintenanceWindowPollCount() {
        return systemSecurityContext.runAsSystem(() -> tenantConfigurationManagement
                .getConfigurationValue(TenantConfigurationKey.MAINTENANCE_WINDOW_POLL_COUNT, Integer.class).getValue());
    }

    @Override
    public String getPollingTimeForAction(final long actionId) {

        final JpaAction action = getActionAndThrowExceptionIfNotFound(actionId);

        if (!action.hasMaintenanceSchedule() || action.isMaintenanceScheduleLapsed()) {
            return getPollingTime();
        }

        return new EventTimer(getPollingTime(), getMinPollingTime(), ChronoUnit.SECONDS)
                .timeToNextEvent(getMaintenanceWindowPollCount(), action.getMaintenanceWindowStartTime().orElse(null));
    }

    /**
     * EventTimer to handle reduction of polling interval based on maintenance
     * window start time. Class models the next polling time as an event to be
     * raised and time to next polling as a timer. The event, in this case the
     * polling, should happen when timer expires. Class makes use of java.time
     * package to manipulate and calculate timer duration.
     */
    private static class EventTimer {

        private final String defaultEventInterval;
        private final Duration defaultEventIntervalDuration;

        private final String minimumEventInterval;
        private final Duration minimumEventIntervalDuration;

        private final TemporalUnit timeUnit;

        /**
         * Constructor.
         *
         * @param defaultEventInterval
         *            default timer value to use for interval between events. This puts
         *            an upper bound for the timer value
         * @param minimumEventInterval
         *            for loading {@link DistributionSet#getModules()}. This puts a
         *            lower bound to the timer value
         * @param timeUnit
         *            representing the unit of time to be used for timer.
         */
        EventTimer(final String defaultEventInterval, final String minimumEventInterval, final TemporalUnit timeUnit) {
            this.defaultEventInterval = defaultEventInterval;
            this.defaultEventIntervalDuration = MaintenanceScheduleHelper.convertToISODuration(defaultEventInterval);

            this.minimumEventInterval = minimumEventInterval;
            this.minimumEventIntervalDuration = MaintenanceScheduleHelper.convertToISODuration(minimumEventInterval);

            this.timeUnit = timeUnit;
        }

        /**
         * This method calculates the time interval until the next event based on the
         * desired number of events before the time when interval is reset to default.
         * The return value is bounded by {@link EventTimer#defaultEventInterval} and
         * {@link EventTimer#minimumEventInterval}.
         *
         * @param eventCount
         *            number of events desired until the interval is reset to default.
         *            This is not guaranteed as the interval between events cannot be
         *            less than the minimum interval
         * @param timerResetTime
         *            time when exponential forwarding should reset to default
         *
         * @return String in HH:mm:ss format for time to next event.
         */
        String timeToNextEvent(final int eventCount, final ZonedDateTime timerResetTime) {
            final ZonedDateTime currentTime = ZonedDateTime.now();

            // If there is no reset time, or if we already past the reset time,
            // return the default interval.
            if (timerResetTime == null || currentTime.compareTo(timerResetTime) > 0) {
                return defaultEventInterval;
            }

            // Calculate the interval timer based on desired event count.
            final Duration currentIntervalDuration = Duration.of(currentTime.until(timerResetTime, timeUnit), timeUnit)
                    .dividedBy(eventCount);

            // Need not return interval greater than the default.
            if (currentIntervalDuration.compareTo(defaultEventIntervalDuration) > 0) {
                return defaultEventInterval;
            }

            // Should not return interval less than minimum.
            if (currentIntervalDuration.compareTo(minimumEventIntervalDuration) < 0) {
                return minimumEventInterval;
            }

            return String.format("%02d:%02d:%02d", currentIntervalDuration.toHours(),
                    currentIntervalDuration.toMinutes() % 60, currentIntervalDuration.getSeconds() % 60);
        }
    }

    @Override
    public Optional<Action> getActionForDownloadByTargetAndSoftwareModule(final String controllerId,
            final long moduleId) {
        throwExceptionIfTargetDoesNotExist(controllerId);
        throwExceptionIfSoftwareModuleDoesNotExist(moduleId);

        final List<Action> action = actionRepository.findActionByTargetAndSoftwareModule(controllerId, moduleId);

        if (action.isEmpty() || action.get(0).isCancelingOrCanceled()) {
            return Optional.empty();
        }

        return Optional.ofNullable(action.get(0));
    }

    private void throwExceptionIfTargetDoesNotExist(final String controllerId) {
        if (!targetRepository.existsByControllerId(controllerId)) {
            throw new EntityNotFoundException(Target.class, controllerId);
        }
    }

    private void throwExceptionIfTargetDoesNotExist(final Long targetId) {
        if (!targetRepository.existsById(targetId)) {
            throw new EntityNotFoundException(Target.class, targetId);
        }
    }

    private void throwExceptionIfSoftwareModuleDoesNotExist(final Long moduleId) {
        if (!softwareModuleRepository.existsById(moduleId)) {
            throw new EntityNotFoundException(SoftwareModule.class, moduleId);
        }
    }

    @Override
    public boolean hasTargetArtifactAssigned(final String controllerId, final String sha1Hash) {
        throwExceptionIfTargetDoesNotExist(controllerId);
        return actionRepository.count(ActionSpecifications.hasTargetAssignedArtifact(controllerId, sha1Hash)) > 0;
    }

    @Override
    public boolean hasTargetArtifactAssigned(final long targetId, final String sha1Hash) {
        throwExceptionIfTargetDoesNotExist(targetId);
        return actionRepository.count(ActionSpecifications.hasTargetAssignedArtifact(targetId, sha1Hash)) > 0;
    }

    @Override
    public Optional<Action> findActiveActionWithHighestWeight(final String controllerId) {
        return findActiveActionsWithHighestWeight(controllerId, 1).stream().findFirst();
    }

    @Override
    public List<Action> findActiveActionsWithHighestWeight(final String controllerId,
            final int maxActionCount) {
        return findActiveActionsWithHighestWeightConsideringDefault(controllerId, maxActionCount);
    }

    @Override
    public int getWeightConsideringDefault(final Action action) {
        return super.getWeightConsideringDefault(action);
    }

    @Override
    public Optional<Action> findActionWithDetails(final long actionId) {
        return actionRepository.getById(actionId);
    }

    @Override
    public List<Action> getActiveActionsByExternalRef(@NotNull final List<String> externalRefs) {
        return actionRepository.findByExternalRefInAndActive(externalRefs, true);
    }

    @Override
    public void deleteExistingTarget(@NotEmpty final String controllerId) {
        final Target target = targetRepository.findByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));
         targetRepository.deleteById(target.getId());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(include = ConcurrencyFailureException.class, exclude = EntityAlreadyExistsException.class, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Target findOrRegisterTargetIfItDoesNotExist(final String controllerId, final URI address) {
        return findOrRegisterTargetIfItDoesNotExist(controllerId, address, null);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(include = ConcurrencyFailureException.class, exclude = EntityAlreadyExistsException.class, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Target findOrRegisterTargetIfItDoesNotExist(final String controllerId, final URI address,
            final String name) {
        final Specification<JpaTarget> spec = (targetRoot, query, cb) -> cb
                .equal(targetRoot.get(JpaTarget_.controllerId), controllerId);

        return targetRepository.findOne(spec).map(target -> updateTarget(target, address, name))
                .orElseGet(() -> createTarget(controllerId, address, name));
    }

    private Target createTarget(final String controllerId, final URI address, String name) {

        final Target result = targetRepository.save((JpaTarget) entityFactory.target().create()
                .controllerId(controllerId).description("Plug and Play target: " + controllerId).name((StringUtils.hasText(name) ? name : controllerId))
                .status(TargetUpdateStatus.REGISTERED).lastTargetQuery(System.currentTimeMillis())
                .address(Optional.ofNullable(address).map(URI::toString).orElse(null)).build());

        afterCommit.afterCommit(() -> eventPublisherHolder.getEventPublisher()
                .publishEvent(new TargetPollEvent(result, eventPublisherHolder.getApplicationId())));

        return result;
    }

    /**
     * Flush the update queue by means to persisting
     * {@link Target#getLastTargetQuery()}.
     */
    private void flushUpdateQueue() {
        LOG.debug("Run flushUpdateQueue.");

        final int size = queue.size();
        if (size <= 0) {
            return;
        }

        LOG.debug("{} events in flushUpdateQueue.", size);

        final Set<TargetPoll> events = Sets.newHashSetWithExpectedSize(queue.size());
        final int drained = queue.drainTo(events);

        if (drained <= 0) {
            return;
        }

        try {
            events.stream().collect(Collectors.groupingBy(TargetPoll::getTenant)).forEach((tenant, polls) -> {
                final TransactionCallback<Void> createTransaction = status -> updateLastTargetQueries(tenant, polls);
                tenantAware.runAsTenant(tenant,
                        () -> DeploymentHelper.runInNewTransaction(txManager, "flushUpdateQueue", createTransaction));
            });
        } catch (final RuntimeException ex) {
            LOG.error("Failed to persist UpdateQueue content.", ex);
            return;
        }

        LOG.debug("{} events persisted.", drained);
    }

    private Void updateLastTargetQueries(final String tenant, final List<TargetPoll> polls) {
        LOG.debug("Persist {} targetqueries.", polls.size());

        final List<List<String>> pollChunks = Lists.partition(
                polls.stream().map(TargetPoll::getControllerId).collect(Collectors.toList()),
                Constants.MAX_ENTRIES_IN_STATEMENT);

        pollChunks.forEach(chunk -> {
            setLastTargetQuery(tenant, System.currentTimeMillis(), chunk);
            chunk.forEach(controllerId -> afterCommit.afterCommit(() -> eventPublisherHolder.getEventPublisher()
                    .publishEvent(new TargetPollEvent(controllerId, tenant, eventPublisherHolder.getApplicationId()))));
        });

        return null;
    }

    /**
     * Sets {@link Target#getLastTargetQuery()} by native SQL in order to avoid
     * raising opt lock revision as this update is not mission critical and in fact
     * only written by {@link ControllerManagement}, i.e. the target itself.
     */
    private void setLastTargetQuery(final String tenant, final long currentTimeMillis, final List<String> chunk) {
        final Map<String, String> paramMapping = Maps.newHashMapWithExpectedSize(chunk.size());

        for (int i = 0; i < chunk.size(); i++) {
            paramMapping.put("cid" + i, chunk.get(i));
        }

        final Query updateQuery = entityManager.createNativeQuery(
                "UPDATE sp_target SET last_target_query = #last_target_query WHERE controller_id IN ("
                        + formatQueryInStatementParams(paramMapping.keySet()) + ") AND tenant = #tenant");

        paramMapping.forEach(updateQuery::setParameter);
        updateQuery.setParameter("last_target_query", currentTimeMillis);
        updateQuery.setParameter("tenant", tenant);

        final int updated = updateQuery.executeUpdate();
        if (updated < chunk.size()) {
            LOG.error("Targets polls could not be applied completely ({} instead of {}).", updated, chunk.size());
        }
    }

    private static String formatQueryInStatementParams(final Collection<String> paramNames) {
        return "#" + String.join(",#", paramNames);
    }

    /**
     * Stores target directly to DB in case either {@link Target#getAddress()} or
     * {@link Target#getUpdateStatus()} or {@link Target#getName()} changes or the buffer queue is full.
     *
     */
    private Target updateTarget(final JpaTarget toUpdate, final URI address, final String name) {
        if (isStoreEager(toUpdate, address, name) || !queue.offer(new TargetPoll(toUpdate))) {
            if (isAddressChanged(toUpdate.getAddress(), address)) {
                toUpdate.setAddress(address.toString());
            }
            if (isNameChanged(toUpdate.getName(), name)) {
                toUpdate.setName(name);
            }
            if (isStatusUnknown(toUpdate.getUpdateStatus())) {
                toUpdate.setUpdateStatus(TargetUpdateStatus.REGISTERED);
            }
            toUpdate.setLastTargetQuery(System.currentTimeMillis());
            afterCommit.afterCommit(() -> eventPublisherHolder.getEventPublisher()
                    .publishEvent(new TargetPollEvent(toUpdate, eventPublisherHolder.getApplicationId())));
            return targetRepository.save(toUpdate);
        }
        return toUpdate;
    }
    private boolean isStoreEager(final JpaTarget toUpdate, final URI address, final String name) {
        return repositoryProperties.isEagerPollPersistence() || isAddressChanged(toUpdate.getAddress(), address)
                || isNameChanged(toUpdate.getName(), name) || isStatusUnknown(toUpdate.getUpdateStatus());
    }
    private boolean isAddressChanged(final URI addressToUpdate, final URI address) {
        return addressToUpdate == null || !addressToUpdate.equals(address);
    }
    private boolean isNameChanged(final String nameToUpdate, final String name) {
        return StringUtils.hasText(name) && !nameToUpdate.equals(name);
    }
    private boolean isStatusUnknown(final TargetUpdateStatus statusToUpdate) {
        return TargetUpdateStatus.UNKNOWN == statusToUpdate;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Action addCancelActionStatus(final ActionStatusCreate c) {
        final JpaActionStatusCreate create = (JpaActionStatusCreate) c;

        final JpaAction action = getActionAndThrowExceptionIfNotFound(create.getActionId());

        if (!action.isCancelingOrCanceled()) {
            throw new CancelActionNotAllowedException("The action is not in canceling state.");
        }

        final JpaActionStatus actionStatus = create.build();

        switch (actionStatus.getStatus()) {
        case CANCELED:
        case FINISHED:
            handleFinishedCancelation(actionStatus, action);
            break;
        case ERROR:
        case CANCEL_REJECTED:
            // Cancellation rejected. Back to running.
            action.setStatus(Status.RUNNING);
            break;
        default:
            // information status entry - check for a potential DOS attack
            assertActionStatusQuota(action);
            assertActionStatusMessageQuota(actionStatus);
            break;
        }

        actionStatus.setAction(actionRepository.save(action));
        actionStatusRepository.save(actionStatus);

        return action;
    }

    private void assertActionStatusMessageQuota(final JpaActionStatus actionStatus) {
        QuotaHelper.assertAssignmentQuota(actionStatus.getId(), actionStatus.getMessages().size(),
                quotaManagement.getMaxMessagesPerActionStatus(), "Message", ActionStatus.class.getSimpleName(), null);
    }

    private void handleFinishedCancelation(final JpaActionStatus actionStatus, final JpaAction action) {
        // in case of successful cancellation we also report the success at
        // the canceled action itself.
        actionStatus.addMessage(
                RepositoryConstants.SERVER_MESSAGE_PREFIX + "Cancellation completion is finished sucessfully.");
        DeploymentHelper.successCancellation(action, actionRepository, targetRepository);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Action addUpdateActionStatus(final ActionStatusCreate c) {
        final JpaActionStatusCreate create = (JpaActionStatusCreate) c;
        final JpaAction action = getActionAndThrowExceptionIfNotFound(create.getActionId());
        final JpaActionStatus actionStatus = create.build();

        if (isUpdatingActionStatusAllowed(action, actionStatus)) {
            return handleAddUpdateActionStatus(actionStatus, action);
        }

        LOG.debug("Update of actionStatus {} for action {} not possible since action not active anymore.",
                actionStatus.getStatus(), action.getId());
        return action;
    }

    /**
     * ActionStatus updates are allowed mainly if the action is active. If the
     * action is not active we accept further status updates if permitted so by
     * repository configuration. In this case, only the values: Status.ERROR and
     * Status.FINISHED are allowed. In the case of a DOWNLOAD_ONLY action, we accept
     * status updates only once.
     */
    private boolean isUpdatingActionStatusAllowed(final JpaAction action, final JpaActionStatus actionStatus) {

        final boolean isIntermediateFeedback = (FINISHED != actionStatus.getStatus())
                && (Status.ERROR != actionStatus.getStatus());

        final boolean isAllowedByRepositoryConfiguration = !repositoryProperties.isRejectActionStatusForClosedAction()
                && isIntermediateFeedback;

        final boolean isAllowedForDownloadOnlyActions = isDownloadOnly(action) && !isIntermediateFeedback;

        return action.isActive() || isAllowedByRepositoryConfiguration || isAllowedForDownloadOnlyActions;
    }

    private static boolean isDownloadOnly(final JpaAction action) {
        return DOWNLOAD_ONLY == action.getActionType();
    }

    /**
     * Sets {@link TargetUpdateStatus} based on given {@link ActionStatus}.
     */
    private Action handleAddUpdateActionStatus(final JpaActionStatus actionStatus, final JpaAction action) {

        String controllerId = null;
        LOG.debug("handleAddUpdateActionStatus for action {}", action.getId());

        // information status entry - check for a potential DOS attack
        assertActionStatusQuota(action);
        assertActionStatusMessageQuota(actionStatus);

        switch (actionStatus.getStatus()) {
        case ERROR:
            final JpaTarget target = (JpaTarget) action.getTarget();
            target.setUpdateStatus(TargetUpdateStatus.ERROR);
            handleErrorOnAction(action, target);
            break;
        case FINISHED:
            controllerId = handleFinishedAndStoreInTargetStatus(action);
            break;
        case DOWNLOADED:
            controllerId = handleDownloadedActionStatus(action);
            break;
        default:
            break;
        }

        actionStatus.setAction(action);
        actionStatusRepository.save(actionStatus);
        final Action savedAction = actionRepository.save(action);

        if (controllerId != null) {
            requestControllerAttributes(controllerId);
        }

        return savedAction;
    }

    private String handleDownloadedActionStatus(final JpaAction action) {
        if (!isDownloadOnly(action)) {
            return null;
        }

        final JpaTarget target = (JpaTarget) action.getTarget();
        action.setActive(false);
        action.setStatus(DOWNLOADED);
        target.setUpdateStatus(TargetUpdateStatus.IN_SYNC);
        targetRepository.save(target);

        return target.getControllerId();
    }

    private void requestControllerAttributes(final String controllerId) {
        final JpaTarget target = (JpaTarget) getByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));

        target.setRequestControllerAttributes(true);

        eventPublisherHolder.getEventPublisher()
                .publishEvent(new TargetAttributesRequestedEvent(tenantAware.getCurrentTenant(), target.getId(),
                        target.getControllerId(), target.getAddress() != null ? target.getAddress().toString() : null,
                        JpaTarget.class.getName(), eventPublisherHolder.getApplicationId()));
    }

    private void handleErrorOnAction(final JpaAction mergedAction, final JpaTarget mergedTarget) {
        mergedAction.setActive(false);
        mergedAction.setStatus(Status.ERROR);
        mergedTarget.setAssignedDistributionSet(null);

        targetRepository.save(mergedTarget);
    }

    private void assertActionStatusQuota(final JpaAction action) {
        QuotaHelper.assertAssignmentQuota(action.getId(), 1, quotaManagement.getMaxStatusEntriesPerAction(),
                ActionStatus.class, Action.class, actionStatusRepository::countByActionId);
    }

    private String handleFinishedAndStoreInTargetStatus(final JpaAction action) {
        final JpaTarget target = (JpaTarget) action.getTarget();
        action.setActive(false);
        action.setStatus(Status.FINISHED);
        final JpaDistributionSet ds = (JpaDistributionSet) entityManager.merge(action.getDistributionSet());

        target.setInstalledDistributionSet(ds);
        target.setInstallationDate(System.currentTimeMillis());

        // Target reported an installation of a DOWNLOAD_ONLY assignment, the
        // assigned DS has to be adapted
        // because the currently assigned DS can be unequal to the currently
        // installed DS (the downloadOnly DS)
        if (isDownloadOnly(action)) {
            target.setAssignedDistributionSet(action.getDistributionSet());
        }

        // check if the assigned set is equal to the installed set (not
        // necessarily the case as another update might be pending already).
        if (target.getAssignedDistributionSet() != null
                && target.getAssignedDistributionSet().getId().equals(target.getInstalledDistributionSet().getId())) {
            target.setUpdateStatus(TargetUpdateStatus.IN_SYNC);
        }

        targetRepository.save(target);
        entityManager.detach(ds);

        return target.getControllerId();
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Target updateControllerAttributes(final String controllerId, final Map<String, String> data,
            final UpdateMode mode) {

        /*
         * Constraints on attribute keys & values are not validated by EclipseLink.
         * Hence, they are validated here.
         */
        if (data.entrySet().stream().anyMatch(e -> !isAttributeEntryValid(e))) {
            throw new InvalidTargetAttributeException();
        }

        final JpaTarget target = (JpaTarget) targetRepository.findByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));

        // get the modifiable attribute map
        final Map<String, String> controllerAttributes = target.getControllerAttributes();
        final UpdateMode updateMode = mode != null ? mode : UpdateMode.MERGE;
        switch (updateMode) {
        case REMOVE:
            // remove the addressed attributes
            data.keySet().forEach(controllerAttributes::remove);
            break;
        case REPLACE:
            // clear the attributes before adding the new attributes
            controllerAttributes.clear();
            copy(data, controllerAttributes);
            target.setRequestControllerAttributes(false);
            break;
        case MERGE:
            // just merge the attributes in
            copy(data, controllerAttributes);
            target.setRequestControllerAttributes(false);
            break;
        default:
            // unknown update mode
            throw new IllegalStateException("The update mode " + updateMode + " is not supported.");
        }
        assertTargetAttributesQuota(target);

        return targetRepository.save(target);
    }


    @Override
    public void triggerDistributionSetAssignmentCheck(String controllerId){
        LOG.debug("Auto assign check with ID triggered...");
        systemSecurityContext.runAsSystem(() -> executeAutoAssignCheck(controllerId));
    }

    private Object executeAutoAssignCheck(String controllerId) {

        final int PAGE_SIZE = 1000;
        final PageRequest pageRequest = PageRequest.of(0, PAGE_SIZE);
        final Page<TargetFilterQuery> filterQueries = targetFilterQueryManagement.findWithAutoAssignDS(pageRequest);

        final Lock lock = lockRegistry.obtain("autoassign");
        if (!lock.tryLock()) {
            return null;
        }

        try {
            systemManagement.forEachTenant(tenant -> checkForAutoAssignDS(controllerId, filterQueries));
        } finally {
            lock.unlock();
        }

        return null;
    }

    private void checkForAutoAssignDS(String controllerId, Page<TargetFilterQuery> filterQueries){

        final JpaTarget target = (JpaTarget) targetRepository.findByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));

        TargetFieldData fieldData = fieldExtractor.extractData(target);

        TargetFilterQuery matchedQuery = null;

        for(TargetFilterQuery query: filterQueries){
            if(RsqlMatcher.matches(query.getQuery(), fieldData)){
                matchedQuery = query;
                break;
            }
        }

        if(matchedQuery == null) {
            return;
        }

        assignDsToTarget(target, matchedQuery);
    }

    private void assignDsToTarget(Target target, TargetFilterQuery query){
        final String actionMessage = String.format("Auto assignment by target filter: %s; Target ID: %s", query.getName(), target.getControllerId());

        DeploymentRequest deploymentRequest = createDeploymentRequest(query, target);

        DeploymentHelper.runInNewTransaction(transactionManager, "AutoAssignDSToTarget",
                Isolation.READ_COMMITTED.value(), status -> deploymentManagement.assignDistributionSet(target, deploymentRequest, actionMessage));
        LOG.debug(actionMessage);
    }

    private static DeploymentRequest createDeploymentRequest(TargetFilterQuery tfq, Target target){
        return DeploymentManagement.deploymentRequest(target.getControllerId(), tfq.getAutoAssignDistributionSet().getId())
                .setActionType(tfq.getAutoAssignActionType()).setWeight(tfq.getAutoAssignWeight().orElse(null)).build();
    }

    private static boolean isAttributeEntryValid(final Map.Entry<String, String> e) {
        return isAttributeKeyValid(e.getKey()) && isAttributeValueValid(e.getValue());
    }

    private static boolean isAttributeKeyValid(final String key) {
        return key != null && key.length() <= CONTROLLER_ATTRIBUTE_KEY_SIZE;
    }

    private static boolean isAttributeValueValid(final String value) {
        return value == null || value.length() <= CONTROLLER_ATTRIBUTE_VALUE_SIZE;
    }

    private static void copy(final Map<String, String> src, final Map<String, String> trg) {
        if (src == null || src.isEmpty()) {
            return;
        }
        src.forEach((key, value) -> {
            if (value != null) {
                trg.put(key, value);
            } else {
                trg.remove(key);
            }
        });
    }

    private void assertTargetAttributesQuota(final JpaTarget target) {
        final int limit = quotaManagement.getMaxAttributeEntriesPerTarget();
        QuotaHelper.assertAssignmentQuota(target.getId(), target.getControllerAttributes().size(), limit, "Attribute",
                Target.class.getSimpleName(), null);
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Action registerRetrieved(final long actionId, final String message) {
        return handleRegisterRetrieved(actionId, message);
    }

    /**
     * Registers retrieved status for given {@link Target} and {@link Action} if it
     * does not exist yet.
     *
     * @param actionId
     *            to the handle status for
     * @param message
     *            for the status
     * @return the updated action in case the status has been changed to
     *         {@link Status#RETRIEVED}
     */
    private Action handleRegisterRetrieved(final Long actionId, final String message) {
        final JpaAction action = getActionAndThrowExceptionIfNotFound(actionId);
        // do a manual query with CriteriaBuilder to avoid unnecessary field
        // queries and an extra
        // count query made by spring-data when using pageable requests, we
        // don't need an extra count
        // query, we just want to check if the last action status is a retrieved
        // or not.
        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<Object[]> queryActionStatus = cb.createQuery(Object[].class);
        final Root<JpaActionStatus> actionStatusRoot = queryActionStatus.from(JpaActionStatus.class);
        final CriteriaQuery<Object[]> query = queryActionStatus
                .multiselect(actionStatusRoot.get(JpaActionStatus_.id), actionStatusRoot.get(JpaActionStatus_.status))
                .where(cb.equal(actionStatusRoot.get(JpaActionStatus_.action).get(JpaAction_.id), actionId))
                .orderBy(cb.desc(actionStatusRoot.get(JpaActionStatus_.id)));
        final List<Object[]> resultList = entityManager.createQuery(query).setFirstResult(0).setMaxResults(1)
                .getResultList();

        // if the latest status is not in retrieve state then we add a retrieved
        // state again, we want
        // to document a deployment retrieved status and a cancel retrieved
        // status, but multiple
        // retrieves after the other we don't want to store to protect to
        // overflood action status in
        // case controller retrieves a action multiple times.
        if (resultList.isEmpty() || (Status.RETRIEVED != resultList.get(0)[1])) {
            // document that the status has been retrieved
            actionStatusRepository
                    .save(new JpaActionStatus(action, Status.RETRIEVED, System.currentTimeMillis(), message));

            // don't change the action status itself in case the action is in
            // canceling state otherwise
            // we modify the action status and the controller won't get the
            // cancel job anymore.
            if (!action.isCancelingOrCanceled()) {
                action.setStatus(Status.RETRIEVED);
                return actionRepository.save(action);
            }
        }
        return action;
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public ActionStatus addInformationalActionStatus(final ActionStatusCreate c) {
        final JpaActionStatusCreate create = (JpaActionStatusCreate) c;
        final JpaAction action = getActionAndThrowExceptionIfNotFound(create.getActionId());
        final JpaActionStatus statusMessage = create.build();
        statusMessage.setAction(action);

        assertActionStatusQuota(action);
        assertActionStatusMessageQuota(statusMessage);

        return actionStatusRepository.save(statusMessage);
    }

    private JpaAction getActionAndThrowExceptionIfNotFound(final Long actionId) {
        return actionRepository.findById(actionId)
                .orElseThrow(() -> new EntityNotFoundException(Action.class, actionId));
    }

    @Override
    public Optional<Target> getByControllerId(final String controllerId) {
        return targetRepository.findByControllerId(controllerId);
    }

    @Override
    public Optional<Target> get(final long targetId) {
        return targetRepository.findById(targetId).map(t -> (Target) t);
    }

    @Override
    public Page<ActionStatus> findActionStatusByAction(final Pageable pageReq, final long actionId) {
        if (!actionRepository.existsById(actionId)) {
            throw new EntityNotFoundException(Action.class, actionId);
        }

        return actionStatusRepository.findByActionId(pageReq, actionId);
    }

    @Override
    public List<String> getActionHistoryMessages(final long actionId, final int messageCount) {
        // Just return empty list in case messageCount is zero.
        if (messageCount == 0) {
            return Collections.emptyList();
        }

        // For negative and large value of messageCount, limit the number of
        // messages.
        final int limit = messageCount < 0 || messageCount >= RepositoryConstants.MAX_ACTION_HISTORY_MSG_COUNT
                ? RepositoryConstants.MAX_ACTION_HISTORY_MSG_COUNT
                : messageCount;

        final PageRequest pageable = PageRequest.of(0, limit, new Sort(Direction.DESC, "occurredAt"));
        final Page<String> messages = actionStatusRepository.findMessagesByActionIdAndMessageNotLike(pageable, actionId,
                RepositoryConstants.SERVER_MESSAGE_PREFIX + "%");

        LOG.debug("Retrieved {} message(s) from action history for action {}.", messages.getNumberOfElements(),
                actionId);

        return messages.getContent();
    }

    @Override
    public Optional<SoftwareModule> getSoftwareModule(final long id) {
        return softwareModuleRepository.findById(id).map(s -> (SoftwareModule) s);
    }

    @Override
    public Map<Long, List<SoftwareModuleMetadata>> findTargetVisibleMetaDataBySoftwareModuleId(
            final Collection<Long> moduleId) {

        return softwareModuleMetadataRepository
                .findBySoftwareModuleIdInAndTargetVisible(PageRequest.of(0, RepositoryConstants.MAX_META_DATA_COUNT),
                        moduleId, true)
                .getContent().stream().collect(Collectors.groupingBy(o -> (Long) o[0],
                        Collectors.mapping(o -> (SoftwareModuleMetadata) o[1], Collectors.toList())));
    }

    private static class TargetPoll {

        private final String tenant;
        private final String controllerId;

        TargetPoll(final Target target) {
            this.tenant = target.getTenant();
            this.controllerId = target.getControllerId();
        }

        public String getTenant() {
            return tenant;
        }

        public String getControllerId() {
            return controllerId;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (controllerId == null ? 0 : controllerId.hashCode());
            result = prime * result + (tenant == null ? 0 : tenant.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TargetPoll other = (TargetPoll) obj;
            if (controllerId == null) {
                if (other.controllerId != null) {
                    return false;
                }
            } else if (!controllerId.equals(other.controllerId)) {
                return false;
            }
            if (tenant == null) {
                if (other.tenant != null) {
                    return false;
                }
            } else if (!tenant.equals(other.tenant)) {
                return false;
            }
            return true;
        }

    }

    /**
     * Cancels given {@link Action} for this {@link Target}. The method will
     * immediately add a {@link Status#CANCELED} status to the action. However,
     * it might be possible that the controller will continue to work on the
     * cancellation. The controller needs to acknowledge or reject the
     * cancellation using {@link DdiRootController#postCancelActionFeedback}.
     *
     * @param actionId
     *            to be canceled
     *
     * @return canceled {@link Action}
     *
     * @throws CancelActionNotAllowedException
     *             in case the given action is not active or is already canceled
     * @throws EntityNotFoundException
     *             if action with given actionId does not exist.
     */
    @Override
    @Modifying
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Action cancelAction(final long actionId) {
        LOG.debug("cancelAction({})", actionId);

        final JpaAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new EntityNotFoundException(Action.class, actionId));

        if (action.isCancelingOrCanceled()) {
            throw new CancelActionNotAllowedException("Actions in canceling or canceled state cannot be canceled");
        }

        if (action.isActive()) {
            LOG.debug("action ({}) was still active. Change to {}.", action, Status.CANCELING);
            action.setStatus(Status.CANCELING);

            // document that the status has been retrieved
            actionStatusRepository.save(new JpaActionStatus(action, Status.CANCELING, System.currentTimeMillis(),
                    "manual cancelation requested"));
            final Action saveAction = actionRepository.save(action);
            cancelAssignDistributionSetEvent((JpaTarget) action.getTarget(), action.getId());

            return saveAction;
        } else {
            throw new CancelActionNotAllowedException(
                    "Action [id: " + action.getId() + "] is not active and cannot be canceled");
        }
    }

    @Override
    public void updateActionExternalRef(final long actionId, @NotEmpty final String externalRef) {
        actionRepository.updateExternalRef(actionId, externalRef);
    }

    @Override
    public Optional<Action> getActionByExternalRef(@NotEmpty final String externalRef) {
        return actionRepository.findByExternalRef(externalRef);
    }

    private void cancelAssignDistributionSetEvent(final JpaTarget target, final Long actionId) {
        afterCommit.afterCommit(() -> eventPublisherHolder.getEventPublisher().publishEvent(
                new CancelTargetAssignmentEvent(target, actionId, eventPublisherHolder.getApplicationId())));
    }

    // for testing
    void setTargetRepository(final TargetRepository targetRepositorySpy) {
        this.targetRepository = targetRepositorySpy;
    }
}
