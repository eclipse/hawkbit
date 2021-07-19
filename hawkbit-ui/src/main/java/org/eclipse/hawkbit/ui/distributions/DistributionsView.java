/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.distributions;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.DistributionSetTagManagement;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.AbstractHawkbitUI;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.AbstractEventListenersAwareView;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutResizeListener;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutResizeListener.ResizeHandler;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutVisibilityListener;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutVisibilityListener.VisibilityHandler;
import org.eclipse.hawkbit.ui.distributions.disttype.filter.DSTypeFilterLayout;
import org.eclipse.hawkbit.ui.distributions.dstable.DistributionSetGridLayout;
import org.eclipse.hawkbit.ui.distributions.smtable.SwModuleGridLayout;
import org.eclipse.hawkbit.ui.distributions.smtype.filter.DistSMTypeFilterLayout;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.annotations.JavaScript;
import com.vaadin.server.Page;
import com.vaadin.server.Page.BrowserWindowResizeEvent;
import com.vaadin.server.Page.BrowserWindowResizeListener;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.spring.annotation.UIScope;
import com.vaadin.ui.HorizontalLayout;

/**
 * Manage distributions and distributions type view.
 */
@UIScope
@SpringView(name = DistributionsView.VIEW_NAME, ui = AbstractHawkbitUI.class)
@JavaScript("vaadin://js/dynamicStylesheet.js")
public class DistributionsView extends AbstractEventListenersAwareView implements BrowserWindowResizeListener {
    private static final long serialVersionUID = 1L;

    public static final String VIEW_NAME = "distributions";

    private final SpPermissionChecker permChecker;
    private final ManageDistUIState manageDistUIState;

    private final DSTypeFilterLayout dsTypeFilterLayout;
    private final DistributionSetGridLayout distributionSetGridLayout;
    private final SwModuleGridLayout swModuleGridLayout;
    private final DistSMTypeFilterLayout distSMTypeFilterLayout;

    private HorizontalLayout mainLayout;

    private final transient LayoutVisibilityListener layoutVisibilityListener;
    private final transient LayoutResizeListener layoutResizeListener;

    @Autowired
    DistributionsView(final SpPermissionChecker permChecker, final UIEventBus eventBus, final VaadinMessageSource i18n,
            final UINotification uiNotification, final ManageDistUIState manageDistUIState,
            final SoftwareModuleManagement softwareModuleManagement,
            final SoftwareModuleTypeManagement softwareModuleTypeManagement,
            final DistributionSetManagement distributionSetManagement,
            final DistributionSetTypeManagement distributionSetTypeManagement, final TargetManagement targetManagement,
            final EntityFactory entityFactory, final DistributionSetTagManagement distributionSetTagManagement,
            final TargetFilterQueryManagement targetFilterQueryManagement, final SystemManagement systemManagement,
            final ArtifactManagement artifactManagement, final TenantConfigurationManagement configManagement,
            final SystemSecurityContext systemSecurityContext) {
        this.permChecker = permChecker;
        this.manageDistUIState = manageDistUIState;

        final CommonUiDependencies uiDependencies = new CommonUiDependencies(i18n, entityFactory, eventBus,
                uiNotification, permChecker);

        if (permChecker.hasReadRepositoryPermission()) {
            this.dsTypeFilterLayout = new DSTypeFilterLayout(uiDependencies, softwareModuleTypeManagement,
                    distributionSetTypeManagement, distributionSetManagement, systemManagement,
                    manageDistUIState.getDsTypeFilterLayoutUiState());
            this.distributionSetGridLayout = new DistributionSetGridLayout(uiDependencies, targetManagement,
                    targetFilterQueryManagement, distributionSetManagement, softwareModuleManagement,
                    distributionSetTypeManagement, distributionSetTagManagement, softwareModuleTypeManagement,
                    systemManagement, configManagement, systemSecurityContext,
                    manageDistUIState.getDsTypeFilterLayoutUiState(),
                    manageDistUIState.getDistributionSetGridLayoutUiState());
            this.swModuleGridLayout = new SwModuleGridLayout(uiDependencies, softwareModuleManagement,
                    softwareModuleTypeManagement, artifactManagement, manageDistUIState.getSmTypeFilterLayoutUiState(),
                    manageDistUIState.getSwModuleGridLayoutUiState());
            this.distSMTypeFilterLayout = new DistSMTypeFilterLayout(uiDependencies, softwareModuleTypeManagement,
                    manageDistUIState.getSmTypeFilterLayoutUiState());

            addEventAwareLayouts(Arrays.asList(dsTypeFilterLayout, distributionSetGridLayout, swModuleGridLayout,
                    distSMTypeFilterLayout));

            final Map<EventLayout, VisibilityHandler> layoutVisibilityHandlers = new EnumMap<>(EventLayout.class);
            layoutVisibilityHandlers.put(EventLayout.DS_TYPE_FILTER,
                    new VisibilityHandler(this::showDsTypeLayout, this::hideDsTypeLayout));
            layoutVisibilityHandlers.put(EventLayout.SM_TYPE_FILTER,
                    new VisibilityHandler(this::showSmTypeLayout, this::hideSmTypeLayout));
            this.layoutVisibilityListener = new LayoutVisibilityListener(eventBus,
                    new EventViewAware(EventView.DISTRIBUTIONS), layoutVisibilityHandlers);

            final Map<EventLayout, ResizeHandler> layoutResizeHandlers = new EnumMap<>(EventLayout.class);
            layoutResizeHandlers.put(EventLayout.DS_LIST,
                    new ResizeHandler(this::maximizeDsGridLayout, this::minimizeDsGridLayout));
            layoutResizeHandlers.put(EventLayout.SM_LIST,
                    new ResizeHandler(this::maximizeSmGridLayout, this::minimizeSmGridLayout));
            this.layoutResizeListener = new LayoutResizeListener(eventBus, new EventViewAware(EventView.DISTRIBUTIONS),
                    layoutResizeHandlers);
        } else {
            this.dsTypeFilterLayout = null;
            this.distributionSetGridLayout = null;
            this.swModuleGridLayout = null;
            this.distSMTypeFilterLayout = null;
            this.layoutVisibilityListener = null;
            this.layoutResizeListener = null;
        }
    }

