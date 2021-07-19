/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.DistributionSetTagManagement;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.AbstractHawkbitUI;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.AbstractEventListenersAwareView;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.suppliers.TargetManagementStateDataSupplier;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutResizeListener;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutResizeListener.ResizeHandler;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutVisibilityListener;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutVisibilityListener.VisibilityHandler;
import org.eclipse.hawkbit.ui.management.actionhistory.ActionHistoryLayout;
import org.eclipse.hawkbit.ui.management.dstable.DistributionGridLayout;
import org.eclipse.hawkbit.ui.management.dstag.filter.DistributionTagLayout;
import org.eclipse.hawkbit.ui.management.targettable.TargetGridLayout;
import org.eclipse.hawkbit.ui.management.targettag.filter.TargetTagFilterLayout;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.server.Page;
import com.vaadin.server.Page.BrowserWindowResizeEvent;
import com.vaadin.server.Page.BrowserWindowResizeListener;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.spring.annotation.UIScope;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Layout;

/**
 * Target status and deployment management view
 */
@UIScope
@SpringView(name = DeploymentView.VIEW_NAME, ui = AbstractHawkbitUI.class)
public class DeploymentView extends AbstractEventListenersAwareView implements BrowserWindowResizeListener {
    private static final long serialVersionUID = 1L;

    public static final String VIEW_NAME = "deployment";

    private final SpPermissionChecker permChecker;
    private final ManagementUIState managementUIState;

    private final TargetTagFilterLayout targetTagFilterLayout;
    private final TargetGridLayout targetGridLayout;
    private final DistributionGridLayout distributionGridLayout;
    private final DistributionTagLayout distributionTagLayout;
    private final ActionHistoryLayout actionHistoryLayout;
    private final Layout targetCountLayout;

    private HorizontalLayout mainLayout;

    private final transient LayoutVisibilityListener layoutVisibilityListener;
    private final transient LayoutResizeListener layoutResizeListener;

