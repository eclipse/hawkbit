/**
 * Copyright (c) 2019 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.model;


import java.util.Objects;

import javax.validation.Valid;

import org.eclipse.hawkbit.repository.exception.InvalidMaintenanceScheduleException;
import org.eclipse.hawkbit.repository.model.Action.ActionType;

/**
 * A custom view on assigning a {@link DistributionSet} to a {@link Target}.
 *
 */
public class DeploymentRequest {
    private final Long distributionSetId;
    @Valid
    private final TargetWithActionType targetWithActionType;

    /**
     * Constructor that also accepts maintenance schedule parameters and checks
     * for validity of the specified maintenance schedule.
     *
     * @param controllerId
     *            for which the action is created.
     * @param distributionSetId
     *            of the distribution set that that should be assigned to the
     *            controller.
     * @param actionType
     *            specified for the action.
     * @param forceTime
     *            at what time the type soft turns into forced.
     * @param weight
     *            the priority of an {@link Action}.
     * @param maintenanceSchedule
     *            is the cron expression to be used for scheduling maintenance
     *            windows. Expression has 6 mandatory fields and 1 last optional
     *            field: "second minute hour dayofmonth month weekday year"
     * @param maintenanceWindowDuration
     *            in HH:mm:ss format specifying the duration of a maintenance
     *            window, for example 00:30:00 for 30 minutes
     * @param maintenanceWindowTimeZone
     *            is the time zone specified as +/-hh:mm offset from UTC, for
     *            example +02:00 for CET summer time and +00:00 for UTC. The
     *            start time of a maintenance window calculated based on the
     *            cron expression is relative to this time zone.
     *
     * @throws InvalidMaintenanceScheduleException
     *             if the parameters do not define a valid maintenance schedule.
     */
    public DeploymentRequest(final String controllerId, final Long distributionSetId, final ActionType actionType,
            final long forceTime, final Integer weight, final String maintenanceSchedule,
            final String maintenanceWindowDuration, final String maintenanceWindowTimeZone) {
        this.targetWithActionType = new TargetWithActionType(controllerId, actionType, forceTime, weight,
                maintenanceSchedule, maintenanceWindowDuration, maintenanceWindowTimeZone);
        this.distributionSetId = distributionSetId;
    }

    public Long getDistributionSetId() {
        return distributionSetId;
    }

    public String getControllerId() {
        return targetWithActionType.getControllerId();
    }

    public TargetWithActionType getTargetWithActionType() {
        return targetWithActionType;
    }

    @Override
    public String toString() {
        return String.format(
                "DeploymentRequest [controllerId=%s, distributionSetId=%d, actionType=%s, forceTime=%d, weight=%d, maintenanceSchedule=%s, maintenanceWindowDuration=%s, maintenanceWindowTimeZone=%s]",
                targetWithActionType.getControllerId(), getDistributionSetId(), targetWithActionType.getActionType(),
                targetWithActionType.getForceTime(), targetWithActionType.getWeight().orElse(null),
                targetWithActionType.getMaintenanceSchedule(), targetWithActionType.getMaintenanceWindowDuration(),
                targetWithActionType.getMaintenanceWindowTimeZone());
    }

    @Override
    public int hashCode() {
        return Objects.hash(distributionSetId, targetWithActionType);
    }
    
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DeploymentRequest other = (DeploymentRequest) obj;
        return Objects.equals(distributionSetId, other.distributionSetId)
                && Objects.equals(targetWithActionType, other.targetWithActionType);
    }
}
