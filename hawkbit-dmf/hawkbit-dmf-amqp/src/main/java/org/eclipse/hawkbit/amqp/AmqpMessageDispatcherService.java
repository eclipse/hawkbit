/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.amqp;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.api.ApiType;
import org.eclipse.hawkbit.api.ArtifactUrl;
import org.eclipse.hawkbit.api.ArtifactUrlHandler;
import org.eclipse.hawkbit.api.URLPlaceholder;
import org.eclipse.hawkbit.api.URLPlaceholder.SoftwareData;
import org.eclipse.hawkbit.dmf.amqp.api.EventTopic;
import org.eclipse.hawkbit.dmf.amqp.api.MessageHeaderKey;
import org.eclipse.hawkbit.dmf.amqp.api.MessageType;
import org.eclipse.hawkbit.dmf.json.model.DmfActionRequest;
import org.eclipse.hawkbit.dmf.json.model.DmfArtifact;
import org.eclipse.hawkbit.dmf.json.model.DmfArtifactHash;
import org.eclipse.hawkbit.dmf.json.model.DmfDownloadAndUpdateRequest;
import org.eclipse.hawkbit.dmf.json.model.DmfMetadata;
import org.eclipse.hawkbit.dmf.json.model.DmfMultiActionRequest;
import org.eclipse.hawkbit.dmf.json.model.DmfSoftwareModule;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.RepositoryConstants;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.event.remote.DeploymentEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAttributesRequestedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.CancelTargetAssignmentEvent;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Artifact;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.SoftwareModuleMetadata;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.util.IpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.CollectionUtils;

import com.google.common.collect.Maps;

/**
 * {@link AmqpMessageDispatcherService} create all outgoing AMQP messages and
 * delegate the messages to a {@link AmqpMessageSenderService}.
 *
 * Additionally the dispatcher listener/subscribe for some target events e.g.
 * assignment.
 *
 */