    @Autowired
    DeploymentView(final UIEventBus eventBus, final SpPermissionChecker permChecker, final VaadinMessageSource i18n,
            final UINotification uiNotification, final ManagementUIState managementUIState,
            final DeploymentManagement deploymentManagement, final DistributionSetManagement distributionSetManagement,
            final SoftwareModuleManagement smManagement,
            final DistributionSetTypeManagement distributionSetTypeManagement, final TargetManagement targetManagement,
            final EntityFactory entityFactory, final UiProperties uiProperties,
            final TargetTagManagement targetTagManagement,
            final DistributionSetTagManagement distributionSetTagManagement,
            final TargetFilterQueryManagement targetFilterQueryManagement, final SystemManagement systemManagement,
            final TenantConfigurationManagement configManagement,
            final TargetManagementStateDataSupplier targetManagementStateDataSupplier,
            final SystemSecurityContext systemSecurityContext, @Qualifier("uiExecutor") final Executor uiExecutor) {
        this.permChecker = permChecker;
        this.managementUIState = managementUIState;

        final CommonUiDependencies uiDependencies = new CommonUiDependencies(i18n, entityFactory, eventBus,
                uiNotification, permChecker);

        if (permChecker.hasTargetReadPermission()) {
            this.targetTagFilterLayout = new TargetTagFilterLayout(uiDependencies, managementUIState,
                    targetFilterQueryManagement, targetTagManagement, targetManagement,
                    managementUIState.getTargetTagFilterLayoutUiState());

            this.targetGridLayout = new TargetGridLayout(uiDependencies, targetManagement, deploymentManagement,
                    uiProperties, targetTagManagement, distributionSetManagement, uiExecutor, configManagement,
                    targetManagementStateDataSupplier, systemSecurityContext,
                    managementUIState.getTargetTagFilterLayoutUiState(), managementUIState.getTargetGridLayoutUiState(),
                    managementUIState.getTargetBulkUploadUiState(),
                    managementUIState.getDistributionGridLayoutUiState());
            this.targetCountLayout = targetGridLayout.getCountMessageLabel().createFooterMessageComponent();

            this.actionHistoryLayout = new ActionHistoryLayout(uiDependencies, deploymentManagement,
                    managementUIState.getActionHistoryGridLayoutUiState());

            addEventAwareLayouts(Arrays.asList(targetTagFilterLayout, targetGridLayout, actionHistoryLayout));
        } else {
            this.targetTagFilterLayout = null;
            this.targetGridLayout = null;
            this.targetCountLayout = null;
            this.actionHistoryLayout = null;
        }

        if (permChecker.hasReadRepositoryPermission()) {
            this.distributionTagLayout = new DistributionTagLayout(uiDependencies, distributionSetTagManagement,
                    distributionSetManagement, managementUIState.getDistributionTagLayoutUiState());
            this.distributionGridLayout = new DistributionGridLayout(uiDependencies, targetManagement,
                    distributionSetManagement, smManagement, distributionSetTypeManagement,
                    distributionSetTagManagement, systemManagement, deploymentManagement, configManagement,
                    systemSecurityContext, uiProperties, managementUIState.getDistributionGridLayoutUiState(),
                    managementUIState.getDistributionTagLayoutUiState(),
                    managementUIState.getTargetGridLayoutUiState());

            addEventAwareLayouts(Arrays.asList(distributionTagLayout, distributionGridLayout));
        } else {
            this.distributionTagLayout = null;
            this.distributionGridLayout = null;
        }

        if (permChecker.hasTargetReadPermission() || permChecker.hasReadRepositoryPermission()) {
            final Map<EventLayout, VisibilityHandler> layoutVisibilityHandlers = new EnumMap<>(EventLayout.class);
            layoutVisibilityHandlers.put(EventLayout.TARGET_TAG_FILTER,
                    new VisibilityHandler(this::showTargetTagLayout, this::hideTargetTagLayout));
            layoutVisibilityHandlers.put(EventLayout.DS_TAG_FILTER,
                    new VisibilityHandler(this::showDsTagLayout, this::hideDsTagLayout));
            this.layoutVisibilityListener = new LayoutVisibilityListener(eventBus,
                    new EventViewAware(EventView.DEPLOYMENT), layoutVisibilityHandlers);

            final Map<EventLayout, ResizeHandler> layoutResizeHandlers = new EnumMap<>(EventLayout.class);
            layoutResizeHandlers.put(EventLayout.TARGET_LIST,
                    new ResizeHandler(this::maximizeTargetGridLayout, this::minimizeTargetGridLayout));
            layoutResizeHandlers.put(EventLayout.DS_LIST,
                    new ResizeHandler(this::maximizeDsGridLayout, this::minimizeDsGridLayout));
            layoutResizeHandlers.put(EventLayout.ACTION_HISTORY_LIST,
                    new ResizeHandler(this::maximizeActionHistoryGridLayout, this::minimizeActionHistoryGridLayout));
            this.layoutResizeListener = new LayoutResizeListener(eventBus, new EventViewAware(EventView.DEPLOYMENT),
                    layoutResizeHandlers);
        } else {
            this.layoutVisibilityListener = null;
            this.layoutResizeListener = null;
        }
    }

