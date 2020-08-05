/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettable;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import org.eclipse.hawkbit.dmf.hono.HonoDeviceSync;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.entity.DistributionSetIdName;
import org.eclipse.hawkbit.ui.common.table.AbstractTable;
import org.eclipse.hawkbit.ui.common.table.AbstractTableHeader;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEventType;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.dd.criteria.ManagementViewClientCriterion;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.management.event.BulkUploadPopupEvent;
import org.eclipse.hawkbit.ui.management.event.BulkUploadValidationMessageEvent;
import org.eclipse.hawkbit.ui.management.event.ManagementUIEvent;
import org.eclipse.hawkbit.ui.management.event.TargetFilterEvent;
import org.eclipse.hawkbit.ui.management.event.TargetTableEvent;
import org.eclipse.hawkbit.ui.management.event.TargetTableEvent.TargetComponentEvent;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPUITargetDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.CollectionUtils;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.TableTransferable;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Target table header layout.
 */
public class TargetTableHeader extends AbstractTableHeader {

    private static final long serialVersionUID = 1L;

    private final UINotification notification;

    private final ManagementViewClientCriterion managementViewClientCriterion;

    private final TargetAddUpdateWindowLayout targetAddUpdateWindow;

    private final TargetBulkUpdateWindowLayout targetBulkUpdateWindow;

    private boolean isComplexFilterViewDisplayed;

    private final transient DistributionSetManagement distributionSetManagement;

    private final transient HonoDeviceSync honoDeviceSync;

    TargetTableHeader(final VaadinMessageSource i18n, final SpPermissionChecker permChecker, final UIEventBus eventBus,
            final UINotification notification, final ManagementUIState managementUIState,
            final ManagementViewClientCriterion managementViewClientCriterion, final TargetManagement targetManagement,
            final DeploymentManagement deploymentManagement, final UiProperties uiproperties,
            final EntityFactory entityFactory, final UINotification uiNotification,
            final TargetTagManagement tagManagement, final DistributionSetManagement distributionSetManagement,
            final Executor uiExecutor, final TargetTable targetTable, final HonoDeviceSync honoDeviceSync) {
        super(i18n, permChecker, eventBus, managementUIState, null, null, honoDeviceSync != null);
        this.notification = notification;
        this.managementViewClientCriterion = managementViewClientCriterion;
        this.targetAddUpdateWindow = new TargetAddUpdateWindowLayout(i18n, targetManagement, eventBus, uiNotification,
                entityFactory, targetTable);
        this.targetBulkUpdateWindow = new TargetBulkUpdateWindowLayout(i18n, targetManagement, eventBus,
                managementUIState, deploymentManagement, uiproperties, permChecker, uiNotification, tagManagement,
                distributionSetManagement, entityFactory, uiExecutor);
        this.distributionSetManagement = distributionSetManagement;
        this.honoDeviceSync = honoDeviceSync;
        onLoadRestoreState();
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final ManagementUIEvent event) {
        if (event == ManagementUIEvent.HIDE_TARGET_TAG_LAYOUT) {
            setFilterButtonsIconVisible(true);
        } else if (event == ManagementUIEvent.SHOW_TARGET_TAG_LAYOUT) {
            setFilterButtonsIconVisible(false);
        } else if (event == ManagementUIEvent.RESET_SIMPLE_FILTERS) {
            UI.getCurrent().access(this::onSimpleFilterReset);
        } else if (event == ManagementUIEvent.RESET_TARGET_FILTER_QUERY) {
            UI.getCurrent().access(this::onCustomFilterReset);
        }
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final BulkUploadPopupEvent event) {
        if (BulkUploadPopupEvent.MAXIMIMIZED == event) {
            targetBulkUpdateWindow.restoreComponentsValue();
            openBulkUploadWindow();
        } else if (BulkUploadPopupEvent.CLOSED == event) {
            UI.getCurrent().access(this::enableBulkUpload);
        }
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final BulkUploadValidationMessageEvent event) {
        this.getUI().access(() -> notification.displayValidationError(event.getValidationErrorMessage()));
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final TargetTableEvent event) {
        if (TargetComponentEvent.BULK_TARGET_CREATED == event.getTargetComponentEvent()) {
            this.getUI().access(() -> targetBulkUpdateWindow.setProgressBarValue(
                    getManagementUIState().getTargetTableFilters().getBulkUpload().getProgressBarCurrentValue()));
        } else if (TargetComponentEvent.BULK_UPLOAD_COMPLETED == event.getTargetComponentEvent()) {
            this.getUI().access(targetBulkUpdateWindow::onUploadCompletion);
        } else if (TargetComponentEvent.BULK_TARGET_UPLOAD_STARTED == event.getTargetComponentEvent()) {
            this.getUI().access(this::onStartOfBulkUpload);
        } else if (TargetComponentEvent.BULK_UPLOAD_PROCESS_STARTED == event.getTargetComponentEvent()) {
            this.getUI().access(() -> targetBulkUpdateWindow.getBulkUploader().getUpload().setEnabled(false));
        }
    }

