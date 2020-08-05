/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.distributions.dstable;

import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.table.AbstractDistributionSetTableHeader;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEventType;
import org.eclipse.hawkbit.ui.distributions.event.DistributionsUIEvent;
import org.eclipse.hawkbit.ui.distributions.state.ManageDistUIState;
import org.eclipse.hawkbit.ui.management.dstable.DistributionAddUpdateWindowLayout;
import org.eclipse.hawkbit.ui.management.event.DistributionTableEvent;
import org.eclipse.hawkbit.ui.management.event.RefreshDistributionTableByFilterEvent;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;

/**
 * Distribution table header.
 */
public class DistributionSetTableHeader extends AbstractDistributionSetTableHeader {

    private static final long serialVersionUID = 1L;

    private final DistributionAddUpdateWindowLayout addUpdateWindowLayout;

    DistributionSetTableHeader(final VaadinMessageSource i18n, final SpPermissionChecker permChecker,
            final UIEventBus eventbus, final ManageDistUIState manageDistUIstate,
            final DistributionAddUpdateWindowLayout addUpdateWindowLayout) {
        super(i18n, permChecker, eventbus, null, manageDistUIstate, null);
        this.addUpdateWindowLayout = addUpdateWindowLayout;
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final DistributionsUIEvent event) {
        if (event == DistributionsUIEvent.HIDE_DIST_FILTER_BY_TYPE) {
            setFilterButtonsIconVisible(true);
        }
    }

    @Override
    protected String onLoadSearchBoxValue() {
        return getManageDistUIstate().getManageDistFilters().getSearchText().orElse(null);
    }

    @Override
    protected void showFilterButtonsLayout() {
        getManageDistUIstate().setDistTypeFilterClosed(false);
        eventBus.publish(this, DistributionsUIEvent.SHOW_DIST_FILTER_BY_TYPE);
    }

    @Override
    protected void resetSearchText() {
        if (getManageDistUIstate().getManageDistFilters().getSearchText().isPresent()) {
            getManageDistUIstate().getManageDistFilters().setSearchText(null);
            eventBus.publish(this, new RefreshDistributionTableByFilterEvent());
        }
    }

    @Override
    public void maximizeTable() {
        getManageDistUIstate().setDsTableMaximized(Boolean.TRUE);
        eventBus.publish(this, new DistributionTableEvent(BaseEntityEventType.MAXIMIZED));
    }

    @Override
    public void minimizeTable() {
        getManageDistUIstate().setDsTableMaximized(Boolean.FALSE);
        eventBus.publish(this, new DistributionTableEvent(BaseEntityEventType.MINIMIZED));
    }

    @Override
    public Boolean onLoadIsTableMaximized() {
        return getManageDistUIstate().isDsTableMaximized();
    }

    @Override
    public Boolean onLoadIsShowFilterButtonDisplayed() {
        return getManageDistUIstate().isDistTypeFilterClosed();
    }

    @Override
    protected void searchBy(final String newSearchText) {
        getManageDistUIstate().getManageDistFilters().setSearchText(newSearchText);
        eventBus.publish(this, new RefreshDistributionTableByFilterEvent());
    }

    @Override
    protected void addNewItem(final ClickEvent event) {
        final Window newDistWindow = addUpdateWindowLayout.getWindowForCreateDistributionSet();
        UI.getCurrent().addWindow(newDistWindow);
        newDistWindow.setVisible(Boolean.TRUE);
    }

    @Override
    protected void syncHono(final ClickEvent event) {
        // do nothing
    }

    @Override
    protected Boolean isAddNewItemAllowed() {
        return Boolean.TRUE;
    }

    @Override
    protected Boolean isHonoSyncAllowed() {
        return Boolean.FALSE;
    }

}
