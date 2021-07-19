/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.distributions.disttype.filter;

import java.util.Arrays;
import java.util.List;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyType;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventLayoutViewAware;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.filterlayout.AbstractFilterLayout;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener.EntityModifiedAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.GridActionsVisibilityListener;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGenericSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGridRefreshAwareSupport;
import org.eclipse.hawkbit.ui.common.state.TypeFilterLayoutUiState;
import org.eclipse.hawkbit.ui.distributions.disttype.DsTypeWindowBuilder;

import com.vaadin.ui.ComponentContainer;

/**
 * Distribution Set Type filter buttons layout.
 */
public class DSTypeFilterLayout extends AbstractFilterLayout {
    private static final long serialVersionUID = 1L;

    private final DSTypeFilterHeader dsTypeFilterHeader;
    private final DSTypeFilterButtons dSTypeFilterButtons;

    private final transient GridActionsVisibilityListener gridActionsVisibilityListener;
    private final transient EntityModifiedListener<ProxyType> entityModifiedListener;

    /**
     * Constructor
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param softwareModuleTypeManagement
     *            SoftwareModuleTypeManagement
     * @param distributionSetTypeManagement
     *            DistributionSetTypeManagement
     * @param distributionSetManagement
     *            DistributionSetManagement
     * @param systemManagement
     *            SystemManagement
     * @param dSTypeFilterLayoutUiState
     *            TypeFilterLayoutUiState
     */
    public DSTypeFilterLayout(final CommonUiDependencies uiDependencies,
            final SoftwareModuleTypeManagement softwareModuleTypeManagement,
            final DistributionSetTypeManagement distributionSetTypeManagement,
            final DistributionSetManagement distributionSetManagement, final SystemManagement systemManagement,
            final TypeFilterLayoutUiState dSTypeFilterLayoutUiState) {
        final DsTypeWindowBuilder dsTypeWindowBuilder = new DsTypeWindowBuilder(uiDependencies,
                distributionSetTypeManagement, distributionSetManagement, softwareModuleTypeManagement);

        this.dsTypeFilterHeader = new DSTypeFilterHeader(uiDependencies, dsTypeWindowBuilder,
                dSTypeFilterLayoutUiState);
        this.dSTypeFilterButtons = new DSTypeFilterButtons(uiDependencies, distributionSetTypeManagement,
                systemManagement, dsTypeWindowBuilder, dSTypeFilterLayoutUiState);

        final EventLayoutViewAware layoutViewAware = new EventLayoutViewAware(EventLayout.DS_TYPE_FILTER,
                EventView.DISTRIBUTIONS);
        this.gridActionsVisibilityListener = new GridActionsVisibilityListener(uiDependencies.getEventBus(),
                layoutViewAware, dSTypeFilterButtons::hideActionColumns, dSTypeFilterButtons::showEditColumn,
                dSTypeFilterButtons::showDeleteColumn);
        this.entityModifiedListener = new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(),
                ProxyType.class).parentEntityType(ProxyDistributionSet.class).viewAware(layoutViewAware)
                        .entityModifiedAwareSupports(getEntityModifiedAwareSupports()).build();

        buildLayout();
    }

    private List<EntityModifiedAwareSupport> getEntityModifiedAwareSupports() {
        return Arrays.asList(EntityModifiedGridRefreshAwareSupport.of(dSTypeFilterButtons::refreshAll),
                EntityModifiedGenericSupport.of(null, null, dSTypeFilterButtons::resetFilterOnTypesDeleted));
    }

    @Override
    protected DSTypeFilterHeader getFilterHeader() {
        return dsTypeFilterHeader;
    }

    @Override
    protected ComponentContainer getFilterContent() {
        return wrapFilterContent(dSTypeFilterButtons);
    }

    @Override
    public void restoreState() {
        dSTypeFilterButtons.restoreState();
    }

    @Override
    public void onViewEnter() {
        dSTypeFilterButtons.reevaluateFilter();
    }

    @Override
    public void subscribeListeners() {
        gridActionsVisibilityListener.subscribe();
        entityModifiedListener.subscribe();
    }

    @Override
    public void unsubscribeListeners() {
        gridActionsVisibilityListener.unsubscribe();
        entityModifiedListener.unsubscribe();
    }
}
