/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ddi.rest.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

import org.eclipse.hawkbit.api.ArtifactUrlHandler;
import org.eclipse.hawkbit.artifact.repository.model.AbstractDbArtifact;
import org.eclipse.hawkbit.ddi.json.model.DdiActionFeedback;
import org.eclipse.hawkbit.ddi.json.model.DdiActionHistory;
import org.eclipse.hawkbit.ddi.json.model.DdiArtifact;
import org.eclipse.hawkbit.ddi.json.model.DdiCancel;
import org.eclipse.hawkbit.ddi.json.model.DdiCancelActionToStop;
import org.eclipse.hawkbit.ddi.json.model.DdiChunk;
import org.eclipse.hawkbit.ddi.json.model.DdiConfigData;
import org.eclipse.hawkbit.ddi.json.model.DdiControllerBase;
import org.eclipse.hawkbit.ddi.json.model.DdiDeployment;
import org.eclipse.hawkbit.ddi.json.model.DdiDeployment.DdiMaintenanceWindowStatus;
import org.eclipse.hawkbit.ddi.json.model.DdiDeployment.HandlingType;
import org.eclipse.hawkbit.ddi.json.model.DdiDeploymentBase;
import org.eclipse.hawkbit.ddi.json.model.DdiResult.FinalResult;
import org.eclipse.hawkbit.ddi.json.model.DdiUpdateMode;
import org.eclipse.hawkbit.ddi.rest.api.DdiRestConstants;
import org.eclipse.hawkbit.ddi.rest.api.DdiRootControllerRestApi;
import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.RepositoryConstants;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.UpdateMode;
import org.eclipse.hawkbit.repository.builder.ActionStatusCreate;
import org.eclipse.hawkbit.repository.event.remote.DownloadProgressEvent;
import org.eclipse.hawkbit.repository.exception.ArtifactBinaryNotFoundException;
import org.eclipse.hawkbit.repository.exception.CancelActionNotAllowedException;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.SoftwareModuleNotAssignedToTargetException;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.ActionStatus;
import org.eclipse.hawkbit.repository.model.Artifact;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.rest.util.FileStreamingUtil;
import org.eclipse.hawkbit.rest.util.HttpUtil;
import org.eclipse.hawkbit.rest.util.RequestResponseContextHolder;
import org.eclipse.hawkbit.security.HawkbitSecurityProperties;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.util.IpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * The {@link DdiRootController} of the hawkBit server DDI API that is queried
 * by the hawkBit controller in order to pull {@link Action}s that have to be
 * fulfilled and report status updates concerning the {@link Action} processing.
 *
 * Transactional (read-write) as all queries at least update the last poll time.
 */
@RestController
@Scope(value = WebApplicationContext.SCOPE_REQUEST)
public class DdiRootController implements DdiRootControllerRestApi {

    private static final Logger LOG = LoggerFactory.getLogger(DdiRootController.class);
    private static final String GIVEN_ACTION_IS_NOT_ASSIGNED_TO_GIVEN_TARGET = "given action ({}) is not assigned to given target ({}).";

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private ServiceMatcher serviceMatcher;

    @Autowired
    private BusProperties bus;

    @Autowired
    private ControllerManagement controllerManagement;

    @Autowired
    private ArtifactManagement artifactManagement;

    @Autowired
    private HawkbitSecurityProperties securityProperties;

    @Autowired
    private TenantAware tenantAware;

    @Autowired
    private SystemManagement systemManagement;

    @Autowired
    private ArtifactUrlHandler artifactUrlHandler;

    @Autowired
    private RequestResponseContextHolder requestResponseContextHolder;

    @Autowired
    private EntityFactory entityFactory;

