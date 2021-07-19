/** 
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.layout.listener;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.PinningChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.PinningChangedEventPayload.PinningChangedEventType;
import org.eclipse.hawkbit.ui.common.grid.support.PinSupport;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

/**
 * Event change listener for pinning changed
 *
 * @param <F>
 *            Generic type
 */
public class PinningChangedListener<F> extends TopicEventListener {
    private final Class<? extends ProxyIdentifiableEntity> entityType;
    private final PinSupport<? extends ProxyIdentifiableEntity, F> pinSupport;
    private final Runnable updatePinCountInfo;

    /**
     * Constructor for PinningChangedListener
     *
     * @param eventBus
     *            UIEventBus
     * @param entityType
     *            Identifiable Entity type
     * @param pinSupport
     *            Pin support
     */
    public PinningChangedListener(final UIEventBus eventBus, final Class<? extends ProxyIdentifiableEntity> entityType,
            final PinSupport<? extends ProxyIdentifiableEntity, F> pinSupport) {
        this(eventBus, entityType, pinSupport, null);
    }

    /**
     * Constructor for PinningChangedListener
     *
     * @param eventBus
     *            UIEventBus
     * @param entityType
     *            Identifiable Entity type
     * @param pinSupport
     *            Pin support
     * @param updatePinCountInfo
     *            Callback to update pinned entities count info on pin change
     */
    public PinningChangedListener(final UIEventBus eventBus, final Class<? extends ProxyIdentifiableEntity> entityType,
            final PinSupport<? extends ProxyIdentifiableEntity, F> pinSupport, final Runnable updatePinCountInfo) {
        super(eventBus, EventTopics.PINNING_CHANGED);

        this.entityType = entityType;
        this.pinSupport = pinSupport;
        this.updatePinCountInfo = updatePinCountInfo;
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    private void onPinEvent(final PinningChangedEventPayload<F> eventPayload) {
        if (!suitableEntityType(eventPayload.getEntityType())) {
            return;
        }

        if (eventPayload.getPinningChangedEventType() == PinningChangedEventType.ENTITY_PINNED) {
            pinSupport.updatePinFilter(eventPayload.getEntityId());
        } else {
            pinSupport.updatePinFilter(null);
        }

        if (updatePinCountInfo != null) {
            updatePinCountInfo.run();
        }
    }

    private boolean suitableEntityType(final Class<? extends ProxyIdentifiableEntity> type) {
        return entityType != null && entityType.equals(type);
    }
}
