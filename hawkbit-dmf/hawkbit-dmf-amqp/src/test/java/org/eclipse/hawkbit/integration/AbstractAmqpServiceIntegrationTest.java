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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.assertj.core.api.HamcrestCondition;
import org.eclipse.hawkbit.amqp.DmfApiConfiguration;
import org.eclipse.hawkbit.dmf.amqp.api.AmqpSettings;
import org.eclipse.hawkbit.dmf.amqp.api.EventTopic;
import org.eclipse.hawkbit.dmf.amqp.api.MessageHeaderKey;
import org.eclipse.hawkbit.dmf.amqp.api.MessageType;
import org.eclipse.hawkbit.dmf.json.model.DmfActionRequest;
import org.eclipse.hawkbit.dmf.json.model.DmfActionStatus;
import org.eclipse.hawkbit.dmf.json.model.DmfActionUpdateStatus;
import org.eclipse.hawkbit.dmf.json.model.DmfAttributeUpdate;
import org.eclipse.hawkbit.dmf.json.model.DmfDownloadAndUpdateRequest;
import org.eclipse.hawkbit.dmf.json.model.DmfMetadata;
import org.eclipse.hawkbit.integration.listener.DeadletterListener;
import org.eclipse.hawkbit.integration.listener.ReplyToListener;
import org.eclipse.hawkbit.matcher.SoftwareModuleJsonMatcher;
import org.eclipse.hawkbit.rabbitmq.test.AbstractAmqpIntegrationTest;
import org.eclipse.hawkbit.rabbitmq.test.AmqpTestConfiguration;
import org.eclipse.hawkbit.repository.jpa.RepositoryApplicationConfiguration;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.test.TestConfiguration;
import org.eclipse.hawkbit.repository.test.util.TestdataFactory;
import org.eclipse.hawkbit.repository.test.util.WithSpringAuthorityRule;
import org.eclipse.hawkbit.util.IpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.junit.BrokerRunningSupport;
import org.springframework.amqp.rabbit.test.RabbitListenerTestHarness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration;
import org.springframework.test.context.ContextConfiguration;

import io.qameta.allure.Step;

/**
 *
 * Common class for {@link AmqpMessageHandlerServiceIntegrationTest} and
 * {@link AmqpMessageDispatcherServiceIntegrationTest}.
 */
@ContextConfiguration(classes = { DmfApiConfiguration.class, DmfTestConfiguration.class,
        RepositoryApplicationConfiguration.class, AmqpTestConfiguration.class, TestConfiguration.class,
        TestSupportBinderAutoConfiguration.class })
public abstract class AbstractAmqpServiceIntegrationTest extends AbstractAmqpIntegrationTest {

    protected static final String CREATED_BY = "CONTROLLER_PLUG_AND_PLAY";

    @Autowired
    protected ReplyToListener replyToListener;
    @Autowired
    protected DeadletterListener deadletterListener;
    private DistributionSet distributionSet;

    @BeforeEach
    public void initDmfClient() {
        deadletterListener.reset();
        getDmfClient().setExchange(AmqpSettings.DMF_EXCHANGE);
    }

    private <T> T waitUntilIsPresent(final Callable<Optional<T>> callable) {

        createConditionFactory().until(() -> WithSpringAuthorityRule.runAsPrivileged(() -> callable.call().isPresent()));

        try {
            return WithSpringAuthorityRule.runAsPrivileged(() -> callable.call().get());
        } catch (final Exception e) {
            return null;
        }
    }

    protected void waitUntilEventMessagesAreDispatchedToTarget(final EventTopic... eventTopics) {
        final String tenant = tenantAware.getCurrentTenant();
        createConditionFactory().untilAsserted(() -> assertThat(replyToListener.getLatestEventMessageTopics(tenant))
                .containsExactlyInAnyOrderElementsOf(Arrays.asList(eventTopics)));
        replyToListener.resetLatestEventMessageTopics(tenant);
    }

    protected DistributionSet getDistributionSet() {
        return distributionSet;
    }

    protected DistributionSetAssignmentResult registerTargetAndAssignDistributionSet(final String controllerId) {
        distributionSet = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        testdataFactory.addSoftwareModuleMetadata(distributionSet);

        return registerTargetAndAssignDistributionSet(distributionSet.getId(), TargetUpdateStatus.REGISTERED,
                distributionSet.getModules(), controllerId);
    }

    protected DistributionSetAssignmentResult registerTargetAndAssignDistributionSet(final Long assignDs,
            final TargetUpdateStatus expectedStatus, final Set<SoftwareModule> expectedSoftwareModulesInMessage,
            final String controllerId) {
        registerAndAssertTargetWithExistingTenant(controllerId, 1, expectedStatus, CREATED_BY);

        final DistributionSetAssignmentResult assignmentResult = assignDistributionSet(assignDs, controllerId);
        assertDownloadAndInstallMessage(expectedSoftwareModulesInMessage, controllerId);
        return assignmentResult;
    }

