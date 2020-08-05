/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettag.filter;

import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.event.FilterHeaderEvent.FilterHeaderEnum;
import org.eclipse.hawkbit.ui.common.event.TargetTagFilterHeaderEvent;
import org.eclipse.hawkbit.ui.components.ConfigMenuBar;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.dd.criteria.ManagementViewClientCriterion;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.management.event.ManagementUIEvent;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.management.targettag.CreateTargetTagLayout;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Accordion;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.MenuBar.Command;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Target filter tabsheet with 'simple' and 'complex' filter options.
 */
public class MultipleTargetFilter extends Accordion implements SelectedTabChangeListener {

    private static final long serialVersionUID = 1L;

    private final TargetTagFilterButtons filterByButtons;

    private final TargetFilterQueryButtons targetFilterQueryButtonsTab;

    private final FilterByStatusLayout filterByStatusFooter;

    private final CustomTargetTagFilterButtonClick customTargetTagFilterButtonClick;

    private final SpPermissionChecker permChecker;

    private final ManagementUIState managementUIState;

    private final VaadinMessageSource i18n;

    private final transient EventBus.UIEventBus eventBus;

    private VerticalLayout simpleFilterTab;

    private ConfigMenuBar menu;

    private final UINotification uiNotification;

    private final transient EntityFactory entityFactory;

    private final transient TargetTagManagement targetTagManagement;

    private VerticalLayout targetTagTableLayout;

    private Button cancelTagButton;

    MultipleTargetFilter(final SpPermissionChecker permChecker, final ManagementUIState managementUIState,
            final VaadinMessageSource i18n, final UIEventBus eventBus,
            final ManagementViewClientCriterion managementViewClientCriterion, final UINotification notification,
            final EntityFactory entityFactory, final TargetFilterQueryManagement targetFilterQueryManagement,
            final TargetTagManagement targetTagManagement) {
        this.filterByButtons = new TargetTagFilterButtons(eventBus, managementUIState, managementViewClientCriterion,
                i18n, notification, permChecker, entityFactory, targetTagManagement);
        this.targetFilterQueryButtonsTab = new TargetFilterQueryButtons(managementUIState, eventBus);
        this.filterByStatusFooter = new FilterByStatusLayout(i18n, eventBus, managementUIState);
        this.customTargetTagFilterButtonClick = new CustomTargetTagFilterButtonClick(eventBus, managementUIState,
                targetFilterQueryManagement);
        this.permChecker = permChecker;
        this.managementUIState = managementUIState;
        this.i18n = i18n;
        this.eventBus = eventBus;
        this.uiNotification = notification;
        this.entityFactory = entityFactory;
        this.targetTagManagement = targetTagManagement;
        buildComponents();
        eventBus.subscribe(this);
    }

    private void buildComponents() {
        filterByStatusFooter.init();

        filterByButtons.addStyleName(SPUIStyleDefinitions.NO_TOP_BORDER);
        targetFilterQueryButtonsTab.init(customTargetTagFilterButtonClick);
        menu = new ConfigMenuBar(permChecker.hasCreateTargetFilterPermission(),
                permChecker.hasUpdateTargetFilterPermission(), permChecker.hasDeleteRepositoryPermission(),
                getAddButtonCommand(), getUpdateButtonCommand(), getDeleteButtonCommand(),
                UIComponentIdProvider.TARGET_MENU_BAR_ID, i18n);
        menu.addStyleName("targetTag");
        addStyleName(ValoTheme.ACCORDION_BORDERLESS);
        addTabs();
        setSizeFull();
        switchToTabSelectedOnLoad();
        addSelectedTabChangeListener(this);
    }

    private void switchToTabSelectedOnLoad() {
        if (managementUIState.isCustomFilterSelected()) {
            this.setSelectedTab(targetFilterQueryButtonsTab);
        } else {
            this.setSelectedTab(simpleFilterTab);
        }
    }

    private void addTabs() {
        this.addTab(getSimpleFilterTab()).setId(UIComponentIdProvider.SIMPLE_FILTER_ACCORDION_TAB);
        this.addTab(getComplexFilterTab()).setId(UIComponentIdProvider.CUSTOM_FILTER_ACCORDION_TAB);
    }

