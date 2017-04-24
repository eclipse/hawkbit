/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.artifacts.smtype;

import org.eclipse.hawkbit.repository.SoftwareManagement;
import org.eclipse.hawkbit.ui.artifacts.event.SMFilterEvent;
import org.eclipse.hawkbit.ui.artifacts.event.SMFilterEvent.SMFilterEventType;
import org.eclipse.hawkbit.ui.artifacts.state.ArtifactUploadState;
import org.eclipse.hawkbit.ui.common.filterlayout.AbstractFilterSingleButtonClick;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.ui.Button;

/**
 * Single button click behavior of filter buttons layout for software module
 * table on the Upload view.
 */
public class SMTypeFilterButtonClick extends AbstractFilterSingleButtonClick {

    private static final long serialVersionUID = 1L;

    private final transient EventBus.UIEventBus eventBus;

    private final ArtifactUploadState artifactUploadState;

    private final transient SoftwareManagement softwareManagement;

    SMTypeFilterButtonClick(final UIEventBus eventBus, final ArtifactUploadState artifactUploadState,
            final SoftwareManagement softwareManagement) {
        this.eventBus = eventBus;
        this.artifactUploadState = artifactUploadState;
        this.softwareManagement = softwareManagement;
    }

    @Override
    protected void filterUnClicked(final Button clickedButton) {
        artifactUploadState.getSoftwareModuleFilters().setSoftwareModuleType(null);
        eventBus.publish(this, new SMFilterEvent(SMFilterEventType.FILTER_BY_TYPE));
    }

    @Override
    protected void filterClicked(final Button clickedButton) {
        softwareManagement.findSoftwareModuleTypeByName(clickedButton.getData().toString())
                .ifPresent(softwareModuleType -> {
                    artifactUploadState.getSoftwareModuleFilters().setSoftwareModuleType(softwareModuleType);
                    eventBus.publish(this, new SMFilterEvent(SMFilterEventType.FILTER_BY_TYPE));
                });
    }

}