    private void onStartOfBulkUpload() {
        disableBulkUpload();
        targetBulkUpdateWindow.onStartOfUpload();
    }

    private void onCustomFilterReset() {
        isComplexFilterViewDisplayed = Boolean.FALSE;
        reEnableSearch();
    }

    private void onLoadRestoreState() {
        if (getManagementUIState().isCustomFilterSelected()) {
            onSimpleFilterReset();
        }
    }

    private void onSimpleFilterReset() {
        isComplexFilterViewDisplayed = Boolean.TRUE;
        disableSearch();
        if (isSearchFieldOpen()) {
            resetSearch();
        }
        if (getManagementUIState().getTargetTableFilters().getDistributionSet().isPresent()) {
            closeFilterByDistribution();
        }
    }

    @Override
    protected String getHeaderCaption() {
        return i18n.getMessage("header.target.table");
    }

    @Override
    protected String getSearchBoxId() {
        return UIComponentIdProvider.TARGET_TEXT_FIELD;
    }

    @Override
    protected String getSearchRestIconId() {
        return UIComponentIdProvider.TARGET_TBL_SEARCH_RESET_ID;
    }

    @Override
    protected String getAddIconId() {
        return UIComponentIdProvider.TARGET_TBL_ADD_ICON_ID;
    }

    @Override
    protected String getSyncIconId() {
        return UIComponentIdProvider.TARGET_TBL_SYNC_ICON_ID;
    }

    @Override
    protected String getBulkUploadIconId() {
        return UIComponentIdProvider.TARGET_TBL_BULK_UPLOAD_ICON_ID;
    }

    @Override
    protected String onLoadSearchBoxValue() {
        return getSearchText();
    }

    @Override
    protected String getDropFilterId() {
        return UIComponentIdProvider.TARGET_DROP_FILTER_ICON;
    }

    @Override
    protected String getDropFilterWrapperId() {
        return UIComponentIdProvider.TARGET_FILTER_WRAPPER_ID;
    }

    @Override
    protected boolean hasCreatePermission() {
        return permChecker.hasCreateTargetPermission();
    }

    @Override
    protected boolean isDropHintRequired() {
        return Boolean.TRUE;
    }

    @Override
    protected boolean isDropFilterRequired() {
        return Boolean.TRUE;
    }

    @Override
    protected String getShowFilterButtonLayoutId() {
        return UIComponentIdProvider.SHOW_TARGET_TAGS;
    }

    @Override
    protected void showFilterButtonsLayout() {
        getManagementUIState().setTargetTagFilterClosed(false);
        eventBus.publish(this, ManagementUIEvent.SHOW_TARGET_TAG_LAYOUT);
    }

    @Override
    protected void resetSearchText() {
        if (getManagementUIState().getTargetTableFilters().getSearchText().isPresent()) {
            getManagementUIState().getTargetTableFilters().setSearchText(null);
            eventBus.publish(this, TargetFilterEvent.REMOVE_FILTER_BY_TEXT);
        }
    }

    private String getSearchText() {
        return getManagementUIState().getTargetTableFilters().getSearchText().orElse(null);
    }

