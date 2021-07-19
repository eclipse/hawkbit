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

import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventLayoutViewAware;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.vaadin.spring.events.EventBus.UIEventBus;

/**
 * Abstract class for layout view aware listener
 */
public abstract class LayoutViewAwareListener extends TopicEventListener {
    private final EventLayoutViewAware layoutViewAware;

    /**
     * Constructor for LayoutViewAwareListener
     *
     * @param eventBus
     *            LayoutViewAwareListener
     * @param topic
     *            Topic
     * @param layoutViewAware
     *            EventLayoutViewAware
     */
    protected LayoutViewAwareListener(final UIEventBus eventBus, final String topic,
            final EventLayoutViewAware layoutViewAware) {
        super(eventBus, topic);

        this.layoutViewAware = layoutViewAware;
    }

    /**
     * Constructor for LayoutViewAwareListener
     *
     * @param eventBus
     *            LayoutViewAwareListener
     * @param topics
     *            Topics
     * @param layoutViewAware
     *            EventLayoutViewAware
     */
    protected LayoutViewAwareListener(final UIEventBus eventBus, final Collection<String> topics,
            final EventLayoutViewAware layoutViewAware) {
        super(eventBus, topics);

        this.layoutViewAware = layoutViewAware;
    }

    /**
     * @return layout view aware
     */
    public EventLayoutViewAware getLayoutViewAware() {
        return layoutViewAware;
    }

    /**
     * @return view
     */
    public EventView getView() {
        return layoutViewAware.getView();
    }

    /**
     * @return layout
     */
    public EventLayout getLayout() {
        return layoutViewAware.getLayout();
    }
}
