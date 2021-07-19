/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout.rollout;

import java.util.Arrays;
import java.util.List;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyRollout;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.eclipse.hawkbit.ui.common.layout.AbstractGridComponentLayout;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener.EntityModifiedAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.FilterChangedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGridRefreshAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedSelectionAwareSupport;
import org.eclipse.hawkbit.ui.rollout.RolloutManagementUIState;
import org.eclipse.hawkbit.ui.rollout.window.RolloutWindowBuilder;
import org.eclipse.hawkbit.ui.rollout.window.RolloutWindowDependencies;

/**
 * Rollout list view.
 */
public class RolloutGridLayout extends AbstractGridComponentLayout {
    private static final long serialVersionUID = 1L;

    private final RolloutGridHeader rolloutListHeader;
    private final RolloutGrid rolloutListGrid;

    private final transient FilterChangedListener<ProxyRollout> rolloutFilterListener;
    private final transient EntityModifiedListener<ProxyRollout> rolloutModifiedListener;

    /**
     * Constructor for RolloutGridLayout
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param rolloutManagementUIState
     *            RolloutManagementUIState
     * @param rolloutManagement
     *            RolloutManagement
     * @param targetManagement
     *            TargetManagement
     * @param uiProperties
     *            UiProperties
     * @param targetFilterQueryManagement
     *            TargetFilterQueryManagement
     * @param rolloutGroupManagement
     *            RolloutGroupManagement
     * @param quotaManagement
     *            QuotaManagement
     * @param tenantConfigManagement
     *            TenantConfigurationManagement
     * @param distributionSetManagement
     *            DistributionSetManagement
     * @param systemSecurityContext
     *            SystemSecurityContext
     */
    public RolloutGridLayout(final CommonUiDependencies uiDependencies,
            final RolloutManagementUIState rolloutManagementUIState, final RolloutManagement rolloutManagement,
            final TargetManagement targetManagement, final UiProperties uiProperties,
            final TargetFilterQueryManagement targetFilterQueryManagement,
            final RolloutGroupManagement rolloutGroupManagement, final QuotaManagement quotaManagement,
            final TenantConfigurationManagement tenantConfigManagement,
            final DistributionSetManagement distributionSetManagement,
            final SystemSecurityContext systemSecurityContext) {
        final RolloutWindowDependencies rolloutWindowDependecies = new RolloutWindowDependencies(uiDependencies,
                rolloutManagement, targetManagement, uiProperties, targetFilterQueryManagement, rolloutGroupManagement,
                quotaManagement, distributionSetManagement);

        final RolloutWindowBuilder rolloutWindowBuilder = new RolloutWindowBuilder(rolloutWindowDependecies);

        this.rolloutListHeader = new RolloutGridHeader(uiDependencies, rolloutManagementUIState, rolloutWindowBuilder);
        this.rolloutListGrid = new RolloutGrid(uiDependencies, rolloutManagement, rolloutGroupManagement,
                rolloutManagementUIState, tenantConfigManagement, rolloutWindowBuilder, systemSecurityContext);

        final EventViewAware viewAware = new EventViewAware(EventView.ROLLOUT);
        this.rolloutFilterListener = new FilterChangedListener<>(uiDependencies.getEventBus(), ProxyRollout.class,
                viewAware, rolloutListGrid.getFilterSupport());
        this.rolloutModifiedListener = new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(),
                ProxyRollout.class).viewAware(viewAware).entityModifiedAwareSupports(getRolloutModifiedAwareSupports())
                        .build();

        buildLayout(rolloutListHeader, rolloutListGrid);
    }

    private List<EntityModifiedAwareSupport> getRolloutModifiedAwareSupports() {
        return Arrays.asList(
                EntityModifiedGridRefreshAwareSupport.of(rolloutListGrid::refreshAll, rolloutListGrid::updateGridItems),
                EntityModifiedSelectionAwareSupport.of(rolloutListGrid.getSelectionSupport(),
                        rolloutListGrid::mapIdToProxyEntity, rolloutListGrid::onSelectedRolloutDeleted));
    }

    @Override
    public void restoreState() {
        rolloutListHeader.restoreState();
        rolloutListGrid.restoreState();
    }

    @Override
    public void onViewEnter() {
        rolloutListGrid.reselectCurrentRollout();
    }

    @Override
    public void subscribeListeners() {
        rolloutFilterListener.subscribe();
        rolloutModifiedListener.subscribe();
    }

    @Override
    public void unsubscribeListeners() {
        rolloutFilterListener.unsubscribe();
        rolloutModifiedListener.unsubscribe();
    }
}
