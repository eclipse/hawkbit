/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.im.authentication.UserPrincipal;
import org.eclipse.hawkbit.repository.model.helper.SystemManagementHolder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

public class WithSpringAuthorityRule implements BeforeEachCallback, AfterEachCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(WithSpringAuthorityRule.class);

    private static final ThreadLocal<WithUser> TEST_SECURITY_CONTEXT = new InheritableThreadLocal<>();
    private static final ThreadLocal<SecurityContext> OLD_TEST_SECURITY_CONTEXT = new InheritableThreadLocal<>();
    private final Map<String, WithUser> securityContexts = new ConcurrentHashMap<>(1000);

    @Override
    public void beforeEach(final ExtensionContext extensionContext) throws Exception {
        OLD_TEST_SECURITY_CONTEXT.set(SecurityContextHolder.getContext());

        final WithUser annotation = getWithUserAnnotation(extensionContext);
        if (annotation != null) {
            if (annotation.autoCreateTenant()) {
                createTenant(annotation.tenantId());
            }
            LOGGER.info("Setting security context for tenant {}", annotation.tenantId());
            setSecurityContext(annotation);
        }
    }

    @Override
    public void afterEach(final ExtensionContext extensionContext) throws Exception {
        after(OLD_TEST_SECURITY_CONTEXT.get());
        TEST_SECURITY_CONTEXT.remove();
        OLD_TEST_SECURITY_CONTEXT.remove();
    }

    private WithUser getWithUserAnnotation(final ExtensionContext ctx) {
        return securityContexts.computeIfAbsent(ctx.getRequiredTestClass().getSimpleName() + ctx.getRequiredTestMethod().getName(),
                s -> getMethodAnnotation(ctx.getRequiredTestMethod())
                        .map(WithSpringAuthorityRule::createWithUser)
                        .orElseGet(() -> getTestClassAnnotation(ctx)));
    }

    private static Optional<WithUser> getMethodAnnotation(final Method method) {
        final WithUser methodAnnotation = method.getAnnotation(WithUser.class);
        return Optional.ofNullable(methodAnnotation);
    }

    private WithUser getTestClassAnnotation(final ExtensionContext description) {
        final Class<?> testClass = description.getRequiredTestClass();
        final WithUser withUser = securityContexts.computeIfAbsent(testClass.getName(),
                className -> testClass.getAnnotation(WithUser.class));

        return withUser.autoCreateTenant() ? createWithUser(withUser) : withUser;
    }

    private static void setSecurityContext(final WithUser annotation) {
        TEST_SECURITY_CONTEXT.set(annotation);
        final String[] authorities = annotation.allSpPermissions() ? getAllAuthorities(annotation.authorities(),
                        new HashSet<>(Arrays.asList(annotation.removeFromAllPermission()))) :
                annotation.authorities();

        final TestingAuthenticationToken testingAuthenticationToken = new TestingAuthenticationToken(
                new UserPrincipal(annotation.principal(), annotation.principal(), annotation.principal(),
                        annotation.principal(), null, annotation.tenantId()),
                annotation.credentials(), authorities);
        testingAuthenticationToken.setDetails(
                new TenantAwareAuthenticationDetails(annotation.tenantId(), annotation.controller()));

        SecurityContextHolder.setContext(new SecurityContextImpl(testingAuthenticationToken));
    }

    private static String[] getAllAuthorities(final String[] additionalAuthorities, final Set<String> notInclude) {
        final Field[] declaredFields = SpPermission.class.getDeclaredFields();
        final List<String> allPermissions = new ArrayList<>();

        for (Field f : declaredFields) {
            if (isPublic(f.getModifiers()) && isStatic(f.getModifiers())) {
                String suppress = suppress(() -> (String) f.get(null));
                if (!notInclude.contains(suppress)) {
                    allPermissions.add(suppress);
                }
            }
        }

        allPermissions.addAll(Arrays.asList(additionalAuthorities));
        return allPermissions.toArray(new String[0]);
    }

    private static <T> T suppress(final Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            // don't want to log this exceptions.
        }
        return null;
    }

    private static void after(final SecurityContext oldContext) {
        SecurityContextHolder.setContext(oldContext);
    }

    public static <T> T runAsPrivileged(final Callable<T> callable) throws Exception {
        if(TEST_SECURITY_CONTEXT.get() == null || !TEST_SECURITY_CONTEXT.get().autoCreateTenant()){
            callable.call();
        }
        final WithUser withUser = privilegedUser();
        return runAs(withUser, callable);
    }

    public static <T> T  runAs(final WithUser withUser, final Callable<T> callable) throws Exception {
        final SecurityContext oldContext = SecurityContextHolder.getContext();
        setSecurityContext(withUser);
        if (withUser.autoCreateTenant()) {
            createTenant(withUser.tenantId());
        }
        try {
            return callable.call();
        } finally {
            after(oldContext);
        }
    }

    private static void createTenant(final String tenantId) {
        final SecurityContext oldContext = SecurityContextHolder.getContext();
        setSecurityContext(privilegedUser());
        try {
            SystemManagementHolder.getInstance().getSystemManagement().getTenantMetadata(tenantId);
        } finally {
            after(oldContext);
        }
    }

    public static WithUser withController(final String principal, final String... authorities) {
        final String tenantId = TEST_SECURITY_CONTEXT.get() != null ? TEST_SECURITY_CONTEXT.get().tenantId() : "";
        return withUserAndTenant(principal, tenantId, true, true, true, authorities);
    }

    public static WithUser withUser(final String principal, final String... authorities) {
        final String tenantId = TEST_SECURITY_CONTEXT.get() != null ? TEST_SECURITY_CONTEXT.get().tenantId() : "";
        return withUserAndTenant(principal, tenantId, true, true, false, authorities);
    }

    public static WithUser withUser(final String principal, final boolean allSpPermision, final String... authorities) {
        final String tenantId = TEST_SECURITY_CONTEXT.get() != null ? TEST_SECURITY_CONTEXT.get().tenantId() : "";
        return withUserAndTenant(principal, tenantId, true, allSpPermision, false, authorities);
    }

    public static WithUser withUser(final boolean autoCreateTenant) {
        final String tenantId = TEST_SECURITY_CONTEXT.get() != null ? TEST_SECURITY_CONTEXT.get().tenantId() : "";
        return withUserAndTenant("", tenantId, autoCreateTenant, true, false);
    }

    public static WithUser withUserAndTenant(final String principal, final String tenant, final String... authorities) {
        return withUserAndTenant(principal, tenant, true, true, false, authorities);
    }

    public static WithUser withUserAndTenant(final String principal, final String tenant,
            final boolean autoCreateTenant, final boolean allSpPermission) {
        return withUserAndTenant(principal, tenant, autoCreateTenant, allSpPermission, false);
    }

    public static WithUser withUserAndTenant(final String principal, final String tenant,
            final boolean autoCreateTenant, final boolean allSpPermission, final boolean controller,
            final String... authorities) {
        return createWithUser(principal, tenant, autoCreateTenant, allSpPermission, controller, authorities);
    }

    private static WithUser privilegedUser() {
        final String tenant = TEST_SECURITY_CONTEXT.get() == null ? "" : TEST_SECURITY_CONTEXT.get().tenantId();
        return createWithUser("", tenant, true, true, false, new String[] { "ROLE_CONTROLLER", "ROLE_SYSTEM_CODE" });
    }

    private static WithUser createWithUser(final WithUser annotation) {
        return createWithUser(annotation.principal(), annotation.tenantId(), annotation.autoCreateTenant(),
                annotation.allSpPermissions(), annotation.controller(),
                Arrays.asList(annotation.removeFromAllPermission()), annotation.authorities());
    }

    private static WithUser createWithUser(final String principal, final String tenant, final boolean autoCreateTenant,
            final boolean allSpPermission, final boolean controller, final String[] authorities) {
        return createWithUser(principal, tenant, autoCreateTenant, allSpPermission, controller, Collections.emptyList(),
                authorities);
    }
    private static WithUser createWithUser(final String principal, final String tenant, final boolean autoCreateTenant,
            final boolean allSpPermission, final boolean controller, final List<String> removeFromAllPermission,
            final String[] authorities) {
        return new DefaultWithUser(principal, tenant, authorities, allSpPermission, removeFromAllPermission,
                autoCreateTenant, controller);
    }
}
