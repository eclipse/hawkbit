/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.tenantconfiguration;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.eclipse.hawkbit.ui.AbstractHawkbitUI;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.ViewNameAware;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigWindow;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.tenantconfiguration.ConfigurationItem.ConfigurationItemChangeListener;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;
import com.vaadin.data.Binder;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
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
public class TenantConfigurationDashboardView extends CustomComponent
        implements View, ViewNameAware, ConfigurationItemChangeListener {
    private static final long serialVersionUID = 1L;

    public static final String VIEW_NAME = "spSystemConfig";

    private final VaadinMessageSource i18n;
    private final UiProperties uiProperties;
    private final UINotification uINotification;

    private final List<BaseConfigurationView<? extends ProxySystemConfigWindow>> autowiredConfigurationViews;
    private List<BaseConfigurationView<? extends ProxySystemConfigWindow>> filteredConfigurationViews;

    private Button saveConfigurationBtn;
    private Button undoConfigurationBtn;

    private final List<Binder<? extends ProxySystemConfigWindow>> binders = Lists.newArrayList();

    @Autowired
    TenantConfigurationDashboardView(final VaadinMessageSource i18n, final UiProperties uiProperties,
            final UINotification uINotification,
            final List<BaseConfigurationView<? extends ProxySystemConfigWindow>> configurationViews) {
        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.uINotification = uINotification;
        this.autowiredConfigurationViews = configurationViews;
    }

    /**
     * Init method adds all Configuration Views to the list of Views.
     */
    @PostConstruct
    public void init() {
        // filter the autowired views
        filteredConfigurationViews = autowiredConfigurationViews.stream().filter(c -> c.getComponentCount() > 0)
                .filter(ConfigurationGroup::show).collect(Collectors.toList());
        // get and add the binders
        filteredConfigurationViews.forEach(entry -> binders.add(entry.getBinder()));

        final Panel rootPanel = new Panel();
        rootPanel.setStyleName("tenantconfig-root");

        final VerticalLayout rootLayout = new VerticalLayout();
        rootLayout.setSizeFull();
        rootLayout.setMargin(true);
        rootLayout.setSpacing(true);
        filteredConfigurationViews.forEach(rootLayout::addComponent);

        final HorizontalLayout buttonContent = saveConfigurationButtonsLayout();
        rootLayout.addComponent(buttonContent);
        rootLayout.setComponentAlignment(buttonContent, Alignment.BOTTOM_LEFT);
        rootPanel.setContent(rootLayout);
        setCompositionRoot(rootPanel);

        filteredConfigurationViews.forEach(view -> view.addChangeListener(this));
        binders.forEach(binder -> binder.addStatusChangeListener(event -> {
            saveConfigurationBtn.setEnabled(event.getBinder().isValid());
            undoConfigurationBtn.setEnabled(event.getBinder().isValid());
        }));
    }

    private HorizontalLayout saveConfigurationButtonsLayout() {
        final HorizontalLayout hlayout = new HorizontalLayout();
        hlayout.setSpacing(true);
        saveConfigurationBtn = SPUIComponentProvider.getButton(UIComponentIdProvider.SYSTEM_CONFIGURATION_SAVE, "", "",
                "", true, VaadinIcons.HARDDRIVE, SPUIButtonStyleNoBorder.class);
        saveConfigurationBtn.setEnabled(false);
        saveConfigurationBtn.setDescription(i18n.getMessage("configuration.savebutton.tooltip"));
        saveConfigurationBtn.addClickListener(event -> saveConfiguration());
        hlayout.addComponent(saveConfigurationBtn);

        undoConfigurationBtn = SPUIComponentProvider.getButton(UIComponentIdProvider.SYSTEM_CONFIGURATION_CANCEL, "",
                "", "", true, VaadinIcons.ARROW_BACKWARD, SPUIButtonStyleNoBorder.class);
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
        final boolean isUserInputValid = filteredConfigurationViews.stream()
                .allMatch(ConfigurationGroup::isUserInputValid);

        if (!isUserInputValid) {
            uINotification.displayValidationError(i18n.getMessage("notification.configuration.save.notpossible"));
            return;
        }
        // Iterate through all View Beans and call their save implementation
        filteredConfigurationViews.forEach(ConfigurationGroup::save);

        // More methods
        saveConfigurationBtn.setEnabled(false);
        undoConfigurationBtn.setEnabled(false);
        uINotification.displaySuccess(i18n.getMessage("notification.configuration.save.successful"));
    }

    private void undoConfiguration() {
        filteredConfigurationViews.forEach(ConfigurationGroup::undo);
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

    @Override
    public String getViewName() {
        return TenantConfigurationDashboardView.VIEW_NAME;
    }
}
