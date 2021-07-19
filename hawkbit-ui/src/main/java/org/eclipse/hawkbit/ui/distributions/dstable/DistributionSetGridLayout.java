/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.distributions.dstable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.DistributionSetTagManagement;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTag;
import org.eclipse.hawkbit.ui.common.distributionset.AbstractDistributionSetGridLayout;
import org.eclipse.hawkbit.ui.common.distributionset.DistributionSetDetails;
import org.eclipse.hawkbit.ui.common.distributionset.DistributionSetDetailsHeader;
import org.eclipse.hawkbit.ui.common.distributionset.DistributionSetGridHeader;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventLayoutViewAware;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.layout.MasterEntityAwareComponent;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener.EntityModifiedAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.FilterChangedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.SelectGridEntityListener;
import org.eclipse.hawkbit.ui.common.layout.listener.SelectionChangedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGridRefreshAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedSelectionAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedTagTokenAwareSupport;
import org.eclipse.hawkbit.ui.common.state.GridLayoutUiState;
import org.eclipse.hawkbit.ui.common.state.TypeFilterLayoutUiState;

/**
 * DistributionSet table layout in distributions view.
 */
public class DistributionSetGridLayout extends AbstractDistributionSetGridLayout {
    private static final long serialVersionUID = 1L;

    private final DistributionSetGridHeader distributionSetGridHeader;
    private final DistributionSetGrid distributionSetGrid;
    private final DistributionSetDetailsHeader distributionSetDetailsHeader;
    private final DistributionSetDetails distributionSetDetails;

    /**
     * Constructor for DistributionSetGridLayout
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param targetManagement
     *            TargetManagement
     * @param targetFilterQueryManagement
     *            TargetFilterQueryManagement
     * @param distributionSetManagement
     *            DistributionSetManagement
     * @param smManagement
     *            SoftwareModuleManagement
     * @param distributionSetTypeManagement
     *            DistributionSetTypeManagement
     * @param distributionSetTagManagement
     *            DistributionSetTagManagement
     * @param smTypeManagement
     *            SoftwareModuleTypeManagement
     * @param systemManagement
     *            SystemManagement
     * @param configManagement
     *            TenantConfigurationManagement
     * @param systemSecurityContext
     *            SystemSecurityContext
     * @param dSTypeFilterLayoutUiState
     *            TypeFilterLayoutUiState
     * @param distributionSetGridLayoutUiState
     *            GridLayoutUiState
     */
    public DistributionSetGridLayout(final CommonUiDependencies uiDependencies, final TargetManagement targetManagement,
            final TargetFilterQueryManagement targetFilterQueryManagement,
            final DistributionSetManagement distributionSetManagement, final SoftwareModuleManagement smManagement,
            final DistributionSetTypeManagement distributionSetTypeManagement,
            final DistributionSetTagManagement distributionSetTagManagement,
            final SoftwareModuleTypeManagement smTypeManagement, final SystemManagement systemManagement,
            final TenantConfigurationManagement configManagement, final SystemSecurityContext systemSecurityContext,
            final TypeFilterLayoutUiState dSTypeFilterLayoutUiState,
            final GridLayoutUiState distributionSetGridLayoutUiState) {
        super(uiDependencies, systemManagement, systemSecurityContext, configManagement, distributionSetManagement,
                distributionSetTypeManagement, EventView.DISTRIBUTIONS);

        this.distributionSetGridHeader = new DistributionSetGridHeader(uiDependencies, dSTypeFilterLayoutUiState,
                distributionSetGridLayoutUiState, EventLayout.DS_TYPE_FILTER, getEventView());
        this.distributionSetGridHeader.addAddHeaderSupport(getDsWindowBuilder());
        this.distributionSetGridHeader.buildHeader();

        this.distributionSetGrid = new DistributionSetGrid(uiDependencies, targetManagement, distributionSetManagement,
                smManagement, distributionSetTypeManagement, smTypeManagement, dSTypeFilterLayoutUiState,
                distributionSetGridLayoutUiState);

        this.distributionSetDetailsHeader = new DistributionSetDetailsHeader(uiDependencies, getDsWindowBuilder(),
                getDsMetaDataWindowBuilder());

        this.distributionSetDetails = new DistributionSetDetails(uiDependencies, distributionSetManagement,
                smManagement, distributionSetTypeManagement, distributionSetTagManagement, configManagement,
                systemSecurityContext, getDsMetaDataWindowBuilder());
        this.distributionSetDetails.setUnassignSmAllowed(true);
        this.distributionSetDetails.addTfqDetailsGrid(targetFilterQueryManagement);
        this.distributionSetDetails.buildDetails();

        final EventLayoutViewAware layoutViewAware = new EventLayoutViewAware(EventLayout.DS_LIST, getEventView());
        addEventListener(new FilterChangedListener<>(uiDependencies.getEventBus(), ProxyDistributionSet.class,
                layoutViewAware, distributionSetGrid.getFilterSupport()));
        addEventListener(new SelectionChangedListener<>(uiDependencies.getEventBus(), layoutViewAware,
                getDsEntityAwareComponents()));
        addEventListener(new SelectGridEntityListener<>(uiDependencies.getEventBus(), layoutViewAware,
                distributionSetGrid.getSelectionSupport()));
        addEventListener(new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(), ProxyDistributionSet.class)
                .viewAware(layoutViewAware).entityModifiedAwareSupports(getDsModifiedAwareSupports()).build());
        addEventListener(new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(), ProxyTag.class)
                .parentEntityType(ProxyDistributionSet.class).viewAware(layoutViewAware)
                .entityModifiedAwareSupports(getTagModifiedAwareSupports()).build());

        buildLayout(distributionSetGridHeader, distributionSetGrid, distributionSetDetailsHeader,
                distributionSetDetails);
    }

    private List<MasterEntityAwareComponent<ProxyDistributionSet>> getDsEntityAwareComponents() {
        return Arrays.asList(distributionSetDetailsHeader, distributionSetDetails);
    }

    private List<EntityModifiedAwareSupport> getDsModifiedAwareSupports() {
        return Arrays.asList(EntityModifiedGridRefreshAwareSupport.of(distributionSetGrid::refreshAll),
                EntityModifiedSelectionAwareSupport.of(distributionSetGrid.getSelectionSupport(),
                        distributionSetGrid::mapIdToProxyEntity));
    }

    private List<EntityModifiedAwareSupport> getTagModifiedAwareSupports() {
        return Collections
                .singletonList(EntityModifiedTagTokenAwareSupport.of(distributionSetDetails.getDistributionTagToken()));
    }

    @Override
    public DistributionSetGrid getDistributionGrid() {
        return distributionSetGrid;
    }

    @Override
    public DistributionSetGridHeader getDistributionSetGridHeader() {
        return distributionSetGridHeader;
    }
}
