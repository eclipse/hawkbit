/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettag.filter;

import java.util.Arrays;
import java.util.List;

import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTag;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventLayoutViewAware;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.TargetFilterTabChangedEventPayload;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityModifiedListener.EntityModifiedAwareSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.GridActionsVisibilityListener;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGenericSupport;
import org.eclipse.hawkbit.ui.common.layout.listener.support.EntityModifiedGridRefreshAwareSupport;
import org.eclipse.hawkbit.ui.management.targettag.TargetTagWindowBuilder;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.ui.Accordion;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Target filter tabsheet with 'simple' and 'complex' filter options.
 */
public class MultipleTargetFilter extends Accordion {
    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;
    private final transient UIEventBus eventBus;

    private final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState;

    private final VerticalLayout simpleFilterTab;
    private final TargetTagFilterButtons filterByButtons;
    private final FilterByStatusLayout filterByStatusFooter;
    private final TargetFilterQueryButtons customFilterTab;

    private final transient GridActionsVisibilityListener gridActionsVisibilityListener;
    private final transient EntityModifiedListener<ProxyTag> entityTagModifiedListener;
    private final transient EntityModifiedListener<ProxyTargetFilterQuery> entityFilterQueryModifiedListener;

    MultipleTargetFilter(final CommonUiDependencies uiDependencies,
            final TargetFilterQueryManagement targetFilterQueryManagement,
            final TargetTagManagement targetTagManagement, final TargetManagement targetManagement,
            final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState,
            final TargetTagWindowBuilder targetTagWindowBuilder) {
        this.i18n = uiDependencies.getI18n();
        this.eventBus = uiDependencies.getEventBus();
        this.targetTagFilterLayoutUiState = targetTagFilterLayoutUiState;

        this.filterByButtons = new TargetTagFilterButtons(uiDependencies, targetTagManagement, targetManagement,
                targetTagFilterLayoutUiState, targetTagWindowBuilder);
        this.filterByStatusFooter = new FilterByStatusLayout(i18n, eventBus, targetTagFilterLayoutUiState);
        this.simpleFilterTab = buildSimpleFilterTab();
        this.customFilterTab = new TargetFilterQueryButtons(i18n, eventBus, targetFilterQueryManagement,
                targetTagFilterLayoutUiState);

        final EventLayoutViewAware layoutViewAware = new EventLayoutViewAware(EventLayout.TARGET_TAG_FILTER,
                EventView.DEPLOYMENT);
        this.gridActionsVisibilityListener = new GridActionsVisibilityListener(eventBus, layoutViewAware,
                filterByButtons::hideActionColumns, filterByButtons::showEditColumn, filterByButtons::showDeleteColumn);
        this.entityTagModifiedListener = new EntityModifiedListener.Builder<>(eventBus, ProxyTag.class)
                .parentEntityType(ProxyTarget.class).viewAware(layoutViewAware)
                .entityModifiedAwareSupports(getTagModifiedAwareSupports()).build();
        this.entityFilterQueryModifiedListener = new EntityModifiedListener.Builder<>(eventBus,
                ProxyTargetFilterQuery.class).viewAware(layoutViewAware)
                        .entityModifiedAwareSupports(getFilterQueryModifiedAwareSupports()).build();

        init();
        addTabs();
        addSelectedTabChangeListener(event -> selectedTabChanged());
    }

