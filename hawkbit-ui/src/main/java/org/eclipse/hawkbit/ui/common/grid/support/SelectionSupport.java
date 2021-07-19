/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.grid.support;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import javax.annotation.PreDestroy;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.SelectionChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.SelectionChangedEventPayload.SelectionChangedEventType;
import org.springframework.util.CollectionUtils;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.shared.Registration;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Grid.SelectionMode;
import com.vaadin.ui.components.grid.MultiSelectionModel;
import com.vaadin.ui.components.grid.MultiSelectionModel.SelectAllCheckBoxVisibility;
import com.vaadin.ui.components.grid.NoSelectionModel;
import com.vaadin.ui.components.grid.SingleSelectionModel;

/**
 * Support for selection on the grid.
 * 
 * @param <T>
 *            The item-type used by the grid
 */
public class SelectionSupport<T extends ProxyIdentifiableEntity> {
    private final Grid<T> grid;
    private final UIEventBus eventBus;
    private final EventLayout layout;
    private final EventView view;

    private final LongFunction<Optional<T>> mapIdToProxyEntityFunction;
    private final Supplier<Long> selectedEntityIdUiStateProvider;
    private final Consumer<Long> setSelectedEntityIdUiStateCallback;

    private Registration singleSelectListenerRegistration;
    private Registration multiSelectListenerRegistration;

    /**
     * Constructor for grids without selection support
     *
     * @param grid
     *            Vaadin grid
     */
    public SelectionSupport(final Grid<T> grid) {
        this(grid, null, null, null, null, null, null);
    }

    /**
     * Constructor for grids with selection support
     *
     * @param grid
     *            Vaadin grid
     * @param eventBus
     *            UIEventBus
     * @param layout
     *            UIEventBus
     * @param view
     *            EventView
     * @param mapIdToProxyEntityFunction
     *            Function to map id to proxy entity
     * @param selectedEntityIdUiStateProvider
     *            Selected entity id provider
     * @param setSelectedEntityIdUiStateCallback
     *            Callback event to set selected entity id
     */
    // UI state values are nullable
    @SuppressWarnings("squid:S4276")
    public SelectionSupport(final Grid<T> grid, final UIEventBus eventBus, final EventLayout layout,
            final EventView view, final LongFunction<Optional<T>> mapIdToProxyEntityFunction,
            final Supplier<Long> selectedEntityIdUiStateProvider,
            final Consumer<Long> setSelectedEntityIdUiStateCallback) {
        this.grid = grid;
        this.eventBus = eventBus;
        this.layout = layout;
        this.view = view;

        this.mapIdToProxyEntityFunction = mapIdToProxyEntityFunction;
        this.selectedEntityIdUiStateProvider = selectedEntityIdUiStateProvider;
        this.setSelectedEntityIdUiStateCallback = setSelectedEntityIdUiStateCallback;
    }

    /**
     * Disable the selection mode in grid
     */
    public final void disableSelection() {
        grid.setSelectionMode(SelectionMode.NONE);

        removeListeners();
    }

    private void removeListeners() {
        if (singleSelectListenerRegistration != null) {
            singleSelectListenerRegistration.remove();
            singleSelectListenerRegistration = null;
        }

        if (multiSelectListenerRegistration != null) {
            multiSelectListenerRegistration.remove();
            multiSelectListenerRegistration = null;
        }
    }

    /**
     * Enable the single selection in grid
     */
    public final void enableSingleSelection() {
        grid.setSelectionMode(SelectionMode.SINGLE);

        singleSelectListenerRegistration = grid.asSingleSelect().addSingleSelectionListener(event -> {
            final SelectionChangedEventType selectionType = event.getSelectedItem().isPresent()
                    ? SelectionChangedEventType.ENTITY_SELECTED
                    : SelectionChangedEventType.ENTITY_DESELECTED;
            final T itemToSend = event.getSelectedItem().orElse(event.getOldValue());

            sendSelectionChangedEvent(selectionType, itemToSend);
        });
    }

    /**
     * Send selection change event
     *
     * @param selectionType
     *            SelectionChangedEventType
     * @param itemToSend
     *            selected item to send
     *
     */
    public void sendSelectionChangedEvent(final SelectionChangedEventType selectionType, final T itemToSend) {
        if (eventBus == null) {
            return;
        }

        if (SelectionChangedEventType.ENTITY_SELECTED == selectionType && itemToSend == null) {
            return;
        }

        eventBus.publish(EventTopics.SELECTION_CHANGED, grid,
                new SelectionChangedEventPayload<>(selectionType, itemToSend, layout, view));

        updateUiState(selectionType, itemToSend);
    }

    private void updateUiState(final SelectionChangedEventType selectionType, final T itemToSend) {
        if (setSelectedEntityIdUiStateCallback == null) {
            return;
        }

        final Long selectedItemId = SelectionChangedEventType.ENTITY_SELECTED == selectionType ? itemToSend.getId()
                : null;
        setSelectedEntityIdUiStateCallback.accept(selectedItemId);
    }

