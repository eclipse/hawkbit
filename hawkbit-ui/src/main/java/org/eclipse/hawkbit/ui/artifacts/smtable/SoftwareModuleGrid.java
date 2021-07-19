/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.artifacts.smtable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.ui.artifacts.upload.FileUploadProgress;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.filters.SwFilterParams;
import org.eclipse.hawkbit.ui.common.data.mappers.AssignedSoftwareModuleToProxyMapper;
import org.eclipse.hawkbit.ui.common.data.mappers.SoftwareModuleToProxyMapper;
import org.eclipse.hawkbit.ui.common.data.providers.SoftwareModuleDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySoftwareModule;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload.EntityModifiedEventType;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.support.DeleteSupport;
import org.eclipse.hawkbit.ui.common.grid.support.DragAndDropSupport;
import org.eclipse.hawkbit.ui.common.grid.support.FilterSupport;
import org.eclipse.hawkbit.ui.common.grid.support.MasterEntitySupport;
import org.eclipse.hawkbit.ui.common.grid.support.SelectionSupport;
import org.eclipse.hawkbit.ui.common.state.GridLayoutUiState;
import org.eclipse.hawkbit.ui.common.state.TypeFilterLayoutUiState;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;

import com.vaadin.ui.Button;

/**
 * Software Module grid.
 */
public class SoftwareModuleGrid extends AbstractGrid<ProxySoftwareModule, SwFilterParams> {
    private static final long serialVersionUID = 1L;

    private static final String SM_NAME_ID = "smName";
    private static final String SM_VERSION_ID = "smVersion";
    private static final String SM_DESC_ID = "smDescription";
    private static final String SM_VENDOR_ID = "smVendor";
    private static final String SM_DELETE_BUTTON_ID = "smDeleteButton";

    private final UINotification notification;

    private final TypeFilterLayoutUiState smTypeFilterLayoutUiState;
    private final GridLayoutUiState smGridLayoutUiState;

    private final transient SoftwareModuleManagement softwareModuleManagement;
    private final transient SoftwareModuleToProxyMapper softwareModuleToProxyMapper;

    private final transient DeleteSupport<ProxySoftwareModule> swModuleDeleteSupport;
    private transient MasterEntitySupport<ProxyDistributionSet> masterEntitySupport;

    private final Map<Long, Integer> numberOfArtifactUploadsForSm;

    /**
     * Constructor for SoftwareModuleGrid
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param smTypeFilterLayoutUiState
     *            TypeFilterLayoutUiState
     * @param smGridLayoutUiState
     *            GridLayoutUiState
     * @param softwareModuleManagement
     *            SoftwareModuleManagement
     * @param view
     *            EventView
     */
    public SoftwareModuleGrid(final CommonUiDependencies uiDependencies,
            final TypeFilterLayoutUiState smTypeFilterLayoutUiState, final GridLayoutUiState smGridLayoutUiState,
            final SoftwareModuleManagement softwareModuleManagement, final EventView view) {
        super(uiDependencies.getI18n(), uiDependencies.getEventBus(), uiDependencies.getPermChecker());

        this.smTypeFilterLayoutUiState = smTypeFilterLayoutUiState;
        this.smGridLayoutUiState = smGridLayoutUiState;
        this.notification = uiDependencies.getUiNotification();
        this.softwareModuleManagement = softwareModuleManagement;
        this.softwareModuleToProxyMapper = new SoftwareModuleToProxyMapper();

        setSelectionSupport(new SelectionSupport<>(this, eventBus, EventLayout.SM_LIST, view, this::mapIdToProxyEntity,
                this::getSelectedEntityIdFromUiState, this::setSelectedEntityIdToUiState));
        if (smGridLayoutUiState.isMaximized()) {
            getSelectionSupport().disableSelection();
        } else {
            getSelectionSupport().enableMultiSelection();
        }

        this.swModuleDeleteSupport = new DeleteSupport<>(this, i18n, notification, "caption.software.module",
                "caption.softwaremodules", ProxySoftwareModule::getNameAndVersion, this::deleteSoftwareModules,
                UIComponentIdProvider.SM_DELETE_CONFIRMATION_DIALOG);

        setFilterSupport(new FilterSupport<>(
                new SoftwareModuleDataProvider(softwareModuleManagement,
                        new AssignedSoftwareModuleToProxyMapper(softwareModuleToProxyMapper)),
                SwFilterParams::new, this::afterFilterRefresh));
        initFilterMappings();
        getFilterSupport().setFilter(new SwFilterParams());

        this.numberOfArtifactUploadsForSm = new HashMap<>();
    }

