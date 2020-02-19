/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.tenantconfiguration;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.eclipse.hawkbit.ControllerPollProperties;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.RepositoryProperties;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SecurityTokenGenerator;
import org.eclipse.hawkbit.ui.AbstractHawkbitUI;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.management.event.ManagementUIEvent;
import org.eclipse.hawkbit.ui.tenantconfiguration.ConfigurationItem.ConfigurationItemChangeListener;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.google.common.collect.Lists;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.FontAwesome;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.spring.annotation.ViewScope;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Link;
import com.vaadin.ui.Panel;
import com.vaadin.ui.VerticalLayout;

/**
 * Main UI for the system configuration view.
 */
@ViewScope
@SpringView(name = TenantConfigurationDashboardView.VIEW_NAME, ui = AbstractHawkbitUI.class)
public class TenantConfigurationDashboardView extends CustomComponent implements View, ConfigurationItemChangeListener {

    public static final String VIEW_NAME = "spSystemConfig";
    private static final long serialVersionUID = 1L;

    private final DefaultDistributionSetTypeLayout defaultDistributionSetTypeLayout;

    private final RepositoryConfigurationView repositoryConfigurationView;

    private final AuthenticationConfigurationView authenticationConfigurationView;

    private final PollingConfigurationView pollingConfigurationView;

    private final RolloutConfigurationView rolloutConfigurationView;

    private final VaadinMessageSource i18n;

    private final UiProperties uiProperties;

    private final UINotification uINotification;

    private Button saveConfigurationBtn;
    private Button undoConfigurationBtn;

    private final transient UIEventBus eventBus;

    private final List<ConfigurationGroup> configurationViews = Lists.newArrayListWithExpectedSize(3);

    @Autowired(required = false)
    private Collection<ConfigurationGroup> customConfigurationViews;

    @Autowired
    TenantConfigurationDashboardView(final UIEventBus eventBus, final VaadinMessageSource i18n,
            final UiProperties uiProperties,
                                     final UINotification uINotification, final SystemManagement systemManagement,
                                     final DistributionSetTypeManagement distributionSetTypeManagement,
                                     final TenantConfigurationManagement tenantConfigurationManagement,
                                     final SecurityTokenGenerator securityTokenGenerator,
                                     final ControllerPollProperties controllerPollProperties,
                                     final SpPermissionChecker permChecker,
                                     final RepositoryProperties repositoryProperties) {
        this.defaultDistributionSetTypeLayout = new DefaultDistributionSetTypeLayout(systemManagement,
                distributionSetTypeManagement, i18n, permChecker);
        this.authenticationConfigurationView = new AuthenticationConfigurationView(i18n, tenantConfigurationManagement,
                securityTokenGenerator, uiProperties);
        this.pollingConfigurationView = new PollingConfigurationView(i18n, controllerPollProperties,
                tenantConfigurationManagement);
        this.repositoryConfigurationView = new RepositoryConfigurationView(i18n, tenantConfigurationManagement,
                uiProperties, repositoryProperties);
        this.rolloutConfigurationView = new RolloutConfigurationView(i18n, tenantConfigurationManagement, uiProperties);

        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.uINotification = uINotification;
        this.eventBus = eventBus;
    }

    /**
     * Init method adds all Configuration Views to the list of Views.
     */
    @PostConstruct
    public void init() {
        if (defaultDistributionSetTypeLayout.getComponentCount() > 0) {
            configurationViews.add(defaultDistributionSetTypeLayout);
        }
        configurationViews.add(repositoryConfigurationView);
        configurationViews.add(rolloutConfigurationView);
        configurationViews.add(authenticationConfigurationView);
        configurationViews.add(pollingConfigurationView);
        if (customConfigurationViews != null) {
            configurationViews.addAll(
                    customConfigurationViews.stream().filter(ConfigurationGroup::show).collect(Collectors.toList()));
        }

        final Panel rootPanel = new Panel();
        rootPanel.setStyleName("tenantconfig-root");

        final VerticalLayout rootLayout = new VerticalLayout();
        rootLayout.setSizeFull();
        rootLayout.setMargin(true);
        rootLayout.setSpacing(true);

        configurationViews.forEach(rootLayout::addComponent);

        final HorizontalLayout buttonContent = saveConfigurationButtonsLayout();
        rootLayout.addComponent(buttonContent);
        rootLayout.setComponentAlignment(buttonContent, Alignment.BOTTOM_LEFT);
        rootPanel.setContent(rootLayout);
        setCompositionRoot(rootPanel);

        configurationViews.forEach(view -> view.addChangeListener(this));
    }

    private HorizontalLayout saveConfigurationButtonsLayout() {

        final HorizontalLayout hlayout = new HorizontalLayout();
        hlayout.setSpacing(true);
        saveConfigurationBtn = SPUIComponentProvider.getButton(UIComponentIdProvider.SYSTEM_CONFIGURATION_SAVE, "", "",
                "", true, FontAwesome.SAVE, SPUIButtonStyleNoBorder.class);
        saveConfigurationBtn.setEnabled(false);
        saveConfigurationBtn.setDescription(i18n.getMessage("configuration.savebutton.tooltip"));
        saveConfigurationBtn.addClickListener(event -> saveConfiguration());
        hlayout.addComponent(saveConfigurationBtn);

        undoConfigurationBtn = SPUIComponentProvider.getButton(UIComponentIdProvider.SYSTEM_CONFIGURATION_CANCEL, "",
                "", "", true, FontAwesome.UNDO, SPUIButtonStyleNoBorder.class);
        undoConfigurationBtn.setEnabled(false);
        undoConfigurationBtn.setDescription(i18n.getMessage("configuration.cancellbutton.tooltip"));
        undoConfigurationBtn.addClickListener(event -> undoConfiguration());
        hlayout.addComponent(undoConfigurationBtn);

        final Link linkToSystemConfigHelp = SPUIComponentProvider.getHelpLink(i18n,
                uiProperties.getLinks().getDocumentation().getSystemConfigurationView());
        hlayout.addComponent(linkToSystemConfigHelp);

        return hlayout;
    }

    private void saveConfiguration() {

        final boolean isUserInputValid = configurationViews.stream().allMatch(ConfigurationGroup::isUserInputValid);

        if (!isUserInputValid) {
            uINotification.displayValidationError(i18n.getMessage("notification.configuration.save.notpossible"));
            return;
        }
        configurationViews.forEach(ConfigurationGroup::save);

        // More methods
        saveConfigurationBtn.setEnabled(false);
        undoConfigurationBtn.setEnabled(false);
        uINotification.displaySuccess(i18n.getMessage("notification.configuration.save.successful"));

        // publish the event to indicate to re-load action history layout in
        // case of multiassignments
        eventBus.publish(this, ManagementUIEvent.SAVE_TENANT_CONFIGURATION);
    }

    private void undoConfiguration() {
        configurationViews.forEach(ConfigurationGroup::undo);
        // More methods
        saveConfigurationBtn.setEnabled(false);
        undoConfigurationBtn.setEnabled(false);
    }

    @Override
    public void configurationHasChanged() {
        saveConfigurationBtn.setEnabled(true);
        undoConfigurationBtn.setEnabled(true);
    }

    @Override
    public void enter(final ViewChangeEvent event) {
        // This view is constructed in the init() method()
    }

}
