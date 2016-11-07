/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.builder;

import org.eclipse.hawkbit.repository.model.BaseEntity;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;

/**
 * Builder to create a new {@link TargetFilterQuery} entry. Defines all fields
 * that can be set at creation time. Other fields are set by the repository
 * automatically, e.g. {@link BaseEntity#getCreatedAt()}.
 *
 */
public interface TargetFilterQueryCreate {
    /**
     * @param name
     *            of {@link TargetFilterQuery#getName()}
     * @return updated builder instance
     */
    TargetFilterQueryCreate name(String name);

    /**
     * @param query
     *            of {@link TargetFilterQuery#getQuery()}
     * @return updated builder instance
     */
    TargetFilterQueryCreate query(String query);

    /**
     * @return peek on current state of {@link TargetFilterQuery} in the builder
     */
    TargetFilterQuery build();
}
