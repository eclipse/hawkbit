/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettag.filter;

import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.TargetFilterTabChangedEventPayload;
import org.eclipse.hawkbit.ui.common.filterlayout.AbstractFilterLayout;
import org.eclipse.hawkbit.ui.common.layout.listener.GenericEventListener;
import org.eclipse.hawkbit.ui.management.ManagementUIState;
import org.eclipse.hawkbit.ui.management.targettag.TargetTagWindowBuilder;

import com.vaadin.ui.ComponentContainer;

/**
 * Target Tag filter layout.
 */
public class TargetTagFilterLayout extends AbstractFilterLayout {
    private static final long serialVersionUID = 1L;

    private final TargetTagFilterHeader targetTagFilterHeader;
    private final MultipleTargetFilter multipleTargetFilter;

    private final transient GenericEventListener<TargetFilterTabChangedEventPayload> filterTabChangedListener;

    /**
     * Constructor
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param managementUIState
     *            ManagementUIState
     * @param targetFilterQueryManagement
     *            TargetFilterQueryManagement
     * @param targetTagManagement
     *            TargetTagManagement
     * @param targetManagement
     *            TargetManagement
     * @param targetTagFilterLayoutUiState
     *            TargetTagFilterLayoutUiState
     */
    public TargetTagFilterLayout(final CommonUiDependencies uiDependencies, final ManagementUIState managementUIState,
            final TargetFilterQueryManagement targetFilterQueryManagement,
            final TargetTagManagement targetTagManagement, final TargetManagement targetManagement,
            final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState) {
        final TargetTagWindowBuilder targetTagWindowBuilder = new TargetTagWindowBuilder(uiDependencies,
                targetTagManagement);

        this.targetTagFilterHeader = new TargetTagFilterHeader(uiDependencies, targetTagFilterLayoutUiState,
                targetTagWindowBuilder);
        this.multipleTargetFilter = new MultipleTargetFilter(uiDependencies, targetFilterQueryManagement,
                targetTagManagement, targetManagement, targetTagFilterLayoutUiState, targetTagWindowBuilder);

        this.filterTabChangedListener = new GenericEventListener<>(uiDependencies.getEventBus(),
                EventTopics.TARGET_FILTER_TAB_CHANGED, this::onTargetFilterTabChanged);

        buildLayout();
    }

    private void onTargetFilterTabChanged(final TargetFilterTabChangedEventPayload eventPayload) {
        if (TargetFilterTabChangedEventPayload.CUSTOM == eventPayload) {
            targetTagFilterHeader.disableCrudMenu();
        } else {
            targetTagFilterHeader.enableCrudMenu();
        }
    }

    @Override
    protected TargetTagFilterHeader getFilterHeader() {
        return targetTagFilterHeader;
    }

    @Override
    protected ComponentContainer getFilterContent() {
        return multipleTargetFilter;
    }

    @Override
    public void restoreState() {
        targetTagFilterHeader.restoreState();
        multipleTargetFilter.restoreState();
    }

    @Override
    public void onViewEnter() {
        multipleTargetFilter.onViewEnter();
    }

    @Override
    public void subscribeListeners() {
        filterTabChangedListener.subscribe();
        multipleTargetFilter.subscribeListeners();
    }

    @Override
    public void unsubscribeListeners() {
        filterTabChangedListener.unsubscribe();
        multipleTargetFilter.unsubscribeListeners();
    }
}
