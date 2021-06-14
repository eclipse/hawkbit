/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.event.TenantAwareEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * A Spring bean that listens on all {@link ApplicationEvent}s from all tenants.
 * See {@link org.eclipse.hawkbit.repository.test.matcher.EventVerifier} for the usage
 */
public class TenantEventCounter implements ApplicationListener<ApplicationEvent> {

    // Using static field to mitigate the spring context refresh, any changes written in this collection
    // will be persisted even between multiple beans construction/destruction
    private final Map<String, Set<TenantAwareEvent>> tenantEventsCount = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(final ApplicationEvent event) {
        if (event instanceof TenantAwareEvent) {
            final String tenant = ((TenantAwareEvent) event).getTenant();
            assertThat(tenant).isNotBlank();
            tenantEventsCount.merge(tenant, toSet((TenantAwareEvent) event), TenantEventCounter::mergeEvents);
        }
    }

    private static Set<TenantAwareEvent> toSet(final TenantAwareEvent event) {
        final Set<TenantAwareEvent> events = Collections.newSetFromMap(new ConcurrentHashMap<>());
        events.add(event);
        return events;
    }

    private static Set<TenantAwareEvent> mergeEvents(final Set<TenantAwareEvent> events1,
            final Set<TenantAwareEvent> events2) {
        if (events1.size() > events2.size()) {
            events1.addAll(events2);
            return events1;
        }
        events2.addAll(events1);
        return events2;
    }

    public Map<Class<? extends TenantAwareEvent>, Integer> getEventsCount(final String tenant) {
        final Set<? extends TenantAwareEvent> events = tenantEventsCount.getOrDefault(tenant, Collections.emptySet());
        return events.stream().collect(Collectors.toMap(e -> e.getClass(), e -> 1, Integer::sum));
    }
}
