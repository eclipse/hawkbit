/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.artifacts.smtype.filter;

import java.util.Arrays;
import java.util.List;

import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.ui.artifacts.smtype.SmTypeWindowBuilder;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySoftwareModule;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyType;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventLayoutViewAware;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.filterlayout.AbstractFilterLayout;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener.EntityModifiedAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.GridActionsVisibilityListener;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGenericSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGridRefreshAwareSupport;
import org.eclipse.hawkbit.ui.common.state.TypeFilterLayoutUiState;

import com.vaadin.ui.ComponentContainer;

/**
 * Software module type filter buttons layout.
 */
public class SMTypeFilterLayout extends AbstractFilterLayout {

    private static final long serialVersionUID = 1L;

    private final SMTypeFilterHeader smTypeFilterHeader;
    private final SMTypeFilterButtons smTypeFilterButtons;

    private final transient GridActionsVisibilityListener gridActionsVisibilityListener;
    private final transient EntityModifiedListener<ProxyType> entityModifiedListener;

    /**
     * Constructor
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param softwareModuleTypeManagement
     *            SoftwareModuleTypeManagement
     * @param smTypeFilterLayoutUiState
     *            SMTypeFilterLayoutUiState
     * @param eventView
     *            {@link EventView}
     */
    public SMTypeFilterLayout(final CommonUiDependencies uiDependencies,
            final SoftwareModuleTypeManagement softwareModuleTypeManagement,
            final TypeFilterLayoutUiState smTypeFilterLayoutUiState, final EventView eventView) {
        final SmTypeWindowBuilder smTypeWindowBuilder = new SmTypeWindowBuilder(uiDependencies,
                softwareModuleTypeManagement);

        this.smTypeFilterHeader = new SMTypeFilterHeader(uiDependencies, smTypeWindowBuilder, smTypeFilterLayoutUiState,
                eventView);
        this.smTypeFilterButtons = new SMTypeFilterButtons(uiDependencies, softwareModuleTypeManagement,
                smTypeWindowBuilder, smTypeFilterLayoutUiState, eventView);

        final EventLayoutViewAware layoutViewAware = new EventLayoutViewAware(EventLayout.SM_TYPE_FILTER, eventView);
        this.gridActionsVisibilityListener = new GridActionsVisibilityListener(uiDependencies.getEventBus(),
                layoutViewAware, smTypeFilterButtons::hideActionColumns, smTypeFilterButtons::showEditColumn,
                smTypeFilterButtons::showDeleteColumn);
        this.entityModifiedListener = new EntityModifiedListener.Builder<>(uiDependencies.getEventBus(),
                ProxyType.class).parentEntityType(ProxySoftwareModule.class).viewAware(layoutViewAware)
                        .entityModifiedAwareSupports(getEntityModifiedAwareSupports()).build();

        buildLayout();
    }

    private List<EntityModifiedAwareSupport> getEntityModifiedAwareSupports() {
        return Arrays.asList(EntityModifiedGridRefreshAwareSupport.of(this::refreshFilterButtons),
                EntityModifiedGenericSupport.of(null, null, smTypeFilterButtons::resetFilterOnTypesDeleted));
    }

    protected void refreshFilterButtons() {
        smTypeFilterButtons.refreshAll();
    }

    @Override
    protected SMTypeFilterHeader getFilterHeader() {
        return smTypeFilterHeader;
    }

    @Override
    protected ComponentContainer getFilterContent() {
        return wrapFilterContent(smTypeFilterButtons);
    }

    @Override
    public void restoreState() {
        smTypeFilterButtons.restoreState();
    }

    @Override
    public void onViewEnter() {
        smTypeFilterButtons.reevaluateFilter();
    }

    @Override
    public void subscribeListeners() {
        gridActionsVisibilityListener.subscribe();
        entityModifiedListener.subscribe();
    }

    @Override
    public void unsubscribeListeners() {
        gridActionsVisibilityListener.unsubscribe();
        entityModifiedListener.unsubscribe();
    }
}
