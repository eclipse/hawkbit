/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.hawkbit.repository.test.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.hawkbit.repository.event.TenantAwareEvent;
import org.eclipse.hawkbit.repository.test.util.TenantEventCounter;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Test rule to setup and verify the event count for a method.
 */
public class EventVerifier extends AbstractTestExecutionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventVerifier.class);

    @Override
    public void afterTestExecution(final TestContext testContext) {
        final Map<Class<?>, Integer> expectedEvents = getExpectations(testContext);
        if (expectedEvents.isEmpty()) {
            return;
        }
        final TenantEventCounter eventCounter = testContext.getApplicationContext().getBean(TenantEventCounter.class);
        final String tenant = testContext.getApplicationContext().getBean(TenantAware.class).getCurrentTenant();
        logEvents(eventCounter.getEventsCount(tenant), expectedEvents);
        verifyRightCountOfEvents(tenant, eventCounter, expectedEvents);
        verifyAllEventsCounted(eventCounter.getEventsCount(tenant), expectedEvents);
    }

    private static Map<Class<?>, Integer> getExpectations(final TestContext testContext) {
        final ExpectEvents methodAnnotation = testContext.getTestMethod().getAnnotation(ExpectEvents.class);

        if (methodAnnotation == null) {
            return Collections.emptyMap();
        }
        return isAutoCreateTenant(testContext) ?
                asMap(methodAnnotation.value(), getBeforeMethodExpects(testContext)) :
                asMap(methodAnnotation.value());
    }

    private static boolean isAutoCreateTenant(final TestContext testContext) {
        final WithUser withUser = testContext.getTestMethod().isAnnotationPresent(WithUser.class) ?
                testContext.getTestMethod().getAnnotation(WithUser.class) :
                testContext.getTestClass().getAnnotation(WithUser.class);

        return withUser == null || withUser.autoCreateTenant();
    }

    private static Map<Class<?>, Integer> asMap(final Expect[]... expectsArray) {
        final Map<Class<?>, Integer> expectsMap = new HashMap<>();
        for (final Expect[] expects : expectsArray) {
            for (Expect expect : expects) {
                expectsMap.merge(expect.type(), expect.count(), Integer::sum);
            }
        }
        return expectsMap;
    }

    private static Expect[] getBeforeMethodExpects(final TestContext testContext) {
        Class<?> clazz = testContext.getTestClass();
        do {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(BeforeEach.class) && method.isAnnotationPresent(ExpectEvents.class)) {
                    return method.getAnnotation(ExpectEvents.class).value();
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        return new Expect[0];
    }

    private static void verifyRightCountOfEvents(final String tenant, final TenantEventCounter eventCounter,
            final Map<Class<?>, Integer> expectedEvents) {

        final StringBuilder failMessage = new StringBuilder();

        for (Map.Entry<Class<?>, Integer> expected : expectedEvents.entrySet()) {
            try {
                Awaitility.await()
                        .atMost(Duration.FIVE_SECONDS).pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
                        .pollInSameThread()
                        .untilAsserted(() -> assertThat(expected.getValue())
                                .isEqualTo(eventCounter.getEventsCount(tenant).getOrDefault(expected.getKey(), 0)));
            } catch (final ConditionTimeoutException e) {
                LOGGER.trace("", e);
                failMessage.append(expected.getKey().getSimpleName())
                        .append("\t\t[expected: ")
                        .append(expected.getValue())
                        .append(", but was: ")
                        .append(eventCounter.getEventsCount(tenant).getOrDefault(expected.getKey(), 0))
                        .append("]\n");
            }
        }

        if (!failMessage.toString().isEmpty()) {
            fail("Did not receive the expected amount of events.\n" + failMessage);
        }
    }

    private static void logEvents(final Map<Class<? extends TenantAwareEvent>, Integer> receivedEvents,
            final Map<Class<?>, Integer> expectedEvents) {
        LOGGER.trace("=============== Actual (Observed) Events ===============");
        receivedEvents.forEach((aClass, integer) -> LOGGER.trace("{} -> {}", aClass.getSimpleName(), integer));
        LOGGER.trace("===============================================\n\n");

        LOGGER.trace("=============== Expected Events ===============");
        expectedEvents.forEach((aClass, integer) -> LOGGER.trace("{} -> {}", aClass.getSimpleName(), integer));
        LOGGER.trace("===============================================\n\n");
    }

    private static void verifyAllEventsCounted(final Map<Class<? extends TenantAwareEvent>, Integer> receivedEvents,
            final Map<Class<?>, Integer> expectedEvents) {
        final StringBuilder failMessage = new StringBuilder();

        for (Map.Entry<Class<? extends TenantAwareEvent>, Integer> received : receivedEvents.entrySet()) {
            if (!expectedEvents.containsKey(received.getKey())) {
                failMessage.append("\n").append(received.getKey()).append(" with count: ").append(received.getValue());
            }
        }

        if (!failMessage.toString().isEmpty()) {
            fail("Missing event verification for [" + failMessage.append("]").toString());
        }
    }
}