    @Override
    protected void init() {
        if (permChecker.hasReadRepositoryPermission()) {
            super.init();
            Page.getCurrent().addBrowserWindowResizeListener(this);
        }
    }

    @Override
    protected void buildLayout() {
        setMargin(false);
        setSpacing(false);
        setSizeFull();

        createMainLayout();

        addComponent(mainLayout);
        setExpandRatio(mainLayout, 1.0F);
    }

    private void createMainLayout() {
        mainLayout = new HorizontalLayout();
        mainLayout.setSizeFull();
        mainLayout.setMargin(false);
        mainLayout.setSpacing(true);

        mainLayout.addComponent(dsTypeFilterLayout);
        mainLayout.addComponent(distributionSetGridLayout);
        mainLayout.addComponent(swModuleGridLayout);
        mainLayout.addComponent(distSMTypeFilterLayout);

        mainLayout.setExpandRatio(dsTypeFilterLayout, 0F);
        mainLayout.setExpandRatio(distributionSetGridLayout, 0.5F);
        mainLayout.setExpandRatio(swModuleGridLayout, 0.5F);
        mainLayout.setExpandRatio(distSMTypeFilterLayout, 0F);
    }

    @Override
    protected void restoreState() {
        if (permChecker.hasReadRepositoryPermission()) {
            restoreDsWidgetsState();
            restoreSmWidgetsState();
        }

        super.restoreState();
    }

    private void restoreDsWidgetsState() {
        if (manageDistUIState.getDsTypeFilterLayoutUiState().isHidden()
                || manageDistUIState.getSwModuleGridLayoutUiState().isMaximized()) {
            hideDsTypeLayout();
        } else {
            showDsTypeLayout();
        }

        if (manageDistUIState.getDistributionSetGridLayoutUiState().isMaximized()) {
            maximizeDsGridLayout();
        }
    }

    private void restoreSmWidgetsState() {
        if (manageDistUIState.getSwModuleGridLayoutUiState().isMaximized()) {
            maximizeSmGridLayout();
        }

        if (manageDistUIState.getSmTypeFilterLayoutUiState().isHidden()
                || manageDistUIState.getDistributionSetGridLayoutUiState().isMaximized()) {
            hideSmTypeLayout();
        } else {
            showSmTypeLayout();
        }
    }

