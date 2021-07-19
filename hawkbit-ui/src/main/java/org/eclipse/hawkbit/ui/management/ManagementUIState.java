/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management;

import java.io.Serializable;

import javax.annotation.PreDestroy;

import org.eclipse.hawkbit.ui.common.layout.listener.BulkUploadChangedListener;
import org.eclipse.hawkbit.ui.common.state.TagFilterLayoutUiState;
import org.eclipse.hawkbit.ui.management.actionhistory.ActionHistoryGridLayoutUiState;
import org.eclipse.hawkbit.ui.management.bulkupload.TargetBulkUploadUiState;
import org.eclipse.hawkbit.ui.management.dstable.DistributionGridLayoutUiState;
import org.eclipse.hawkbit.ui.management.targettable.TargetGridLayoutUiState;
import org.eclipse.hawkbit.ui.management.targettag.filter.TargetTagFilterLayoutUiState;
import org.vaadin.spring.events.EventBus.SessionEventBus;

import com.vaadin.spring.annotation.SpringComponent;
import com.vaadin.spring.annotation.VaadinSessionScope;

/**
 * User action on management UI.
 */
@VaadinSessionScope
@SpringComponent
public class ManagementUIState implements Serializable {
    private static final long serialVersionUID = 1L;

    private final transient BulkUploadChangedListener bulkUploadListener;

    private final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState;
    private final TargetGridLayoutUiState targetGridLayoutUiState;
    private final TargetBulkUploadUiState targetBulkUploadUiState;
    private final DistributionGridLayoutUiState distributionGridLayoutUiState;
    private final TagFilterLayoutUiState distributionTagLayoutUiState;
    private final ActionHistoryGridLayoutUiState actionHistoryGridLayoutUiState;

    ManagementUIState(final SessionEventBus sessionEventBus) {
        this.targetTagFilterLayoutUiState = new TargetTagFilterLayoutUiState();
        this.targetGridLayoutUiState = new TargetGridLayoutUiState();
        this.targetBulkUploadUiState = new TargetBulkUploadUiState();
        this.distributionGridLayoutUiState = new DistributionGridLayoutUiState();
        this.distributionTagLayoutUiState = new TagFilterLayoutUiState();
        this.actionHistoryGridLayoutUiState = new ActionHistoryGridLayoutUiState();

        this.bulkUploadListener = new BulkUploadChangedListener(sessionEventBus,
                targetBulkUploadUiState::onBulkUploadChanged);

        init();
    }

    private void init() {
        distributionTagLayoutUiState.setHidden(true);
        bulkUploadListener.subscribe();
    }

    /**
     * @return Target tag filter layout ui state
     */
    public TargetTagFilterLayoutUiState getTargetTagFilterLayoutUiState() {
        return targetTagFilterLayoutUiState;
    }

    /**
     * @return Target grid layout ui state
     */
    public TargetGridLayoutUiState getTargetGridLayoutUiState() {
        return targetGridLayoutUiState;
    }

    /**
     * @return Distribution grid layout ui state
     */
    public DistributionGridLayoutUiState getDistributionGridLayoutUiState() {
        return distributionGridLayoutUiState;
    }

    /**
     * @return Target filter layout ui state
     */
    public TagFilterLayoutUiState getDistributionTagLayoutUiState() {
        return distributionTagLayoutUiState;
    }

    /**
     * @return Action history grid layout ui state
     */
    public ActionHistoryGridLayoutUiState getActionHistoryGridLayoutUiState() {
        return actionHistoryGridLayoutUiState;
    }

    /**
     * @return Target bulk upload ui state
     */
    public TargetBulkUploadUiState getTargetBulkUploadUiState() {
        return targetBulkUploadUiState;
    }

    @PreDestroy
    public void destroy() {
        bulkUploadListener.unsubscribe();
    }
}
