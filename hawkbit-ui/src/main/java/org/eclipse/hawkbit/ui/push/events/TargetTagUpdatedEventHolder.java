/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.push.events;

import java.util.List;

import org.eclipse.hawkbit.repository.eventbus.event.TargetTagUpdateEvent;

/**
 * EventHolder for {@link TargetTagUpdateEvent}s.
 *
 */
public class TargetTagUpdatedEventHolder implements EventHolder<TargetTagUpdateEvent> {
    private final List<TargetTagUpdateEvent> events;

    public TargetTagUpdatedEventHolder(final List<TargetTagUpdateEvent> events) {
        this.events = events;
    }

    @Override
    public List<TargetTagUpdateEvent> getEvents() {
        return events;
    }

}
