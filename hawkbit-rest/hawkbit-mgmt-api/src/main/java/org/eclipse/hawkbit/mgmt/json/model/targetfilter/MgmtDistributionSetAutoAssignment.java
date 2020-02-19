/**
 * Copyright (c) 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.mgmt.json.model.targetfilter;

import org.eclipse.hawkbit.mgmt.json.model.MgmtId;
import org.eclipse.hawkbit.mgmt.json.model.distributionset.MgmtActionType;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request Body of DistributionSet Id, weight, and Action Type for target filter
 * auto assignment operation.
 */
public class MgmtDistributionSetAutoAssignment extends MgmtId {

    @JsonProperty(required = false)
    private MgmtActionType type;

    @JsonProperty(required = false)
    private Integer weight;

    public MgmtActionType getType() {
        return type;
    }

    public void setType(final MgmtActionType type) {
        this.type = type;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(final Integer weight) {
        this.weight = weight;
    }
}