    private void afterFilterRefresh() {
        // keep selection on master distribution set change as it does not
        // filter out any software module entries, only sorts them
        if (masterFilterHasNotChanged()) {
            getSelectionSupport().deselectAll();
        }
    }

    private boolean masterFilterHasNotChanged() {
        final Long filterDsId = getFilter().map(SwFilterParams::getLastSelectedDistributionId).orElse(null);
        final Long masterDsId = getMasterEntitySupport().getMasterId();

        return Objects.equals(filterDsId, masterDsId);
    }

    private void initFilterMappings() {
        getFilterSupport().addMapping(FilterType.SEARCH, SwFilterParams::setSearchText,
                smGridLayoutUiState.getSearchFilter());
        getFilterSupport().addMapping(FilterType.TYPE, SwFilterParams::setSoftwareModuleTypeId,
                smTypeFilterLayoutUiState.getClickedTypeId());
    }

    /**
     * Initial method of grid and set style name
     */
    @Override
    public void init() {
        super.init();

        addStyleName("grid-row-border");
    }

    /**
     * Map SoftwareModule to Proxy type
     *
     * @param entityId
     *            EntityId Long type
     *
     * @return ProxySoftwareModule
     */
    public Optional<ProxySoftwareModule> mapIdToProxyEntity(final long entityId) {
        return softwareModuleManagement.get(entityId).map(softwareModuleToProxyMapper::map);
    }

    private Long getSelectedEntityIdFromUiState() {
        return smGridLayoutUiState.getSelectedEntityId();
    }

    private void setSelectedEntityIdToUiState(final Long entityId) {
        smGridLayoutUiState.setSelectedEntityId(entityId);
    }

    private boolean deleteSoftwareModules(final Collection<ProxySoftwareModule> swModulesToBeDeleted) {
        final Collection<Long> swModuleToBeDeletedIds = swModulesToBeDeleted.stream()
                .map(ProxyIdentifiableEntity::getId).collect(Collectors.toList());
        if (isUploadInProgressForSoftwareModule(swModuleToBeDeletedIds)) {
            notification.displayValidationError(i18n.getMessage("message.error.swModule.notDeleted"));

            return false;
        }

        softwareModuleManagement.delete(swModuleToBeDeletedIds);

        eventBus.publish(EventTopics.ENTITY_MODIFIED, this, new EntityModifiedEventPayload(
                EntityModifiedEventType.ENTITY_REMOVED, ProxySoftwareModule.class, swModuleToBeDeletedIds));

        return true;
    }

    private boolean isUploadInProgressForSoftwareModule(final Collection<Long> swModuleToBeDeletedIds) {
        return swModuleToBeDeletedIds.stream().anyMatch(
                smId -> numberOfArtifactUploadsForSm.containsKey(smId) && numberOfArtifactUploadsForSm.get(smId) > 0);
    }

