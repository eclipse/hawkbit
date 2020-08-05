/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.autoconfigure.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.PostConstruct;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.eclipse.hawkbit.cache.DownloadIdCache;
import org.eclipse.hawkbit.ddi.rest.api.DdiRestConstants;
import org.eclipse.hawkbit.ddi.rest.resource.DdiApiConfiguration;
import org.eclipse.hawkbit.dmf.hono.HonoDeviceSync;
import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions;
import org.eclipse.hawkbit.im.authentication.TenantUserPasswordAuthenticationToken;
import org.eclipse.hawkbit.im.authentication.UserAuthenticationFilter;
import org.eclipse.hawkbit.mgmt.rest.api.MgmtRestConstants;
import org.eclipse.hawkbit.mgmt.rest.resource.MgmtApiConfiguration;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.ControllerTenantAwareAuthenticationDetailsSource;
import org.eclipse.hawkbit.security.DdiSecurityProperties;
import org.eclipse.hawkbit.security.DosFilter;
import org.eclipse.hawkbit.security.HawkbitSecurityProperties;
import org.eclipse.hawkbit.security.HttpControllerPreAuthenticateAnonymousDownloadFilter;
import org.eclipse.hawkbit.security.HttpControllerPreAuthenticateSecurityTokenFilter;
import org.eclipse.hawkbit.security.HttpControllerPreAuthenticatedGatewaySecurityTokenFilter;
import org.eclipse.hawkbit.security.HttpControllerPreAuthenticatedHonoFilter;
import org.eclipse.hawkbit.security.HttpControllerPreAuthenticatedSecurityHeaderFilter;
import org.eclipse.hawkbit.security.HttpDownloadAuthenticationFilter;
import org.eclipse.hawkbit.security.PreAuthHonoAuthenticationProvider;
import org.eclipse.hawkbit.security.PreAuthTokenSourceTrustAuthenticationProvider;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.ui.MgmtUiConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.WebSecurityConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.vaadin.spring.security.VaadinSecurityContext;
import org.vaadin.spring.security.annotation.EnableVaadinSecurity;
import org.vaadin.spring.security.web.VaadinRedirectStrategy;
import org.vaadin.spring.security.web.authentication.VaadinAuthenticationSuccessHandler;
import org.vaadin.spring.security.web.authentication.VaadinUrlAuthenticationSuccessHandler;

