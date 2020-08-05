/**
 * Copyright (c) 2019 Kiwigrid GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.dmf.hono.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;

public abstract class HonoSecret {
    private String id;
    private boolean enabled = true;
    private ZonedDateTime notBefore;
    private ZonedDateTime notAfter;

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty("not-before")
    public ZonedDateTime getNotBefore() {
        return notBefore;
    }

    @JsonProperty("not-after")
    public ZonedDateTime getNotAfter() {
        return notAfter;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("enabled")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonProperty("not-before")
    public void setNotBefore(ZonedDateTime notBefore) {
        this.notBefore = notBefore;
    }

    @JsonProperty("not-before")
    public void setNotBefore(String notBefore) {
        this.notBefore = ZonedDateTime.parse(notBefore);
    }

    @JsonProperty("not-after")
    public void setNotAfter(ZonedDateTime notAfter) {
        this.notAfter = notAfter;
    }

    @JsonProperty("not-after")
    public void setNotAfter(String notAfter) {
        this.notAfter = ZonedDateTime.parse(notAfter);
    }

    public boolean isValid() {
        ZonedDateTime now = ZonedDateTime.now();
        return (notBefore == null || now.compareTo(notBefore) >= 0) && (notAfter == null || now.compareTo(notAfter) <= 0);
    }

    public abstract boolean matches(final String password);
}
