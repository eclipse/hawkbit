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
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Collection;

@JsonTypeName("x509-cert")
public class HonoX509CertificateCredentials extends HonoCredentials {
    @JsonProperty("secrets")
    public void setSecrets(Collection<Secret> secrets) {
        this.secrets = secrets;
    }

    public static class Secret extends HonoSecret {
        @Override
        public boolean matches(String password) {
            return false;
        }
    }
}
