/**
 * Copyright (c) 2019 Kiwigrid GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.security;

import java.util.Collection;
import java.util.Optional;

import org.eclipse.hawkbit.dmf.hono.HonoDeviceSync;
import org.eclipse.hawkbit.dmf.hono.model.HonoCredentials;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An pre-authenticated processing filter which extracts (if enabled through
 * configuration) the possibility to authenticate a target based on its target
 * security-token with the {@code Authorization} HTTP header.
 * {@code Example Header: Authorization: HonoToken
 * 5d8fSD54fdsFG98DDsa.}
 */
public class ControllerPreAuthenticatedHonoFilter extends AbstractControllerAuthenticationFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerPreAuthenticatedHonoFilter.class);
    private static final String TARGET_SECURITY_TOKEN_AUTH_SCHEME = "HonoToken ";
    private static final int OFFSET_TARGET_TOKEN = TARGET_SECURITY_TOKEN_AUTH_SCHEME.length();

    private final ControllerManagement controllerManagement;
    private final HonoDeviceSync honoDeviceSync;

    /**
     * Constructor.
     *
     * @param tenantConfigurationManagement
     *            the tenant management service to retrieve configuration
     *            properties
     * @param controllerManagement
     *            the controller management to retrieve the specific target
     *            security token to verify
     * @param tenantAware
     *            the tenant aware service to get configuration for the specific
     *            tenant
     * @param systemSecurityContext
     *            the system security context to get access to tenant
     *            configuration
     * @param honoDeviceSync
     *            the hono device sync interface
     */
    public ControllerPreAuthenticatedHonoFilter(
            final TenantConfigurationManagement tenantConfigurationManagement,
            final ControllerManagement controllerManagement, final TenantAware tenantAware,
            final SystemSecurityContext systemSecurityContext, final HonoDeviceSync honoDeviceSync) {
        super(tenantConfigurationManagement, tenantAware, systemSecurityContext);
        this.controllerManagement = controllerManagement;
        this.honoDeviceSync = honoDeviceSync;
    }

    @Override
    public HeaderAuthentication getPreAuthenticatedPrincipal(final DmfTenantSecurityToken secruityToken) {
        final String controllerId = resolveControllerId(secruityToken);
        final String authHeader = secruityToken.getHeader(DmfTenantSecurityToken.AUTHORIZATION_HEADER);
        if ((authHeader != null) && authHeader.startsWith(TARGET_SECURITY_TOKEN_AUTH_SCHEME)) {
            LOGGER.debug("found authorization header with scheme {} using target security token for authentication",
                    TARGET_SECURITY_TOKEN_AUTH_SCHEME);
            return new HeaderAuthentication(controllerId, authHeader.substring(OFFSET_TARGET_TOKEN));
        }
        LOGGER.debug(
                "security token filter is enabled but request does not contain either the necessary path variables {} or the authorization header with scheme {}",
                secruityToken, TARGET_SECURITY_TOKEN_AUTH_SCHEME);
        return null;
    }

    @Override
    public Collection<HonoCredentials> getPreAuthenticatedCredentials(final DmfTenantSecurityToken securityToken) {
        return honoDeviceSync.getAllHonoCredentials(securityToken.getTenant(), resolveControllerId(securityToken));
    }

    private String resolveControllerId(final DmfTenantSecurityToken securityToken) {
        if (securityToken.getControllerId() != null) {
            return securityToken.getControllerId();
        }
        final Optional<Target> foundTarget = systemSecurityContext.runAsSystemAsTenant(
                () -> controllerManagement.get(securityToken.getTargetId()), securityToken.getTenant());
        return foundTarget.map(Target::getControllerId).orElse(null);
    }

    @Override
    protected String getTenantConfigurationKey() {
        return TenantConfigurationKey.AUTHENTICATION_MODE_TARGET_SECURITY_TOKEN_ENABLED;
    }
}
