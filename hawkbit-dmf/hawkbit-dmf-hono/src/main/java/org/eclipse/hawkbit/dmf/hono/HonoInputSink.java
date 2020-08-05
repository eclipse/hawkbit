/**
 * Copyright (c) 2019 Kiwigrid GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.dmf.hono;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.messaging.SubscribableChannel;

public interface HonoInputSink {
    String DEVICE_CREATED = "device-created";
    String DEVICE_DELETED = "device-deleted";
    String DEVICE_UPDATED = "device-updated";

    @Input(DEVICE_CREATED)
    SubscribableChannel onDeviceCreated();

    @Input(DEVICE_UPDATED)
    SubscribableChannel onDeviceUpdated();

    @Input(DEVICE_DELETED)
    SubscribableChannel onDeviceDeleted();
}
