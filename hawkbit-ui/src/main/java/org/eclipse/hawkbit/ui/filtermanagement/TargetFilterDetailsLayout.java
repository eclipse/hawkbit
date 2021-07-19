/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.rsql.RsqlValidationOracle;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.common.data.suppliers.TargetFilterStateDataSupplier;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventLayoutViewAware;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.eclipse.hawkbit.ui.common.layout.AbstractGridComponentLayout;
import org.eclipse.hawkbit.ui.common.layout.listener.FilterChangedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.ShowEntityFormLayoutListener;
import org.eclipse.hawkbit.ui.filtermanagement.state.TargetFilterDetailsLayoutUiState;

/**
 * DistributionSet table layout.
 */
public class TargetFilterDetailsLayout extends AbstractGridComponentLayout {
    private static final long serialVersionUID = 1L;

    private final TargetFilterDetailsGridHeader targetFilterDetailsGridHeader;
    private final TargetFilterTargetGrid targetFilterTargetGrid;
    private final transient TargetFilterCountMessageLabel targetFilterCountMessageLabel;

    private final transient ShowEntityFormLayoutListener<ProxyTargetFilterQuery> showFilterQueryFormListener;
    private final transient FilterChangedListener<ProxyTarget> targetFilterListener;

    /**
     * TargetFilterDetailsLayout constructor.
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param uiProperties
     *            properties
     * @param rsqlValidationOracle
     *            to get RSQL validation and suggestions
     * @param targetFilterManagement
     *            management to CRUD target filters
     * @param targetFilterStateDataSupplier
     *            target grid data supplier
     * @param uiState
     *            to persist the user interaction
     */
    public TargetFilterDetailsLayout(final CommonUiDependencies uiDependencies, final UiProperties uiProperties,
            final RsqlValidationOracle rsqlValidationOracle, final TargetFilterQueryManagement targetFilterManagement,
            final TargetFilterStateDataSupplier targetFilterStateDataSupplier,
            final TargetFilterDetailsLayoutUiState uiState) {

        this.targetFilterDetailsGridHeader = new TargetFilterDetailsGridHeader(uiDependencies, targetFilterManagement,
                uiProperties, rsqlValidationOracle, uiState);
        this.targetFilterTargetGrid = new TargetFilterTargetGrid(uiDependencies, targetFilterStateDataSupplier,
                uiState);
        this.targetFilterCountMessageLabel = new TargetFilterCountMessageLabel(uiDependencies.getI18n());

        initGridDataUpdatedListener();

        final EventViewAware viewAware = new EventViewAware(EventView.TARGET_FILTER);
        final EventLayoutViewAware layoutViewAware = new EventLayoutViewAware(EventLayout.TARGET_FILTER_QUERY_FORM,
                EventView.TARGET_FILTER);

        this.showFilterQueryFormListener = new ShowEntityFormLayoutListener<>(uiDependencies.getEventBus(),
                ProxyTargetFilterQuery.class, layoutViewAware, targetFilterDetailsGridHeader::showAddFilterLayout,
                targetFilterDetailsGridHeader::showEditFilterLayout);
        this.targetFilterListener = new FilterChangedListener<>(uiDependencies.getEventBus(), ProxyTarget.class,
                viewAware, targetFilterTargetGrid.getFilterSupport());

        buildLayout(targetFilterDetailsGridHeader, targetFilterTargetGrid, targetFilterCountMessageLabel);
    }

    private void initGridDataUpdatedListener() {
        targetFilterTargetGrid.addDataChangedListener(event -> targetFilterCountMessageLabel
                .updateTotalFilteredTargetsCount(targetFilterTargetGrid.getDataSize()));
    }

    @Override
    public void restoreState() {
        targetFilterDetailsGridHeader.restoreState();
        if (targetFilterDetailsGridHeader.isFilterQueryValid()) {
            targetFilterTargetGrid.restoreState();
        }
    }

    @Override
    public void subscribeListeners() {
        showFilterQueryFormListener.subscribe();
        targetFilterListener.subscribe();
    }

    @Override
    public void unsubscribeListeners() {
        showFilterQueryFormListener.unsubscribe();
        targetFilterListener.unsubscribe();
    }
}
