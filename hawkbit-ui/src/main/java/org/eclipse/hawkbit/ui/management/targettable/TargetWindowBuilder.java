/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.targettable;

import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTypeManagement;
import org.eclipse.hawkbit.ContextAware;
import org.eclipse.hawkbit.ui.common.AbstractEntityWindowBuilder;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.ui.Window;

/**
 * Builder for target window
 */
public class TargetWindowBuilder extends AbstractEntityWindowBuilder<ProxyTarget> {

    private final TargetManagement targetManagement;

    private final TargetTypeManagement targetTypeManagement;

    private final EventView view;
    private final ContextAware contextAware;

    /**
     * Constructor for TargetWindowBuilder
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param targetManagement
     *            TargetManagement
     * @param targetTypeManagement
     *            TargetTypeManagement
     * @param view
     *            EventView
     * @param contextAware
     *            ContextAware
     */
    public TargetWindowBuilder(final CommonUiDependencies uiDependencies, final TargetManagement targetManagement,
            final TargetTypeManagement targetTypeManagement, final EventView view, final ContextAware contextAware) {
        super(uiDependencies);

        this.targetManagement = targetManagement;
        this.targetTypeManagement = targetTypeManagement;
        this.view = view;
        this.contextAware = contextAware;
    }

    @Override
    protected String getWindowId() {
        return UIComponentIdProvider.CREATE_POPUP_ID;
    }

    @Override
    public Window getWindowForAdd() {
        return getWindowForNewEntity(new AddTargetWindowController(uiDependencies, targetManagement,
                new TargetWindowLayout(getI18n(), targetTypeManagement), view, contextAware));

    }

    @Override
    public Window getWindowForUpdate(final ProxyTarget proxyTarget) {
        return getWindowForEntity(proxyTarget, new UpdateTargetWindowController(uiDependencies, targetManagement,
                new TargetWindowLayout(getI18n(), targetTypeManagement), contextAware));
    }
}
