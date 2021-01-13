/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions;
import org.eclipse.hawkbit.repository.builder.ActionStatusCreate;
import org.eclipse.hawkbit.repository.exception.CancelActionNotAllowedException;
import org.eclipse.hawkbit.repository.exception.EntityAlreadyExistsException;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.InvalidTargetAttributeException;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.ActionStatus;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.SoftwareModuleMetadata;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service layer for all operations of the DDI API (with access permissions only
 * for the controller).
 *
 */
public interface ControllerManagement {

    /**
     * Adds an {@link ActionStatus} for a cancel {@link Action} including
     * potential state changes for the target and the {@link Action} itself.
     *
     * @param create
     *            to be added
     * @return the updated {@link Action}
     *
     * @throws EntityAlreadyExistsException
     *             if a given entity already exists
     *
     * @throws AssignmentQuotaExceededException
     *             if more than the allowed number of status entries or messages
     *             per entry are inserted
     * @throws EntityNotFoundException
     *             if given action does not exist
     * @throws ConstraintViolationException
     *             if fields are not filled as specified. Check
     *             {@link ActionStatusCreate} for field constraints.
     *
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Action addCancelActionStatus(@NotNull @Valid ActionStatusCreate create);

    /**
     * Retrieves assigned {@link SoftwareModule} of a target.
     *
     * @param moduleId
     *            of the {@link SoftwareModule}
     * @return {@link SoftwareModule} identified by ID
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Optional<SoftwareModule> getSoftwareModule(long moduleId);

    /**
     * Retrieves {@link SoftwareModuleMetadata} where
     * {@link SoftwareModuleMetadata#isTargetVisible()}.
     *
     * @param moduleId
     *            of the {@link SoftwareModule}
     * @return list of {@link SoftwareModuleMetadata} with maximum size of
     *         {@link RepositoryConstants#MAX_META_DATA_COUNT}
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Map<Long, List<SoftwareModuleMetadata>> findTargetVisibleMetaDataBySoftwareModuleId(
            @NotNull Collection<Long> moduleId);

    /**
     * Simple addition of a new {@link ActionStatus} entry to the
     * {@link Action}. No state changes.
     *
     * @param create
     *            to add to the action
     *
     * @return created {@link ActionStatus} entity
     *
     * @throws AssignmentQuotaExceededException
     *             if more than the allowed number of status entries or messages
     *             per entry are inserted
     * @throws EntityNotFoundException
     *             if given action does not exist
     * @throws ConstraintViolationException
     *             if fields are not filled as specified. Check
     *             {@link ActionStatusCreate} for field constraints.
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    ActionStatus addInformationalActionStatus(@NotNull @Valid ActionStatusCreate create);

    /**
     * Adds an {@link ActionStatus} entry for an update {@link Action} including
     * potential state changes for the target and the {@link Action} itself.
     *
     * @param create
     *            to be added
     * @return the updated {@link Action}
     *
     * @throws EntityAlreadyExistsException
     *             if a given entity already exists
     * @throws AssignmentQuotaExceededException
     *             if more than the allowed number of status entries or messages
     *             per entry are inserted
     * @throws EntityNotFoundException
     *             if action status not exist
     * @throws ConstraintViolationException
     *             if fields are not filled as specified. Check
     *             {@link ActionStatusCreate} for field constraints.
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Action addUpdateActionStatus(@NotNull @Valid ActionStatusCreate create);

    /**
     * Retrieves active {@link Action} with highest priority that is assigned to
     * a {@link Target}.
     * 
     * For performance reasons this method does not throw
     * {@link EntityNotFoundException} in case target with given controllerId
     * does not exist but will return an {@link Optional#empty()} instead.
     *
     * @param controllerId
     *            identifies the target to retrieve the action from
     * @return the action
     * 
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Optional<Action> findActiveActionWithHighestWeight(@NotEmpty String controllerId);

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
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
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
     * Get the {@link Action} entity for given actionId with all lazy
     * attributes.
     *
     * @param actionId
     *            to be id of the action
     * @return the corresponding {@link Action}
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Optional<Action> findActionWithDetails(long actionId);

    /**
     * Retrieves all the {@link ActionStatus} entries of the given
     * {@link Action}.
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
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Page<ActionStatus> findActionStatusByAction(@NotNull Pageable pageReq, long actionId);

    /**
     * Register new target in the repository (plug-and-play) and in case it
     * already exists updates {@link Target#getAddress()} and
     * {@link Target#getLastTargetQuery()} and switches if
     * {@link TargetUpdateStatus#UNKNOWN} to
     * {@link TargetUpdateStatus#REGISTERED}.
     *
     * @param controllerId
     *            reference
     * @param address
     *            the client IP address of the target, might be {@code null}
     * @return target reference
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Target findOrRegisterTargetIfItDoesNotExist(@NotEmpty String controllerId, @NotNull URI address);

    /**
     * Register new target in the repository (plug-and-play) and in case it
     * already exists updates {@link Target#getAddress()} and
     * {@link Target#getLastTargetQuery()} and {@link Target#getName()} and
     * switches if {@link TargetUpdateStatus#UNKNOWN} to
     * {@link TargetUpdateStatus#REGISTERED}.
     *
     * @param controllerId
     *            reference
     * @param address
     *            the client IP address of the target, might be {@code null}
     * @param name
     *            the name of the target
     * @return target reference
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Target findOrRegisterTargetIfItDoesNotExist(@NotEmpty String controllerId, @NotNull URI address, String name);

    /**
     * Retrieves last {@link Action} for a download of an artifact of given
     * module and target if exists and is not canceled.
     *
     * @param controllerId
     *            to look for
     * @param moduleId
     *            of the the {@link SoftwareModule} that should be assigned to
     *            the target
     * @return last {@link Action} for given combination
     *
     * @throws EntityNotFoundException
     *             if target with given ID does not exist
     *
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Optional<Action> getActionForDownloadByTargetAndSoftwareModule(@NotEmpty String controllerId, long moduleId);

    /**
     * Returns configured polling interval at which the controller polls hawkBit
     * server.
     *
     * @return current {@link TenantConfigurationKey#POLLING_TIME_INTERVAL}.
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    String getPollingTime();

    /**
     * Returns the configured minimum polling interval.
     *
     * @return current {@link TenantConfigurationKey#MIN_POLLING_TIME_INTERVAL}.
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    String getMinPollingTime();

    /**
     * Returns the count to be used for reducing polling interval while calling
     * {@link ControllerManagement#getPollingTimeForAction(long)}.
     *
     * @return configured value of
     *         {@link TenantConfigurationKey#MAINTENANCE_WINDOW_POLL_COUNT}.
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    int getMaintenanceWindowPollCount();

    /**
     * Returns polling time based on the maintenance window for an action.
     * Server will reduce the polling interval as the start time for maintenance
     * window approaches, so that at least these many attempts are made between
     * current polling until start of maintenance window. Poll time keeps
     * reducing with MinPollingTime as lower limit
     * {@link TenantConfigurationKey#MIN_POLLING_TIME_INTERVAL}. After the start
     * of maintenance window, it resets to default
     * {@link TenantConfigurationKey#POLLING_TIME_INTERVAL}.
     *
     * @param actionId
     *            id the {@link Action} for which polling time is calculated
     *            based on it having maintenance window or not
     *
     * @return current {@link TenantConfigurationKey#POLLING_TIME_INTERVAL}.
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    String getPollingTimeForAction(long actionId);

    /**
     * Checks if a given target has currently or has even been assigned to the
     * given artifact through the action history list. This can e.g. indicate if
     * a target is allowed to download a given artifact because it has currently
     * assigned or had ever been assigned to the target and so it's visible to a
     * specific target e.g. for downloading.
     *
     * @param controllerId
     *            the ID of the target to check
     * @param sha1Hash
     *            of the artifact to verify if the given target had even been
     *            assigned to
     * @return {@code true} if the given target has currently or had ever a
     *         relation to the given artifact through the action history,
     *         otherwise {@code false}
     *
     * @throws EntityNotFoundException
     *             if target with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    boolean hasTargetArtifactAssigned(@NotEmpty String controllerId, @NotEmpty String sha1Hash);

    /**
     * Checks if a given target has currently or has even been assigned to the
     * given artifact through the action history list. This can e.g. indicate if
     * a target is allowed to download a given artifact because it has currently
     * assigned or had ever been assigned to the target and so it's visible to a
     * specific target e.g. for downloading.
     *
     * @param targetId
     *            the ID of the target to check
     * @param sha1Hash
     *            of the artifact to verify if the given target had even been
     *            assigned to
     * @return {@code true} if the given target has currently or had ever a
     *         relation to the given artifact through the action history,
     *         otherwise {@code false}
     *
     * @throws EntityNotFoundException
     *             if target with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    boolean hasTargetArtifactAssigned(long targetId, @NotEmpty String sha1Hash);

    /**
     * Registers retrieved status for given {@link Target} and {@link Action} if
     * it does not exist yet.
     *
     * @param actionId
     *            to the handle status for
     * @param message
     *            for the status
     * @return the update action in case the status has been changed to
     *         {@link Status#RETRIEVED}
     *
     * @throws EntityNotFoundException
     *             if action with given ID does not exist
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Action registerRetrieved(long actionId, String message);

    /**
     * Updates attributes of the controller according to the given
     * {@link UpdateMode}.
     *
     * @param controllerId
     *            to update
     * @param attributes
     *            to insert
     * @param mode
     *            the update mode or <code>null</code>
     *
     * @return updated {@link Target}
     *
     * @throws EntityNotFoundException
     *             if target that has to be updated could not be found
     * @throws AssignmentQuotaExceededException
     *             if maximum number of attributes per target is exceeded
     * @throws InvalidTargetAttributeException
     *             if attributes violate constraints
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Target updateControllerAttributes(@NotEmpty String controllerId, @NotNull Map<String, String> attributes,
            UpdateMode mode);

    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    void triggerDistributionSetAssignmentCheck(String controllerId);

    /**
     * Finds {@link Target} based on given controller ID returns found Target
     * without details, i.e. NO {@link Target#getTags()} and
     * {@link Target#getActions()} possible.
     *
     * @param controllerId
     *            to look for.
     * @return {@link Target} or {@code null} if it does not exist
     * @see Target#getControllerId()
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER + SpringEvalExpressions.HAS_AUTH_OR
            + SpringEvalExpressions.IS_SYSTEM_CODE)
    Optional<Target> getByControllerId(@NotEmpty String controllerId);

    /**
     * Finds {@link Target} based on given ID returns found Target without
     * details, i.e. NO {@link Target#getTags()} and {@link Target#getActions()}
     * possible.
     *
     * @param targetId
     *            to look for.
     * @return {@link Target} or {@code null} if it does not exist
     * @see Target#getId()
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER + SpringEvalExpressions.HAS_AUTH_OR
            + SpringEvalExpressions.IS_SYSTEM_CODE)
    Optional<Target> get(long targetId);

    /**
     * Retrieves the specified number of messages from action history of the
     * given {@link Action} based on messageCount. Regardless of the value of
     * messageCount, in order to restrict resource utilisation by controllers,
     * maximum number of messages that are retrieved from database is limited by
     * {@link RepositoryConstants#MAX_ACTION_HISTORY_MSG_COUNT}. messageCount
     * less then zero, retrieves the maximum allowed number of action status
     * messages from history; messageCount equal zero, does not retrieve any
     * message; and messageCount larger then zero, retrieves the specified
     * number of messages, limited by maximum allowed number. A controller sends
     * the feedback for an {@link ActionStatus} as a list of messages; while
     * returning the messages, even though the messages from multiple
     * {@link ActionStatus} are retrieved in descending order by the reported
     * time ({@link ActionStatus#getOccurredAt()}), i.e. latest ActionStatus
     * first, the sub-ordering of messages from within single
     * {@link ActionStatus} is unspecified.
     *
     * @param actionId
     *            to be filtered on
     * @param messageCount
     *            is the number of messages to return from history
     *
     * @return action history.
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    List<String> getActionHistoryMessages(long actionId, int messageCount);

    /**
     * Cancels given {@link Action} for this {@link Target}. However, it might
     * be possible that the controller will continue to work on the
     * cancellation. The controller needs to acknowledge or reject the
     * cancellation using {@link DdiRootController#postCancelActionFeedback}.
     *
     * @param actionId
     *            to be canceled
     *
     * @return canceled {@link Action}
     *
     * @throws CancelActionNotAllowedException
     *             in case the given action is not active or is already canceled
     * @throws EntityNotFoundException
     *             if action with given actionId does not exist.
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Action cancelAction(long actionId);

    /**
     * Updates given {@link Action} with its external id.
     *
     * @param actionId
     *            to be updated
     * @param externalRef
     *            of the action
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    void updateActionExternalRef(long actionId, @NotEmpty String externalRef);

    /**
     * Retrieves list of {@link Action}s which matches the provided
     * externalRefs.
     * 
     * @param externalRefs
     *            for which the actions need to be fetched.
     * @return list of {@link Action}s matching the externalRefs.
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    List<Action> getActiveActionsByExternalRef(@NotNull List<String> externalRefs);

    /**
     * Retrieves an {@link Action} using {@link Action#getExternalRef()}
     * 
     * @param externalRef
     *            of the action. See {@link Action#getExternalRef()}
     * @return {@link Action} or {@code null} if it does not exist
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    Optional<Action> getActionByExternalRef(@NotEmpty String externalRef);

    /**
     * Delete a single target.
     *
     * @param controllerId
     *            of the target to delete
     */
    @PreAuthorize(SpringEvalExpressions.IS_CONTROLLER)
    void deleteExistingTarget(@NotEmpty String controllerId);
}
