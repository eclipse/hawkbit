/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.security;

/**
 * The authentication principal and credentials object which holds the
 * controller-id and the authority name from the http-headers as principal or
 * from the http-url and tenant configuration for the credentials.
 * 
 *
 *
 */
public final class HeaderAuthentication {
    private final String controllerId;
    private final String headerAuth;

    HeaderAuthentication(final String controllerId, final String headerAuth) {
        this.controllerId = controllerId;
        this.headerAuth = headerAuth;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((controllerId == null) ? 0 : controllerId.hashCode());
        result = prime * result + ((headerAuth == null) ? 0 : headerAuth.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HeaderAuthentication other = (HeaderAuthentication) obj;
        if (controllerId == null) {
            if (other.controllerId != null) {
                return false;
            }
        } else if (!controllerId.equals(other.controllerId)) {
            return false;
        }
        if (headerAuth == null) {
            if (other.headerAuth != null) {
                return false;
            }
        } else if (!headerAuth.equals(other.headerAuth)) {
            return false;
        }
        return true;
    }

    public String getControllerId() {
        return controllerId;
    }

    public String getHeaderAuth() {
        return headerAuth;
    }

    @Override
    public String toString() {
        // only the controller ID because the principal is stored as string for
        // audit information
        // etc.
        return controllerId;
    }

}
