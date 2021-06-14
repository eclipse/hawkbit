/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.eclipse.hawkbit.repository.OffsetBasedPageRequest;
import org.eclipse.hawkbit.repository.builder.RolloutCreate;
import org.eclipse.hawkbit.repository.builder.RolloutGroupCreate;
import org.eclipse.hawkbit.repository.event.remote.RolloutDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.RolloutGroupDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.EntityAlreadyExistsException;
import org.eclipse.hawkbit.repository.exception.EntityReadOnlyException;
import org.eclipse.hawkbit.repository.exception.MultiAssignmentIsNotEnabledException;
import org.eclipse.hawkbit.repository.exception.RolloutIllegalStateException;
import org.eclipse.hawkbit.repository.jpa.model.JpaAction;
import org.eclipse.hawkbit.repository.jpa.model.JpaRollout;
import org.eclipse.hawkbit.repository.jpa.model.JpaRolloutGroup;
import org.eclipse.hawkbit.repository.jpa.utils.MultipleInvokeHelper;
import org.eclipse.hawkbit.repository.jpa.utils.SuccessCondition;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupErrorAction;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupErrorCondition;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupSuccessCondition;
import org.eclipse.hawkbit.repository.model.RolloutGroupConditionBuilder;
import org.eclipse.hawkbit.repository.model.RolloutGroupConditions;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;

/**
 * Junit tests for RolloutManagment.
 */
@Feature("Component Tests - Repository")
@Story("Rollout Management")
public class RolloutManagementTest extends AbstractJpaIntegrationTest {

    @BeforeEach
    public void reset() {
        this.approvalStrategy.setApprovalNeeded(false);
    }

    @Test
    @Description("Verifies that a running action with distribution-set (A) is not canceled by a rollout which tries to also assign a distribution-set (A)")
    public void rolloutShouldNotCancelRunningActionWithTheSameDistributionSet() {
        // manually assign distribution set to target
        final String knownControllerId = "controller12345";
        final DistributionSet knownDistributionSet = testdataFactory.createDistributionSet();
        testdataFactory.createTarget(knownControllerId);
        final DistributionSetAssignmentResult assignmentResult = assignDistributionSet(knownDistributionSet.getId(),
                knownControllerId);
        final Long manuallyAssignedActionId = getFirstAssignedActionId(assignmentResult);

        // create rollout with the same distribution set already assigned
        // start rollout
        final Rollout rollout = testdataFactory.createRolloutByVariables("rolloutNotCancelRunningAction", "description",
                1, "name==*", knownDistributionSet, "50", "5");
        rolloutManagement.start(rollout.getId());
        rolloutManagement.handleRollouts();

        // verify that manually created action is still running and action
        // created from rollout is finished
        final List<Action> actionsByKnownTarget = deploymentManagement.findActionsByTarget(knownControllerId, PAGE)
                .getContent();
        // should be 2 actions, one manually and one from the rollout
        assertThat(actionsByKnownTarget).hasSize(2);
        // verify that manually assigned action is still running
        assertThat(deploymentManagement.findAction(manuallyAssignedActionId).get().getStatus())
                .isEqualTo(Status.RUNNING);
        // verify that rollout management created action is finished because is
        // duplicate assignment
        final Action rolloutCreatedAction = actionsByKnownTarget.stream()
                .filter(action -> !action.getId().equals(manuallyAssignedActionId)).findAny().get();
        assertThat(rolloutCreatedAction.getStatus()).isEqualTo(Status.FINISHED);
    }

    @Test
    @Description("Verifies that a running action is auto canceled by a rollout which assigns another distribution-set.")
    public void rolloutAssignesNewDistributionSetAndAutoCloseActiveActions() {
        tenantConfigurationManagement
                .addOrUpdateConfiguration(TenantConfigurationKey.REPOSITORY_ACTIONS_AUTOCLOSE_ENABLED, true);

        try {
            // manually assign distribution set to target
            final String knownControllerId = "controller12345";
            final DistributionSet firstDistributionSet = testdataFactory.createDistributionSet();
            final DistributionSet secondDistributionSet = testdataFactory.createDistributionSet("second");
            testdataFactory.createTarget(knownControllerId);
            final DistributionSetAssignmentResult assignmentResult = assignDistributionSet(firstDistributionSet.getId(),
                    knownControllerId);
            final Long manuallyAssignedActionId = getFirstAssignedActionId(assignmentResult);

            // create rollout with the same distribution set already assigned
            // start rollout
            final Rollout rollout = testdataFactory.createRolloutByVariables("rolloutNotCancelRunningAction",
                    "description", 1, "name==*", secondDistributionSet, "50", "5");
            rolloutManagement.start(rollout.getId());
            rolloutManagement.handleRollouts();

            // verify that manually created action is canceled and action
            // created from rollout is running
            final List<Action> actionsByKnownTarget = deploymentManagement.findActionsByTarget(knownControllerId, PAGE)
                    .getContent();
            // should be 2 actions, one manually and one from the rollout
            assertThat(actionsByKnownTarget).hasSize(2);
            // verify that manually assigned action is still running
            assertThat(deploymentManagement.findAction(manuallyAssignedActionId).get().getStatus())
                    .isEqualTo(Status.CANCELED);
            // verify that rollout management created action is running
            final Action rolloutCreatedAction = actionsByKnownTarget.stream()
                    .filter(action -> !action.getId().equals(manuallyAssignedActionId)).findAny().get();
            assertThat(rolloutCreatedAction.getStatus()).isEqualTo(Status.RUNNING);
        } finally {
            tenantConfigurationManagement
                    .addOrUpdateConfiguration(TenantConfigurationKey.REPOSITORY_ACTIONS_AUTOCLOSE_ENABLED, false);
        }

    }

    @Test
    @Description("Verifies that management get access reacts as specfied on calls for non existing entities by means "
            + "of Optional not present.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void nonExistingEntityAccessReturnsNotPresent() {
        assertThat(rolloutManagement.get(NOT_EXIST_IDL)).isNotPresent();
        assertThat(rolloutManagement.getByName(NOT_EXIST_ID)).isNotPresent();
        assertThat(rolloutManagement.getWithDetailedStatus(NOT_EXIST_IDL)).isNotPresent();
    }

