/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.hawkbit.repository.model.Action.ActionType.DOWNLOAD_ONLY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.hawkbit.amqp.AmqpMessageHandlerService;
import org.eclipse.hawkbit.amqp.AmqpProperties;
import org.eclipse.hawkbit.dmf.amqp.api.EventTopic;
import org.eclipse.hawkbit.dmf.amqp.api.MessageHeaderKey;
import org.eclipse.hawkbit.dmf.amqp.api.MessageType;
import org.eclipse.hawkbit.dmf.json.model.DmfActionStatus;
import org.eclipse.hawkbit.dmf.json.model.DmfActionUpdateStatus;
import org.eclipse.hawkbit.dmf.json.model.DmfAttributeUpdate;
import org.eclipse.hawkbit.dmf.json.model.DmfUpdateMode;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.RepositoryConstants;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAttributesRequestedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetPollEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.CancelTargetAssignmentEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.exception.EntityAlreadyExistsException;
import org.eclipse.hawkbit.repository.jpa.model.JpaDistributionSet;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.ActionStatus;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;

@Feature("Component Tests - Device Management Federation API")
@Story("Amqp Message Handler Service")
public class AmqpMessageHandlerServiceIntegrationTest extends AbstractAmqpServiceIntegrationTest {
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final String TARGET_PREFIX = "Dmf_hand_";

    @Autowired
    private AmqpProperties amqpProperties;
    
    @Autowired
    private AmqpMessageHandlerService amqpMessageHandlerService;

    @Test
    @Description("Tests DMF PING request and expected reponse.")
    public void pingDmfInterface() {
        final Message pingMessage = createPingMessage(CORRELATION_ID, TENANT_EXIST);
        getDmfClient().send(pingMessage);

        assertPingReplyMessage(CORRELATION_ID);

        Mockito.verifyZeroInteractions(getDeadletterListener());
    }

