/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.actionhistory;

import java.util.Arrays;
import java.util.List;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyActionStatus;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventLayoutViewAware;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.layout.AbstractGridComponentLayout;
import org.eclipse.hawkbit.ui.common.layout.MasterEntityAwareComponent;
import org.eclipse.hawkbit.ui.common.layout.listener.SelectionChangedListener;

/**
 * Layout responsible for messages-grid and the corresponding header.
 */
public class ActionStatusMsgGridLayout extends AbstractGridComponentLayout {
    private static final long serialVersionUID = 1L;

    private final ActionStatusMsgGridHeader actionStatusMsgHeader;
    private final ActionStatusMsgGrid actionStatusMsgGrid;

    private final transient SelectionChangedListener<ProxyActionStatus> selectionChangedListener;

    /**
     * Constructor for ActionStatusMsgGridLayout
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param deploymentManagement
     *            DeploymentManagement
     */
    public ActionStatusMsgGridLayout(final CommonUiDependencies uiDependencies,
            final DeploymentManagement deploymentManagement) {
        this.actionStatusMsgHeader = new ActionStatusMsgGridHeader(uiDependencies.getI18n());
        this.actionStatusMsgGrid = new ActionStatusMsgGrid(uiDependencies, deploymentManagement);

        final EventLayoutViewAware masterLayoutView = new EventLayoutViewAware(EventLayout.ACTION_HISTORY_STATUS_LIST,
                EventView.DEPLOYMENT);
        this.selectionChangedListener = new SelectionChangedListener<>(uiDependencies.getEventBus(), masterLayoutView,
                getMasterEntityAwareComponents());

        buildLayout(actionStatusMsgHeader, actionStatusMsgGrid);
    }

    private List<MasterEntityAwareComponent<ProxyActionStatus>> getMasterEntityAwareComponents() {
        return Arrays.asList(actionStatusMsgGrid.getMasterEntitySupport(), this::selectActionStatusMsg);
    }

    private void selectActionStatusMsg(final ProxyActionStatus masterAction) {
        if (masterAction == null) {
            actionStatusMsgGrid.getSelectionSupport().deselectAll();
            return;
        }

        actionStatusMsgGrid.getSelectionSupport().selectFirstRow();
    }

    /**
     * Enable the single selection in action status message grid
     */
    public void enableSelection() {
        actionStatusMsgGrid.getSelectionSupport().enableSingleSelection();
    }

    /**
     * Disable the selection in action status message grid
     */
    public void disableSelection() {
        actionStatusMsgGrid.getSelectionSupport().disableSelection();
    }

    @Override
    public void subscribeListeners() {
        selectionChangedListener.subscribe();
    }

    @Override
    public void unsubscribeListeners() {
        selectionChangedListener.unsubscribe();
    }
}
