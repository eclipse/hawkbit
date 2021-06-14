/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.integration;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.eclipse.hawkbit.dmf.amqp.api.EventTopic;
import org.eclipse.hawkbit.dmf.amqp.api.MessageHeaderKey;
import org.eclipse.hawkbit.dmf.json.model.DmfActionRequest;
import org.eclipse.hawkbit.dmf.json.model.DmfActionStatus;
import org.eclipse.hawkbit.dmf.json.model.DmfDownloadAndUpdateRequest;
import org.eclipse.hawkbit.dmf.json.model.DmfMultiActionRequest;
import org.eclipse.hawkbit.dmf.json.model.DmfMultiActionRequest.DmfMultiActionElement;
import org.eclipse.hawkbit.dmf.json.model.DmfSoftwareModule;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.event.remote.MultiActionAssignEvent;
import org.eclipse.hawkbit.repository.event.remote.MultiActionCancelEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAttributesRequestedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetPollEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.CancelTargetAssignmentEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TenantConfigurationCreatedEvent;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.RepositoryModelConstants;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.eclipse.hawkbit.repository.test.util.WithSpringAuthorityRule;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.hawkbit.dmf.amqp.api.EventTopic.DOWNLOAD;
import static org.eclipse.hawkbit.dmf.amqp.api.MessageType.EVENT;
import static org.eclipse.hawkbit.repository.model.Action.ActionType.DOWNLOAD_ONLY;

@Feature("Component Tests - Device Management Federation API")
@Story("Amqp Message Dispatcher Service")
public class AmqpMessageDispatcherServiceIntegrationTest extends AbstractAmqpServiceIntegrationTest {
    private static final String TARGET_PREFIX = "Dmf_disp_";

    @Test
    @Description("Verify that a distribution assignment send a download and install message.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void sendDownloadAndInstallStatus() {
        final String controllerId = TARGET_PREFIX + "sendDownloadAndInstallStatus";
        registerTargetAndAssignDistributionSet(controllerId);

        waitUntilTargetHasStatus(controllerId, TargetUpdateStatus.PENDING);
        assertDownloadAndInstallMessage(getDistributionSet().getModules(), controllerId);
    }

    @Test
    @Description("Verify that a distribution assignment sends a download message with window configured but before maintenance window start time.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void sendDownloadStatusBeforeMaintenanceWindowStartTime() {
        final String controllerId = TARGET_PREFIX + "sendDownloadStatusBeforeWindowStartTime";

        registerAndAssertTargetWithExistingTenant(controllerId);
        final DistributionSet distributionSet = testdataFactory.createDistributionSet();
        testdataFactory.addSoftwareModuleMetadata(distributionSet);
        assignDistributionSetWithMaintenanceWindow(distributionSet.getId(), controllerId, getTestSchedule(2),
                getTestDuration(10), getTestTimeZone());

        waitUntilTargetHasStatus(controllerId, TargetUpdateStatus.PENDING);
        assertDownloadMessage(distributionSet.getModules(), controllerId);
    }

    @Test
    @Description("Verify that a distribution assignment sends a download and install message with window configured and during maintenance window start time.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void sendDownloadAndInstallStatusMessageDuringMaintenanceWindow() {
        final String controllerId = TARGET_PREFIX + "sendDAndIStatusMessageDuringWindow";

        registerAndAssertTargetWithExistingTenant(controllerId);
        final DistributionSet distributionSet = testdataFactory.createDistributionSet();
        testdataFactory.addSoftwareModuleMetadata(distributionSet);
        assignDistributionSetWithMaintenanceWindow(distributionSet.getId(), controllerId, getTestSchedule(-5),
                getTestDuration(10), getTestTimeZone());

        waitUntilTargetHasStatus(controllerId, TargetUpdateStatus.PENDING);
        assertDownloadAndInstallMessage(distributionSet.getModules(), controllerId);
    }