    private VerticalLayout buildSimpleFilterTab() {
        final VerticalLayout simpleTab = new VerticalLayout();
        simpleTab.setSpacing(false);
        simpleTab.setMargin(false);
        simpleTab.setSizeFull();
        simpleTab.setCaption(i18n.getMessage("caption.filter.simple"));
        simpleTab.addStyleName(SPUIStyleDefinitions.SIMPLE_FILTER_HEADER);

        final VerticalLayout targetTagGridLayout = new VerticalLayout();
        targetTagGridLayout.setSpacing(false);
        targetTagGridLayout.setMargin(false);
        targetTagGridLayout.setSizeFull();
        targetTagGridLayout.setId(UIComponentIdProvider.TARGET_TAG_DROP_AREA_ID);

        targetTagGridLayout.addComponent(filterByButtons.getNoTagButton());
        targetTagGridLayout.addComponent(filterByButtons);
        targetTagGridLayout.setComponentAlignment(filterByButtons, Alignment.MIDDLE_CENTER);
        targetTagGridLayout.setExpandRatio(filterByButtons, 1.0F);

        simpleTab.addComponent(targetTagGridLayout);
        simpleTab.setExpandRatio(targetTagGridLayout, 1.0F);

        simpleTab.addComponent(filterByStatusFooter);
        simpleTab.setComponentAlignment(filterByStatusFooter, Alignment.MIDDLE_CENTER);

        return simpleTab;
    }

    private List<EntityModifiedAwareSupport> getTagModifiedAwareSupports() {
        return Arrays.asList(EntityModifiedGridRefreshAwareSupport.of(filterByButtons::refreshAll),
                EntityModifiedGenericSupport.of(null, null, filterByButtons::resetFilterOnTagsDeleted));
    }

    private List<EntityModifiedAwareSupport> getFilterQueryModifiedAwareSupports() {
        return Arrays.asList(EntityModifiedGridRefreshAwareSupport.of(customFilterTab::refreshAll),
                EntityModifiedGenericSupport.of(null, customFilterTab::reselectFilterOnTfqUpdated,
                        customFilterTab::resetFilterOnTfqDeleted));
    }

    private void init() {
        setSizeFull();
        addStyleName(ValoTheme.ACCORDION_BORDERLESS);
    }

    private void addTabs() {
        addTab(simpleFilterTab).setId(UIComponentIdProvider.SIMPLE_FILTER_ACCORDION_TAB);
        addTab(customFilterTab).setId(UIComponentIdProvider.CUSTOM_FILTER_ACCORDION_TAB);
    }

    /**
     * Update target filter ui state on tab changed
     */
    public void selectedTabChanged() {
        final String selectedTabId = getTab(getSelectedTab()).getId();

        if (UIComponentIdProvider.SIMPLE_FILTER_ACCORDION_TAB.equals(selectedTabId)) {
            customFilterTab.clearAppliedTargetFilterQuery();

            targetTagFilterLayoutUiState.setCustomFilterTabSelected(false);

            eventBus.publish(EventTopics.TARGET_FILTER_TAB_CHANGED, this, TargetFilterTabChangedEventPayload.SIMPLE);
        } else {
            filterByButtons.clearTargetTagFilters();
            filterByStatusFooter.clearStatusAndOverdueFilters();

            targetTagFilterLayoutUiState.setCustomFilterTabSelected(true);

            eventBus.publish(EventTopics.TARGET_FILTER_TAB_CHANGED, this, TargetFilterTabChangedEventPayload.CUSTOM);
        }
    }

    /**
     * Restore the target tag filter layout ui state
     */
    public void restoreState() {
        if (targetTagFilterLayoutUiState.isCustomFilterTabSelected()) {
            customFilterTab.restoreState();

            setSelectedTab(customFilterTab);
        } else {
            filterByButtons.restoreState();
            filterByStatusFooter.restoreState();

            setSelectedTab(simpleFilterTab);
        }
    }

    /**
     * Update components on view enter
     */
    public void onViewEnter() {
        filterByButtons.reevaluateFilter();
        customFilterTab.reevaluateFilter();
    }

    /**
     * Subscribe event listeners
     */
    public void subscribeListeners() {
        gridActionsVisibilityListener.subscribe();
        entityTagModifiedListener.subscribe();
        entityFilterQueryModifiedListener.subscribe();
    }

    /**
     * Unsubscribe event listeners
     */
    public void unsubscribeListeners() {
        gridActionsVisibilityListener.unsubscribe();
        entityTagModifiedListener.unsubscribe();
        entityFilterQueryModifiedListener.unsubscribe();
    }
}
