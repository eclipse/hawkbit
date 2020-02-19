/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.AbstractHawkbitUI;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.filtermanagement.event.CustomFilterUIEvent;
import org.eclipse.hawkbit.ui.filtermanagement.footer.TargetFilterCountMessageLabel;
import org.eclipse.hawkbit.ui.filtermanagement.state.FilterManagementUIState;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.spring.annotation.UIScope;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

/**
 * View for custom target filter management.
 */
@UIScope
@SpringView(name = FilterManagementView.VIEW_NAME, ui = AbstractHawkbitUI.class)
public class FilterManagementView extends VerticalLayout implements View {

    private static final long serialVersionUID = 8751545414237389386L;

    public static final String VIEW_NAME = "targetFilters";

    private final TargetFilterHeader targetFilterHeader;

    private final TargetFilterTable targetFilterTable;

    private final CreateOrUpdateFilterHeader createNewFilterHeader;

    private final CreateOrUpdateFilterTable createNewFilterTable;

    private final FilterManagementUIState filterManagementUIState;

    private final TargetFilterCountMessageLabel targetFilterCountMessageLabel;

    private final transient EventBus.UIEventBus eventBus;

    @Autowired
    FilterManagementView(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final FilterManagementUIState filterManagementUIState,
            final TargetFilterQueryManagement targetFilterQueryManagement, final SpPermissionChecker permissionChecker,
            final UINotification notification, final UiProperties uiProperties, final EntityFactory entityFactory,
            final AutoCompleteTextFieldComponent queryTextField, final TargetManagement targetManagement,
            final TenantConfigurationManagement configManagement, final SystemSecurityContext systemSecurityContext) {
        this.targetFilterHeader = new TargetFilterHeader(eventBus, filterManagementUIState, permissionChecker, i18n);
        this.targetFilterTable = new TargetFilterTable(i18n, notification, eventBus, filterManagementUIState,
                targetFilterQueryManagement, targetManagement, permissionChecker, entityFactory, configManagement,
                systemSecurityContext);
        this.createNewFilterHeader = new CreateOrUpdateFilterHeader(i18n, eventBus, filterManagementUIState,
                targetFilterQueryManagement, permissionChecker, notification, uiProperties, entityFactory,
                queryTextField);
        this.createNewFilterTable = new CreateOrUpdateFilterTable(i18n, eventBus, filterManagementUIState);
        this.filterManagementUIState = filterManagementUIState;
        this.targetFilterCountMessageLabel = new TargetFilterCountMessageLabel(filterManagementUIState, i18n, eventBus);
        this.eventBus = eventBus;
    }

    @PostConstruct
    void init() {
        setSizeFull();
        setImmediate(true);
        buildLayout();
        eventBus.subscribe(this);
    }

    @PreDestroy
    void destroy() {
        eventBus.unsubscribe(this);
    }

    private void buildLayout() {
        setSizeFull();
        setSpacing(false);
        setMargin(false);
        if (filterManagementUIState.isCreateFilterViewDisplayed()) {
            viewCreateTargetFilterLayout();
        } else if (filterManagementUIState.isEditViewDisplayed()) {
            viewTargetFilterDetailLayout();
        } else {
            viewListView();
        }
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final CustomFilterUIEvent custFilterUIEvent) {
        if (custFilterUIEvent == CustomFilterUIEvent.TARGET_FILTER_DETAIL_VIEW) {
            viewTargetFilterDetailLayout();
        } else if (custFilterUIEvent == CustomFilterUIEvent.CREATE_NEW_FILTER_CLICK) {
            this.getUI().access(this::viewCreateTargetFilterLayout);
        } else if (custFilterUIEvent == CustomFilterUIEvent.EXIT_CREATE_OR_UPDATE_FILTRER_VIEW
                || custFilterUIEvent == CustomFilterUIEvent.SHOW_FILTER_MANAGEMENT) {
            UI.getCurrent().access(this::viewListView);
        }
    }

    private void viewCreateTargetFilterLayout() {
        buildFilterDetailOrCreateView();

    }

    private void viewTargetFilterDetailLayout() {
        buildFilterDetailOrCreateView();
    }

    private void buildFilterDetailOrCreateView() {
        removeAllComponents();
        final VerticalLayout tableHeaderLayout = new VerticalLayout();
        tableHeaderLayout.setSizeFull();
        tableHeaderLayout.setSpacing(false);
        tableHeaderLayout.setMargin(false);
        tableHeaderLayout.setStyleName("table-layout");
        tableHeaderLayout.addComponent(createNewFilterHeader);
        tableHeaderLayout.setComponentAlignment(createNewFilterHeader, Alignment.TOP_CENTER);
        tableHeaderLayout.addComponent(createNewFilterTable);
        tableHeaderLayout.setComponentAlignment(createNewFilterTable, Alignment.TOP_CENTER);
        tableHeaderLayout.setExpandRatio(createNewFilterTable, 1.0F);

        addComponent(tableHeaderLayout);
        setComponentAlignment(tableHeaderLayout, Alignment.TOP_CENTER);
        setExpandRatio(tableHeaderLayout, 1.0F);

        final HorizontalLayout targetsCountmessageLabelLayout = addTargetFilterMessageLabel();
        addComponent(targetsCountmessageLabelLayout);
        setComponentAlignment(targetsCountmessageLabelLayout, Alignment.BOTTOM_CENTER);

    }

    private void viewListView() {
        removeAllComponents();
        final VerticalLayout tableListViewLayout = new VerticalLayout();
        tableListViewLayout.setSizeFull();
        tableListViewLayout.setSpacing(false);
        tableListViewLayout.setMargin(false);
        tableListViewLayout.setStyleName("table-layout");
        tableListViewLayout.addComponent(targetFilterHeader);
        tableListViewLayout.setComponentAlignment(targetFilterHeader, Alignment.TOP_CENTER);
        tableListViewLayout.addComponent(targetFilterTable);
        tableListViewLayout.setComponentAlignment(targetFilterTable, Alignment.TOP_CENTER);
        tableListViewLayout.setExpandRatio(targetFilterTable, 1.0F);
        addComponent(tableListViewLayout);
    }

    private HorizontalLayout addTargetFilterMessageLabel() {
        final HorizontalLayout messageLabelLayout = new HorizontalLayout();
        messageLabelLayout.addComponent(targetFilterCountMessageLabel);
        return messageLabelLayout;
    }

    @Override
    public void enter(final ViewChangeEvent event) {
        // This view is constructed in the init() method()
    }

}