/**
 * All configurations related to HawkBit's authentication and authorization
 * layer.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, mode = AdviceMode.ASPECTJ, proxyTargetClass = true, securedEnabled = true)
@Order(value = Ordered.HIGHEST_PRECEDENCE)
@PropertySource("classpath:/hawkbit-security-defaults.properties")
public class SecurityManagedConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityManagedConfiguration.class);

    private static final int DOS_FILTER_ORDER = -200;

    /**
     * @return the {@link UserAuthenticationFilter} to include into the hawkBit
     *         security configuration.
     * @throws Exception
     *             lazy bean exception maybe if the authentication manager
     *             cannot be instantiated
     */
    @Bean
    @ConditionalOnMissingBean
    // Exception squid:S00112 - Is aspectJ proxy
    @SuppressWarnings({ "squid:S00112" })
    UserAuthenticationFilter userAuthenticationFilter(final AuthenticationConfiguration configuration)
            throws Exception {
        return new UserAuthenticationFilterBasicAuth(configuration.getAuthenticationManager());
    }

    private static final class UserAuthenticationFilterBasicAuth extends BasicAuthenticationFilter
            implements UserAuthenticationFilter {

        private UserAuthenticationFilterBasicAuth(final AuthenticationManager authenticationManager) {
            super(authenticationManager);
        }

    }

    /**
     * {@link WebSecurityConfigurer} for the hawkBit server DDI interface.
     */
    @Configuration
    @Order(300)
    @ConditionalOnClass(DdiApiConfiguration.class)
    static class ControllerSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        private static final String[] DDI_ANT_MATCHERS = { DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}",
                DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/deploymentBase/**",
                DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/cancelAction/**",
                DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/configData",
                DdiRestConstants.BASE_V1_REQUEST_MAPPING
                        + "/{controllerId}/softwaremodules/{softwareModuleId}/artifacts" };

        private final ControllerManagement controllerManagement;
        private final TenantConfigurationManagement tenantConfigurationManagement;
        private final TenantAware tenantAware;
        private final DdiSecurityProperties ddiSecurityConfiguration;
        private final HawkbitSecurityProperties securityProperties;
        private final SystemSecurityContext systemSecurityContext;

        @Autowired(required = false)
        private HonoDeviceSync honoDeviceSync;

        @Autowired
        ControllerSecurityConfigurationAdapter(final ControllerManagement controllerManagement,
                final TenantConfigurationManagement tenantConfigurationManagement, final TenantAware tenantAware,
                final DdiSecurityProperties ddiSecurityConfiguration,
                final HawkbitSecurityProperties securityProperties, final SystemSecurityContext systemSecurityContext) {
            this.controllerManagement = controllerManagement;
            this.tenantConfigurationManagement = tenantConfigurationManagement;
            this.tenantAware = tenantAware;
            this.ddiSecurityConfiguration = ddiSecurityConfiguration;
            this.securityProperties = securityProperties;
            this.systemSecurityContext = systemSecurityContext;
        }

        /**
         * Filter to protect the hawkBit server DDI interface against to many
         * requests.
         * 
         * @param securityProperties
         *            for filter configuration
         *
         * @return the spring filter registration bean for registering a denial
         *         of service protection filter in the filter chain
         */
        @Bean
        @ConditionalOnProperty(prefix = "hawkbit.server.security.dos.filter", name = "enabled", matchIfMissing = true)
        public FilterRegistrationBean<DosFilter> dosDDiFilter(final HawkbitSecurityProperties securityProperties) {

            final FilterRegistrationBean<DosFilter> filterRegBean = dosFilter(Arrays.asList(DDI_ANT_MATCHERS),
                    securityProperties.getDos().getFilter(), securityProperties.getClients());
            filterRegBean.setOrder(DOS_FILTER_ORDER);
            filterRegBean.setName("dosDDiFilter");

            return filterRegBean;
        }

        @Override
        protected void configure(final HttpSecurity http) throws Exception {

            final ControllerTenantAwareAuthenticationDetailsSource authenticationDetailsSource = new ControllerTenantAwareAuthenticationDetailsSource();

            final HttpControllerPreAuthenticatedSecurityHeaderFilter securityHeaderFilter = new HttpControllerPreAuthenticatedSecurityHeaderFilter(
                    ddiSecurityConfiguration.getRp().getCnHeader(),
                    ddiSecurityConfiguration.getRp().getSslIssuerHashHeader(), tenantConfigurationManagement,
                    tenantAware, systemSecurityContext);
            securityHeaderFilter.setAuthenticationManager(authenticationManager());
            securityHeaderFilter.setCheckForPrincipalChanges(true);
            securityHeaderFilter.setAuthenticationDetailsSource(authenticationDetailsSource);


            HttpControllerPreAuthenticatedHonoFilter honoFilter = null;
            if (honoDeviceSync != null) {
                honoFilter = new HttpControllerPreAuthenticatedHonoFilter(
                        tenantConfigurationManagement, tenantAware, controllerManagement, systemSecurityContext,
                        honoDeviceSync);
                honoFilter.setAuthenticationManager(authenticationManager());
                honoFilter.setCheckForPrincipalChanges(true);
                honoFilter.setAuthenticationDetailsSource(authenticationDetailsSource);
            }

            final HttpControllerPreAuthenticateSecurityTokenFilter securityTokenFilter = new HttpControllerPreAuthenticateSecurityTokenFilter(
                    tenantConfigurationManagement, tenantAware, controllerManagement, systemSecurityContext);
            securityTokenFilter.setAuthenticationManager(authenticationManager());
            securityTokenFilter.setCheckForPrincipalChanges(true);
            securityTokenFilter.setAuthenticationDetailsSource(authenticationDetailsSource);

            final HttpControllerPreAuthenticatedGatewaySecurityTokenFilter gatewaySecurityTokenFilter = new HttpControllerPreAuthenticatedGatewaySecurityTokenFilter(
                    tenantConfigurationManagement, tenantAware, systemSecurityContext);
            gatewaySecurityTokenFilter.setAuthenticationManager(authenticationManager());
            gatewaySecurityTokenFilter.setCheckForPrincipalChanges(true);
            gatewaySecurityTokenFilter.setAuthenticationDetailsSource(authenticationDetailsSource);

            HttpSecurity httpSec = http.csrf().disable();

            if (securityProperties.isRequireSsl()) {
                httpSec = httpSec.requiresChannel().anyRequest().requiresSecure().and();
            }

            if (ddiSecurityConfiguration.getAuthentication().getAnonymous().isEnabled()) {

                LOG.info(
                        "******************\n** Anonymous controller security enabled, should only be used for developing purposes **\n******************");

                final AnonymousAuthenticationFilter anonymousFilter = new AnonymousAuthenticationFilter(
                        "controllerAnonymousFilter", "anonymous",
                        Arrays.asList(new SimpleGrantedAuthority(SpringEvalExpressions.CONTROLLER_ROLE_ANONYMOUS)));
                anonymousFilter.setAuthenticationDetailsSource(authenticationDetailsSource);
                httpSec.requestMatchers().antMatchers(DDI_ANT_MATCHERS).and().securityContext().disable().anonymous()
                        .authenticationFilter(anonymousFilter);
            } else {

                httpSec.addFilter(securityHeaderFilter);
                if (honoFilter != null) {
                    httpSec.addFilter(honoFilter);
                }
                httpSec.addFilter(securityTokenFilter)
                        .addFilter(gatewaySecurityTokenFilter).requestMatchers().antMatchers(DDI_ANT_MATCHERS).and()
                        .anonymous().disable().authorizeRequests().anyRequest().authenticated().and()
                        .exceptionHandling()
                        .authenticationEntryPoint((request, response, authException) -> response
                                .setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
            }
        }

        @Override
        protected void configure(final AuthenticationManagerBuilder auth) throws Exception {

            if (honoDeviceSync != null) {
                auth.authenticationProvider(new PreAuthHonoAuthenticationProvider(honoDeviceSync,
                        ddiSecurityConfiguration.getRp().getTrustedIPs()));
            }
            auth.authenticationProvider(new PreAuthTokenSourceTrustAuthenticationProvider(
                    ddiSecurityConfiguration.getRp().getTrustedIPs()));
        }
    }

    /**
     * {@link WebSecurityConfigurer} for the hawkBit server DDI download
     * interface.
     */
    @Configuration
    @Order(301)
    @ConditionalOnClass(DdiApiConfiguration.class)
    static class ControllerDownloadSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        private static final String DDI_DL_ANT_MATCHER = DdiRestConstants.BASE_V1_REQUEST_MAPPING
                + "/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/*";

        private final ControllerManagement controllerManagement;
        private final TenantConfigurationManagement tenantConfigurationManagement;
        private final TenantAware tenantAware;
        private final DdiSecurityProperties ddiSecurityConfiguration;
        private final HawkbitSecurityProperties securityProperties;
        private final SystemSecurityContext systemSecurityContext;

        @Autowired(required = false)
        private HonoDeviceSync honoDeviceSync;

        @Autowired
        ControllerDownloadSecurityConfigurationAdapter(final ControllerManagement controllerManagement,
                final TenantConfigurationManagement tenantConfigurationManagement, final TenantAware tenantAware,
                final DdiSecurityProperties ddiSecurityConfiguration,
                final HawkbitSecurityProperties securityProperties, final SystemSecurityContext systemSecurityContext) {
            this.controllerManagement = controllerManagement;
            this.tenantConfigurationManagement = tenantConfigurationManagement;
            this.tenantAware = tenantAware;
            this.ddiSecurityConfiguration = ddiSecurityConfiguration;
            this.securityProperties = securityProperties;
            this.systemSecurityContext = systemSecurityContext;
        }

        /**
         * Filter to protect the hawkBit server DDI download interface against
         * to many requests.
         * 
         * @param securityProperties
         *            for filter configuration
         *
         * @return the spring filter registration bean for registering a denial
         *         of service protection filter in the filter chain
         */
        @Bean
        @ConditionalOnProperty(prefix = "hawkbit.server.security.dos.filter", name = "enabled", matchIfMissing = true)
        public FilterRegistrationBean<DosFilter> dosDDiDlFilter(final HawkbitSecurityProperties securityProperties) {

            final FilterRegistrationBean<DosFilter> filterRegBean = dosFilter(Arrays.asList(DDI_DL_ANT_MATCHER),
                    securityProperties.getDos().getFilter(), securityProperties.getClients());
            filterRegBean.setOrder(DOS_FILTER_ORDER);
            filterRegBean.setName("dosDDiDlFilter");

            return filterRegBean;
        }

        @Override
        protected void configure(final HttpSecurity http) throws Exception {

            final ControllerTenantAwareAuthenticationDetailsSource authenticationDetailsSource = new ControllerTenantAwareAuthenticationDetailsSource();

            final HttpControllerPreAuthenticatedSecurityHeaderFilter securityHeaderFilter = new HttpControllerPreAuthenticatedSecurityHeaderFilter(
                    ddiSecurityConfiguration.getRp().getCnHeader(),
                    ddiSecurityConfiguration.getRp().getSslIssuerHashHeader(), tenantConfigurationManagement,
                    tenantAware, systemSecurityContext);
            securityHeaderFilter.setAuthenticationManager(authenticationManager());
            securityHeaderFilter.setCheckForPrincipalChanges(true);
            securityHeaderFilter.setAuthenticationDetailsSource(authenticationDetailsSource);

            HttpControllerPreAuthenticatedHonoFilter honoFilter = null;
            if (honoDeviceSync != null) {
                honoFilter = new HttpControllerPreAuthenticatedHonoFilter(
                        tenantConfigurationManagement, tenantAware, controllerManagement, systemSecurityContext,
                        honoDeviceSync);
                honoFilter.setAuthenticationManager(authenticationManager());
                honoFilter.setCheckForPrincipalChanges(true);
                honoFilter.setAuthenticationDetailsSource(authenticationDetailsSource);
            }

            final HttpControllerPreAuthenticateSecurityTokenFilter securityTokenFilter = new HttpControllerPreAuthenticateSecurityTokenFilter(
                    tenantConfigurationManagement, tenantAware, controllerManagement, systemSecurityContext);
            securityTokenFilter.setAuthenticationManager(authenticationManager());
            securityTokenFilter.setCheckForPrincipalChanges(true);
            securityTokenFilter.setAuthenticationDetailsSource(authenticationDetailsSource);

            final HttpControllerPreAuthenticatedGatewaySecurityTokenFilter gatewaySecurityTokenFilter = new HttpControllerPreAuthenticatedGatewaySecurityTokenFilter(
                    tenantConfigurationManagement, tenantAware, systemSecurityContext);
            gatewaySecurityTokenFilter.setAuthenticationManager(authenticationManager());
            gatewaySecurityTokenFilter.setCheckForPrincipalChanges(true);
            gatewaySecurityTokenFilter.setAuthenticationDetailsSource(authenticationDetailsSource);

            final HttpControllerPreAuthenticateAnonymousDownloadFilter controllerAnonymousDownloadFilter = new HttpControllerPreAuthenticateAnonymousDownloadFilter(
                    tenantConfigurationManagement, tenantAware, systemSecurityContext);
            controllerAnonymousDownloadFilter.setAuthenticationManager(authenticationManager());
            controllerAnonymousDownloadFilter.setCheckForPrincipalChanges(true);
            controllerAnonymousDownloadFilter.setAuthenticationDetailsSource(authenticationDetailsSource);

            HttpSecurity httpSec = http.csrf().disable();

            if (securityProperties.isRequireSsl()) {
                httpSec = httpSec.requiresChannel().anyRequest().requiresSecure().and();
            }

            if (ddiSecurityConfiguration.getAuthentication().getAnonymous().isEnabled()) {

                LOG.info(
                        "******************\n** Anonymous controller security enabled, should only be used for developing purposes **\n******************");

                final AnonymousAuthenticationFilter anonymousFilter = new AnonymousAuthenticationFilter(
                        "controllerAnonymousFilter", "anonymous",
                        Arrays.asList(new SimpleGrantedAuthority(SpringEvalExpressions.CONTROLLER_ROLE_ANONYMOUS)));
                anonymousFilter.setAuthenticationDetailsSource(authenticationDetailsSource);
                httpSec.requestMatchers().antMatchers(DDI_DL_ANT_MATCHER).and().securityContext().disable().anonymous()
                        .authenticationFilter(anonymousFilter);
            } else {

                httpSec.addFilter(securityHeaderFilter);
                if (honoFilter != null) {
                    httpSec.addFilter(honoFilter);
                }
                httpSec.addFilter(securityTokenFilter)
                        .addFilter(gatewaySecurityTokenFilter).addFilter(controllerAnonymousDownloadFilter)
                        .requestMatchers().antMatchers(DDI_DL_ANT_MATCHER).and().anonymous().disable()
                        .authorizeRequests().anyRequest().authenticated().and().exceptionHandling()
                        .authenticationEntryPoint((request, response, authException) -> response
                                .setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
            }
        }

        @Override
        protected void configure(final AuthenticationManagerBuilder auth) throws Exception {

            if (honoDeviceSync != null) {
                auth.authenticationProvider(new PreAuthHonoAuthenticationProvider(honoDeviceSync,
                        ddiSecurityConfiguration.getRp().getTrustedIPs()));
            }
            auth.authenticationProvider(new PreAuthTokenSourceTrustAuthenticationProvider(
                    ddiSecurityConfiguration.getRp().getTrustedIPs()));
        }
    }

    /**
     * Filter to protect the hawkBit server system management interface against
     * to many requests.
     * 
     * @param securityProperties
     *            for filter configuration
     *
     * @return the spring filter registration bean for registering a denial of
     *         service protection filter in the filter chain
     */
    @Bean
    @ConditionalOnProperty(prefix = "hawkbit.server.security.dos.filter", name = "enabled", matchIfMissing = true)
    public FilterRegistrationBean<DosFilter> dosSystemFilter(final HawkbitSecurityProperties securityProperties) {

        final FilterRegistrationBean<DosFilter> filterRegBean = dosFilter(Collections.emptyList(),
                securityProperties.getDos().getFilter(), securityProperties.getClients());
        filterRegBean.setUrlPatterns(Arrays.asList("/system/*"));
        filterRegBean.setOrder(DOS_FILTER_ORDER);
        filterRegBean.setName("dosSystemFilter");

        return filterRegBean;
    }

    private static FilterRegistrationBean<DosFilter> dosFilter(final Collection<String> includeAntPaths,
            final HawkbitSecurityProperties.Dos.Filter filterProperties,
            final HawkbitSecurityProperties.Clients clientProperties) {

        final FilterRegistrationBean<DosFilter> filterRegBean = new FilterRegistrationBean<>();

        filterRegBean.setFilter(new DosFilter(includeAntPaths, filterProperties.getMaxRead(),
                filterProperties.getMaxWrite(), filterProperties.getWhitelist(), clientProperties.getBlacklist(),
                clientProperties.getRemoteIpHeader()));

        return filterRegBean;
    }

    /**
     * A Websecurity config to handle and filter the download ids.
     */
    @Configuration
    @EnableWebSecurity
    @Order(320)
    @ConditionalOnClass(MgmtApiConfiguration.class)
    public static class IdRestSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Autowired
        private DdiSecurityProperties ddiSecurityConfiguration;

        @Autowired
        private DownloadIdCache downloadIdCache;

        @Autowired(required = false)
        private HonoDeviceSync honoDeviceSync;

        @Override
        protected void configure(final HttpSecurity http) throws Exception {

            final HttpDownloadAuthenticationFilter downloadIdAuthenticationFilter = new HttpDownloadAuthenticationFilter(
                    downloadIdCache);
            downloadIdAuthenticationFilter.setAuthenticationManager(authenticationManager());

            http.csrf().disable();
            http.anonymous().disable();

            http.regexMatcher(HttpDownloadAuthenticationFilter.REQUEST_ID_REGEX_PATTERN)
                    .addFilterBefore(downloadIdAuthenticationFilter, FilterSecurityInterceptor.class);
            http.authorizeRequests().anyRequest().authenticated().and().sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        }

        @Override
        protected void configure(final AuthenticationManagerBuilder auth) throws Exception {
            if (honoDeviceSync != null) {
                auth.authenticationProvider(new PreAuthHonoAuthenticationProvider(honoDeviceSync,
                        ddiSecurityConfiguration.getRp().getTrustedIPs()));
            }
            auth.authenticationProvider(new PreAuthTokenSourceTrustAuthenticationProvider(
                    ddiSecurityConfiguration.getRp().getTrustedIPs()));
        }
    }

    /**
     * Security configuration for the REST management API.
     */
    @Configuration
    @Order(350)
    @EnableWebSecurity
    @ConditionalOnClass(MgmtApiConfiguration.class)
    public static class RestSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Autowired
        private UserAuthenticationFilter userAuthenticationFilter;

        @Autowired(required = false)
        private OidcBearerTokenAuthenticationFilter oidcBearerTokenAuthenticationFilter;

        @Autowired(required = false)
        private InMemoryClientRegistrationRepository clientRegistrationRepository;

        @Autowired
        private SystemManagement systemManagement;

        @Autowired
        private HawkbitSecurityProperties securityProperties;

        @Autowired
        private SystemSecurityContext systemSecurityContext;

        /**
         * Filter to protect the hawkBit server Management interface against to
         * many requests.
         * 
         * @param securityProperties
         *            for filter configuration
         *
         * @return the spring filter registration bean for registering a denial
         *         of service protection filter in the filter chain
         */
        @Bean
        @ConditionalOnProperty(prefix = "hawkbit.server.security.dos.filter", name = "enabled", matchIfMissing = true)
        public FilterRegistrationBean<DosFilter> dosMgmtFilter(final HawkbitSecurityProperties securityProperties) {

            final FilterRegistrationBean<DosFilter> filterRegBean = dosFilter(null,
                    securityProperties.getDos().getFilter(), securityProperties.getClients());
            filterRegBean.setUrlPatterns(Arrays.asList("/rest/*", "/api/*"));
            filterRegBean.setOrder(DOS_FILTER_ORDER);
            filterRegBean.setName("dosMgmtFilter");

            return filterRegBean;
        }

        @Override
        protected void configure(final HttpSecurity http) throws Exception {

            HttpSecurity httpSec = http.regexMatcher("\\/rest.*|\\/system/admin.*").csrf().disable();

            if (securityProperties.getCors().isEnabled()) {
                httpSec = httpSec.cors().and();
            }

            if (securityProperties.isRequireSsl()) {
                httpSec = httpSec.requiresChannel().anyRequest().requiresSecure().and();
            }

            httpSec.authorizeRequests().anyRequest().authenticated()
                    .antMatchers(MgmtRestConstants.BASE_SYSTEM_MAPPING + "/admin/**")
                    .hasAnyAuthority(SpPermission.SYSTEM_ADMIN);

            if (oidcBearerTokenAuthenticationFilter != null) {

                // Only get the first client registration. Testing against every
                // client could increase the
                // attack vector
                ClientRegistration clientRegistration = null;
                for (final ClientRegistration cr : clientRegistrationRepository) {
                    clientRegistration = cr;
                    break;
                }

                Assert.notNull(clientRegistration, "There must be a valid client registration");
                httpSec.oauth2ResourceServer().jwt().jwkSetUri(clientRegistration.getProviderDetails().getJwkSetUri());

                oidcBearerTokenAuthenticationFilter.setClientRegistration(clientRegistration);

                httpSec.addFilterAfter(oidcBearerTokenAuthenticationFilter, BearerTokenAuthenticationFilter.class);
            } else {
                final BasicAuthenticationEntryPoint basicAuthEntryPoint = new BasicAuthenticationEntryPoint();
                basicAuthEntryPoint.setRealmName(securityProperties.getBasicRealm());

                httpSec.addFilterBefore(new Filter() {
                    @Override
                    public void init(final FilterConfig filterConfig) throws ServletException {
                        userAuthenticationFilter.init(filterConfig);
                    }

                    @Override
                    public void doFilter(final ServletRequest request, final ServletResponse response,
                            final FilterChain chain) throws IOException, ServletException {
                        userAuthenticationFilter.doFilter(request, response, chain);
                    }

                    @Override
                    public void destroy() {
                        userAuthenticationFilter.destroy();
                    }
                }, RequestHeaderAuthenticationFilter.class);
                httpSec.httpBasic().and().exceptionHandling().authenticationEntryPoint(basicAuthEntryPoint);
            }

            httpSec.addFilterAfter(
                    new AuthenticationSuccessTenantMetadataCreationFilter(systemManagement, systemSecurityContext),
                    SessionManagementFilter.class);

            httpSec.anonymous().disable();
            httpSec.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        }

        @Bean
        @ConditionalOnProperty(prefix = "hawkbit.server.security.cors", name = "enabled", matchIfMissing = false)
        CorsConfigurationSource corsConfigurationSource() {
            final CorsConfiguration restCorsConfiguration = new CorsConfiguration();

            restCorsConfiguration.setAllowedOrigins(securityProperties.getCors().getAllowedOrigins());
            restCorsConfiguration.setAllowCredentials(true);
            restCorsConfiguration.setAllowedHeaders(securityProperties.getCors().getAllowedHeaders());
            restCorsConfiguration.setAllowedMethods(securityProperties.getCors().getAllowedMethods());

            final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/rest/**", restCorsConfiguration);

            return source;
        }
    }

    /**
     * {@link WebSecurityConfigurer} for external (management) access.
     */
    @Configuration
    @Order(400)
    @EnableVaadinSecurity
    @ConditionalOnClass(MgmtUiConfiguration.class)
    public static class UISecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Autowired
        private VaadinSecurityContext vaadinSecurityContext;

        @Autowired
        private HawkbitSecurityProperties hawkbitSecurityProperties;

        private final VaadinUrlAuthenticationSuccessHandler handler;

        @Autowired(required = false)
        private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;

        @Autowired(required = false)
        private AuthenticationSuccessHandler authenticationSuccessHandler;

        @Autowired
        private LogoutHandler logoutHandler;

        @Autowired
        private LogoutSuccessHandler logoutSuccessHandler;


        public UISecurityConfigurationAdapter(final VaadinRedirectStrategy redirectStrategy) {
            handler = new TenantMetadataSavedRequestAwareVaadinAuthenticationSuccessHandler();
            handler.setRedirectStrategy(redirectStrategy);
            handler.setDefaultTargetUrl("/UI/");
            handler.setTargetUrlParameter("r");
        }

        /**
         * Filter to protect the hawkBit management UI against to many requests.
         * 
         * @param securityProperties
         *            for filter configuration
         *
         * @return the spring filter registration bean for registering a denial
         *         of service protection filter in the filter chain
         */
        @Bean
        @ConditionalOnProperty(prefix = "hawkbit.server.security.dos.ui-filter", name = "enabled", matchIfMissing = true)
        public FilterRegistrationBean<DosFilter> dosMgmtUiFilter(final HawkbitSecurityProperties securityProperties) {

            final FilterRegistrationBean<DosFilter> filterRegBean = dosFilter(null,
                    securityProperties.getDos().getUiFilter(), securityProperties.getClients());
            // All URLs that can be called anonymous
            filterRegBean.setUrlPatterns(Arrays.asList("/UI/login", "/UI/login/*", "/UI/logout", "/UI/logout/*"));
            filterRegBean.setOrder(DOS_FILTER_ORDER);
            filterRegBean.setName("dosMgmtUiFilter");

            return filterRegBean;
        }

        /**
         * post construct for setting the authentication success handler for the
         * vaadin security context.
         */
        @PostConstruct
        public void afterPropertiesSet() {
            this.vaadinSecurityContext.addAuthenticationSuccessHandler(handler);
        }

        @Override
        @Bean(name = "authenticationManager")
        public AuthenticationManager authenticationManagerBean() throws Exception {
            return super.authenticationManagerBean();
        }

        /**
         * @return the vaadin success authentication handler
         */
        @Bean
        public VaadinAuthenticationSuccessHandler redirectSaveHandler() {
            return handler;
        }

        /**
         * Listener to redirect to login page after session timeout. Close the
         * vaadin session, because it's is not possible to redirect in
         * atmosphere.
         *
         * @return the servlet listener.
         */
        @Bean
        public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
            return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
        }

        @Override
        protected void configure(final HttpSecurity http) throws Exception {

            final boolean enableOidc = oidcUserService != null && authenticationSuccessHandler != null;

            // workaround regex: we need to exclude the URL /UI/HEARTBEAT here
            // because we bound the vaadin application to /UI and not to root,
            // described in vaadin-forum:
            // https://vaadin.com/forum#!/thread/3200565.
            HttpSecurity httpSec;
            if (enableOidc) {
                httpSec = http.regexMatcher("(?!.*HEARTBEAT)^.*\\/(UI|oauth2).*$");
            } else {
                httpSec = http.regexMatcher("(?!.*HEARTBEAT)^.*\\/UI.*$");
            }
            // disable as CSRF is handled by Vaadin
            httpSec.csrf().disable();

            if (hawkbitSecurityProperties.isRequireSsl()) {
                httpSec = httpSec.requiresChannel().anyRequest().requiresSecure().and();
            } else {

                LOG.info(
                        "\"******************\\n** Requires HTTPS Security has been disabled for UI, should only be used for developing purposes **\\n******************\"");
            }

            if (!StringUtils.isEmpty(hawkbitSecurityProperties.getContentSecurityPolicy())) {
                httpSec.headers().contentSecurityPolicy(hawkbitSecurityProperties.getContentSecurityPolicy());
            }

            // UI
            httpSec.authorizeRequests().antMatchers("/UI/login/**", "/UI/UIDL/**").permitAll()
                    .anyRequest().authenticated();

            if (enableOidc) {
                // OIDC
                httpSec.oauth2Login().userInfoEndpoint().oidcUserService(oidcUserService).and()
                        .successHandler(authenticationSuccessHandler).and().oauth2Client();
            } else {
                // UI login / Basic auth
                httpSec.exceptionHandling().authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/UI/login"));
            }

            // UI logout
            httpSec.logout().logoutUrl("/UI/logout*").addLogoutHandler(logoutHandler)
                    .logoutSuccessHandler(logoutSuccessHandler);

        }

        @Override
        public void configure(final WebSecurity webSecurity) throws Exception {
            // Not security for static content
            webSecurity.ignoring().antMatchers("/documentation/**", "/VAADIN/**", "/*.*", "/docs/**");
        }
    }

}

