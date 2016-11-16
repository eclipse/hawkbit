/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout.groupschart.client;

import com.vaadin.client.communication.StateChangeEvent;
import org.eclipse.hawkbit.ui.rollout.groupschart.GroupsPieChart;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.client.communication.RpcProxy;
import com.vaadin.client.ui.AbstractComponentConnector;
import com.vaadin.shared.ui.Connect;

@Connect(GroupsPieChart.class)
public class GroupsPieChartConnector extends AbstractComponentConnector {
    private final GroupsPieChartServerRpc serverRpc = RpcProxy.create(GroupsPieChartServerRpc.class, this);

    public GroupsPieChartConnector() {
        registerRpc(GroupsPieChartClientRpc.class, new GroupsPieChartClientRpc() {
        });
    }

    @Override
    protected Widget createWidget() {
        return GWT.create(GroupsPieChartWidget.class);
    }

    @Override
    public GroupsPieChartWidget getWidget() {
        return (GroupsPieChartWidget) super.getWidget();
    }

    @Override
    public GroupsPieChartState getState() {
        return (GroupsPieChartState) super.getState();
    }

    @Override
    public void onStateChanged(StateChangeEvent stateChangeEvent) {
        super.onStateChanged(stateChangeEvent);
        getWidget().update(getState().groupTargetCounts, getState().totalTargetCount);
    }
}
