/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.exception.CancelActionNotAllowedException;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.IncompleteDistributionSetException;
import org.eclipse.hawkbit.repository.exception.MultiAssignmentIsNotEnabledException;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.RSQLParameterSyntaxException;
import org.eclipse.hawkbit.repository.exception.RSQLParameterUnsupportedFieldException;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.ActionStatus;
import org.eclipse.hawkbit.repository.model.DeploymentRequest;
import org.eclipse.hawkbit.repository.model.DeploymentRequestBuilder;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.DistributionSetType;
import org.eclipse.hawkbit.repository.model.SoftwareModuleType;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * A DeploymentManagement service provides operations for the deployment of
 * {@link DistributionSet}s to {@link Target}s.
 *
 */
public interface DeploymentManagement {

    /**
     * Assigns {@link DistributionSet}s to {@link Target}s according to the
     * {@link DeploymentRequest}.
     *
     * @param deploymentRequests
     *            information about all target-ds-assignments that shall be made
     * 
     * @return the list of assignment results
     *
     * @throws IncompleteDistributionSetException
     *             if mandatory {@link SoftwareModuleType} are not assigned as
     *             defined by the {@link DistributionSetType}.
     *
     * @throws EntityNotFoundException
     *             if either provided {@link DistributionSet} or {@link Target}s
     *             do not exist
     * 
     * @throws AssignmentQuotaExceededException
     *             if the maximum number of targets the distribution set can be
     *             assigned to at once is exceeded
     * @throws MultiAssignmentIsNotEnabledException
     *             if the request results in multiple assignments to the same
     *             target and multiassignment is disabled
     * 
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_REPOSITORY_AND_UPDATE_TARGET)
    List<DistributionSetAssignmentResult> assignDistributionSets(@Valid @NotEmpty List<DeploymentRequest> deploymentRequests);

    /**
     * Assigns {@link DistributionSet}s to {@link Target}s according to the
     * {@link DeploymentRequest}.
     *
     * @param initiatedBy
     *            the username of the user who initiated the assignment
     * @param deploymentRequests
     *            information about all target-ds-assignments that shall be made
     * @param actionMessage
     *            an optional message for the action status
     * 
     * @return the list of assignment results
     *
     * @throws IncompleteDistributionSetException
     *             if mandatory {@link SoftwareModuleType} are not assigned as
     *             defined by the {@link DistributionSetType}.
     *
     * @throws EntityNotFoundException
     *             if either provided {@link DistributionSet} or {@link Target}s
     *             do not exist
     * 
     * @throws AssignmentQuotaExceededException
     *             if the maximum number of targets the distribution set can be
     *             assigned to at once is exceeded
     * @throws MultiAssignmentIsNotEnabledException
     *             if the request results in multiple assignments to the same
     *             target and multiassignment is disabled
     * 
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_REPOSITORY_AND_UPDATE_TARGET)
    List<DistributionSetAssignmentResult> assignDistributionSets(String initiatedBy,
            @Valid @NotEmpty List<DeploymentRequest> deploymentRequests, String actionMessage);

    /**
     * Assigns {@link DistributionSet} to {@link Target} according to the
     * {@link DeploymentRequest}.
     *
     * @param target
     *            target that the DS shall be assigned to
     * @param deploymentRequest
     *            information about target-ds-assignment that shall be made
     * @param actionMessage
     *            an optional message for the action status
     *
     * @return the assignment result
     *
     * @throws IncompleteDistributionSetException
     *             if mandatory {@link SoftwareModuleType} are not assigned as
     *             defined by the {@link DistributionSetType}.
     *
     * @throws EntityNotFoundException
     *             if either provided {@link DistributionSet} or {@link Target}s
     *             do not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_REPOSITORY_AND_UPDATE_TARGET)
    DistributionSetAssignmentResult assignDistributionSet(@Valid Target target,
            DeploymentRequest deploymentRequest, String actionMessage);

                /**
     * build a {@link DeploymentRequest} for a target distribution set
     * assignment
     * 
     * @param controllerId
     *            ID of target
     * @param distributionSetId
     *            ID of distribution set
     * @return the builder
     */
    static DeploymentRequestBuilder deploymentRequest(final String controllerId, final long distributionSetId) {
        return new DeploymentRequestBuilder(controllerId, distributionSetId);
    }

