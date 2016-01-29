/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository;

import static org.fest.assertions.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.eclipse.hawkbit.AbstractIntegrationTest;
import org.eclipse.hawkbit.TestDataUtil;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.ActionStatus;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupConditions;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupErrorAction;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupErrorCondition;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupSuccessCondition;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;

import ru.yandex.qatools.allure.annotations.Features;
import ru.yandex.qatools.allure.annotations.Stories;

/**
 * @author Michael Hirsch
 *
 */
@Features("Component Tests - Repository")
@Stories("Rollout Management")
public class RolloutManagementTest extends AbstractIntegrationTest {

    @Autowired
    private RolloutManagement rolloutManagement;

    @Test
    @Description("Verfiying that the rollout is created correctly, executing the filter and split up the targets in the correct group size.")
    public void creatingRolloutIsCorrectPersisted() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, "50", "80");

        // verify the split of the target and targetGroup
        final Page<RolloutGroup> rolloutGroups = rolloutManagement.findRolloutGroupsByRollout(createdRollout.getId(),
                pageReq);
        // we have total of #amountTargetsForRollout in rollouts splitted in
        // group size #groupSize
        assertThat(rolloutGroups).hasSize(amountGroups);
    }

    @Test
    @Description("Verfiying that when the rollout is started the actions for all targets in the rollout is created and the state of the first group is running as well as the corresponding actions")
    public void startRolloutSetFirstGroupAndActionsInRunningStateAndOthersInScheduleState() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, "50", "80");

        // start the rollout
        rolloutManagement.startRollout(createdRollout);

        // verify first group is running
        final RolloutGroup firstGroup = rolloutManagement
                .findRolloutGroupsByRollout(createdRollout.getId(),
                        new OffsetBasedPageRequest(0, 1, new Sort(Direction.ASC, "id"))).getContent().get(0);
        assertThat(firstGroup.getStatus()).isEqualTo(RolloutGroupStatus.RUNNING);

        // verify other groups are scheduled
        final List<RolloutGroup> scheduledGroups = rolloutManagement.findRolloutGroupsByRollout(createdRollout.getId(),
                new OffsetBasedPageRequest(1, 100, new Sort(Direction.ASC, "id"))).getContent();
        scheduledGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.SCHEDULED).as(
                "group which should be in scheduled state is in " + group.getStatus() + " state"));
        // verify that the first group actions has been started and are in state
        // running
        final List<Action> runningActions = deploymentManagement.findActionsByRolloutAndStatus(createdRollout,
                Status.RUNNING);
        assertThat(runningActions).hasSize(amountTargetsForRollout / amountGroups);
        // the rest targets are only scheduled
        assertThat(deploymentManagement.findActionsByRolloutAndStatus(createdRollout, Status.SCHEDULED)).hasSize(
                amountTargetsForRollout - (amountTargetsForRollout / amountGroups));
    }

    @Test
    @Description("Verfiying that a finish condition of a group is hit the next group of the rollout is also started")
    public void checkRunningRolloutsDoesNotStartNextGroupIfFinishConditionIsNotHit() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, "50", "80");

        // start the rollout
        rolloutManagement.startRollout(createdRollout);

        // set first actions in finish state so finish condition hits
        final List<Action> runningActions = deploymentManagement.findActionsByRolloutAndStatus(createdRollout,
                Status.RUNNING);
        // finish one action should be sufficient due the finish condition is at
        // 50%
        final Action action = runningActions.get(0);
        action.setStatus(Status.FINISHED);
        controllerManagament.addUpdateActionStatus(new ActionStatus(action, Status.FINISHED,
                System.currentTimeMillis(), ""), action);

        // check running rollouts again, now the finish condition should be hit
        // and should start the next group
        rolloutManagement.checkRunningRollouts(0);

        // verify that now the first and the second group are in running state
        final List<RolloutGroup> runningRolloutGroups = rolloutManagement.findRolloutGroupsByRollout(
                createdRollout.getId(), new OffsetBasedPageRequest(0, 2, new Sort(Direction.ASC, "id"))).getContent();
        runningRolloutGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.RUNNING).as(
                "group should be in running state because it should be started but it is in " + group.getStatus()
                        + " state"));

        // verify that the other groups are still in schedule state
        final List<RolloutGroup> scheduledRolloutGroups = rolloutManagement.findRolloutGroupsByRollout(
                createdRollout.getId(), new OffsetBasedPageRequest(2, 10, new Sort(Direction.ASC, "id"))).getContent();
        scheduledRolloutGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.SCHEDULED)
                .as("group should be in scheduled state because it should not be started but it is in "
                        + group.getStatus() + " state"));
    }

    @Test
    @Description("Verfiying that the error handling action of a group is executed to pause the current rollout")
    public void checkErrorHitOfGroupCallsErrorActionToPauseTheRollout() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, "50", "80");

        // start the rollout
        rolloutManagement.startRollout(createdRollout);

        // set both actions in error state so error condition is hit and error
        // action is executed
        final List<Action> runningActions = deploymentManagement.findActionsByRolloutAndStatus(createdRollout,
                Status.RUNNING);

        // finish actions with error
        for (final Action action : runningActions) {
            action.setStatus(Status.ERROR);
            controllerManagament.addUpdateActionStatus(
                    new ActionStatus(action, Status.ERROR, System.currentTimeMillis(), ""), action);
        }

        // check running rollouts again, now the error condition should be hit
        // and should execute the error action
        rolloutManagement.checkRunningRollouts(0);

        final Rollout rollout = rolloutManagement.findRolloutById(createdRollout.getId());
        // the rollout itself should be in paused based on the error action
        assertThat(rollout.getStatus()).isEqualTo(RolloutStatus.PAUSED);

        // the first rollout group should be in error state
        final List<RolloutGroup> errorGroup = rolloutManagement.findRolloutGroupsByRollout(createdRollout.getId(),
                new OffsetBasedPageRequest(0, 1, new Sort(Direction.ASC, "id"))).getContent();
        assertThat(errorGroup).hasSize(1);
        assertThat(errorGroup.get(0).getStatus()).isEqualTo(RolloutGroupStatus.ERROR);

        // all other groups should still be in scheduled state
        final List<RolloutGroup> scheduleGroups = rolloutManagement.findRolloutGroupsByRollout(createdRollout.getId(),
                new OffsetBasedPageRequest(1, 100, new Sort(Direction.ASC, "id"))).getContent();
        scheduleGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.SCHEDULED));
    }

    @Test
    @Description("Verfiying a paused rollout in case of error action hit can be resumed again")
    public void errorActionPausesRolloutAndRolloutGetsResumedStartsNextScheduledGroup() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, "50", "80");

        // start the rollout
        rolloutManagement.startRollout(createdRollout);

        // set both actions in error state so error condition is hit and error
        // action is executed
        final List<Action> runningActions = deploymentManagement.findActionsByRolloutAndStatus(createdRollout,
                Status.RUNNING);

        // finish actions with error
        for (final Action action : runningActions) {
            action.setStatus(Status.ERROR);
            controllerManagament.addUpdateActionStatus(
                    new ActionStatus(action, Status.ERROR, System.currentTimeMillis(), ""), action);
        }

        // check running rollouts again, now the error condition should be hit
        // and should execute the error action
        rolloutManagement.checkRunningRollouts(0);

        final Rollout rollout = rolloutManagement.findRolloutById(createdRollout.getId());
        // the rollout itself should be in paused based on the error action
        assertThat(rollout.getStatus()).isEqualTo(RolloutStatus.PAUSED);

        // all other groups should still be in scheduled state
        final List<RolloutGroup> scheduleGroups = rolloutManagement.findRolloutGroupsByRollout(createdRollout.getId(),
                new OffsetBasedPageRequest(1, 100, new Sort(Direction.ASC, "id"))).getContent();
        scheduleGroups.forEach(group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.SCHEDULED));

        // resume the rollout again after it gets paused by error action
        rolloutManagement.resumeRollout(rolloutManagement.findRolloutById(createdRollout.getId()));

        // the rollout should be running again
        assertThat(rolloutManagement.findRolloutById(createdRollout.getId()).getStatus()).isEqualTo(
                RolloutStatus.RUNNING);

        // checking rollouts again
        rolloutManagement.checkRunningRollouts(0);

        // next group should be running again after resuming the rollout
        final List<RolloutGroup> resumedGroups = rolloutManagement.findRolloutGroupsByRollout(createdRollout.getId(),
                new OffsetBasedPageRequest(1, 1, new Sort(Direction.ASC, "id"))).getContent();
        assertThat(resumedGroups).hasSize(1);
        assertThat(resumedGroups.get(0).getStatus()).isEqualTo(RolloutGroupStatus.RUNNING);
    }

    @Test
    @Description("Verfiying that the rollout is starting group after group and gets finished at the end")
    public void rolloutStartsGroupAfterGroupAndGetsFinished() {
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 15;
        final int amountGroups = 5;
        final Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, "50", "80");

        // start the rollout
        rolloutManagement.startRollout(createdRollout);
        // finish running actions, 2 actions should be finished
        assertThat(changeStatusForAllRunningActions(createdRollout, Status.FINISHED)).isEqualTo(2);

        // calculate the rest of the groups and finish them
        for (int groupsLeft = amountGroups - 1; groupsLeft >= 1; groupsLeft--) {
            // next check and start next group
            rolloutManagement.checkRunningRollouts(0);
            // finish running actions, 2 actions should be finished
            assertThat(changeStatusForAllRunningActions(createdRollout, Status.FINISHED)).isEqualTo(2);
            assertThat(rolloutManagement.findRolloutById(createdRollout.getId()).getStatus()).isEqualTo(
                    RolloutStatus.RUNNING);

        }
        // check rollout to see that all actions and all groups are finished and
        // so can go to FINISHED state of the rollout
        rolloutManagement.checkRunningRollouts(0);

        // verify all groups are in finished state
        rolloutManagement.findRolloutGroupsByRollout(createdRollout.getId(),
                new OffsetBasedPageRequest(0, 100, new Sort(Direction.ASC, "id"))).forEach(
                group -> assertThat(group.getStatus()).isEqualTo(RolloutGroupStatus.FINISHED));

        // verify that rollout itself is in finished state
        final Rollout findRolloutById = rolloutManagement.findRolloutById(createdRollout.getId());
        assertThat(findRolloutById.getStatus()).isEqualTo(RolloutStatus.FINISHED);
    }

    @Test
    @Description("Verify that the targets have the right status during the rollout.")
    public void countCorrectStatusForEachTargetDuringRollout() {

        // setup
        final int amountTargetsForRollout = 8;
        final int amountOtherTargets = 15;
        final int amountGroups = 4;
        final Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, "50", "80");

        // verify
        // targets have not started
        Map<TotalTargetCountStatus.Status, Long> validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.NOTSTARTED, 8L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        // test 1
        rolloutManagement.startRollout(createdRollout);

        // verify
        // 6 targets are ready and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.READY, 6L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        // test 2
        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.checkRunningRollouts(0);

        // verify
        // 4 targets are ready, 2 are finished and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.READY, 4L);
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 2L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        // test 3
        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.checkRunningRollouts(0);

        // verify
        // 2 targets are ready, 4 are finished and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.READY, 2L);
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 4L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        // test 4
        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.checkRunningRollouts(0);

        // verify
        // 0 targets are ready, 6 are finished and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 6L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        // test 5
        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.checkRunningRollouts(0);

        // verify
        // 0 targets are ready, 8 are finished and 0 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.FINISHED, 8L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

    }

    @Test
    @Description("Verify that the targets have the right status during the rollout when an error emerges.")
    public void countCorrectStatusForEachTargetDuringRolloutWithError() {

        // setup
        final int amountTargetsForRollout = 8;
        final int amountOtherTargets = 15;
        final int amountGroups = 4;
        final Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout,
                amountOtherTargets, amountGroups, "50", "80");

        // verify
        // 8 targets have not started
        Map<TotalTargetCountStatus.Status, Long> validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.NOTSTARTED, 8L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        // test
        rolloutManagement.startRollout(createdRollout);

        // verify
        // 6 targets are ready and 2 are running
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.READY, 6L);
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);

        // test
        changeStatusForAllRunningActions(createdRollout, Status.ERROR);
        rolloutManagement.checkRunningRollouts(0);

        // verify
        // 6 targets are ready and 2 are error
        validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.READY, 6L);
        validationMap.put(TotalTargetCountStatus.Status.ERROR, 2L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);
    }

    @Test
    @Description("Verify that the targets have the right status during the rollout when receiving the status of rollout groups.")
    public void countCorrectStatusForEachTargetGroupDuringRollout() {

        // setup
        final int amountTargetsForRollout = 9;
        final int amountOtherTargets = 15;
        final int amountGroups = 4;
        Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout, amountOtherTargets,
                amountGroups, "50", "80");

        // test
        rolloutManagement.startRollout(createdRollout);
        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.checkRunningRollouts(0);
        changeStatusForAllRunningActions(createdRollout, Status.FINISHED);
        rolloutManagement.checkRunningRollouts(0);

        // Verify
        // 4 targets finished (Group 1 and 2), 2 targets running (Group 3) and 2
        // targets ready (Group 4) and one 1 target ready (Group 5)
        createdRollout = rolloutManagement.findRolloutById(createdRollout.getId());
        final List<RolloutGroup> rolloutGruops = createdRollout.getRolloutGroups();
        Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 2L);
        validateRolloutGroupActionStatus(rolloutGruops.get(0), expectedTargetCountStatus);
        validateRolloutGroupActionStatus(rolloutGruops.get(1), expectedTargetCountStatus);
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validateRolloutGroupActionStatus(rolloutGruops.get(2), expectedTargetCountStatus);
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.READY, 2L);
        validateRolloutGroupActionStatus(rolloutGruops.get(3), expectedTargetCountStatus);
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.READY, 1L);
        validateRolloutGroupActionStatus(rolloutGruops.get(4), expectedTargetCountStatus);
    }

    @Test
    @Description("Verify that target actions of rollout get canceled when a manuel distribution sets assignment is done.")
    public void targetsOfRolloutGetsManuelDsAssignment() {

        // setup
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        Rollout createdRollout = createTestRolloutWithDistributionSet(amountTargetsForRollout, amountOtherTargets,
                amountGroups, "50", "80");
        final DistributionSet ds = createdRollout.getDistributionSet();

        // test case 1
        rolloutManagement.startRollout(createdRollout);
        createdRollout = rolloutManagement.findRolloutById(createdRollout.getId());

        // Verify
        // 5 are running
        final List<Action> runningActions = deploymentManagement.findActionsByRolloutAndStatus(createdRollout,
                Status.RUNNING);
        assertThat(runningActions.size()).isEqualTo(5);
        // 5 targets in the group and the DS has been assigned
        final List<RolloutGroup> rolloutGroups = createdRollout.getRolloutGroups();
        final Page<Target> targets = rolloutManagement.getRolloutGroupTargets(rolloutGroups.get(0),
                new OffsetBasedPageRequest(0, 20, new Sort(Direction.ASC, "id")));
        final List<Target> targetList = targets.getContent();
        assertThat(targetList.size()).isEqualTo(5);
        for (final Target t : targetList) {
            final DistributionSet assignedDs = t.getAssignedDistributionSet();
            assertThat(assignedDs.getId()).isEqualTo(ds.getId());
        }

        // test case 2
        // add the target that will get canceled
        final List<Target> targetToCancel = new ArrayList<Target>();
        targetToCancel.add(targetList.get(0));
        targetToCancel.add(targetList.get(1));
        targetToCancel.add(targetList.get(2));
        final DistributionSet dsForCancelTest = TestDataUtil.generateDistributionSet("dsForTest", softwareManagement,
                distributionSetManagement);
        deploymentManagement.assignDistributionSet(dsForCancelTest, targetToCancel);

        // verify
        // 3 targets are canceled, 2 are still running and 5 are ready
        final Map<TotalTargetCountStatus.Status, Long> validationMap = createInitStatusMap();
        validationMap.put(TotalTargetCountStatus.Status.RUNNING, 2L);
        validationMap.put(TotalTargetCountStatus.Status.CANCELLED, 3L);
        validationMap.put(TotalTargetCountStatus.Status.READY, 5L);
        validateRolloutActionStatus(createdRollout.getId(), validationMap);
    }

    @Test
    @Description("Verify that target actions of a rollout get cancelled when another rollout with same targets gets started.")
    public void targetsOfRolloutGetDistributionSetAssignmentByOtherRollout() {

        final int amountTargetsForRollout = 15;
        final int amountOtherTargets = 5;
        final int amountGroups = 3;
        Rollout rolloutOne = createTestRolloutWithDistributionSet(amountTargetsForRollout, amountOtherTargets,
                amountGroups, "50", "80");
        rolloutManagement.startRollout(rolloutOne);
        rolloutOne = rolloutManagement.findRolloutById(rolloutOne.getId());

        final DistributionSet dsForRolloutTwo = TestDataUtil.generateDistributionSet("dsForRolloutTwo",
                softwareManagement, distributionSetManagement);

        // same Filter = same targets
        final Rollout rolloutTwo = createRolloutWithVariables("rolloutTwo", "This is the description for rollout two",
                1, "controllerId==rollout-*", dsForRolloutTwo, "50", "80");
        changeStatusForAllRunningActions(rolloutOne, Status.FINISHED);
        rolloutManagement.checkRunningRollouts(0);

        // Verify that 5 targets are finished, 5 are running and 5 are ready.
        Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.READY, 5L);
        validateRolloutActionStatus(rolloutOne.getId(), expectedTargetCountStatus);

        rolloutManagement.startRollout(rolloutTwo);

        // Verify that 5 targets are finished and 10 are cancelled.
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.CANCELLED, 10L);
        validateRolloutActionStatus(rolloutOne.getId(), expectedTargetCountStatus);

    }

    @Test
    @Description("Verify that error status of DistributionSet installation during rollout can get rerun with second rollout so that all targets have some DistributionSet installed at the end.")
    public void startSecondRolloutAfterFristRolloutEndenWithErrors() {

        // setup 1
        final int amountTargetsForRollout = 15;
        final int amountOtherTargets = 0;
        final int amountGroups = 3;
        Rollout rolloutOne = createTestRolloutWithDistributionSet(amountTargetsForRollout, amountOtherTargets,
                amountGroups, "50", "80");
        final DistributionSet distributionSet = rolloutOne.getDistributionSet();
        rolloutManagement.startRollout(rolloutOne);
        rolloutOne = rolloutManagement.findRolloutById(rolloutOne.getId());

        // test case 1
        // Group one
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.checkRunningRollouts(0);
        // Group two
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.checkRunningRollouts(0);
        // Group three
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.checkRunningRollouts(0);

        // verify
        // 9 targets are finished and 6 have error
        Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 9L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.ERROR, 6L);
        validateRolloutActionStatus(rolloutOne.getId(), expectedTargetCountStatus);
        // rollout is finished
        rolloutOne = rolloutManagement.findRolloutById(rolloutOne.getId());
        assertThat(rolloutOne.getStatus()).isEqualTo(RolloutStatus.FINISHED);

        // setup 2
        Rollout rolloutTwo = createRolloutWithVariables("rolloutTwo", "This is the description for rollout two", 1,
                "controllerId==rollout-*", distributionSet, "50", "80");

        // test case 2
        rolloutManagement.startRollout(rolloutTwo);
        rolloutTwo = rolloutManagement.findRolloutById(rolloutTwo.getId());

        // Verify
        // 6 error targets are know running
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 6L);
        validateRolloutActionStatus(rolloutTwo.getId(), expectedTargetCountStatus);

        changeStatusForAllRunningActions(rolloutTwo, Status.FINISHED);
        final Page<Target> targetPage = targetManagement.findTargetByUpdateStatus(pageReq, TargetUpdateStatus.IN_SYNC);
        final List<Target> targetList = targetPage.getContent();

        // 15 targets in finished/IN_SYNC status and same DS assigned
        assertThat(targetList.size()).isEqualTo(amountTargetsForRollout);
        for (final Target t : targetList) {
            final DistributionSet ds = t.getAssignedDistributionSet();
            assertThat(ds).isEqualTo(distributionSet);
        }
    }

    @Test
    @Description("Verify that the rollout moves to the next group when the success condition was achieved and the error condition was not exceeded.")
    public void successConditionAchievedAndErrorConditionNotExceeded() {

        // setup
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "80";
        Rollout rolloutOne = createTestRolloutWithDistributionSet(amountTargetsForRollout, amountOtherTargets,
                amountGroups, successCondition, errorCondition);

        // test
        rolloutManagement.startRollout(rolloutOne);
        rolloutOne = rolloutManagement.findRolloutById(rolloutOne.getId());
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.checkRunningRollouts(0);

        // verify: 40% error but 60% finished -> should move to next group
        final List<RolloutGroup> rolloutGruops = rolloutOne.getRolloutGroups();
        final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        validateRolloutGroupActionStatus(rolloutGruops.get(1), expectedTargetCountStatus);

    }

    @Test
    @Description("Verify that the rollout does not move to the next group when the sucess condition was not achieved.")
    public void successConditionNotAchieved() {

        // setup
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final String successCondition = "80";
        final String errorCondition = "90";
        Rollout rolloutOne = createTestRolloutWithDistributionSet(amountTargetsForRollout, amountOtherTargets,
                amountGroups, successCondition, errorCondition);

        // test
        rolloutManagement.startRollout(rolloutOne);
        rolloutOne = rolloutManagement.findRolloutById(rolloutOne.getId());
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.checkRunningRollouts(0);

        // verify: 40% error and 60% finished -> should not move to next group
        // because successCondition 80%
        rolloutOne = rolloutManagement.findRolloutById(rolloutOne.getId());
        final List<RolloutGroup> rolloutGruops = rolloutOne.getRolloutGroups();
        final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.READY, 5L);
        validateRolloutGroupActionStatus(rolloutGruops.get(1), expectedTargetCountStatus);
    }

    @Test
    @Description("Verify that the rollout pauses when the error condition was exceeded.")
    public void errorConditionExceeded() {

        // setup
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final String successCondition = "50";
        final String errorCondition = "20";
        Rollout rolloutOne = createTestRolloutWithDistributionSet(amountTargetsForRollout, amountOtherTargets,
                amountGroups, successCondition, errorCondition);

        // test
        rolloutManagement.startRollout(rolloutOne);
        rolloutOne = rolloutManagement.findRolloutById(rolloutOne.getId());
        changeStatusForRunningActions(rolloutOne, Status.ERROR, 2);
        changeStatusForRunningActions(rolloutOne, Status.FINISHED, 3);
        rolloutManagement.checkRunningRollouts(0);

        // verify: 40% error -> should pause because errorCondition is 20%
        rolloutOne = rolloutManagement.findRolloutById(rolloutOne.getId());
        assertThat(RolloutStatus.PAUSED).isEqualTo(rolloutOne.getStatus());
    }

    @Test
    @Description("Verify that all rollouts are returns with expected target statuses.")
    public void findAllRolloutsWithDetailedStatus() {

        // setup
        final int amountTargetsForRollout = 12;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final Rollout rolloutA = createRolloutWithContainedTargets(amountTargetsForRollout, amountOtherTargets,
                amountGroups, "50", "80", "RolloutA", "RolloutA");
        rolloutManagement.startRollout(rolloutA);

        final int amountTargetsForRollout2 = 10;
        final int amountOtherTargets2 = 0;
        final int amountGroups2 = 2;
        final Rollout rolloutB = createRolloutWithContainedTargets(amountTargetsForRollout2, amountOtherTargets2,
                amountGroups2, "50", "80", "RolloutB", "RolloutB");
        rolloutManagement.startRollout(rolloutB);
        changeStatusForAllRunningActions(rolloutB, Status.FINISHED);
        rolloutManagement.checkRunningRollouts(0);

        final int amountTargetsForRollout3 = 10;
        final int amountOtherTargets3 = 0;
        final int amountGroups3 = 2;
        final Rollout rolloutC = createRolloutWithContainedTargets(amountTargetsForRollout3, amountOtherTargets3,
                amountGroups3, "50", "80", "RolloutC", "RolloutC");
        rolloutManagement.startRollout(rolloutC);
        changeStatusForAllRunningActions(rolloutC, Status.ERROR);
        rolloutManagement.checkRunningRollouts(0);

        final int amountTargetsForRollout4 = 15;
        final int amountOtherTargets4 = 0;
        final int amountGroups4 = 3;
        final Rollout rolloutD = createRolloutWithContainedTargets(amountTargetsForRollout4, amountOtherTargets4,
                amountGroups4, "50", "80", "RolloutD", "RolloutD");
        rolloutManagement.startRollout(rolloutD);
        changeStatusForRunningActions(rolloutD, Status.ERROR, 1);
        rolloutManagement.checkRunningRollouts(0);
        changeStatusForAllRunningActions(rolloutD, Status.FINISHED);
        rolloutManagement.checkRunningRollouts(0);

        // test
        final Page<Rollout> rolloutPage = rolloutManagement.findAllWithDetailedStatus(new OffsetBasedPageRequest(0,
                100, new Sort(Direction.ASC, "name")));
        final List<Rollout> rolloutList = rolloutPage.getContent();

        // validate rolloutA -> 6 running and 6 ready
        Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 6L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.READY, 6L);
        validateRolloutActionStatus(rolloutList.get(0).getId(), expectedTargetCountStatus);

        // validate rolloutB -> 5 running and 5 finished
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        validateRolloutActionStatus(rolloutList.get(1).getId(), expectedTargetCountStatus);

        // validate rolloutC -> 5 running and 5 error
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.ERROR, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.READY, 5L);
        validateRolloutActionStatus(rolloutList.get(2).getId(), expectedTargetCountStatus);

        // validate rolloutD -> 1, error, 4 finished, 5 running and 5 ready
        expectedTargetCountStatus = createInitStatusMap();
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.ERROR, 1L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.FINISHED, 4L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.RUNNING, 5L);
        expectedTargetCountStatus.put(TotalTargetCountStatus.Status.READY, 5L);
        validateRolloutActionStatus(rolloutList.get(3).getId(), expectedTargetCountStatus);
    }

    @Test
    @Description("Verify the count of existing rollouts.")
    public void rightCountForAllRollouts() {
        // setup
        final int amountTargetsForRollout = 6;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        for (int i = 1; i <= 10; i++) {
            createRolloutWithContainedTargets(amountTargetsForRollout, amountOtherTargets, amountGroups, "50", "80",
                    "Rollout" + i, "Rollout" + i);
        }
        // test
        final Long count = rolloutManagement.countRolloutsAll();

        // verify
        assertThat(count).isEqualTo(10L);
    }

    @Test
    @Description("Verify the count of filtered existing rollouts.")
    public void countRolloutsAllByFilters() {

        // setup
        final int amountTargetsForRollout = 6;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        for (int i = 1; i <= 5; i++) {
            createRolloutWithContainedTargets(amountTargetsForRollout, amountOtherTargets, amountGroups, "50", "80",
                    "Rollout" + i, "Rollout" + i);
        }
        for (int i = 1; i <= 5; i++) {
            createRolloutWithContainedTargets(amountTargetsForRollout, amountOtherTargets, amountGroups, "50", "80",
                    "SomethingElse" + i, "SomethingElse" + i);
        }

        // test
        final Long count = rolloutManagement.countRolloutsAllByFilters("Rollout%");

        // verify
        assertThat(count).isEqualTo(5L);

    }

    @Test
    @Description("Verify that the filtering and soring ascending for rollout is working correctly.")
    public void findRolloutByFilters() {

        // setup
        final int amountTargetsForRollout = 6;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        for (int i = 1; i <= 5; i++) {
            createRolloutWithContainedTargets(amountTargetsForRollout, amountOtherTargets, amountGroups, "50", "80",
                    "Rollout" + i, "Rollout" + i);
        }
        for (int i = 1; i <= 8; i++) {
            createRolloutWithContainedTargets(amountTargetsForRollout, amountOtherTargets, amountGroups, "50", "80",
                    "SomethingElse" + i, "SomethingElse" + i);
        }

        // TODO Change method name in rolloutManagement
        // test
        final Slice<Rollout> rollout = rolloutManagement.findRolloutByFilters(new OffsetBasedPageRequest(0, 100,
                new Sort(Direction.ASC, "name")), "Rollout%");

        // verify
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

        // setup
        final int amountTargetsForRollout = 12;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final String rolloutName = "Rollout137";
        final Rollout rolloutCreated = createRolloutWithContainedTargets(amountTargetsForRollout, amountOtherTargets,
                amountGroups, "50", "80", rolloutName, "RolloutA");

        // test
        final Rollout rolloutFound = rolloutManagement.findRolloutByName(rolloutName);

        // verify
        assertThat(rolloutCreated).isEqualTo(rolloutFound);

    }

    @Test
    @Description("Verify that the percent count is acting like aspected when targets move to the status finished or error.")
    public void getFinishedPercentForRunningGroup() {

        // setup
        final int amountTargetsForRollout = 10;
        final int amountOtherTargets = 0;
        final int amountGroups = 2;
        final String rolloutName = "MyRollout";
        Rollout myRollout = createRolloutWithContainedTargets(amountTargetsForRollout, amountOtherTargets,
                amountGroups, "50", "80", rolloutName, rolloutName);
        rolloutManagement.startRollout(myRollout);
        changeStatusForRunningActions(myRollout, Status.FINISHED, 2);
        rolloutManagement.checkRunningRollouts(0);
        myRollout = rolloutManagement.findRolloutById(myRollout.getId());

        // test
        float percent = rolloutManagement.getFinishedPercentForRunningGroup(myRollout, myRollout.getRolloutGroups()
                .get(0));

        // verify
        assertThat(percent).isEqualTo(40);

        changeStatusForRunningActions(myRollout, Status.FINISHED, 3);
        rolloutManagement.checkRunningRollouts(0);

        // test
        percent = rolloutManagement.getFinishedPercentForRunningGroup(myRollout, myRollout.getRolloutGroups().get(0));

        // verify
        assertThat(percent).isEqualTo(100);

        changeStatusForRunningActions(myRollout, Status.FINISHED, 4);
        changeStatusForAllRunningActions(myRollout, Status.ERROR);
        rolloutManagement.checkRunningRollouts(0);

        // test
        percent = rolloutManagement.getFinishedPercentForRunningGroup(myRollout, myRollout.getRolloutGroups().get(1));

        // verify
        assertThat(percent).isEqualTo(80);
    }

    @Test
    @Description("Verify that the expected targets are returned for the rollout groups.")
    public void findRolloutGroupTargets() {

        // setup
        final int amountTargetsForRollout = 15;
        final int amountOtherTargets = 0;
        final int amountGroups = 3;
        final String rolloutName = "MyRollout";
        Rollout myRollout = createRolloutWithContainedTargets(amountTargetsForRollout, amountOtherTargets,
                amountGroups, "50", "80", rolloutName, rolloutName);
        rolloutManagement.startRollout(myRollout);
        myRollout = rolloutManagement.findRolloutById(myRollout.getId());
        final List<RolloutGroup> rolloutGroups = myRollout.getRolloutGroups();

        final Specification<Target> specification = new Specification<Target>() {
            @Override
            public Predicate toPredicate(final Root<Target> root, final CriteriaQuery<?> query, final CriteriaBuilder cb) {
                // TODO criteria anpassen
                return cb.conjunction();
            }
        };

        // test group 1
        Page<Target> targetPage = rolloutManagement.findRolloutGroupTargets(rolloutGroups.get(0), specification,
                new OffsetBasedPageRequest(0, 100, new Sort(Direction.ASC, "name")));
        final List<Target> targetlistGroup1 = targetPage.getContent();

        // TODO change --> verify in a different way
        // verify
        assertThat(targetlistGroup1.size()).isEqualTo(5);
        int i = 0;
        for (final Target t : targetlistGroup1) {
            assertThat(t.getName()).as("MyRollout--0000" + i);
            i++;
        }

        targetPage = rolloutManagement.findRolloutGroupTargets(rolloutGroups.get(1), specification,
                new OffsetBasedPageRequest(0, 100, new Sort(Direction.ASC, "name")));
        final List<Target> targetlistGroup2 = targetPage.getContent();
        assertThat(targetlistGroup2.size()).isEqualTo(5);
        for (final Target t : targetlistGroup1) {
            assertThat(t.getName()).as("MyRollout--0000" + i);
            i++;
        }

    }

    private void validateRolloutGroupActionStatus(final RolloutGroup rolloutGroup,
            final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus) {
        final RolloutGroup rolloutGroupWithDetail = rolloutManagement.getRolloutGroupDetailedStatus(rolloutGroup
                .getId());
        validateStatus(rolloutGroupWithDetail.getTotalTargetCountStatus(), expectedTargetCountStatus);
    }

    private void validateRolloutActionStatus(final Long rolloutId,
            final Map<TotalTargetCountStatus.Status, Long> expectedTargetCountStatus) {
        final Rollout rolloutWithDetail = rolloutManagement.getRolloutDetailedStatus(rolloutId);
        validateStatus(rolloutWithDetail.getTotalTargetCountStatus(), expectedTargetCountStatus);
    }

    private void validateStatus(final TotalTargetCountStatus totalTargetCountStatus,
            final Map<TotalTargetCountStatus.Status, Long> expectedTotalCountStates) {
        for (final Map.Entry<TotalTargetCountStatus.Status, Long> entry : expectedTotalCountStates.entrySet()) {
            final Long countReady = totalTargetCountStatus.getTotalCountByStatus(entry.getKey());
            assertThat(countReady).isEqualTo(entry.getValue());
        }
    }

    private Rollout createTestRolloutWithDistributionSet(final int amountTargetsForRollout,
            final int amountOtherTargets, final int groupSize, final String successCondition,
            final String errorCondition) {
        // setup - distribution set
        final SoftwareModule ah = softwareManagement.createSoftwareModule(new SoftwareModule(appType, "agent-hub",
                "1.0.1", null, ""));
        final SoftwareModule jvm = softwareManagement.createSoftwareModule(new SoftwareModule(runtimeType,
                "oracle-jre", "1.7.2", null, ""));
        final SoftwareModule os = softwareManagement.createSoftwareModule(new SoftwareModule(osType, "poky", "3.0.2",
                null, ""));
        final DistributionSet rolloutDS = distributionSetManagement.createDistributionSet(TestDataUtil
                .buildDistributionSet("rolloutDS", "0.0.0", standardDsType, os, jvm, ah));

        // setup - targets
        targetManagement
                .createTargets(TestDataUtil.buildTargetFixtures(amountTargetsForRollout, "rollout-", "rollout"));
        targetManagement.createTargets(TestDataUtil.buildTargetFixtures(amountOtherTargets, "others-", "rollout"));

        final String filterQuery = "controllerId==rollout-*";
        return createRolloutWithVariables("test-rollout-name-1", "test-rollout-description-1", groupSize, filterQuery,
                rolloutDS, successCondition, errorCondition);
    }

    private Rollout createRolloutWithContainedTargets(final int amountTargetsForRollout, final int amountOtherTargets,
            final int groupSize, final String successCondition, final String errorCondition, final String rolloutName,
            final String targetPrefixName) {
        final DistributionSet dsForRolloutTwo = TestDataUtil.generateDistributionSet("dsFor" + rolloutName,
                softwareManagement, distributionSetManagement);
        targetManagement.createTargets(TestDataUtil.buildTargetFixtures(amountTargetsForRollout,
                targetPrefixName + "-", targetPrefixName));
        return createRolloutWithVariables(rolloutName, rolloutName + "description", groupSize, "controllerId=="
                + targetPrefixName + "-*", dsForRolloutTwo, successCondition, errorCondition);
    }

    private Rollout createRolloutWithVariables(final String rolloutName, final String rolloutDescription,
            final int groupSize, final String filterQuery, final DistributionSet distributionSet,
            final String successCondition, final String errorCondition) {
        // setup - variables
        final RolloutGroupConditions conditions = new RolloutGroup.RolloutGroupConditionBuilder()
                .successCondition(RolloutGroupSuccessCondition.THRESHOLD, successCondition)
                .errorCondition(RolloutGroupErrorCondition.THRESHOLD, errorCondition)
                .errorAction(RolloutGroupErrorAction.PAUSE, null).build();
        // creating rollout
        final Rollout rolloutToCreate = new Rollout();
        rolloutToCreate.setName(rolloutName);
        rolloutToCreate.setDescription(rolloutDescription);
        rolloutToCreate.setTargetFilterQuery(filterQuery);
        rolloutToCreate.setDistributionSet(distributionSet);
        return rolloutManagement.createRollout(rolloutToCreate, groupSize, conditions);
    }

    private int changeStatusForAllRunningActions(final Rollout rollout, final Status status) {
        // set both actions in error state so error condition is hit and error
        // action is executed
        final List<Action> runningActions = deploymentManagement.findActionsByRolloutAndStatus(rollout, Status.RUNNING);
        // finish actions with error
        for (final Action action : runningActions) {
            action.setStatus(status);
            controllerManagament.addUpdateActionStatus(
                    new ActionStatus(action, status, System.currentTimeMillis(), ""), action);
        }
        return runningActions.size();
    }

    private int changeStatusForRunningActions(final Rollout rollout, final Status status,
            final int amountOfTargetsToGetChanged) {
        final List<Action> runningActions = deploymentManagement.findActionsByRolloutAndStatus(rollout, Status.RUNNING);
        assertThat(runningActions.size()).isGreaterThanOrEqualTo(amountOfTargetsToGetChanged);
        for (int i = 0; i < amountOfTargetsToGetChanged; i++) {
            controllerManagament.addUpdateActionStatus(
                    new ActionStatus(runningActions.get(i), status, System.currentTimeMillis(), ""),
                    runningActions.get(i));
        }
        return runningActions.size();
    }

    private Map<TotalTargetCountStatus.Status, Long> createInitStatusMap() {
        final Map<TotalTargetCountStatus.Status, Long> map = new HashMap<TotalTargetCountStatus.Status, Long>();
        for (final TotalTargetCountStatus.Status status : TotalTargetCountStatus.Status.values()) {
            map.put(status, 0L);
        }
        return map;
    }

}
