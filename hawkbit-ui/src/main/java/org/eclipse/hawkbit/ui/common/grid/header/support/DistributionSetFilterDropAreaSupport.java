/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.grid.header.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.FilterChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.common.layout.listener.EntityDraggingListener;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.management.targettable.TargetGridLayoutUiState;
import org.eclipse.hawkbit.ui.utils.SPUITargetDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.Sizeable.Unit;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.dnd.DropTargetExtension;
import com.vaadin.ui.dnd.event.DropEvent;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Allows to drop a Distribution Set in order to filter for it
 *
 */
public class DistributionSetFilterDropAreaSupport implements HeaderSupport {

    private final VaadinMessageSource i18n;
    private final UIEventBus eventBus;
    private final UINotification notification;

    private final TargetGridLayoutUiState targetGridLayoutUiState;

    private final HorizontalLayout currentDsFilterInfo;
    private final HorizontalLayout dropAreaLayout;

    private EntityDraggingListener draggingListener;

    /**
     * Constructor
     * 
     * @param i18n
     *            i18n
     * @param eventBus
     *            for sending filter event and get informed about started
     *            dragging
     * @param notification
     *            to display notification
     * @param targetGridLayoutUiState
     *            TargetGridLayoutUiState
     */
    public DistributionSetFilterDropAreaSupport(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final UINotification notification, final TargetGridLayoutUiState targetGridLayoutUiState) {
        this.i18n = i18n;
        this.eventBus = eventBus;
        this.notification = notification;
        this.targetGridLayoutUiState = targetGridLayoutUiState;

        this.currentDsFilterInfo = buildCurrentDsFilterInfo();
        this.dropAreaLayout = buildDsDropArea();

        addDropStylingListener();
    }

    private static HorizontalLayout buildCurrentDsFilterInfo() {
        final HorizontalLayout dropArea = new HorizontalLayout();

        dropArea.setId(UIComponentIdProvider.TARGET_DROP_FILTER_ICON);
        dropArea.setStyleName("target-dist-filter-info");
        dropArea.setSizeUndefined();

        return dropArea;
    }

    private HorizontalLayout buildDsDropArea() {
        final HorizontalLayout hintDropFilterLayout = new HorizontalLayout();

        hintDropFilterLayout.addStyleName("filter-drop-hint-layout");
        hintDropFilterLayout.setWidth(100, Unit.PERCENTAGE);

        final DropTargetExtension<HorizontalLayout> dropExtension = new DropTargetExtension<>(hintDropFilterLayout);
        dropExtension.addDropListener(event -> {
            final List<ProxyDistributionSet> dSets = getDroppedDistributionSets(event);
            if (dSets.size() == 1) {
                final ProxyDistributionSet droppedDs = dSets.get(0);
                addDsFilterDropAreaTextField(droppedDs.getNameVersion());

                eventBus.publish(EventTopics.FILTER_CHANGED, this, new FilterChangedEventPayload<>(ProxyTarget.class,
                        FilterType.DISTRIBUTION, droppedDs.getId(), EventView.DEPLOYMENT));
                updateUiState(droppedDs);
            } else {
                notification.displayValidationError(i18n.getMessage("message.onlyone.distribution.dropallowed"));
            }
        });

        hintDropFilterLayout.addComponent(currentDsFilterInfo);
        hintDropFilterLayout.setComponentAlignment(currentDsFilterInfo, Alignment.TOP_CENTER);
        hintDropFilterLayout.setExpandRatio(currentDsFilterInfo, 1.0F);

        return hintDropFilterLayout;
    }

    private static List<ProxyDistributionSet> getDroppedDistributionSets(final DropEvent<?> dropEvent) {
        final List<ProxyDistributionSet> list = new ArrayList<>();
        dropEvent.getDragSourceExtension().ifPresent(dragSource -> {
            final Object dragData = dragSource.getDragData();
            if (dragData instanceof ProxyDistributionSet) {
                list.add((ProxyDistributionSet) dragData);
            }
            if (dragData instanceof List
                    && ((List<?>) dragData).stream().allMatch(element -> element instanceof ProxyDistributionSet)) {
                list.addAll(((List<?>) dragData).stream().map(element -> (ProxyDistributionSet) element)
                        .collect(Collectors.toList()));
            }
        });
        return list;
    }

    private void addDsFilterDropAreaTextField(final String dsNameAndVersion) {
        final Button filterLabelClose = SPUIComponentProvider.getButton("drop.filter.close", "", "", "", true,
                VaadinIcons.CLOSE_CIRCLE, SPUIButtonStyleNoBorder.class);
        filterLabelClose.addClickListener(event -> removeFilter());

        final Label filteredDistLabel = new Label();
        filteredDistLabel.setStyleName(ValoTheme.LABEL_COLORED + " " + ValoTheme.LABEL_SMALL);
        filteredDistLabel.setValue(sanitizeDsNameVersion(dsNameAndVersion));
        filteredDistLabel.setSizeUndefined();

        currentDsFilterInfo.removeAllComponents();
        currentDsFilterInfo.setSizeFull();
        currentDsFilterInfo.addComponent(filteredDistLabel);
        currentDsFilterInfo.addComponent(filterLabelClose);
        currentDsFilterInfo.setExpandRatio(filteredDistLabel, 1.0F);
    }

    private void removeFilter() {
        reset();
        eventBus.publish(EventTopics.FILTER_CHANGED, this, new FilterChangedEventPayload<>(ProxyTarget.class,
                FilterType.DISTRIBUTION, null, EventView.DEPLOYMENT));
    }

    /**
     * Remove the components from current distribution set filter info
     */
    public void reset() {
        currentDsFilterInfo.removeAllComponents();
        currentDsFilterInfo.setSizeUndefined();

        targetGridLayoutUiState.setFilterDsInfo(null);
    }

    private static String sanitizeDsNameVersion(final String dsNameAndVersion) {
        return dsNameAndVersion.length() > SPUITargetDefinitions.DISTRIBUTION_NAME_MAX_LENGTH_ALLOWED
                ? new StringBuilder(
                        dsNameAndVersion.substring(0, SPUITargetDefinitions.DISTRIBUTION_NAME_LENGTH_ON_FILTER))
                                .append("...").toString()
                : dsNameAndVersion;
    }

    private void updateUiState(final ProxyDistributionSet ds) {
        targetGridLayoutUiState.setFilterDsInfo(ds.getInfo());
    }

    private void addDropStylingListener() {
        if (draggingListener == null) {
            draggingListener = new EntityDraggingListener(eventBus,
                    Collections.singletonList(UIComponentIdProvider.DIST_TABLE_ID), dropAreaLayout);
        }

        draggingListener.subscribe();
    }

    @Override
    public Component getHeaderComponent() {
        return dropAreaLayout;
    }

    @Override
    public void restoreState() {
        if (targetGridLayoutUiState.getFilterDsInfo() != null) {
            addDsFilterDropAreaTextField(targetGridLayoutUiState.getFilterDsInfo().getNameVersion());
        }
    }

    @PreDestroy
    void destroy() {
        if (draggingListener != null) {
            draggingListener.unsubscribe();
            draggingListener = null;
        }
    }
}
