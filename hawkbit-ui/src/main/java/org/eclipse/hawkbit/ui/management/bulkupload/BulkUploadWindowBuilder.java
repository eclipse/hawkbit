/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.bulkupload;

import java.util.Optional;
import java.util.concurrent.Executor;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.builder.WindowBuilder;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.ui.Window;

/**
 * Builder for bulk upload window
 */
public class BulkUploadWindowBuilder {
    private final UiProperties uiproperties;
    private final Executor uiExecutor;
    private final TargetManagement targetManagement;
    private final DeploymentManagement deploymentManagement;
    private final TargetTagManagement tagManagement;
    private final DistributionSetManagement distributionSetManagement;
    private final CommonUiDependencies uiDependencies;

    private final TargetBulkUploadUiState targetBulkUploadUiState;

    private TargetBulkUpdateWindowLayout targetBulkUpdateWindowLayout;

    /**
     * Constructor for BulkUploadWindowBuilder
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param uiproperties
     *            UiProperties
     * @param uiExecutor
     *            Executor
     * @param targetManagement
     *            TargetManagement
     * @param deploymentManagement
     *            DeploymentManagement
     * @param tagManagement
     *            TargetTagManagement
     * @param distributionSetManagement
     *            DistributionSetManagement
     * @param targetBulkUploadUiState
     *            TargetBulkUploadUiState
     */
    public BulkUploadWindowBuilder(final CommonUiDependencies uiDependencies, final UiProperties uiproperties,
            final Executor uiExecutor, final TargetManagement targetManagement,
            final DeploymentManagement deploymentManagement, final TargetTagManagement tagManagement,
            final DistributionSetManagement distributionSetManagement,
            final TargetBulkUploadUiState targetBulkUploadUiState) {
        this.uiDependencies = uiDependencies;
        this.uiproperties = uiproperties;
        this.uiExecutor = uiExecutor;
        this.targetManagement = targetManagement;
        this.deploymentManagement = deploymentManagement;
        this.tagManagement = tagManagement;
        this.distributionSetManagement = distributionSetManagement;
        this.targetBulkUploadUiState = targetBulkUploadUiState;
    }

    /**
     * @return Target bulk upload window
     */
    public Window getWindowForTargetBulkUpload() {
        if (!targetBulkUploadUiState.isInProgress() || targetBulkUpdateWindowLayout == null) {
            targetBulkUpdateWindowLayout = new TargetBulkUpdateWindowLayout(uiDependencies, targetManagement,
                    deploymentManagement, tagManagement, distributionSetManagement, uiproperties, uiExecutor,
                    targetBulkUploadUiState);
            targetBulkUpdateWindowLayout.clearUiState();
        }

        final Window bulkUploadWindow = new WindowBuilder(SPUIDefinitions.CREATE_UPDATE_WINDOW)
                .id(UIComponentIdProvider.BULK_UPLOAD_POPUP_ID).caption("").content(targetBulkUpdateWindowLayout)
                .buildWindow();
        bulkUploadWindow.addStyleName("bulk-upload-window");

        targetBulkUpdateWindowLayout.setCloseCallback(() -> closePopup(bulkUploadWindow));

        return bulkUploadWindow;
    }

    private void closePopup(final Window bulkUploadWindow) {
        if (!targetBulkUploadUiState.isInProgress() && targetBulkUpdateWindowLayout != null) {
            targetBulkUpdateWindowLayout.clearUiState();
            targetBulkUpdateWindowLayout = null;
        }
        bulkUploadWindow.close();
    }

    /**
     * Restore target bulk update window layout
     */
    public void restoreState() {
        targetBulkUpdateWindowLayout = new TargetBulkUpdateWindowLayout(uiDependencies, targetManagement,
                deploymentManagement, tagManagement, distributionSetManagement, uiproperties, uiExecutor,
                targetBulkUploadUiState);
        targetBulkUpdateWindowLayout.restoreComponentsValue();
    }

    /**
     * @return Target bulk update window layout
     */
    public Optional<TargetBulkUpdateWindowLayout> getLayout() {
        return Optional.ofNullable(targetBulkUpdateWindowLayout);
    }
}