    @Override
    protected String getMaxMinIconId() {
        return UIComponentIdProvider.TARGET_MAX_MIN_TABLE_ICON;
    }

    @Override
    public void maximizeTable() {
        getManagementUIState().setTargetTableMaximized(Boolean.TRUE);
        eventBus.publish(this, new TargetTableEvent(BaseEntityEventType.MAXIMIZED));
    }

    @Override
    public void minimizeTable() {
        getManagementUIState().setTargetTableMaximized(Boolean.FALSE);
        eventBus.publish(this, new TargetTableEvent(BaseEntityEventType.MINIMIZED));
    }

    @Override
    public Boolean onLoadIsTableMaximized() {
        return getManagementUIState().isTargetTableMaximized();
    }

    @Override
    public Boolean onLoadIsShowFilterButtonDisplayed() {
        return getManagementUIState().isTargetTagFilterClosed();
    }

    @Override
    protected void searchBy(final String newSearchText) {
        getManagementUIState().getTargetTableFilters().setSearchText(newSearchText);
        eventBus.publish(this, TargetFilterEvent.FILTER_BY_TEXT);
    }

    @Override
    protected void addNewItem(final ClickEvent event) {
        targetAddUpdateWindow.resetComponents();
        final Window addTargetWindow = targetAddUpdateWindow.createNewWindow();
        addTargetWindow.setCaption(i18n.getMessage("caption.create.new", i18n.getMessage("caption.target")));
        UI.getCurrent().addWindow(addTargetWindow);
        addTargetWindow.setVisible(Boolean.TRUE);
    }

    @Override
    protected void syncHono(final ClickEvent event) {
        event.getButton().setEnabled(false);    // Make sure there is only one synchronization process at a time.
        honoDeviceSync.synchronize(true);
        event.getButton().setEnabled(true);
    }

    @Override
    protected void bulkUpload(final ClickEvent event) {
        targetBulkUpdateWindow.resetComponents();
        openBulkUploadWindow();
    }

    private void openBulkUploadWindow() {
        final Window bulkUploadTargetWindow = targetBulkUpdateWindow.getWindow();
        UI.getCurrent().addWindow(bulkUploadTargetWindow);
        bulkUploadTargetWindow.setVisible(true);
    }

    @Override
    protected Boolean isBulkUploadAllowed() {
        return Boolean.TRUE;
    }

    @Override
    protected DropHandler getDropFilterHandler() {
        return new DropHandler() {
            /**
             *
             */
            private static final long serialVersionUID = 1L;

            @Override
            public void drop(final DragAndDropEvent event) {
                filterByDroppedDist(event);
            }

            @Override
            public AcceptCriterion getAcceptCriterion() {
                return managementViewClientCriterion;
            }

        };
    }

    private void filterByDroppedDist(final DragAndDropEvent event) {
        if (doValidations(event)) {
            final TableTransferable tableTransferable = (TableTransferable) event.getTransferable();
            final Table source = tableTransferable.getSourceComponent();
            if (!UIComponentIdProvider.DIST_TABLE_ID.equals(source.getId())) {
                return;
            }
            final Set<Long> distributionIdSet = getDropppedDistributionDetails(tableTransferable);
            if (CollectionUtils.isEmpty(distributionIdSet)) {
                return;
            }
            final Long distributionSetId = distributionIdSet.iterator().next();
            final Optional<DistributionSet> distributionSet = distributionSetManagement.get(distributionSetId);
            if (!distributionSet.isPresent()) {
                notification.displayWarning(i18n.getMessage("distributionset.not.exists"));
                return;
            }
            final DistributionSetIdName distributionSetIdName = new DistributionSetIdName(distributionSet.get());
            getManagementUIState().getTargetTableFilters().setDistributionSet(distributionSetIdName);
            addFilterTextField(distributionSetIdName);
        }
    }

