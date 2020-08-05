/**
 * Copyright (c) 2019 Kiwigrid GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.dmf.hono.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Collection;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = HonoPasswordCredentials.class, name = "hashed-password"),
    @JsonSubTypes.Type(value = HonoPSKCredentials.class, name = "psk"),
    @JsonSubTypes.Type(value = HonoX509CertificateCredentials.class, name = "x509-cert")
})
public abstract class HonoCredentials {
    private String authId;
    private String type;
    private boolean enabled = true;
    Collection<? extends HonoSecret> secrets;

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("auth-id")
    public String getAuthId() {
        return authId;
    }

    @JsonProperty("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty("secrets")
    public Collection<? extends HonoSecret> getSecrets() {
        return secrets;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("auth-id")
    public void setAuthId(String authId) {
        this.authId = authId;
    }

    @JsonProperty("enabled")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean matches(final String providedSecret) {
        if (enabled) {
            for (HonoSecret secret : secrets) {
                if (secret.isValid() && secret.matches(providedSecret)) {
                    return true;
                }
            }
        }

        return false;
    }
}