/**
 * After a successful login on the UI we need to ensure to create the tenant
 * meta data within SP.
 */
class TenantMetadataSavedRequestAwareVaadinAuthenticationSuccessHandler extends VaadinUrlAuthenticationSuccessHandler {

    @Autowired
    private SystemManagement systemManagement;

    @Autowired
    private SystemSecurityContext systemSecurityContext;

    @Override
    public void onAuthenticationSuccess(final Authentication authentication) throws Exception {

        if (authentication.getClass().equals(TenantUserPasswordAuthenticationToken.class)) {
            systemSecurityContext.runAsSystemAsTenant(systemManagement::getTenantMetadata,
                    ((TenantUserPasswordAuthenticationToken) authentication).getTenant().toString());
        } else if (authentication.getClass().equals(UsernamePasswordAuthenticationToken.class)) {
            // TODO: vaadin4spring-ext-security does not give us the
            // fullyAuthenticatedToken
            // in the GenericVaadinSecurity class. Only the token which has been
            // created in the
            // LoginView. This needs to be changed with the update of
            // vaadin4spring 0.0.7 because it
            // has been fixed.
            final String defaultTenant = "DEFAULT";
            systemSecurityContext.runAsSystemAsTenant(systemManagement::getTenantMetadata, defaultTenant);
        }

        super.onAuthenticationSuccess(authentication);
    }
}

/**
 * Servletfilter to create metadata after successful authentication over RESTful.
 */
class AuthenticationSuccessTenantMetadataCreationFilter implements Filter {

    private final SystemManagement systemManagement;
    private final SystemSecurityContext systemSecurityContext;

    AuthenticationSuccessTenantMetadataCreationFilter(final SystemManagement systemManagement,
            final SystemSecurityContext systemSecurityContext) {
        this.systemManagement = systemManagement;
        this.systemSecurityContext = systemSecurityContext;
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        // not needed
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        lazyCreateTenantMetadata();
        chain.doFilter(request, response);

    }

    private void lazyCreateTenantMetadata() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            systemSecurityContext.runAsSystem(systemManagement::getTenantMetadata);
        }
    }

    @Override
    public void destroy() {
        // not needed
    }

}
