/**
 * Copyright (c) 2019 Kiwigrid GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.autoconfigure.security;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.im.authentication.UserAuthenticationFilter;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.oauth2.client.ClientsConfiguredCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithms;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Auto-configuration for OpenID Connect user management.
 *
 */
@Configuration
@Conditional(value = ClientsConfiguredCondition.class)
public class OidcUserManagementAutoConfiguration {

    /**
     * @return the oauth2 user details service to load a user from oidc user
     *         manager
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserDetailsService(
            final JwtAuthoritiesExtractor extractor) {
        return new JwtAuthoritiesOidcUserService(extractor);
    }

    /**
     * @return the logout success handler for OpenID Connect
     */
    @Bean
    public LogoutSuccessHandler oidcLogoutSuccessHandler() {
        return new OidcLogoutSuccessHandler();
    }

    /**
     * @return the OpenID Connect authentication success handler
     */
    @Bean
    public AuthenticationSuccessHandler oidcAuthenticationSuccessHandler() {
        return new OidcAuthenticationSuccessHandler();
    }

    /**
     * @return the OpenID Connect logout handler
     */
    @Bean
    public LogoutHandler oidcLogoutHandler() {
        return new OidcLogoutHandler();
    }

    /**
     * @return a jwt authorities extractor which interprets the roles of a user
     *         as their authorities.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthoritiesExtractor jwtAuthoritiesExtractor() {
        final SimpleAuthorityMapper authorityMapper = new SimpleAuthorityMapper();
        authorityMapper.setPrefix("");
        authorityMapper.setConvertToUpperCase(true);

        return new JwtAuthoritiesExtractor(authorityMapper);
    }

    /**
     * @return an authentication filter for using OAuth2 Bearer Tokens.
     */
    @Bean
    @ConditionalOnMissingBean
    public OidcBearerTokenAuthenticationFilter oidcBearerTokenAuthenticationFilter() {
        return new OidcBearerTokenAuthenticationFilter();
    }
}

/**
 * Extended {@link OidcUserService} supporting JWT containing authorities
 */
class JwtAuthoritiesOidcUserService extends OidcUserService {

    private final JwtAuthoritiesExtractor authoritiesExtractor;

    JwtAuthoritiesOidcUserService(final JwtAuthoritiesExtractor authoritiesExtractor) {
        super();

        this.authoritiesExtractor = authoritiesExtractor;
    }

    @Override
    public OidcUser loadUser(final OidcUserRequest userRequest) {
        final OidcUser user = super.loadUser(userRequest);
        final ClientRegistration clientRegistration = userRequest.getClientRegistration();

        final Set<GrantedAuthority> authorities = authoritiesExtractor.extract(clientRegistration,
                userRequest.getAccessToken().getTokenValue());
        if (authorities.isEmpty()) {
            return user;
        }

        final String userNameAttributeName = clientRegistration.getProviderDetails().getUserInfoEndpoint()
                .getUserNameAttributeName();
        final OidcUser oidcUser;
        if (StringUtils.hasText(userNameAttributeName) && user.getClaims().containsKey(userNameAttributeName)) {
            oidcUser = new DefaultOidcUser(authorities, userRequest.getIdToken(), user.getUserInfo(),
                    userNameAttributeName);
        } else {
            oidcUser = new DefaultOidcUser(authorities, userRequest.getIdToken(), user.getUserInfo());
        }
        return oidcUser;
    }
}

/**
 * OpenID Connect Authentication Success Handler which load tenant data
 */
class OidcAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Autowired
    private SystemManagement systemManagement;

    @Autowired
    private SystemSecurityContext systemSecurityContext;

    @Override
    public void onAuthenticationSuccess(final HttpServletRequest request, final HttpServletResponse response,
            final Authentication authentication) throws ServletException, IOException {
        if (authentication instanceof AbstractAuthenticationToken) {
            final String defaultTenant = "DEFAULT";

            final AbstractAuthenticationToken token = (AbstractAuthenticationToken) authentication;
            token.setDetails(new TenantAwareAuthenticationDetails(defaultTenant, false));

            systemSecurityContext.runAsSystemAsTenant(systemManagement::getTenantMetadata, defaultTenant);
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}

/**
 * LogoutHandler to invalidate OpenID Connect tokens
 */
class OidcLogoutHandler extends SecurityContextLogoutHandler {

    @Override
    public void logout(final HttpServletRequest request, final HttpServletResponse response,
            final Authentication authentication) {
        super.logout(request, response, authentication);

        final Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser) {
            final OidcUser user = (OidcUser) authentication.getPrincipal();
            final String endSessionEndpoint = user.getIssuer() + "/protocol/openid-connect/logout";

            final UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endSessionEndpoint)
                    .queryParam("id_token_hint", user.getIdToken().getTokenValue());

            final RestTemplate restTemplate = new RestTemplate();
            restTemplate.getForEntity(builder.toUriString(), String.class);
        }
    }
}

