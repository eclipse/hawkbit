/**
 * Copyright (c) 2019 Kiwigrid GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.dmf.hono.model;

import java.util.List;

public class HonoTenantListPage {
    private long total;
    private List<IdentifiableHonoTenant> items;

    public long getTotal() {
        return total;
    }

    public List<IdentifiableHonoTenant> getItems() {
        return items;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public void setItems(List<IdentifiableHonoTenant> items) {
        this.items = items;
    }
}
