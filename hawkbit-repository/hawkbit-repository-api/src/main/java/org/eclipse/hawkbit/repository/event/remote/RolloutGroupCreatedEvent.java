/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.event.remote;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event definition which is been published in case a rollout group has been
 * created for a specific rollout.
 *
 */
public class RolloutGroupCreatedEvent extends TenantAwareDistributedEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty(required = true)
    private final Long rolloutId;

    @JsonProperty(required = true)
    private final Long rolloutGroupId;

    /**
     * Creating a new rollout group created event for a specific rollout.
     * 
     * @param tenant
     *            the tenant of this event
     * @param rolloutId
     *            the ID of the rollout has been created
     * @param rolloutGroupId
     *            the ID of the rollout the group has been created
     * @param applicationId
     *            the applicationId
     */
    @JsonCreator
    public RolloutGroupCreatedEvent(@JsonProperty("tenant") final String tenant,
            @JsonProperty("rolloutId") final Long rolloutId, @JsonProperty("rolloutGroupId") final Long rolloutGroupId,
            @JsonProperty("originService") final String applicationId) {
        super(rolloutId, tenant, applicationId);
        this.rolloutId = rolloutId;
        this.rolloutGroupId = rolloutGroupId;

    }

    public Long getRolloutId() {
        return rolloutId;
    }

    public Long getRolloutGroupId() {
        return rolloutGroupId;
    }

}
