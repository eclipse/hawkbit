/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions;
import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.im.authentication.UserPrincipal;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.tenancy.UserAuthoritiesResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * A {@link TenantAware} implementation which retrieves the ID of the tenant
 * from the {@link SecurityContext#getAuthentication()}
 * {@link Authentication#getDetails()} which holds the
 * {@link TenantAwareAuthenticationDetails} object.
 *
 */
public class SecurityContextTenantAware implements TenantAware {

    public static final String SYSTEM_USER = "system";
    private static final Collection<? extends GrantedAuthority> SYSTEM_AUTHORITIES = Collections
            .singletonList(new SimpleGrantedAuthority(SpringEvalExpressions.SYSTEM_ROLE));

    private final UserAuthoritiesResolver authoritiesResolver;

    /**
     * Creates the {@link SecurityContextTenantAware} based on the given
     * {@link UserAuthoritiesResolver}.
     * 
     * @param authoritiesResolver
     *            Resolver to retrieve the authorities for a given user. Must
     *            not be <code>null</code>.
     */
    public SecurityContextTenantAware(final UserAuthoritiesResolver authoritiesResolver) {
        this.authoritiesResolver = authoritiesResolver;
    }

    @Override
    public String getCurrentTenant() {
        final SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() != null) {
            final Object principal = context.getAuthentication().getPrincipal();
            if (context.getAuthentication().getDetails() instanceof TenantAwareAuthenticationDetails) {
                return ((TenantAwareAuthenticationDetails) context.getAuthentication().getDetails()).getTenant();
            } else if (principal instanceof UserPrincipal) {
                return ((UserPrincipal) principal).getTenant();
            }
        }
        return null;
    }

    @Override
    public String getCurrentUsername() {
        final SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() != null) {
            final Object principal = context.getAuthentication().getPrincipal();
            if (principal instanceof UserPrincipal) {
                return ((UserPrincipal) principal).getUsername();
            }
            if (principal instanceof OidcUser) {
                return ((OidcUser) principal).getPreferredUsername();
            }            
        }
        return null;
    }

    @Override
    public <T> T runAsTenant(final String tenant, final TenantRunner<T> tenantRunner) {
        return runInContext(buildSystemSecurityContext(tenant), tenantRunner);
    }

    @Override
    public <T> T runAsTenantAsUser(final String tenant, final String username, final TenantRunner<T> tenantRunner) {
        final List<SimpleGrantedAuthority> authorities = runAsSystem(
                () -> authoritiesResolver.getUserAuthorities(tenant, username).stream().map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()));
        return runInContext(buildUserSecurityContext(tenant, username, authorities), tenantRunner);
    }

    private static <T> T runInContext(final SecurityContext context, final TenantRunner<T> tenantRunner) {
        final SecurityContext originalContext = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(context);
            return tenantRunner.run();
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }

    private static SecurityContext buildSystemSecurityContext(final String tenant) {
        return buildUserSecurityContext(tenant, SYSTEM_USER, SYSTEM_AUTHORITIES);
    }

    private static <T> T runAsSystem(final TenantRunner<T> tenantRunner) {
        final SecurityContext currentContext = SecurityContextHolder.getContext();
        try {
            SystemSecurityContext.setSystemContext(currentContext);
            return tenantRunner.run();
        } finally {
            SecurityContextHolder.setContext(currentContext);
        }
    }

    private static SecurityContext buildUserSecurityContext(final String tenant, final String username,
            final Collection<? extends GrantedAuthority> authorities) {
        final SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new AuthenticationDelegate(
                SecurityContextHolder.getContext().getAuthentication(), tenant, username, authorities));
        return securityContext;
    }

    /**
     * An {@link Authentication} implementation to delegate to an existing
     * {@link Authentication} object except setting the details specifically for
     * a specific tenant and user.
     */
    private static final class AuthenticationDelegate implements Authentication {
        private static final long serialVersionUID = 1L;

        private final Authentication delegate;

        private final UserPrincipal principal;

        private final TenantAwareAuthenticationDetails tenantAwareAuthenticationDetails;

        private AuthenticationDelegate(final Authentication delegate, final String tenant, final String username,
                final Collection<? extends GrantedAuthority> authorities) {
            this.delegate = delegate;
            this.principal = new UserPrincipal(username, username, null, null, username, null, tenant, authorities);
            tenantAwareAuthenticationDetails = new TenantAwareAuthenticationDetails(tenant, false);
        }

        @Override
        public boolean equals(final Object another) {
            if (delegate != null) {
                return delegate.equals(another);
            } else if (another == null) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return delegate != null ? delegate.toString() : null;
        }

        @Override
        public int hashCode() {
            return delegate != null ? delegate.hashCode() : -1;
        }

        @Override
        public String getName() {
            return delegate != null ? delegate.getName() : null;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return delegate != null ? delegate.getAuthorities() : Collections.emptyList();
        }

        @Override
        public Object getCredentials() {
            return delegate != null ? delegate.getCredentials() : null;
        }

        @Override
        public Object getDetails() {
            return tenantAwareAuthenticationDetails;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public boolean isAuthenticated() {
            return delegate == null || delegate.isAuthenticated();
        }

        @Override
        public void setAuthenticated(final boolean isAuthenticated) {
            if (delegate == null) {
                return;
            }
            delegate.setAuthenticated(isAuthenticated);
        }
    }
}
