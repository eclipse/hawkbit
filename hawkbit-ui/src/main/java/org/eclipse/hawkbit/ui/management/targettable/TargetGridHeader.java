/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettable;

import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.event.BulkUploadEventPayload;
import org.eclipse.hawkbit.ui.common.event.BulkUploadEventPayload.BulkUploadState;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractEntityGridHeader;
import org.eclipse.hawkbit.ui.common.grid.header.support.BulkUploadHeaderSupport;
import org.eclipse.hawkbit.ui.common.grid.header.support.DistributionSetFilterDropAreaSupport;
import org.eclipse.hawkbit.ui.management.bulkupload.BulkUploadWindowBuilder;
import org.eclipse.hawkbit.ui.management.bulkupload.TargetBulkUpdateWindowLayout;
import org.eclipse.hawkbit.ui.management.bulkupload.TargetBulkUploadUiState;
import org.eclipse.hawkbit.ui.management.targettag.filter.TargetTagFilterLayoutUiState;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;

/**
 * Target table header layout.
 */
public class TargetGridHeader extends AbstractEntityGridHeader {
    private static final long serialVersionUID = 1L;

    private static final String TARGET_TABLE_HEADER = "header.target.table";
    private static final String TARGET_CAPTION = "caption.target";

    private final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState;
    private final TargetBulkUploadUiState targetBulkUploadUiState;
    private final TargetGridLayoutUiState targetGridLayoutUiState;

    private final transient BulkUploadWindowBuilder bulkUploadWindowBuilder;

    private final transient BulkUploadHeaderSupport bulkUploadHeaderSupport;
    private final transient DistributionSetFilterDropAreaSupport distributionSetFilterDropAreaSupport;

    TargetGridHeader(final CommonUiDependencies uiDependencies, final TargetWindowBuilder targetWindowBuilder,
            final BulkUploadWindowBuilder bulkUploadWindowBuilder,
            final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState,
            final TargetGridLayoutUiState targetGridLayoutUiState,
            final TargetBulkUploadUiState targetBulkUploadUiState) {
        super(uiDependencies, targetTagFilterLayoutUiState, targetGridLayoutUiState, EventLayout.TARGET_TAG_FILTER,
                EventView.DEPLOYMENT);

        this.targetTagFilterLayoutUiState = targetTagFilterLayoutUiState;
        this.targetGridLayoutUiState = targetGridLayoutUiState;
        this.targetBulkUploadUiState = targetBulkUploadUiState;

        this.bulkUploadWindowBuilder = bulkUploadWindowBuilder;

        addAddHeaderSupport(targetWindowBuilder);

        if (hasCreatePermission()) {
            this.bulkUploadHeaderSupport = new BulkUploadHeaderSupport(i18n, this::showBulkUploadWindow,
                    this::isBulkUploadInProgress, this::onLoadIsTableMaximized);
            addHeaderSupport(bulkUploadHeaderSupport, getHeaderSupportsSize() - 1);
        } else {
            this.bulkUploadHeaderSupport = null;
        }

        // DistributionSetFilterDropArea is only available in TargetTableHeader
        this.distributionSetFilterDropAreaSupport = new DistributionSetFilterDropAreaSupport(i18n, eventBus,
                uiDependencies.getUiNotification(), targetGridLayoutUiState);
    }

    /**
     * Add distribution set filter drop area
     */
    public void addDsDropArea() {
        final Component distributionSetFilterDropArea = distributionSetFilterDropAreaSupport.getHeaderComponent();
        addComponent(distributionSetFilterDropArea);
        setComponentAlignment(distributionSetFilterDropArea, Alignment.TOP_CENTER);
    }

    @Override
    protected String getCaptionMsg() {
        return TARGET_TABLE_HEADER;
    }

    @Override
    protected String getSearchFieldId() {
        return UIComponentIdProvider.TARGET_TEXT_FIELD;
    }

    @Override
    protected String getSearchResetIconId() {
        return UIComponentIdProvider.TARGET_TBL_SEARCH_RESET_ID;
    }

    @Override
    protected Class<? extends ProxyIdentifiableEntity> getEntityType() {
        return ProxyTarget.class;
    }

    @Override
    protected String getFilterButtonsIconId() {
        return UIComponentIdProvider.SHOW_TARGET_TAGS;
    }

