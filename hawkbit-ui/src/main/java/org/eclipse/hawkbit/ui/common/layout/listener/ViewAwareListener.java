/** 
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.layout.listener;

import java.util.Collection;

import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.vaadin.spring.events.EventBus.UIEventBus;

/**
 * Event listener for view aware
 */
public abstract class ViewAwareListener extends TopicEventListener {
    private final EventViewAware viewAware;

    /**
     * Constructor for ViewAwareListener
     *
     * @param eventBus
     *            ViewAwareListener
     * @param topic
     *            Topic
     * @param viewAware
     *            EventViewAware
     */
    protected ViewAwareListener(final UIEventBus eventBus, final String topic, final EventViewAware viewAware) {
        super(eventBus, topic);

        this.viewAware = viewAware;
    }

    /**
     * Constructor for ViewAwareListener
     *
     * @param eventBus
     *            ViewAwareListener
     * @param topics
     *            Topics
     * @param viewAware
     *            EventViewAware
     */
    protected ViewAwareListener(final UIEventBus eventBus, final Collection<String> topics,
            final EventViewAware viewAware) {
        super(eventBus, topics);

        this.viewAware = viewAware;
    }

    /**
     * @return View aware
     */
    public EventViewAware getViewAware() {
        return viewAware;
    }

    /**
     * @return View
     */
    public EventView getView() {
        return viewAware != null ? viewAware.getView() : null;
    }
}
