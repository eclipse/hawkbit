/**
 * Copyright (c) 2023 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hawkbit.repository.event.remote.CancelTargetAssignmentEvent;
import org.eclipse.hawkbit.repository.event.remote.DistributionSetDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.DistributionSetTagDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.DistributionSetTypeDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.DownloadProgressEvent;
import org.eclipse.hawkbit.repository.event.remote.MultiActionAssignEvent;
import org.eclipse.hawkbit.repository.event.remote.MultiActionCancelEvent;
import org.eclipse.hawkbit.repository.event.remote.RolloutDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.RolloutGroupDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.RolloutStoppedEvent;
import org.eclipse.hawkbit.repository.event.remote.SoftwareModuleDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.SoftwareModuleTypeDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAttributesRequestedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetFilterQueryDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetPollEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetTagDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetTypeDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.TenantConfigurationDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetTagCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetTagUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetTypeCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetTypeUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleTypeCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleTypeUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetFilterQueryCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetFilterQueryUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetTagCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetTagUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetTypeCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetTypeUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TenantConfigurationCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TenantConfigurationUpdatedEvent;

/**
 * Service to provide the corresponding instance of {@link EventType} for a specific event class.
 */
public class EventTypeProvider {

    protected static final Map<Integer, Class<?>> TYPES = new HashMap<>();

    /**
     * The associated event-type-value must remain the same as initially declared.
     * Otherwise, messages cannot correctly de-serialized.
     */
    static {

        // target
        TYPES.put(1, TargetCreatedEvent.class);
        TYPES.put(2, TargetUpdatedEvent.class);
        TYPES.put(3, TargetDeletedEvent.class);
        TYPES.put(4, CancelTargetAssignmentEvent.class);
        TYPES.put(5, TargetAssignDistributionSetEvent.class);

        // target tag
        TYPES.put(6, TargetTagCreatedEvent.class);
        TYPES.put(7, TargetTagUpdatedEvent.class);
        TYPES.put(8, TargetTagDeletedEvent.class);

        // action
        TYPES.put(9, ActionCreatedEvent.class);
        TYPES.put(10, ActionUpdatedEvent.class);

        // distribution set
        TYPES.put(11, DistributionSetCreatedEvent.class);
        TYPES.put(12, DistributionSetUpdatedEvent.class);
        TYPES.put(13, DistributionSetDeletedEvent.class);

        // distribution set tag
        TYPES.put(14, DistributionSetTagCreatedEvent.class);
        TYPES.put(15, DistributionSetTagUpdatedEvent.class);
        TYPES.put(16, DistributionSetTagDeletedEvent.class);

        // rollout
        TYPES.put(17, RolloutUpdatedEvent.class);

        // rollout group
        TYPES.put(18, RolloutGroupCreatedEvent.class);
        TYPES.put(19, RolloutGroupUpdatedEvent.class);

        // download
        TYPES.put(20, DownloadProgressEvent.class);

        TYPES.put(21, SoftwareModuleCreatedEvent.class);
        TYPES.put(22, SoftwareModuleDeletedEvent.class);
        TYPES.put(23, SoftwareModuleUpdatedEvent.class);

        TYPES.put(24, TargetPollEvent.class);
        TYPES.put(25, RolloutDeletedEvent.class);
        TYPES.put(26, RolloutGroupDeletedEvent.class);
        TYPES.put(27, RolloutCreatedEvent.class);

        // distribution set type
        TYPES.put(28, DistributionSetTypeCreatedEvent.class);
        TYPES.put(29, DistributionSetTypeUpdatedEvent.class);
        TYPES.put(30, DistributionSetTypeDeletedEvent.class);

        // software module type
        TYPES.put(31, SoftwareModuleTypeCreatedEvent.class);
        TYPES.put(32, SoftwareModuleTypeUpdatedEvent.class);
        TYPES.put(33, SoftwareModuleTypeDeletedEvent.class);

        // target filter query
        TYPES.put(34, TargetFilterQueryCreatedEvent.class);
        TYPES.put(35, TargetFilterQueryUpdatedEvent.class);
        TYPES.put(36, TargetFilterQueryDeletedEvent.class);

        // target attributes requested flag
        TYPES.put(37, TargetAttributesRequestedEvent.class);

        // deployment event for assignments and /or cancellations
        TYPES.put(38, MultiActionAssignEvent.class);
        TYPES.put(39, MultiActionCancelEvent.class);

        // tenant configuration
        TYPES.put(40, TenantConfigurationCreatedEvent.class);
        TYPES.put(41, TenantConfigurationUpdatedEvent.class);
        TYPES.put(42, TenantConfigurationDeletedEvent.class);

        // rollout stopped due to invalidated distribution set
        TYPES.put(43, RolloutStoppedEvent.class);

        // target type
        TYPES.put(44, TargetTypeCreatedEvent.class);
        TYPES.put(45, TargetTypeUpdatedEvent.class);
        TYPES.put(46, TargetTypeDeletedEvent.class);
    }

    public Class<?> getTargetClass(final EventType type) {
        return TYPES.get(type.getValue());
    }

    /**
     * Returns a {@link org.eclipse.hawkbit.event.EventType} based on the given
     * class type.
     *
     * @param clazz
     *            the clazz type to retrieve the corresponding
     *            {@link org.eclipse.hawkbit.event.EventType} .
     * @return the corresponding {@link org.eclipse.hawkbit.event.EventType} or
     *         {@code null} if the clazz does not have a
     *         {@link org.eclipse.hawkbit.event.EventType}.
     */
    public EventType from(final Class<?> clazz) {
        final Optional<Integer> foundEventType = TYPES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(clazz)).map(Map.Entry::getKey).findAny();

        return foundEventType.map(EventType::new).orElse(null);
    }

}
