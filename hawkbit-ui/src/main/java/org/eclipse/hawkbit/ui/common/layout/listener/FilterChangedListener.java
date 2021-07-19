/** 
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.layout.listener;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.eclipse.hawkbit.ui.common.event.FilterChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.common.grid.support.FilterSupport;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

/**
 * Event changed listener for filter changed
 *
 * @param <T>
 *            Generic type of ProxyIdentifiableEntity
 */
public class FilterChangedListener<T extends ProxyIdentifiableEntity> extends ViewAwareListener {
    private final Class<T> entityType;
    private final FilterSupport<T, ?> filterSupport;
    private final Runnable updateFilterCountInfo;

    /**
     * Constructor for FilterChangedListener
     *
     * @param eventBus
     *            UIEventBus
     * @param entityType
     *            Generic type entity
     * @param viewAware
     *            EventViewAware
     * @param filterSupport
     *            Generic type filter support
     */
    public FilterChangedListener(final UIEventBus eventBus, final Class<T> entityType, final EventViewAware viewAware,
            final FilterSupport<T, ?> filterSupport) {
        this(eventBus, entityType, viewAware, filterSupport, null);
    }

    /**
     * Constructor for FilterChangedListener
     *
     * @param eventBus
     *            UIEventBus
     * @param entityType
     *            Generic type entity
     * @param viewAware
     *            EventViewAware
     * @param filterSupport
     *            Generic type filter support
     * @param updateFilterCountInfo
     *            Callback to update entities count info on filter change
     */
    public FilterChangedListener(final UIEventBus eventBus, final Class<T> entityType, final EventViewAware viewAware,
            final FilterSupport<T, ?> filterSupport, final Runnable updateFilterCountInfo) {
        super(eventBus, EventTopics.FILTER_CHANGED, viewAware);

        this.entityType = entityType;
        this.filterSupport = filterSupport;
        this.updateFilterCountInfo = updateFilterCountInfo;
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    private void onFilterEvent(final FilterChangedEventPayload<?> eventPayload) {
        if (!suitableEntityType(eventPayload.getEntityType()) || !getViewAware().suitableView(eventPayload)) {
            return;
        }

        final FilterType filterType = eventPayload.getFilterType();

        if (filterSupport.isFilterTypeSupported(filterType)) {
            filterSupport.updateFilter(filterType, eventPayload.getFilterValue());
            if (updateFilterCountInfo != null) {
                updateFilterCountInfo.run();
            }
        }
    }

    private boolean suitableEntityType(final Class<? extends ProxyIdentifiableEntity> type) {
        return entityType != null && entityType.equals(type);
    }
}