    @Test
    @Description("Verify that a distribution assignment multiple times send cancel and assign events with right softwaremodules")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 2),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 2), @Expect(type = ActionUpdatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 12),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2), @Expect(type = TargetPollEvent.class, count = 2) })
    public void assignDistributionSetMultipleTimes() {
        final String controllerId = TARGET_PREFIX + "assignDistributionSetMultipleTimes";

        final DistributionSetAssignmentResult assignmentResult = registerTargetAndAssignDistributionSet(controllerId);
        final DistributionSet distributionSet2 = testdataFactory.createDistributionSet();
        testdataFactory.addSoftwareModuleMetadata(distributionSet2);
        assignDistributionSet(distributionSet2.getId(), controllerId);
        assertDownloadAndInstallMessage(distributionSet2.getModules(), controllerId);
        assertCancelActionMessage(getFirstAssignedActionId(assignmentResult), controllerId);

        createAndSendThingCreated(controllerId, tenantAware.getCurrentTenant());
        waitUntilTargetHasStatus(controllerId, TargetUpdateStatus.PENDING);
        assertCancelActionMessage(getFirstAssignedActionId(assignmentResult), controllerId);
    }

    @Test
    @Description("If multi assignment is enabled multi-action messages are sent.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = MultiActionAssignEvent.class, count = 2),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 0),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 0),
            @Expect(type = ActionCreatedEvent.class, count = 2), @Expect(type = ActionUpdatedEvent.class, count = 0),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2), @Expect(type = TargetPollEvent.class, count = 1),
            @Expect(type = TenantConfigurationCreatedEvent.class, count = 1) })
    public void assignMultipleDsInMultiAssignMode() {
        enableMultiAssignments();
        final String controllerId = TARGET_PREFIX + "assignMultipleDsInMultiAssignMode";
        registerAndAssertTargetWithExistingTenant(controllerId);

        final Long actionId1 = assignNewDsToTarget(controllerId, 450);
        final Entry<Long, EventTopic> action1Install = new SimpleEntry<>(actionId1, EventTopic.DOWNLOAD_AND_INSTALL);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);
        assertLatestMultiActionMessage(controllerId, Collections.singletonList(action1Install));

        final Long actionId2 = assignNewDsToTarget(controllerId, 111);
        final Entry<Long, EventTopic> action2Install = new SimpleEntry<>(actionId2, EventTopic.DOWNLOAD_AND_INSTALL);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);
        assertLatestMultiActionMessage(controllerId, Arrays.asList(action1Install, action2Install));
    }

    @Test
    @Description("Verify payload of multi action messages.")
    public void assertMultiActionMessagePayloads() {
        final int expectedWeightIfNotSet = 1000;
        final int weight1 = 600;
        final String controllerId = UUID.randomUUID().toString();
        registerAndAssertTargetWithExistingTenant(controllerId);
        final DistributionSet ds = testdataFactory.createDistributionSet();
        testdataFactory.addSoftwareModuleMetadata(ds);

        final Long installActionId = makeAssignment(DeploymentManagement.deploymentRequest(controllerId, ds.getId())
                .setActionType(ActionType.FORCED).build()).getAssignedEntity().get(0).getId();
        enableMultiAssignments();
        final Long downloadActionId = makeAssignment(DeploymentManagement.deploymentRequest(controllerId, ds.getId())
                .setActionType(ActionType.DOWNLOAD_ONLY).setWeight(weight1).build()).getAssignedEntity().get(0).getId();
        final Long cancelActionId = makeAssignment(
                DeploymentManagement.deploymentRequest(controllerId, ds.getId()).setWeight(DEFAULT_TEST_WEIGHT).build())
                        .getAssignedEntity().get(0).getId();
        // make sure the latest message in the queue is the one triggered by the
        // cancellation
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.DOWNLOAD_AND_INSTALL, EventTopic.MULTI_ACTION,
                EventTopic.MULTI_ACTION);
        deploymentManagement.cancelAction(cancelActionId);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);

        final List<DmfMultiActionElement> multiActionMessages = getLatestMultiActionMessages(controllerId);
        assertThat(multiActionMessages).hasSize(3);
        final DmfMultiActionElement installMessage = multiActionMessages.stream()
                .filter(message -> message.getTopic().equals(EventTopic.DOWNLOAD_AND_INSTALL)).findFirst().get();
        final DmfMultiActionElement downloadMessage = multiActionMessages.stream()
                .filter(message -> message.getTopic().equals(EventTopic.DOWNLOAD)).findFirst().get();
        final DmfMultiActionElement cancelMessage = multiActionMessages.stream()
                .filter(message -> message.getTopic().equals(EventTopic.CANCEL_DOWNLOAD)).findFirst().get();
        assertThat(installMessage.getWeight()).isEqualTo(expectedWeightIfNotSet);
        assertThat(downloadMessage.getWeight()).isEqualTo(weight1);
        assertThat(cancelMessage.getWeight()).isEqualTo(DEFAULT_TEST_WEIGHT);

        assertThat(installMessage.getAction()).isExactlyInstanceOf(DmfDownloadAndUpdateRequest.class)
                .hasFieldOrPropertyWithValue("actionId", installActionId);
        assertThat(downloadMessage.getAction()).isExactlyInstanceOf(DmfDownloadAndUpdateRequest.class)
                .hasFieldOrPropertyWithValue("actionId", downloadActionId);
        assertThat(cancelMessage.getAction()).isExactlyInstanceOf(DmfActionRequest.class)
                .hasFieldOrPropertyWithValue("actionId", cancelActionId);
        assertDmfDownloadAndUpdateRequest((DmfDownloadAndUpdateRequest) installMessage.getAction(), ds.getModules(),
                controllerId);
        assertDmfDownloadAndUpdateRequest((DmfDownloadAndUpdateRequest) downloadMessage.getAction(), ds.getModules(),
                controllerId);
    }

    private List<DmfMultiActionElement> getLatestMultiActionMessages(final String expectedControllerId) {
        final Message multiactionMessage = replyToListener.getLatestEventMessage(EventTopic.MULTI_ACTION,
                tenantAware.getCurrentTenant());
        assertThat(multiactionMessage.getMessageProperties().getHeaders().get(MessageHeaderKey.THING_ID))
                .isEqualTo(expectedControllerId);
        return ((DmfMultiActionRequest) getDmfClient().getMessageConverter().fromMessage(multiactionMessage))
                .getElements();
    }

    @Test
    @Description("Handle cancelation process of an action in multi assignment mode.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = MultiActionCancelEvent.class, count = 1),
            @Expect(type = MultiActionAssignEvent.class, count = 2),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 0),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 0),
            @Expect(type = ActionCreatedEvent.class, count = 2), @Expect(type = ActionUpdatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2), @Expect(type = TargetPollEvent.class, count = 1),
            @Expect(type = TenantConfigurationCreatedEvent.class, count = 1) })
    public void cancelActionInMultiAssignMode() {
        enableMultiAssignments();
        final String controllerId = TARGET_PREFIX + "cancelActionInMultiAssignMode";
        registerAndAssertTargetWithExistingTenant(controllerId);

        final long actionId1 = assignNewDsToTarget(controllerId, 675);
        final long actionId2 = assignNewDsToTarget(controllerId, 343);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION, EventTopic.MULTI_ACTION);
        deploymentManagement.cancelAction(actionId1);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);

        final Entry<Long, EventTopic> action1Cancel = new SimpleEntry<>(actionId1, EventTopic.CANCEL_DOWNLOAD);
        final Entry<Long, EventTopic> action2Install = new SimpleEntry<>(actionId2, EventTopic.DOWNLOAD_AND_INSTALL);

        assertLatestMultiActionMessage(controllerId, Arrays.asList(action1Cancel, action2Install));
        updateActionViaDmfClient(controllerId, actionId1, DmfActionStatus.CANCELED);

        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);
        assertLatestMultiActionMessage(controllerId, Collections.singletonList(action2Install));
    }

    @Test
    @Description("Handle finishing an action in multi assignment mode.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = MultiActionAssignEvent.class, count = 2),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 0),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 0),
            @Expect(type = ActionCreatedEvent.class, count = 2), @Expect(type = ActionUpdatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 3), @Expect(type = TargetPollEvent.class, count = 1),
            @Expect(type = TenantConfigurationCreatedEvent.class, count = 1) })
    public void finishActionInMultiAssignMode() {
        enableMultiAssignments();
        final String controllerId = TARGET_PREFIX + "finishActionInMultiAssignMode";
        registerAndAssertTargetWithExistingTenant(controllerId);

        final long actionId1 = assignNewDsToTarget(controllerId, 66);
        final long actionId2 = assignNewDsToTarget(controllerId, 767);
        final Entry<Long, EventTopic> action2Install = new SimpleEntry<>(actionId2, EventTopic.DOWNLOAD_AND_INSTALL);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION, EventTopic.MULTI_ACTION);

        updateActionViaDmfClient(controllerId, actionId1, DmfActionStatus.FINISHED);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.REQUEST_ATTRIBUTES_UPDATE, EventTopic.MULTI_ACTION);
        assertRequestAttributesUpdateMessage(controllerId);
        assertLatestMultiActionMessage(controllerId, Collections.singletonList(action2Install));
    }

    @Test
    @Description("If multi assignment is enabled assigning a DS multiple times creates a new action every time.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = MultiActionAssignEvent.class, count = 2),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 0),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 0),
            @Expect(type = ActionCreatedEvent.class, count = 2), @Expect(type = ActionUpdatedEvent.class, count = 0),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 2), @Expect(type = TargetPollEvent.class, count = 1),
            @Expect(type = TenantConfigurationCreatedEvent.class, count = 1) })
    public void assignDsMultipleTimesInMultiAssignMode() {
        enableMultiAssignments();
        final String controllerId = TARGET_PREFIX + "assignDsMultipleTimesInMultiAssignMode";
        registerAndAssertTargetWithExistingTenant(controllerId);
        final Long dsId = testdataFactory.createDistributionSet().getId();

        final Long actionId1 = getFirstAssignedAction(assignDistributionSet(dsId, controllerId, 344)).getId();
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);
        final Long actionId2 = getFirstAssignedAction(assignDistributionSet(dsId, controllerId, 775)).getId();
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);

        final Entry<Long, EventTopic> action1Install = new SimpleEntry<>(actionId1, EventTopic.DOWNLOAD_AND_INSTALL);
        final Entry<Long, EventTopic> action2Install = new SimpleEntry<>(actionId2, EventTopic.DOWNLOAD_AND_INSTALL);
        assertLatestMultiActionMessage(controllerId, Arrays.asList(action1Install, action2Install));
    }

    private void updateActionViaDmfClient(final String controllerId, final long actionId,
            final DmfActionStatus status) {
        createAndSendActionStatusUpdateMessage(controllerId, tenantAware.getCurrentTenant(), actionId, status);
    }

    private Long assignNewDsToTarget(final String controllerId) {
        return assignNewDsToTarget(controllerId, null);
    }

    private Long assignNewDsToTarget(final String controllerId, final Integer weight) {
        final DistributionSet ds = testdataFactory.createDistributionSet();
        final Long actionId = getFirstAssignedActionId(
                assignDistributionSet(ds.getId(), Collections.singletonList(controllerId), ActionType.FORCED,
                        RepositoryModelConstants.NO_FORCE_TIME, weight));
        waitUntilTargetHasStatus(controllerId, TargetUpdateStatus.PENDING);
        return actionId;
    }

    @Test
    @Description("If multi assignment is enabled multiple rollouts with the same DS lead to multiple actions.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = MultiActionAssignEvent.class, count = 2),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 0),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 0),
            @Expect(type = ActionCreatedEvent.class, count = 2), @Expect(type = ActionUpdatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1),
            @Expect(type = RolloutCreatedEvent.class, count = 2), @Expect(type = RolloutUpdatedEvent.class, count = 6),
            @Expect(type = RolloutGroupCreatedEvent.class, count = 2),
            @Expect(type = RolloutGroupUpdatedEvent.class, count = 4),
            @Expect(type = TenantConfigurationCreatedEvent.class, count = 1) })
    public void startRolloutsWithSameDsInMultiAssignMode() {
        enableMultiAssignments();
        final String controllerId = TARGET_PREFIX + "startRolloutsWithSameDsInMultiAssignMode";

        registerAndAssertTargetWithExistingTenant(controllerId);
        final DistributionSet ds = testdataFactory.createDistributionSet();
        final Set<Long> smIds = getSoftwareModuleIds(ds);
        final String filterQuery = "controllerId==" + controllerId;

        createAndStartRollout(ds, filterQuery, 122);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);
        assertLatestMultiActionMessageContainsInstallMessages(controllerId, Collections.singletonList(smIds));

        createAndStartRollout(ds, filterQuery, 43);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);
        assertLatestMultiActionMessageContainsInstallMessages(controllerId, Arrays.asList(smIds, smIds));
    }

    @Test
    @Description("If multi assignment is enabled finishing one rollout does not affect other rollouts of the target.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = MultiActionAssignEvent.class, count = 3), @Expect(type = ActionCreatedEvent.class, count = 3),
            @Expect(type = ActionUpdatedEvent.class, count = 5),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 5), @Expect(type = TargetPollEvent.class, count = 1),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 2),
            @Expect(type = RolloutCreatedEvent.class, count = 3), @Expect(type = RolloutUpdatedEvent.class, count = 9),
            @Expect(type = RolloutGroupCreatedEvent.class, count = 3),
            @Expect(type = RolloutGroupUpdatedEvent.class, count = 6),
            @Expect(type = TenantConfigurationCreatedEvent.class, count = 1) })
    public void startMultipleRolloutsAndFinishInMultiAssignMode() {
        enableMultiAssignments();
        final String controllerId = TARGET_PREFIX + "startMultipleRolloutsAndFinishInMultiAssignMode";

        registerAndAssertTargetWithExistingTenant(controllerId);
        final String filterQuery = "controllerId==" + controllerId;
        final DistributionSet ds1 = testdataFactory.createDistributionSet();
        final Set<Long> smIds1 = getSoftwareModuleIds(ds1);
        final DistributionSet ds2 = testdataFactory.createDistributionSet();
        final Set<Long> smIds2 = getSoftwareModuleIds(ds2);

        createAndStartRollout(ds1, filterQuery, 12);
        createAndStartRollout(ds2, filterQuery, 45);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION, EventTopic.MULTI_ACTION);
        createAndStartRollout(ds1, filterQuery, 65);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.MULTI_ACTION);
        assertLatestMultiActionMessageContainsInstallMessages(controllerId, Arrays.asList(smIds1, smIds2, smIds1));

        final List<Long> installActions = getLatestMultiActionMessageActions(controllerId).stream()
                .filter(entry -> entry.getValue().equals(EventTopic.DOWNLOAD_AND_INSTALL)).map(Entry::getKey)
                .collect(Collectors.toList());

        updateActionViaDmfClient(controllerId, installActions.get(0), DmfActionStatus.FINISHED);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.REQUEST_ATTRIBUTES_UPDATE, EventTopic.MULTI_ACTION);
        assertLatestMultiActionMessageContainsInstallMessages(controllerId, Arrays.asList(smIds2, smIds1));

        updateActionViaDmfClient(controllerId, installActions.get(1), DmfActionStatus.FINISHED);
        waitUntilEventMessagesAreDispatchedToTarget(EventTopic.REQUEST_ATTRIBUTES_UPDATE, EventTopic.MULTI_ACTION);
        assertLatestMultiActionMessageContainsInstallMessages(controllerId, Collections.singletonList(smIds1));
    }

    private Set<Long> getSoftwareModuleIds(final DistributionSet ds) {
        return ds.getModules().stream().map(SoftwareModule::getId).collect(Collectors.toSet());
    }

    private Rollout createAndStartRollout(final DistributionSet ds, final String filterQuery) {
        return createAndStartRollout(ds, filterQuery, null);
    }

    private Rollout createAndStartRollout(final DistributionSet ds, final String filterQuery, final Integer weight) {
        final Rollout rollout = testdataFactory.createRolloutByVariables(UUID.randomUUID().toString(), "", 1,
                filterQuery, ds, "50", "5", ActionType.FORCED, weight);
        rolloutManagement.start(rollout.getId());
        rolloutManagement.handleRollouts();
        return rollout;
    }

    @Test
    @Description("Verify that a cancel assignment send a cancel message.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 1),
            @Expect(type = ActionUpdatedEvent.class, count = 1), @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 2) })
    public void sendCancelStatus() {
        final String controllerId = TARGET_PREFIX + "sendCancelStatus";

        final Long actionId = registerTargetAndCancelActionId(controllerId);

        createAndSendThingCreated(controllerId, tenantAware.getCurrentTenant());
        waitUntilTargetHasStatus(controllerId, TargetUpdateStatus.PENDING);
        assertCancelActionMessage(actionId, controllerId);
    }

    @Test
    @Description("Verify that when a target is deleted a target delete message is send.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 1), @Expect(type = TargetDeletedEvent.class, count = 1) })
    public void sendDeleteMessage() {
        final String controllerId = TARGET_PREFIX + "sendDeleteMessage";

        registerAndAssertTargetWithExistingTenant(controllerId);
        targetManagement.deleteByControllerID(controllerId);
        assertDeleteMessage(controllerId);
    }

    @Test
    @Description("Verify that attribute update is requested after device successfully closed software update.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 2),
            @Expect(type = ActionUpdatedEvent.class, count = 2), @Expect(type = ActionCreatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 4),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 1) })
    public void attributeRequestAfterSuccessfulUpdate() {
        final String controllerId = TARGET_PREFIX + "attributeUpdateRequest";
        registerAndAssertTargetWithExistingTenant(controllerId);

        final long actionId1 = assignNewDsToTarget(controllerId);
        updateActionViaDmfClient(controllerId, actionId1, DmfActionStatus.ERROR);
        waitUntilTargetHasStatus(controllerId, TargetUpdateStatus.ERROR);
        assertRequestAttributesUpdateMessageAbsent();

        final long actionId2 = assignNewDsToTarget(controllerId);
        updateActionViaDmfClient(controllerId, actionId2, DmfActionStatus.FINISHED);
        waitUntilTargetHasStatus(controllerId, TargetUpdateStatus.IN_SYNC);
        assertRequestAttributesUpdateMessage(controllerId);
    }

    @Test
    @Description("Tests the download_only assignment: asserts correct dmf Message topic, and assigned DS")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void downloadOnlyAssignmentSendsDownloadMessageTopic() {
        final String controllerId = TARGET_PREFIX + "registerTargets_1";
        final DistributionSet distributionSet = createTargetAndDistributionSetAndAssign(controllerId, DOWNLOAD_ONLY);

        final Message message = assertReplyMessageHeader(EventTopic.DOWNLOAD, controllerId);
        assertThat(deadletterListener.getMessageCount()).isZero();

        assertThat(message).isNotNull();
        final Map<String, Object> headers = message.getMessageProperties().getHeaders();
        assertThat(headers).containsEntry("thingId", controllerId);
        assertThat(headers).containsEntry("type", EVENT.toString());
        assertThat(headers).containsEntry("topic", DOWNLOAD.toString());

        final Optional<Target> target = controllerManagement.getByControllerId(controllerId);
        assertThat(target).isPresent();

        // verify the DS was assigned to the Target
        final DistributionSet assignedDistributionSet = ((JpaTarget) target.get()).getAssignedDistributionSet();
        assertThat(assignedDistributionSet.getId()).isEqualTo(distributionSet.getId());
    }

    private void waitUntilTargetHasStatus(final String controllerId, final TargetUpdateStatus status) {
        waitUntil(() -> {
            final Optional<Target> findTargetByControllerID = targetManagement.getByControllerID(controllerId);
            return findTargetByControllerID.isPresent()
                    && status.equals(findTargetByControllerID.get().getUpdateStatus());
        });
    }

    private void waitUntil(final Callable<Boolean> callable) {
        createConditionFactory().until(() -> WithSpringAuthorityRule.runAsPrivileged(callable));
    }

    private void assertLatestMultiActionMessageContainsInstallMessages(final String controllerId,
            final List<Set<Long>> smIdsOfActionsExpected) {
        final Message multiactionMessage = replyToListener.getLatestEventMessage(EventTopic.MULTI_ACTION,
                tenantAware.getCurrentTenant());
        assertThat(multiactionMessage.getMessageProperties().getHeaders().get(MessageHeaderKey.THING_ID))
                .isEqualTo(controllerId);
        final DmfMultiActionRequest multiActionRequest = (DmfMultiActionRequest) getDmfClient().getMessageConverter()
                .fromMessage(multiactionMessage);

        final List<Set<Long>> smIdsOfActionsFound = getDownloadAndUpdateRequests(multiActionRequest).stream()
                .map(AmqpMessageDispatcherServiceIntegrationTest::getSmIds).collect(Collectors.toList());
        assertThat(smIdsOfActionsFound).containsExactlyInAnyOrderElementsOf(smIdsOfActionsExpected);
    }

    private void assertLatestMultiActionMessage(final String controllerId,
            final List<Entry<Long, EventTopic>> actionsExpected) {
        final List<Entry<Long, EventTopic>> actionsFromMessage = getLatestMultiActionMessageActions(controllerId);
        assertThat(actionsFromMessage).containsExactlyInAnyOrderElementsOf(actionsExpected);
    }

    private List<Entry<Long, EventTopic>> getLatestMultiActionMessageActions(final String expectedControllerId) {
        final List<DmfMultiActionElement> multiActionRequest = getLatestMultiActionMessages(expectedControllerId);
        return multiActionRequest.stream()
                .map(request -> new SimpleEntry<>(request.getAction().getActionId(), request.getTopic()))
                .collect(Collectors.toList());
    }

    private static Set<Long> getSmIds(final DmfDownloadAndUpdateRequest request) {
        return request.getSoftwareModules().stream().map(DmfSoftwareModule::getModuleId).collect(Collectors.toSet());
    }

    private static List<DmfDownloadAndUpdateRequest> getDownloadAndUpdateRequests(final DmfMultiActionRequest request) {
        return request.getElements().stream()
                .filter(AmqpMessageDispatcherServiceIntegrationTest::isDownloadAndUpdateRequest)
                .map(multiAction -> (DmfDownloadAndUpdateRequest) multiAction.getAction()).collect(Collectors.toList());
    }

    private static boolean isDownloadAndUpdateRequest(final DmfMultiActionElement multiActionElement) {
        return multiActionElement.getTopic().equals(EventTopic.DOWNLOAD)
                || multiActionElement.getTopic().equals(EventTopic.DOWNLOAD_AND_INSTALL);
    }

}