    /**
     * Registers "offline" assignments. "offline" assignment means adding a
     * completed action for a {@link DistributionSet} to a {@link Target}.
     * 
     * The handling differs to hawkBit-managed updates by means that:<br/>
     * 
     * <ol type="A">
     * <li>it ignores targets completely that are in
     * {@link TargetUpdateStatus#PENDING}.</li>
     * <li>it creates completed actions.</li>
     * <li>sets both installed and assigned DS on the target and switches the
     * status to {@link TargetUpdateStatus#IN_SYNC}.</li>
     * <li>does not send a {@link TargetAssignDistributionSetEvent}.</li>
     * </ol>
     * 
     * @param assignments
     *            target IDs with the respective distribution set ID which they
     *            are supposed to be assigned to
     * @return the assignment results
     * 
     * @throws IncompleteDistributionSetException
     *             if mandatory {@link SoftwareModuleType} are not assigned as
     *             defined by the {@link DistributionSetType}.
     *
     * @throws EntityNotFoundException
     *             if either provided {@link DistributionSet} or {@link Target}s
     *             do not exist
     * 
     * @throws AssignmentQuotaExceededException
     *             if the maximum number of targets the distribution set can be
     *             assigned to at once is exceeded
     * 
     * @throws MultiAssignmentIsNotEnabledException
     *             if the request results in multiple assignments to the same
     *             target and multiassignment is disabled
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_REPOSITORY_AND_UPDATE_TARGET)
    List<DistributionSetAssignmentResult> offlineAssignedDistributionSets(Collection<Entry<String, Long>> assignments);

    /**
     * Cancels the {@link Action} with the given ID. The method will immediately
     * add a {@link Status#CANCELED} status to the action. However, it might be
     * possible that the controller will continue to work on the cancellation.
     *
     * @param actionId
     *            to be canceled
     *
     * @return canceled {@link Action}
     * 
     * @throws CancelActionNotAllowedException
     *             in case the given action is not active or is already a cancel
     *             action
     * @throws EntityNotFoundException
     *             if action with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_UPDATE_TARGET)
    Action cancelAction(long actionId);

    /**
     * Counts all actions associated to a specific target.
     *
     * @param rsqlParam
     *            rsql query string
     * @param controllerId
     *            the target associated to the actions to count
     * @return the count value of found actions associated to the target
     *
     * @throws RSQLParameterUnsupportedFieldException
     *             if a field in the RSQL string is used but not provided by the
     *             given {@code fieldNameProvider}
     * @throws RSQLParameterSyntaxException
     *             if the RSQL syntax is wrong
     * @throws EntityNotFoundException
     *             if target with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    long countActionsByTarget(@NotNull String rsqlParam, @NotEmpty String controllerId);

    /**
     * @return the total amount of stored action status
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    long countActionStatusAll();

    /**
     * @return the total amount of stored actions
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    long countActionsAll();

    /**
     * Counts all actions associated to a specific target.
     *
     * @param controllerId
     *            the target associated to the actions to count
     * @return the count value of found actions associated to the target
     * 
     * @throws EntityNotFoundException
     *             if target with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    long countActionsByTarget(@NotEmpty String controllerId);

    /**
     * Get the {@link Action} entity for given actionId.
     *
     * @param actionId
     *            to be id of the action
     * @return the corresponding {@link Action}
     * 
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Optional<Action> findAction(long actionId);

    /**
     * Retrieves all {@link Action}s from repository.
     *
     * @param pageable
     *            pagination parameter
     * @return a paged list of {@link Action}s
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Slice<Action> findActionsAll(@NotNull Pageable pageable);

    /**
     * Retrieves all {@link Action} which assigned to a specific
     * {@link DistributionSet}.
     * 
     * @param pageable
     *            the page request parameter for paging and sorting the result
     * @param distributionSetId
     *            the distribution set which should be assigned to the actions
     *            in the result
     * @return a list of {@link Action} which are assigned to a specific
     *         {@link DistributionSet}
     * 
     * @throws EntityNotFoundException
     *             if distribution set with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Slice<Action> findActionsByDistributionSet(@NotNull Pageable pageable, long distributionSetId);

    /**
     * Retrieves all {@link Action}s assigned to a specific {@link Target} and a
     * given specification.
     *
     * @param rsqlParam
     *            rsql query string
     * @param controllerId
     *            the target which must be assigned to the actions
     * @param pageable
     *            the page request
     * @return a slice of actions assigned to the specific target and the
     *         specification
     * 
     * @throws RSQLParameterUnsupportedFieldException
     *             if a field in the RSQL string is used but not provided by the
     *             given {@code fieldNameProvider}
     * @throws RSQLParameterSyntaxException
     *             if the RSQL syntax is wrong
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Slice<Action> findActionsByTarget(@NotNull String rsqlParam, @NotEmpty String controllerId,
            @NotNull Pageable pageable);

    /**
     * Retrieves all {@link Action}s which are referring the given
     * {@link Target}.
     *
     * @param controllerId
     *            the target to find actions for
     * @param pageable
     *            the pageable request to limit, sort the actions
     * @return a slice of actions found for a specific target
     * 
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Slice<Action> findActionsByTarget(@NotEmpty String controllerId, @NotNull Pageable pageable);

    /**
     * Retrieves all the {@link ActionStatus} entries of the given
     * {@link Action} and {@link Target}.
     *
     * @param pageReq
     *            pagination parameter
     * @param actionId
     *            to be filtered on
     * @return the corresponding {@link Page} of {@link ActionStatus}
     * 
     * @throws EntityNotFoundException
     *             if action with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<ActionStatus> findActionStatusByAction(@NotNull Pageable pageReq, long actionId);

    /**
     * Retrieves all messages for an {@link ActionStatus}.
     *
     *
     * @param pageable
     *            the page request parameter for paging and sorting the result
     * @param actionStatusId
     *            the id of {@link ActionStatus} to retrieve the messages from
     * @return a page of messages by a specific {@link ActionStatus} id
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<String> findMessagesByActionStatusId(@NotNull Pageable pageable, long actionStatusId);

    /**
     * Get the {@link Action} entity for given actionId with all lazy attributes
     * (i.e. distributionSet, target, target.assignedDs).
     *
     * @param actionId
     *            to be id of the action
     * @return the corresponding {@link Action}
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Optional<Action> findActionWithDetails(long actionId);

    /**
     * Retrieves all active {@link Action}s of a specific target.
     * 
     * @param pageable
     *            the page request parameter for paging and sorting the result
     * @param controllerId
     *            the target associated with the actions
     * @return a list of actions associated with the given target
     * 
     * @throws EntityNotFoundException
     *             if target with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<Action> findActiveActionsByTarget(@NotNull Pageable pageable, @NotEmpty String controllerId);

    /**
     * Retrieves all inactive {@link Action}s of a specific target.
     *
     * @param pageable
     *            the page request parameter for paging and sorting the result
     * @param controllerId
     *            the target associated with the actions
     * @return a list of actions associated with the given target
     * 
     * @throws EntityNotFoundException
     *             if target with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<Action> findInActiveActionsByTarget(@NotNull Pageable pageable, @NotEmpty String controllerId);

    /**
     * Retrieves active {@link Action}s with highest weight that are assigned to
     * a {@link Target}.
     * 
     * @param controllerId
     *            identifies the target to retrieve the action from
     * @param maxActionCount
     *            max size of returned list
     * @return the action
     * 
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    List<Action> findActiveActionsWithHighestWeight(@NotEmpty String controllerId, int maxActionCount);

    /**
     * Get weight of an Action. Returns the default value if the weight is null
     * according to the properties.
     * 
     * @param action
     *            to extract the weight from
     * @return weight of the action
     */
    int getWeightConsideringDefault(final Action action);

