/** 
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.layout.listener;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.hawkbit.ui.common.ViewNameAware;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload.EntityModifiedEventType;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.springframework.util.CollectionUtils;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.navigator.View;
import com.vaadin.ui.UI;

/**
 * Event listener for entity modified
 *
 * @param <T>
 *            Generic typ of ProxyIdentifiableEntity
 */
public final class EntityModifiedListener<T extends ProxyIdentifiableEntity> extends ViewAwareListener {
    private final Class<T> entityType;
    private final Class<? extends ProxyIdentifiableEntity> parentEntityType;
    private final Supplier<Optional<Long>> parentEntityIdProvider;
    private final List<EntityModifiedAwareSupport> entityModifiedAwareSupports;

    /**
     * Constructor for EntityModifiedListener
     * 
     * @param eventBus
     *            UIEventBus
     * @param entityType
     *            Generic tpe entity
     * @param parentEntityType
     *            Identifiable entity
     * @param parentEntityIdProvider
     *            Parent entity id provider
     * @param viewAware
     *            View aware
     * @param entityModifiedAwareSupports
     *            List of entity modified aware support
     */
    private EntityModifiedListener(final UIEventBus eventBus, final Class<T> entityType,
            final Class<? extends ProxyIdentifiableEntity> parentEntityType,
            final Supplier<Optional<Long>> parentEntityIdProvider, final EventViewAware viewAware,
            final List<EntityModifiedAwareSupport> entityModifiedAwareSupports) {
        super(eventBus, EventTopics.ENTITY_MODIFIED, viewAware);

        this.entityType = entityType;
        this.parentEntityType = parentEntityType;
        this.parentEntityIdProvider = parentEntityIdProvider;
        this.entityModifiedAwareSupports = entityModifiedAwareSupports;
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    private void onEntityModifiedEvent(final EntityModifiedEventPayload eventPayload) {
        if (!suitableView() || !suitableEntityType(eventPayload.getEntityType())
                || !suitableParentEntity(eventPayload.getParentType(), eventPayload.getParentId())) {
            return;
        }

        if (CollectionUtils.isEmpty(entityModifiedAwareSupports)) {
            return;
        }

        final EntityModifiedEventType eventType = eventPayload.getEntityModifiedEventType();
        final Collection<Long> entityIds = eventPayload.getEntityIds();

        switch (eventType) {
        case ENTITY_ADDED:
            handleEntitiesModified(support -> support.onEntitiesAdded(entityIds));
            break;
        case ENTITY_UPDATED:
            handleEntitiesModified(support -> support.onEntitiesUpdated(entityIds));
            break;
        case ENTITY_REMOVED:
            handleEntitiesModified(support -> support.onEntitiesDeleted(entityIds));
            break;
        }
    }

    private boolean suitableView() {
        // if view is not explicitly defined allow events from all views
        if (getView() == null) {
            return true;
        }

        return getView().matchByViewName(getCurrentViewName());
    }

    private String getCurrentViewName() {
        final View currentView = UI.getCurrent().getNavigator().getCurrentView();
        if (currentView instanceof ViewNameAware) {
            return ((ViewNameAware) currentView).getViewName();
        }

        return "";
    }

    private boolean suitableEntityType(final Class<? extends ProxyIdentifiableEntity> type) {
        return entityType != null && entityType.equals(type);
    }

    private boolean suitableParentEntity(final Class<? extends ProxyIdentifiableEntity> parentType,
            final Long parentId) {
        return suitableParentEntityType(parentType) && suitableParentEntityId(parentId);
    }

    private boolean suitableParentEntityType(final Class<? extends ProxyIdentifiableEntity> parentType) {
        // parent type is optional
        return parentEntityType == null || parentEntityType.equals(parentType);
    }

    private boolean suitableParentEntityId(final Long parentId) {
        // parent id is optional
        return parentEntityIdProvider == null
                || parentEntityIdProvider.get().map(id -> id.equals(parentId)).orElse(false);
    }

    private void handleEntitiesModified(final Consumer<EntityModifiedAwareSupport> handler) {
        UI.getCurrent().access(() -> entityModifiedAwareSupports.forEach(handler::accept));
    }

    /**
     * Interface for entity modified aware support
     */
    public interface EntityModifiedAwareSupport {

        /**
         * On entities added
         *
         * @param entityIds
         *            List of id
         */
        default void onEntitiesAdded(final Collection<Long> entityIds) {
            // do nothing by default
        }

        /**
         * On entities updated
         *
         * @param entityIds
         *            List of id
         */
        default void onEntitiesUpdated(final Collection<Long> entityIds) {
            // do nothing by default
        }

        /**
         * On entities deleted
         *
         * @param entityIds
         *            List of id
         */
        default void onEntitiesDeleted(final Collection<Long> entityIds) {
            // do nothing by default
        }
    }

    /**
     * Builder for entity
     *
     * @param <T>
     *            Generic type of ProxyIdentifiableEntity
     */
    public static class Builder<T extends ProxyIdentifiableEntity> {
        private final UIEventBus eventBus;
        private final Class<T> entityType;
        private Class<? extends ProxyIdentifiableEntity> parentEntityType;
        private Supplier<Optional<Long>> parentEntityIdProvider;
        private EventViewAware viewAware;
        private List<EntityModifiedAwareSupport> entityModifiedAwareSupports;

        /**
         * Constructor for builder
         *
         * @param eventBus
         *            UIEventBus
         * @param entityType
         *            Generic type entity
         */
        public Builder(final UIEventBus eventBus, final Class<T> entityType) {
            this.eventBus = eventBus;
            this.entityType = entityType;
        }

        /**
         * Get parent entity type.
         * 
         * @param parentEntityType
         *            Parent entity type
         *
         * @return builder
         */
        public Builder<T> parentEntityType(final Class<? extends ProxyIdentifiableEntity> parentEntityType) {
            this.parentEntityType = parentEntityType;
            return this;
        }

        /**
         * Get parent id
         *
         * @param parentEntityIdProvider
         *            Parent entity id provider
         *
         * @return builder
         */
        public Builder<T> parentEntityIdProvider(final Supplier<Optional<Long>> parentEntityIdProvider) {
            this.parentEntityIdProvider = parentEntityIdProvider;
            return this;
        }

        /**
         * Set View aware
         *
         * @param viewAware
         *            View aware
         * @return builder
         */
        public Builder<T> viewAware(final EventViewAware viewAware) {
            this.viewAware = viewAware;
            return this;
        }

        /**
         * Build entity modified support for layout
         *
         * @param entityModifiedAwareSupports
         *            List of entity modified aware support
         * @return builder
         */
        public Builder<T> entityModifiedAwareSupports(
                final List<EntityModifiedAwareSupport> entityModifiedAwareSupports) {
            this.entityModifiedAwareSupports = entityModifiedAwareSupports;
            return this;
        }

        /**
         * @return builder
         */
        public EntityModifiedListener<T> build() {
            return new EntityModifiedListener<>(eventBus, entityType, parentEntityType, parentEntityIdProvider,
                    viewAware, entityModifiedAwareSupports);
        }
    }
}
