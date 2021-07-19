/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.softwaremodule;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.ui.artifacts.smtable.SmMetaDataWindowBuilder;
import org.eclipse.hawkbit.ui.artifacts.smtable.SmWindowBuilder;
import org.eclipse.hawkbit.ui.artifacts.smtable.SoftwareModuleGrid;
import org.eclipse.hawkbit.ui.artifacts.smtable.SoftwareModuleGridHeader;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.layout.AbstractGridComponentLayout;
import org.eclipse.hawkbit.ui.common.layout.listener.TopicEventListener;

/**
 * Abstract base class for software modules in grid layouts.
 */
public abstract class AbstractSoftwareModuleGridLayout extends AbstractGridComponentLayout {
    private static final long serialVersionUID = 1L;

    private final transient Set<TopicEventListener> listeners = new HashSet<>();
    private final transient SmWindowBuilder smWindowBuilder;
    private final transient SmMetaDataWindowBuilder smMetaDataWindowBuilder;
    private final EventView eventView;

    /**
     * Constructor for AbstractSoftwareModuleGridLayout.
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param softwareModuleManagement
     *            SoftwareModuleManagement
     * @param softwareModuleTypeManagement
     *            SoftwareModuleTypeManagement
     * @param eventView
     *            EventView
     */
    protected AbstractSoftwareModuleGridLayout(final CommonUiDependencies uiDependencies,
            final SoftwareModuleManagement softwareModuleManagement,
            final SoftwareModuleTypeManagement softwareModuleTypeManagement, final EventView eventView) {

        this.eventView = eventView;
        smWindowBuilder = new SmWindowBuilder(uiDependencies, softwareModuleManagement, softwareModuleTypeManagement,
                eventView);
        smMetaDataWindowBuilder = new SmMetaDataWindowBuilder(uiDependencies, softwareModuleManagement);

    }

    protected abstract SoftwareModuleGridHeader getSoftwareModuleGridHeader();

    protected abstract SoftwareModuleGrid getSoftwareModuleGrid();

    /**
     * Return SmMetaDataWindowBuilder
     *
     * @return the smMetaDataWindowBuilder
     */
    public SmMetaDataWindowBuilder getSmMetaDataWindowBuilder() {
        return smMetaDataWindowBuilder;
    }

    /**
     * Return SmWindowBuilder.
     *
     * @return the smWindowBuilder
     */
    public SmWindowBuilder getSmWindowBuilder() {
        return smWindowBuilder;
    }

    /**
     * Return EventView.
     *
     * @return the eventView
     */
    public EventView getEventView() {
        return eventView;
    }

    /**
     * Show software module grid header
     */
    public void showSmTypeHeaderIcon() {
        getSoftwareModuleGridHeader().showFilterIcon();
    }

    /**
     * Hide software module grid header
     */
    public void hideSmTypeHeaderIcon() {
        getSoftwareModuleGridHeader().hideFilterIcon();
    }

    /**
     * Maximize the software module grid
     */
    public void maximize() {
        getSoftwareModuleGrid().createMaximizedContent();
        hideDetailsLayout();
    }

    /**
     * Minimize the software module grid
     */
    public void minimize() {
        getSoftwareModuleGrid().createMinimizedContent();
        showDetailsLayout();
    }

    protected void addEventListener(final TopicEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void restoreState() {
        getSoftwareModuleGridHeader().restoreState();
        getSoftwareModuleGrid().restoreState();
    }

    @Override
    public void onViewEnter() {
        getSoftwareModuleGrid().getSelectionSupport().reselectCurrentEntity();
    }

    @Override
    public void subscribeListeners() {
        listeners.forEach(TopicEventListener::subscribe);
    }

    @Override
    public void unsubscribeListeners() {
        listeners.forEach(TopicEventListener::unsubscribe);
    }

}