    private void showDsTypeLayout() {
        dsTypeFilterLayout.setVisible(true);
        distributionSetGridLayout.hideDsFilterHeaderIcon();
    }

    private void hideDsTypeLayout() {
        dsTypeFilterLayout.setVisible(false);
        distributionSetGridLayout.showDsFilterHeaderIcon();
    }

    private void maximizeDsGridLayout() {
        swModuleGridLayout.setVisible(false);
        hideSmTypeLayout();

        mainLayout.setExpandRatio(dsTypeFilterLayout, 0F);
        mainLayout.setExpandRatio(distributionSetGridLayout, 1.0F);
        mainLayout.setExpandRatio(swModuleGridLayout, 0F);
        mainLayout.setExpandRatio(distSMTypeFilterLayout, 0F);

        distributionSetGridLayout.maximize();
    }

    private void minimizeDsGridLayout() {
        swModuleGridLayout.setVisible(true);
        if (!manageDistUIState.getSmTypeFilterLayoutUiState().isHidden()) {
            showSmTypeLayout();
        }

        mainLayout.setExpandRatio(dsTypeFilterLayout, 0F);
        mainLayout.setExpandRatio(distributionSetGridLayout, 0.5F);
        mainLayout.setExpandRatio(swModuleGridLayout, 0.5F);
        mainLayout.setExpandRatio(distSMTypeFilterLayout, 0F);

        distributionSetGridLayout.minimize();
    }

    private void maximizeSmGridLayout() {
        distributionSetGridLayout.setVisible(false);
        hideDsTypeLayout();

        mainLayout.setExpandRatio(dsTypeFilterLayout, 0F);
        mainLayout.setExpandRatio(distributionSetGridLayout, 0F);
        mainLayout.setExpandRatio(swModuleGridLayout, 1.0F);
        mainLayout.setExpandRatio(distSMTypeFilterLayout, 0F);

        swModuleGridLayout.maximize();
    }

    private void minimizeSmGridLayout() {
        distributionSetGridLayout.setVisible(true);
        if (!manageDistUIState.getDsTypeFilterLayoutUiState().isHidden()) {
            showDsTypeLayout();
        }

        mainLayout.setExpandRatio(dsTypeFilterLayout, 0F);
        mainLayout.setExpandRatio(distributionSetGridLayout, 0.5F);
        mainLayout.setExpandRatio(swModuleGridLayout, 0.5F);
        mainLayout.setExpandRatio(distSMTypeFilterLayout, 0F);

        swModuleGridLayout.minimize();
    }

    private void showSmTypeLayout() {
        distSMTypeFilterLayout.setVisible(true);
        swModuleGridLayout.hideSmTypeHeaderIcon();
    }

    private void hideSmTypeLayout() {
        distSMTypeFilterLayout.setVisible(false);
        swModuleGridLayout.showSmTypeHeaderIcon();
    }

    @Override
    public void browserWindowResized(final BrowserWindowResizeEvent event) {
        showOrHideFilterButtons(event.getWidth());
    }

    private void showOrHideFilterButtons(final int browserWidth) {
        if (browserWidth < SPUIDefinitions.REQ_MIN_BROWSER_WIDTH) {
            if (!manageDistUIState.getDsTypeFilterLayoutUiState().isHidden()) {
                hideDsTypeLayout();
            }

            if (!manageDistUIState.getSmTypeFilterLayoutUiState().isHidden()) {
                hideSmTypeLayout();
            }
        } else {
            if (manageDistUIState.getDsTypeFilterLayoutUiState().isHidden()) {
                showDsTypeLayout();
            }

            if (manageDistUIState.getSmTypeFilterLayoutUiState().isHidden()) {
                showSmTypeLayout();
            }
        }
    }

    @Override
    public String getViewName() {
        return DistributionsView.VIEW_NAME;
    }

    @Override
    protected void subscribeListeners() {
        if (permChecker.hasReadRepositoryPermission()) {
            layoutVisibilityListener.subscribe();
            layoutResizeListener.subscribe();
        }

        super.subscribeListeners();
    }

    @Override
    protected void unsubscribeListeners() {
        if (permChecker.hasReadRepositoryPermission()) {
            layoutVisibilityListener.unsubscribe();
            layoutResizeListener.unsubscribe();
        }

        super.unsubscribeListeners();
    }
}