    @Override
    protected void init() {
        if (permChecker.hasTargetReadPermission() || permChecker.hasReadRepositoryPermission()) {
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

        if (targetCountLayout != null) {
            addComponent(targetCountLayout);
        }
    }

    private void createMainLayout() {
        mainLayout = new HorizontalLayout();
        mainLayout.setSizeFull();
        mainLayout.setMargin(false);
        mainLayout.setSpacing(true);

        if (permChecker.hasReadRepositoryPermission() && permChecker.hasTargetReadPermission()) {
            addAllWidgets();
        } else if (permChecker.hasReadRepositoryPermission()) {
            addDistributionWidgetsOnly();
        } else if (permChecker.hasTargetReadPermission()) {
            addTargetWidgetsOnly();
        }
    }

    private void addAllWidgets() {
        mainLayout.addComponent(targetTagFilterLayout);
        mainLayout.addComponent(targetGridLayout);
        mainLayout.addComponent(distributionGridLayout);
        mainLayout.addComponent(distributionTagLayout);
        mainLayout.addComponent(actionHistoryLayout);

        adaptAllWidgetsRatios();
    }

    private void adaptAllWidgetsRatios() {
        mainLayout.setExpandRatio(targetTagFilterLayout, 0F);
        mainLayout.setExpandRatio(targetGridLayout, 0.275F);
        mainLayout.setExpandRatio(distributionGridLayout, 0.275F);
        mainLayout.setExpandRatio(distributionTagLayout, 0F);
        mainLayout.setExpandRatio(actionHistoryLayout, 0.45F);
    }

    private void addDistributionWidgetsOnly() {
        mainLayout.addComponent(distributionGridLayout);
        mainLayout.addComponent(distributionTagLayout);

        adaptDsWidgetsRatios();
    }

    private void adaptDsWidgetsRatios() {
        mainLayout.setExpandRatio(distributionGridLayout, 1F);
        mainLayout.setExpandRatio(distributionTagLayout, 0F);
    }

    private void addTargetWidgetsOnly() {
        mainLayout.addComponent(targetTagFilterLayout);
        mainLayout.addComponent(targetGridLayout);
        mainLayout.addComponent(actionHistoryLayout);

        adaptTargetWidgetsRatios();
    }

    private void adaptTargetWidgetsRatios() {
        mainLayout.setExpandRatio(targetTagFilterLayout, 0F);
        mainLayout.setExpandRatio(targetGridLayout, 0.4F);
        mainLayout.setExpandRatio(actionHistoryLayout, 0.6F);
    }

    @Override
    protected void restoreState() {
        if (permChecker.hasTargetReadPermission()) {
            restoreTargetWidgetsState();
        }

        if (permChecker.hasReadRepositoryPermission()) {
            restoreDsWidgetsState();
        }

        super.restoreState();
    }

    private void restoreTargetWidgetsState() {
        if (managementUIState.getTargetTagFilterLayoutUiState().isHidden()
                || managementUIState.getDistributionGridLayoutUiState().isMaximized()
                || managementUIState.getActionHistoryGridLayoutUiState().isMaximized()) {
            hideTargetTagLayout();
        } else {
            showTargetTagLayout();
        }

        if (managementUIState.getTargetGridLayoutUiState().isMaximized()) {
            maximizeTargetGridLayout();
        }

        if (managementUIState.getActionHistoryGridLayoutUiState().isMaximized()) {
            maximizeActionHistoryGridLayout();
        }
    }

    private void restoreDsWidgetsState() {
        if (managementUIState.getDistributionTagLayoutUiState().isHidden()
                || managementUIState.getTargetGridLayoutUiState().isMaximized()
                || managementUIState.getActionHistoryGridLayoutUiState().isMaximized()) {
            hideDsTagLayout();
        } else {
            showDsTagLayout();
        }

        if (managementUIState.getDistributionGridLayoutUiState().isMaximized()) {
            maximizeDsGridLayout();
        }
    }

    private void showTargetTagLayout() {
        targetTagFilterLayout.setVisible(true);
        targetGridLayout.hideTargetTagHeaderIcon();
    }

    private void hideTargetTagLayout() {
        targetTagFilterLayout.setVisible(false);
        targetGridLayout.showTargetTagHeaderIcon();
    }

    private void maximizeTargetGridLayout() {
        if (distributionGridLayout != null) {
            distributionGridLayout.setVisible(false);
        }
        if (distributionTagLayout != null) {
            hideDsTagLayout();
        }
        actionHistoryLayout.setVisible(false);

        clearAllWidgetsRatios();
        mainLayout.setExpandRatio(targetGridLayout, 1F);

        targetGridLayout.maximize();
    }

    private void clearAllWidgetsRatios() {
        mainLayout.iterator().forEachRemaining(layout -> mainLayout.setExpandRatio(layout, 0F));
    }

    private void minimizeTargetGridLayout() {
        if (distributionGridLayout != null) {
            distributionGridLayout.setVisible(true);
        }
        if (distributionTagLayout != null && !managementUIState.getDistributionTagLayoutUiState().isHidden()) {
            showDsTagLayout();
        }
        actionHistoryLayout.setVisible(true);

        if (distributionGridLayout != null && distributionTagLayout != null) {
            adaptAllWidgetsRatios();
        } else {
            adaptTargetWidgetsRatios();
        }

        targetGridLayout.minimize();
    }

    private void maximizeDsGridLayout() {
        if (targetTagFilterLayout != null) {
            hideTargetTagLayout();
        }
        if (targetGridLayout != null) {
            targetGridLayout.setVisible(false);
            targetCountLayout.setVisible(false);
        }
        if (actionHistoryLayout != null) {
            actionHistoryLayout.setVisible(false);
        }

        clearAllWidgetsRatios();
        mainLayout.setExpandRatio(distributionGridLayout, 1F);

        distributionGridLayout.maximize();
    }

    private void minimizeDsGridLayout() {
        if (targetTagFilterLayout != null && !managementUIState.getTargetTagFilterLayoutUiState().isHidden()) {
            showTargetTagLayout();
        }
        if (targetGridLayout != null) {
            targetGridLayout.setVisible(true);
            targetCountLayout.setVisible(true);
        }
        if (actionHistoryLayout != null) {
            actionHistoryLayout.setVisible(true);
        }

        if (targetTagFilterLayout != null && targetGridLayout != null && actionHistoryLayout != null) {
            adaptAllWidgetsRatios();
        } else {
            adaptDsWidgetsRatios();
        }

        distributionGridLayout.minimize();
    }

    private void showDsTagLayout() {
        distributionTagLayout.setVisible(true);
        distributionGridLayout.hideDsFilterHeaderIcon();
    }

    private void hideDsTagLayout() {
        distributionTagLayout.setVisible(false);
        distributionGridLayout.showDsFilterHeaderIcon();
    }

    private void maximizeActionHistoryGridLayout() {
        hideTargetTagLayout();
        targetGridLayout.setVisible(false);
        targetCountLayout.setVisible(false);
        if (distributionGridLayout != null) {
            distributionGridLayout.setVisible(false);
        }
        if (distributionTagLayout != null) {
            hideDsTagLayout();
        }

        clearAllWidgetsRatios();
        mainLayout.setExpandRatio(actionHistoryLayout, 1F);

        actionHistoryLayout.maximize();
    }

    private void minimizeActionHistoryGridLayout() {
        if (!managementUIState.getTargetTagFilterLayoutUiState().isHidden()) {
            showTargetTagLayout();
        }
        targetGridLayout.setVisible(true);
        targetCountLayout.setVisible(true);
        if (distributionGridLayout != null) {
            distributionGridLayout.setVisible(true);
        }
        if (distributionTagLayout != null && !managementUIState.getDistributionTagLayoutUiState().isHidden()) {
            showDsTagLayout();
        }

        if (distributionGridLayout != null && distributionTagLayout != null) {
            adaptAllWidgetsRatios();
        } else {
            adaptTargetWidgetsRatios();
        }

        actionHistoryLayout.minimize();
    }

    @Override
    public void browserWindowResized(final BrowserWindowResizeEvent event) {
        showOrHideFilterButtons(event.getWidth());
    }

    private void showOrHideFilterButtons(final int browserWidth) {
        if (browserWidth < SPUIDefinitions.REQ_MIN_BROWSER_WIDTH) {
            if (permChecker.hasTargetReadPermission()
                    && !managementUIState.getTargetTagFilterLayoutUiState().isHidden()) {
                hideTargetTagLayout();
            }

            if (permChecker.hasReadRepositoryPermission()
                    && !managementUIState.getDistributionTagLayoutUiState().isHidden()) {
                hideDsTagLayout();
            }
        } else {
            if (permChecker.hasTargetReadPermission()
                    && managementUIState.getTargetTagFilterLayoutUiState().isHidden()) {
                showTargetTagLayout();
            }

            if (permChecker.hasReadRepositoryPermission()
                    && managementUIState.getDistributionTagLayoutUiState().isHidden()) {
                showDsTagLayout();
            }
        }
    }

    @Override
    public String getViewName() {
        return DeploymentView.VIEW_NAME;
    }

    @Override
    protected void subscribeListeners() {
        if (permChecker.hasTargetReadPermission() || permChecker.hasReadRepositoryPermission()) {
            layoutVisibilityListener.subscribe();
            layoutResizeListener.subscribe();
        }

        super.subscribeListeners();
    }

    @Override
    protected void unsubscribeListeners() {
        if (permChecker.hasTargetReadPermission() || permChecker.hasReadRepositoryPermission()) {
            layoutVisibilityListener.unsubscribe();
            layoutResizeListener.unsubscribe();
        }

        super.unsubscribeListeners();
    }
}
