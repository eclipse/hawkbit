/**
 * Copyright (c) 2019 Kiwigrid GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.dmf.hono.model;

public class IdentifiableHonoDevice {
    private String id;
    private String tenant;
    private HonoDevice device;

    public String getId() {
        return id;
    }

    public String getTenant() {
        return tenant;
    }

    public HonoDevice getDevice() {
        return device;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public void setDevice(HonoDevice device) {
        this.device = device;
    }
}
