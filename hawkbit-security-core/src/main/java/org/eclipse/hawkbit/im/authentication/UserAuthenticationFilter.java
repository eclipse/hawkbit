/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.im.authentication;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Filter to integrate into the SP security filter-chain. The filter is called
 * in any remote call through HTTP except the SP login screen. E.g. using the SP
 * REST-API. To authenticate user e.g. using Basic-Authentication implement the
 * {@link #doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse, jakarta.servlet.FilterChain)}
 * method.
 *
 *
 *
 */
public interface UserAuthenticationFilter {

    /**
     * @see Filter#init(FilterConfig)
     *
     * @param filterConfig
     *            the filter config
     */
    void init(FilterConfig filterConfig) throws ServletException;

    /**
     * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
     *
     * @param request
     *            the servlet request
     * @param response
     *            the servlet response
     * @param chain
     *            the filterchain
     * @throws IOException
     *             cannot read from request
     * @throws ServletException
     *             servlet exception
     */
    // this declaration of multiple checked exception is necessary so it's
    // aligned with the servlet API.
    @SuppressWarnings("squid:S1160")
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException;

    /**
     * @see Filter#destroy()
     */
    void destroy();

}
