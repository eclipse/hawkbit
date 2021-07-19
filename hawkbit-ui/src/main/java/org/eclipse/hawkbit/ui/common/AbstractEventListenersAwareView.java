/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewBeforeLeaveEvent;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.ui.VerticalLayout;

/**
 * Abstract class managing event listeners aware layouts.
 *
 */
public abstract class AbstractEventListenersAwareView extends VerticalLayout implements View, ViewNameAware {
    private static final long serialVersionUID = 1L;

    private final transient List<EventListenersAwareLayout> eventAwareLayouts = new ArrayList<>();
    private boolean initial;

    /**
     * Adds event aware layout.
     * 
     * @param eventAwareLayout
     *            event aware layout to add
     */
    protected void addEventAwareLayout(final EventListenersAwareLayout eventAwareLayout) {
        if (eventAwareLayout != null) {
            eventAwareLayouts.add(eventAwareLayout);
        }
    }

    /**
     * Adds a list of event aware layouts.
     * 
     * @param eventAwareLayouts
     *            event aware layouts to add
     */
    protected void addEventAwareLayouts(final Collection<EventListenersAwareLayout> eventAwareLayouts) {
        eventAwareLayouts.forEach(this::addEventAwareLayout);
    }

    @PostConstruct
    protected void init() {
        buildLayout();
        initial = true;
    }

    /**
     * Builds view layout.
     * 
     */
    protected abstract void buildLayout();

    @Override
    public void enter(final ViewChangeEvent event) {
        subscribeListeners();

        if (initial) {
            restoreState();
            initial = false;
            return;
        }

        updateLayoutsOnViewEnter();
    }

    /**
     * Subscribes all listeners of added event aware layouts to event bus.
     * 
     */
    protected void subscribeListeners() {
        eventAwareLayouts.forEach(EventListenersAwareLayout::subscribeListeners);
    }

    /**
     * Restores session state of added event aware layouts.
     * 
     */
    protected void restoreState() {
        eventAwareLayouts.forEach(EventListenersAwareLayout::restoreState);
    }

    /**
     * Called on on view enter for added event aware layouts to update their
     * state.
     * 
     */
    protected void updateLayoutsOnViewEnter() {
        eventAwareLayouts.forEach(EventListenersAwareLayout::onViewEnter);
    }

    @Override
    public void beforeLeave(final ViewBeforeLeaveEvent event) {
        unsubscribeListeners();
        event.navigate();
    }

    /**
     * Unsubscribes all listeners of added event aware layouts to event bus.
     * 
     */
    protected void unsubscribeListeners() {
        eventAwareLayouts.forEach(EventListenersAwareLayout::unsubscribeListeners);
    }

    @PreDestroy
    public void destroy() {
        unsubscribeListeners();
    }
}
