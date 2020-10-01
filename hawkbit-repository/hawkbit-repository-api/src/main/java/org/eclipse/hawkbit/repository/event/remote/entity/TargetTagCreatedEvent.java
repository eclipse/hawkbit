/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.event.remote.entity;

import org.eclipse.hawkbit.repository.event.entity.EntityCreatedEvent;
import org.eclipse.hawkbit.repository.model.TargetTag;

/**
 * Defines the remote event for the creation of a new {@link TargetTag}.
 *
 */
public class TargetTagCreatedEvent extends RemoteEntityEvent<TargetTag> implements EntityCreatedEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public TargetTagCreatedEvent() {
        // for serialization libs like jackson
    }

    /**
     * Constructor.
     *
     * @param tag
     *            the tag which is deleted
     * @param applicationId
     *            the origin application id
     */
    public TargetTagCreatedEvent(final TargetTag tag, final String applicationId) {
        super(tag, applicationId);
    }

    /**
     * Constructor for json serialization.
     *
     * @param tenant
     *            the tenant
     * @param entityId
     *            the entity id
     * @param entityClass
     *            the entity class
     * @param applicationId
     *            the origin application id
     */
    public TargetTagCreatedEvent(final String tenant, final Long entityId, final String entityClass,
            final String applicationId) {
        super(entityId, tenant, entityClass, applicationId);
    }

}
