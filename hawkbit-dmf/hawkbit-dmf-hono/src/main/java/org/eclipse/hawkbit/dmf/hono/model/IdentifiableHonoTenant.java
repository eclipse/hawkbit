/**
 * Copyright (c) 2019 Kiwigrid GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.dmf.hono.model;

public class IdentifiableHonoTenant {
    private String id;
    private HonoTenant tenant;

    public String getId() {
        return id;
    }

    public HonoTenant getTenant() {
        return tenant;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setTenant(HonoTenant tenant) {
        this.tenant = tenant;
    }
}