    /**
     * Artifacts upload process
     *
     * @param fileUploadProgress
     *            FileUploadProgress
     */
    public void onUploadChanged(final FileUploadProgress fileUploadProgress) {
        final FileUploadProgress.FileUploadStatus uploadProgressEventType = fileUploadProgress.getFileUploadStatus();
        final Long fileUploadSmId = fileUploadProgress.getFileUploadId().getSoftwareModuleId();

        if (fileUploadSmId == null) {
            return;
        }

        if (FileUploadProgress.FileUploadStatus.UPLOAD_STARTED == uploadProgressEventType) {
            numberOfArtifactUploadsForSm.merge(fileUploadSmId, 1, Integer::sum);
        }

        if (FileUploadProgress.FileUploadStatus.UPLOAD_FINISHED == uploadProgressEventType) {
            numberOfArtifactUploadsForSm.computeIfPresent(fileUploadSmId, (smId, oldCount) -> {
                final Integer newCount = oldCount - 1;
                return newCount.equals(0) ? null : newCount;
            });
        }
    }

    /**
     * Drag and drop for grid elements
     */
    public void addDragAndDropSupport() {
        setDragAndDropSupportSupport(
                new DragAndDropSupport<>(this, i18n, notification, Collections.emptyMap(), eventBus));
        if (!smGridLayoutUiState.isMaximized()) {
            getDragAndDropSupportSupport().addDragSource();
        }
    }

    /**
     * Add master filter type
     */
    public void addMasterSupport() {
        getFilterSupport().addMapping(FilterType.MASTER, SwFilterParams::setLastSelectedDistributionId);

        masterEntitySupport = new MasterEntitySupport<>(getFilterSupport());

        initMasterDsStyleGenerator();
    }

    private void initMasterDsStyleGenerator() {
        setStyleGenerator(sm -> {
            if (masterEntitySupport.getMasterId() == null || !sm.isAssigned()) {
                return null;
            }

            return String.join("-", UIComponentIdProvider.SM_TYPE_COLOR_CLASS,
                    String.valueOf(sm.getTypeInfo().getId()));
        });
    }

    /**
     * @return gridId of software module grid
     */
    @Override
    public String getGridId() {
        return UIComponentIdProvider.SOFTWARE_MODULE_TABLE;
    }

    /**
     * Add columns to Grid
     */
    @Override
    public void addColumns() {
        addNameColumn().setExpandRatio(2);
        addVersionColumn();
        addDeleteColumn();
    }

    private Column<ProxySoftwareModule, String> addNameColumn() {
        return GridComponentBuilder.addNameColumn(this, i18n, SM_NAME_ID);
    }

    private Column<ProxySoftwareModule, String> addVersionColumn() {
        return GridComponentBuilder.addVersionColumn(this, i18n, ProxySoftwareModule::getVersion, SM_VERSION_ID);
    }

    private Column<ProxySoftwareModule, Button> addDeleteColumn() {
        return GridComponentBuilder.addDeleteColumn(this, i18n, SM_DELETE_BUTTON_ID, swModuleDeleteSupport,
                UIComponentIdProvider.SM_DELET_ICON, e -> permissionChecker.hasDeleteRepositoryPermission());
    }

    @Override
    protected void addMaxColumns() {
        addNameColumn().setExpandRatio(7);
        addVersionColumn();
        addDescriptionColumn().setExpandRatio(5);
        addVendorColumn();
        GridComponentBuilder.addCreatedAndModifiedColumns(this, i18n);
        addDeleteColumn();

        getColumns().forEach(column -> column.setHidable(true));
    }

    private Column<ProxySoftwareModule, String> addDescriptionColumn() {
        return GridComponentBuilder.addDescriptionColumn(this, i18n, SM_DESC_ID);
    }

    private Column<ProxySoftwareModule, String> addVendorColumn() {
        return GridComponentBuilder.addColumn(this, ProxySoftwareModule::getVendor).setId(SM_VENDOR_ID)
                .setCaption(i18n.getMessage("header.vendor"));
    }

    /**
     * @return ProxyDistributionSet of master entity type
     */
    public MasterEntitySupport<ProxyDistributionSet> getMasterEntitySupport() {
        return masterEntitySupport;
    }
}