    protected void assertCancelActionMessage(final Long actionId, final String controllerId) {
        final Message replyMessage = assertReplyMessageHeader(EventTopic.CANCEL_DOWNLOAD, controllerId);

        final DmfActionRequest actionUpdateStatus = (DmfActionRequest) getDmfClient().getMessageConverter()
                .fromMessage(replyMessage);
        assertThat(actionUpdateStatus.getActionId()).isEqualTo(actionId);
    }

    protected void assertDeleteMessage(final String target) {

        final String tenant = tenantAware.getCurrentTenant();
        verifyReplyToListener(() -> replyToListener.getTenantDeleteMessages(tenant).size());
        final Message replyMessage = replyToListener.getTenantDeleteMessages(tenant).get(target);
        assertAllTargetsCount(0);
        final Map<String, Object> headers = replyMessage.getMessageProperties().getHeaders();
        assertThat(headers.get(MessageHeaderKey.THING_ID)).isEqualTo(target);
        assertThat(headers.get(MessageHeaderKey.TENANT)).isEqualTo(tenant);
        assertThat(headers.get(MessageHeaderKey.TYPE)).isEqualTo(MessageType.THING_DELETED.toString());
    }

    protected void assertRequestAttributesUpdateMessage(final String target) {
        assertReplyMessageHeader(EventTopic.REQUEST_ATTRIBUTES_UPDATE, target);
    }

    protected void assertRequestAttributesUpdateMessageAbsent() {
        assertThat(replyToListener.getTenantEventMessages(tenantAware.getCurrentTenant()))
                .doesNotContainKey(EventTopic.REQUEST_ATTRIBUTES_UPDATE);
    }

    protected void assertPingReplyMessage(final String correlationId) {

        final String tenant = tenantAware.getCurrentTenant();
        verifyReplyToListener(() -> replyToListener.getTenantPingResponseMessages(tenant).size());
        final Message replyMessage = replyToListener.getTenantPingResponseMessages(tenant).get(correlationId);

        final Map<String, Object> headers = replyMessage.getMessageProperties().getHeaders();

        assertThat(headers.get(MessageHeaderKey.TENANT)).isEqualTo(tenant);
        assertThat(correlationId).isEqualTo(replyMessage.getMessageProperties().getCorrelationId());
        assertThat(headers.get(MessageHeaderKey.TYPE)).isEqualTo(MessageType.PING_RESPONSE.toString());
        assertThat(Long.valueOf(new String(replyMessage.getBody(), StandardCharsets.UTF_8)))
                .isLessThanOrEqualTo(System.currentTimeMillis());

    }

    private void assertAssignmentMessage(final Set<SoftwareModule> dsModules, final String controllerId,
            final EventTopic topic) {
        final Message replyMessage = assertReplyMessageHeader(topic, controllerId);
        assertAllTargetsCount(1);

        final DmfDownloadAndUpdateRequest downloadAndUpdateRequest = (DmfDownloadAndUpdateRequest) getDmfClient()
                .getMessageConverter().fromMessage(replyMessage);

        assertDmfDownloadAndUpdateRequest(downloadAndUpdateRequest, dsModules, controllerId);
    }

    protected void assertDmfDownloadAndUpdateRequest(final DmfDownloadAndUpdateRequest request,
            final Set<SoftwareModule> softwareModules, final String controllerId) {
        assertThat(softwareModules).is(new HamcrestCondition<>(SoftwareModuleJsonMatcher.containsExactly(request.getSoftwareModules())));
        request.getSoftwareModules().forEach(dmfModule -> assertThat(dmfModule.getMetadata()).containsExactly(
                        new DmfMetadata(TestdataFactory.VISIBLE_SM_MD_KEY, TestdataFactory.VISIBLE_SM_MD_VALUE)));
        final Target updatedTarget = waitUntilIsPresent(() -> targetManagement.getByControllerID(controllerId));
        assertThat(updatedTarget.getSecurityToken()).isEqualTo(request.getTargetSecurityToken());
    }

    protected void assertDownloadAndInstallMessage(final Set<SoftwareModule> softwareModules,
            final String controllerId) {
        assertAssignmentMessage(softwareModules, controllerId, EventTopic.DOWNLOAD_AND_INSTALL);

    }

    protected void assertDownloadMessage(final Set<SoftwareModule> dsModules, final String controllerId) {
        assertAssignmentMessage(dsModules, controllerId, EventTopic.DOWNLOAD);
    }

