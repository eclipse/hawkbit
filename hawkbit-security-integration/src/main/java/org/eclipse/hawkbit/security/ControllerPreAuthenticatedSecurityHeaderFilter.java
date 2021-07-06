/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.security;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An pre-authenticated processing filter which extracts the principal from a
 * request URI and the credential from a request header in a the
 * {@link DmfTenantSecurityToken}.
 *
 */
public class ControllerPreAuthenticatedSecurityHeaderFilter extends AbstractControllerAuthenticationFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerPreAuthenticatedSecurityHeaderFilter.class);
    private static final Logger LOG_SECURITY_AUTH = LoggerFactory.getLogger("server-security.authentication");

    private final GetSecurityAuthorityNameTenantRunner sslIssuerNameConfigTenantRunner = new GetSecurityAuthorityNameTenantRunner();
    // Example Headers with Cert Information
    // Clientip: 217.24.201.180
    // X-Forwarded-Proto: https
    // X-Ssl-Client-Cn: my.name
    // X-Ssl-Client-Dn:
    // CN=my.name,CN=O,CN=R,CN=DE,CN=BOSCH,CN=pki,DC=bosch,DC=com
    // X-Ssl-Client-Hash: 7f:87:cb:b5:9c:e0:c5:0a:1a:a6:57:69:0f:ca:0a:95
    // X-Ssl-Client-Notafter: Dec 18 08:02:45 2017 GMT
    // X-Ssl-Client-Notbefore: Dec 18 07:32:45 2014 GMT
    // X-Ssl-Client-Verify: ok
    // X-Ssl-Issuer: CN=Bosch-CA1-DE,CN=PKI,DC=Bosch,DC=com
    // X-Ssl-Issuer-Dn-1: CN=Bosch-CA-DE,CN=PKI,DC=Bosch,DC=com
    // X-Ssl-Issuer-Hash-1: ae:11:f5:6a:0a:e8:74:50:81:0e:0c:37:ec:c5:22:fc
    private final String caCommonNameHeader;
    // the X-Ssl-Issuer-Hash basic header header which contains the x509
    // fingerprint hash, this
    // header exists multiple in the request for all trusted chain.
    private final String sslIssuerHashBasicHeader;

    /**
     * Constructor.
     *
     * @param caCommonNameHeader
     *            the http-header which holds the common-name of the certificate
     * @param caAuthorityNameHeader
     *            the http-header which holds the ca-authority name of the
     *            certificate
     * @param tenantConfigurationManagement
     *            the tenant management service to retrieve configuration
     *            properties
     * @param tenantAware
     *            the tenant aware service to get configuration for the specific
     *            tenant
     * @param systemSecurityContext
     *            the system security context to get access to tenant
     *            configuration
     */
    public ControllerPreAuthenticatedSecurityHeaderFilter(final String caCommonNameHeader,
            final String caAuthorityNameHeader, final TenantConfigurationManagement tenantConfigurationManagement,
            final TenantAware tenantAware, final SystemSecurityContext systemSecurityContext) {
        super(tenantConfigurationManagement, tenantAware, systemSecurityContext);
        this.caCommonNameHeader = caCommonNameHeader;
        this.sslIssuerHashBasicHeader = caAuthorityNameHeader;
    }

    @Override
    public HeaderAuthentication getPreAuthenticatedPrincipal(final DmfTenantSecurityToken secruityToken) {
        // retrieve the common name header and the authority name header from
        // the http request and combine them together
        final String commonNameValue = getCommonNameHeader(secruityToken);
        final String knownSslIssuerConfigurationValue = tenantAware.runAsTenant(secruityToken.getTenant(),
                sslIssuerNameConfigTenantRunner);
        final String sslIssuerHashValue = getIssuerHashHeader(secruityToken, knownSslIssuerConfigurationValue);
        if (commonNameValue != null && LOGGER.isTraceEnabled()) {
            LOGGER.trace("Found commonNameHeader {}={}, using as credentials", caCommonNameHeader, commonNameValue);
        }
        if (sslIssuerHashValue != null && LOGGER.isTraceEnabled()) {
            LOGGER.trace("Found sslIssuerHash ****, using as credentials for tenant {}", secruityToken.getTenant());
        }

        if (commonNameValue != null && sslIssuerHashValue != null) {
            return new HeaderAuthentication(commonNameValue, sslIssuerHashValue);
        }
        return null;
    }

    @Override
    public Object getPreAuthenticatedCredentials(final DmfTenantSecurityToken secruityToken) {
        final String authorityNameConfigurationValue = tenantAware.runAsTenant(secruityToken.getTenant(),
                sslIssuerNameConfigTenantRunner);
        String controllerId = secruityToken.getControllerId();
        // in case of legacy download artifact, the controller ID is not in the
        // URL path, so then we just use the common name header
        if (controllerId == null || "anonymous".equals(controllerId)) {
            controllerId = getCommonNameHeader(secruityToken);
        }

        final List<String> knownHashes = splitMultiHashBySemicolon(authorityNameConfigurationValue);

        final String cntlId = controllerId;
        return knownHashes.stream().map(hashItem -> new HeaderAuthentication(cntlId, hashItem))
                .collect(Collectors.toSet());
    }

    /**
     * Iterates over the {@link #sslIssuerHashBasicHeader} basic header
     * {@code X-Ssl-Issuer-Hash-%d} and try to finds the same hash as known.
     * It's ok if we find the the hash in any the trusted CA chain to accept
     * this request for this tenant.
     */
    private String getIssuerHashHeader(final DmfTenantSecurityToken secruityToken, final String knownIssuerHashes) {
        String foundHash;
        // there may be several knownIssuerHashes configured for the tenant
        final List<String> knownHashes = splitMultiHashBySemicolon(knownIssuerHashes);
        if (sslIssuerHashBasicHeader.contains("%d")) {
            // iterate over the headers until we get a null header.
            int iHeader = 1;
            while ((foundHash = secruityToken.getHeader(String.format(sslIssuerHashBasicHeader, iHeader))) != null) {
                if (knownHashes.contains(foundHash.toLowerCase())) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Found matching ssl issuer hash at position {}", iHeader);
                    }
                    return foundHash.toLowerCase();
                }
                iHeader++;
            }
        } else if ((foundHash = secruityToken.getHeader(sslIssuerHashBasicHeader)) != null && knownHashes.contains(foundHash.toLowerCase())) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Found matching ssl issuer hashes");
            }
            return foundHash.toLowerCase();
        }
        LOG_SECURITY_AUTH.debug(
                "Certifacte request but no matching hash found in headers {} for common name {} in request",
                sslIssuerHashBasicHeader, getCommonNameHeader(secruityToken));
        return null;
    }

    private String getCommonNameHeader(final DmfTenantSecurityToken secruityToken) {
        String header = secruityToken.getHeader(caCommonNameHeader);
        if (header == null) {
            return null;
        }
        int begin = header.indexOf("CN=");
        if (begin == -1) {
            return header;
        } else {
            begin += 3;
        }
        int end = header.indexOf(",", begin);
        if (end == -1) {
            return header.substring(begin);
        } else {
            return header.substring(begin, end);
        }
    }

    @Override
    protected String getTenantConfigurationKey() {
        return TenantConfigurationKey.AUTHENTICATION_MODE_HEADER_ENABLED;
    }

    private final class GetSecurityAuthorityNameTenantRunner implements TenantAware.TenantRunner<String> {
        @Override
        public String run() {
            return systemSecurityContext.runAsSystem(() -> tenantConfigurationManagement.getConfigurationValue(
                    TenantConfigurationKey.AUTHENTICATION_MODE_HEADER_AUTHORITY_NAME, String.class).getValue());
        }
    }

    private static List<String> splitMultiHashBySemicolon(final String knownIssuerHashes) {
        return Arrays.stream(knownIssuerHashes.split(";")).map(String::toLowerCase).collect(Collectors.toList());
    }
}