    @Test
    @Description("Verifies that management queries react as specfied on calls for non existing entities "
            + " by means of throwing EntityNotFoundException.")
    @ExpectEvents({ @Expect(type = RolloutDeletedEvent.class, count = 0),
            @Expect(type = RolloutGroupCreatedEvent.class, count = 10),
            @Expect(type = RolloutGroupUpdatedEvent.class, count = 10),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = RolloutUpdatedEvent.class, count = 1), @Expect(type = RolloutCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 10) })
    public void entityQueriesReferringToNotExistingEntitiesThrowsException() {
        testdataFactory.createRollout("xxx");

        verifyThrownExceptionBy(() -> rolloutManagement.delete(NOT_EXIST_IDL), "Rollout");

        verifyThrownExceptionBy(() -> rolloutManagement.pauseRollout(NOT_EXIST_IDL), "Rollout");
        verifyThrownExceptionBy(() -> rolloutManagement.resumeRollout(NOT_EXIST_IDL), "Rollout");
        verifyThrownExceptionBy(() -> rolloutManagement.start(NOT_EXIST_IDL), "Rollout");

        verifyThrownExceptionBy(() -> rolloutManagement.update(entityFactory.rollout().update(NOT_EXIST_IDL)),
                "Rollout");
    }

    @Test
    @Description("Verifying that the rollout is created correctly, executing the filter and split up the targets in the correct group size.")
    public void creatingRolloutIsCorrectPersisted() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createSimpleTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, successCondition, errorCondition);

        // verify the split of the target and targetGroup
        final Page<RolloutGroup> rolloutGroups = rolloutGroupManagement.findByRollout(PAGE, createdRollout.getId());
        // we have total of #amountTargetsForRollout in rollouts splitted in
        // group size #groupSize
        assertThat(rolloutGroups).hasSize(amountGroups);
    }

    @Test
    @Description("Verifying that when the rollout is started the actions for all targets in the rollout is created and the state of the first group is running as well as the corresponding actions")
    public void startRolloutSetFirstGroupAndActionsInRunningStateAndOthersInScheduleState() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        // verify first group is running
        final RolloutGroup firstGroup = rolloutGroupManagement
                .findByRollout(new OffsetBasedPageRequest(0, 1, Sort.by(Direction.ASC, "id")), createdRollout.getId())
                .getContent().get(0);
        assertThat(firstGroup.getStatus()).isEqualTo(RolloutGroupStatus.RUNNING);

        // verify other groups are scheduled
        final List<RolloutGroup> scheduledGroups = rolloutGroupManagement.findByRollout(
                new OffsetBasedPageRequest(1, 100, Sort.by(Direction.ASC, "id")), createdRollout.getId()).getContent();
        scheduledGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.SCHEDULED)
                .as("group which should be in scheduled state is in " + group.getStatus() + " state"));
        // verify that the first group actions has been started and are in state
        // running
        final List<Action> runningActions = findActionsByRolloutAndStatus(createdRollout, Status.RUNNING);
        assertThat(runningActions).hasSize(amountTargetsForRollout / amountGroups);
        assertThat(runningActions).as("Created actions are initiated by rollout creator")
                .allMatch(a -> a.getInitiatedBy().equals(createdRollout.getCreatedBy()));
        // the rest targets are only scheduled
        assertThat(findActionsByRolloutAndStatus(createdRollout, Status.SCHEDULED))
                .hasSize(amountTargetsForRollout - (amountTargetsForRollout / amountGroups));
    }

    @Test
    @Description("Verifying that a finish condition of a group is hit the next group of the rollout is also started")
    public void checkRunningRolloutsDoesNotStartNextGroupIfFinishConditionIsNotHit() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        final List<Action> runningActions = findActionsByRolloutAndStatus(createdRollout, Status.RUNNING);
        // finish one action should be sufficient due the finish condition is at
        // 50%
        final JpaAction action = (JpaAction) runningActions.get(0);
        controllerManagement
                .addUpdateActionStatus(entityFactory.actionStatus().create(action.getId()).status(Status.FINISHED));

        // check running rollouts again, now the finish condition should be hit
        // and should start the next group
        rolloutManagement.handleRollouts();

        // verify that now the first and the second group are in running state
        final List<RolloutGroup> runningRolloutGroups = rolloutGroupManagement
                .findByRollout(new OffsetBasedPageRequest(0, 2, Sort.by(Direction.ASC, "id")), createdRollout.getId())
                .getContent();
        runningRolloutGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.RUNNING)
                .as("group should be in running state because it should be started but it is in " + group.getStatus()
                        + " state"));

        // verify that the other groups are still in schedule state
        final List<RolloutGroup> scheduledRolloutGroups = rolloutGroupManagement
                .findByRollout(new OffsetBasedPageRequest(2, 10, Sort.by(Direction.ASC, "id")), createdRollout.getId())
                .getContent();
        scheduledRolloutGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.SCHEDULED)
                .as("group should be in scheduled state because it should not be started but it is in "
                        + group.getStatus() + " state"));
    }

    @Test
    // @Title("Deleting targets of a rollout")
    @Description("Verfiying that next group is started when targets of the group have been deleted.")
    public void checkRunningRolloutsStartsNextGroupIfTargetsDeleted() {

        final int amountTargetsForRollout = 15;
        final int amountOtherTargets = 0;
        final int amountGroups = 3;
        final String successCondition = "100";
        final String errorCondition = "80";
        final Rollout createdRollout = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        finishActionAndDeleteTargetsOfFirstRunningGroup(createdRollout);

        checkSecondGroupStatusIsRunning(createdRollout);

        finishActionAndDeleteTargetsOfSecondRunningGroup(createdRollout);

        deleteAllTargetsFromThirdGroup(createdRollout);

        verifyRolloutAndAllGroupsAreFinished(createdRollout);

    }

    @Step("Create a rollout by the given parameter and start it.")
    private Rollout createAndStartRollout(final int amountTargetsForRollout, final int amountOtherTargets,
            final int amountGroups, final String successCondition, final String errorCondition) {

        final Rollout createdRollout = createSimpleTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, successCondition, errorCondition);
        rolloutManagement.start(createdRollout.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        return rolloutManagement.get(createdRollout.getId()).get();
    }

    @Step("Finish three actions of the rollout group and delete two targets")
    private void finishActionAndDeleteTargetsOfFirstRunningGroup(final Rollout createdRollout) {
        // finish group one by finishing targets and deleting targets
        final Slice<JpaAction> runningActionsSlice = actionRepository.findByRolloutIdAndStatus(PAGE,
                createdRollout.getId(), Status.RUNNING);
        final List<JpaAction> runningActions = runningActionsSlice.getContent();
        finishAction(runningActions.get(0));
        finishAction(runningActions.get(1));
        finishAction(runningActions.get(2));
        targetManagement.delete(
                Arrays.asList(runningActions.get(3).getTarget().getId(), runningActions.get(4).getTarget().getId()));
    }

    @Step("Check the status of the rollout groups, second group should be in running status")
    private void checkSecondGroupStatusIsRunning(final Rollout createdRollout) {
        rolloutManagement.handleRollouts();
        final List<RolloutGroup> runningRolloutGroups = rolloutGroupManagement
                .findByRollout(new OffsetBasedPageRequest(0, 10, Sort.by(Direction.ASC, "id")), createdRollout.getId())
                .getContent();
        assertThat(runningRolloutGroups.get(0).getStatus()).isEqualTo(RolloutGroupStatus.FINISHED);
        assertThat(runningRolloutGroups.get(1).getStatus()).isEqualTo(RolloutGroupStatus.RUNNING);
        assertThat(runningRolloutGroups.get(2).getStatus()).isEqualTo(RolloutGroupStatus.SCHEDULED);
    }

    @Step("Finish one action of the rollout group and delete four targets")
    private void finishActionAndDeleteTargetsOfSecondRunningGroup(final Rollout createdRollout) {
        final Slice<JpaAction> runningActionsSlice = actionRepository.findByRolloutIdAndStatus(PAGE,
                createdRollout.getId(), Status.RUNNING);
        final List<JpaAction> runningActions = runningActionsSlice.getContent();
        finishAction(runningActions.get(0));
        targetManagement.delete(
                Arrays.asList(runningActions.get(1).getTarget().getId(), runningActions.get(2).getTarget().getId(),
                        runningActions.get(3).getTarget().getId(), runningActions.get(4).getTarget().getId()));

    }

    @Step("Delete all targets of the rollout group")
    private void deleteAllTargetsFromThirdGroup(final Rollout createdRollout) {
        final Slice<JpaAction> runningActionsSlice = actionRepository.findByRolloutIdAndStatus(PAGE,
                createdRollout.getId(), Status.SCHEDULED);
        final List<JpaAction> runningActions = runningActionsSlice.getContent();
        targetManagement.delete(Arrays.asList(runningActions.get(0).getTarget().getId(),
                runningActions.get(1).getTarget().getId(), runningActions.get(2).getTarget().getId(),
                runningActions.get(3).getTarget().getId(), runningActions.get(4).getTarget().getId()));
    }

    @Step("Check the status of the rollout groups and the rollout")
    private void verifyRolloutAndAllGroupsAreFinished(final Rollout createdRollout) {
        rolloutManagement.handleRollouts();
        final List<RolloutGroup> runningRolloutGroups = rolloutGroupManagement
                .findByRollout(PAGE, createdRollout.getId()).getContent();
        assertThat(runningRolloutGroups.get(0).getStatus()).isEqualTo(RolloutGroupStatus.FINISHED);
        assertThat(runningRolloutGroups.get(1).getStatus()).isEqualTo(RolloutGroupStatus.FINISHED);
        assertThat(runningRolloutGroups.get(2).getStatus()).isEqualTo(RolloutGroupStatus.FINISHED);
        assertThat(rolloutManagement.get(createdRollout.getId()).get().getStatus()).isEqualTo(RolloutStatus.FINISHED);

    }

    private void finishAction(final Action action) {
        controllerManagement
                .addUpdateActionStatus(entityFactory.actionStatus().create(action.getId()).status(Status.FINISHED));
    }

    @Test
    @Description("Verfiying that the error handling action of a group is executed to pause the current rollout")
    public void checkErrorHitOfGroupCallsErrorActionToPauseTheRollout() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        // set both actions in error state so error condition is hit and error
        // action is executed
        final List<Action> runningActions = findActionsByRolloutAndStatus(createdRollout, Status.RUNNING);

        // finish actions with error
        for (final Action action : runningActions) {
            controllerManagement
                    .addUpdateActionStatus(entityFactory.actionStatus().create(action.getId()).status(Status.ERROR));
        }

        // check running rollouts again, now the error condition should be hit
        // and should execute the error action
        rolloutManagement.handleRollouts();

        final Rollout rollout = rolloutManagement.get(createdRollout.getId()).get();
        // the rollout itself should be in paused based on the error action
        assertThat(rollout.getStatus()).isEqualTo(RolloutStatus.PAUSED);

        // the first rollout group should be in error state
        final List<RolloutGroup> errorGroup = rolloutGroupManagement
                .findByRollout(new OffsetBasedPageRequest(0, 1, Sort.by(Direction.ASC, "id")), createdRollout.getId())
                .getContent();
        assertThat(errorGroup).hasSize(1);
        assertThat(errorGroup.get(0).getStatus()).isEqualTo(RolloutGroupStatus.ERROR);

        // all other groups should still be in scheduled state
        final List<RolloutGroup> scheduleGroups = rolloutGroupManagement.findByRollout(
                new OffsetBasedPageRequest(1, 100, Sort.by(Direction.ASC, "id")), createdRollout.getId()).getContent();
        scheduleGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.SCHEDULED));
    }

    @Test
    @Description("Verfiying a paused rollout in case of error action hit can be resumed again")
    public void errorActionPausesRolloutAndRolloutGetsResumedStartsNextScheduledGroup() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        // set both actions in error state so error condition is hit and error
        // action is executed
        final List<Action> runningActions = findActionsByRolloutAndStatus(createdRollout, Status.RUNNING);
        // finish actions with error
        for (final Action action : runningActions) {
            controllerManagement
                    .addUpdateActionStatus(entityFactory.actionStatus().create(action.getId()).status(Status.ERROR));
        }

        // check running rollouts again, now the error condition should be hit
        // and should execute the error action
        rolloutManagement.handleRollouts();

        final Rollout rollout = rolloutManagement.get(createdRollout.getId()).get();
        // the rollout itself should be in paused based on the error action
        assertThat(rollout.getStatus()).isEqualTo(RolloutStatus.PAUSED);

        // all other groups should still be in scheduled state
        final List<RolloutGroup> scheduleGroups = rolloutGroupManagement.findByRollout(
                new OffsetBasedPageRequest(1, 100, Sort.by(Direction.ASC, "id")), createdRollout.getId()).getContent();
        scheduleGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.SCHEDULED));

        // resume the rollout again after it gets paused by error action
        rolloutManagement.resumeRollout(createdRollout.getId());

        // the rollout should be running again
        assertThat(rolloutManagement.get(createdRollout.getId()).get().getStatus()).isEqualTo(RolloutStatus.RUNNING);

        // checking rollouts again
        rolloutManagement.handleRollouts();

        // next group should be running again after resuming the rollout
        final List<RolloutGroup> resumedGroups = rolloutGroupManagement
                .findByRollout(new OffsetBasedPageRequest(1, 1, Sort.by(Direction.ASC, "id")), createdRollout.getId())
                .getContent();
        assertThat(resumedGroups).hasSize(1);
        assertThat(resumedGroups.get(0).getStatus()).isEqualTo(RolloutGroupStatus.RUNNING);
    }

    @Test
    @Description("Verfiying that the rollout is starting group after group and gets finished at the end")
    public void rolloutStartsGroupAfterGroupAndGetsFinished() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        // finish running actions, 2 actions should be finished
        assertThat(changeStatusForAllRunningActions(createdRollout, Status.FINISHED)).isEqualTo(2);

        // calculate the rest of the groups and finish them
        for (int groupsLeft = amountGroups - 1; groupsLeft >= 1; groupsLeft--) {
            // next check and start next group
            rolloutManagement.handleRollouts();
            // finish running actions, 2 actions should be finished
            assertThat(changeStatusForAllRunningActions(createdRollout, Status.FINISHED)).isEqualTo(2);
            assertThat(rolloutManagement.get(createdRollout.getId()).get().getStatus())
                    .isEqualTo(RolloutStatus.RUNNING);

        }
        // check rollout to see that all actions and all groups are finished and
        // so can go to FINISHED state of the rollout
        rolloutManagement.handleRollouts();

        // verify all groups are in finished state
        rolloutGroupManagement
                .findByRollout(new OffsetBasedPageRequest(0, 100, Sort.by(Direction.ASC, "id")),
                        createdRollout.getId())
                .forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.FINISHED));

        // verify that rollout itself is in finished state
        final Rollout findRolloutById = rolloutManagement.get(createdRollout.getId()).get();
        assertThat(findRolloutById.getStatus()).isEqualTo(RolloutStatus.FINISHED);
    }

    @Test
    @Description("Verify that the targets have the right status during the rollout.")
    public void countCorrectStatusForEachTargetDuringRollout() {

        final int amountTargetsForRollout = 8;
        final int amountOtherTargets = 15;
        final int amountGroups = 4;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createSimpleTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, successCondition, errorCondition);

        // targets have not started
        Map<TotalTargetCountStatus.Status, Long> validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.NOTSTARTED, 8L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        rolloutManagement.start(createdRollout.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // 6 targets are ready and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.SCHEDULED, 6L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.handleRollouts();
        // 4 targets are ready, 2 are finished and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.SCHEDULED, 4L);
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 2L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.handleRollouts();
        // 2 targets are ready, 4 are finished and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.SCHEDULED, 2L);
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 4L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.handleRollouts();
        // 0 targets are ready, 6 are finished and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 6L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.handleRollouts();
        // 0 targets are ready, 8 are finished and 0 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 8L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

    }

    @Test
    @Description("Verify that the targets have the right status during a download_only rollout.")
    public void countCorrectStatusForEachTargetDuringDownloadOnlyRollout() {

        final int amountTargetsForRollout = 8;
        final int amountOtherTargets = 15;
        final int amountGroups = 4;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createSimpleTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, successCondition, errorCondition, ActionType.DOWNLOAD_ONLY, null);

        // targets have not started
        Map<TotalTargetCountStatus.Status, Long> validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.NOTSTARTED, 8L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        rolloutManagement.start(createdRollout.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // 6 targets are ready and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.SCHEDULED, 6L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        changeStatusForAllRunningActions(createdRollout, Status.DOWNLOADED);
        rolloutManagement.handleRollouts();
        // 4 targets are ready, 2 are finished(with DOWNLOADED action status)
        // and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.SCHEDULED, 4L);
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 2L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        changeStatusForAllRunningActions(createdRollout, Status.DOWNLOADED);
        rolloutManagement.handleRollouts();
        // 2 targets are ready, 4 are finished(with DOWNLOADED action status)
        // and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.SCHEDULED, 2L);
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 4L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        changeStatusForAllRunningActions(createdRollout, Status.DOWNLOADED);
        rolloutManagement.handleRollouts();
        // 0 targets are ready, 6 are finished(with DOWNLOADED action status)
        // and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 6L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.handleRollouts();
        // 0 targets are ready, 6 are finished(with DOWNLOADED action status), 2
        // are finished and 0 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 8L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

    }

    @Test
    @Description("Verify that the targets have the right status during the rollout when an error emerges.")
    public void countCorrectStatusForEachTargetDuringRolloutWithError() {

        final int amountTargetsForRollout = 8;
        final int amountOtherTargets = 15;
        final int amountGroups = 4;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createSimpleTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, successCondition, errorCondition);

        // 8 targets have not started
        Map<TotalTargetCountStatus.Status, Long> validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.NOTSTARTED, 8L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        rolloutManagement.start(createdRollout.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // 6 targets are ready and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.SCHEDULED, 6L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        changeStatusForAllRunningActions(createdRollout, Status.ERROR);
        rolloutManagement.handleRollouts();
        // 6 targets are ready and 2 are error
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.SCHEDULED, 6L);
        validationMap.put(TotalTargetCountStatus.Status.ERROR, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);
    }

    @Test
    @Description("Verify that the targets have the right status during the rollout when receiving the status of rollout groups.")
    public void countCorrectStatusForEachTargetGroupDuringRollout() {

        final int amountTargetsForRollout = 9;
        final int amountOtherTargets = 15;
        final int amountGroups = 4;
        final String successCondition = "50";
        final String errorCondition = "80";
        Rollout createdRollout = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.handleRollouts();

        // round(9/4)=2 targets finished (Group 1)
        // round(7/3)=2 targets running (Group 3)
        // round(5/2)=3 targets SCHEDULED (Group 3)
        // round(2/1)=2 targets SCHEDULED (Group 4)
        createdRollout = rolloutManagement.get(createdRollout.getId()).get();
        final List<RolloutGroup> rolloutGroups = rolloutGroupManagement.findByRollout(PAGE, createdRollout.getId())
                .getContent();

        Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 2L);
        validateRolloutGroupActionStatus(rolloutGroups.get(0), expectedTargetCountStatus);

        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutGroupActionStatus(rolloutGroups.get(1), expectedTargetCountStatus);

        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 3L);
        validateRolloutGroupActionStatus(rolloutGroups.get(2), expectedTargetCountStatus);

        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 2L);
        validateRolloutGroupActionStatus(rolloutGroups.get(3), expectedTargetCountStatus);

    }

    @Test
    @Description("Verify that target actions of rollout get canceled when a manuel distribution sets assignment is done.")
    public void targetsOfRolloutGetsManuelDsAssignment() {

        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "80";
        Rollout createdRollout = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);
        final DistributionSet ds = createdRollout.getDistributionSet();

        createdRollout = rolloutManagement.get(createdRollout.getId()).get();
        // 5 targets are running
        final List<Action> runningActions = findActionsByRolloutAndStatus(createdRollout, Status.RUNNING);
        assertThat(runningActions.size()).isEqualTo(5);

        // 5 targets are in the group and the DS has been assigned
        final List<RolloutGroup> rolloutGroups = rolloutGroupManagement.findByRollout(PAGE, createdRollout.getId())
                .getContent();
        final Page<Target> targets = rolloutGroupManagement.findTargetsOfRolloutGroup(PAGE,
                rolloutGroups.get(0).getId());
        final List<Target> targetList = targets.getContent();
        assertThat(targetList.size()).isEqualTo(5);

        targets.getContent().stream().map(Target::getControllerId).map(deploymentManagement::getAssignedDistributionSet)
                .forEach(d -> assertThat(d.get().getId()).isEqualTo(ds.getId()));

        final List<Target> targetToCancel = new ArrayList<>();
        targetToCancel.add(targetList.get(0));
        targetToCancel.add(targetList.get(1));
        targetToCancel.add(targetList.get(2));
        final DistributionSet dsForCancelTest = testdataFactory.createDistributionSet("dsForTest");
        assignDistributionSet(dsForCancelTest, targetToCancel);
        // 5 targets are canceling but still have the status running and 5 are
        // still in SCHEDULED
        final Map<TotalTargetCountStatus.Status, Long> validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        validationMap.put(TotalTargetCountStatus.Status.SCHEDULED, 5L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);
    }

    @Test
    @Description("Verify that target actions of a rollout get cancelled when another rollout with same targets gets started.")
    public void targetsOfRolloutGetDistributionSetAssignmentByOtherRollout() {

        final int amountTargetsForRollout = 15;
        final int amountOtherTargets = 5;
        final int amountGroups = 3;
        final String successCondition = "50";
        final String errorCondition = "80";
        Rollout rolloutOne = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        rolloutOne = rolloutManagement.get(rolloutOne.getId()).get();

        final DistributionSet dsForRolloutTwo = testdataFactory.createDistributionSet("dsForRolloutTwo");

        final Rollout rolloutTwo = testdataFactory.createRolloutByVariables("rolloutTwo",
                "This is the description for rollout two", 1, "controllerId==rollout-*", dsForRolloutTwo, "50", "80");
        changeStatusForAllRunningActions(rolloutOne, Status.FINISHED);
        rolloutManagement.handleRollouts();
        // Verify that 5 targets are finished, 5 are running and 5 are ready.
        Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 5L);
        validateRolloutActionStatus(rolloutOne.getId(), expectedTargetCountStatus);

        rolloutManagement.start(rolloutTwo.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // Verify that 5 targets are finished, 5 are still running and 5 are
        // cancelled.
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.CANCELLED, 5L);
        validateRolloutActionStatus(rolloutOne.getId(), expectedTargetCountStatus);
    }

    @Test
    @Description("Verify that error status of DistributionSet installation during rollout can get rerun with second rollout so that all targets have some DistributionSet installed at the end.")
    public void startSecondRolloutAfterFristRolloutEndenWithErrors() {

        final int amountTargetsForRollout = 15;
        final int amountOtherTargets = 0;
        final int amountGroups = 3;
        final String successCondition = "50";
        final String errorCondition = "80";
        Rollout rolloutOne = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);
        final DistributionSet distributionSet = rolloutOne.getDistributionSet();

        rolloutOne = rolloutManagement.get(rolloutOne.getId()).get();
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.handleRollouts();
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.handleRollouts();
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.handleRollouts();

        // 9 targets are finished and 6 have error
        Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 9L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.ERROR, 6L);
        validateRolloutActionStatus(rolloutOne.getId(), expectedTargetCountStatus);
        // rollout is finished
        rolloutOne = rolloutManagement.get(rolloutOne.getId()).get();
        assertThat(rolloutOne.getStatus()).isEqualTo(RolloutStatus.FINISHED);

        final int amountGroupsForRolloutTwo = 1;
        Rollout rolloutTwo = testdataFactory.createRolloutByVariables("rolloutTwo",
                "This is the description for rollout two", amountGroupsForRolloutTwo, "controllerId==rollout-*",
                distributionSet, "50", "80");

        rolloutManagement.start(rolloutTwo.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        rolloutTwo = rolloutManagement.get(rolloutTwo.getId()).get();
        // 6 error targets are now running
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 6L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 9L);
        validateRolloutActionStatus(rolloutTwo.getId(), expectedTargetCountStatus);
        changeStatusForAllRunningActions(rolloutTwo, Status.FINISHED);
        final Page<Target> targetPage = targetManagement.findByUpdateStatus(PAGE, TargetUpdateStatus.IN_SYNC);
        final List<Target> targetList = targetPage.getContent();
        // 15 targets in finished/IN_SYNC status and same DS assigned
        assertThat(targetList.size()).isEqualTo(amountTargetsForRollout);
        targetList.stream().map(Target::getControllerId).map(deploymentManagement::getAssignedDistributionSet)
                .forEach(d -> assertThat(d.get()).isEqualTo(distributionSet));
    }

    @Test
    @Description("Verify that the rollout moves to the next group when the success condition was achieved and the error condition was not exceeded.")
    public void successConditionAchievedAndErrorConditionNotExceeded() {

        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "80";
        Rollout rolloutOne = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        rolloutOne = rolloutManagement.get(rolloutOne.getId()).get();
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.handleRollouts();
        // verify: 40% error but 60% finished -> should move to next group
        final List<RolloutGroup> rolloutGruops = rolloutGroupManagement.findByRollout(PAGE, rolloutOne.getId())
                .getContent();
        final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        validateRolloutGroupActionStatus(rolloutGruops.get(1), expectedTargetCountStatus);

    }

    @Test
    @Description("Verify that the rollout does not move to the next group when the sucess condition was not achieved.")
    public void successConditionNotAchieved() {

        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final String successCondition = "80";
        final String errorCondition = "90";
        Rollout rolloutOne = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        rolloutOne = rolloutManagement.get(rolloutOne.getId()).get();
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.handleRollouts();
        // verify: 40% error and 60% finished -> should not move to next group
        // because successCondition 80%
        final List<RolloutGroup> rolloutGruops = rolloutGroupManagement.findByRollout(PAGE, rolloutOne.getId())
                .getContent();
        final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 5L);
        validateRolloutGroupActionStatus(rolloutGruops.get(1), expectedTargetCountStatus);
    }

    @Test
    @Description("Verify that the rollout pauses when the error condition was exceeded.")
    public void errorConditionExceeded() {

        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "20";
        Rollout rolloutOne = createAndStartRollout(amountTargetsForRollout, amountOtherTargets, amountGroups,
                successCondition, errorCondition);

        rolloutOne = rolloutManagement.get(rolloutOne.getId()).get();
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.handleRollouts();
        // verify: 40% error -> should pause because errorCondition is 20%
        rolloutOne = rolloutManagement.get(rolloutOne.getId()).get();
        assertThat(RolloutStatus.PAUSED).isEqualTo(rolloutOne.getStatus());
    }

    @Test
    @Description("Verify that all rollouts are return with expected target statuses.")
    public void findAllRolloutsWithDetailedStatus() {

        final int amountTargetsForRollout = 12;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "20";
        final Rollout rolloutA = createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, amountGroups,
                successCondition, errorCondition, "RolloutA", "RolloutA");
        rolloutManagement.start(rolloutA.getId());
        rolloutManagement.handleRollouts();

        final int amountTargetsForRollout2 = 10;
        final int amountGroups2 = 2;
        final Rollout rolloutB = createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout2, amountGroups2,
                successCondition, errorCondition, "RolloutB", "RolloutB");
        rolloutManagement.start(rolloutB.getId());
        rolloutManagement.handleRollouts();

        changeStatusForAllRunningActions(rolloutB, Status.FINISHED);
        rolloutManagement.handleRollouts();

        final int amountTargetsForRollout3 = 10;
        final int amountGroups3 = 2;
        final Rollout rolloutC = createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout3, amountGroups3,
                successCondition, errorCondition, "RolloutC", "RolloutC");
        rolloutManagement.start(rolloutC.getId());
        rolloutManagement.handleRollouts();

        changeStatusForAllRunningActions(rolloutC, Status.ERROR);
        rolloutManagement.handleRollouts();

        final int amountTargetsForRollout4 = 15;
        final int amountGroups4 = 3;
        final Rollout rolloutD = createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout4, amountGroups4,
                successCondition, errorCondition, "RolloutD", "RolloutD");
        rolloutManagement.start(rolloutD.getId());
        rolloutManagement.handleRollouts();

        changeStatusForRunningActions(rolloutD, Status.ERROR, 1);
        rolloutManagement.handleRollouts();
        changeStatusForAllRunningActions(rolloutD, Status.FINISHED);
        rolloutManagement.handleRollouts();

        final Page<Rollout> rolloutPage = rolloutManagement
                .findAllWithDetailedStatus(new OffsetBasedPageRequest(0, 100, Sort.by(Direction.ASC, "name")), false);
        final List<Rollout> rolloutList = rolloutPage.getContent();

        // validate rolloutA -> 6 running and 6 ready
        Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 6L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 6L);
        validateRolloutActionStatus(rolloutList.get(0).getId(), expectedTargetCountStatus);

        // validate rolloutB -> 5 running and 5 finished
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        validateRolloutActionStatus(rolloutList.get(1).getId(), expectedTargetCountStatus);

        // validate rolloutC -> 5 running and 5 error
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.ERROR, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 5L);
        validateRolloutActionStatus(rolloutList.get(2).getId(), expectedTargetCountStatus);

        // validate rolloutD -> 1, error, 4 finished, 5 running and 5 ready
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.ERROR, 1L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 4L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 5L);
        validateRolloutActionStatus(rolloutList.get(3).getId(), expectedTargetCountStatus);
    }

    @Test
    @Description("Verify the count of existing rollouts.")
    public void rightCountForAllRollouts() {

        final int amountTargetsForRollout = 6;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "80";
        for (int i = 1; i <= 10; i++) {
            createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, amountGroups, successCondition,
                    errorCondition, "Rollout" + i, "Rollout" + i);
        }
        final Long count = rolloutManagement.count();
        assertThat(count).isEqualTo(10L);
    }

    @Test
    @Description("Verify the count of filtered existing rollouts.")
    public void countRolloutsAllByFilters() {

        final int amountTargetsForRollout = 6;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "80";
        for (int i = 1; i <= 5; i++) {
            createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, amountGroups, successCondition,
                    errorCondition, "Rollout" + i, "Rollout" + i);
        }
        for (int i = 1; i <= 5; i++) {
            createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, amountGroups, successCondition,
                    errorCondition, "SomethingElse" + i, "SomethingElse" + i);
        }

        final Long count = rolloutManagement.countByFilters("Rollout%");
        assertThat(count).isEqualTo(5L);

    }

    @Test
    @Description("Verify that the filtering and sorting ascending for rollout is working correctly.")
    public void findRolloutByFilters() {

        final int amountTargetsForRollout = 6;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "80";
        for (int i = 1; i <= 5; i++) {
            createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, amountGroups, successCondition,
                    errorCondition, "Rollout" + i, "Rollout" + i);
        }
        for (int i = 1; i <= 8; i++) {
            createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, amountGroups, successCondition,
                    errorCondition, "SomethingElse" + i, "SomethingElse" + i);
        }

        final Slice<Rollout> rollout = rolloutManagement.findByFiltersWithDetailedStatus(
                new OffsetBasedPageRequest(0, 100, Sort.by(Direction.ASC, "name")), "Rollout%", false);
        final List<Rollout> rolloutList = rollout.getContent();
        assertThat(rolloutList.size()).isEqualTo(5);
        int i = 1;
        for (final Rollout r : rolloutList) {
            assertThat(r.getName()).isEqualTo("Rollout" + i);
            i++;
        }
    }

    @Test
    @Description("Verify that the expected rollout is found by name.")
    public void findRolloutByName() {

        final int amountTargetsForRollout = 12;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "Rollout137";
        final Rollout rolloutCreated = createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout,
                amountGroups, successCondition, errorCondition, rolloutName, "RolloutA");

        final Rollout rolloutFound = rolloutManagement.getByName(rolloutName).get();
        assertThat(rolloutCreated).isEqualTo(rolloutFound);

    }

    @Test
    @Description("Verify that the percent count is acting like aspected when targets move to the status finished or error.")
    public void getFinishedPercentForRunningGroup() {

        final int amountTargetsForRollout = 10;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "MyRollout";
        Rollout myRollout = createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, amountGroups,
                successCondition, errorCondition, rolloutName, rolloutName);
        rolloutManagement.start(myRollout.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        changeStatusForRunningActions(myRollout, Status.FINISHED, 2);
        rolloutManagement.handleRollouts();
        myRollout = rolloutManagement.get(myRollout.getId()).get();

        float percent = rolloutGroupManagement
                .getWithDetailedStatus(
                        rolloutGroupManagement.findByRollout(PAGE, myRollout.getId()).getContent().get(0).getId())
                .get().getTotalTargetCountStatus().getFinishedPercent();
        assertThat(percent).isEqualTo(40);

        changeStatusForRunningActions(myRollout, Status.FINISHED, 3);
        rolloutManagement.handleRollouts();

        percent = rolloutGroupManagement
                .getWithDetailedStatus(
                        rolloutGroupManagement.findByRollout(PAGE, myRollout.getId()).getContent().get(0).getId())
                .get().getTotalTargetCountStatus().getFinishedPercent();
        assertThat(percent).isEqualTo(100);

        changeStatusForRunningActions(myRollout, Status.FINISHED, 4);
        changeStatusForAllRunningActions(myRollout, Status.ERROR);
        rolloutManagement.handleRollouts();

        percent = rolloutGroupManagement
                .getWithDetailedStatus(
                        rolloutGroupManagement.findByRollout(PAGE, myRollout.getId()).getContent().get(1).getId())
                .get().getTotalTargetCountStatus().getFinishedPercent();
        assertThat(percent).isEqualTo(80);
    }

    @Test
    @Description("Verify that the expected targets are returned for the rollout groups.")
    public void findRolloutGroupTargetsWithRsqlParam() {

        final int amountTargetsForRollout = 15;
        final int amountGroups = 3;
        final int amountOtherTargets = 15;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "MyRollout";
        Rollout myRollout = createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, amountGroups,
                successCondition, errorCondition, rolloutName, rolloutName);

        testdataFactory.createTargets(amountOtherTargets, "others-", "rollout");

        final String rsqlParam = "controllerId==*MyRoll*";

        rolloutManagement.start(myRollout.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        final Condition<String> targetBelongsInRollout = new Condition<>(s -> s.startsWith(rolloutName),
                "Target belongs into rollout");

        myRollout = rolloutManagement.get(myRollout.getId()).get();
        final List<RolloutGroup> rolloutGroups = rolloutGroupManagement.findByRollout(PAGE, myRollout.getId())
                .getContent();

        Page<Target> targetPage = rolloutGroupManagement.findTargetsOfRolloutGroupByRsql(
                new OffsetBasedPageRequest(0, 100), rolloutGroups.get(0).getId(), rsqlParam);
        final List<Target> targetlistGroup1 = targetPage.getContent();
        assertThat(targetlistGroup1.size()).isEqualTo(5);
        assertThat(targetlistGroup1.stream().map(Target::getControllerId).collect(Collectors.toList()))
                .are(targetBelongsInRollout);

        targetPage = rolloutGroupManagement.findTargetsOfRolloutGroupByRsql(new OffsetBasedPageRequest(0, 100),
                rolloutGroups.get(1).getId(), rsqlParam);
        final List<Target> targetlistGroup2 = targetPage.getContent();
        assertThat(targetlistGroup2.size()).isEqualTo(5);
        assertThat(targetlistGroup2.stream().map(Target::getControllerId).collect(Collectors.toList()))
                .are(targetBelongsInRollout);

        targetPage = rolloutGroupManagement.findTargetsOfRolloutGroupByRsql(new OffsetBasedPageRequest(0, 100),
                rolloutGroups.get(2).getId(), rsqlParam);
        final List<Target> targetlistGroup3 = targetPage.getContent();
        assertThat(targetlistGroup3.size()).isEqualTo(5);
        assertThat(targetlistGroup3.stream().map(Target::getControllerId).collect(Collectors.toList()))
                .are(targetBelongsInRollout);

    }

    @Test
    @Description("Verify the creation of a Rollout without targets throws an Exception.")
    public void createRolloutNotMatchingTargets() {
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "rolloutTest3";

        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dsFor" + rolloutName);

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> testdataFactory.createRolloutByVariables(rolloutName, "desc", amountGroups,
                        "id==notExisting", distributionSet, successCondition, errorCondition))
                .withMessageContaining("does not match any existing");

    }

    @Test
    @Description("Verify the creation of a Rollout with the same name throws an Exception.")
    public void createDuplicateRollout() {
        final int amountGroups = 5;
        final int amountTargetsForRollout = 10;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "rolloutTest4";

        testdataFactory.createTargets(amountTargetsForRollout, "dup-ro-", "rollout");

        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dsFor" + rolloutName);
        testdataFactory.createRolloutByVariables(rolloutName, "desc", amountGroups, "id==dup-ro-*", distributionSet,
                successCondition, errorCondition);

        assertThatExceptionOfType(EntityAlreadyExistsException.class)
                .isThrownBy(() -> testdataFactory.createRolloutByVariables(rolloutName, "desc", amountGroups,
                        "id==dup-ro-*", distributionSet, successCondition, errorCondition))
                .withMessageContaining("already exists in database");
    }

    @Test
    @Description("Verify the creation and the start of a Rollout with more groups than targets.")
    public void createAndStartRolloutWithEmptyGroups() throws Exception {
        final int amountTargetsForRollout = 3;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "rolloutTestG";
        final String targetPrefixName = rolloutName;
        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dsFor" + rolloutName);
        testdataFactory.createTargets(amountTargetsForRollout, targetPrefixName + "-", targetPrefixName);

        Rollout myRollout = testdataFactory.createRolloutByVariables(rolloutName, "desc", amountGroups,
                "controllerId==" + targetPrefixName + "-*", distributionSet, successCondition, errorCondition);

        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.READY);

        final List<RolloutGroup> groups = rolloutGroupManagement.findByRollout(PAGE, myRollout.getId()).getContent();

        assertThat(groups.get(0).getStatus()).isEqualTo(RolloutGroupStatus.READY);
        assertThat(groups.get(0).getTotalTargets()).isEqualTo(1);
        assertThat(groups.get(1).getStatus()).isEqualTo(RolloutGroupStatus.READY);
        assertThat(groups.get(1).getTotalTargets()).isEqualTo(1);
        assertThat(groups.get(2).getStatus()).isEqualTo(RolloutGroupStatus.READY);
        assertThat(groups.get(2).getTotalTargets()).isEqualTo(0);
        assertThat(groups.get(3).getStatus()).isEqualTo(RolloutGroupStatus.READY);
        assertThat(groups.get(3).getTotalTargets()).isEqualTo(1);
        assertThat(groups.get(4).getStatus()).isEqualTo(RolloutGroupStatus.READY);
        assertThat(groups.get(4).getTotalTargets()).isEqualTo(0);

        rolloutManagement.start(myRollout.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        final SuccessConditionRolloutStatus conditionRolloutStatus = new SuccessConditionRolloutStatus(
                RolloutStatus.RUNNING);
        assertThat(MultipleInvokeHelper.doWithTimeout(new RolloutStatusCallable(myRollout.getId()),
                conditionRolloutStatus, 15000, 500)).as("Rollout status").isNotNull();

        myRollout = rolloutManagement.get(myRollout.getId()).get();
        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.RUNNING);
        final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 1L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 2L);
        validateRolloutActionStatus(myRollout.getId(), expectedTargetCountStatus);

    }

    @Test
    @Description("Verify the creation and the start of a rollout.")
    public void createAndStartRollout() throws Exception {

        final int amountTargetsForRollout = 50;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "rolloutTest";
        final String targetPrefixName = rolloutName;
        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dsFor" + rolloutName);
        testdataFactory.createTargets(amountTargetsForRollout, targetPrefixName + "-", targetPrefixName);

        Rollout myRollout = testdataFactory.createRolloutByVariables(rolloutName, "desc", amountGroups,
                "controllerId==" + targetPrefixName + "-*", distributionSet, successCondition, errorCondition);

        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.READY);

        rolloutManagement.handleRollouts();

        myRollout = rolloutManagement.get(myRollout.getId()).get();
        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.READY);

        rolloutManagement.start(myRollout.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        final SuccessConditionRolloutStatus conditionRolloutTargetCount = new SuccessConditionRolloutStatus(
                RolloutStatus.RUNNING);
        assertThat(MultipleInvokeHelper.doWithTimeout(new RolloutStatusCallable(myRollout.getId()),
                conditionRolloutTargetCount, 15000, 500)).as("Rollout status").isNotNull();

        myRollout = rolloutManagement.get(myRollout.getId()).get();
        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.RUNNING);
        final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 10L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 40L);
        validateRolloutActionStatus(myRollout.getId(), expectedTargetCountStatus);
    }

    @Test
    @Description("Verify that a rollout cannot be created if the 'max targets per rollout group' quota is violated.")
    public void createRolloutFailsIfQuotaGroupQuotaIsViolated() throws Exception {

        final int maxTargets = quotaManagement.getMaxTargetsPerRolloutGroup();

        final int amountTargetsForRollout = maxTargets + 1;
        final int amountGroups = 1;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "rolloutTest";
        final String targetPrefixName = rolloutName;
        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dsFor" + rolloutName);
        testdataFactory.createTargets(amountTargetsForRollout, targetPrefixName + "-", targetPrefixName);

        final RolloutGroupConditions conditions = new RolloutGroupConditionBuilder().withDefaults()
                .successCondition(RolloutGroupSuccessCondition.THRESHOLD, successCondition)
                .errorCondition(RolloutGroupErrorCondition.THRESHOLD, errorCondition)
                .errorAction(RolloutGroupErrorAction.PAUSE, null).build();

        assertThatExceptionOfType(AssignmentQuotaExceededException.class).isThrownBy(() -> rolloutManagement.create(
                entityFactory.rollout().create().name(rolloutName).description(rolloutName)
                        .targetFilterQuery("controllerId==" + targetPrefixName + "-*").set(distributionSet),
                amountGroups, conditions));

    }

    @Test
    @Description("Verify that a rollout cannot be created based on group definitions if the 'max targets per rollout group' quota is violated for one of the groups.")
    public void createRolloutWithGroupDefinitionsFailsIfQuotaGroupQuotaIsViolated() throws Exception {

        final int maxTargets = quotaManagement.getMaxTargetsPerRolloutGroup();

        final int amountTargetsForRollout = maxTargets * 2 + 2;
        final String rolloutName = "rolloutTest";
        final String targetPrefixName = rolloutName;
        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dsFor" + rolloutName);
        testdataFactory.createTargets(amountTargetsForRollout, targetPrefixName + "-", targetPrefixName);

        final RolloutGroupConditions conditions = new RolloutGroupConditionBuilder().withDefaults().build();

        // create group definitions
        final RolloutGroupCreate group1 = entityFactory.rolloutGroup().create().conditions(conditions).name("group1")
                .targetPercentage(50.0F);
        final RolloutGroupCreate group2 = entityFactory.rolloutGroup().create().conditions(conditions).name("group2")
                .targetPercentage(100.0F);

        // group1 exceeds the quota
        assertThatExceptionOfType(AssignmentQuotaExceededException.class).isThrownBy(() -> rolloutManagement.create(
                entityFactory.rollout().create().name(rolloutName).description(rolloutName)
                        .targetFilterQuery("controllerId==" + targetPrefixName + "-*").set(distributionSet),
                Arrays.asList(group1, group2), conditions));

        // create group definitions
        final RolloutGroupCreate group3 = entityFactory.rolloutGroup().create().conditions(conditions).name("group3")
                .targetPercentage(1.0F);
        final RolloutGroupCreate group4 = entityFactory.rolloutGroup().create().conditions(conditions).name("group4")
                .targetPercentage(100.0F);

        // group4 exceeds the quota
        assertThatExceptionOfType(AssignmentQuotaExceededException.class).isThrownBy(() -> rolloutManagement.create(
                entityFactory.rollout().create().name(rolloutName).description(rolloutName)
                        .targetFilterQuery("controllerId==" + targetPrefixName + "-*").set(distributionSet),
                Arrays.asList(group3, group4), conditions));

        // create group definitions
        final RolloutGroupCreate group5 = entityFactory.rolloutGroup().create().conditions(conditions).name("group5")
                .targetPercentage(33.3F);
        final RolloutGroupCreate group6 = entityFactory.rolloutGroup().create().conditions(conditions).name("group6")
                .targetPercentage(66.6F);
        final RolloutGroupCreate group7 = entityFactory.rolloutGroup().create().conditions(conditions).name("group7")
                .targetPercentage(100.0F);

        // should work fine
        assertThat(rolloutManagement.create(
                entityFactory.rollout().create().name(rolloutName).description(rolloutName)
                        .targetFilterQuery("controllerId==" + targetPrefixName + "-*").set(distributionSet),
                Arrays.asList(group5, group6, group7), conditions)).isNotNull();

    }

    @Test
    @Description("Verify the creation and the automatic start of a rollout.")
    public void createAndAutoStartRollout() throws Exception {

        final int amountTargetsForRollout = 50;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "rolloutTest8";
        final String targetPrefixName = rolloutName;
        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dsFor" + rolloutName);
        testdataFactory.createTargets(amountTargetsForRollout, targetPrefixName + "-", targetPrefixName);

        Rollout myRollout = testdataFactory.createRolloutByVariables(rolloutName, "desc", amountGroups,
                "controllerId==" + targetPrefixName + "-*", distributionSet, successCondition, errorCondition);

        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.READY);

        // schedule rollout auto start into the future
        rolloutManagement
                .update(entityFactory.rollout().update(myRollout.getId()).startAt(System.currentTimeMillis() + 60000));
        rolloutManagement.handleRollouts();

        // rollout should not have been started
        myRollout = rolloutManagement.get(myRollout.getId()).get();
        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.READY);

        // schedule to now
        rolloutManagement.update(entityFactory.rollout().update(myRollout.getId()).startAt(System.currentTimeMillis()));
        rolloutManagement.handleRollouts();

        myRollout = rolloutManagement.get(myRollout.getId()).get();
        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.STARTING);

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        final SuccessConditionRolloutStatus conditionRolloutTargetCount = new SuccessConditionRolloutStatus(
                RolloutStatus.RUNNING);
        assertThat(MultipleInvokeHelper.doWithTimeout(new RolloutStatusCallable(myRollout.getId()),
                conditionRolloutTargetCount, 15000, 500)).as("Rollout status").isNotNull();

        myRollout = rolloutManagement.get(myRollout.getId()).get();
        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.RUNNING);
        final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 10L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.SCHEDULED, 40L);
        validateRolloutActionStatus(myRollout.getId(), expectedTargetCountStatus);
    }

    @Test
    @Description("Verify the creation of a rollout with a groups definition.")
    public void createRolloutWithGroupDefinition() throws Exception {
        final String rolloutName = "rolloutTest3";

        final int amountTargetsInGroup1 = 100;
        final int percentTargetsInGroup1 = 100;

        final int amountTargetsInGroup1and2 = 500;
        final int percentTargetsInGroup2 = 20;
        final int percentTargetsInGroup3 = 100;

        final int countTargetsInGroup2 = (int) Math
                .ceil((double) percentTargetsInGroup2 / 100 * amountTargetsInGroup1and2);
        final int countTargetsInGroup3 = amountTargetsInGroup1and2 - countTargetsInGroup2;

        final RolloutGroupConditions conditions = new RolloutGroupConditionBuilder().withDefaults().build();
        // Generate Targets for group 2 and 3 and generate the Rollout
        final RolloutCreate rolloutcreate = generateTargetsAndRollout(rolloutName, amountTargetsInGroup1and2);

        // Generate Targets for group 1
        testdataFactory.createTargets(amountTargetsInGroup1, rolloutName + "-gr1-", rolloutName);

        final List<RolloutGroupCreate> rolloutGroups = new ArrayList<>(3);
        rolloutGroups.add(generateRolloutGroup(0, percentTargetsInGroup1, "id==" + rolloutName + "-gr1-*"));
        rolloutGroups.add(generateRolloutGroup(1, percentTargetsInGroup2, null));
        rolloutGroups.add(generateRolloutGroup(2, percentTargetsInGroup3, null));

        Rollout myRollout = rolloutManagement.create(rolloutcreate, rolloutGroups, conditions);
        myRollout = rolloutManagement.get(myRollout.getId()).get();

        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.CREATING);
        for (final RolloutGroup group : rolloutGroupManagement.findByRollout(PAGE, myRollout.getId()).getContent()) {
            assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.CREATING);
        }

        // Generate Targets that must not be addressed by the rollout, because
        // they were added after the rollout was created
        TimeUnit.SECONDS.sleep(1);
        testdataFactory.createTargets(10, rolloutName + "-notIn-", rolloutName);

        rolloutManagement.handleRollouts();

        myRollout = rolloutManagement.get(myRollout.getId()).get();
        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.READY);
        assertThat(myRollout.getTotalTargets()).isEqualTo(amountTargetsInGroup1and2 + amountTargetsInGroup1);

        final List<RolloutGroup> groups = rolloutGroupManagement.findByRollout(PAGE, myRollout.getId()).getContent();

        assertThat(groups.get(0).getStatus()).isEqualTo(RolloutGroupStatus.READY);
        assertThat(groups.get(0).getTotalTargets()).isEqualTo(amountTargetsInGroup1);

        assertThat(groups.get(1).getStatus()).isEqualTo(RolloutGroupStatus.READY);
        assertThat(groups.get(1).getTotalTargets()).isEqualTo(countTargetsInGroup2);

        assertThat(groups.get(2).getStatus()).isEqualTo(RolloutGroupStatus.READY);
        assertThat(groups.get(2).getTotalTargets()).isEqualTo(countTargetsInGroup3);

    }

    @Test
    @Description("Verify rollout creation fails if group definition does not address all targets")
    public void createRolloutWithGroupsNotMatchingTargets() throws Exception {
        final String rolloutName = "rolloutTest4";
        final int amountTargetsForRollout = 500;
        final int percentTargetsInGroup1 = 20;
        final int percentTargetsInGroup2 = 50;

        final RolloutGroupConditions conditions = new RolloutGroupConditionBuilder().withDefaults().build();
        final RolloutCreate myRollout = generateTargetsAndRollout(rolloutName, amountTargetsForRollout);

        final List<RolloutGroupCreate> rolloutGroups = new ArrayList<>(2);
        rolloutGroups.add(generateRolloutGroup(0, percentTargetsInGroup1, null));
        rolloutGroups.add(generateRolloutGroup(1, percentTargetsInGroup2, null));

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> rolloutManagement.create(myRollout, rolloutGroups, conditions))
                .withMessageContaining("groups don't match");

    }

    @Test
    @Description("Verify rollout creation fails if group definition specifies illegal target percentage")
    public void createRolloutWithIllegalPercentage() throws Exception {
        final String rolloutName = "rolloutTest6";
        final int amountTargetsForRollout = 10;
        final int percentTargetsInGroup1 = 101;
        final int percentTargetsInGroup2 = 50;

        final RolloutGroupConditions conditions = new RolloutGroupConditionBuilder().withDefaults().build();
        final RolloutCreate myRollout = generateTargetsAndRollout(rolloutName, amountTargetsForRollout);

        final List<RolloutGroupCreate> rolloutGroups = Arrays.asList(
                generateRolloutGroup(0, percentTargetsInGroup1, null),
                generateRolloutGroup(1, percentTargetsInGroup2, null));

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> rolloutManagement.create(myRollout, rolloutGroups, conditions))
                .withMessageContaining("percentage has to be between 1 and 100");

    }

    @Test
    @Description("Verify rollout creation fails if the 'max rollout groups per rollout' quota is violated.")
    public void createRolloutWithIllegalAmountOfGroups() throws Exception {
        final String rolloutName = "rolloutTest5";
        final int targets = 10;
        final int maxGroups = quotaManagement.getMaxRolloutGroupsPerRollout();

        final RolloutGroupConditions conditions = new RolloutGroupConditionBuilder().withDefaults().build();
        final RolloutCreate rollout = generateTargetsAndRollout(rolloutName, targets);

        assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> rolloutManagement.create(rollout, maxGroups + 1, conditions))
                .withMessageContaining("not be greater than " + maxGroups);

    }

    @Test
    @Description("Verify the start of a Rollout does not work during creation phase.")
    public void createAndStartRolloutDuringCreationFails() throws Exception {
        final int amountTargetsForRollout = 3;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final String rolloutName = "rolloutTestGC";
        final String targetPrefixName = rolloutName;
        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dsFor" + rolloutName);
        testdataFactory.createTargets(amountTargetsForRollout, targetPrefixName + "-", targetPrefixName);

        final RolloutGroupConditions conditions = new RolloutGroupConditionBuilder().withDefaults()
                .successCondition(RolloutGroupSuccessCondition.THRESHOLD, successCondition)
                .errorCondition(RolloutGroupErrorCondition.THRESHOLD, errorCondition)
                .errorAction(RolloutGroupErrorAction.PAUSE, null).build();
        final RolloutCreate rolloutToCreate = entityFactory.rollout().create().name(rolloutName)
                .description("some description").targetFilterQuery("id==" + targetPrefixName + "-*")
                .set(distributionSet);

        Rollout myRollout = rolloutManagement.create(rolloutToCreate, amountGroups, conditions);
        myRollout = rolloutManagement.get(myRollout.getId()).get();

        assertThat(myRollout.getStatus()).isEqualTo(RolloutStatus.CREATING);

        final Long rolloutId = myRollout.getId();
        assertThatExceptionOfType(RolloutIllegalStateException.class)
                .isThrownBy(() -> rolloutManagement.start(rolloutId))
                .withMessageContaining("can only be started in state ready");

    }

    @Test
    @Description("Creating a rollout with approval role or approval engine disabled results in the rollout being in "
            + "READY state.")
    public void createdRolloutWithApprovalRoleOrApprovalDisabledTransitionsToReadyState() {
        approvalStrategy.setApprovalNeeded(false);
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout rollout = createSimpleTestRolloutWithTargetsAndDistributionSet(10, 10, 5, successCondition,
                errorCondition);
        assertThat(rollout.getStatus()).isEqualTo(Rollout.RolloutStatus.READY);
    }

    @Test
    @Description("Creating a rollout without approver role and approval enabled leads to transition to "
            + "WAITING_FOR_APPROVAL state.")
    public void createdRolloutWithoutApprovalRoleTransitionsToWaitingForApprovalState() {
        approvalStrategy.setApprovalNeeded(true);
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout rollout = createSimpleTestRolloutWithTargetsAndDistributionSet(10, 10, 5, successCondition,
                errorCondition);
        assertThat(rollout.getStatus()).isEqualTo(Rollout.RolloutStatus.WAITING_FOR_APPROVAL);
    }

    @Test
    @Description("Approving a rollout leads to transition to READY state.")
    public void approvedRolloutTransitionsToReadyState() {
        approvalStrategy.setApprovalNeeded(true);
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout rollout = createSimpleTestRolloutWithTargetsAndDistributionSet(10, 10, 5, successCondition,
                errorCondition);
        assertThat(rollout.getStatus()).isEqualTo(Rollout.RolloutStatus.WAITING_FOR_APPROVAL);
        rolloutManagement.approveOrDeny(rollout.getId(), Rollout.ApprovalDecision.APPROVED);
        final Rollout resultingRollout = rolloutRepository.findById(rollout.getId()).get();
        assertThat(resultingRollout.getStatus()).isEqualTo(Rollout.RolloutStatus.READY);
    }

    @Test
    @Description("Denying approval for a rollout leads to transition to APPROVAL_DENIED state.")
    public void deniedRolloutTransitionsToApprovalDeniedState() {
        approvalStrategy.setApprovalNeeded(true);
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout rollout = createSimpleTestRolloutWithTargetsAndDistributionSet(10, 10, 5, successCondition,
                errorCondition);
        assertThat(rollout.getStatus()).isEqualTo(Rollout.RolloutStatus.WAITING_FOR_APPROVAL);
        rolloutManagement.approveOrDeny(rollout.getId(), Rollout.ApprovalDecision.DENIED);
        final Rollout resultingRollout = rolloutRepository.findById(rollout.getId()).get();
        assertThat(resultingRollout.getStatus()).isEqualTo(RolloutStatus.APPROVAL_DENIED);
    }

    @Test
    @ExpectEvents({ @Expect(type = RolloutDeletedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 25), @Expect(type = RolloutUpdatedEvent.class, count = 2),
            @Expect(type = RolloutGroupCreatedEvent.class, count = 5),
            @Expect(type = RolloutGroupDeletedEvent.class, count = 5),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = RolloutGroupUpdatedEvent.class, count = 5),
            @Expect(type = RolloutCreatedEvent.class, count = 1) })
    public void deleteRolloutWhichHasNeverStartedIsHardDeleted() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createSimpleTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, successCondition, errorCondition);

        // test
        rolloutManagement.delete(createdRollout.getId());
        final Page<RolloutGroup> rolloutGroups = rolloutGroupManagement.findByRollout(Pageable.unpaged(), createdRollout.getId());
        rolloutManagement.handleRollouts();

        // verify
        final Optional<JpaRollout> deletedRollout = rolloutRepository.findById(createdRollout.getId());
        assertThat(deletedRollout).isNotPresent();
        assertThat(rolloutGroupRepository.count()).isZero();
        rolloutGroups.forEach(group -> assertThat(
                rolloutTargetGroupRepository.countByRolloutGroup((JpaRolloutGroup) group)).isZero());
    }

    @Test
    @ExpectEvents({ @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = RolloutGroupUpdatedEvent.class, count = 10),
            @Expect(type = RolloutUpdatedEvent.class, count = 6),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 25), @Expect(type = TargetUpdatedEvent.class, count = 2),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = RolloutGroupCreatedEvent.class, count = 5),
            @Expect(type = RolloutGroupDeletedEvent.class, count = 5),
            @Expect(type = ActionCreatedEvent.class, count = 10), @Expect(type = ActionUpdatedEvent.class, count = 2),
            @Expect(type = RolloutDeletedEvent.class, count = 1),
            @Expect(type = RolloutCreatedEvent.class, count = 1) })
    public void deleteRolloutWhichHasBeenStartedBeforeIsSoftDeleted() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final String successCondition = "50";
        final String errorCondition = "80";
        final Rollout createdRollout = createSimpleTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, successCondition, errorCondition);

        // start the rollout, so it has active running actions and a group which
        // has been started
        rolloutManagement.start(createdRollout.getId());
        rolloutManagement.handleRollouts();

        // verify we have running actions
        assertThat(actionRepository.findByRolloutIdAndStatus(PAGE, createdRollout.getId(), Status.RUNNING)
                .getNumberOfElements()).isEqualTo(2);

        // test
        rolloutManagement.delete(createdRollout.getId());
        rolloutManagement.handleRollouts();

        // verify
        final JpaRollout deletedRollout = rolloutRepository.findById(createdRollout.getId()).get();
        assertThat(deletedRollout).isNotNull();

        assertThat(deletedRollout.getStatus()).isEqualTo(RolloutStatus.DELETED);
        assertThatExceptionOfType(EntityReadOnlyException.class)
                .isThrownBy(() -> rolloutManagement
                        .update(entityFactory.rollout().update(createdRollout.getId()).description("test")))
                .withMessageContaining("" + createdRollout.getId());

        assertThat(rolloutManagement.findAll(PAGE, true).getContent()).hasSize(1);
        assertThat(rolloutManagement.findAll(PAGE, false).getContent()).hasSize(0);
        assertThat(rolloutGroupManagement.findByRolloutWithDetailedStatus(PAGE, createdRollout.getId()).getContent())
                .hasSize(amountGroups);

        // verify that all scheduled actions are deleted
        assertThat(actionRepository.findByRolloutIdAndStatus(PAGE, deletedRollout.getId(), Status.SCHEDULED)
                .getNumberOfElements()).isEqualTo(0);
        // verify that all running actions keep running
        assertThat(actionRepository.findByRolloutIdAndStatus(PAGE, deletedRollout.getId(), Status.RUNNING)
                .getNumberOfElements()).isEqualTo(2);
    }

    @Test
    @Description("Creating a rollout without weight value when multi assignment in enabled.")
    public void weightNotRequiredInMultiAssignmentMode() {
        enableMultiAssignments();
        createSimpleTestRolloutWithTargetsAndDistributionSet(10, 10, 2, "50", "80", ActionType.FORCED, null);
    }

    @Test
    @Description("Creating a rollout with a weight causes an error when multi assignment in disabled.")
    public void weightNotAllowedWhenMultiAssignmentModeNotEnabled() {
        Assertions.assertThatExceptionOfType(MultiAssignmentIsNotEnabledException.class)
                .isThrownBy(() -> createSimpleTestRolloutWithTargetsAndDistributionSet(10, 10, 2, "50", "80",
                        ActionType.FORCED, 66));
    }

    @Test
    @Description("Weight is validated and saved to the Rollout.")
    public void weightValidatedAndSaved() {
        final String targetPrefix = UUID.randomUUID().toString();
        testdataFactory.createTargets(4, targetPrefix);
        enableMultiAssignments();

        Assertions.assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> createTestRolloutWithTargetsAndDistributionSet(4, 2, "50", "80",
                        UUID.randomUUID().toString(), UUID.randomUUID().toString(), Action.WEIGHT_MAX + 1));
        Assertions.assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> createTestRolloutWithTargetsAndDistributionSet(4, 2, "50", "80",
                        UUID.randomUUID().toString(), UUID.randomUUID().toString(), Action.WEIGHT_MIN - 1));
        final Rollout createdRollout1 = createTestRolloutWithTargetsAndDistributionSet(4, 2, "50", "80",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), Action.WEIGHT_MAX);
        final Rollout createdRollout2 = createTestRolloutWithTargetsAndDistributionSet(4, 2, "50", "80",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), Action.WEIGHT_MIN);
        assertThat(rolloutRepository.findById(createdRollout1.getId()).get().getWeight()).get()
                .isEqualTo(Action.WEIGHT_MAX);
        assertThat(rolloutRepository.findById(createdRollout2.getId()).get().getWeight()).get()
                .isEqualTo(Action.WEIGHT_MIN);
    }

    @Test
    @Description("A Rollout with weight creats actions with weights")
    public void actionsWithWeightAreCreated() {
        final int amountOfTargets = 5;
        final int weight = 99;
        enableMultiAssignments();
        final Long rolloutId = createSimpleTestRolloutWithTargetsAndDistributionSet(amountOfTargets, 2, amountOfTargets,
                "80", "50", null, weight).getId();
        rolloutManagement.start(rolloutId);
        rolloutManagement.handleRollouts();
        final List<Action> actions = deploymentManagement.findActionsAll(PAGE).getContent();
        assertThat(actions).hasSize(amountOfTargets);
        assertThat(actions).allMatch(action -> action.getWeight().get() == weight);
    }

    @Test
    @Description("Rollout can be created without weight in single assignment and be started in multi assignment")
    public void createInSingleStartInMultiassigMode() {
        final int amountOfTargets = 5;
        final Long rolloutId = createSimpleTestRolloutWithTargetsAndDistributionSet(amountOfTargets, 2, amountOfTargets,
                "80", "50", null, null).getId();

        enableMultiAssignments();
        rolloutManagement.start(rolloutId);
        rolloutManagement.handleRollouts();
        final List<Action> actions = deploymentManagement.findActionsAll(PAGE).getContent();
        assertThat(actions).hasSize(amountOfTargets);
        assertThat(actions).allMatch(action -> !action.getWeight().isPresent());
    }

    private RolloutGroupCreate generateRolloutGroup(final int index, final Integer percentage,
            final String targetFilter) {
        return entityFactory.rolloutGroup().create().name("Group" + index).description("Group" + index + "desc")
                .targetPercentage(Float.valueOf(percentage)).targetFilterQuery(targetFilter);
    }

    private RolloutCreate generateTargetsAndRollout(final String rolloutName, final int amountTargetsForRollout) {
        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dsFor" + rolloutName);

        testdataFactory.createTargets(amountTargetsForRollout, rolloutName + "-", rolloutName);

        return entityFactory.rollout().create().name(rolloutName)
                .description("This is a test description for the rollout")
                .targetFilterQuery("controllerId==" + rolloutName + "-*").set(distributionSet);
    }

    private void validateRolloutGroupActionStatus(final RolloutGroup rolloutGroup,
            final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus) {
        final RolloutGroup rolloutGroupWithDetail = rolloutGroupManagement.getWithDetailedStatus(rolloutGroup.getId())
                .get();
        validateStatus(rolloutGroupWithDetail.getTotalTargetCountStatus(), expectedTargetCountStatus);
    }

    private void validateRolloutActionStatus(final Long rolloutId,
            final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus) {
        final Rollout rolloutWithDetail = rolloutManagement.getWithDetailedStatus(rolloutId).get();
        validateStatus(rolloutWithDetail.getTotalTargetCountStatus(), expectedTargetCountStatus);
    }

    private void validateStatus(final TotalTargetCountStatus totalTargetCountStatus,
            final Map<TotalTargetCountStatus.Status, Long> expectedTotalCountStates) {
        for (final Map.Entry<TotalTargetCountStatus.Status, Long> entry : expectedTotalCountStates.entrySet()) {
            final Long countReady = totalTargetCountStatus.getTotalTargetCountByStatus(entry.getKey());
            assertThat(countReady).as("targets in status " + entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    private Rollout createSimpleTestRolloutWithTargetsAndDistributionSet(final int amountTargetsForRollout,
            final int amountOtherTargets, final int groupSize, final String successCondition,
            final String errorCondition) {
        return createSimpleTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, amountOtherTargets,
                groupSize, successCondition, errorCondition, ActionType.FORCED, null);
    }

    private Rollout createSimpleTestRolloutWithTargetsAndDistributionSet(final int amountTargetsForRollout,
            final int amountOtherTargets, final int groupSize, final String successCondition,
            final String errorCondition, final ActionType actionType, final Integer weight) {
        final DistributionSet rolloutDS = testdataFactory.createDistributionSet("rolloutDS");
        testdataFactory.createTargets(amountTargetsForRollout, "rollout-", "rollout");
        testdataFactory.createTargets(amountOtherTargets, "others-", "rollout");
        final String filterQuery = "controllerId==rollout-*";
        return testdataFactory.createRolloutByVariables("test-rollout-name-1", "test-rollout-description-1", groupSize,
                filterQuery, rolloutDS, successCondition, errorCondition, actionType, weight);
    }

    private Rollout createTestRolloutWithTargetsAndDistributionSet(final int amountTargetsForRollout,
            final int groupSize, final String successCondition, final String errorCondition, final String rolloutName,
            final String targetPrefixName) {
        return createTestRolloutWithTargetsAndDistributionSet(amountTargetsForRollout, groupSize, successCondition,
                errorCondition, rolloutName, targetPrefixName, null);
    }

    private Rollout createTestRolloutWithTargetsAndDistributionSet(final int amountTargetsForRollout,
            final int groupSize, final String successCondition, final String errorCondition, final String rolloutName,
            final String targetPrefixName, final Integer weight) {
        final DistributionSet dsForRolloutTwo = testdataFactory.createDistributionSet("dsFor" + rolloutName);
        testdataFactory.createTargets(amountTargetsForRollout, targetPrefixName + "-", targetPrefixName);
        return testdataFactory.createRolloutByVariables(rolloutName, rolloutName + "description", groupSize,
                "controllerId==" + targetPrefixName + "-*", dsForRolloutTwo, successCondition, errorCondition,
                Action.ActionType.FORCED, weight);
    }

    private int changeStatusForAllRunningActions(final Rollout rollout, final Status status) {
        final List<Action> runningActions = findActionsByRolloutAndStatus(rollout, Status.RUNNING);
        for (final Action action : runningActions) {
            controllerManagement
                    .addUpdateActionStatus(entityFactory.actionStatus().create(action.getId()).status(status));
        }
        return runningActions.size();
    }

    private int changeStatusForRunningActions(final Rollout rollout, final Status status,
            final int amountOfTargetsToGetChanged) {
        final List<Action> runningActions = findActionsByRolloutAndStatus(rollout, Status.RUNNING);
        assertThat(runningActions.size()).isGreaterThanOrEqualTo(amountOfTargetsToGetChanged);
        for (int i = 0; i < amountOfTargetsToGetChanged; i++) {
            controllerManagement.addUpdateActionStatus(
                    entityFactory.actionStatus().create(runningActions.get(i).getId()).status(status));
        }
        return runningActions.size();
    }

    private static Map<TotalTargetCountStatus.Status, Long> createInitStatusMap() {
        final Map<TotalTargetCountStatus.Status, Long> map = new HashMap<>();
        for (final TotalTargetCountStatus.Status status : TotalTargetCountStatus.Status.values()) {
            map.put(status, 0L);
        }
        return map;
    }

    private static class SuccessConditionRolloutStatus implements SuccessCondition<RolloutStatus> {

        private final RolloutStatus rolloutStatus;

        public SuccessConditionRolloutStatus(final RolloutStatus rolloutStatus) {
            this.rolloutStatus = rolloutStatus;
        }

        @Override
        public boolean success(final RolloutStatus result) {
            return result.equals(rolloutStatus);
        }
    }

    private class RolloutStatusCallable implements Callable<RolloutStatus> {

        final Long rolloutId;

        RolloutStatusCallable(final Long rolloutId) {
            this.rolloutId = rolloutId;
        }

        @Override
        public RolloutStatus call() throws Exception {

            final Rollout myRollout = rolloutManagement.get(rolloutId).get();
            return myRollout.getStatus();

        }
    }

}
