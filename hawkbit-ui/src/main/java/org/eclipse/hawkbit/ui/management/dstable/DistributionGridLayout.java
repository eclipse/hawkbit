/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.dstable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.DistributionSetTagManagement;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTag;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
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
import org.eclipse.hawkbit.ui.common.layout.listener.PinningChangedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.SelectionChangedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGridRefreshAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedPinAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedSelectionAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedTagTokenAwareSupport;
import org.eclipse.hawkbit.ui.common.state.TagFilterLayoutUiState;
import org.eclipse.hawkbit.ui.management.targettable.TargetGridLayoutUiState;

/**
 * Distribution Set table layout in deployment view.
 */
public class DistributionGridLayout extends AbstractDistributionSetGridLayout {
    private static final long serialVersionUID = 1L;

    private final DistributionSetGridHeader distributionGridHeader;
    private final DistributionGrid distributionGrid;
    private final DistributionSetDetailsHeader distributionSetDetailsHeader;
    private final DistributionSetDetails distributionDetails;

    /**
     * Constructor for DistributionGridLayout
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param targetManagement
     *            TargetManagement
     * @param distributionSetManagement
     *            DistributionSetManagement
     * @param smManagement
     *            SoftwareModuleManagement
     * @param distributionSetTypeManagement
     *            DistributionSetTypeManagement
     * @param distributionSetTagManagement
     *            DistributionSetTagManagement
     * @param systemManagement
     *            SystemManagement
     * @param deploymentManagement
     *            DeploymentManagement
     * @param configManagement
     *            TenantConfigurationManagement
     * @param systemSecurityContext
     *            SystemSecurityContext
     * @param uiProperties
     *            UiProperties
     * @param distributionGridLayoutUiState
     *            DistributionGridLayoutUiState
     * @param distributionTagLayoutUiState
     *            TagFilterLayoutUiState
     * @param targetGridLayoutUiState
     *            TargetGridLayoutUiState
     */
    public DistributionGridLayout(final CommonUiDependencies uiDependencies, final TargetManagement targetManagement,
            final DistributionSetManagement distributionSetManagement, final SoftwareModuleManagement smManagement,
            final DistributionSetTypeManagement distributionSetTypeManagement,
            final DistributionSetTagManagement distributionSetTagManagement, final SystemManagement systemManagement,
            final DeploymentManagement deploymentManagement, final TenantConfigurationManagement configManagement,
            final SystemSecurityContext systemSecurityContext, final UiProperties uiProperties,
            final DistributionGridLayoutUiState distributionGridLayoutUiState,
            final TagFilterLayoutUiState distributionTagLayoutUiState,
            final TargetGridLayoutUiState targetGridLayoutUiState) {
        super(uiDependencies, systemManagement, systemSecurityContext, configManagement, distributionSetManagement,
                distributionSetTypeManagement, EventView.DEPLOYMENT);

        this.distributionGridHeader = new DistributionSetGridHeader(uiDependencies, distributionTagLayoutUiState,
                distributionGridLayoutUiState, EventLayout.DS_TAG_FILTER, getEventView());
        this.distributionGridHeader.buildHeader();

        this.distributionGrid = new DistributionGrid(uiDependencies, targetManagement, distributionSetManagement,
                deploymentManagement, uiProperties, distributionGridLayoutUiState, targetGridLayoutUiState,
                distributionTagLayoutUiState);

        this.distributionSetDetailsHeader = new DistributionSetDetailsHeader(uiDependencies, getDsWindowBuilder(),
                getDsMetaDataWindowBuilder());

        this.distributionDetails = new DistributionSetDetails(uiDependencies, distributionSetManagement, smManagement,
                distributionSetTypeManagement, distributionSetTagManagement, configManagement, systemSecurityContext,
                getDsMetaDataWindowBuilder());
        this.distributionDetails.setUnassignSmAllowed(false);
        this.distributionDetails.buildDetails();

        final EventLayoutViewAware layoutViewAware = new EventLayoutViewAware(EventLayout.DS_LIST, getEventView());
        addEventListener(new FilterChangedListener<>(uiDependencies.getEventBus(), ProxyDistributionSet.class,
                layoutViewAware, distributionGrid.getFilterSupport()));
        addEventListener(new PinningChangedListener<>(uiDependencies.getEventBus(), ProxyTarget.class,
                distributionGrid.getPinSupport()));
        addEventListener(new SelectionChangedListener<>(uiDependencies.getEventBus(), layoutViewAware,
                getMasterDsAwareComponents()));
        addEventListener(new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(), ProxyDistributionSet.class)
                .viewAware(layoutViewAware).entityModifiedAwareSupports(getDsModifiedAwareSupports()).build());
        addEventListener(new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(), ProxyTag.class)
                .parentEntityType(ProxyDistributionSet.class).viewAware(layoutViewAware)
                .entityModifiedAwareSupports(getTagModifiedAwareSupports()).build());

        buildLayout(distributionGridHeader, distributionGrid, distributionSetDetailsHeader, distributionDetails);
    }

    private List<MasterEntityAwareComponent<ProxyDistributionSet>> getMasterDsAwareComponents() {
        return Arrays.asList(distributionSetDetailsHeader, distributionDetails);
    }

    private List<EntityModifiedAwareSupport> getDsModifiedAwareSupports() {
        return Arrays.asList(EntityModifiedGridRefreshAwareSupport.of(distributionGrid::refreshAll),
                EntityModifiedSelectionAwareSupport.of(distributionGrid.getSelectionSupport(),
                        distributionGrid::mapIdToProxyEntity, DistributionGridLayout::isIncomplete),
                EntityModifiedPinAwareSupport.of(distributionGrid.getPinSupport(), distributionGrid::mapIdToProxyEntity,
                        DistributionGridLayout::isIncomplete));
    }

    private static boolean isIncomplete(final ProxyDistributionSet ds) {
        return ds != null && !ds.getIsComplete();
    }

    private List<EntityModifiedAwareSupport> getTagModifiedAwareSupports() {
        return Collections
                .singletonList(EntityModifiedTagTokenAwareSupport.of(distributionDetails.getDistributionTagToken()));
    }

    @Override
    public DistributionGrid getDistributionGrid() {
        return distributionGrid;
    }

    @Override
    public DistributionSetGridHeader getDistributionSetGridHeader() {
        return distributionGridHeader;
    }
}
