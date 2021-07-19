/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.rsql.RsqlValidationOracle;
import org.eclipse.hawkbit.ui.AbstractHawkbitUI;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.AbstractEventListenersAwareView;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.suppliers.TargetFilterStateDataSupplier;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutVisibilityListener;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutVisibilityListener.VisibilityHandler;
import org.eclipse.hawkbit.ui.filtermanagement.state.FilterManagementUIState;
import org.eclipse.hawkbit.ui.filtermanagement.state.FilterManagementUIState.FilterView;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.spring.annotation.SpringView;
import com.vaadin.spring.annotation.UIScope;
import com.vaadin.ui.Alignment;

/**
 * View for custom target filter management.
 */
@UIScope
@SpringView(name = FilterManagementView.VIEW_NAME, ui = AbstractHawkbitUI.class)
public class FilterManagementView extends AbstractEventListenersAwareView {
    private static final long serialVersionUID = 1L;

    public static final String VIEW_NAME = "targetFilters";

    private final FilterManagementUIState filterManagementUIState;

    private final TargetFilterGridLayout targetFilterGridLayout;
    private final TargetFilterDetailsLayout targetFilterDetailsLayout;

    private final transient LayoutVisibilityListener layoutVisibilityListener;

    @Autowired
    FilterManagementView(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final FilterManagementUIState filterManagementUIState, final RsqlValidationOracle rsqlValidationOracle,
            final TargetFilterQueryManagement targetFilterQueryManagement, final SpPermissionChecker permissionChecker,
            final UINotification notification, final UiProperties uiProperties, final EntityFactory entityFactory,
            final TargetManagement targetManagement, final DistributionSetManagement distributionSetManagement,
            final TargetFilterStateDataSupplier targetFilterStateDataSupplier) {
        this.filterManagementUIState = filterManagementUIState;

        final CommonUiDependencies uiDependencies = new CommonUiDependencies(i18n, entityFactory, eventBus,
                notification, permissionChecker);

        this.targetFilterGridLayout = new TargetFilterGridLayout(uiDependencies, targetFilterQueryManagement,
                targetManagement, distributionSetManagement, filterManagementUIState);

        this.targetFilterDetailsLayout = new TargetFilterDetailsLayout(uiDependencies, uiProperties,
                rsqlValidationOracle, targetFilterQueryManagement, targetFilterStateDataSupplier,
                filterManagementUIState.getDetailsLayoutUiState());

        addEventAwareLayouts(Arrays.asList(targetFilterGridLayout, targetFilterDetailsLayout));

        final Map<EventLayout, VisibilityHandler> layoutVisibilityHandlers = new EnumMap<>(EventLayout.class);
        layoutVisibilityHandlers.put(EventLayout.TARGET_FILTER_QUERY_LIST,
                new VisibilityHandler(this::showTfqListLayout, this::showTfqFormLayout));
        layoutVisibilityHandlers.put(EventLayout.TARGET_FILTER_QUERY_FORM,
                new VisibilityHandler(this::showTfqFormLayout, this::showTfqListLayout));
        this.layoutVisibilityListener = new LayoutVisibilityListener(eventBus,
                new EventViewAware(EventView.TARGET_FILTER), layoutVisibilityHandlers);
    }

    @Override
    protected void buildLayout() {
        setMargin(false);
        setSpacing(false);
        setSizeFull();

        addComponent(targetFilterGridLayout);
        setComponentAlignment(targetFilterGridLayout, Alignment.TOP_CENTER);
        setExpandRatio(targetFilterGridLayout, 1.0F);

        targetFilterDetailsLayout.setVisible(false);
        addComponent(targetFilterDetailsLayout);
        setComponentAlignment(targetFilterDetailsLayout, Alignment.TOP_CENTER);
        setExpandRatio(targetFilterDetailsLayout, 1.0F);
    }

    private void showTfqListLayout() {
        filterManagementUIState.setCurrentView(FilterView.FILTERS);
        targetFilterGridLayout.setVisible(true);
        targetFilterDetailsLayout.setVisible(false);
    }

    private void showTfqFormLayout() {
        filterManagementUIState.setCurrentView(FilterView.DETAILS);
        targetFilterGridLayout.setVisible(false);
        targetFilterDetailsLayout.setVisible(true);
    }

    @Override
    protected void restoreState() {
        if (FilterView.FILTERS == filterManagementUIState.getCurrentView()) {
            showTfqListLayout();
        } else if (FilterView.DETAILS == filterManagementUIState.getCurrentView()) {
            showTfqFormLayout();
        }

        super.restoreState();
    }

    @Override
    public String getViewName() {
        return FilterManagementView.VIEW_NAME;
    }

    @Override
    protected void subscribeListeners() {
        layoutVisibilityListener.subscribe();

        super.subscribeListeners();
    }

    @Override
    protected void unsubscribeListeners() {
        layoutVisibilityListener.unsubscribe();

        super.unsubscribeListeners();
    }
}