    private Component getSimpleFilterTab() {
        simpleFilterTab = new VerticalLayout();
        targetTagTableLayout = new VerticalLayout();
        targetTagTableLayout.setSizeFull();
        if (menu != null) {
            targetTagTableLayout.addComponent(menu);
            targetTagTableLayout.setComponentAlignment(menu, Alignment.TOP_RIGHT);
        }
        targetTagTableLayout.addComponent(filterByButtons);
        targetTagTableLayout.setComponentAlignment(filterByButtons, Alignment.MIDDLE_CENTER);
        targetTagTableLayout.setId(UIComponentIdProvider.TARGET_TAG_DROP_AREA_ID);
        targetTagTableLayout.setExpandRatio(filterByButtons, 1.0F);
        simpleFilterTab.setCaption(i18n.getMessage("caption.filter.simple"));
        simpleFilterTab.addComponent(targetTagTableLayout);
        simpleFilterTab.setExpandRatio(targetTagTableLayout, 1.0F);
        simpleFilterTab.addComponent(filterByStatusFooter);
        simpleFilterTab.setComponentAlignment(filterByStatusFooter, Alignment.MIDDLE_CENTER);
        simpleFilterTab.setSizeFull();
        simpleFilterTab.addStyleName(SPUIStyleDefinitions.SIMPLE_FILTER_HEADER);
        return simpleFilterTab;
    }

    private Component getComplexFilterTab() {
        targetFilterQueryButtonsTab.setCaption(i18n.getMessage(UIMessageIdProvider.CAPTION_FILTER_CUSTOM));
        return targetFilterQueryButtonsTab;
    }

    @Override
    public void selectedTabChange(final SelectedTabChangeEvent event) {
        if (i18n.getMessage("caption.filter.simple").equals(getSelectedTab().getCaption())) {
            managementUIState.setCustomFilterSelected(false);
            eventBus.publish(this, ManagementUIEvent.RESET_TARGET_FILTER_QUERY);
        } else {
            managementUIState.setCustomFilterSelected(true);
            eventBus.publish(this, ManagementUIEvent.RESET_SIMPLE_FILTERS);
        }
    }

    protected Command getAddButtonCommand() {
        return command -> new CreateTargetTagLayout(i18n, targetTagManagement, entityFactory, eventBus, permChecker,
                uiNotification);
    }

    protected Command getDeleteButtonCommand() {
        return command -> {
            filterByButtons.addDeleteColumn();
            eventBus.publish(this, new TargetTagFilterHeaderEvent(FilterHeaderEnum.SHOW_CANCEL_BUTTON));
        };
    }

    protected Command getUpdateButtonCommand() {
        return command -> {
            filterByButtons.addUpdateColumn();
            eventBus.publish(this, new TargetTagFilterHeaderEvent(FilterHeaderEnum.SHOW_CANCEL_BUTTON));
        };
    }

    protected void processFilterHeaderEvent(final TargetTagFilterHeaderEvent event) {
        if (FilterHeaderEnum.SHOW_MENUBAR == event.getFilterHeaderEnum()
                && targetTagTableLayout.getComponent(0).equals(cancelTagButton)) {
            removeCancelButtonAndAddMenuBar();
        } else if (FilterHeaderEnum.SHOW_CANCEL_BUTTON == event.getFilterHeaderEnum()) {
            removeMenuBarAndAddCancelButton();
        }
    }

    protected void removeMenuBarAndAddCancelButton() {
        targetTagTableLayout.removeComponent(menu);
        targetTagTableLayout.addComponent(createCancelButtonForUpdateOrDeleteTag(), 0);
        targetTagTableLayout.setComponentAlignment(cancelTagButton, Alignment.TOP_RIGHT);
    }

    protected Button createCancelButtonForUpdateOrDeleteTag() {
        cancelTagButton = SPUIComponentProvider.getButton(UIComponentIdProvider.CANCEL_UPDATE_TAG_ID, "", "", null,
                false, FontAwesome.TIMES_CIRCLE, SPUIButtonStyleNoBorder.class);
        cancelTagButton.addClickListener(this::cancelUpdateOrDeleteTag);
        return cancelTagButton;
    }

    protected void removeCancelButtonAndAddMenuBar() {
        targetTagTableLayout.removeComponent(cancelTagButton);
        targetTagTableLayout.addComponent(menu, 0);
        targetTagTableLayout.setComponentAlignment(menu, Alignment.TOP_RIGHT);
        filterByButtons.removeUpdateAndDeleteColumn();
    }

    @SuppressWarnings("squid:S1172")
    protected void cancelUpdateOrDeleteTag(final ClickEvent event) {
        removeCancelButtonAndAddMenuBar();
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    private void onEvent(final TargetTagFilterHeaderEvent event) {
        processFilterHeaderEvent(event);
    }

    public TargetTagFilterButtons getFilterByButtons() {
        return filterByButtons;
    }

    public VerticalLayout getTargetTagTableLayout() {
        return targetTagTableLayout;
    }

}
