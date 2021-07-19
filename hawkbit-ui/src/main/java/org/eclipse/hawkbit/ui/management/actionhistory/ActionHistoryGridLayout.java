/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.actionhistory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyAction;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventLayoutViewAware;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.layout.AbstractGridComponentLayout;
import org.eclipse.hawkbit.ui.common.layout.MasterEntityAwareComponent;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener.EntityModifiedAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.SelectionChangedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGridRefreshAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedSelectionAwareSupport;

/**
 * Layout responsible for action-history-grid and the corresponding header.
 */
public class ActionHistoryGridLayout extends AbstractGridComponentLayout {
    private static final long serialVersionUID = 1L;

    private final ActionHistoryGridHeader actionHistoryHeader;
    private final ActionHistoryGrid actionHistoryGrid;

    private final transient SelectionChangedListener<ProxyTarget> masterEntityChangedListener;
    private final transient EntityModifiedListener<ProxyAction> entityModifiedListener;

    /**
     * Constructor for ActionHistoryGridLayout
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param deploymentManagement
     *            DeploymentManagement
     * @param actionHistoryGridLayoutUiState
     *            ActionHistoryGridLayoutUiState
     */
    public ActionHistoryGridLayout(final CommonUiDependencies uiDependencies,
            final DeploymentManagement deploymentManagement,
            final ActionHistoryGridLayoutUiState actionHistoryGridLayoutUiState) {
        this.actionHistoryHeader = new ActionHistoryGridHeader(uiDependencies, actionHistoryGridLayoutUiState);
        this.actionHistoryGrid = new ActionHistoryGrid(uiDependencies, deploymentManagement,
                actionHistoryGridLayoutUiState);

        final EventLayoutViewAware masterLayoutView = new EventLayoutViewAware(EventLayout.TARGET_LIST,
                EventView.DEPLOYMENT);
        this.masterEntityChangedListener = new SelectionChangedListener<>(uiDependencies.getEventBus(),
                masterLayoutView, getMasterEntityAwareComponents());
        this.entityModifiedListener = new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(),
                ProxyAction.class).parentEntityType(ProxyTarget.class).parentEntityIdProvider(this::getMasterEntityId)
                        .viewAware(masterLayoutView).entityModifiedAwareSupports(getEntityModifiedAwareSupports())
                        .build();

        buildLayout(actionHistoryHeader, actionHistoryGrid);
    }

    private List<MasterEntityAwareComponent<ProxyTarget>> getMasterEntityAwareComponents() {
        return Arrays.asList(actionHistoryHeader, actionHistoryGrid.getMasterEntitySupport());
    }

    private List<EntityModifiedAwareSupport> getEntityModifiedAwareSupports() {
        return Arrays.asList(EntityModifiedGridRefreshAwareSupport.of(actionHistoryGrid::refreshAll),
                EntityModifiedSelectionAwareSupport.of(actionHistoryGrid.getSelectionSupport(),
                        actionHistoryGrid::mapIdToProxyEntity));
    }

    private Optional<Long> getMasterEntityId() {
        return Optional.ofNullable(actionHistoryGrid.getMasterEntitySupport().getMasterId());
    }

    /**
     * Maximize the action history grid
     */
    public void maximize() {
        actionHistoryGrid.createMaximizedContent();
        actionHistoryGrid.getSelectionSupport().selectFirstRow();
    }

    /**
     * Minimize the action history grid
     */
    public void minimize() {
        actionHistoryGrid.createMinimizedContent();
    }

    @Override
    public void restoreState() {
        actionHistoryHeader.restoreState();
    }

    @Override
    public void onViewEnter() {
        actionHistoryGrid.getSelectionSupport().reselectCurrentEntity();
    }

    @Override
    public void subscribeListeners() {
        entityModifiedListener.subscribe();
        masterEntityChangedListener.subscribe();
    }

    @Override
    public void unsubscribeListeners() {
        entityModifiedListener.unsubscribe();
        masterEntityChangedListener.unsubscribe();
    }
}