    protected void createAndSendThingCreated(final String target, final String tenant) {
        final Message message = createTargetMessage(target, tenant);
        getDmfClient().send(message);
    }

    protected Message createAndSendPingMessage(final String correlationId, final String tenant) {
        final Message message = createPingMessage(correlationId, tenant);
        getDmfClient().send(message);
        return message;
    }

    protected void verifyReplyToListener(final Callable<Integer> invocations) {
        createConditionFactory().untilAsserted(() -> assertThat(invocations.call()).isGreaterThanOrEqualTo(1));
    }

    protected Long cancelAction(final Long actionId, final String controllerId) {
        deploymentManagement.cancelAction(actionId);
        assertCancelActionMessage(actionId, controllerId);
        return actionId;
    }

    protected Long registerTargetAndCancelActionId(final String controllerId) {
        final DistributionSetAssignmentResult assignmentResult = registerTargetAndAssignDistributionSet(controllerId);
        return cancelAction(getFirstAssignedActionId(assignmentResult), controllerId);
    }

    protected void assertAllTargetsCount(final long expectedTargetsCount) {
        assertThat(targetManagement.count()).isEqualTo(expectedTargetsCount);
    }

    protected Message assertReplyMessageHeader(final EventTopic eventTopic, final String controllerId) {
        final String tenant = tenantAware.getCurrentTenant();
        verifyReplyToListener(() -> replyToListener.getTenantEventMessages(tenant).get(eventTopic).size());

        final Message replyMessage = replyToListener.getLatestEventMessage(eventTopic, tenant);
        assertAllTargetsCount(1);
        assertThat(replyMessage).isNotNull();
        assertThat(replyMessage.getMessageProperties()).isNotNull();
        final Map<String, Object> headers = replyMessage.getMessageProperties().getHeaders();
        assertThat(headers.get(MessageHeaderKey.TOPIC)).isEqualTo(eventTopic.toString());
        assertThat(headers.get(MessageHeaderKey.THING_ID)).isEqualTo(controllerId);
        assertThat(headers.get(MessageHeaderKey.TENANT)).isEqualTo(tenant);
        assertThat(headers.get(MessageHeaderKey.TYPE)).isEqualTo(MessageType.EVENT.toString());
        return replyMessage;
    }

    @Step
    protected void registerAndAssertTargetWithExistingTenant(final String controllerId) {
        registerAndAssertTargetWithExistingTenant(controllerId, 1);
    }

    protected void registerAndAssertTargetWithExistingTenant(final String target,
            final int existingTargetsAfterCreation) {

        registerAndAssertTargetWithExistingTenant(target, existingTargetsAfterCreation, TargetUpdateStatus.REGISTERED,
                CREATED_BY);

    }

    protected void registerAndAssertTargetWithExistingTenant(final String target,
            final int existingTargetsAfterCreation, final TargetUpdateStatus expectedTargetStatus,
            final String createdBy) {
        createAndSendThingCreated(target, tenantAware.getCurrentTenant());
        final Target registerdTarget = waitUntilIsPresent(() -> targetManagement.getByControllerID(target));
        assertAllTargetsCount(existingTargetsAfterCreation);
        assertTarget(registerdTarget, expectedTargetStatus, createdBy);
    }

    protected void registerSameTargetAndAssertBasedOnVersion(final String controllerId,
            final int existingTargetsAfterCreation, final TargetUpdateStatus expectedTargetStatus) {
        final int version = controllerManagement.getByControllerId(controllerId).get().getOptLockRevision();
        createAndSendThingCreated(controllerId, tenantAware.getCurrentTenant());
        final Target registeredTarget = waitUntilIsPresent(() -> findTargetBasedOnNewVersion(controllerId, version));
        assertAllTargetsCount(existingTargetsAfterCreation);
        assertThat(registeredTarget.getUpdateStatus()).isEqualTo(expectedTargetStatus);
    }

    private Optional<Target> findTargetBasedOnNewVersion(final String controllerId, final int version) {
        final Optional<Target> target2 = controllerManagement.getByControllerId(controllerId);
        if (version < target2.get().getOptLockRevision()) {
            return target2;
        }
        return Optional.empty();
    }

    private void assertTarget(final Target target, final TargetUpdateStatus updateStatus, final String createdBy) {
        assertThat(target.getTenant()).isEqualTo(tenantAware.getCurrentTenant());
        assertThat(target.getDescription()).contains("Plug and Play");
        assertThat(target.getDescription()).contains(target.getControllerId());
        assertThat(target.getCreatedBy()).isEqualTo(createdBy);
        assertThat(target.getUpdateStatus()).isEqualTo(updateStatus);
        assertThat(target.getAddress()).isEqualTo(
                IpUtil.createAmqpUri(getVirtualHost(), DmfTestConfiguration.REPLY_TO_EXCHANGE));
    }

