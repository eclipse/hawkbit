/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettag.filter;

import java.util.Collection;

import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.mappers.TargetFilterQueryToProxyTargetFilterMapper;
import org.eclipse.hawkbit.ui.common.data.providers.TargetFilterQueryDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.FilterChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.common.filterlayout.AbstractFilterButtonClickBehaviour.ClickBehaviourType;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.support.FilterSupport;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUITagButtonStyle;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.ui.Button;
import com.vaadin.ui.StyleGenerator;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Target filter query{#link {@link TargetFilterQuery} buttons layout.
 */
public class TargetFilterQueryButtons extends AbstractGrid<ProxyTargetFilterQuery, String> {
    private static final long serialVersionUID = 1L;

    private static final String FILTER_BUTTON_COLUMN_ID = "filterButton";

    private final transient TargetFilterQueryManagement targetFilterQueryManagement;
    private final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState;

    private final CustomTargetTagFilterButtonClick customTargetTagFilterButtonClick;

    TargetFilterQueryButtons(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final TargetFilterQueryManagement targetFilterQueryManagement,
            final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState) {
        super(i18n, eventBus);

        this.targetFilterQueryManagement = targetFilterQueryManagement;
        this.targetTagFilterLayoutUiState = targetTagFilterLayoutUiState;

        this.customTargetTagFilterButtonClick = new CustomTargetTagFilterButtonClick(this::onFilterChangedEvent);

        setFilterSupport(new FilterSupport<>(new TargetFilterQueryDataProvider(targetFilterQueryManagement,
                new TargetFilterQueryToProxyTargetFilterMapper())));

        init();
    }

    /**
     * initializing table.
     */
    @Override
    public void init() {
        super.init();

        setStyleName("type-button-layout");
        addStyleName(ValoTheme.TABLE_NO_STRIPES);
        addStyleName(ValoTheme.TABLE_NO_HORIZONTAL_LINES);
        addStyleName(ValoTheme.TABLE_NO_VERTICAL_LINES);
        addStyleName(ValoTheme.TABLE_BORDERLESS);
        addStyleName(ValoTheme.TABLE_COMPACT);

        removeHeaderRow(0);
        setCaption(i18n.getMessage(UIMessageIdProvider.CAPTION_FILTER_CUSTOM));
    }

    @Override
    public String getGridId() {
        return UIComponentIdProvider.CUSTOM_TARGET_TAG_TABLE_ID;
    }

    @Override
    public void addColumns() {
        final StyleGenerator<ProxyTargetFilterQuery> styleGenerator = item -> {
            if (customTargetTagFilterButtonClick.isFilterPreviouslyClicked(item)) {
                return SPUIStyleDefinitions.SP_FILTER_BTN_CLICKED_STYLE;
            } else {
                return null;
            }
        };
        GridComponentBuilder.addComponentColumn(this, this::buildTfqButton, styleGenerator)
                .setId(FILTER_BUTTON_COLUMN_ID);
    }

    private Button buildTfqButton(final ProxyTargetFilterQuery filterQuery) {
        final Button tfqButton = SPUIComponentProvider.getButton(
                UIComponentIdProvider.CUSTOM_FILTER_BUTTON_PREFIX + "." + filterQuery.getId(), filterQuery.getName(),
                filterQuery.getName(), null, false, null, SPUITagButtonStyle.class);
        tfqButton.setWidthFull();

        tfqButton.addClickListener(event -> customTargetTagFilterButtonClick.processFilterClick(filterQuery));

        return tfqButton;
    }

    private void onFilterChangedEvent(final ProxyTargetFilterQuery targetFilterQueryFilter,
            final ClickBehaviourType clickType) {
        getDataCommunicator().reset();

        final Long targetFilterQueryId = ClickBehaviourType.CLICKED == clickType ? targetFilterQueryFilter.getId()
                : null;

        publishFilterChangedEvent(targetFilterQueryId);
    }

    private void publishFilterChangedEvent(final Long targetFilterQueryId) {
        eventBus.publish(EventTopics.FILTER_CHANGED, this, new FilterChangedEventPayload<>(ProxyTarget.class,
                FilterType.QUERY, targetFilterQueryId, EventView.DEPLOYMENT));

        targetTagFilterLayoutUiState.setClickedTargetFilterQueryId(targetFilterQueryId);
    }

    /**
     * Remove applied target filter query
     */
    public void clearAppliedTargetFilterQuery() {
        if (customTargetTagFilterButtonClick.getPreviouslyClickedFilterId() != null) {
            customTargetTagFilterButtonClick.setPreviouslyClickedFilterId(null);
            targetTagFilterLayoutUiState.setClickedTargetFilterQueryId(null);
        }
    }

    /**
     * Reselect filter when target filter query updated
     *
     * @param updatedTargetFilterQueryIds
     *            List of update query id
     */
    public void reselectFilterOnTfqUpdated(final Collection<Long> updatedTargetFilterQueryIds) {
        if (isClickedTfqInIds(updatedTargetFilterQueryIds)) {
            publishFilterChangedEvent(customTargetTagFilterButtonClick.getPreviouslyClickedFilterId());
        }
    }

    private boolean isClickedTfqInIds(final Collection<Long> tfqIds) {
        final Long clickedTargetFilterQueryId = customTargetTagFilterButtonClick.getPreviouslyClickedFilterId();

        return clickedTargetFilterQueryId != null && tfqIds.contains(clickedTargetFilterQueryId);
    }

    /**
     * Reselect filter when target filter query deleted
     *
     * @param deletedTargetFilterQueryIds
     *            List of deleted query id
     */
    public void resetFilterOnTfqDeleted(final Collection<Long> deletedTargetFilterQueryIds) {
        if (isClickedTfqInIds(deletedTargetFilterQueryIds)) {
            customTargetTagFilterButtonClick.setPreviouslyClickedFilterId(null);
            publishFilterChangedEvent(null);
        }
    }

    @Override
    public void restoreState() {
        final Long targetFilterQueryIdToRestore = targetTagFilterLayoutUiState.getClickedTargetFilterQueryId();

        if (targetFilterQueryIdToRestore != null) {
            if (targetFilterQueryExists(targetFilterQueryIdToRestore)) {
                customTargetTagFilterButtonClick
                        .setPreviouslyClickedFilterId(targetTagFilterLayoutUiState.getClickedTargetFilterQueryId());
            } else {
                targetTagFilterLayoutUiState.setClickedTargetFilterQueryId(null);
            }
        }
    }

    private boolean targetFilterQueryExists(final Long targetFilterQueryId) {
        return targetFilterQueryManagement.get(targetFilterQueryId).isPresent();
    }

    /**
     * Re-evaluates a filter (usually after view enter).
     *
     */
    public void reevaluateFilter() {
        final Long clickedTargetFilterQueryId = customTargetTagFilterButtonClick.getPreviouslyClickedFilterId();

        if (clickedTargetFilterQueryId != null && !targetFilterQueryExists(clickedTargetFilterQueryId)) {
            customTargetTagFilterButtonClick.setPreviouslyClickedFilterId(null);
            publishFilterChangedEvent(null);
        }
    }
}