    @Override
    protected String getMaxMinIconId() {
        return UIComponentIdProvider.TARGET_MAX_MIN_TABLE_ICON;
    }

    @Override
    protected EventLayout getLayout() {
        return EventLayout.TARGET_LIST;
    }

    @Override
    protected boolean hasCreatePermission() {
        return permChecker.hasCreateTargetPermission();
    }

    @Override
    protected String getAddIconId() {
        return UIComponentIdProvider.TARGET_TBL_ADD_ICON_ID;
    }

    @Override
    protected String getAddWindowCaptionMsg() {
        return TARGET_CAPTION;
    }

    @Override
    public void restoreState() {
        super.restoreState();

        if (targetTagFilterLayoutUiState.isCustomFilterTabSelected()) {
            onSimpleFilterReset();
        }

        if (isBulkUploadInProgress()) {
            bulkUploadWindowBuilder.restoreState();
        }

        if (targetGridLayoutUiState.getFilterDsInfo() != null) {
            distributionSetFilterDropAreaSupport.restoreState();
        }
    }

    /**
     * Reset the distribution set filer drop area support
     */
    public void onSimpleFilterReset() {
        getSearchHeaderSupport().resetSearch();
        getSearchHeaderSupport().disableSearch();

        distributionSetFilterDropAreaSupport.reset();
    }

    @Override
    protected void maximizeTable() {
        super.maximizeTable();

        if (bulkUploadHeaderSupport != null) {
            bulkUploadHeaderSupport.hideBulkUpload();
        }
    }

    @Override
    protected void minimizeTable() {
        super.minimizeTable();

        if (bulkUploadHeaderSupport != null) {
            bulkUploadHeaderSupport.showBulkUpload();
        }
    }

    private void showBulkUploadWindow() {
        final Window bulkUploadTargetWindow = bulkUploadWindowBuilder.getWindowForTargetBulkUpload();
        UI.getCurrent().addWindow(bulkUploadTargetWindow);
        bulkUploadTargetWindow.setVisible(true);
    }

    private boolean isBulkUploadInProgress() {
        return targetBulkUploadUiState.isInProgress();
    }

    /**
     * Perform tasks on bulk upload state
     *
     * @param eventPayload
     *            BulkUploadEventPayload
     */
    public void onBulkUploadChanged(final BulkUploadEventPayload eventPayload) {
        bulkUploadWindowBuilder.getLayout()
                .ifPresent(layout -> onBulkUploadStateChanged(layout, eventPayload.getBulkUploadState(),
                        eventPayload.getFailureReason(), eventPayload.getBulkUploadProgress(),
                        eventPayload.getSuccessBulkUploadCount(), eventPayload.getFailBulkUploadCount()));
    }

    private void onBulkUploadStateChanged(final TargetBulkUpdateWindowLayout layout, final BulkUploadState state,
            final String failureReason, final float progress, final int successCount, final int failCount) {
        switch (state) {
        case UPLOAD_STARTED:
            bulkUploadHeaderSupport.showProgressIndicator();
            layout.onStartOfUpload();
            break;
        case UPLOAD_FAILED:
            bulkUploadHeaderSupport.hideProgressIndicator();
            layout.onUploadFailure(failureReason);
            break;
        case TARGET_PROVISIONING_STARTED:
            layout.onStartOfProvisioning();
            break;
        case TARGET_PROVISIONING_PROGRESS_UPDATED:
            layout.setProgressBarValue(progress);
            break;
        case TAGS_AND_DS_ASSIGNMENT_STARTED:
            layout.onStartOfAssignment();
            break;
        case TAGS_AND_DS_ASSIGNMENT_FAILED:
            layout.onAssignmentFailure(failureReason);
            break;
        case BULK_UPLOAD_COMPLETED:
            bulkUploadHeaderSupport.hideProgressIndicator();
            layout.onUploadCompletion(successCount, failCount);
            break;
        }
    }

    /**
     * Hides progress indicator in case no bulk upload is running.
     */
    public void checkBulkUpload() {
        if (bulkUploadHeaderSupport != null && !isBulkUploadInProgress()) {
            bulkUploadHeaderSupport.hideProgressIndicator();
        }
    }

    /**
     * Enable search icon in the search header
     */
    public void enableSearchIcon() {
        getSearchHeaderSupport().enableSearch();
    }
}
