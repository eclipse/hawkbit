/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.distributions.smtable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.ui.artifacts.smtable.SoftwareModuleGrid;
import org.eclipse.hawkbit.ui.artifacts.smtable.SoftwareModuleGridHeader;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySoftwareModule;
import org.eclipse.hawkbit.ui.common.detailslayout.SoftwareModuleDetails;
import org.eclipse.hawkbit.ui.common.detailslayout.SoftwareModuleDetailsHeader;
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
import org.eclipse.hawkbit.ui.common.softwaremodule.AbstractSoftwareModuleGridLayout;
import org.eclipse.hawkbit.ui.common.state.GridLayoutUiState;
import org.eclipse.hawkbit.ui.common.state.TypeFilterLayoutUiState;

/**
 * Implementation of software module Layout on the Distribution View
 */
public class SwModuleGridLayout extends AbstractSoftwareModuleGridLayout {
    private static final long serialVersionUID = 1L;

    private final SoftwareModuleGridHeader swModuleGridHeader;
    private final SoftwareModuleGrid swModuleGrid;
    private final SoftwareModuleDetailsHeader softwareModuleDetailsHeader;
    private final SoftwareModuleDetails swModuleDetails;

    /**
     * Constructor for SwModuleGridLayout
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param softwareModuleManagement
     *            SoftwareModuleManagement
     * @param softwareModuleTypeManagement
     *            SoftwareModuleTypeManagement
     * @param artifactManagement
     *            ArtifactManagement
     * @param smTypeFilterLayoutUiState
     *            TypeFilterLayoutUiState
     * @param swModuleGridLayoutUiState
     *            GridLayoutUiState
     */
    public SwModuleGridLayout(final CommonUiDependencies uiDependencies,
            final SoftwareModuleManagement softwareModuleManagement,
            final SoftwareModuleTypeManagement softwareModuleTypeManagement,
            final ArtifactManagement artifactManagement, final TypeFilterLayoutUiState smTypeFilterLayoutUiState,
            final GridLayoutUiState swModuleGridLayoutUiState) {
        super(uiDependencies, softwareModuleManagement, softwareModuleTypeManagement, EventView.DISTRIBUTIONS);

        this.swModuleGridHeader = new SoftwareModuleGridHeader(uiDependencies, smTypeFilterLayoutUiState,
                swModuleGridLayoutUiState, getSmWindowBuilder(), getEventView());
        this.swModuleGridHeader.buildHeader();
        this.swModuleGrid = new SoftwareModuleGrid(uiDependencies, smTypeFilterLayoutUiState, swModuleGridLayoutUiState,
                softwareModuleManagement, getEventView());
        this.swModuleGrid.addDragAndDropSupport();
        this.swModuleGrid.addMasterSupport();
        this.swModuleGrid.init();

        this.softwareModuleDetailsHeader = new SoftwareModuleDetailsHeader(uiDependencies, getSmWindowBuilder(),
                getSmMetaDataWindowBuilder());
        this.softwareModuleDetailsHeader.addArtifactDetailsHeaderSupport(artifactManagement);
        this.softwareModuleDetailsHeader.buildHeader();
        this.swModuleDetails = new SoftwareModuleDetails(uiDependencies, softwareModuleManagement,
                softwareModuleTypeManagement, getSmMetaDataWindowBuilder());
        this.swModuleDetails.buildDetails();

        final EventLayoutViewAware smLayoutViewAware = new EventLayoutViewAware(EventLayout.SM_LIST, getEventView());
        final EventLayoutViewAware dsLayoutViewAware = new EventLayoutViewAware(EventLayout.DS_LIST, getEventView());
        addEventListener(new FilterChangedListener<>(uiDependencies.getEventBus(), ProxySoftwareModule.class,
                smLayoutViewAware, swModuleGrid.getFilterSupport()));
        addEventListener(new SelectionChangedListener<>(uiDependencies.getEventBus(), dsLayoutViewAware,
                getMasterDsAwareComponents()));
        addEventListener(new SelectionChangedListener<>(uiDependencies.getEventBus(), smLayoutViewAware,
                getMasterSmAwareComponents()));
        addEventListener(new SelectGridEntityListener<>(uiDependencies.getEventBus(), smLayoutViewAware,
                swModuleGrid.getSelectionSupport()));
        addEventListener(new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(), ProxySoftwareModule.class)
                .viewAware(smLayoutViewAware).entityModifiedAwareSupports(getSmModifiedAwareSupports()).build());

        buildLayout(swModuleGridHeader, swModuleGrid, softwareModuleDetailsHeader, swModuleDetails);
    }

    private List<MasterEntityAwareComponent<ProxyDistributionSet>> getMasterDsAwareComponents() {
        return Collections.singletonList(swModuleGrid.getMasterEntitySupport());
    }

    private List<MasterEntityAwareComponent<ProxySoftwareModule>> getMasterSmAwareComponents() {
        return Arrays.asList(softwareModuleDetailsHeader, swModuleDetails);
    }

    private List<EntityModifiedAwareSupport> getSmModifiedAwareSupports() {
        return Arrays.asList(EntityModifiedGridRefreshAwareSupport.of(swModuleGrid::refreshAll),
                EntityModifiedSelectionAwareSupport.of(swModuleGrid.getSelectionSupport(),
                        swModuleGrid::mapIdToProxyEntity));
    }

    @Override
    protected SoftwareModuleGridHeader getSoftwareModuleGridHeader() {
        return swModuleGridHeader;
    }

    @Override
    protected SoftwareModuleGrid getSoftwareModuleGrid() {
        return swModuleGrid;
    }
}
