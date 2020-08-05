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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;

@JsonTypeName("hashed-password")
public class HonoPasswordCredentials extends HonoCredentials {
    @JsonProperty("secrets")
    public void setSecrets(Collection<Secret> secrets) {
        this.secrets = secrets;
    }

    public static class Secret extends HonoSecret {
        private String hashFunction;
        private String salt;
        private String pwdHash;

        @Override
        public boolean matches(final String password) {
            if ("none".equals(hashFunction)) {
                return pwdHash.equals(password);
            }

            PasswordEncoder encoder;
            if ("bcrypt".equals(hashFunction)) {
                encoder = new BCryptPasswordEncoder();
            }
            else if("sha-256".equals(hashFunction)) {
                encoder = new MessageDigestPasswordEncoder("SHA-256");
            }
            else if("SHA-512".equals(hashFunction)) {
                encoder = new MessageDigestPasswordEncoder("SHA-512");
            }
            else {
                return false;
            }

            return encoder.matches(password + (salt != null ? salt : ""), pwdHash);
        }

        @JsonProperty("hash-function")
        public String getHashFunction() {
            return hashFunction;
        }

        @JsonProperty("salt")
        public String getSalt() {
            return salt;
        }

        @JsonProperty("pwd-hash")
        public String getPwdHash() {
            return pwdHash;
        }

        @JsonProperty("hash-function")
        public void setHashFunction(String hashFunction) {
            this.hashFunction = hashFunction;
        }

        @JsonProperty("salt")
        public void setSalt(byte[] salt) {
            this.salt = new String(salt);
        }

        @JsonProperty("pwd-hash")
        public void setPwdHash(byte[] pwdHash) {
            this.pwdHash = new String(pwdHash);
        }
    }
}