    /**
     * Validation for drag event.
     *
     * @param dragEvent
     * @return
     */
    private Boolean doValidations(final DragAndDropEvent dragEvent) {
        final Component compsource = dragEvent.getTransferable().getSourceComponent();
        Boolean isValid = Boolean.TRUE;
        if (compsource instanceof Table && !isComplexFilterViewDisplayed) {
            final TableTransferable transferable = (TableTransferable) dragEvent.getTransferable();
            final Table source = transferable.getSourceComponent();

            if (!source.getId().equals(UIComponentIdProvider.DIST_TABLE_ID)) {
                notification.displayValidationError(i18n.getMessage(UIMessageIdProvider.MESSAGE_ACTION_NOT_ALLOWED));
                isValid = Boolean.FALSE;
            } else {
                if (getDropppedDistributionDetails(transferable).size() > 1) {
                    notification.displayValidationError(i18n.getMessage("message.onlyone.distribution.dropallowed"));
                    isValid = Boolean.FALSE;
                }
            }
        } else {
            notification.displayValidationError(i18n.getMessage(UIMessageIdProvider.MESSAGE_ACTION_NOT_ALLOWED));
            isValid = Boolean.FALSE;
        }
        return isValid;
    }

    private static Set<Long> getDropppedDistributionDetails(final TableTransferable transferable) {
        final AbstractTable<?> distTable = (AbstractTable<?>) transferable.getSourceComponent();
        return distTable.getSelectedEntitiesByTransferable(transferable);
    }

    private void addFilterTextField(final DistributionSetIdName distributionSetIdName) {
        final Button filterLabelClose = SPUIComponentProvider.getButton("drop.filter.close", "", "", "", true,
                FontAwesome.TIMES_CIRCLE, SPUIButtonStyleNoBorder.class);
        filterLabelClose.addClickListener(clickEvent -> closeFilterByDistribution());
        final Label filteredDistLabel = new Label();
        filteredDistLabel.setStyleName(ValoTheme.LABEL_COLORED + " " + ValoTheme.LABEL_SMALL);
        String name = HawkbitCommonUtil.getDistributionNameAndVersion(distributionSetIdName.getName(),
                distributionSetIdName.getVersion());
        if (name.length() > SPUITargetDefinitions.DISTRIBUTION_NAME_MAX_LENGTH_ALLOWED) {
            name = new StringBuilder(name.substring(0, SPUITargetDefinitions.DISTRIBUTION_NAME_LENGTH_ON_FILTER))
                    .append("...").toString();
        }
        filteredDistLabel.setValue(name);
        filteredDistLabel.setSizeUndefined();
        getFilterDroppedInfo().removeAllComponents();
        getFilterDroppedInfo().setSizeFull();
        getFilterDroppedInfo().addComponent(filteredDistLabel);
        getFilterDroppedInfo().addComponent(filterLabelClose);
        getFilterDroppedInfo().setExpandRatio(filteredDistLabel, 1.0F);
        eventBus.publish(this, TargetFilterEvent.FILTER_BY_DISTRIBUTION);
    }

    private void closeFilterByDistribution() {

        /* Remove filter by distribution information. */
        getFilterDroppedInfo().removeAllComponents();
        getFilterDroppedInfo().setSizeUndefined();
        /* Remove distribution Id from target filter parameters */
        getManagementUIState().getTargetTableFilters().setDistributionSet(null);

        /* Reload the table */
        eventBus.publish(this, TargetFilterEvent.REMOVE_FILTER_BY_DISTRIBUTION);
    }

    @Override
    protected void displayFilterDropedInfoOnLoad() {
        getManagementUIState().getTargetTableFilters().getDistributionSet().ifPresent(this::addFilterTextField);
    }

    @Override
    protected boolean isBulkUploadInProgress() {
        return getManagementUIState().getTargetTableFilters().getBulkUpload().getSucessfulUploadCount() != 0
                || getManagementUIState().getTargetTableFilters().getBulkUpload().getFailedUploadCount() != 0;
    }

    @Override
    protected String getFilterIconStyle() {
        return null;
    }

    @Override
    protected Boolean isAddNewItemAllowed() {
        return Boolean.TRUE;
    }

    @Override
    protected Boolean isHonoSyncAllowed() {
        return Boolean.TRUE;
    }
}