public class AmqpMessageDispatcherService extends BaseAmqpService {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpMessageDispatcherService.class);

    private static final int MAX_ACTIONS = 100;

    private final ArtifactUrlHandler artifactUrlHandler;
    private final AmqpMessageSenderService amqpSenderService;
    private final SystemSecurityContext systemSecurityContext;
    private final SystemManagement systemManagement;
    private final TargetManagement targetManagement;
    private final ServiceMatcher serviceMatcher;
    private final DistributionSetManagement distributionSetManagement;
    private final DeploymentManagement deploymentManagement;
    private final SoftwareModuleManagement softwareModuleManagement;

    /**
     * Constructor.
     *
     * @param rabbitTemplate
     *            the rabbitTemplate
     * @param amqpSenderService
     *            to send AMQP message
     * @param artifactUrlHandler
     *            for generating download URLs
     * @param systemSecurityContext
     *            for execution with system permissions
     * @param systemManagement
     *            the systemManagement
     * @param targetManagement
     *            to access target information
     * @param serviceMatcher
     *            to check in cluster case if the message is from the same
     *            cluster node
     * @param distributionSetManagement
     *            to retrieve modules
     */
    protected AmqpMessageDispatcherService(final RabbitTemplate rabbitTemplate,
            final AmqpMessageSenderService amqpSenderService, final ArtifactUrlHandler artifactUrlHandler,
            final SystemSecurityContext systemSecurityContext, final SystemManagement systemManagement,
            final TargetManagement targetManagement, final ServiceMatcher serviceMatcher,
            final DistributionSetManagement distributionSetManagement,
            final SoftwareModuleManagement softwareModuleManagement, final DeploymentManagement deploymentManagement) {
        super(rabbitTemplate);
        this.artifactUrlHandler = artifactUrlHandler;
        this.amqpSenderService = amqpSenderService;
        this.systemSecurityContext = systemSecurityContext;
        this.systemManagement = systemManagement;
        this.targetManagement = targetManagement;
        this.serviceMatcher = serviceMatcher;
        this.distributionSetManagement = distributionSetManagement;
        this.softwareModuleManagement = softwareModuleManagement;
        this.deploymentManagement = deploymentManagement;
    }

    /**
     * Method to send a message to a RabbitMQ Exchange after the Distribution
     * set has been assign to a Target.
     *
     * @param assignedEvent
     *            the object to be send.
     */
    @EventListener(classes = TargetAssignDistributionSetEvent.class)
    protected void targetAssignDistributionSet(final TargetAssignDistributionSetEvent assignedEvent) {
        if (isNotFromSelf(assignedEvent)) {
            return;
        }

        LOG.debug("targetAssignDistributionSet retrieved. I will forward it to DMF broker.");

        distributionSetManagement.get(assignedEvent.getDistributionSetId()).ifPresent(ds -> {

            final Map<SoftwareModule, List<SoftwareModuleMetadata>> softwareModules = getSoftwareModulesWithMetadata(
                    ds);

            targetManagement.getByControllerID(assignedEvent.getActions().keySet())
                    .forEach(target -> sendUpdateMessageToTarget(assignedEvent.getTenant(), target,
                            assignedEvent.getActions().get(target.getControllerId()), softwareModules,
                            assignedEvent.isMaintenanceWindowAvailable()));

        });
    }

    /**
     * Method to send a message to a RabbitMQ exchange after the Distribution
     * set has been assign to a Target.
     *
     * @param e
     *            the object to be send.
     */
    @EventListener(classes = DeploymentEvent.class)
    protected void onDeployment(final DeploymentEvent e) {
        if (isNotFromSelf(e)) {
            return;
        }

        LOG.debug("DeploymentEvent received. I will forward it to DMF broker.");
        sendMultiActionRequestMessages(e.getTenant(), e.getControllerIds());
    }

    protected void sendMultiActionRequestMessages(final String tenant, final List<String> controllerIds) {

        final Map<Long, Map<SoftwareModule, List<SoftwareModuleMetadata>>> softwareModuleMetadata = new HashMap<>();
        targetManagement.getByControllerID(controllerIds).stream().forEach(target -> {

            final List<Action> activeActions = deploymentManagement
                    .findActiveActionsByTarget(PageRequest.of(0, MAX_ACTIONS), target.getControllerId()).getContent();

            activeActions.forEach(action -> {
                final DistributionSet distributionSet = action.getDistributionSet();
                softwareModuleMetadata.computeIfAbsent(distributionSet.getId(),
                        id -> getSoftwareModulesWithMetadata(distributionSet));
            });

            if (!activeActions.isEmpty()) {
                sendMultiActionRequestToTarget(tenant, target, activeActions, softwareModuleMetadata);
            }

        });

    }

    protected void sendMultiActionRequestToTarget(final String tenant, final Target target, final List<Action> actions,
            final Map<Long, Map<SoftwareModule, List<SoftwareModuleMetadata>>> softwareModulesPerDistributionSet) {

        final URI targetAdress = target.getAddress();
        if (!IpUtil.isAmqpUri(targetAdress)) {
            return;
        }

        final DmfMultiActionRequest multiActionRequest = new DmfMultiActionRequest();
        actions.forEach(action -> {
            final DmfActionRequest actionRequest = createDmfActionRequest(target, action,
                    softwareModulesPerDistributionSet.get(action.getDistributionSet().getId()));
            multiActionRequest.addElement(getEventTypeForAction(action), actionRequest);
        });

        final Message message = getMessageConverter().toMessage(multiActionRequest,
                createConnectorMessagePropertiesEvent(tenant, target.getControllerId(), EventTopic.MULTI_ACTION));
        amqpSenderService.sendMessage(message, targetAdress);

    }

    private DmfActionRequest createDmfActionRequest(final Target target, final Action action,
            final Map<SoftwareModule, List<SoftwareModuleMetadata>> softwareModules) {
        if (action.isCancelingOrCanceled()) {
            return createPlainActionRequest(action);
        }
        return createDownloadAndUpdateRequest(target, action, softwareModules);

    }

    private static DmfActionRequest createPlainActionRequest(final Action action) {
        final DmfActionRequest actionRequest = new DmfActionRequest();
        actionRequest.setActionId(action.getId());
        return actionRequest;
    }
    
    private DmfDownloadAndUpdateRequest createDownloadAndUpdateRequest(final Target target, final Action action,
            final Map<SoftwareModule, List<SoftwareModuleMetadata>> softwareModules) {
        final DmfDownloadAndUpdateRequest request = new DmfDownloadAndUpdateRequest();
        request.setActionId(action.getId());
        request.setTargetSecurityToken(systemSecurityContext.runAsSystem(target::getSecurityToken));
        if (softwareModules != null) {
            softwareModules.entrySet()
                    .forEach(entry -> request.addSoftwareModule(convertToAmqpSoftwareModule(target, entry)));
        }
        return request;
    }

    /**
     * Method to get the type of event depending on whether the action has a
     * valid maintenance window available or not based on defined maintenance
     * schedule. In case of no maintenance schedule or if there is a valid
     * window available, the topic {@link EventTopic#DOWNLOAD_AND_INSTALL} is
     * returned else {@link EventTopic#DOWNLOAD} is returned.
     *
     * @param maintenanceWindowAvailable
     *            valid maintenance window or not.
     *
     * @return {@link EventTopic} to use for message.
     */
    private static EventTopic getEventTypeForTarget(final boolean maintenanceWindowAvailable) {
        return maintenanceWindowAvailable ? EventTopic.DOWNLOAD_AND_INSTALL : EventTopic.DOWNLOAD;
    }

    /**
     * Method to get the type of event depending on whether the action has a
     * valid maintenance window available or not based on defined maintenance
     * schedule. In case of no maintenance schedule or if there is a valid
     * window available, the topic {@link EventTopic#DOWNLOAD_AND_INSTALL} is
     * returned else {@link EventTopic#DOWNLOAD} is returned.
     *
     * @param maintenanceWindowAvailable
     *            valid maintenance window or not.
     *
     * @return {@link EventTopic} to use for message.
     */
    private static EventTopic getEventTypeForAction(final Action action) {
        if (action.isCancelingOrCanceled()) {
            return EventTopic.CANCEL_DOWNLOAD;
        }
        return getEventTypeForTarget(action.isMaintenanceWindowAvailable());
    }

    /**
     * Method to send a message to a RabbitMQ Exchange after the assignment of
     * the Distribution set to a Target has been canceled.
     *
     * @param cancelEvent
     *            the object to be send.
     */
    @EventListener(classes = CancelTargetAssignmentEvent.class)
    protected void targetCancelAssignmentToDistributionSet(final CancelTargetAssignmentEvent cancelEvent) {
        if (isNotFromSelf(cancelEvent)) {
            return;
        }

        sendCancelMessageToTarget(cancelEvent.getTenant(), cancelEvent.getEntity().getControllerId(),
                cancelEvent.getActionId(), cancelEvent.getEntity().getAddress());
    }

    /**
     * Method to send a message to a RabbitMQ Exchange after a Target was
     * deleted.
     *
     * @param deleteEvent
     *            the TargetDeletedEvent which holds the necessary data for
     *            sending a target delete message.
     */
    @EventListener(classes = TargetDeletedEvent.class)
    protected void targetDelete(final TargetDeletedEvent deleteEvent) {
        if (isNotFromSelf(deleteEvent)) {
            return;
        }
        sendDeleteMessage(deleteEvent.getTenant(), deleteEvent.getControllerId(), deleteEvent.getTargetAddress());
    }

    @EventListener(classes = TargetAttributesRequestedEvent.class)
    protected void targetTriggerUpdateAttributes(final TargetAttributesRequestedEvent updateAttributesEvent) {
        sendUpdateAttributesMessageToTarget(updateAttributesEvent.getTenant(), updateAttributesEvent.getControllerId(),
                updateAttributesEvent.getTargetAddress());
    }

    protected void sendUpdateMessageToTarget(final String tenant, final Target target, final Long actionId,
            final Map<SoftwareModule, List<SoftwareModuleMetadata>> modules, final boolean maintenanceWindowAvailable) {

        final URI targetAdress = target.getAddress();
        if (!IpUtil.isAmqpUri(targetAdress)) {
            return;
        }

        final DmfDownloadAndUpdateRequest downloadAndUpdateRequest = new DmfDownloadAndUpdateRequest();
        downloadAndUpdateRequest.setActionId(actionId);

        final String targetSecurityToken = systemSecurityContext.runAsSystem(target::getSecurityToken);
        downloadAndUpdateRequest.setTargetSecurityToken(targetSecurityToken);

        modules.entrySet().forEach(entry -> {

            final DmfSoftwareModule amqpSoftwareModule = convertToAmqpSoftwareModule(target, entry);
            downloadAndUpdateRequest.addSoftwareModule(amqpSoftwareModule);
        });

        final Message message = getMessageConverter().toMessage(downloadAndUpdateRequest,
                createConnectorMessagePropertiesEvent(tenant, target.getControllerId(),
                        getEventTypeForTarget(maintenanceWindowAvailable)));
        amqpSenderService.sendMessage(message, targetAdress);
    }

    protected void sendPingReponseToDmfReceiver(final Message ping, final String tenant, final String virtualHost) {
        final Message message = MessageBuilder.withBody(String.valueOf(System.currentTimeMillis()).getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .setCorrelationId(ping.getMessageProperties().getCorrelationId())
                .setHeader(MessageHeaderKey.TYPE, MessageType.PING_RESPONSE).setHeader(MessageHeaderKey.TENANT, tenant)
                .build();

        amqpSenderService.sendMessage(message,
                IpUtil.createAmqpUri(virtualHost, ping.getMessageProperties().getReplyTo()));
    }

    protected void sendDeleteMessage(final String tenant, final String controllerId, final String targetAddress) {

        if (!hasValidAddress(targetAddress)) {
            return;
        }

        final Message message = new Message(null, createConnectorMessagePropertiesDeleteThing(tenant, controllerId));
        amqpSenderService.sendMessage(message, URI.create(targetAddress));
    }

    private boolean hasValidAddress(final String targetAddress) {
        return targetAddress != null && IpUtil.isAmqpUri(URI.create(targetAddress));
    }

    private boolean isNotFromSelf(final RemoteApplicationEvent event) {
        return serviceMatcher != null && !serviceMatcher.isFromSelf(event);
    }

    protected void sendCancelMessageToTarget(final String tenant, final String controllerId, final Long actionId,
            final URI address) {
        if (!IpUtil.isAmqpUri(address)) {
            return;
        }

        final DmfActionRequest actionRequest = new DmfActionRequest();
        actionRequest.setActionId(actionId);

        final Message message = getMessageConverter().toMessage(actionRequest,
                createConnectorMessagePropertiesEvent(tenant, controllerId, EventTopic.CANCEL_DOWNLOAD));

        amqpSenderService.sendMessage(message, address);

    }

    protected void sendUpdateAttributesMessageToTarget(final String tenant, final String controllerId,
            final String targetAddress) {
        if (!hasValidAddress(targetAddress)) {
            return;
        }

        final Message message = new Message(null,
                createConnectorMessagePropertiesEvent(tenant, controllerId, EventTopic.REQUEST_ATTRIBUTES_UPDATE));

        amqpSenderService.sendMessage(message, URI.create(targetAddress));
    }

    private static MessageProperties createConnectorMessagePropertiesEvent(final String tenant,
            final String controllerId, final EventTopic topic) {
        final MessageProperties messageProperties = createConnectorMessageProperties(tenant, controllerId);
        messageProperties.setHeader(MessageHeaderKey.TOPIC, topic);
        messageProperties.setHeader(MessageHeaderKey.TYPE, MessageType.EVENT);
        return messageProperties;
    }

    private static MessageProperties createConnectorMessagePropertiesDeleteThing(final String tenant,
            final String controllerId) {
        final MessageProperties messageProperties = createConnectorMessageProperties(tenant, controllerId);
        messageProperties.setHeader(MessageHeaderKey.TYPE, MessageType.THING_DELETED);
        return messageProperties;
    }

    private static MessageProperties createConnectorMessageProperties(final String tenant, final String controllerId) {
        final MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setHeader(MessageHeaderKey.CONTENT_TYPE, MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setHeader(MessageHeaderKey.THING_ID, controllerId);
        messageProperties.setHeader(MessageHeaderKey.TENANT, tenant);
        return messageProperties;
    }

    private DmfSoftwareModule convertToAmqpSoftwareModule(final Target target,
            final Entry<SoftwareModule, List<SoftwareModuleMetadata>> entry) {
        final DmfSoftwareModule amqpSoftwareModule = new DmfSoftwareModule();
        amqpSoftwareModule.setModuleId(entry.getKey().getId());
        amqpSoftwareModule.setModuleType(entry.getKey().getType().getKey());
        amqpSoftwareModule.setModuleVersion(entry.getKey().getVersion());
        amqpSoftwareModule.setArtifacts(convertArtifacts(target, entry.getKey().getArtifacts()));

        if (!CollectionUtils.isEmpty(entry.getValue())) {
            amqpSoftwareModule.setMetadata(convertMetadata(entry.getValue()));
        }

        return amqpSoftwareModule;
    }

    private List<DmfMetadata> convertMetadata(final List<SoftwareModuleMetadata> metadata) {
        return metadata.stream().map(md -> new DmfMetadata(md.getKey(), md.getValue())).collect(Collectors.toList());
    }

    private List<DmfArtifact> convertArtifacts(final Target target, final List<Artifact> localArtifacts) {
        if (localArtifacts.isEmpty()) {
            return Collections.emptyList();
        }

        return localArtifacts.stream().map(localArtifact -> convertArtifact(target, localArtifact))
                .collect(Collectors.toList());
    }

    private DmfArtifact convertArtifact(final Target target, final Artifact localArtifact) {
        final DmfArtifact artifact = new DmfArtifact();

        artifact.setUrls(artifactUrlHandler
                .getUrls(new URLPlaceholder(systemManagement.getTenantMetadata().getTenant(),
                        systemManagement.getTenantMetadata().getId(), target.getControllerId(), target.getId(),
                        new SoftwareData(localArtifact.getSoftwareModule().getId(), localArtifact.getFilename(),
                                localArtifact.getId(), localArtifact.getSha1Hash())),
                        ApiType.DMF)
                .stream().collect(Collectors.toMap(ArtifactUrl::getProtocol, ArtifactUrl::getRef)));

        artifact.setFilename(localArtifact.getFilename());
        artifact.setHashes(new DmfArtifactHash(localArtifact.getSha1Hash(), localArtifact.getMd5Hash()));
        artifact.setSize(localArtifact.getSize());
        return artifact;
    }

    private Map<SoftwareModule, List<SoftwareModuleMetadata>> getSoftwareModulesWithMetadata(
            final DistributionSet distributionSet) {
        final Map<SoftwareModule, List<SoftwareModuleMetadata>> moduleMetadata = Maps
                .newHashMapWithExpectedSize(distributionSet.getModules().size());
        distributionSet.getModules()
                .forEach(
                        module -> moduleMetadata.put(module,
                                softwareModuleManagement.findMetaDataBySoftwareModuleIdAndTargetVisible(
                                        PageRequest.of(0, RepositoryConstants.MAX_META_DATA_COUNT), module.getId())
                                        .getContent()));
        return moduleMetadata;
    }

}
