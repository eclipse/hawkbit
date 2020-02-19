/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
/*
 * Licenses The source code is released under Apache 2.0. The application uses the Vaadin Charts 2
 * add-on, which is released under the Commercial Vaadin Addon License:
 * https://vaadin.com/license/cval-3
 */
package org.eclipse.hawkbit.ui.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.HawkbitServerProperties;
import org.eclipse.hawkbit.im.authentication.PermissionService;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.UserDetailsFormatter;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.menu.DashboardEvent.PostViewChangeEvent;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.vaadin.server.FontAwesome;
import com.vaadin.server.Page;
import com.vaadin.server.Resource;
import com.vaadin.server.ThemeResource;
import com.vaadin.spring.annotation.SpringComponent;
import com.vaadin.spring.annotation.UIScope;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Label;
import com.vaadin.ui.Link;
import com.vaadin.ui.MenuBar;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

/**
 * A responsive menu component providing user information and the controls for
 * primary navigation between the views.
 */
@UIScope
@SpringComponent
public final class DashboardMenu extends CustomComponent {

    private static final String ID = "dashboard-menu";

    private static final String LOGOUT_BASE = "/UI/logout";
    private static final String LOGIN_BASE = "/UI/login";

    private final VaadinMessageSource i18n;

    private final UiProperties uiProperties;

    private final transient HawkbitServerProperties serverProperties;

    private static final long serialVersionUID = 5394474618559481462L;

    // this should be resolved when we introduce event bus on UI to just inform
    // the buttons directly via events
    private final List<ValoMenuItemButton> menuButtons = new ArrayList<>();

    private final transient PermissionService permissionService;

    private final List<DashboardMenuItem> dashboardVaadinViews;

    private String initialViewName;

    private boolean accessibleViewsEmpty;

    @Autowired
    DashboardMenu(final VaadinMessageSource i18n, final UiProperties uiProperties,
            final HawkbitServerProperties serverProperties, final PermissionService permissionService,
            final List<DashboardMenuItem> dashboardVaadinViews) {
        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.serverProperties = serverProperties;
        this.permissionService = permissionService;
        this.dashboardVaadinViews = dashboardVaadinViews;
    }

    /**
     * initializing the view and creating the layout, cannot be done in the
     * constructor because the constructor will be called by spring and the
     * dashboard must be initialized when the dashboard UI is creating.
     */
    public void init() {
        initialViewName = "";
        addStyleName("valo-menu");
        setId(ID);
        setSizeUndefined();
        setHeight("100%");
        setCompositionRoot(buildContent());
    }

    private Component buildContent() {
        final VerticalLayout dashboardMenuLayout = new VerticalLayout();
        dashboardMenuLayout.setSizeFull();
        final VerticalLayout menuContent = getMenuLayout();
        menuContent.addComponent(buildUserMenu(uiProperties));
        menuContent.addComponent(buildToggleButton());

        final VerticalLayout menus = buildMenuItems();
        final VerticalLayout links = buildLinksAndVersion();
        menus.addComponent(links);
        menus.setComponentAlignment(links, Alignment.BOTTOM_CENTER);
        menus.setExpandRatio(links, 1.0F);
        menuContent.addComponent(menus);
        menuContent.setExpandRatio(menus, 1.0F);

        dashboardMenuLayout.addComponent(menuContent);
        return dashboardMenuLayout;
    }

    private static VerticalLayout getMenuLayout() {
        final VerticalLayout menuContent = new VerticalLayout();
        menuContent.addStyleName(ValoTheme.MENU_PART);
        menuContent.addStyleName("sidebar");

        menuContent.addStyleName("no-vertical-drag-hints");
        menuContent.addStyleName("no-horizontal-drag-hints");
        menuContent.setWidth(null);
        menuContent.setHeight("100%");
        return menuContent;
    }