    @Override
    public ResponseEntity<List<DdiArtifact>> getSoftwareModulesArtifacts(@PathVariable("tenant") final String tenant,
            @PathVariable("controllerId") final String controllerId,
            @PathVariable("softwareModuleId") final Long softwareModuleId) {
        LOG.debug("getSoftwareModulesArtifacts({})", controllerId);

        final Target target = controllerManagement.getByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));

        final SoftwareModule softwareModule = controllerManagement.getSoftwareModule(softwareModuleId)
                .orElseThrow(() -> new EntityNotFoundException(SoftwareModule.class, softwareModuleId));

        return new ResponseEntity<>(
                DataConversionHelper.createArtifacts(target, softwareModule, artifactUrlHandler, systemManagement,
                        new ServletServerHttpRequest(requestResponseContextHolder.getHttpServletRequest())),
                HttpStatus.OK);
    }

    @Override
    public ResponseEntity<DdiControllerBase> getControllerBase(@PathVariable("tenant") final String tenant,
            @PathVariable("controllerId") final String controllerId) {
        LOG.debug("getControllerBase({})", controllerId);

        final Target target = controllerManagement.findOrRegisterTargetIfItDoesNotExist(controllerId, IpUtil
                .getClientIpFromRequest(requestResponseContextHolder.getHttpServletRequest(), securityProperties));
        final Action action = controllerManagement.findActiveActionWithHighestWeight(controllerId).orElse(null);

        checkAndCancelExpiredAction(action);

        return new ResponseEntity<>(
                DataConversionHelper.fromTarget(target, action,
                        action == null ? controllerManagement.getPollingTime()
                                : controllerManagement.getPollingTimeForAction(action.getId()),
                        tenantAware),
                HttpStatus.OK);
    }

    @Override
    public ResponseEntity<InputStream> downloadArtifact(@PathVariable("tenant") final String tenant,
            @PathVariable("controllerId") final String controllerId,
            @PathVariable("softwareModuleId") final Long softwareModuleId,
            @PathVariable("fileName") final String fileName) {
        final ResponseEntity<InputStream> result;

        final Target target = controllerManagement.getByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));
        final SoftwareModule module = controllerManagement.getSoftwareModule(softwareModuleId)
                .orElseThrow(() -> new EntityNotFoundException(SoftwareModule.class, softwareModuleId));

        if (checkModule(fileName, module)) {
            LOG.warn("Softare module with id {} could not be found.", softwareModuleId);
            result = ResponseEntity.notFound().build();
        } else {

            // Exception squid:S3655 - Optional access is checked in checkModule
            // subroutine
            @SuppressWarnings("squid:S3655")
            final Artifact artifact = module.getArtifactByFilename(fileName).get();

            final AbstractDbArtifact file = artifactManagement.loadArtifactBinary(artifact.getSha1Hash())
                    .orElseThrow(() -> new ArtifactBinaryNotFoundException(artifact.getSha1Hash()));

            final String ifMatch = requestResponseContextHolder.getHttpServletRequest().getHeader(HttpHeaders.IF_MATCH);
            if (ifMatch != null && !HttpUtil.matchesHttpHeader(ifMatch, artifact.getSha1Hash())) {
                result = new ResponseEntity<>(HttpStatus.PRECONDITION_FAILED);
            } else {
                final ActionStatus action = checkAndLogDownload(requestResponseContextHolder.getHttpServletRequest(),
                        target, module.getId());

                final Long statusId = action.getId();

                result = FileStreamingUtil.writeFileResponse(file, artifact.getFilename(), artifact.getCreatedAt(),
                        requestResponseContextHolder.getHttpServletResponse(),
                        requestResponseContextHolder.getHttpServletRequest(),
                        (length, shippedSinceLastEvent,
                                total) -> eventPublisher.publishEvent(new DownloadProgressEvent(
                                        tenantAware.getCurrentTenant(), statusId, shippedSinceLastEvent,
                                        serviceMatcher != null ? serviceMatcher.getServiceId() : bus.getId())));

            }
        }
        return result;
    }

    private ActionStatus checkAndLogDownload(final HttpServletRequest request, final Target target, final Long module) {
        final Action action = controllerManagement
                .getActionForDownloadByTargetAndSoftwareModule(target.getControllerId(), module)
                .orElseThrow(() -> new SoftwareModuleNotAssignedToTargetException(module, target.getControllerId()));
        final String range = request.getHeader("Range");

        String message;
        if (range != null) {
            message = RepositoryConstants.SERVER_MESSAGE_PREFIX + "Target downloads range " + range + " of: "
                    + request.getRequestURI();
        } else {
            message = RepositoryConstants.SERVER_MESSAGE_PREFIX + "Target downloads " + request.getRequestURI();
        }

        return controllerManagement.addInformationalActionStatus(
                entityFactory.actionStatus().create(action.getId()).status(Status.DOWNLOAD).message(message));
    }

    private static boolean checkModule(final String fileName, final SoftwareModule module) {
        return null == module || !module.getArtifactByFilename(fileName).isPresent();
    }

    @Override
    // Exception squid:S3655 - Optional access is checked in checkModule
    // subroutine
    @SuppressWarnings("squid:S3655")
    public ResponseEntity<Void> downloadArtifactMd5(@PathVariable("tenant") final String tenant,
            @PathVariable("controllerId") final String controllerId,
            @PathVariable("softwareModuleId") final Long softwareModuleId,
            @PathVariable("fileName") final String fileName) {
        final Target target = controllerManagement.getByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));

        final SoftwareModule module = controllerManagement.getSoftwareModule(softwareModuleId)
                .orElseThrow(() -> new EntityNotFoundException(SoftwareModule.class, softwareModuleId));

        if (checkModule(fileName, module)) {
            LOG.warn("Software module with id {} could not be found.", softwareModuleId);
            return ResponseEntity.notFound().build();
        }

        final Artifact artifact = module.getArtifactByFilename(fileName)
                .orElseThrow(() -> new EntityNotFoundException(Artifact.class, fileName));

        checkAndLogDownload(requestResponseContextHolder.getHttpServletRequest(), target, module.getId());

        try {
            FileStreamingUtil.writeMD5FileResponse(requestResponseContextHolder.getHttpServletResponse(),
                    artifact.getMd5Hash(), fileName);
        } catch (final IOException e) {
            LOG.error("Failed to stream MD5 File", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return ResponseEntity.ok().build();

    }

    @Override
    public ResponseEntity<DdiDeploymentBase> getControllerBasedeploymentAction(
            @PathVariable("tenant") final String tenant, @PathVariable("controllerId") final String controllerId,
            @PathVariable("actionId") final Long actionId,
            @RequestParam(value = "c", required = false, defaultValue = "-1") final int resource,
            @RequestParam(value = "actionHistory", defaultValue = DdiRestConstants.NO_ACTION_HISTORY) final Integer actionHistoryMessageCount) {
        LOG.debug("getControllerBasedeploymentAction({},{})", controllerId, resource);

        final Target target = controllerManagement.getByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));

        final Action action = findActionWithExceptionIfNotFound(actionId);
        if (!action.getTarget().getId().equals(target.getId())) {
            LOG.warn(GIVEN_ACTION_IS_NOT_ASSIGNED_TO_GIVEN_TARGET, action.getId(), target.getId());
            return ResponseEntity.notFound().build();
        }

        checkAndCancelExpiredAction(action);

        if (!action.isCancelingOrCanceled()) {

            final List<DdiChunk> chunks = DataConversionHelper.createChunks(target, action, artifactUrlHandler,
                    systemManagement,
                    new ServletServerHttpRequest(requestResponseContextHolder.getHttpServletRequest()),
                    controllerManagement);

            final List<String> actionHistoryMsgs = controllerManagement.getActionHistoryMessages(action.getId(),
                    actionHistoryMessageCount == null ? Integer.parseInt(DdiRestConstants.NO_ACTION_HISTORY)
                            : actionHistoryMessageCount);

            final DdiActionHistory actionHistory = actionHistoryMsgs.isEmpty() ? null
                    : new DdiActionHistory(action.getStatus().name(), actionHistoryMsgs);

            final HandlingType downloadType = calculateDownloadType(action);
            final HandlingType updateType = calculateUpdateType(action, downloadType);

            final DdiMaintenanceWindowStatus maintenanceWindow = calculateMaintenanceWindow(action);

            final DdiDeploymentBase base = new DdiDeploymentBase(Long.toString(action.getId()),
                    new DdiDeployment(downloadType, updateType, chunks, maintenanceWindow), actionHistory);

            LOG.debug("Found an active UpdateAction for target {}. returning deployment: {}", controllerId, base);

            controllerManagement.registerRetrieved(action.getId(), RepositoryConstants.SERVER_MESSAGE_PREFIX
                    + "Target retrieved update action and should start now the download.");

            return new ResponseEntity<>(base, HttpStatus.OK);
        }

        return ResponseEntity.notFound().build();
    }

    private static HandlingType calculateDownloadType(final Action action) {
        if (action.isDownloadOnly() || action.isForce()) {
            return HandlingType.FORCED;
        }
        return HandlingType.ATTEMPT;
    }

    private static DdiMaintenanceWindowStatus calculateMaintenanceWindow(final Action action) {
        if (action.hasMaintenanceSchedule()) {
            return action.isMaintenanceWindowAvailable() ? DdiMaintenanceWindowStatus.AVAILABLE
                    : DdiMaintenanceWindowStatus.UNAVAILABLE;
        }
        return null;
    }

    private static HandlingType calculateUpdateType(final Action action, final HandlingType downloadType) {
        if (action.isDownloadOnly()) {
            return HandlingType.SKIP;
        } else if (action.hasMaintenanceSchedule()) {
            return action.isMaintenanceWindowAvailable() ? downloadType : HandlingType.SKIP;
        }
        return downloadType;
    }

    @Override
    public ResponseEntity<Void> postBasedeploymentActionFeedback(@Valid @RequestBody final DdiActionFeedback feedback,
            @PathVariable("tenant") final String tenant, @PathVariable("controllerId") final String controllerId,
            @PathVariable("actionId") @NotEmpty final Long actionId) {
        LOG.debug("provideBasedeploymentActionFeedback for target [{},{}]: {}", controllerId, actionId, feedback);

        final Target target = controllerManagement.getByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));

        if (!actionId.equals(feedback.getId())) {
            LOG.warn(
                    "provideBasedeploymentActionFeedback: action in payload ({}) was not identical to action in path ({}).",
                    feedback.getId(), actionId);
            return ResponseEntity.notFound().build();
        }

        final Action action = findActionWithExceptionIfNotFound(actionId);
        if (!action.getTarget().getId().equals(target.getId())) {
            LOG.warn(GIVEN_ACTION_IS_NOT_ASSIGNED_TO_GIVEN_TARGET, action.getId(), target.getId());
            return ResponseEntity.notFound().build();
        }

        if (!action.isActive()) {
            LOG.warn("Updating action {} with feedback {} not possible since action not active anymore.",
                    action.getId(), feedback.getId());
            return new ResponseEntity<>(HttpStatus.GONE);
        }

        controllerManagement.addUpdateActionStatus(generateUpdateStatus(feedback, controllerId, feedback.getId()));

        return ResponseEntity.ok().build();

    }

    private ActionStatusCreate generateUpdateStatus(final DdiActionFeedback feedback, final String controllerId,
            final Long actionid) {

        final List<String> messages = new ArrayList<>();

        if (!CollectionUtils.isEmpty(feedback.getStatus().getDetails())) {
            messages.addAll(feedback.getStatus().getDetails());
        }

        Status status;
        switch (feedback.getStatus().getExecution()) {
        case CANCELED:
            LOG.debug("Controller confirmed cancel (actionid: {}, controllerId: {}) as we got {} report.", actionid,
                    controllerId, feedback.getStatus().getExecution());
            status = Status.CANCELED;
            addMessageIfEmpty("Target confirmed cancelation.", messages);
            break;
        case REJECTED:
            LOG.info("Controller reported internal error (actionid: {}, controllerId: {}) as we got {} report.",
                    actionid, controllerId, feedback.getStatus().getExecution());
            status = Status.WARNING;
            addMessageIfEmpty("Target REJECTED update", messages);
            break;
        case CLOSED:
            status = handleClosedCase(feedback, controllerId, actionid, messages);
            break;
        case DOWNLOAD:
            LOG.debug("Controller confirmed status of download (actionId: {}, controllerId: {}) as we got {} report.",
                    actionid, controllerId, feedback.getStatus().getExecution());
            status = Status.DOWNLOAD;
            addMessageIfEmpty("Target confirmed download start", messages);
            break;
        case DOWNLOADED:
            LOG.debug("Controller confirmed download (actionId: {}, controllerId: {}) as we got {} report.", actionid,
                    controllerId, feedback.getStatus().getExecution());
            status = Status.DOWNLOADED;
            addMessageIfEmpty("Target confirmed download finished", messages);
            break;
        default:
            status = handleDefaultCase(feedback, controllerId, actionid, messages);
            break;
        }

        return entityFactory.actionStatus().create(actionid).status(status).messages(messages);
    }

    private static void addMessageIfEmpty(final String text, final List<String> messages) {
        if (messages != null && messages.isEmpty()) {
            messages.add(RepositoryConstants.SERVER_MESSAGE_PREFIX + text + ".");
        }
    }

    private Status handleDefaultCase(final DdiActionFeedback feedback, final String controllerId, final Long actionid,
            final List<String> messages) {
        Status status;
        LOG.debug("Controller reported intermediate status (actionid: {}, controllerId: {}) as we got {} report.",
                actionid, controllerId, feedback.getStatus().getExecution());
        status = Status.RUNNING;
        addMessageIfEmpty("Target reported " + feedback.getStatus().getExecution(), messages);
        return status;
    }

    private Status handleClosedCase(final DdiActionFeedback feedback, final String controllerId, final Long actionid,
            final List<String> messages) {
        Status status;
        LOG.debug("Controller reported closed (actionid: {}, controllerId: {}) as we got {} report.", actionid,
                controllerId, feedback.getStatus().getExecution());
        if (feedback.getStatus().getResult().getFinished() == FinalResult.FAILURE) {
            status = Status.ERROR;
            addMessageIfEmpty("Target reported CLOSED with ERROR!", messages);
        } else {
            status = Status.FINISHED;
            addMessageIfEmpty("Target reported CLOSED with OK!", messages);
        }
        return status;
    }

    @Override
    public ResponseEntity<Void> putConfigData(@Valid @RequestBody final DdiConfigData configData,
            @PathVariable("tenant") final String tenant, @PathVariable("controllerId") final String controllerId) {

        controllerManagement.updateControllerAttributes(controllerId, configData.getData(), getUpdateMode(configData));

        controllerManagement.triggerDistributionSetAssignmentCheck(controllerId);

        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<DdiCancel> getControllerCancelAction(@PathVariable("tenant") final String tenant,
            @PathVariable("controllerId") @NotEmpty final String controllerId,
            @PathVariable("actionId") @NotEmpty final Long actionId) {
        LOG.debug("getControllerCancelAction({})", controllerId);

        final Target target = controllerManagement.getByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));

        final Action action = findActionWithExceptionIfNotFound(actionId);
        if (!action.getTarget().getId().equals(target.getId())) {
            LOG.warn(GIVEN_ACTION_IS_NOT_ASSIGNED_TO_GIVEN_TARGET, action.getId(), target.getId());
            return ResponseEntity.notFound().build();
        }

        if (action.isCancelingOrCanceled()) {
            final DdiCancel cancel = new DdiCancel(String.valueOf(action.getId()),
                    new DdiCancelActionToStop(String.valueOf(action.getId())));

            LOG.debug("Found an active CancelAction for target {}. returning cancel: {}", controllerId, cancel);

            controllerManagement.registerRetrieved(action.getId(), RepositoryConstants.SERVER_MESSAGE_PREFIX
                    + "Target retrieved cancel action and should start now the cancelation.");

            return new ResponseEntity<>(cancel, HttpStatus.OK);
        }

        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<Void> postCancelActionFeedback(@Valid @RequestBody final DdiActionFeedback feedback,
            @PathVariable("tenant") final String tenant,
            @PathVariable("controllerId") @NotEmpty final String controllerId,
            @PathVariable("actionId") @NotEmpty final Long actionId) {
        LOG.debug("provideCancelActionFeedback for target [{}]: {}", controllerId, feedback);

        final Target target = controllerManagement.getByControllerId(controllerId)
                .orElseThrow(() -> new EntityNotFoundException(Target.class, controllerId));

        if (!actionId.equals(feedback.getId())) {
            LOG.warn(
                    "provideBasedeploymentActionFeedback: action in payload ({}) was not identical to action in path ({}).",
                    feedback.getId(), actionId);
            return ResponseEntity.notFound().build();
        }

        final Action action = findActionWithExceptionIfNotFound(actionId);
        if (!action.getTarget().getId().equals(target.getId())) {
            LOG.warn(GIVEN_ACTION_IS_NOT_ASSIGNED_TO_GIVEN_TARGET, action.getId(), target.getId());
            return ResponseEntity.notFound().build();
        }

        controllerManagement
                .addCancelActionStatus(generateActionCancelStatus(feedback, target, feedback.getId(), entityFactory));
        return ResponseEntity.ok().build();
    }

    private static ActionStatusCreate generateActionCancelStatus(final DdiActionFeedback feedback, final Target target,
            final Long actionid, final EntityFactory entityFactory) {

        final List<String> messages = new ArrayList<>();
        Status status;
        switch (feedback.getStatus().getExecution()) {
        case CANCELED:
            status = handleCaseCancelCanceled(feedback, target, actionid, messages);
            break;
        case REJECTED:
            LOG.info("Target rejected the cancelation request (actionid: {}, controllerId: {}).", actionid,
                    target.getControllerId());
            status = Status.CANCEL_REJECTED;
            messages.add(RepositoryConstants.SERVER_MESSAGE_PREFIX + "Target rejected the cancelation request.");
            break;
        case CLOSED:
            status = handleCancelClosedCase(feedback, messages);
            break;
        default:
            status = Status.RUNNING;
            break;
        }

        if (feedback.getStatus().getDetails() != null) {
            messages.addAll(feedback.getStatus().getDetails());
        }

        return entityFactory.actionStatus().create(actionid).status(status).messages(messages);

    }

    private static Status handleCancelClosedCase(final DdiActionFeedback feedback, final List<String> messages) {
        Status status;
        if (feedback.getStatus().getResult().getFinished() == FinalResult.FAILURE) {
            status = Status.ERROR;
            addMessageIfEmpty("Target was not able to complete cancelation", messages);
        } else {
            status = Status.CANCELED;
            addMessageIfEmpty("Cancelation confirmed", messages);
        }
        return status;
    }

    private static Status handleCaseCancelCanceled(final DdiActionFeedback feedback, final Target target,
            final Long actionid, final List<String> messages) {
        Status status;
        LOG.error(
                "Target reported cancel for a cancel which is not supported by the server (actionid: {}, controllerId: {}) as we got {} report.",
                actionid, target.getControllerId(), feedback.getStatus().getExecution());
        status = Status.WARNING;
        messages.add(RepositoryConstants.SERVER_MESSAGE_PREFIX
                + "Target reported cancel for a cancel which is not supported by the server.");
        return status;
    }

    private Action findActionWithExceptionIfNotFound(final Long actionId) {
        return controllerManagement.findActionWithDetails(actionId)
                .orElseThrow(() -> new EntityNotFoundException(Action.class, actionId));
    }

    /**
     * If the action has a maintenance schedule defined but is no longer valid,
     * cancel the action.
     *
     * @param action
     *            is the {@link Action} to check.
     */
    private void checkAndCancelExpiredAction(final Action action) {
        if (action != null && action.hasMaintenanceSchedule() && action.isMaintenanceScheduleLapsed()) {
            try {
                controllerManagement.cancelAction(action.getId());
            } catch (final CancelActionNotAllowedException e) {
                LOG.info("Cancel action not allowed exception :{}", e);
            }
        }
    }

    /**
     * Retrieve the update mode from the given update message.
     */
    private static UpdateMode getUpdateMode(final DdiConfigData configData) {
        final DdiUpdateMode mode = configData.getMode();
        if (mode != null) {
            return UpdateMode.valueOf(mode.name());
        }
        return null;
    }

}