    @Test
    @Description("Tests register target")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetPollEvent.class, count = 3)})
    public void registerTargets() {
        final String controllerId = TARGET_PREFIX + "registerTargets";
        registerAndAssertTargetWithExistingTenant(controllerId, 1);

        final String target2 = "Target2";
        registerAndAssertTargetWithExistingTenant(target2, 2);

        registerSameTargetAndAssertBasedOnVersion(target2, 2, TargetUpdateStatus.REGISTERED);
        Mockito.verifyZeroInteractions(getDeadletterListener());
    }

    @Test
    @Description("Tests register invalid target withy empty controller id.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void registerEmptyTarget() {
        createAndSendThingCreated("", TENANT_EXIST);
        assertAllTargetsCount(0);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests register invalid target with whitspace controller id.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void registerWhitespaceTarget() {
        createAndSendThingCreated("Invalid Invalid", TENANT_EXIST);
        assertAllTargetsCount(0);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests register invalid target with null controller id.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void registerInvalidNullTarget() {
        createAndSendThingCreated(null, TENANT_EXIST);
        assertAllTargetsCount(0);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests register invalid target with too long controller id")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void registerInvalidTargetWithTooLongControllerId() {
        createAndSendThingCreated(RandomStringUtils.randomAlphabetic(Target.CONTROLLER_ID_MAX_SIZE + 1), TENANT_EXIST);
        assertAllTargetsCount(0);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests null reply to property in message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void missingReplyToProperty() {
        final String controllerId = TARGET_PREFIX + "missingReplyToProperty";
        final Message createTargetMessage = createTargetMessage(controllerId, TENANT_EXIST);
        createTargetMessage.getMessageProperties().setReplyTo(null);
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests missing reply to property in message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void emptyReplyToProperty() {
        final String controllerId = TARGET_PREFIX + "emptyReplyToProperty";
        final Message createTargetMessage = createTargetMessage(controllerId, TENANT_EXIST);
        createTargetMessage.getMessageProperties().setReplyTo("");
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests missing thing id property in message. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void missingThingIdProperty() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().remove(MessageHeaderKey.THING_ID);
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests null thing id property in message. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void nullThingIdProperty() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests missing tenant message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void missingTenantHeader() {
        final String controllerId = TARGET_PREFIX + "missingTenantHeader";
        final Message createTargetMessage = createTargetMessage(controllerId, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().remove(MessageHeaderKey.TENANT);
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests null tenant message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void nullTenantHeader() {
        final String controllerId = TARGET_PREFIX + "nullTenantHeader";
        final Message createTargetMessage = createTargetMessage(controllerId, null);
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests empty tenant message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void emptyTenantHeader() {
        final String controllerId = TARGET_PREFIX + "emptyTenantHeader";
        final Message createTargetMessage = createTargetMessage(controllerId, "");
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests tenant not exist. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void tenantNotExist() {
        final String controllerId = TARGET_PREFIX + "tenantNotExist";
        final Message createTargetMessage = createTargetMessage(controllerId, "TenantNotExist");
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertThat(systemManagement.findTenants(PAGE)).hasSize(1);
    }

    @Test
    @Description("Tests missing type message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void missingTypeHeader() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().remove(MessageHeaderKey.TYPE);
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests null type message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void nullTypeHeader() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TYPE, null);
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests empty type message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void emptyTypeHeader() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TYPE, "");
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests invalid type message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void invalidTypeHeader() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TYPE, "NotExist");
        getDmfClient().send(createTargetMessage);

        verifyOneDeadLetterMessage();
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests null topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void nullTopicHeader() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        eventMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TOPIC, null);
        getDmfClient().send(eventMessage);

        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests null topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void emptyTopicHeader() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        eventMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TOPIC, "");
        getDmfClient().send(eventMessage);

        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests null topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void invalidTopicHeader() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        eventMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TOPIC, "NotExist");
        getDmfClient().send(eventMessage);

        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests missing topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void missingTopicHeader() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        eventMessage.getMessageProperties().getHeaders().remove(MessageHeaderKey.TOPIC);
        getDmfClient().send(eventMessage);

        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests invalid null message content. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void updateActionStatusWithNullContent() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, null);
        getDmfClient().send(eventMessage);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests invalid empty message content. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void updateActionStatusWithEmptyContent() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        getDmfClient().send(eventMessage);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests invalid json message content. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void updateActionStatusWithInvalidJsonContent() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS,
                "Invalid Content");
        getDmfClient().send(eventMessage);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests invalid topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void updateActionStatusWithInvalidActionId() {
        final DmfActionUpdateStatus actionUpdateStatus = new DmfActionUpdateStatus(1L, DmfActionStatus.RUNNING);
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS,
                actionUpdateStatus);
        getDmfClient().send(eventMessage);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Tests register target and send finished message")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 2), @Expect(type = TargetPollEvent.class, count = 1)})
    public void finishActionStatus() {
        final String controllerId = TARGET_PREFIX + "finishActionStatus";
        registerTargetAndSendAndAssertUpdateActionStatus(DmfActionStatus.FINISHED, Status.FINISHED, controllerId);
    }

    @Test
    @Description("Register a target and send a update action status (running). Verfiy if the updated action status is correct.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 0), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1)})
    public void runningActionStatus() {
        final String controllerId = TARGET_PREFIX + "runningActionStatus";
        registerTargetAndSendAndAssertUpdateActionStatus(DmfActionStatus.RUNNING, Status.RUNNING, controllerId);
    }

    @Test
    @Description("Register a target and send an update action status (downloaded). Verfiy if the updated action status is correct.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 0), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1)})
    public void downloadedActionStatus() {
        final String controllerId = TARGET_PREFIX + "downloadedActionStatus";
        registerTargetAndSendAndAssertUpdateActionStatus(DmfActionStatus.DOWNLOADED, Status.DOWNLOADED, controllerId);
    }

    @Test
    @Description("Register a target and send a update action status (download). Verfiy if the updated action status is correct.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1)})
    public void downloadActionStatus() {
        final String controllerId = TARGET_PREFIX + "downloadActionStatus";
        registerTargetAndSendAndAssertUpdateActionStatus(DmfActionStatus.DOWNLOAD, Status.DOWNLOAD, controllerId);
    }

    @Test
    @Description("Register a target and send a update action status (error). Verfiy if the updated action status is correct.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 2), @Expect(type = TargetPollEvent.class, count = 1)})
    public void errorActionStatus() {
        final String controllerId = TARGET_PREFIX + "errorActionStatus";
        registerTargetAndSendAndAssertUpdateActionStatus(DmfActionStatus.ERROR, Status.ERROR, controllerId);
    }

    @Test
    @Description("Register a target and send a update action status (warning). Verfiy if the updated action status is correct.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1)})
    public void warningActionStatus() {
        final String controllerId = TARGET_PREFIX + "warningActionStatus";
        registerTargetAndSendAndAssertUpdateActionStatus(DmfActionStatus.WARNING, Status.WARNING, controllerId);
    }

    @Test
    @Description("Register a target and send a update action status (retrieved). Verfiy if the updated action status is correct.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1)})
    public void retrievedActionStatus() {
        final String controllerId = TARGET_PREFIX + "retrievedActionStatus";
        registerTargetAndSendAndAssertUpdateActionStatus(DmfActionStatus.RETRIEVED, Status.RETRIEVED, controllerId);
    }

    @Test
    @Description("Register a target and send a invalid update action status (cancel). This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1)})
    public void cancelNotAllowActionStatus() {
        final String controllerId = TARGET_PREFIX + "cancelNotAllowActionStatus";
        registerTargetAndSendActionStatus(DmfActionStatus.CANCELED, controllerId);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Verfiy receiving a download and install message if a deployment is done before the target has polled the first time.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 2)})
    public void receiveDownLoadAndInstallMessageAfterAssignment() {
        final String controllerId = TARGET_PREFIX + "receiveDownLoadAndInstallMessageAfterAssignment";

        // setup
        registerAndAssertTargetWithExistingTenant(controllerId);
        final DistributionSet distributionSet = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        testdataFactory.addSoftwareModuleMetadata(distributionSet);
        assignDistributionSet(distributionSet.getId(), controllerId);

        // test
        registerSameTargetAndAssertBasedOnVersion(controllerId, 1, TargetUpdateStatus.PENDING);

        // verify
        assertDownloadAndInstallMessage(distributionSet.getModules(), controllerId);
        Mockito.verifyZeroInteractions(getDeadletterListener());
    }

    @Test
    @Description("Verfiy receiving a download message if a deployment is done with window configured but before maintenance window start time.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 2)})
    public void receiveDownloadMessageBeforeMaintenanceWindowStartTime() {
        final String controllerId = TARGET_PREFIX + "receiveDownLoadMessageBeforeMaintenanceWindowStartTime";

        // setup
        registerAndAssertTargetWithExistingTenant(controllerId);
        final DistributionSet distributionSet = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        testdataFactory.addSoftwareModuleMetadata(distributionSet);
        assignDistributionSetWithMaintenanceWindow(distributionSet.getId(), controllerId, getTestSchedule(2),
                getTestDuration(1), getTestTimeZone());

        // test
        registerSameTargetAndAssertBasedOnVersion(controllerId, 1, TargetUpdateStatus.PENDING);

        // verify
        assertDownloadMessage(distributionSet.getModules(), controllerId);
        Mockito.verifyZeroInteractions(getDeadletterListener());
    }

    @Test
    @Description("Verify receiving a download_and_install message if a deployment is done with window configured and during maintenance window start time.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 2)})
    public void receiveDownloadAndInstallMessageDuringMaintenanceWindow() {
        final String controllerId = TARGET_PREFIX + "receiveDownLoadAndInstallMessageDuringMaintenanceWindow";

        // setup
        registerAndAssertTargetWithExistingTenant(controllerId);
        final DistributionSet distributionSet = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        testdataFactory.addSoftwareModuleMetadata(distributionSet);
        assignDistributionSetWithMaintenanceWindow(distributionSet.getId(), controllerId, getTestSchedule(-5),
                getTestDuration(10), getTestTimeZone());

        // test
        registerSameTargetAndAssertBasedOnVersion(controllerId, 1, TargetUpdateStatus.PENDING);

        // verify
        assertDownloadAndInstallMessage(distributionSet.getModules(), controllerId);
        Mockito.verifyZeroInteractions(getDeadletterListener());
    }

    @Test
    @Description("Verfiy receiving a cancel update message if a deployment is canceled before the target has polled the first time.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = TargetUpdatedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 2)})
    public void receiveCancelUpdateMessageAfterAssignmentWasCanceled() {
        final String controllerId = TARGET_PREFIX + "receiveCancelUpdateMessageAfterAssignmentWasCanceled";

        // Setup
        registerAndAssertTargetWithExistingTenant(controllerId);
        final DistributionSet distributionSet = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        final DistributionSetAssignmentResult distributionSetAssignmentResult = assignDistributionSet(
                distributionSet.getId(), controllerId);
        deploymentManagement.cancelAction(getFirstAssignedActionId(distributionSetAssignmentResult));

        // test
        registerSameTargetAndAssertBasedOnVersion(controllerId, 1, TargetUpdateStatus.PENDING);

        // verify
        assertCancelActionMessage(getFirstAssignedActionId(distributionSetAssignmentResult), controllerId);
        Mockito.verifyZeroInteractions(getDeadletterListener());
    }

    @Test
    @Description("Register a target and send a invalid update action status (canceled). The current status (pending) is not a canceling state. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1)})
    public void actionNotExists() {
        final String controllerId = TARGET_PREFIX + "actionNotExists";

        final Long actionId = registerTargetAndCancelActionId(controllerId);
        final Long actionNotExist = actionId + 1;

        sendActionUpdateStatus(new DmfActionUpdateStatus(actionNotExist, DmfActionStatus.CANCELED));
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Register a target and send a invalid update action status (cancel_rejected). This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1)})
    public void canceledRejectedNotAllowActionStatus() {
        final String controllerId = TARGET_PREFIX + "canceledRejectedNotAllowActionStatus";
        registerTargetAndSendActionStatus(DmfActionStatus.CANCEL_REJECTED, controllerId);
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Register a target and send a valid update action status (cancel_rejected). Verfiy if the updated action status is correct.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1)})
    public void canceledRejectedActionStatus() {
        final String controllerId = TARGET_PREFIX + "canceledRejectedActionStatus";

        final Long actionId = registerTargetAndCancelActionId(controllerId);

        sendActionUpdateStatus(new DmfActionUpdateStatus(actionId, DmfActionStatus.CANCEL_REJECTED));
        assertAction(actionId, 1, Status.RUNNING, Status.CANCELING, Status.CANCEL_REJECTED);
    }

    @Test
    @Description("Verify that sending an update controller attribute message to an existing target works. Verify that different update modes (merge, replace, remove) can be used.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 4), @Expect(type = TargetPollEvent.class, count = 1)})
    public void updateAttributesWithDifferentUpdateModes() {
        final String controllerId = TARGET_PREFIX + "updateAttributes";

        // setup
        registerAndAssertTargetWithExistingTenant(controllerId);

        // no update mode specified
        updateAttributesWithoutUpdateMode(controllerId);

        // update mode REPLACE
        updateAttributesWithUpdateModeReplace(controllerId);

        // update mode MERGE
        updateAttributesWithUpdateModeMerge(controllerId);

        // update mode REMOVE
        updateAttributesWithUpdateModeRemove(controllerId);

    }

    @Step
    private void updateAttributesWithUpdateModeRemove(final String controllerId) {

        // assemble the expected attributes
        final Map<String, String> expectedAttributes = targetManagement.getControllerAttributes(controllerId);
        expectedAttributes.remove("k1");
        expectedAttributes.remove("k3");

        // send a update message with update mode
        final Map<String, String> removeAttributes = new HashMap<>();
        removeAttributes.put("k1", "foo");
        removeAttributes.put("k3", "bar");

        final DmfAttributeUpdate remove = new DmfAttributeUpdate();
        remove.setMode(DmfUpdateMode.REMOVE);
        remove.getAttributes().putAll(removeAttributes);
        sendUpdateAttributeMessage(controllerId, TENANT_EXIST, remove);

        // validate
        assertUpdateAttributes(controllerId, expectedAttributes);
    }

    @Step
    private void updateAttributesWithUpdateModeMerge(final String controllerId) {

        // get the current attributes
        final Map<String, String> attributes = new HashMap<>(targetManagement.getControllerAttributes(controllerId));

        // send a update message with update mode MERGE
        final Map<String, String> mergeAttributes = new HashMap<>();
        mergeAttributes.put("k1", "v1_modified_again");
        mergeAttributes.put("k4", "v4");

        final DmfAttributeUpdate merge = new DmfAttributeUpdate();
        merge.setMode(DmfUpdateMode.MERGE);
        merge.getAttributes().putAll(mergeAttributes);
        sendUpdateAttributeMessage(controllerId, TENANT_EXIST, merge);

        // validate
        final Map<String, String> expectedAttributes = new HashMap<>();
        expectedAttributes.putAll(attributes);
        expectedAttributes.putAll(mergeAttributes);
        assertUpdateAttributes(controllerId, expectedAttributes);
    }

    @Step
    private void updateAttributesWithUpdateModeReplace(final String controllerId) {

        // send a update message with update mode REPLACE
        final Map<String, String> expectedAttributes = new HashMap<>();
        expectedAttributes.put("k1", "v1_modified");
        expectedAttributes.put("k2", "v2");
        expectedAttributes.put("k3", "v3");

        final DmfAttributeUpdate replace = new DmfAttributeUpdate();
        replace.setMode(DmfUpdateMode.REPLACE);
        replace.getAttributes().putAll(expectedAttributes);
        sendUpdateAttributeMessage(controllerId, TENANT_EXIST, replace);

        // validate
        assertUpdateAttributes(controllerId, expectedAttributes);
    }

    @Step
    private void updateAttributesWithoutUpdateMode(final String controllerId) {

        // send a update message which does not specify an update mode
        final Map<String, String> expectedAttributes = new HashMap<>();
        expectedAttributes.put("k0", "v0");
        expectedAttributes.put("k1", "v1");

        final DmfAttributeUpdate defaultUpdate = new DmfAttributeUpdate();
        defaultUpdate.getAttributes().putAll(expectedAttributes);
        sendUpdateAttributeMessage(controllerId, TENANT_EXIST, defaultUpdate);

        // validate
        assertUpdateAttributes(controllerId, expectedAttributes);
    }

    @Test
    @Description("Verify that sending an update controller attribute message with no thingid header to an existing target does not work.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 0), @Expect(type = TargetPollEvent.class, count = 1)})
    public void updateAttributesWithNoThingId() {
        final String controllerId = TARGET_PREFIX + "updateAttributesWithNoThingId";

        // setup
        registerAndAssertTargetWithExistingTenant(controllerId);
        final DmfAttributeUpdate controllerAttribute = new DmfAttributeUpdate();
        controllerAttribute.getAttributes().put("test1", "testA");
        controllerAttribute.getAttributes().put("test2", "testB");
        final Message createUpdateAttributesMessage = createUpdateAttributesMessage(null, TENANT_EXIST,
                controllerAttribute);
        createUpdateAttributesMessage.getMessageProperties().getHeaders().remove(MessageHeaderKey.THING_ID);

        // test
        getDmfClient().send(createUpdateAttributesMessage);

        // verify
        verifyOneDeadLetterMessage();
        final DmfAttributeUpdate controllerAttributeEmpty = new DmfAttributeUpdate();
        assertUpdateAttributes(controllerId, controllerAttributeEmpty.getAttributes());
    }

    @Test
    @Description("Verify that sending an update controller attribute message with invalid body to an existing target does not work.")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 0), @Expect(type = TargetPollEvent.class, count = 1)})
    public void updateAttributesWithWrongBody() {

        // setup
        final String target = "ControllerAttributeTestTarget";
        registerAndAssertTargetWithExistingTenant(target);
        final DmfAttributeUpdate controllerAttribute = new DmfAttributeUpdate();
        controllerAttribute.getAttributes().put("test1", "testA");
        controllerAttribute.getAttributes().put("test2", "testB");
        final Message createUpdateAttributesMessageWrongBody = createUpdateAttributesMessageWrongBody(target,
                TENANT_EXIST);

        // test
        getDmfClient().send(createUpdateAttributesMessageWrongBody);

        // verify
        verifyOneDeadLetterMessage();
    }

    @Test
    @Description("Verifies that sending an UPDATE_ATTRIBUTES message with invalid attributes is handled correctly.")
    public void updateAttributesWithInvalidValues() {
        // setup
        final String target = "ControllerAttributeTestTarget";
        registerAndAssertTargetWithExistingTenant(target);
        final String keyTooLong = generateRandomStringWithLength(Target.CONTROLLER_ATTRIBUTE_KEY_SIZE + 1);
        final String keyValid = generateRandomStringWithLength(Target.CONTROLLER_ATTRIBUTE_KEY_SIZE);
        final String valueTooLong = generateRandomStringWithLength(Target.CONTROLLER_ATTRIBUTE_VALUE_SIZE + 1);
        final String valueValid = generateRandomStringWithLength(Target.CONTROLLER_ATTRIBUTE_VALUE_SIZE);

        sendUpdateAttributesMessageWithGivenAttributes(target, keyTooLong, valueValid);

        sendUpdateAttributesMessageWithGivenAttributes(target, keyTooLong, valueTooLong);

        sendUpdateAttributesMessageWithGivenAttributes(target, keyValid, valueTooLong);

        verifyNumberOfDeadLetterMessages(3);
    }

    @Test
    @Description("Tests the download_only assignment: tests the handling of a target reporting DOWNLOADED")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 2), @Expect(type = TargetPollEvent.class, count = 1)})
    public void downloadOnlyAssignmentFinishesActionWhenTargetReportsDownloaded() throws IOException {
        // create target
        final String controllerId = TARGET_PREFIX + "registerTargets_1";
        final DistributionSet distributionSet = createTargetAndDistributionSetAndAssign(controllerId, DOWNLOAD_ONLY);

        // verify
        final Message message = assertReplyMessageHeader(EventTopic.DOWNLOAD, controllerId);
        Mockito.verifyZeroInteractions(getDeadletterListener());

        // get actionId from Message
        Long actionId = Long.parseLong(getJsonFieldFromBody(message.getBody(), "actionId"));

        // Send DOWNLOADED message
        sendActionUpdateStatus(new DmfActionUpdateStatus(actionId, DmfActionStatus.DOWNLOADED));
        assertAction(actionId, 1, Status.RUNNING, Status.DOWNLOADED);
        Mockito.verifyZeroInteractions(getDeadletterListener());

        verifyAssignedDsAndInstalledDs(controllerId, distributionSet.getId(), null);
    }

    @Test
    @Description("Tests the download_only assignment: tests the handling of a target reporting FINISHED")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 2), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 3), @Expect(type = TargetPollEvent.class, count = 1)})
    public void downloadOnlyAssignmentAllowsActionStatusUpdatesWhenTargetReportsFinishedAndUpdatesInstalledDS()
            throws IOException {

        // create target
        final String controllerId = TARGET_PREFIX + "registerTargets_1";
        final DistributionSet distributionSet = createTargetAndDistributionSetAndAssign(controllerId, DOWNLOAD_ONLY);

        // verify
        final Message message = assertReplyMessageHeader(EventTopic.DOWNLOAD, controllerId);
        Mockito.verifyZeroInteractions(getDeadletterListener());

        // get actionId from Message
        Long actionId = Long.parseLong(getJsonFieldFromBody(message.getBody(), "actionId"));

        // Send DOWNLOADED message, should result in the action being closed
        sendActionUpdateStatus(new DmfActionUpdateStatus(actionId, DmfActionStatus.DOWNLOADED));
        assertAction(actionId, 1, Status.RUNNING, Status.DOWNLOADED);
        Mockito.verifyZeroInteractions(getDeadletterListener());

        verifyAssignedDsAndInstalledDs(controllerId, distributionSet.getId(), null);

        // Send FINISHED message
        sendActionUpdateStatus(new DmfActionUpdateStatus(actionId, DmfActionStatus.FINISHED));
        assertAction(actionId, 2, Status.RUNNING, Status.DOWNLOADED, Status.FINISHED);
        Mockito.verifyZeroInteractions(getDeadletterListener());

        verifyAssignedDsAndInstalledDs(controllerId, distributionSet.getId(), distributionSet.getId());
    }

    @Test
    @Description("Messages that result into certain exceptions being raised should not be requeued. This message should forwarded to the deadletter queue")
    @ExpectEvents({@Expect(type = TargetCreatedEvent.class, count = 0)})
    public void ignoredExceptionTypesShouldNotBeRequeued() {
        final ControllerManagement mockedControllerManagement = Mockito.mock(ControllerManagement.class);

        final List<Class<? extends RuntimeException>> exceptionsThatShouldNotBeRequeued = Arrays
                .asList(IllegalArgumentException.class, EntityAlreadyExistsException.class);
        final String controllerId = "dummy_target";

        try {
            for (Class<? extends RuntimeException> exceptionClass : exceptionsThatShouldNotBeRequeued) {
                doThrow(exceptionClass).when(mockedControllerManagement)
                        .findOrRegisterTargetIfItDoesNotExist(eq(controllerId), any());

                amqpMessageHandlerService.setControllerManagement(mockedControllerManagement);
                createAndSendThingCreated(controllerId, TENANT_EXIST);
                verifyOneDeadLetterMessage();
                assertThat(targetManagement.getByControllerID(controllerId)).isEmpty();
            }
        } finally {
            amqpMessageHandlerService.setControllerManagement(controllerManagement);
        }
    }

    @Step
    private void verifyAssignedDsAndInstalledDs(final String controllerId, final Long assignedDsId,
            final Long installedDsId) {
        final Optional<Target> target = controllerManagement.getByControllerId(controllerId);
        assertThat(target).isPresent();

        // verify the DS was assigned to the Target
        final DistributionSet assignedDistributionSet = target.get().getAssignedDistributionSet();
        assertThat(assignedDsId).isNotNull();
        assertThat(assignedDistributionSet.getId()).isEqualTo(assignedDsId);

        // verify that the installed DS was not affected
        final DistributionSet installedDistributionSet = target.get().getInstalledDistributionSet();
        if (installedDsId == null) {
            assertThat(installedDistributionSet).isNull();
        } else {
            assertThat(installedDistributionSet.getId()).isEqualTo(installedDsId);
        }
    }

    private void sendUpdateAttributesMessageWithGivenAttributes(final String target, final String key,
            final String value) {
        final DmfAttributeUpdate controllerAttribute = new DmfAttributeUpdate();
        controllerAttribute.getAttributes().put(key, value);
        final Message message = createUpdateAttributesMessage(target, TENANT_EXIST, controllerAttribute);
        getDmfClient().send(message);
    }

    private Long registerTargetAndSendActionStatus(final DmfActionStatus sendActionStatus, final String controllerId) {
        final DistributionSetAssignmentResult assignmentResult = registerTargetAndAssignDistributionSet(controllerId);
        final Long actionId = getFirstAssignedActionId(assignmentResult);
        sendActionUpdateStatus(new DmfActionUpdateStatus(actionId, sendActionStatus));
        return actionId;
    }

    private void sendActionUpdateStatus(final DmfActionUpdateStatus actionStatus) {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, actionStatus);
        getDmfClient().send(eventMessage);
    }

    private void registerTargetAndSendAndAssertUpdateActionStatus(final DmfActionStatus sendActionStatus,
            final Status expectedActionStatus, final String controllerId) {
        final Long actionId = registerTargetAndSendActionStatus(sendActionStatus, controllerId);
        assertAction(actionId, 1, Status.RUNNING, expectedActionStatus);
    }

    private void assertAction(final Long actionId, final int messages, final Status... expectedActionStates) {
        createConditionFactory().await().untilAsserted(() -> {
            try {
                securityRule.runAsPrivileged(() -> {
                    final List<ActionStatus> actionStatusList = deploymentManagement
                            .findActionStatusByAction(PAGE, actionId).getContent();

                    // Check correlation ID
                    final List<String> messagesFromServer = actionStatusList.stream()
                            .flatMap(actionStatus -> deploymentManagement
                                    .findMessagesByActionStatusId(PAGE, actionStatus.getId()).getContent().stream())
                            .filter(Objects::nonNull)
                            .filter(message -> message
                                    .startsWith(RepositoryConstants.SERVER_MESSAGE_PREFIX + "DMF message"))
                            .collect(Collectors.toList());

                    assertThat(messagesFromServer).hasSize(messages)
                            .allMatch(message -> message.endsWith(CORRELATION_ID));

                    final List<Status> status = actionStatusList.stream().map(ActionStatus::getStatus)
                            .collect(Collectors.toList());
                    assertThat(status).containsOnly(expectedActionStates);

                    return null;
                });
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Message createEventMessage(final String tenant, final EventTopic eventTopic, final Object payload) {
        final MessageProperties messageProperties = createMessagePropertiesWithTenant(tenant);
        messageProperties.getHeaders().put(MessageHeaderKey.TYPE, MessageType.EVENT.toString());
        messageProperties.getHeaders().put(MessageHeaderKey.TOPIC, eventTopic.toString());
        messageProperties.setCorrelationId(CORRELATION_ID);

        return createMessage(payload, messageProperties);
    }

    private void sendUpdateAttributeMessage(final String target, final String tenant,
            final DmfAttributeUpdate attributeUpdate) {
        final Message updateMessage = createUpdateAttributesMessage(target, tenant, attributeUpdate);
        getDmfClient().send(updateMessage);
    }

    private int getAuthenticationMessageCount() {
        return Integer.parseInt(getRabbitAdmin().getQueueProperties(amqpProperties.getReceiverQueue())
                .get(RabbitAdmin.QUEUE_MESSAGE_COUNT).toString());
    }

    private void assertEmptyReceiverQueueCount() {
        assertThat(getAuthenticationMessageCount()).isEqualTo(0);
    }

    private void verifyOneDeadLetterMessage() {
        verifyNumberOfDeadLetterMessages(1);
    }

    private void verifyNumberOfDeadLetterMessages(final int numberOfInvocations) {
        assertEmptyReceiverQueueCount();
        createConditionFactory().untilAsserted(() -> Mockito
                .verify(getDeadletterListener(), Mockito.times(numberOfInvocations)).handleMessage(Mockito.any()));
        Mockito.reset(getDeadletterListener());
    }

    private static String getJsonFieldFromBody(final byte[] body, final String fieldName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode node = objectMapper.readValue(new String(body, Charset.defaultCharset()), ObjectNode.class);
        assertThat(node.has(fieldName)).isTrue();
        return node.get(fieldName).asText();
    }
}