    private VerticalLayout buildLinksAndVersion() {
        final VerticalLayout links = new VerticalLayout();
        links.setSpacing(true);
        links.addStyleName("links");
        final String linkStyle = "v-link";

        if (!uiProperties.getLinks().getDocumentation().getRoot().isEmpty()) {
            final Link docuLink = SPUIComponentProvider.getLink(UIComponentIdProvider.LINK_DOCUMENTATION,
                    i18n.getMessage("link.documentation.name"), uiProperties.getLinks().getDocumentation().getRoot(),
                    FontAwesome.QUESTION_CIRCLE, "_blank", linkStyle);
            docuLink.setSizeFull();
            links.addComponent(docuLink);
            links.setComponentAlignment(docuLink, Alignment.BOTTOM_CENTER);
        }

        if (!uiProperties.getLinks().getUserManagement().isEmpty()) {
            final Link userManagementLink = SPUIComponentProvider.getLink(UIComponentIdProvider.LINK_USERMANAGEMENT,
                    i18n.getMessage("link.usermanagement.name"), uiProperties.getLinks().getUserManagement(),
                    FontAwesome.USERS, "_blank", linkStyle);
            links.addComponent(userManagementLink);
            userManagementLink.setSizeFull();
            links.setComponentAlignment(userManagementLink, Alignment.BOTTOM_CENTER);
        }

        if (!uiProperties.getLinks().getSupport().isEmpty()) {
            final Link supportLink = SPUIComponentProvider.getLink(UIComponentIdProvider.LINK_SUPPORT,
                    i18n.getMessage("link.support.name"), uiProperties.getLinks().getSupport(), FontAwesome.ENVELOPE_O,
                    "", linkStyle);
            supportLink.setSizeFull();
            links.addComponent(supportLink);
            links.setComponentAlignment(supportLink, Alignment.BOTTOM_CENTER);

        }

        final Component buildVersionInfo = buildVersionInfo();
        links.addComponent(buildVersionInfo);
        links.setComponentAlignment(buildVersionInfo, Alignment.BOTTOM_CENTER);
        links.setSizeFull();
        links.setHeightUndefined();
        return links;
    }

    private static Resource getImage(final boolean gravatar) {
        if (!gravatar) {
            return new ThemeResource("images/profile-pic-57px.jpg");
        }

        return UserDetailsFormatter.getCurrentUserEmail().map(email -> (Resource) new GravatarResource(email))
                .orElse(new ThemeResource("images/profile-pic-57px.jpg"));

    }

    private Component buildUserMenu(final UiProperties uiProperties) {
        final MenuBar settings = new MenuBar();
        settings.addStyleName("user-menu");
        settings.setHtmlContentAllowed(true);

        final MenuItem settingsItem = settings.addItem("", getImage(uiProperties.isGravatar()), null);

        final String formattedTenant = UserDetailsFormatter.formatCurrentTenant();
        if (!StringUtils.isEmpty(formattedTenant)) {
            settingsItem.setText(formattedTenant);
            UserDetailsFormatter.getCurrentTenant().ifPresent(tenant -> settingsItem.setDescription(i18n
                    .getMessage("menu.user.description", tenant, UserDetailsFormatter.getCurrentUser().getUsername())));
        } else {
            settingsItem.setText("...");
        }

        settingsItem.setStyleName("user-menuitem");

        final String logoutUrl = generateLogoutUrl();

        settingsItem.addItem(i18n.getMessage("label.sign.out"),
                selectedItem -> Page.getCurrent().setLocation(logoutUrl));
        return settings;
    }

    private static String generateLogoutUrl() {
        final UriComponentsBuilder logout = ServletUriComponentsBuilder.fromCurrentContextPath().path(LOGOUT_BASE);

        UserDetailsFormatter.getCurrentTenant().ifPresent(tenant -> logout.queryParam("login",
                UriComponentsBuilder.fromPath(LOGIN_BASE).queryParam("tenant", tenant).build().toUriString()));

        return logout.toUriString();
    }

    private Component buildToggleButton() {
        final Button valoMenuToggleButton = new Button(i18n.getMessage("label.menu"),
                new MenuToggleClickListenerMyClickListener());
        valoMenuToggleButton.setIcon(FontAwesome.LIST);
        valoMenuToggleButton.addStyleName("valo-menu-toggle");
        valoMenuToggleButton.addStyleName(ValoTheme.BUTTON_BORDERLESS);
        valoMenuToggleButton.addStyleName(ValoTheme.BUTTON_SMALL);
        return valoMenuToggleButton;
    }

    private VerticalLayout buildMenuItems() {
        final VerticalLayout menuItemsLayout = new VerticalLayout();
        menuItemsLayout.addStyleName("valo-menuitems");
        menuItemsLayout.setHeight(100.0F, Unit.PERCENTAGE);

        final List<DashboardMenuItem> accessibleViews = getAccessibleViews();
        if (accessibleViews.isEmpty()) {
            accessibleViewsEmpty = true;
            return menuItemsLayout;
        }
        initialViewName = accessibleViews.get(0).getViewName();
        for (final DashboardMenuItem view : accessibleViews) {
            final ValoMenuItemButton menuItemComponent = new ValoMenuItemButton(view);
            menuButtons.add(menuItemComponent);
            menuItemsLayout.addComponent(buildLabelWrapper(menuItemComponent, view.getNotificationUnreadLabel()));
        }
        return menuItemsLayout;
    }