    /**
     * Force cancels given {@link Action} for given {@link Target}. Force
     * canceling means that the action is marked as canceled on the SP server
     * and a cancel request is sent to the target. But however it's not tracked,
     * if the targets handles the cancel request or not.
     *
     * @param actionId
     *            to be canceled
     *
     * @return quite {@link Action}
     * 
     * @throws CancelActionNotAllowedException
     *             in case the given action is not active
     * 
     * @throws EntityNotFoundException
     *             if action with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_UPDATE_TARGET)
    Action forceQuitAction(long actionId);

    /**
     * Updates a {@link Action} and forces the {@link Action} if it's not
     * already forced.
     *
     * @param actionId
     *            the ID of the action
     * @return the updated or the found {@link Action}
     * 
     * @throws EntityNotFoundException
     *             if action with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_UPDATE_TARGET)
    Action forceTargetAction(long actionId);

    /**
     * Sets the status of inactive scheduled {@link Action}s for the specified
     * {@link Target}s to {@link Status#CANCELED}
     *
     * @param targetIds
     *            ids of the {@link Target}s the actions belong to
     * 
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_UPDATE_TARGET)
    void cancelInactiveScheduledActionsForTargets(List<Long> targetIds);

    /**
     * Starts all scheduled actions of an RolloutGroup parent.
     *
     * @param rolloutId
     *            the rollout the actions belong to
     * @param rolloutGroupParentId
     *            the parent rollout group the actions should reference. null
     *            references the first group
     * @return the amount of started actions
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    long startScheduledActionsByRolloutGroupParent(long rolloutId, long distributionSetId, Long rolloutGroupParentId);

    /**
     * All {@link ActionStatus} entries in the repository.
     *
     * @param pageable
     *            the pagination parameter
     * @return {@link Page} of {@link ActionStatus} entries
     */
    @PreAuthorize(SpringEvalExpressions.HAS_AUTH_READ_TARGET)
    Page<ActionStatus> findActionStatusAll(@NotNull Pageable pageable);

    /**
     * Returns {@link DistributionSet} that is assigned to given {@link Target}.
     * 
     * @param controllerId
     *            of target
     * @return assigned {@link DistributionSet}
     * 
     * @throws EntityNotFoundException
     *             if target with given ID does not exist
     */
    Optional<DistributionSet> getAssignedDistributionSet(@NotEmpty String controllerId);

    /**
     * Returns {@link DistributionSet} that is installed on given
     * {@link Target}.
     * 
     * @param controllerId
     *            of target
     * @return installed {@link DistributionSet}
     * 
     * @throws EntityNotFoundException
     *             if target with given ID does not exist
     */
    Optional<DistributionSet> getInstalledDistributionSet(@NotEmpty String controllerId);

    /**
     * Deletes actions which match one of the given action status and which have
     * not been modified since the given (absolute) time-stamp.
     * 
     * @param status
     *            Set of action status.
     * @param lastModified
     *            A time-stamp in milliseconds.
     * 
     * @return The number of action entries that were deleted.
     */
    @PreAuthorize(SpringEvalExpressions.IS_SYSTEM_CODE)
    int deleteActionsByStatusAndLastModifiedBefore(@NotNull Set<Action.Status> status, long lastModified);

}
