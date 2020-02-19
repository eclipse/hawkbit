/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.model;

import java.util.Objects;
import java.util.Optional;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import org.eclipse.hawkbit.repository.exception.InvalidMaintenanceScheduleException;
import org.eclipse.hawkbit.repository.model.Action.ActionType;

/**
 * A custom view on {@link Target} with {@link ActionType}.
 *
 */
public class TargetWithActionType {
    private final String controllerId;
    private final ActionType actionType;
    private final long forceTime;
    @Min(Action.WEIGHT_MIN)
    @Max(Action.WEIGHT_MAX)
    private final Integer weight;
    private String maintenanceSchedule;
    private String maintenanceWindowDuration;
    private String maintenanceWindowTimeZone;

    /**
     * Constructor that uses {@link ActionType#FORCED}
     * 
     * @param controllerId
     *            ID if the controller
     */
    public TargetWithActionType(final String controllerId) {
        this(controllerId, ActionType.FORCED, 0, null);
    }

    /**
     * Constructor that leaves the maintenance info empty
     *
     * @param controllerId
     *            for which the action is created.
     * @param actionType
     *            specified for the action.
     * @param forceTime
     *            after that point in time the action is exposed as forced in
     *            case the type is {@link ActionType#TIMEFORCED}
     * @param weight
     *            the priority of an {@link Action}
     */
    public TargetWithActionType(final String controllerId, final ActionType actionType, final long forceTime,
            final Integer weight) {
        this.controllerId = controllerId;
        this.actionType = actionType != null ? actionType : ActionType.FORCED;
        this.forceTime = forceTime;
        this.weight = weight;
    }

    /**
     * Constructor that also accepts maintenance schedule parameters and checks
     * for validity of the specified maintenance schedule.
     *
     * @param controllerId
     *            for which the action is created.
     * @param actionType
     *            specified for the action.
     * @param forceTime
     *            after that point in time the action is exposed as forced in
     *            case the type is {@link ActionType#TIMEFORCED}
     * @param weight
     *            the priority of an {@link Action}
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
    public TargetWithActionType(final String controllerId, final ActionType actionType, final long forceTime,
            final Integer weight, final String maintenanceSchedule, final String maintenanceWindowDuration,
            final String maintenanceWindowTimeZone) {
        this(controllerId, actionType, forceTime, weight);

        this.maintenanceSchedule = maintenanceSchedule;
        this.maintenanceWindowDuration = maintenanceWindowDuration;
        this.maintenanceWindowTimeZone = maintenanceWindowTimeZone;
    }

    public ActionType getActionType() {
        if (actionType != null) {
            return actionType;
        }
        // default value
        return ActionType.FORCED;
    }

    public long getForceTime() {
        if (actionType == ActionType.TIMEFORCED) {
            return forceTime;
        }
        return RepositoryModelConstants.NO_FORCE_TIME;
    }

    public Optional<Integer> getWeight() {
        return Optional.ofNullable(weight);
    }

    public String getControllerId() {
        return controllerId;
    }

    /**
     * Returns the maintenance schedule for the {@link Action}.
     *
     * @return cron expression as {@link String}.
     */
    public String getMaintenanceSchedule() {
        return this.maintenanceSchedule;
    }

    /**
     * Returns the duration of maintenance window for the {@link Action}.
     *
     * @return duration in HH:mm:ss format as {@link String}.
     */
    public String getMaintenanceWindowDuration() {
        return maintenanceWindowDuration;
    }

    /**
     * Returns the timezone of maintenance window for the {@link Action}.
     *
     * @return the timezone offset from UTC in +/-hh:mm as {@link String}.
     */
    public String getMaintenanceWindowTimeZone() {
        return maintenanceWindowTimeZone;
    }

    @Override
    public String toString() {
        return "TargetWithActionType [controllerId=" + controllerId + ", actionType=" + getActionType() + ", forceTime="
                + getForceTime() + ", weight=" + getWeight().orElse(null) + ", maintenanceSchedule=" + getMaintenanceSchedule()
                + ", maintenanceWindowDuration=" + getMaintenanceWindowDuration() + ", maintenanceWindowTimeZone="
                + getMaintenanceWindowTimeZone() + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(actionType, controllerId, forceTime, weight, maintenanceSchedule, maintenanceWindowDuration,
                maintenanceWindowTimeZone);
    }

    @SuppressWarnings("squid:S1067")
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
        final TargetWithActionType other = (TargetWithActionType) obj;
        return Objects.equals(actionType, other.actionType) && Objects.equals(controllerId, other.controllerId)
                && Objects.equals(forceTime, other.forceTime) && Objects.equals(weight, other.weight)
                && Objects.equals(maintenanceSchedule, other.maintenanceSchedule)
                && Objects.equals(maintenanceWindowDuration, other.maintenanceWindowDuration)
                && Objects.equals(maintenanceWindowTimeZone, other.maintenanceWindowTimeZone);
    }
}