    /**
     * Creates the wrapper which contains the menu item and the adjacent label
     * for displaying the occurred events
     * 
     * @param menuItemButton
     *            the menu item
     * @param notificationLabel
     *            the label for displaying the occurred events
     * @return Component of type CssLayout
     */
    private static Component buildLabelWrapper(final ValoMenuItemButton menuItemButton,
            final Component notificationLabel) {
        final CssLayout dashboardWrapper = new CssLayout(menuItemButton);
        dashboardWrapper.addStyleName("labelwrapper");
        dashboardWrapper.addStyleName(ValoTheme.MENU_ITEM);
        notificationLabel.addStyleName(ValoTheme.MENU_BADGE);
        notificationLabel.setWidthUndefined();
        notificationLabel.setVisible(false);
        notificationLabel.setId(UIComponentIdProvider.NOTIFICATION_MENU_ID + menuItemButton.getCaption().toLowerCase());
        dashboardWrapper.addComponent(notificationLabel);
        return dashboardWrapper;
    }

    /**
     * Returns all views which are currently accessible by the current logged in
     * user.
     *
     * @return a list of all views which are currently visible and accessible
     *         for the current logged in user
     */
    private List<DashboardMenuItem> getAccessibleViews() {
        return this.dashboardVaadinViews.stream()
                .filter(view -> permissionService.hasAtLeastOnePermission(view.getPermissions()))
                .collect(Collectors.toList());
    }

    private Component buildVersionInfo() {
        final Label label = new Label();
        label.setSizeFull();
        label.setStyleName("version-layout");
        label.setValue(serverProperties.getBuild().getVersion());
        return label;
    }

    /**
     * Returns the view name for the start page after login.
     *
     * @return the initialViewName of the start page
     */
    public String getInitialViewName() {
        return initialViewName;
    }

    /**
     * Is a View available.
     *
     * @return the accessibleViewsEmpty <code>true</code> no rights for any view
     *         <code>false</code> a view is available
     */
    public boolean isAccessibleViewsEmpty() {
        return accessibleViewsEmpty;
    }

    /**
     * notifies the dashboard that the view has been changed and the button
     * needs to be re-styled.
     *
     * @param event
     *            the post view change event
     */
    public void postViewChange(final PostViewChangeEvent event) {
        menuButtons.forEach(button -> button.postViewChange(event));
    }

    /**
     * Returns the dashboard view type by a given view name.
     *
     * @param viewName
     *            the name of the view to retrieve
     * @return the dashboard view for a given viewname or {@code null} if view
     *         with given viewname does not exists
     */
    public DashboardMenuItem getByViewName(final String viewName) {

        return dashboardVaadinViews.stream()
                .filter(view -> view.getViewName().equals(viewName)).findAny().orElse(null);
    }

    /**
     * Is the given view accessible.
     *
     * @param viewName
     *            the view name
     * @return <code>true</code> = denied, <code>false</code> = accessible
     */
    public boolean isAccessDenied(final String viewName) {
        return getAccessibleViews().stream()
                .noneMatch(dashboardMenuItem -> dashboardMenuItem.getViewName().equals(viewName));
    }

    private class MenuToggleClickListenerMyClickListener implements ClickListener {

        private static final long serialVersionUID = 1L;
        private static final String STYLE_VISIBLE = "valo-menu-visible";

        @Override
        public void buttonClick(final ClickEvent event) {
            if (getCompositionRoot().getStyleName().contains(STYLE_VISIBLE)) {
                getCompositionRoot().removeStyleName(STYLE_VISIBLE);
            } else {
                getCompositionRoot().addStyleName(STYLE_VISIBLE);
            }
        }
    }

    /**
     * An menu item button wrapper for the dashboard menu item.
     */
    public static final class ValoMenuItemButton extends Button {

        private static final long serialVersionUID = 1L;

        private static final String STYLE_SELECTED = "selected";

        private final DashboardMenuItem view;

        /**
         * creates a new button in case of pressed switches to the given
         * {@code view}.
         *
         * @param view
         *            the view to switch to in case the button is pressed
         */
        public ValoMenuItemButton(final DashboardMenuItem view) {
            this.view = view;
            setPrimaryStyleName("valo-menu-item");
            setIcon(view.getDashboardIcon());
            setCaption(view.getDashboardCaption());
            setDescription(view.getDashboardCaptionLong());
            setId(view.getDashboardCaption().toLowerCase());
            /* Avoid double click */
            setDisableOnClick(true);
            addClickListener(event -> event.getComponent().getUI().getNavigator().navigateTo(view.getViewName()));
        }

        /**
         * notifies the button to change his style.
         *
         * @param event
         *            the post view change event
         */
        public void postViewChange(final PostViewChangeEvent event) {
            removeStyleName(STYLE_SELECTED);
            if (event.getView().equals(view)) {
                addStyleName(STYLE_SELECTED);
                /* disable current selected view */
                setEnabled(false);
            } else {
                /* Enable other views */
                setEnabled(true);
            }
        }
    }
}
