/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.dstable;

import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.table.AbstractDistributionSetTableHeader;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEventType;
import org.eclipse.hawkbit.ui.management.event.DistributionTableEvent;
import org.eclipse.hawkbit.ui.management.event.ManagementUIEvent;
import org.eclipse.hawkbit.ui.management.event.RefreshDistributionTableByFilterEvent;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.ui.Button.ClickEvent;

/**
 * Distribution table header.
 *
 */
public class DistributionTableHeader extends AbstractDistributionSetTableHeader {

    private static final long serialVersionUID = 1L;

    DistributionTableHeader(final VaadinMessageSource i18n, final SpPermissionChecker permChecker,
            final UIEventBus eventbus, final ManagementUIState managementUIState) {
        super(i18n, permChecker, eventbus, managementUIState, null, null);
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final ManagementUIEvent event) {
        if (event == ManagementUIEvent.HIDE_DISTRIBUTION_TAG_LAYOUT) {
            setFilterButtonsIconVisible(true);
        } else if (event == ManagementUIEvent.SHOW_DISTRIBUTION_TAG_LAYOUT) {
            setFilterButtonsIconVisible(false);
        }
    }

    @Override
    protected String onLoadSearchBoxValue() {
        return getManagementUIState().getDistributionTableFilters().getSearchText().orElse(null);
    }

    @Override
    protected void showFilterButtonsLayout() {
        getManagementUIState().setDistTagFilterClosed(false);
        eventBus.publish(this, ManagementUIEvent.SHOW_DISTRIBUTION_TAG_LAYOUT);
    }

    @Override
    protected void resetSearchText() {
        if (getManagementUIState().getDistributionTableFilters().getSearchText().isPresent()) {
            getManagementUIState().getDistributionTableFilters().setSearchText(null);
            eventBus.publish(this, new RefreshDistributionTableByFilterEvent());
        }
    }

    @Override
    public void maximizeTable() {
        getManagementUIState().setDsTableMaximized(Boolean.TRUE);
        eventBus.publish(this, new DistributionTableEvent(BaseEntityEventType.MAXIMIZED));
    }

    @Override
    public void minimizeTable() {
        getManagementUIState().setDsTableMaximized(Boolean.FALSE);
        eventBus.publish(this, new DistributionTableEvent(BaseEntityEventType.MINIMIZED));
    }

    @Override
    public Boolean onLoadIsTableMaximized() {
        return getManagementUIState().isDsTableMaximized();
    }

    @Override
    public Boolean onLoadIsShowFilterButtonDisplayed() {
        return getManagementUIState().isDistTagFilterClosed();
    }

    @Override
    protected void searchBy(final String newSearchText) {
        getManagementUIState().getDistributionTableFilters().setSearchText(newSearchText);
        eventBus.publish(this, new RefreshDistributionTableByFilterEvent());
    }

    @Override
    protected void addNewItem(final ClickEvent event) {
        // is okay and not supported
    }

    @Override
    protected void syncHono(final ClickEvent event) {
        // is okay and not supported
    }

    @Override
    protected Boolean isAddNewItemAllowed() {
        return Boolean.FALSE;
    }

    @Override
    protected Boolean isHonoSyncAllowed() {
        return Boolean.FALSE;
    }

}
