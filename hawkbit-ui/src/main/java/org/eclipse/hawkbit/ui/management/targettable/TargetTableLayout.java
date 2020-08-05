/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettable;

import java.util.concurrent.Executor;

import org.eclipse.hawkbit.dmf.hono.HonoDeviceSync;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.table.AbstractTableLayout;
import org.eclipse.hawkbit.ui.dd.criteria.ManagementViewClientCriterion;
import org.eclipse.hawkbit.ui.management.event.TargetTableEvent;
import org.eclipse.hawkbit.ui.management.event.TargetTableEvent.TargetComponentEvent;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;

/**
 * Target table layout.
 */
public class TargetTableLayout extends AbstractTableLayout<TargetTable> {

    private static final long serialVersionUID = 2248703121998709112L;

    private final transient EventBus.UIEventBus eventBus;

    public TargetTableLayout(final UIEventBus eventBus, final TargetTable targetTable,
            final TargetManagement targetManagement, final EntityFactory entityFactory, final VaadinMessageSource i18n,
            final UINotification uiNotification, final ManagementUIState managementUIState,
            final ManagementViewClientCriterion managementViewClientCriterion,
            final DeploymentManagement deploymentManagement, final UiProperties uiProperties,
            final SpPermissionChecker permissionChecker, final TargetTagManagement tagManagement,
            final DistributionSetManagement distributionSetManagement, final Executor uiExecutor,
            final HonoDeviceSync honoDeviceSync) {
        final TargetMetadataPopupLayout targetMetadataPopupLayout = new TargetMetadataPopupLayout(i18n, uiNotification,
                eventBus, targetManagement, entityFactory, permissionChecker);
        this.eventBus = eventBus;
        TargetDetails targetDetails = new TargetDetails(i18n, eventBus, permissionChecker, managementUIState, uiNotification,
                tagManagement, targetManagement, targetMetadataPopupLayout, deploymentManagement, entityFactory,
                targetTable, honoDeviceSync != null);
        TargetTableHeader targetTableHeader = new TargetTableHeader(i18n, permissionChecker, eventBus, uiNotification,
                managementUIState, managementViewClientCriterion, targetManagement, deploymentManagement, uiProperties,
                entityFactory, uiNotification, tagManagement, distributionSetManagement, uiExecutor, targetTable, honoDeviceSync);

        super.init(i18n, targetTableHeader, targetTable, targetDetails);
    }

    @Override
    protected void publishEvent() {
        eventBus.publish(this, new TargetTableEvent(TargetComponentEvent.SELECT_ALL));
    }

}