    /**
     * Enable multiple selection in grid
     */
    public final void enableMultiSelection() {
        grid.setSelectionMode(SelectionMode.MULTI);

        grid.asMultiSelect().setSelectAllCheckBoxVisibility(SelectAllCheckBoxVisibility.VISIBLE);
        multiSelectListenerRegistration = grid.asMultiSelect().addMultiSelectionListener(event -> {
            if (event.getAllSelectedItems().size() == 1) {
                sendSelectionChangedEvent(SelectionChangedEventType.ENTITY_SELECTED,
                        event.getAllSelectedItems().iterator().next());
            } else if (event.getOldSelection().size() == 1) {
                sendSelectionChangedEvent(SelectionChangedEventType.ENTITY_DESELECTED,
                        event.getOldSelection().iterator().next());
            }
        });
    }

    /**
     * @return true if grid selection model is not enable else false
     */
    public boolean isNoSelectionModel() {
        return grid.getSelectionModel() instanceof NoSelectionModel;
    }

    public boolean isSingleSelectionModel() {
        return grid.getSelectionModel() instanceof SingleSelectionModel;
    }

    /**
     * @return true if grid selection model is multi selection else false
     */
    public boolean isMultiSelectionModel() {
        return grid.getSelectionModel() instanceof MultiSelectionModel;
    }

    /**
     * @return Selected items from the grid
     */
    public Set<T> getSelectedItems() {
        if (isNoSelectionModel()) {
            return Collections.emptySet();
        }

        return grid.getSelectedItems();
    }

    /**
     * @return Selected entity from the grid
     */
    public Optional<T> getSelectedEntity() {
        final Set<T> selectedItems = getSelectedItems();

        if (selectedItems.size() == 1) {
            return Optional.of(selectedItems.iterator().next());
        }

        return Optional.empty();
    }

    /**
     * @return Id of selected entity from the grid
     */
    public Optional<Long> getSelectedEntityId() {
        if (isNoSelectionModel() && selectedEntityIdUiStateProvider != null) {
            return Optional.ofNullable(selectedEntityIdUiStateProvider.get());
        }

        return getSelectedEntity().map(ProxyIdentifiableEntity::getId);
    }

    /**
     * Selects the first row if available and enabled.
     *
     * @return True if first row is selected else false
     */
    public boolean selectFirstRow() {
        if (isNoSelectionModel()) {
            return false;
        }

        final List<T> firstItem = grid.getDataCommunicator().fetchItemsWithRange(0, 1);

        if (!CollectionUtils.isEmpty(firstItem)) {
            grid.select(firstItem.get(0));

            return true;
        }

        grid.deselectAll();

        return false;
    }

    /**
     * Select the item in grid
     *
     * @param itemToSelect
     *            Item for selection
     */
    public void select(final T itemToSelect) {
        if (isNoSelectionModel()) {
            return;
        }

        grid.select(itemToSelect);
    }

    /**
     * Select entity based on Id
     *
     * @param entityId
     *            Entity id
     */
    public void selectEntityById(final Long entityId) {
        if (isNoSelectionModel()) {
            return;
        }

        if (mapIdToProxyEntityFunction == null || entityId == null) {
            return;
        }

        mapIdToProxyEntityFunction.apply(entityId).ifPresent(this::select);
    }

    /**
     * Select all items or entities in the grid
     */
    public void selectAll() {
        if (!isMultiSelectionModel()) {
            return;
        }

        grid.asMultiSelect().selectAll();
    }

    /**
     * Deselect the item from the grid
     *
     * @param itemToDeselect
     *            Item for selection
     */
    public void deselect(final T itemToDeselect) {
        if (isNoSelectionModel()) {
            return;
        }

        grid.deselect(itemToDeselect);
    }

    /**
     * Clears the selection.
     */
    public void deselectAll() {
        if (isNoSelectionModel()) {
            return;
        }

        if (!getSelectedItems().isEmpty()) {
            grid.deselectAll();
        }
    }

    /**
     * Restores the last selection
     */
    public void restoreSelection() {
        if (selectedEntityIdUiStateProvider == null) {
            return;
        }

        final Long lastSelectedEntityId = selectedEntityIdUiStateProvider.get();

        if (lastSelectedEntityId != null) {
            selectEntityById(lastSelectedEntityId);
        } else {
            selectFirstRow();
        }
    }

    /**
     * Re-fetches and re-selects currently selected entity
     */
    public void reselectCurrentEntity() {
        getSelectedEntityId().ifPresent(selectedEntityId -> {
            deselectAll();
            selectEntityById(selectedEntityId);
        });
    }

    @PreDestroy
    void destroy() {
        removeListeners();
    }
}
