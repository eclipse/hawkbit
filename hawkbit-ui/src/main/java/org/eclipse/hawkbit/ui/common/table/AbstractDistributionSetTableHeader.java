/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.table;

import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.artifacts.state.ArtifactUploadState;
import org.eclipse.hawkbit.ui.distributions.state.ManageDistUIState;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.event.dd.DropHandler;
import com.vaadin.ui.Button.ClickEvent;

/**
 * Abstract class which contains common method implementations for the header of
 * Distribution Set Table elements
 *
 */
public abstract class AbstractDistributionSetTableHeader extends AbstractTableHeader {

    private static final long serialVersionUID = 1L;

    protected AbstractDistributionSetTableHeader(final VaadinMessageSource i18n, final SpPermissionChecker permChecker,
            final UIEventBus eventbus, final ManagementUIState managementUIState,
            final ManageDistUIState manageDistUIstate, final ArtifactUploadState artifactUploadState) {
        super(i18n, permChecker, eventbus, managementUIState, manageDistUIstate, artifactUploadState, false);
    }

    @Override
    protected String getHeaderCaption() {
        return i18n.getMessage("header.dist.table");
    }

    @Override
    protected String getSearchBoxId() {
        return UIComponentIdProvider.DIST_SEARCH_TEXTFIELD;
    }

    @Override
    protected String getSearchRestIconId() {
        return UIComponentIdProvider.DIST_SEARCH_ICON;
    }

    @Override
    protected String getAddIconId() {
        return UIComponentIdProvider.DIST_ADD_ICON;
    }

    @Override
    protected String getSyncIconId() {
        return null;
    }

    @Override
    protected boolean isDropHintRequired() {
        return true;
    }

    @Override
    protected boolean hasCreatePermission() {
        return permChecker.hasCreateRepositoryPermission();
    }

    @Override
    protected String getShowFilterButtonLayoutId() {
        return UIComponentIdProvider.SHOW_DIST_TAG_ICON;
    }

    @Override
    protected String getMaxMinIconId() {
        return UIComponentIdProvider.DS_MAX_MIN_TABLE_ICON;
    }

    @Override
    protected String getBulkUploadIconId() {
        return null;
    }

    @Override
    protected Boolean isBulkUploadAllowed() {
        return Boolean.FALSE;
    }

    @Override
    protected void bulkUpload(final ClickEvent event) {
        // No implementation as no bulk upload is supported except for
        // targetTable.
    }

    @Override
    protected boolean isBulkUploadInProgress() {
        return false;
    }

    @Override
    protected String getDropFilterId() {
        return null;
    }

    @Override
    protected String getFilterIconStyle() {
        return null;
    }

    @Override
    protected String getDropFilterWrapperId() {
        return null;
    }

    @Override
    protected DropHandler getDropFilterHandler() {
        return null;
    }

    @Override
    protected boolean isDropFilterRequired() {
        return false;
    }

}
