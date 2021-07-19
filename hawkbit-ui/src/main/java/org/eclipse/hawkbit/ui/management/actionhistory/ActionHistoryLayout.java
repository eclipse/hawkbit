/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.actionhistory;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.EventListenersAwareLayout;

import com.vaadin.ui.HorizontalLayout;

/**
 * Layout responsible for action-history-grid and the corresponding header.
 */
public class ActionHistoryLayout extends HorizontalLayout implements EventListenersAwareLayout {
    private static final long serialVersionUID = 1L;

    private final ActionHistoryGridLayout actionHistoryGridLayout;
    private final ActionStatusGridLayout actionStatusLayout;
    private final ActionStatusMsgGridLayout actionStatusMsgLayout;

    /**
     * Constructor for ActionHistoryLayout
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param deploymentManagement
     *            DeploymentManagement
     * @param actionHistoryGridLayoutUiState
     *            ActionHistoryGridLayoutUiState
     */
    public ActionHistoryLayout(final CommonUiDependencies uiDependencies,
            final DeploymentManagement deploymentManagement,
            final ActionHistoryGridLayoutUiState actionHistoryGridLayoutUiState) {

        this.actionHistoryGridLayout = new ActionHistoryGridLayout(uiDependencies, deploymentManagement,
                actionHistoryGridLayoutUiState);

        this.actionStatusLayout = new ActionStatusGridLayout(uiDependencies, deploymentManagement);
        this.actionStatusMsgLayout = new ActionStatusMsgGridLayout(uiDependencies, deploymentManagement);

        init();
        buildLayout();
    }

    private void init() {
        setSizeFull();
        setMargin(false);
        setSpacing(true);
    }

    private void buildLayout() {
        addComponent(actionHistoryGridLayout);
        setExpandRatio(actionHistoryGridLayout, 0.55F);

        actionStatusLayout.setVisible(false);
        addComponent(actionStatusLayout);
        setExpandRatio(actionStatusLayout, 0.18F);

        actionStatusMsgLayout.setVisible(false);
        addComponent(actionStatusMsgLayout);
        setExpandRatio(actionStatusMsgLayout, 0.27F);
    }

    /**
     * Maximize the action history grid layout
     */
    public void maximize() {
        actionStatusLayout.enableSelection();
        actionStatusMsgLayout.enableSelection();

        actionHistoryGridLayout.maximize();

        actionStatusLayout.setVisible(true);
        actionStatusMsgLayout.setVisible(true);
    }

    /**
     * Minimize the action history grid layout
     */
    public void minimize() {
        actionStatusLayout.disableSelection();
        actionStatusMsgLayout.disableSelection();

        actionHistoryGridLayout.minimize();

        actionStatusLayout.setVisible(false);
        actionStatusMsgLayout.setVisible(false);
    }

    @Override
    public void restoreState() {
        actionHistoryGridLayout.restoreState();
    }

    @Override
    public void onViewEnter() {
        actionHistoryGridLayout.onViewEnter();
    }

    @Override
    public void subscribeListeners() {
        actionHistoryGridLayout.subscribeListeners();
        actionStatusLayout.subscribeListeners();
        actionStatusMsgLayout.subscribeListeners();
    }

    @Override
    public void unsubscribeListeners() {
        actionHistoryGridLayout.unsubscribeListeners();
        actionStatusLayout.unsubscribeListeners();
        actionStatusMsgLayout.unsubscribeListeners();
    }
}