/**
 * LogoutSuccessHandler that decides where to redirect to after logout, depending on
 * the previously used auth mechanism
 */
class OidcLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (authentication instanceof OAuth2AuthenticationToken) {
            this.setTargetUrlParameter("/");
        } else {
            this.setTargetUrlParameter("login");
        }
        super.onLogoutSuccess(request, response, authentication);
    }
}

/**
 * Utility class to extract authorities out of the jwt. It interprets the user's
 * role as their authorities.
 */
class JwtAuthoritiesExtractor {

    private final GrantedAuthoritiesMapper authoritiesMapper;

    private static final OAuth2Error INVALID_REQUEST = new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST);

    JwtAuthoritiesExtractor(final GrantedAuthoritiesMapper authoritiesMapper) {
        super();

        this.authoritiesMapper = authoritiesMapper;
    }

    Set<GrantedAuthority> extract(final ClientRegistration clientRegistration, final String tokenValue) {
        try {
            // Token is already verified by spring security
            final NimbusJwtDecoder jwtDecoder =
                NimbusJwtDecoder
                    .withJwkSetUri(clientRegistration.getProviderDetails().getJwkSetUri())
                    .jwsAlgorithm(SignatureAlgorithm.from(JwsAlgorithms.RS256))
                    .build();
            final Jwt token = jwtDecoder.decode(tokenValue);

            return extract(clientRegistration.getClientId(), token.getClaims());
        } catch (final JwtException e) {
            throw new OAuth2AuthenticationException(INVALID_REQUEST, e);
        }
    }

    @SuppressWarnings("unchecked")
    Set<GrantedAuthority> extract(final String clientId, final Map<String, Object> claims) {
        final Map<String, Object> resourceMap = (Map<String, Object>) claims.get("resource_access");

        final Map<String, Map<String, Object>> clientResource = (Map<String, Map<String, Object>>) resourceMap
                .get(clientId);
        if (CollectionUtils.isEmpty(clientResource)) {
            return Collections.emptySet();
        }

        final List<String> roles = (List<String>) clientResource.get("roles");
        if (CollectionUtils.isEmpty(roles)) {
            return Collections.emptySet();
        }

        final List<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList(roles.toArray(new String[0]));
        if (authoritiesMapper != null) {
            return new LinkedHashSet<>(authoritiesMapper.mapAuthorities(authorities));
        }

        return new LinkedHashSet<>(authorities);
    }
}

class OidcBearerTokenAuthenticationFilter implements UserAuthenticationFilter, Filter {

    @Autowired
    private JwtAuthoritiesExtractor authoritiesExtractor;

    @Autowired
    private SystemManagement systemManagement;

    @Autowired
    private SystemSecurityContext systemSecurityContext;

    private ClientRegistration clientRegistration;

    void setClientRegistration(final ClientRegistration clientRegistration) {
        this.clientRegistration = clientRegistration;
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken) {
            final String defaultTenant = "DEFAULT";

            final JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) authentication;
            final Jwt jwt = jwtAuthenticationToken.getToken();
            final OidcIdToken idToken = new OidcIdToken(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(),
                    jwt.getClaims());
            final OidcUserInfo userInfo = new OidcUserInfo(jwt.getClaims());

            final Set<GrantedAuthority> authorities = authoritiesExtractor.extract(clientRegistration.getClientId(),
                    jwt.getClaims());

            if (authorities.isEmpty()) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            final String userNameAttributeName = clientRegistration.getProviderDetails().getUserInfoEndpoint()
                    .getUserNameAttributeName();

            final DefaultOidcUser oidcUser;            
            if (StringUtils.hasText(userNameAttributeName) && jwt.getClaims().containsKey(userNameAttributeName)) {
                oidcUser = new DefaultOidcUser(authorities, idToken, userInfo, userNameAttributeName);

            } else {
                oidcUser = new DefaultOidcUser(authorities, idToken, userInfo);
            }

            final OAuth2AuthenticationToken oAuth2AuthenticationToken = new OAuth2AuthenticationToken(oidcUser, authorities,
                    clientRegistration.getRegistrationId());

            oAuth2AuthenticationToken.setDetails(new TenantAwareAuthenticationDetails(defaultTenant, false));

            systemSecurityContext.runAsSystemAsTenant(systemManagement::getTenantMetadata, defaultTenant);
            SecurityContextHolder.getContext().setAuthentication(oAuth2AuthenticationToken);
        }

        chain.doFilter(request, response);
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        // Nothing to do
    }

    @Override
    public void destroy() {
        // Nothing to do
    }
}
