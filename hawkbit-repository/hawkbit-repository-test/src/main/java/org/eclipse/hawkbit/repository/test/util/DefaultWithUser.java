/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.UUID;

public class DefaultWithUser implements WithUser {

    private final String principal;
    private final String tenant;
    private final String[] authorities;
    private final boolean allSpPermission;
    private final List<String> removeFromAllPermission;
    private final boolean autoCreateTenant;
    private final boolean controller;

    public DefaultWithUser(final String principal, final String tenant, final String[] authorities,
            final boolean allSpPermission, final List<String> removeFromAllPermission,
            final boolean autoCreateTenant, final boolean controller) {
        this.authorities = authorities;
        this.allSpPermission = allSpPermission;
        this.removeFromAllPermission = removeFromAllPermission;
        this.autoCreateTenant = autoCreateTenant;
        this.controller = controller;
        this.principal = principal.isEmpty() ? UUID.randomUUID().toString() : principal;
        this.tenant = (tenant.isEmpty() ? UUID.randomUUID().toString() : tenant).toUpperCase();
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return WithUser.class;
    }

    @Override
    public String principal() {
        return principal;
    }

    @Override
    public String credentials() {
        return null;
    }

    @Override
    public String[] authorities() {
        return authorities;
    }

    @Override
    public boolean allSpPermissions() {
        return allSpPermission;
    }

    @Override
    public String[] removeFromAllPermission() {
        return removeFromAllPermission.toArray(new String[0]);
    }

    @Override
    public String tenantId() {
        return tenant;
    }

    @Override
    public boolean autoCreateTenant() {
        return autoCreateTenant;
    }

    @Override
    public boolean controller() {
        return controller;
    }
}
