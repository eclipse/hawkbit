/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository.model;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus.Status;

/**
 * Software update operations in large scale IoT scenarios with hundred of
 * thousands of devices require special handling.
 *
 * That includes secure handling of large volumes of devices at rollout creation
 * time. Monitoring of the rollout progress. Emergency rollout shutdown in case
 * of problems on to many devices and reporting capabilities for a complete
 * understanding of the rollout progress at each point in time.
 *
 */
public interface Rollout extends NamedEntity {

    /**
     * Maximum length of author name.
     */
    int APPROVED_BY_MAX_SIZE = 64;

    /**
     * Maximum length on comment regarding approval decision.
     */
    int APPROVAL_REMARK_MAX_SIZE = 255;

    /**
     * @return <code>true</code> if the rollout is deleted and only kept for history
     *         purposes.
     */
    boolean isDeleted();

    /**
     * @return {@link DistributionSet} that is rolled out
     */
    DistributionSet getDistributionSet();

    /**
     * @return rsql query that identifies the targets that are part of this rollout.
     */
    String getTargetFilterQuery();

    /**
     * @return status of the rollout
     */
    RolloutStatus getStatus();

    /**
     * @return {@link ActionType} of the rollout.
     */
    ActionType getActionType();

    /**
     * @return time in {@link TimeUnit#MILLISECONDS} after which {@link #isForced()}
     *         switches to <code>true</code> in case of
     *         {@link ActionType#TIMEFORCED}.
     */
    long getForcedTime();

    /**
     * @return Timestamp when the rollout should be started automatically. Can be
     *         null.
     */
    Long getStartAt();

    /**
     * @return number of {@link Target}s in this rollout.
     */
    long getTotalTargets();

    /**
     * @return number of {@link RolloutGroup}s.
     */
    int getRolloutGroupsCreated();

    /**
     * @return all states with the respective target count in that {@link Status}.
     */
    TotalTargetCountStatus getTotalTargetCountStatus();

    /**
     * @return user that approved or denied the {@link Rollout}.
     */
    String getApprovalDecidedBy();

    /**
     * @return additional note on approval/denial decision.
     */
    String getApprovalRemark();

    /**
     * @return the priority of {@link Rollout}.
     */
    Optional<Integer> getWeight();

    /**
     * @return the current retry count of a rollout. Could be null in case of no retries.
     */
    Integer getRetryCount();

    /**
     *
     * State machine for rollout.
     *
     */
    enum RolloutStatus {

        /**
         * Rollouts is being created.
         */
        CREATING,

        /**
         * Rollout needs to be approved.
         */
        WAITING_FOR_APPROVAL,

        /**
         * Rollout approval is denied. Can not be started.
         */
        APPROVAL_DENIED,

        /**
         * Rollout is ready to start.
         */
        READY,

        /**
         * Rollout is paused.
         */
        PAUSED,

        /**
         * Rollout is starting.
         */
        STARTING,

        /**
         * Rollout is stopped.
         */
        STOPPED,

        /**
         * Rollout is going to be stopped.
         */
        STOPPING,

        /**
         * Rollout is running.
         */
        RUNNING,

        /**
         * Rollout is finished.
         */
        FINISHED,

        /**
         * Rollout is under deletion.
         */
        DELETING,

        /**
         * Rollout has been deleted. This state is only set in case of a soft-deletion
         * of the rollout which keeps references, in case of an hard-deletion of a
         * rollout the rollout-entry itself is deleted.
         */
        DELETED,

        /**
         * Rollout could not be created due to errors, might be a database problem
         * during asynchronous creating.
         */
        ERROR_CREATING,

        /**
         * Rollout could not be started due to errors, might be database problem during
         * asynchronous starting.
         */
        ERROR_STARTING,

        /**
         * Rollout could not be deleted due to errors, might be database problem.
         */
        ERROR_DELETING
    }

    /**
     * Enum that holds all possible approval workflow decisions.
     */
    enum ApprovalDecision {
        /**
         * Representing an granted approval for a rollout.
         */
        APPROVED,
        /**
         * Representing a rejected rollout.
         */
        DENIED
    }
}
