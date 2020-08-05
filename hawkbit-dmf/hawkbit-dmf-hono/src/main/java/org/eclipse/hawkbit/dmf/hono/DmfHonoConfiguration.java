/**
 * Copyright (c) 2019 Kiwigrid GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.dmf.hono;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hawkbit.dmf.hono")
@ConditionalOnProperty(prefix = "hawkbit.dmf.hono", name = "enabled")
@ComponentScan
public class DmfHonoConfiguration {
    private String tenantListUri;
    private String deviceListUri;
    private String credentialsListUri;
    private String authenticationMethod = "none";
    private String oidcTokenUri = "";
    private String oidcClientId = "";
    private String oidcClientSecret = "";
    private String username = "";
    private String password = "";
    private String targetNameField = "";

    @Bean
    public HonoDeviceSync honoDeviceSync() {
        return new HonoDeviceSync(tenantListUri, deviceListUri, credentialsListUri, authenticationMethod,
                oidcTokenUri, oidcClientId, oidcClientSecret, username, password, targetNameField);
    }

    public String getTenantListUri() {
        return tenantListUri;
    }

    public void setTenantListUri(String tenantListUri) {
        this.tenantListUri = tenantListUri;
    }

    public String getDeviceListUri() {
        return deviceListUri;
    }

    public void setDeviceListUri(String deviceListUri) {
        this.deviceListUri = deviceListUri;
    }

    public String getCredentialsListUri() {
        return credentialsListUri;
    }

    public void setCredentialsListUri(String credentialsListUri) {
        this.credentialsListUri = credentialsListUri;
    }

    public String getAuthenticationMethod() {
        return authenticationMethod;
    }

    public void setAuthenticationMethod(String authenticationMethod) {
        this.authenticationMethod = authenticationMethod;
    }

    public String getOidcTokenUri() {
        return oidcTokenUri;
    }

    public void setOidcTokenUri(String oidcTokenUri) {
        this.oidcTokenUri = oidcTokenUri;
    }

    public String getOidcClientId() {
        return oidcClientId;
    }

    public void setOidcClientId(String oidcClientId) {
        this.oidcClientId = oidcClientId;
    }

    public String getOidcClientSecret() {
        return oidcClientSecret;
    }

    public void setOidcClientSecret(String oidcClientSecret) {
        this.oidcClientSecret = oidcClientSecret;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTargetNameField() {
        return targetNameField;
    }

    public void setTargetNameField(String targetNameField) {
        this.targetNameField = targetNameField;
    }
}
