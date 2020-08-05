/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * An spring authentication provider which supports authentication tokens of
 * type {@link PreAuthenticatedAuthenticationToken} created by the
 * {@link ControllerPreAuthenticatedSecurityHeaderFilter}.
 *
 * Additionally to the authentication token providing the principal and the
 * credentials which must be match, this authentication provider can also check
 * the remote IP address of the request.
 *
 * E.g. The request path is /controller/v1/{controllerId} then the controllerId
 * in the path is the principal. The credentials are the extracted information
 * from e.g. a certificate provided by an reverse proxy. Due this request is
 * only allowed from a specific source address this authentication manager can
 * also check the remote IP address of the request.
 *
 *
 *
 */
public class PreAuthTokenSourceTrustAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreAuthenticatedAuthenticationToken.class);
    private final List<String> authorizedSourceIps;

    /**
     * Creates a new PreAuthTokenSourceTrustAuthenticationProvider without
     * source IPs, which disables the source IP check.
     */
    public PreAuthTokenSourceTrustAuthenticationProvider() {
        authorizedSourceIps = null;
    }

    /**
     * Creates a new PreAuthTokenSourceTrustAuthenticationProvider with given
     * source IP addresses which are trusted and should be checked against the
     * request remote IP address.
     *
     * @param authorizedSourceIps
     *            a list of IP addresses.
     */
    public PreAuthTokenSourceTrustAuthenticationProvider(final List<String> authorizedSourceIps) {
        this.authorizedSourceIps = authorizedSourceIps;
    }

    /**
     * Creates a new PreAuthTokenSourceTrustAuthenticationProvider with given
     * source IP addresses which are trusted and should be checked against the
     * request remote IP address.
     *
     * @param authorizedSourceIps
     *            a list of IP addresses.
     */
    public PreAuthTokenSourceTrustAuthenticationProvider(final String... authorizedSourceIps) {
        this.authorizedSourceIps = new ArrayList<>();
        for (final String ip : authorizedSourceIps) {
            this.authorizedSourceIps.add(ip);
        }
    }

    @Override
    public Authentication authenticate(final Authentication authentication) {
        if (!supports(authentication.getClass())) {
            return null;
        }

        final PreAuthenticatedAuthenticationToken token = (PreAuthenticatedAuthenticationToken) authentication;
        final Object credentials = token.getCredentials();
        final Object principal = token.getPrincipal();
        final Object tokenDetails = token.getDetails();
        final Collection<GrantedAuthority> authorities = token.getAuthorities();

        if (principal == null) {
            throw new BadCredentialsException("The provided principal and credentials are not match");
        }

        final boolean successAuthentication = calculateAuthenticationSuccess(principal, credentials, tokenDetails);

        if (successAuthentication) {
            final PreAuthenticatedAuthenticationToken successToken = new PreAuthenticatedAuthenticationToken(principal,
                    credentials, authorities);
            successToken.setDetails(tokenDetails);
            return successToken;
        }

        throw new BadCredentialsException("The provided principal and credentials are not match");
    }

    /**
     *
     * The credentials may either be of type HeaderAuthentication or of type
     * Collection<HeaderAuthentication> depending on the authentication mode in
     * use (the latter is used in case of trusted reverse-proxy). It is checked
     * whether principal equals credentials (respectively if credentials
     * contains principal in case of collection) because we want to check if
     * e.g. controllerId containing in the URL equals the controllerId in the
     * special header set by the reverse-proxy which extracted the CN from the
     * certificate.
     *
     * @param principal
     *            the {@link HeaderAuthentication} from the header
     * @param credentials
     *            a single {@link HeaderAuthentication} or a Collection of
     *            HeaderAuthentication
     * @param tokenDetails
     *            authentication details
     * @return <code>true</code> if authentication succeeded, otherwise
     *         <code>false</code>
     */
    private boolean calculateAuthenticationSuccess(final Object principal, final Object credentials,
            final Object tokenDetails) {
        boolean successAuthentication = false;
        if (credentials instanceof Collection) {
            final Collection<?> multiValueCredentials = (Collection<?>) credentials;
            if (multiValueCredentials.contains(principal)) {
                successAuthentication = checkSourceIPAddressIfNeccessary(tokenDetails);
            }
        } else if (principal.equals(credentials)) {
            successAuthentication = checkSourceIPAddressIfNeccessary(tokenDetails);
        }

        return successAuthentication;
    }

    boolean checkSourceIPAddressIfNeccessary(final Object tokenDetails) {
        boolean success = authorizedSourceIps == null;
        String remoteAddress = null;
        // controllerIds in URL path and request header are the same but is the
        // request coming
        // from a trustful source, like the reverse proxy.
        if (authorizedSourceIps != null) {
            if (!(tokenDetails instanceof TenantAwareWebAuthenticationDetails)) {
                // is not of type WebAuthenticationDetails, then we cannot
                // determine the remote address!
                LOGGER.error(
                        "Cannot determine the controller remote-ip-address based on the given authentication token - {} , token details are not TenantAwareWebAuthenticationDetails! ",
                        tokenDetails);
                success = false;
            } else {
                remoteAddress = ((TenantAwareWebAuthenticationDetails) tokenDetails).getRemoteAddress();
                if (authorizedSourceIps.contains(remoteAddress)) {
                    // source ip matches the given pattern -> authenticated
                    success = true;
                }
            }
        }

        if (!success) {
            throw new InsufficientAuthenticationException("The remote source IP address " + remoteAddress
                    + " is not in the list of trusted IP addresses " + authorizedSourceIps);
        }

        // no trusted IP check, because no authorizedSourceIPs configuration
        return true;
    }

    @Override
    public boolean supports(final Class<?> authentication) {
        return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
