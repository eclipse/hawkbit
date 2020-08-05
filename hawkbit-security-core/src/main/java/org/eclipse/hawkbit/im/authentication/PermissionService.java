/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.im.authentication;

import java.util.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Service to check permissions.
 *
 */
public class PermissionService {

    private boolean honoSyncEnabled = false;
    private final List<String> disabledPermissionsByUsingHono = Arrays.asList(SpPermission.CREATE_TARGET, SpPermission.DELETE_TARGET);

    /**
     * Checks if the given {@code permission} contains in the. In case no
     * {@code context} is available {@code false} will be returned.
     *
     * @param permission
     *            the permission to check against the
     * @return {@code true} if a is available and contains the given
     *         {@code permission}, otherwise {@code false}.
     * @see SpPermission
     */
    public boolean hasPermission(final String permission) {
        final SecurityContext context = SecurityContextHolder.getContext();
        if (context == null) {
            return false;
        }
        final Authentication authentication = context.getAuthentication();
        if (authentication == null) {
            return false;
        }

        if (honoSyncEnabled && disabledPermissionsByUsingHono.contains(permission)) {
            return false;
        }

        for (final GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority.getAuthority().equals(permission)) {
                return true;
            }
        }

        return false;
    }

    public List<String> getAllPermission() {
        final List<String> permissions = new ArrayList<>();
        final SecurityContext context = SecurityContextHolder.getContext();
        if (context == null) {
            return permissions;
        }
        final Authentication authentication = context.getAuthentication();
        if (authentication == null) {
            return permissions;
        }

        authentication.getAuthorities().stream().forEach(authority -> permissions.add(authority.getAuthority()));

        return permissions;
    }

    /**
     * Checks if at least on permission of the given {@code permissions}
     * contains in the . In case no {@code context} is available {@code false}
     * will be returned.
     *
     * @param permissions
     *            the permissions to check against the
     * @return {@code true} if a is available and contains the given
     *         {@code permission}, otherwise {@code false}.
     * @see SpPermission
     */
    public boolean hasAtLeastOnePermission(final List<String> permissions) {
        final SecurityContext context = SecurityContextHolder.getContext();
        if (context == null) {
            return false;
        }

        final Authentication authentication = context.getAuthentication();
        if (authentication == null) {
            return false;
        }

        for (final GrantedAuthority authority : authentication.getAuthorities()) {
            for (final String permission : permissions) {
                if (authority.getAuthority().equals(permission)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void setHonoSyncEnabled(boolean honoSyncEnabled) {
        this.honoSyncEnabled = honoSyncEnabled;
    }
}
