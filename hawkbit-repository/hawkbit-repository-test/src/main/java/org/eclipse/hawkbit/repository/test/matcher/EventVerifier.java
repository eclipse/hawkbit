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
import static org.hamcrest.Matchers.equalTo;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.hawkbit.repository.event.remote.RemoteIdEvent;
import org.eclipse.hawkbit.repository.event.remote.RemoteTenantAwareEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ConditionTimeoutException;

/**
 * Test rule to setup and verify the event count for a method.
 */
public class EventVerifier extends AbstractTestExecutionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventVerifier.class);

    private EventCaptor eventCaptor;

    @Override
    public void beforeTestMethod(final TestContext testContext) throws Exception {
        final Optional<Expect[]> expectedEvents = getExpectationsFrom(testContext.getTestMethod());
        expectedEvents.ifPresent(events -> beforeTest(testContext));
    }

    @Override
    public void afterTestMethod(final TestContext testContext) throws Exception {
        final Optional<Expect[]> expectedEvents = getExpectationsFrom(testContext.getTestMethod());
        try {
            expectedEvents.ifPresent(events -> afterTest(events));
        } finally {
            expectedEvents.ifPresent(listener -> removeEventListener(testContext));
        }
    }

    private Optional<Expect[]> getExpectationsFrom(final Method testMethod) {
        return Optional.ofNullable(testMethod.getAnnotation(ExpectEvents.class)).map(ExpectEvents::value);
    }

    private void beforeTest(final TestContext testContext) {
        final ConfigurableApplicationContext context = (ConfigurableApplicationContext) testContext
                .getApplicationContext();
        eventCaptor = new EventCaptor();
        context.addApplicationListener(eventCaptor);
    }

    private void afterTest(final Expect[] expectedEvents) {
        verifyRightCountOfEvents(expectedEvents);
        verifyAllEventsCounted(expectedEvents);
    }

    private void verifyRightCountOfEvents(final Expect[] expectedEvents) {

        for (final Expect expectedEvent : expectedEvents) {
            try {
                Awaitility.await().atMost(30, TimeUnit.SECONDS)
                        .until(() -> eventCaptor.getCountFor(expectedEvent.type()), equalTo(expectedEvent.count()));

            } catch (final ConditionTimeoutException ex) {
                Assert.fail("Did not receive the expected amount of events form " + expectedEvent.type() + " Expected: "
                        + expectedEvent.count() + " but was: " + eventCaptor.getCountFor(expectedEvent.type()));
            }
        }
    }

    private void verifyAllEventsCounted(final Expect[] expectedEvents) {

        final Set<Class<?>> diffSet = eventCaptor.diff(expectedEvents);
        if (diffSet.size() > 0) {
            final StringBuilder failMessage = new StringBuilder("Missing event verification for ");
            final Iterator<Class<?>> itr = diffSet.iterator();
            while (itr.hasNext()) {
                final Class<?> element = itr.next();
                final int count = eventCaptor.getCountFor(element);
                failMessage.append(element + " with count: " + count + " ");
            }
            Assert.fail(failMessage.toString());
        }

    }

    private void removeEventListener(final TestContext testContext) {
        final ApplicationEventMulticaster multicaster = testContext.getApplicationContext()
                .getBean(ApplicationEventMulticaster.class);
        multicaster.removeApplicationListener(eventCaptor);
    }

    private static class EventCaptor implements ApplicationListener<RemoteApplicationEvent> {

        private final Multiset<Class<?>> capturedEvents = ConcurrentHashMultiset.create();

        @Override
        public void onApplicationEvent(final RemoteApplicationEvent event) {
            LOGGER.debug("Received event {}", event.getClass().getSimpleName());

            if (event instanceof RemoteTenantAwareEvent) {
                assertThat(((RemoteTenantAwareEvent) event).getTenant()).isNotEmpty();
            }

            if (event instanceof RemoteIdEvent) {
                assertThat(((RemoteIdEvent) event).getEntityId()).isNotNull();
            }

            if (event instanceof TargetAssignDistributionSetEvent) {
                assertThat(((TargetAssignDistributionSetEvent) event).getActionId()).isNotNull();
                assertThat(((TargetAssignDistributionSetEvent) event).getControllerId()).isNotEmpty();
                assertThat(((TargetAssignDistributionSetEvent) event).getDistributionSetId()).isNotNull();
            }

            capturedEvents.add(event.getClass());
        }

        public int getCountFor(final Class<?> expectedEvent) {
            return capturedEvents.count(expectedEvent);
        }

        public Set<Class<?>> diff(final Expect[] allEvents) {
            return Sets.difference(capturedEvents.elementSet(),
                    Stream.of(allEvents).map(Expect::type).collect(Collectors.toSet()));
        }

    }

}
