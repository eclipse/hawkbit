/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout.rolloutgroup;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyRollout;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyRolloutGroup;
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
import org.eclipse.hawkbit.ui.rollout.RolloutManagementUIState;

/**
 * Groups List View.
 */
public class RolloutGroupGridLayout extends AbstractGridComponentLayout {
    private static final long serialVersionUID = 1L;

    private final RolloutGroupGridHeader rolloutGroupsListHeader;
    private final RolloutGroupGrid rolloutGroupListGrid;

    private final transient SelectionChangedListener<ProxyRollout> masterEntityChangedListener;
    private final transient EntityModifiedListener<ProxyRolloutGroup> entityModifiedListener;

    /**
     * Constructor for RolloutGroupsListView
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param rolloutGroupManagement
     *            RolloutGroupManagement
     * @param rolloutManagementUIState
     *            UIState
     */
    public RolloutGroupGridLayout(final CommonUiDependencies uiDependencies,
            final RolloutGroupManagement rolloutGroupManagement,
            final RolloutManagementUIState rolloutManagementUIState) {
        this.rolloutGroupsListHeader = new RolloutGroupGridHeader(uiDependencies, rolloutManagementUIState);
        this.rolloutGroupListGrid = new RolloutGroupGrid(uiDependencies, rolloutGroupManagement,
                rolloutManagementUIState);

        final EventLayoutViewAware masterLayoutView = new EventLayoutViewAware(EventLayout.ROLLOUT_LIST,
                EventView.ROLLOUT);
        this.masterEntityChangedListener = new SelectionChangedListener<>(uiDependencies.getEventBus(),
                masterLayoutView, getMasterEntityAwareComponents());
        this.entityModifiedListener = new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(),
                ProxyRolloutGroup.class).parentEntityType(ProxyRollout.class)
                        .parentEntityIdProvider(this::getMasterEntityId).viewAware(masterLayoutView)
                        .entityModifiedAwareSupports(getEntityModifiedAwareSupports()).build();

        buildLayout(rolloutGroupsListHeader, rolloutGroupListGrid);
    }

    private List<MasterEntityAwareComponent<ProxyRollout>> getMasterEntityAwareComponents() {
        return Arrays.asList(rolloutGroupsListHeader, rolloutGroupListGrid.getMasterEntitySupport());
    }

    private List<EntityModifiedAwareSupport> getEntityModifiedAwareSupports() {
        return Arrays.asList(
                EntityModifiedGridRefreshAwareSupport.of(rolloutGroupListGrid::refreshAll,
                        rolloutGroupListGrid::updateGridItems),
                EntityModifiedSelectionAwareSupport.of(rolloutGroupListGrid.getSelectionSupport(),
                        rolloutGroupListGrid::mapIdToProxyEntity));
    }

    private Optional<Long> getMasterEntityId() {
        return Optional.ofNullable(rolloutGroupListGrid.getMasterEntitySupport().getMasterId());
    }

    @Override
    public void restoreState() {
        rolloutGroupsListHeader.restoreState();
        rolloutGroupListGrid.restoreState();
    }

    @Override
    public void subscribeListeners() {
        masterEntityChangedListener.subscribe();
        entityModifiedListener.subscribe();
    }

    @Override
    public void unsubscribeListeners() {
        masterEntityChangedListener.unsubscribe();
        entityModifiedListener.unsubscribe();
    }
}
