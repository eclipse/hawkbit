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

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.dmf.amqp.api.EventTopic;
import org.eclipse.hawkbit.dmf.amqp.api.MessageHeaderKey;
import org.eclipse.hawkbit.dmf.amqp.api.MessageType;
import org.eclipse.hawkbit.dmf.json.model.ActionStatus;
import org.eclipse.hawkbit.dmf.json.model.ActionUpdateStatus;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetPollEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.CancelTargetAssignmentEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import ru.yandex.qatools.allure.annotations.Description;
import ru.yandex.qatools.allure.annotations.Features;
import ru.yandex.qatools.allure.annotations.Stories;

@Features("Component Tests - Device Management Federation API")
@Stories("Amqp Message Handler Service")
// TODO UPDATE_ATTRIBUTES
public class AmqpMessageHandlerServiceIntegrationTest extends AmqpServiceIntegrationTest {

    @Test
    @Description("Tests register target")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetPollEvent.class, count = 3) })
    public void registerTargets() {
        registerAndAssertTargetWithExistingTenant(REGISTER_TARGET, 1);

        final String target2 = "Target2";
        registerAndAssertTargetWithExistingTenant(target2, 2);

        // already registered target should not increase targets
        // TODO 2 sekunden Micha weiß Lösung! Erst selber überlegen
        registerAndAssertTargetWithExistingTenant(REGISTER_TARGET, 2);

        Mockito.verifyZeroInteractions(getDeadletterListener());

    }

    @Test
    @Description("Tests register invalid target withy empty controller id. Tests register invalid target with null controller id")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void registerEmptyTarget() {
        createAndSendTarget("", TENANT_EXIST);
        assertAllTargetsCount(0);
        verifyDeadLetterMessages(1);

    }

    @Test
    @Description("Tests register invalid target with whitspace controller id. Tests register invalid target with null controller id")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void registerWhitespaceTarget() {
        createAndSendTarget("Invalid Invalid", TENANT_EXIST);
        assertAllTargetsCount(0);
        verifyDeadLetterMessages(1);

    }

    @Test
    @Description("Tests register invalid target with null controller id. Tests register invalid target with null controller id")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void registerInvalidNullTargets() {
        createAndSendTarget(null, TENANT_EXIST);
        assertAllTargetsCount(0);
        verifyDeadLetterMessages(1);

    }

    @Test
    @Description("Tests not allowed content-type in message. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void wrongContentType() {
        final Message createTargetMessage = createTargetMessage(REGISTER_TARGET, TENANT_EXIST);
        createTargetMessage.getMessageProperties().setContentType("WrongContentType");
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests null reply to property in message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void missingReplyToProperty() {
        final Message createTargetMessage = createTargetMessage(REGISTER_TARGET, TENANT_EXIST);
        createTargetMessage.getMessageProperties().setReplyTo(null);
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests missing reply to property in message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void emptyReplyToProperty() {
        final Message createTargetMessage = createTargetMessage(REGISTER_TARGET, TENANT_EXIST);
        createTargetMessage.getMessageProperties().setReplyTo("");
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests missing thing id property in message. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void missingThingIdProperty() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().remove(MessageHeaderKey.THING_ID);
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests null thing id property in message. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void nullThingIdProperty() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests missing tenant message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void missingTenantHeader() {
        final Message createTargetMessage = createTargetMessage(REGISTER_TARGET, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().remove(MessageHeaderKey.TENANT);
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests null tenant message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void nullTenantHeader() {
        final Message createTargetMessage = createTargetMessage(REGISTER_TARGET, null);
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests empty tenant message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void emptyTenantHeader() {
        final Message createTargetMessage = createTargetMessage(REGISTER_TARGET, "");
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests tenant not exist. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void tenantNotExist() {
        final Message createTargetMessage = createTargetMessage(REGISTER_TARGET, "TenantNotExist");
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertThat(systemManagement.findTenants()).hasSize(1);
    }

    @Test
    @Description("Tests missing type message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void missingTypeHeader() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().remove(MessageHeaderKey.TYPE);
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests null type message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void nullTypeHeader() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TYPE, null);
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests empty type message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void emptyTypeHeader() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TYPE, "");
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests invalid type message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void invalidTypeHeader() {
        final Message createTargetMessage = createTargetMessage(null, TENANT_EXIST);
        createTargetMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TYPE, "NotExist");
        getDmfClient().send(createTargetMessage);

        verifyDeadLetterMessages(1);
        assertAllTargetsCount(0);
    }

    @Test
    @Description("Tests null topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void nullTopicHeader() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        eventMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TOPIC, null);
        getDmfClient().send(eventMessage);

        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Tests null topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void emptyTopicHeader() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        eventMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TOPIC, "");
        getDmfClient().send(eventMessage);

        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Tests null topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void invalidTopicHeader() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        eventMessage.getMessageProperties().getHeaders().put(MessageHeaderKey.TOPIC, "NotExist");
        getDmfClient().send(eventMessage);

        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Tests missing topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void missingTopicHeader() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        eventMessage.getMessageProperties().getHeaders().remove(MessageHeaderKey.TOPIC);
        getDmfClient().send(eventMessage);

        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Tests invalid null message content. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void updateActionStatusWithNullContent() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, null);
        getDmfClient().send(eventMessage);
        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Tests invalid empty message content. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void updateActionStatusWithEmptyContent() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, "");
        getDmfClient().send(eventMessage);
        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Tests invalid json message content. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void updateActionStatusWithInvalidJsonContent() {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS,
                "Invalid Content");
        getDmfClient().send(eventMessage);
        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Tests invalid topic message header. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void updateActionStatusWithInvalidActionId() {
        final ActionUpdateStatus actionUpdateStatus = new ActionUpdateStatus(1L, ActionStatus.RUNNING);
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS,
                actionUpdateStatus);
        getDmfClient().send(eventMessage);
        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Tests register target and cancel a assignment")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 2), @Expect(type = TargetPollEvent.class, count = 1) })
    public void finishActionStatus() {
        registerTargetAndSendAndAssertUpdateActionStatus(ActionStatus.FINISHED, Status.FINISHED);
    }

    @Test
    @Description("Register a target and send a update action status (running). Verfiy if the updated action status is correct.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 0), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void runningActionStatus() {
        registerTargetAndSendAndAssertUpdateActionStatus(ActionStatus.RUNNING, Status.RUNNING);
    }

    @Test
    @Description("Register a target and send a update action status (download). Verfiy if the updated action status is correct.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void downloadActionStatus() {
        registerTargetAndSendAndAssertUpdateActionStatus(ActionStatus.DOWNLOAD, Status.DOWNLOAD);
    }

    @Test
    @Description("Register a target and send a update action status (error). Verfiy if the updated action status is correct.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 2), @Expect(type = TargetPollEvent.class, count = 1) })
    public void errorActionStatus() {
        registerTargetAndSendAndAssertUpdateActionStatus(ActionStatus.ERROR, Status.ERROR);
    }

    @Test
    @Description("Register a target and send a update action status (warning). Verfiy if the updated action status is correct.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void warningActionStatus() {
        registerTargetAndSendAndAssertUpdateActionStatus(ActionStatus.WARNING, Status.WARNING);
    }

    @Test
    @Description("Register a target and send a update action status (retrieved). Verfiy if the updated action status is correct.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void retrievedActionStatus() {
        registerTargetAndSendAndAssertUpdateActionStatus(ActionStatus.RETRIEVED, Status.RETRIEVED);
    }

    @Test
    @Description("Register a target and send a invalid update action status (cancel). This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void cancelNotAllowActionStatus() {
        registerTargetAndSendActionStatus(ActionStatus.CANCELED);
        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("TODO: Verfiy update the action status to canceld, if the current status is not a canceling state")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void cancelActionStatus() {
        // TODO: AmqpMessageHandlerService komme nicht in zeile 255 und danach
        // 189
        // final DistributionSet distributionSet =
        // registerTargetAndAssignDistributionSet();
        // assertDownloadAndInstallMessage(distributionSet);
        // registerTargetAndAssignDistributionSet(TargetUpdateStatus.PENDING);
        // final assertCancel
        // Long actionId = getReplyAction();
        // actionId = controllerManagament
        // .addUpdateActionStatus(entityFactory.actionStatus().create(actionId).status(Action.Status.CANCELING))
        // .getId();
        //
        // assertThat(controllerManagament.findOldestActiveActionByTarget(REGISTER_TARGET).isPresent()).isTrue();
        // sendActionUpdateStatus(new ActionUpdateStatus(actionId,
        // ActionStatus.CANCELED));
        // assertAction(actionId, Status.RUNNING, Status.CANCELING,
        // Status.CANCELED);
    }

    @Test
    @Description("Register a target and send a invalid update action status (canceled). The current status (pending) is not a canceling state. This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void actionNotExists() {
        final Long actionId = registerTargetAndCancelActionId();
        final Long actionNotExist = actionId + 1;

        sendActionUpdateStatus(new ActionUpdateStatus(actionNotExist, ActionStatus.CANCELED));
        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Register a target and send a invalid update action status (cancel_rejected). This message should forwarded to the deadletter queue")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void canceledRejectedNotAllowActionStatus() {
        registerTargetAndSendActionStatus(ActionStatus.CANCEL_REJECTED);
        verifyDeadLetterMessages(1);
    }

    @Test
    @Description("Register a target and send a valid update action status (cancel_rejected). Verfiy if the updated action status is correct.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void canceledRejectedActionStatus() {
        final Long actionId = registerTargetAndCancelActionId();

        sendActionUpdateStatus(new ActionUpdateStatus(actionId, ActionStatus.CANCEL_REJECTED));
        assertAction(actionId, Status.RUNNING, Status.CANCELING, Status.CANCEL_REJECTED);
    }

    private Long registerTargetAndSendActionStatus(ActionStatus sendActionStatus) {
        final DistributionSetAssignmentResult assignmentResult = registerTargetAndAssignDistributionSet();
        final Long actionId = assignmentResult.getActions().get(0);
        sendActionUpdateStatus(new ActionUpdateStatus(actionId, sendActionStatus));
        return actionId;
    }

    private void sendActionUpdateStatus(ActionUpdateStatus actionStatus) {
        final Message eventMessage = createEventMessage(TENANT_EXIST, EventTopic.UPDATE_ACTION_STATUS, actionStatus);
        getDmfClient().send(eventMessage);
    }

    private void registerTargetAndSendAndAssertUpdateActionStatus(ActionStatus sendActionStatus,
            Status expectedActionStatus) {
        final Long actionId = registerTargetAndSendActionStatus(sendActionStatus);
        assertAction(actionId, Status.RUNNING, expectedActionStatus);
    }

    private void assertAction(final Long actionId, Status... expectedActionStates) {
        final Action action = waitUntilIsPresent(() -> controllerManagement.findActionWithDetails(actionId));
        final List<Status> status = action.getActionStatus().stream().map(actionStatus -> actionStatus.getStatus())
                .collect(Collectors.toList());
        assertThat(status).containsOnly(expectedActionStates);
    }

    private Message createEventMessage(String tenant, EventTopic eventTopic, Object payload) {
        final MessageProperties messageProperties = createMessagePropertiesWithTenant(tenant);
        messageProperties.getHeaders().put(MessageHeaderKey.TYPE, MessageType.EVENT.toString());
        messageProperties.getHeaders().put(MessageHeaderKey.TOPIC, eventTopic.toString());

        return createMessage(payload, messageProperties);
    }

}
