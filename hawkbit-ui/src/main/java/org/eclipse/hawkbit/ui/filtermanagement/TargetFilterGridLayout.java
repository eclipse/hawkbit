/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import java.util.Collections;
import java.util.List;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.eclipse.hawkbit.ui.common.layout.AbstractGridComponentLayout;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener.EntityModifiedAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.FilterChangedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGridRefreshAwareSupport;
import org.eclipse.hawkbit.ui.filtermanagement.state.FilterManagementUIState;

/**
 * TargetFilter table layout.
 */
public class TargetFilterGridLayout extends AbstractGridComponentLayout {
    private static final long serialVersionUID = 1L;

    private final TargetFilterGridHeader targetFilterGridHeader;
    private final TargetFilterGrid targetFilterGrid;

    private final transient FilterChangedListener<ProxyTargetFilterQuery> targetQueryFilterListener;
    private final transient EntityModifiedListener<ProxyTargetFilterQuery> filterQueryModifiedListener;

    /**
     * TargetFilterGridLayout constructor
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param targetFilterQueryManagement
     *            management to CRUD target filters
     * @param targetManagement
     *            management to get targets matching the filters
     * @param distributionSetManagement
     *            management to get distribution sets for auto-assignment
     * @param filterManagementUIState
     *            to persist the user interaction
     */
    public TargetFilterGridLayout(final CommonUiDependencies uiDependencies,
            final TargetFilterQueryManagement targetFilterQueryManagement, final TargetManagement targetManagement,
            final DistributionSetManagement distributionSetManagement,
            final FilterManagementUIState filterManagementUIState) {
        this.targetFilterGridHeader = new TargetFilterGridHeader(uiDependencies,
                filterManagementUIState.getGridLayoutUiState());

        final AutoAssignmentWindowBuilder autoAssignmentWindowBuilder = new AutoAssignmentWindowBuilder(uiDependencies,
                targetManagement, targetFilterQueryManagement, distributionSetManagement);

        this.targetFilterGrid = new TargetFilterGrid(uiDependencies, filterManagementUIState.getGridLayoutUiState(),
                targetFilterQueryManagement, autoAssignmentWindowBuilder);

        final EventViewAware viewAware = new EventViewAware(EventView.TARGET_FILTER);
        this.targetQueryFilterListener = new FilterChangedListener<>(uiDependencies.getEventBus(),
                ProxyTargetFilterQuery.class, viewAware, targetFilterGrid.getFilterSupport());
        this.filterQueryModifiedListener = new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(),
                ProxyTargetFilterQuery.class).viewAware(viewAware)
                        .entityModifiedAwareSupports(getFilterQueryModifiedAwareSupports()).build();

        buildLayout(targetFilterGridHeader, targetFilterGrid);
    }

    private List<EntityModifiedAwareSupport> getFilterQueryModifiedAwareSupports() {
        return Collections.singletonList(EntityModifiedGridRefreshAwareSupport.of(targetFilterGrid::refreshAll));
    }

    @Override
    public void restoreState() {
        targetFilterGridHeader.restoreState();
        targetFilterGrid.restoreState();
    }

    @Override
    public void subscribeListeners() {
        targetQueryFilterListener.subscribe();
        filterQueryModifiedListener.subscribe();
    }

    @Override
    public void unsubscribeListeners() {
        targetQueryFilterListener.unsubscribe();
        filterQueryModifiedListener.unsubscribe();
    }
}
