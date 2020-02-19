/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.dstable;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.DistributionSetTagManagement;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.table.AbstractTableLayout;
import org.eclipse.hawkbit.ui.dd.criteria.ManagementViewClientCriterion;
import org.eclipse.hawkbit.ui.distributions.dstable.DsMetadataPopupLayout;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;

/**
 * Distribution Set table layout which is shown on the Distribution View
 */
public class DistributionTableLayout extends AbstractTableLayout<DistributionTable> {

    private static final long serialVersionUID = 1L;

    private final DistributionTable distributionTable;

    public DistributionTableLayout(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final SpPermissionChecker permissionChecker, final ManagementUIState managementUIState,
            final DistributionSetManagement distributionSetManagement,
            final DistributionSetTypeManagement distributionSetTypeManagement,
            final ManagementViewClientCriterion managementViewClientCriterion, final EntityFactory entityFactory,
            final UINotification notification, final DistributionSetTagManagement distributionSetTagManagement,
            final TargetTagManagement targetTagManagement, final SystemManagement systemManagement,
            final TargetManagement targetManagement, final DeploymentManagement deploymentManagement,
            final TenantConfigurationManagement configManagement, final SystemSecurityContext systemSecurityContext,
            final UiProperties uiProperties) {

        final DistributionAddUpdateWindowLayout distributionAddUpdateWindowLayout = new DistributionAddUpdateWindowLayout(
                i18n, notification, eventBus, distributionSetManagement, distributionSetTypeManagement,
                systemManagement, entityFactory, null, configManagement, systemSecurityContext);

        final DsMetadataPopupLayout dsMetadataPopupLayout = new DsMetadataPopupLayout(i18n, notification, eventBus,
                distributionSetManagement, entityFactory, permissionChecker);

        this.distributionTable = new DistributionTable(eventBus, i18n, permissionChecker, notification,
                managementUIState, managementViewClientCriterion, targetManagement, distributionSetManagement,
                deploymentManagement, targetTagManagement, uiProperties, configManagement, systemSecurityContext);

        super.init(i18n, new DistributionTableHeader(i18n, permissionChecker, eventBus, managementUIState),
                distributionTable,
                new DistributionDetails(i18n, eventBus, permissionChecker, managementUIState, distributionSetManagement,
                        dsMetadataPopupLayout, notification, distributionSetTagManagement,
                        distributionAddUpdateWindowLayout, configManagement, systemSecurityContext));
    }

    public DistributionTable getDistributionTable() {
        return distributionTable;
    }

}
