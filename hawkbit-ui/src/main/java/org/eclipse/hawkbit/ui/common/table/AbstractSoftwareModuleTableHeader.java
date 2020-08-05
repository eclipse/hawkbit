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
import org.eclipse.hawkbit.ui.artifacts.smtable.SoftwareModuleAddUpdateWindow;
import org.eclipse.hawkbit.ui.artifacts.state.ArtifactUploadState;
import org.eclipse.hawkbit.ui.distributions.state.ManageDistUIState;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.event.dd.DropHandler;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;

/**
 * Abstract class which contains common method implementations for the header of
 * Software Module Table elements
 *
 */
public abstract class AbstractSoftwareModuleTableHeader extends AbstractTableHeader {

    private static final long serialVersionUID = 1L;

    private final SoftwareModuleAddUpdateWindow softwareModuleAddUpdateWindow;

    protected AbstractSoftwareModuleTableHeader(final VaadinMessageSource i18n, final SpPermissionChecker permChecker,
            final UIEventBus eventbus, final ManagementUIState managementUIState,
            final ManageDistUIState manageDistUIstate, final ArtifactUploadState artifactUploadState,
            final SoftwareModuleAddUpdateWindow softwareModuleAddUpdateWindow) {
        super(i18n, permChecker, eventbus, managementUIState, manageDistUIstate, artifactUploadState, false);
        this.softwareModuleAddUpdateWindow = softwareModuleAddUpdateWindow;
    }

    @Override
    protected String getHeaderCaption() {
        return i18n.getMessage("upload.swModuleTable.header");
    }

    @Override
    protected String getSearchBoxId() {
        return UIComponentIdProvider.SW_MODULE_SEARCH_TEXT_FIELD;
    }

    @Override
    protected String getSearchRestIconId() {
        return UIComponentIdProvider.SW_MODULE_SEARCH_RESET_ICON;
    }

    @Override
    protected String getAddIconId() {
        return UIComponentIdProvider.SW_MODULE_ADD_BUTTON;
    }

    @Override
    protected String getSyncIconId() {
        return null;
    }

    @Override
    protected String getShowFilterButtonLayoutId() {
        return "show.type.icon";
    }

    @Override
    protected String getMaxMinIconId() {
        return UIComponentIdProvider.SW_MAX_MIN_TABLE_ICON;
    }

    @Override
    protected Boolean isAddNewItemAllowed() {
        return Boolean.TRUE;
    }

    @Override
    protected Boolean isHonoSyncAllowed() {
        return Boolean.FALSE;
    }

    @Override
    protected void addNewItem(final ClickEvent event) {
        final Window addSoftwareModule = softwareModuleAddUpdateWindow.createAddSoftwareModuleWindow();
        addSoftwareModule.setCaption(i18n.getMessage("caption.create.new", i18n.getMessage("caption.software.module")));
        UI.getCurrent().addWindow(addSoftwareModule);
        addSoftwareModule.setVisible(Boolean.TRUE);
    }

    @Override
    protected void syncHono(final ClickEvent event) {
        // do nothing
    }

    @Override
    protected boolean hasCreatePermission() {
        return permChecker.hasCreateRepositoryPermission();
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

    @Override
    protected boolean isDropHintRequired() {
        return false;
    }

}