    protected Message createTargetMessage(final String target, final String tenant) {
        final MessageProperties messageProperties = createMessagePropertiesWithTenant(tenant);
        messageProperties.getHeaders().put(MessageHeaderKey.THING_ID, target);
        messageProperties.getHeaders().put(MessageHeaderKey.TYPE, MessageType.THING_CREATED.toString());
        messageProperties.setReplyTo(DmfTestConfiguration.REPLY_TO_EXCHANGE);

        return createMessage(null, messageProperties);
    }

    protected Message createPingMessage(final String correlationId, final String tenant) {
        final MessageProperties messageProperties = createMessagePropertiesWithTenant(tenant);
        messageProperties.getHeaders().put(MessageHeaderKey.TYPE, MessageType.PING.toString());
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setReplyTo(DmfTestConfiguration.REPLY_TO_EXCHANGE);

        return createMessage(null, messageProperties);
    }

    protected void createAndSendActionStatusUpdateMessage(final String target, final String tenant, final long actionId,
            final DmfActionStatus status) {
        final MessageProperties messageProperties = createMessagePropertiesWithTenant(tenant);
        messageProperties.getHeaders().put(MessageHeaderKey.THING_ID, target);
        messageProperties.getHeaders().put(MessageHeaderKey.TYPE, MessageType.EVENT.toString());
        messageProperties.getHeaders().put(MessageHeaderKey.TOPIC, EventTopic.UPDATE_ACTION_STATUS.toString());

        final DmfActionUpdateStatus dmfActionUpdateStatus = new DmfActionUpdateStatus(actionId, status);
        getDmfClient().send(createMessage(dmfActionUpdateStatus, messageProperties));
    }

    protected MessageProperties createMessagePropertiesWithTenant(final String tenant) {
        final MessageProperties messageProperties = new MessageProperties();
        messageProperties.getHeaders().put(MessageHeaderKey.TENANT, tenant);
        return messageProperties;
    }

    protected Message createUpdateAttributesMessage(final String target, final String tenant,
            final DmfAttributeUpdate attributeUpdate) {
        final MessageProperties messageProperties = createMessagePropertiesWithTenant(tenant);
        messageProperties.getHeaders().put(MessageHeaderKey.THING_ID, target);
        messageProperties.getHeaders().put(MessageHeaderKey.TYPE, MessageType.EVENT.toString());
        messageProperties.getHeaders().put(MessageHeaderKey.TOPIC, EventTopic.UPDATE_ATTRIBUTES.toString());

        return createMessage(attributeUpdate, messageProperties);

    }

    protected Message createUpdateAttributesMessageWrongBody(final String target, final String tenant) {
        final MessageProperties messageProperties = createMessagePropertiesWithTenant(tenant);
        messageProperties.getHeaders().put(MessageHeaderKey.THING_ID, target);
        messageProperties.getHeaders().put(MessageHeaderKey.TYPE, MessageType.EVENT.toString());
        messageProperties.getHeaders().put(MessageHeaderKey.TOPIC, EventTopic.UPDATE_ATTRIBUTES.toString());

        return createMessage("wrongbody", messageProperties);

    }

    @Step
    protected void assertUpdateAttributes(final String controllerId, final Map<String, String> attributes) {
        waitUntilIsPresent(() -> controllerManagement.getByControllerId(controllerId));

        createConditionFactory().untilAsserted(() -> {
            try {
                final Map<String, String> controllerAttributes = WithSpringAuthorityRule
                        .runAsPrivileged(() -> targetManagement.getControllerAttributes(controllerId));
                assertThat(controllerAttributes.size()).isEqualTo(attributes.size());
                attributes.forEach((k, v) -> assertKeyValueInMap(k, v, controllerAttributes));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void assertKeyValueInMap(final String key, final String value,
            final Map<String, String> controllerAttributes) {
        assertThat(controllerAttributes.containsKey(key)).isTrue();
        assertThat(controllerAttributes.get(key)).isEqualTo(value);
    }

    @Override
    protected String getExchange() {
        return AmqpSettings.DMF_EXCHANGE;
    }

    @Step
    protected DistributionSet createTargetAndDistributionSetAndAssign(final String controllerId,
            final Action.ActionType actionType) {
        registerAndAssertTargetWithExistingTenant(controllerId);

        final DistributionSet distributionSet = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        testdataFactory.addSoftwareModuleMetadata(distributionSet);

        assignDistributionSet(distributionSet.getId(), controllerId, actionType);
        return distributionSet;
    }

}
