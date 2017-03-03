/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.dstable;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TagManagement;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.detailslayout.AbstractDistributionSetDetails;
import org.eclipse.hawkbit.ui.common.detailslayout.SoftwareModuleDetailsTable;
import org.eclipse.hawkbit.ui.distributions.dstable.DsMetadataPopupLayout;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.I18N;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.ui.VerticalLayout;

/**
 * Distribution set details layout.
 */
public class DistributionDetails extends AbstractDistributionSetDetails {

    private static final long serialVersionUID = 1L;

    DistributionDetails(final I18N i18n, final UIEventBus eventBus, final SpPermissionChecker permissionChecker,
            final ManagementUIState managementUIState, final DistributionSetManagement distributionSetManagement,
            final DsMetadataPopupLayout dsMetadataPopupLayout, final EntityFactory entityFactory,
            final UINotification uiNotification, final TagManagement tagManagement,
            final DistributionAddUpdateWindowLayout distributionAddUpdateWindowLayout) {
        super(i18n, eventBus, permissionChecker, managementUIState, distributionAddUpdateWindowLayout,
                distributionSetManagement, dsMetadataPopupLayout, entityFactory, uiNotification, tagManagement);

        setSoftwareModuleTable(
                new SoftwareModuleDetailsTable(i18n, false, permissionChecker, null, null, null, uiNotification));

        addTabs(detailsTab);
        restoreState();
    }

    @Override
    protected Boolean onLoadIsTableRowSelected() {
        return !(managementUIState.getSelectedDsIdName().isPresent()
                && managementUIState.getSelectedDsIdName().get().isEmpty());
    }

    @Override
    protected Boolean onLoadIsTableMaximized() {
        return managementUIState.isDsTableMaximized();
    }

    @Override
    protected void populateDetailsWidget() {
        populateModule();
        populateDetails();
        populateMetadataDetails();
    }

    @Override
    protected VerticalLayout createTagsLayout() {
        super.createTagsLayout();
        getTagsLayout().addComponent(getDistributionTagToken().getTokenField());
        return getTagsLayout();
    }

}
