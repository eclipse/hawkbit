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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolationException;

import org.assertj.core.api.Assertions;
import org.eclipse.hawkbit.repository.ActionStatusFields;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.event.remote.MultiActionEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.CancelTargetAssignmentEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.ForceQuitActionNotAllowedException;
import org.eclipse.hawkbit.repository.exception.IncompleteDistributionSetException;
import org.eclipse.hawkbit.repository.exception.MultiAssignmentIsNotEnabledException;
import org.eclipse.hawkbit.repository.exception.NoWeightProvidedInMultiAssignmentModeException;
import org.eclipse.hawkbit.repository.jpa.configuration.Constants;
import org.eclipse.hawkbit.repository.jpa.model.JpaAction;
import org.eclipse.hawkbit.repository.jpa.model.JpaActionStatus;
import org.eclipse.hawkbit.repository.jpa.model.JpaDistributionSet;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget;
import org.eclipse.hawkbit.repository.jpa.specifications.DistributionSetSpecification;
import org.eclipse.hawkbit.repository.jpa.specifications.SpecificationsBuilder;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.ActionStatus;
import org.eclipse.hawkbit.repository.model.DeploymentRequest;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.DistributionSetTag;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort.Direction;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Test class testing the functionality of triggering a deployment of
 * {@link DistributionSet}s to {@link Target}s.
 *
 */
@Feature("Component Tests - Repository")
@Story("Deployment Management")
public class DeploymentManagementTest extends AbstractJpaIntegrationTest {

    private static final boolean STATE_ACTIVE = true;
    private static final boolean STATE_INACTIVE = false;

    @Test
    @Description("Verifies that management get access react as specfied on calls for non existing entities by means "
            + "of Optional not present.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void nonExistingEntityAccessReturnsNotPresent() {
        assertThat(deploymentManagement.findAction(1234L)).isNotPresent();
        assertThat(deploymentManagement.findActionWithDetails(NOT_EXIST_IDL)).isNotPresent();
    }

    @Test
    @Description("Verifies that management queries react as specfied on calls for non existing entities "
            + " by means of throwing EntityNotFoundException.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1) })
    public void entityQueriesReferringToNotExistingEntitiesThrowsException() {
        final Target target = testdataFactory.createTarget();
        final String dsName = "DistributionSet";

        verifyThrownExceptionBy(() -> assignDistributionSet(NOT_EXIST_IDL, target.getControllerId()), dsName);

        verifyThrownExceptionBy(() -> deploymentManagement.cancelAction(NOT_EXIST_IDL), "Action");
        verifyThrownExceptionBy(() -> deploymentManagement.countActionsByTarget(NOT_EXIST_ID), "Target");
        verifyThrownExceptionBy(() -> deploymentManagement.countActionsByTarget("xxx", NOT_EXIST_ID), "Target");

        verifyThrownExceptionBy(() -> deploymentManagement.findActionsByDistributionSet(PAGE, NOT_EXIST_IDL),
                "DistributionSet");
        verifyThrownExceptionBy(() -> deploymentManagement.findActionsByTarget(NOT_EXIST_ID, PAGE), "Target");
        verifyThrownExceptionBy(() -> deploymentManagement.findActionsByTarget("id==*", NOT_EXIST_ID, PAGE), "Target");

        verifyThrownExceptionBy(() -> deploymentManagement.findActiveActionsByTarget(PAGE, NOT_EXIST_ID), "Target");
        verifyThrownExceptionBy(() -> deploymentManagement.findInActiveActionsByTarget(PAGE, NOT_EXIST_ID), "Target");
        verifyThrownExceptionBy(() -> deploymentManagement.forceQuitAction(NOT_EXIST_IDL), "Action");
        verifyThrownExceptionBy(() -> deploymentManagement.forceTargetAction(NOT_EXIST_IDL), "Action");
    }

    @Test
    @Description("Test verifies that the repistory retrieves the action including all defined (lazy) details.")
    public void findActionWithLazyDetails() {
        final DistributionSet testDs = testdataFactory.createDistributionSet("TestDs", "1.0",
                new ArrayList<DistributionSetTag>());
        final List<Target> testTarget = testdataFactory.createTargets(1);
        // one action with one action status is generated
        final Long actionId = getFirstAssignedActionId(assignDistributionSet(testDs, testTarget));
        final Action action = deploymentManagement.findActionWithDetails(actionId).get();

        assertThat(action.getDistributionSet()).as("DistributionSet in action").isNotNull();
        assertThat(action.getTarget()).as("Target in action").isNotNull();
        assertThat(deploymentManagement.getAssignedDistributionSet(action.getTarget().getControllerId()).get())
                .as("AssignedDistributionSet of target in action").isNotNull();

    }

    @Test
    @Description("Test verifies that actions of a target are found by using id-based search.")
    public void findActionByTargetId() {
        final DistributionSet testDs = testdataFactory.createDistributionSet("TestDs", "1.0",
                new ArrayList<DistributionSetTag>());
        final List<Target> testTarget = testdataFactory.createTargets(1);
        // one action with one action status is generated
        final Long actionId = getFirstAssignedActionId(assignDistributionSet(testDs, testTarget));

        // act
        final Slice<Action> actions = deploymentManagement.findActionsByTarget(testTarget.get(0).getControllerId(),
                PAGE);
        final Long count = deploymentManagement.countActionsByTarget(testTarget.get(0).getControllerId());

        assertThat(count).as("One Action for target").isEqualTo(1L).isEqualTo(actions.getContent().size());
        assertThat(actions.getContent().get(0).getId()).as("Action of target").isEqualTo(actionId);
    }

    @Test
    @Description("Test verifies that the 'max actions per target' quota is enforced.")
    public void assertMaxActionsPerTargetQuotaIsEnforced() {

        final int maxActions = quotaManagement.getMaxActionsPerTarget();
        final Target testTarget = testdataFactory.createTarget();
        final DistributionSet ds1 = testdataFactory.createDistributionSet("ds1");
        final int weight = 500;

        enableMultiAssignments();
        for (int i = 0; i < maxActions; i++) {
            deploymentManagement.offlineAssignedDistributionSets(Collections
                    .singletonList(new SimpleEntry<String, Long>(testTarget.getControllerId(), ds1.getId())));
        }

        assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> assignDistributionSet(ds1.getId(),
                        Collections.singletonList(testTarget.getControllerId()),weight));
    }

    @Test
    @Description("An assignment request with more assignments than allowed by 'maxTargetDistributionSetAssignmentsPerManualAssignment' quota throws an exception.")
    public void assignmentRequestThatIsTooLarge() {
        final int maxActions = quotaManagement.getMaxTargetDistributionSetAssignmentsPerManualAssignment();
        final DistributionSet ds1 = testdataFactory.createDistributionSet("1");
        final DistributionSet ds2 = testdataFactory.createDistributionSet("2");

        final List<Target> targets = testdataFactory.createTargets(maxActions, "assignmentTest1");
        assignDistributionSet(ds1, targets);

        targets.add(testdataFactory.createTarget("assignmentTest2"));
        assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> assignDistributionSet(ds2, targets));
    }

    @Test
    @Description("Test verifies that action-states of an action are found by using id-based search.")
    public void findActionStatusByActionId() {
        final DistributionSet testDs = testdataFactory.createDistributionSet("TestDs", "1.0", Collections.emptyList());
        final List<Target> testTarget = testdataFactory.createTargets(1);
        // one action with one action status is generated
        final Long actionId = getFirstAssignedActionId(assignDistributionSet(testDs, testTarget));
        final Slice<Action> actions = deploymentManagement.findActionsByTarget(testTarget.get(0).getControllerId(),
                PAGE);
        final ActionStatus expectedActionStatus = ((JpaAction) actions.getContent().get(0)).getActionStatus().get(0);

        // act
        final Page<ActionStatus> actionStates = deploymentManagement.findActionStatusByAction(PAGE, actionId);

        assertThat(actionStates.getContent()).hasSize(1);
        assertThat(actionStates.getContent().get(0)).as("Action-status of action").isEqualTo(expectedActionStatus);
    }

    @Test
    @Description("Test verifies that messages of an action-status are found by using id-based search.")
    public void findMessagesByActionStatusId() {
        final DistributionSet testDs = testdataFactory.createDistributionSet("TestDs", "1.0",
                new ArrayList<DistributionSetTag>());
        final List<Target> testTarget = testdataFactory.createTargets(1);
        // one action with one action status is generated
        final Long actionId = getFirstAssignedActionId(assignDistributionSet(testDs, testTarget));
        // create action-status entry with one message
        controllerManagement.addUpdateActionStatus(entityFactory.actionStatus().create(actionId)
                .status(Action.Status.FINISHED).messages(Collections.singletonList("finished message")));
        final Page<ActionStatus> actionStates = deploymentManagement.findActionStatusByAction(PAGE, actionId);

        // find newly created action-status entry with message
        final JpaActionStatus actionStatusWithMessage = actionStates.getContent().stream().map(c -> (JpaActionStatus) c)
                .filter(entry -> entry.getMessages() != null && entry.getMessages().size() > 0).findFirst().get();
        final String expectedMsg = actionStatusWithMessage.getMessages().get(0);

        // act
        final Page<String> messages = deploymentManagement.findMessagesByActionStatusId(PAGE,
                actionStatusWithMessage.getId());

        assertThat(actionStates.getTotalElements()).as("Two action-states in total").isEqualTo(2L);
        assertThat(messages.getContent().get(0)).as("Message of action-status").isEqualTo(expectedMsg);
    }

    @Test
    @Description("Ensures that tag to distribution set assignment that does not exist will cause EntityNotFoundException.")
    public void assignDistributionSetToTagThatDoesNotExistThrowsException() {
        final List<Long> assignDS = Lists.newArrayListWithExpectedSize(5);
        for (int i = 0; i < 4; i++) {
            assignDS.add(testdataFactory.createDistributionSet("DS" + i, "1.0", Collections.emptyList()).getId());
        }
        // not exists
        assignDS.add(100L);

        final DistributionSetTag tag = distributionSetTagManagement.create(entityFactory.tag().create().name("Tag1"));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> distributionSetManagement.assignTag(assignDS, tag.getId()))
                .withMessageContaining("DistributionSet").withMessageContaining(String.valueOf(100L));
    }

    @Test
    @Description("Test verifies that an assignment with automatic cancelation works correctly even if the update is split into multiple partitions on the database.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = Constants.MAX_ENTRIES_IN_STATEMENT + 10),
            @Expect(type = TargetUpdatedEvent.class, count = 2 * (Constants.MAX_ENTRIES_IN_STATEMENT + 10)),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 2),
            @Expect(type = ActionCreatedEvent.class, count = 2 * (Constants.MAX_ENTRIES_IN_STATEMENT + 10)),
            @Expect(type = CancelTargetAssignmentEvent.class, count = Constants.MAX_ENTRIES_IN_STATEMENT + 10),
            @Expect(type = ActionUpdatedEvent.class, count = Constants.MAX_ENTRIES_IN_STATEMENT + 10),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6) })
    public void multiAssigmentHistoryOverMultiplePagesResultsInTwoActiveAction() {

        final DistributionSet cancelDs = testdataFactory.createDistributionSet("Canceled DS", "1.0",
                Collections.emptyList());

        final DistributionSet cancelDs2 = testdataFactory.createDistributionSet("Canceled DS", "1.2",
                Collections.emptyList());

        final List<Target> targets = testdataFactory.createTargets(Constants.MAX_ENTRIES_IN_STATEMENT + 10);

        assertThat(deploymentManagement.countActionsAll()).isEqualTo(0);

        assignDistributionSet(cancelDs, targets).getAssignedEntity();
        assertThat(deploymentManagement.countActionsAll()).isEqualTo(Constants.MAX_ENTRIES_IN_STATEMENT + 10);
        assignDistributionSet(cancelDs2, targets).getAssignedEntity();
        assertThat(deploymentManagement.countActionsAll()).isEqualTo(2 * (Constants.MAX_ENTRIES_IN_STATEMENT + 10));
    }

    @Test
    @Description("Cancels multiple active actions on a target. Expected behaviour is that with two active "
            + "actions after canceling the second active action the first one is still running as it is not touched by the cancelation. After canceling the first one "
            + "also the target goes back to IN_SYNC as no open action is left.")
    public void manualCancelWithMultipleAssignmentsCancelLastOneFirst() {
        final Action action = prepareFinishedUpdate("4712", "installed", true);
        final Target target = action.getTarget();
        final DistributionSet dsFirst = testdataFactory.createDistributionSet("", true);
        final DistributionSet dsSecond = testdataFactory.createDistributionSet("2", true);
        final DistributionSet dsInstalled = action.getDistributionSet();

        // check initial status
        assertThat(targetManagement.getByControllerID("4712").get().getUpdateStatus()).as("target has update status")
                .isEqualTo(TargetUpdateStatus.IN_SYNC);

        // assign the two sets in a row
        JpaAction firstAction = assignSet(target, dsFirst);
        JpaAction secondAction = assignSet(target, dsSecond);

        assertThat(actionRepository.findAll()).as("wrong size of actions").hasSize(3);
        assertThat(actionStatusRepository.findAll()).as("wrong size of action status").hasSize(5);

        // we cancel second -> back to first
        deploymentManagement.cancelAction(secondAction.getId());
        secondAction = (JpaAction) deploymentManagement.findActionWithDetails(secondAction.getId()).get();
        // confirm cancellation
        controllerManagement.addCancelActionStatus(
                entityFactory.actionStatus().create(secondAction.getId()).status(Status.CANCELED));
        assertThat(actionStatusRepository.findAll()).as("wrong size of actions status").hasSize(7);
        assertThat(deploymentManagement.getAssignedDistributionSet("4712").get()).as("wrong ds").isEqualTo(dsFirst);
        assertThat(targetManagement.getByControllerID("4712").get().getUpdateStatus()).as("wrong update status")
                .isEqualTo(TargetUpdateStatus.PENDING);

        // we cancel first -> back to installed
        deploymentManagement.cancelAction(firstAction.getId());
        firstAction = (JpaAction) deploymentManagement.findActionWithDetails(firstAction.getId()).get();
        // confirm cancellation
        controllerManagement.addCancelActionStatus(
                entityFactory.actionStatus().create(firstAction.getId()).status(Status.CANCELED));
        assertThat(actionStatusRepository.findAll()).as("wrong size of action status").hasSize(9);
        assertThat(deploymentManagement.getAssignedDistributionSet("4712").get()).as("wrong assigned ds")
                .isEqualTo(dsInstalled);
        assertThat(targetManagement.getByControllerID("4712").get().getUpdateStatus()).as("wrong update status")
                .isEqualTo(TargetUpdateStatus.IN_SYNC);
    }

    @Test
    @Description("Cancels multiple active actions on a target. Expected behaviour is that with two active "
            + "actions after canceling the first active action the system switched to second one. After canceling this one "
            + "also the target goes back to IN_SYNC as no open action is left.")
    public void manualCancelWithMultipleAssignmentsCancelMiddleOneFirst() {
        final Action action = prepareFinishedUpdate("4712", "installed", true);
        final Target target = action.getTarget();
        final DistributionSet dsFirst = testdataFactory.createDistributionSet("", true);
        final DistributionSet dsSecond = testdataFactory.createDistributionSet("2", true);
        final DistributionSet dsInstalled = action.getDistributionSet();

        // check initial status
        assertThat(targetManagement.getByControllerID("4712").get().getUpdateStatus()).as("wrong update status")
                .isEqualTo(TargetUpdateStatus.IN_SYNC);

        // assign the two sets in a row
        JpaAction firstAction = assignSet(target, dsFirst);
        JpaAction secondAction = assignSet(target, dsSecond);

        assertThat(actionRepository.findAll()).as("wrong size of actions").hasSize(3);
        assertThat(actionStatusRepository.findAll()).as("wrong size of action status").hasSize(5);

        // we cancel first -> second is left
        deploymentManagement.cancelAction(firstAction.getId());
        // confirm cancellation
        firstAction = (JpaAction) deploymentManagement.findActionWithDetails(firstAction.getId()).get();
        controllerManagement.addCancelActionStatus(
                entityFactory.actionStatus().create(firstAction.getId()).status(Status.CANCELED));
        assertThat(actionStatusRepository.findAll()).as("wrong size of action status").hasSize(7);
        assertThat(deploymentManagement.getAssignedDistributionSet("4712").get()).as("wrong assigned ds")
                .isEqualTo(dsSecond);
        assertThat(targetManagement.getByControllerID("4712").get().getUpdateStatus()).as("wrong target update status")
                .isEqualTo(TargetUpdateStatus.PENDING);

        // we cancel second -> remain assigned until finished cancellation
        deploymentManagement.cancelAction(secondAction.getId());
        secondAction = (JpaAction) deploymentManagement.findActionWithDetails(secondAction.getId()).get();
        assertThat(actionStatusRepository.findAll()).as("wrong size of action status").hasSize(8);
        assertThat(deploymentManagement.getAssignedDistributionSet("4712").get()).as("wrong assigned ds")
                .isEqualTo(dsSecond);
        // confirm cancellation
        controllerManagement.addCancelActionStatus(
                entityFactory.actionStatus().create(secondAction.getId()).status(Status.CANCELED));
        // cancelled success -> back to dsInstalled
        assertThat(deploymentManagement.getAssignedDistributionSet("4712").get()).as("wrong installed ds")
                .isEqualTo(dsInstalled);
        assertThat(targetManagement.getByControllerID("4712").get().getUpdateStatus())
                .as("wrong target info update status").isEqualTo(TargetUpdateStatus.IN_SYNC);
    }

    @Test
    @Description("Force Quit an Assignment. Expected behaviour is that the action is canceled and is marked as deleted. The assigned Software module")
    public void forceQuitSetActionToInactive() throws InterruptedException {
        final Action action = prepareFinishedUpdate("4712", "installed", true);
        final Target target = action.getTarget();
        final DistributionSet dsInstalled = action.getDistributionSet();

        final DistributionSet ds = testdataFactory.createDistributionSet("newDS", true);

        // verify initial status
        assertThat(targetManagement.getByControllerID("4712").get().getUpdateStatus())
                .as("wrong target info update status").isEqualTo(TargetUpdateStatus.IN_SYNC);

        Action assigningAction = assignSet(target, ds);

        // verify assignment
        assertThat(actionRepository.findAll()).as("wrong size of action").hasSize(2);
        assertThat(actionStatusRepository.findAll()).as("wrong size of action status").hasSize(4);

        // force quit assignment
        deploymentManagement.cancelAction(assigningAction.getId());
        assigningAction = deploymentManagement.findActionWithDetails(assigningAction.getId()).get();

        deploymentManagement.forceQuitAction(assigningAction.getId());

        assigningAction = deploymentManagement.findActionWithDetails(assigningAction.getId()).get();

        // verify
        assertThat(assigningAction.getStatus()).as("wrong size of status").isEqualTo(Status.CANCELED);
        assertThat(deploymentManagement.getAssignedDistributionSet("4712").get()).as("wrong assigned ds")
                .isEqualTo(dsInstalled);
        assertThat(targetManagement.getByControllerID("4712").get().getUpdateStatus()).as("wrong target update status")
                .isEqualTo(TargetUpdateStatus.IN_SYNC);
    }

    @Test
    @Description("Force Quit an not canceled Assignment. Expected behaviour is that the action can not be force quit and there is thrown an exception.")
    public void forceQuitNotAllowedThrowsException() {
        final Action action = prepareFinishedUpdate("4712", "installed", true);
        final Target target = action.getTarget();

        final DistributionSet ds = testdataFactory.createDistributionSet("newDS", true);

        // verify initial status
        assertThat(targetManagement.getByControllerID("4712").get().getUpdateStatus()).as("wrong update status")
                .isEqualTo(TargetUpdateStatus.IN_SYNC);

        final Action assigningAction = assignSet(target, ds);

        // verify assignment
        assertThat(actionRepository.findAll()).as("wrong size of action").hasSize(2);
        assertThat(actionStatusRepository.findAll()).as("wrong size of action status").hasSize(4);

        // force quit assignment
        try {
            deploymentManagement.forceQuitAction(assigningAction.getId());
            fail("expected ForceQuitActionNotAllowedException");
        } catch (final ForceQuitActionNotAllowedException ex) {
        }
    }

    private JpaAction assignSet(final Target target, final DistributionSet ds) {
        assignDistributionSet(ds.getId(), target.getControllerId());
        assertThat(targetManagement.getByControllerID(target.getControllerId()).get().getUpdateStatus())
                .as("wrong update status").isEqualTo(TargetUpdateStatus.PENDING);
        assertThat(deploymentManagement.getAssignedDistributionSet(target.getControllerId()).get())
                .as("wrong assigned ds").isEqualTo(ds);
        final JpaAction action = actionRepository
                .findByTargetAndDistributionSet(PAGE, (JpaTarget) target, (JpaDistributionSet) ds).getContent().get(0);
        assertThat(action).as("action should not be null").isNotNull();
        return action;
    }

    @Test
    @Description("Simple offline deployment of a distribution set to a list of targets. Verifies that offline assigment "
            + "is correctly executed for targets that do not have a running update already. Those are ignored.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 20),
            @Expect(type = TargetUpdatedEvent.class, count = 20), @Expect(type = ActionCreatedEvent.class, count = 20),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1) })
    public void assignedDistributionSet() {

        final List<String> controllerIds = testdataFactory.createTargets(10).stream().map(Target::getControllerId)
                .collect(Collectors.toList());
        final List<Target> onlineAssignedTargets = testdataFactory.createTargets(10, "2");
        controllerIds.addAll(onlineAssignedTargets.stream().map(Target::getControllerId).collect(Collectors.toList()));

        final DistributionSet ds = testdataFactory.createDistributionSet();
        assignDistributionSet(testdataFactory.createDistributionSet("2"), onlineAssignedTargets);

        final long current = System.currentTimeMillis();

        final List<Entry<String, Long>> offlineAssignments = controllerIds.stream()
                .map(targetId -> new SimpleEntry<String, Long>(targetId, ds.getId())).collect(Collectors.toList());
        final List<DistributionSetAssignmentResult> assignmentResults = deploymentManagement
                .offlineAssignedDistributionSets(offlineAssignments);
        assertThat(assignmentResults).hasSize(1);
        final List<Target> targets = assignmentResults.get(0).getAssignedEntity().stream().map(Action::getTarget)
                .collect(Collectors.toList());

        assertThat(actionRepository.count()).isEqualTo(20);
        assertThat(actionRepository.findByDistributionSetId(PAGE, ds.getId())).as("Offline actions are not active")
                .allMatch(action -> !action.isActive());

        assertThat(targetManagement.findByInstalledDistributionSet(PAGE, ds.getId()).getContent())
                .usingElementComparator(controllerIdComparator()).containsAll(targets).hasSize(10)
                .containsAll(targetManagement.findByAssignedDistributionSet(PAGE, ds.getId()))
                .as("InstallationDate set").allMatch(target -> target.getInstallationDate() >= current)
                .as("TargetUpdateStatus IN_SYNC")
                .allMatch(target -> TargetUpdateStatus.IN_SYNC.equals(target.getUpdateStatus()))
                .as("InstallationDate equal to LastModifiedAt")
                .allMatch(target -> target.getLastModifiedAt() == target.getInstallationDate());
    }

    @Test
    @Description("Offline assign multiple DSs to multiple Targets in multiassignment mode.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 4), @Expect(type = ActionCreatedEvent.class, count = 4),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6) })
    public void multiOfflineAssignment() {
        final List<String> targetIds = testdataFactory.createTargets(2).stream().map(Target::getControllerId)
                .collect(Collectors.toList());
        final List<Long> dsIds = testdataFactory.createDistributionSets(2).stream().map(DistributionSet::getId)
                .collect(Collectors.toList());

        enableMultiAssignments();
        final List<Entry<String, Long>> offlineAssignments = new ArrayList<>();
        targetIds.forEach(targetId -> dsIds
                .forEach(dsId -> offlineAssignments.add(new SimpleEntry<String, Long>(targetId, dsId))));
        final List<DistributionSetAssignmentResult> assignmentResults = deploymentManagement
                .offlineAssignedDistributionSets(offlineAssignments);

        assertThat(getResultingActionCount(assignmentResults)).isEqualTo(4);
        targetIds.forEach(controllerId -> {
            final List<Long> assignedDsIds = actionRepository.findByTargetControllerId(PAGE, controllerId).stream()
                    .map(action -> action.getDistributionSet().getId()).collect(Collectors.toList());
            assertThat(assignedDsIds).containsExactlyInAnyOrderElementsOf(dsIds);
        });
    }

    @Test
    @Description("Verifies that if an account is set to action autoclose running actions in case of a new assigned set get closed and set to CANCELED.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 10),
            @Expect(type = TargetUpdatedEvent.class, count = 20), @Expect(type = ActionCreatedEvent.class, count = 20),
            @Expect(type = ActionUpdatedEvent.class, count = 10),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 2) })
    public void assignDistributionSetAndAutoCloseActiveActions() {
        tenantConfigurationManagement
                .addOrUpdateConfiguration(TenantConfigurationKey.REPOSITORY_ACTIONS_AUTOCLOSE_ENABLED, true);

        try {
            final List<Target> targets = testdataFactory.createTargets(10);

            // First assignment
            final DistributionSet ds1 = testdataFactory.createDistributionSet("1");
            assignDistributionSet(ds1, targets);

            assertDsExclusivelyAssignedToTargets(targets, ds1.getId(), STATE_ACTIVE, Status.RUNNING);

            // Second assignment
            final DistributionSet ds2 = testdataFactory.createDistributionSet("2");
            assignDistributionSet(ds2, targets);

            assertDsExclusivelyAssignedToTargets(targets, ds2.getId(), STATE_ACTIVE, Status.RUNNING);
            assertDsExclusivelyAssignedToTargets(targets, ds1.getId(), STATE_INACTIVE, Status.CANCELED);

            assertThat(targetManagement.findByAssignedDistributionSet(PAGE, ds2.getId()).getContent()).hasSize(10)
                    .as("InstallationDate not set").allMatch(target -> (target.getInstallationDate() == null));

        } finally {
            tenantConfigurationManagement
                    .addOrUpdateConfiguration(TenantConfigurationKey.REPOSITORY_ACTIONS_AUTOCLOSE_ENABLED, false);
        }
    }

    @Test
    @Description("If multi-assignment is enabled, verify that the previous Distribution Set assignment is not canceled when a new one is assigned.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 10),
            @Expect(type = TargetUpdatedEvent.class, count = 20), @Expect(type = ActionCreatedEvent.class, count = 20),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = MultiActionEvent.class, count = 2),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 0) })
    public void previousAssignmentsAreNotCanceledInMultiAssignMode() {
        enableMultiAssignments();
        final List<Target> targets = testdataFactory.createTargets(10);
        final List<String> targetIds = targets.stream().map(Target::getControllerId).collect(Collectors.toList());

        // First assignment
        final DistributionSet ds1 = testdataFactory.createDistributionSet("Multi-assign-1");
        assignDistributionSet(ds1.getId(), targetIds, 77);

        assertDsExclusivelyAssignedToTargets(targets, ds1.getId(), STATE_ACTIVE, Status.RUNNING);

        // Second assignment
        final DistributionSet ds2 = testdataFactory.createDistributionSet("Multi-assign-2");
        assignDistributionSet(ds2.getId(), targetIds, 45);

        assertDsExclusivelyAssignedToTargets(targets, ds2.getId(), STATE_ACTIVE, Status.RUNNING);
        assertDsExclusivelyAssignedToTargets(targets, ds1.getId(), STATE_ACTIVE, Status.RUNNING);
    }

    private void assertDsExclusivelyAssignedToTargets(final List<Target> targets, final long dsId, final boolean active,
            final Status status) {
        final List<Action> assignment = actionRepository.findByDistributionSetId(PAGE, dsId).getContent();

        assertThat(assignment).hasSize(10).allMatch(action -> action.isActive() == active)
                .as("Is assigned to DS " + dsId).allMatch(action -> action.getDistributionSet().getId().equals(dsId))
                .as("State is " + status).allMatch(action -> action.getStatus() == status);
        final long[] targetIds = targets.stream().mapToLong(Target::getId).toArray();
        assertThat(targetIds).as("All targets represented in assignment").containsExactlyInAnyOrder(
                assignment.stream().mapToLong(action -> action.getTarget().getId()).toArray());
    }

    @Test
    @Description("Assign multiple DSs to multiple Targets in one request in multiassignment mode.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 4), @Expect(type = ActionCreatedEvent.class, count = 4),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6),
            @Expect(type = MultiActionEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 0) })
    public void multiassignmentInOneRequest() {
        final List<Target> targets = testdataFactory.createTargets(2);
        final List<DistributionSet> distributionSets = testdataFactory.createDistributionSets(2);
        final List<DeploymentRequest> deploymentRequests = createAssignmentRequests(distributionSets, targets, 34);

        enableMultiAssignments();
        final List<DistributionSetAssignmentResult> results = deploymentManagement
                .assignDistributionSets(deploymentRequests);

        assertThat(getResultingActionCount(results)).isEqualTo(deploymentRequests.size());
        final List<Long> dsIds = distributionSets.stream().map(DistributionSet::getId).collect(Collectors.toList());
        targets.forEach(target -> {
            final List<Long> assignedDsIds = actionRepository.findByTargetControllerId(PAGE, target.getControllerId())
                    .stream().map(action -> action.getDistributionSet().getId()).collect(Collectors.toList());
            assertThat(assignedDsIds).containsExactlyInAnyOrderElementsOf(dsIds);
        });
    }

    protected List<DeploymentRequest> createAssignmentRequests(final Collection<DistributionSet> distributionSets,
            final Collection<Target> targets, final int weight) {
        final List<DeploymentRequest> deploymentRequests = new ArrayList<>();
        distributionSets.forEach(ds -> targets.forEach(target -> deploymentRequests.add(DeploymentManagement
                .deploymentRequest(target.getControllerId(), ds.getId()).setWeight(weight).build())));
        return deploymentRequests;
    }

    @Test
    @Description("A Request resulting in multiple assignments to a single target is only allowed when multiassignment is enabled.")
    public void multipleAssignmentsToTargetOnlyAllowedInMultiAssignMode() {
        final Target target = testdataFactory.createTarget();
        final List<DistributionSet> distributionSets = testdataFactory.createDistributionSets(2);

        final DeploymentRequest targetToDS0 = DeploymentManagement
                .deploymentRequest(target.getControllerId(), distributionSets.get(0).getId()).setWeight(78).build();

        final DeploymentRequest targetToDS1 = DeploymentManagement
                .deploymentRequest(target.getControllerId(), distributionSets.get(1).getId()).setWeight(565).build();

        Assertions.assertThatExceptionOfType(MultiAssignmentIsNotEnabledException.class)
                .isThrownBy(() -> deploymentManagement.assignDistributionSets(Arrays.asList(targetToDS0, targetToDS1)));

        enableMultiAssignments();
        assertThat(getResultingActionCount(
                deploymentManagement.assignDistributionSets(Arrays.asList(targetToDS0, targetToDS1)))).isEqualTo(2);
    }

    @Test
    @Description("Assigning distribution set to the list of targets with a non-existing one leads to successful assignment of valid targets, while not found targets are silently ignored.")
    public void assignDistributionSetToNotExistingTarget() {
        final String notExistingId = "notExistingTarget";

        final DistributionSet createdDs = testdataFactory.createDistributionSet();

        final String[] knownTargetIdsArray = { "1", "2" };
        final List<String> knownTargetIds = Lists.newArrayList(knownTargetIdsArray);
        testdataFactory.createTargets(knownTargetIdsArray);

        // add not existing target to targets
        knownTargetIds.add(notExistingId);

        final List<DistributionSetAssignmentResult> assignDistributionSetsResults = assignDistributionSetToTargets(
                createdDs, knownTargetIds);

        for (final DistributionSetAssignmentResult assignDistributionSetsResult : assignDistributionSetsResults) {
            assertThat(assignDistributionSetsResult.getAlreadyAssigned()).isEqualTo(0);
            assertThat(assignDistributionSetsResult.getAssigned()).isEqualTo(2);
            assertThat(assignDistributionSetsResult.getTotal()).isEqualTo(2);
        }
    }

    private List<DistributionSetAssignmentResult> assignDistributionSetToTargets(final DistributionSet distributionSet,
            final Iterable<String> targetIds) {
        final List<DeploymentRequest> deploymentRequests = new ArrayList<>();
        for (final String controllerId : targetIds) {
            deploymentRequests.add(new DeploymentRequest(controllerId, distributionSet.getId(), ActionType.FORCED, 0,
                    null, null, null, null));
        }
        return deploymentManagement.assignDistributionSets(deploymentRequests);
    }

    @Test
    @Description("Duplicate Assignments are removed from a request when multiassignment is disabled, otherwise not")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 3), @Expect(type = TargetUpdatedEvent.class, count = 2),
            @Expect(type = MultiActionEvent.class, count = 1) })
    public void duplicateAssignmentsInRequestAreOnlyRemovedIfMultiassignmentDisabled() {
        final String targetId = testdataFactory.createTarget().getControllerId();
        final Long dsId = testdataFactory.createDistributionSet().getId();
        final List<DeploymentRequest> twoEqualAssignments = Collections.nCopies(2,
                DeploymentManagement.deploymentRequest(targetId, dsId).build());

        assertThat(getResultingActionCount(deploymentManagement.assignDistributionSets(twoEqualAssignments)))
                .isEqualTo(1);

        enableMultiAssignments();
        final List<DeploymentRequest> twoEqualAssignmentsWithWeight = Collections.nCopies(2,
                DeploymentManagement.deploymentRequest(targetId, dsId).setWeight(555).build());

        assertThat(getResultingActionCount(deploymentManagement.assignDistributionSets(twoEqualAssignmentsWithWeight)))
                .isEqualTo(2);
    }

    private int getResultingActionCount(final List<DistributionSetAssignmentResult> results) {
        return results.stream().map(DistributionSetAssignmentResult::getTotal).reduce(0, Integer::sum);
    }

    @Test
    @Description("An assignment request is not accepted if it would lead to a target exceeding the max actions per target quota.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 0) })
    public void maxActionsPerTargetIsCheckedBeforeAssignmentExecution() {
        final int maxActions = quotaManagement.getMaxActionsPerTarget();
        final String controllerId = testdataFactory.createTarget().getControllerId();
        final Long dsId = testdataFactory.createDistributionSet().getId();

        final List<DeploymentRequest> deploymentRequests = Collections.nCopies(maxActions + 1,
                DeploymentManagement.deploymentRequest(controllerId, dsId).setWeight(24).build());

        enableMultiAssignments();
        Assertions.assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> deploymentManagement.assignDistributionSets(deploymentRequests));
        assertThat(actionRepository.countByTargetControllerId(controllerId)).isEqualTo(0);
    }

    @Test
    @Description("An assignment request without a weight will cause an error when multi assignment in enabled.")
    public void weightIsRequiredInMultiAssignmentMode() {
        final String targetId = testdataFactory.createTarget().getControllerId();
        final Long dsId = testdataFactory.createDistributionSet().getId();

        final DeploymentRequest assignWithoutWeight = DeploymentManagement.deploymentRequest(targetId, dsId).build();
        final DeploymentRequest assignWithWeight = DeploymentManagement.deploymentRequest(targetId, dsId).setWeight(567)
                .build();

        enableMultiAssignments();
        deploymentManagement.assignDistributionSets(Arrays.asList(assignWithWeight));
        Assertions.assertThatExceptionOfType(NoWeightProvidedInMultiAssignmentModeException.class).isThrownBy(
                () -> deploymentManagement.assignDistributionSets(Arrays.asList(assignWithoutWeight)));
    }

    @Test
    @Description("An assignment request containing a weight causes an error when multi assignment is disabled.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3) })
    public void weightNotAllowedWhenMultiAssignmentModeNotEnabled() {
        final String targetId = testdataFactory.createTarget().getControllerId();
        final Long dsId = testdataFactory.createDistributionSet().getId();

        final DeploymentRequest assignWithoutWeight = DeploymentManagement.deploymentRequest(targetId, dsId)
                .setWeight(456).build();

        Assertions.assertThatExceptionOfType(MultiAssignmentIsNotEnabledException.class).isThrownBy(
                () -> deploymentManagement.assignDistributionSets(Collections.singletonList(assignWithoutWeight)));
    }

    @Test
    @Description("Weights are validated and contained in the resulting Action.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = ActionCreatedEvent.class, count = 2), @Expect(type = TargetUpdatedEvent.class, count = 2),
            @Expect(type = MultiActionEvent.class, count = 2) })
    public void weightValidatedAndSaved() {
        final String targetId = testdataFactory.createTarget().getControllerId();
        final Long dsId = testdataFactory.createDistributionSet().getId();
        final DeploymentRequest valideRequest1 = DeploymentManagement.deploymentRequest(targetId, dsId)
                .setWeight(Action.WEIGHT_MAX).build();
        final DeploymentRequest valideRequest2 = DeploymentManagement.deploymentRequest(targetId, dsId)
                .setWeight(Action.WEIGHT_MIN).build();
        final DeploymentRequest weightTooLow = DeploymentManagement.deploymentRequest(targetId, dsId)
                .setWeight(Action.WEIGHT_MIN - 1).build();
        final DeploymentRequest weightTooHigh = DeploymentManagement.deploymentRequest(targetId, dsId)
                .setWeight(Action.WEIGHT_MAX + 1).build();
        enableMultiAssignments();
        Assertions.assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> deploymentManagement.assignDistributionSets(Collections.singletonList(weightTooLow)));
        Assertions.assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(
                () -> deploymentManagement.assignDistributionSets(Collections.singletonList(weightTooHigh)));
        final Long valideActionId1 = getFirstAssignedAction(
                deploymentManagement.assignDistributionSets(Collections.singletonList(valideRequest1)).get(0)).getId();
        final Long valideActionId2 = getFirstAssignedAction(
                deploymentManagement.assignDistributionSets(Collections.singletonList(valideRequest2)).get(0)).getId();
        assertThat(actionRepository.getById(valideActionId1).get().getWeight()).get().isEqualTo(Action.WEIGHT_MAX);
        assertThat(actionRepository.getById(valideActionId2).get().getWeight()).get().isEqualTo(Action.WEIGHT_MIN);
    }

    /**
     * test a simple deployment by calling the
     * {@link TargetRepository#assignDistributionSet(DistributionSet, Iterable)}
     * and checking the active action and the action history of the targets.
     *
     */
    @Test
    @Description("Simple deployment or distribution set to target assignment test.")
    @ExpectEvents({ @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 30), @Expect(type = ActionCreatedEvent.class, count = 20),
            @Expect(type = TargetUpdatedEvent.class, count = 20),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3) })
    public void assignDistributionSet2Targets() {

        final String myCtrlIDPref = "myCtrlID";
        final Iterable<Target> savedNakedTargets = testdataFactory.createTargets(10, myCtrlIDPref, "first description");

        final String myDeployedCtrlIDPref = "myDeployedCtrlID";
        List<Target> savedDeployedTargets = testdataFactory.createTargets(20, myDeployedCtrlIDPref,
                "first description");

        final DistributionSet ds = testdataFactory.createDistributionSet("");

        assignDistributionSet(ds, savedDeployedTargets);

        // verify that one Action for each assignDistributionSet
        assertThat(actionRepository.findAll(PAGE).getNumberOfElements()).as("wrong size of actions").isEqualTo(20);

        final Iterable<Target> allFoundTargets = targetManagement.findAll(PAGE).getContent();

        // get final updated version of targets
        savedDeployedTargets = targetManagement.getByControllerID(
                savedDeployedTargets.stream().map(target -> target.getControllerId()).collect(Collectors.toList()));

        assertThat(allFoundTargets).as("founded targets are wrong").containsAll(savedDeployedTargets)
                .containsAll(savedNakedTargets);
        assertThat(savedDeployedTargets).as("saved target are wrong")
                .doesNotContain(Iterables.toArray(savedNakedTargets, Target.class));
        assertThat(savedNakedTargets).as("saved target are wrong")
                .doesNotContain(Iterables.toArray(savedDeployedTargets, Target.class));

        for (final Target myt : savedNakedTargets) {
            final Target t = targetManagement.getByControllerID(myt.getControllerId()).get();
            assertThat(deploymentManagement.countActionsByTarget(t.getControllerId())).as("action should be empty")
                    .isEqualTo(0L);
        }

        for (final Target myt : savedDeployedTargets) {
            final Target t = targetManagement.getByControllerID(myt.getControllerId()).get();
            final List<Action> activeActionsByTarget = deploymentManagement
                    .findActiveActionsByTarget(PAGE, t.getControllerId()).getContent();
            assertThat(activeActionsByTarget).as("action should not be empty").isNotEmpty();
            assertThat(t.getUpdateStatus()).as("wrong target update status").isEqualTo(TargetUpdateStatus.PENDING);
            for (final Action ua : activeActionsByTarget) {
                assertThat(ua.getDistributionSet()).as("action has wrong ds").isEqualTo(ds);
            }
        }
    }

    @Test
    @Description("Test that it is not possible to assign a distribution set that is not complete.")
    @ExpectEvents({ @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 10), @Expect(type = ActionCreatedEvent.class, count = 10),
            @Expect(type = TargetUpdatedEvent.class, count = 10),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 2),
            @Expect(type = DistributionSetUpdatedEvent.class, count = 1)

    })
    public void failDistributionSetAssigmentThatIsNotComplete() throws InterruptedException {
        final List<Target> targets = testdataFactory.createTargets(10);

        final SoftwareModule ah = testdataFactory.createSoftwareModuleApp();
        final SoftwareModule os = testdataFactory.createSoftwareModuleOs();

        final DistributionSet incomplete = distributionSetManagement.create(entityFactory.distributionSet().create()
                .name("incomplete").version("v1").type(standardDsType).modules(Collections.singletonList(ah.getId())));

        try {
            assignDistributionSet(incomplete, targets);
            fail("expected IncompleteDistributionSetException");
        } catch (final IncompleteDistributionSetException ex) {
        }

        final DistributionSet nowComplete = distributionSetManagement.assignSoftwareModules(incomplete.getId(),
                Sets.newHashSet(os.getId()));

        assertThat(assignDistributionSet(nowComplete, targets).getAssigned()).as("assign ds doesn't work")
                .isEqualTo(10);
    }

    @Test
    @Description("Multiple deployments or distribution set to target assignment test. Expected behaviour is that a new deployment "
            + "overides unfinished old one which are canceled as part of the operation.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 5 + 4),
            @Expect(type = TargetUpdatedEvent.class, count = 3 * 4),
            @Expect(type = ActionCreatedEvent.class, count = 3 * 4),
            @Expect(type = ActionUpdatedEvent.class, count = 4 * 2),
            @Expect(type = CancelTargetAssignmentEvent.class, count = 4 * 2),
            @Expect(type = DistributionSetCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 9),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 2) })
    public void mutipleDeployments() throws InterruptedException {
        final String undeployedTargetPrefix = "undep-T";
        final int noOfUndeployedTargets = 5;

        final String deployedTargetPrefix = "dep-T";
        final int noOfDeployedTargets = 4;
        final int noOfDistributionSets = 3;

        final DeploymentResult deploymentResult = prepareComplexRepo(undeployedTargetPrefix, noOfUndeployedTargets,
                deployedTargetPrefix, noOfDeployedTargets, noOfDistributionSets, "myTestDS");

        final List<Long> deployedTargetIDs = deploymentResult.getDeployedTargetIDs();
        final List<Long> undeployedTargetIDs = deploymentResult.getUndeployedTargetIDs();
        final Collection<JpaTarget> savedNakedTargets = (Collection) deploymentResult.getUndeployedTargets();
        final Collection<JpaTarget> savedDeployedTargets = (Collection) deploymentResult.getDeployedTargets();

        // retrieving all Actions created by the assignDistributionSet call
        final Page<JpaAction> page = actionRepository.findAll(PAGE);
        // and verify the number
        assertThat(page.getTotalElements()).as("wrong size of actions")
                .isEqualTo(noOfDeployedTargets * noOfDistributionSets);

        // only records retrieved from the DB can be evaluated to be sure that
        // all fields are
        // populated;
        final List<JpaTarget> allFoundTargets = targetRepository.findAll();

        final List<JpaTarget> deployedTargetsFromDB = targetRepository.findAllById(deployedTargetIDs);
        final List<JpaTarget> undeployedTargetsFromDB = targetRepository.findAllById(undeployedTargetIDs);

        // test that number of Targets
        assertThat(allFoundTargets.spliterator().getExactSizeIfKnown()).as("number of target is wrong")
                .isEqualTo(deployedTargetsFromDB.spliterator().getExactSizeIfKnown()
                        + undeployedTargetsFromDB.spliterator().getExactSizeIfKnown());
        assertThat(deployedTargetsFromDB.spliterator().getExactSizeIfKnown()).as("number of target is wrong")
                .isEqualTo(noOfDeployedTargets);
        assertThat(undeployedTargetsFromDB.spliterator().getExactSizeIfKnown()).as("number of target is wrong")
                .isEqualTo(noOfUndeployedTargets);

        // test the content of different lists
        assertThat(allFoundTargets).as("content of founded target is wrong").containsAll(deployedTargetsFromDB)
                .containsAll(undeployedTargetsFromDB);

        assertThat(deployedTargetsFromDB).as("content of deployed target is wrong")
                .usingElementComparator(controllerIdComparator()).containsAll(savedDeployedTargets)
                .doesNotContain(Iterables.toArray(undeployedTargetsFromDB, JpaTarget.class));
        assertThat(undeployedTargetsFromDB).as("content of undeployed target is wrong").containsAll(savedNakedTargets)
                .doesNotContain(Iterables.toArray(deployedTargetsFromDB, JpaTarget.class));
    }

    @Test
    @Description("Multiple deployments or distribution set to target assignment test including finished response "
            + "from target/controller. Expected behaviour is that in case of OK finished update the target will go to "
            + "IN_SYNC status and installed DS is set to the assigned DS entry.")
    public void assignDistributionSetAndAddFinishedActionStatus() {
        final PageRequest pageRequest = PageRequest.of(0, 100, Direction.ASC, ActionStatusFields.ID.getFieldName());

        final DeploymentResult deployResWithDsA = prepareComplexRepo("undep-A-T", 2, "dep-A-T", 4, 1, "dsA");
        final DeploymentResult deployResWithDsB = prepareComplexRepo("undep-B-T", 3, "dep-B-T", 5, 1, "dsB");
        final DeploymentResult deployResWithDsC = prepareComplexRepo("undep-C-T", 4, "dep-C-T", 6, 1, "dsC");

        // keep a reference to the created DistributionSets
        final JpaDistributionSet dsA = (JpaDistributionSet) deployResWithDsA.getDistributionSets().get(0);
        final JpaDistributionSet dsB = (JpaDistributionSet) deployResWithDsB.getDistributionSets().get(0);
        final JpaDistributionSet dsC = (JpaDistributionSet) deployResWithDsC.getDistributionSets().get(0);

        // retrieving the UpdateActions created by the assignments
        actionRepository.findByDistributionSetId(pageRequest, dsA.getId()).getContent().get(0);
        actionRepository.findByDistributionSetId(pageRequest, dsB.getId()).getContent().get(0);
        actionRepository.findByDistributionSetId(pageRequest, dsC.getId()).getContent().get(0);

        // verifying the correctness of the assignments
        for (final Target t : deployResWithDsA.getDeployedTargets()) {
            assertThat(deploymentManagement.getAssignedDistributionSet(t.getControllerId()).get().getId())
                    .as("assignment is not correct").isEqualTo(dsA.getId());
            assertThat(deploymentManagement.getInstalledDistributionSet(t.getControllerId()))
                    .as("installed ds should be null").isNotPresent();
        }
        for (final Target t : deployResWithDsB.getDeployedTargets()) {
            assertThat(deploymentManagement.getAssignedDistributionSet(t.getControllerId()).get().getId())
                    .as("assigned ds is wrong").isEqualTo(dsB.getId());
            assertThat(deploymentManagement.getInstalledDistributionSet(t.getControllerId()))
                    .as("installed ds should be null").isNotPresent();
        }
        for (final Target t : deployResWithDsC.getDeployedTargets()) {
            assertThat(deploymentManagement.getAssignedDistributionSet(t.getControllerId()).get().getId())
                    .isEqualTo(dsC.getId());
            assertThat(deploymentManagement.getInstalledDistributionSet(t.getControllerId()))
                    .as("installed ds should not be null").isNotPresent();
            assertThat(targetManagement.getByControllerID(t.getControllerId()).get().getUpdateStatus())
                    .as("wrong target info update status").isEqualTo(TargetUpdateStatus.PENDING);
        }

        final List<Target> updatedTsDsA = testdataFactory
                .sendUpdateActionStatusToTargets(deployResWithDsA.getDeployedTargets(), Status.FINISHED,
                        Collections.singletonList("alles gut"))
                .stream().map(Action::getTarget).collect(Collectors.toList());

        // verify, that dsA is deployed correctly
        for (final Target t_ : updatedTsDsA) {
            final Target t = targetManagement.getByControllerID(t_.getControllerId()).get();
            assertThat(deploymentManagement.getAssignedDistributionSet(t.getControllerId()).get())
                    .as("assigned ds is wrong").isEqualTo(dsA);
            assertThat(deploymentManagement.getInstalledDistributionSet(t.getControllerId()).get())
                    .as("installed ds is wrong").isEqualTo(dsA);
            assertThat(targetManagement.getByControllerID(t.getControllerId()).get().getUpdateStatus())
                    .as("wrong target info update status").isEqualTo(TargetUpdateStatus.IN_SYNC);
            assertThat(deploymentManagement.findActiveActionsByTarget(PAGE, t.getControllerId()))
                    .as("no actions should be active").hasSize(0);
        }

        // deploy dsA to the target which already have dsB deployed -> must
        // remove updActB from
        // activeActions, add a corresponding cancelAction and another
        // UpdateAction for dsA
        final List<Target> deployed2DS = assignDistributionSet(dsA, deployResWithDsB.getDeployedTargets())
                .getAssignedEntity().stream().map(Action::getTarget).collect(Collectors.toList());
        actionRepository.findByDistributionSetId(pageRequest, dsA.getId()).getContent().get(1);

        // get final updated version of targets
        final List<Target> deployResWithDsBTargets = targetManagement.getByControllerID(deployResWithDsB
                .getDeployedTargets().stream().map(Target::getControllerId).collect(Collectors.toList()));

        assertThat(deployed2DS).as("deployed ds is wrong").usingElementComparator(controllerIdComparator())
                .containsAll(deployResWithDsBTargets);
        assertThat(deployed2DS).as("deployed ds is wrong").hasSameSizeAs(deployResWithDsBTargets);

        for (final Target t_ : deployed2DS) {
            final Target t = targetManagement.getByControllerID(t_.getControllerId()).get();
            assertThat(deploymentManagement.getAssignedDistributionSet(t.getControllerId()).get())
                    .as("assigned ds is wrong").isEqualTo(dsA);
            assertThat(deploymentManagement.getInstalledDistributionSet(t.getControllerId()))
                    .as("installed ds should be null").isNotPresent();
            assertThat(targetManagement.getByControllerID(t.getControllerId()).get().getUpdateStatus())
                    .as("wrong target info update status").isEqualTo(TargetUpdateStatus.PENDING);

        }
    }

    /**
     * test the deletion of {@link DistributionSet}s including exception in case
     * of {@link Target}s are assigned by
     * {@link Target#getAssignedDistributionSet()} or
     * {@link Target#getInstalledDistributionSet()}
     */
    @Test
    @Description("Deletes distribution set. Expected behaviour is that a soft delete is performed "
            + "if the DS is assigned to a target and a hard delete if the DS is not in use at all.")
    public void deleteDistributionSet() {

        final PageRequest pageRequest = PageRequest.of(0, 100, Direction.ASC, "id");

        final String undeployedTargetPrefix = "undep-T";
        final int noOfUndeployedTargets = 2;

        final String deployedTargetPrefix = "dep-T";
        final int noOfDeployedTargets = 4;

        final int noOfDistributionSets = 3;

        final DeploymentResult deploymentResult = prepareComplexRepo(undeployedTargetPrefix, noOfUndeployedTargets,
                deployedTargetPrefix, noOfDeployedTargets, noOfDistributionSets, "myTestDS");

        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        distributionSetManagement.delete(dsA.getId());

        assertThat(distributionSetManagement.get(dsA.getId())).isNotPresent();

        // // verify that the ds is not physically deleted
        for (final DistributionSet ds : deploymentResult.getDistributionSets()) {
            distributionSetManagement.delete(ds.getId());
            final DistributionSet foundDS = distributionSetManagement.get(ds.getId()).get();
            assertThat(foundDS).as("founded should not be null").isNotNull();
            assertThat(foundDS.isDeleted()).as("found ds should be deleted").isTrue();
        }

        // verify that deleted attribute is used correctly
        List<DistributionSet> allFoundDS = distributionSetManagement.findByCompleted(PAGE, true).getContent();
        assertThat(allFoundDS.size()).as("no ds should be founded").isEqualTo(0);

        assertThat(distributionSetRepository.findAll(SpecificationsBuilder.combineWithAnd(Arrays
                .asList(DistributionSetSpecification.isDeleted(true), DistributionSetSpecification.isCompleted(true))),
                PAGE).getContent()).as("wrong size of founded ds").hasSize(noOfDistributionSets);

        for (final DistributionSet ds : deploymentResult.getDistributionSets()) {
            testdataFactory.sendUpdateActionStatusToTargets(deploymentResult.getDeployedTargets(), Status.FINISHED,
                    Collections.singletonList("blabla alles gut"));
        }
        // try to delete again
        distributionSetManagement.delete(deploymentResult.getDistributionSetIDs());
        // verify that the result is the same, even though distributionSet dsA
        // has been installed
        // successfully and no activeAction is referring to created distribution
        // sets
        allFoundDS = distributionSetManagement.findByCompleted(pageRequest, true).getContent();
        assertThat(allFoundDS.size()).as("no ds should be founded").isEqualTo(0);
        assertThat(distributionSetRepository.findAll(SpecificationsBuilder.combineWithAnd(Arrays
                .asList(DistributionSetSpecification.isDeleted(true), DistributionSetSpecification.isCompleted(true))),
                PAGE).getContent()).as("wrong size of founded ds").hasSize(noOfDistributionSets);

    }

    @Test
    @Description("Deletes multiple targets and verfies that all related metadata is also deleted.")
    public void deletesTargetsAndVerifyCascadeDeletes() {

        final String undeployedTargetPrefix = "undep-T";
        final int noOfUndeployedTargets = 2;

        final String deployedTargetPrefix = "dep-T";
        final int noOfDeployedTargets = 4;

        final int noOfDistributionSets = 3;

        final DeploymentResult deploymentResult = prepareComplexRepo(undeployedTargetPrefix, noOfUndeployedTargets,
                deployedTargetPrefix, noOfDeployedTargets, noOfDistributionSets, "myTestDS");

        for (final DistributionSet ds : deploymentResult.getDistributionSets()) {
            testdataFactory.sendUpdateActionStatusToTargets(deploymentResult.getDeployedTargets(), Status.FINISHED,
                    Collections.singletonList("blabla alles gut"));
        }

        assertThat(targetManagement.count()).as("size of targets is wrong").isNotZero();
        assertThat(actionStatusRepository.count()).as("size of action status is wrong").isNotZero();

        targetManagement.delete(deploymentResult.getUndeployedTargetIDs());
        targetManagement.delete(deploymentResult.getDeployedTargetIDs());

        assertThat(targetManagement.count()).as("size of targets should be zero").isZero();
        assertThat(actionStatusRepository.count()).as("size of action status is wrong").isZero();
    }

    @Test
    @Description("Testing if changing target and the status without refreshing the entities from the DB (e.g. concurrent changes from UI and from controller) works")
    public void alternatingAssignmentAndAddUpdateActionStatus() {

        final DistributionSet dsA = testdataFactory.createDistributionSet("a");
        final DistributionSet dsB = testdataFactory.createDistributionSet("b");
        List<Target> targs = Collections.singletonList(testdataFactory.createTarget("target-id-A"));

        // doing the assignment
        targs = assignDistributionSet(dsA, targs).getAssignedEntity().stream().map(Action::getTarget)
                .collect(Collectors.toList());
        Target targ = targetManagement.getByControllerID(targs.iterator().next().getControllerId()).get();

        // checking the revisions of the created entities
        // verifying that the revision of the object and the revision within the
        // DB has not changed
        assertThat(dsA.getOptLockRevision()).as("lock revision is wrong")
                .isEqualTo(distributionSetManagement.getWithDetails(dsA.getId()).get().getOptLockRevision());

        // verifying that the assignment is correct
        assertThat(deploymentManagement.findActiveActionsByTarget(PAGE, targ.getControllerId()).getTotalElements())
                .as("Active target actions are wrong").isEqualTo(1);
        assertThat(deploymentManagement.countActionsByTarget(targ.getControllerId())).as("Target actions are wrong")
                .isEqualTo(1);
        assertThat(targ.getUpdateStatus()).as("UpdateStatus of target is wrong").isEqualTo(TargetUpdateStatus.PENDING);
        assertThat(deploymentManagement.getAssignedDistributionSet(targ.getControllerId()).get())
                .as("Assigned distribution set of target is wrong").isEqualTo(dsA);
        assertThat(deploymentManagement.findActiveActionsByTarget(PAGE, targ.getControllerId()).getContent().get(0)
                .getDistributionSet()).as("Distribution set of actionn is wrong").isEqualTo(dsA);
        assertThat(deploymentManagement.findActiveActionsByTarget(PAGE, targ.getControllerId()).getContent().get(0)
                .getDistributionSet()).as("Installed distribution set of action should be null").isNotNull();

        final Page<Action> updAct = actionRepository.findByDistributionSetId(PAGE, dsA.getId());
        controllerManagement.addUpdateActionStatus(
                entityFactory.actionStatus().create(updAct.getContent().get(0).getId()).status(Status.FINISHED));

        targ = targetManagement.getByControllerID(targ.getControllerId()).get();

        assertEquals("active target actions are wrong", 0,
                deploymentManagement.findActiveActionsByTarget(PAGE, targ.getControllerId()).getTotalElements());
        assertEquals("active actions are wrong", 1,
                deploymentManagement.findInActiveActionsByTarget(PAGE, targ.getControllerId()).getTotalElements());

        assertEquals("tagret update status is not correct", TargetUpdateStatus.IN_SYNC, targ.getUpdateStatus());
        assertEquals("wrong assigned ds", dsA,
                deploymentManagement.getAssignedDistributionSet(targ.getControllerId()).get());
        assertEquals("wrong installed ds", dsA,
                deploymentManagement.getInstalledDistributionSet(targ.getControllerId()).get());

        targs = assignDistributionSet(dsB.getId(), "target-id-A").getAssignedEntity().stream().map(Action::getTarget)
                .collect(Collectors.toList());

        targ = targs.iterator().next();

        assertEquals("active actions are wrong", 1,
                deploymentManagement.findActiveActionsByTarget(PAGE, targ.getControllerId()).getTotalElements());
        assertEquals("target status is wrong", TargetUpdateStatus.PENDING,
                targetManagement.getByControllerID(targ.getControllerId()).get().getUpdateStatus());
        assertEquals("wrong assigned ds", dsB,
                deploymentManagement.getAssignedDistributionSet(targ.getControllerId()).get());
        assertEquals("Installed ds is wrong", dsA.getId(),
                deploymentManagement.getInstalledDistributionSet(targ.getControllerId()).get().getId());
        assertEquals("Active ds is wrong", dsB, deploymentManagement
                .findActiveActionsByTarget(PAGE, targ.getControllerId()).getContent().get(0).getDistributionSet());

    }

    @Test
    @Description("The test verfies that the DS itself is not changed because of an target assignment"
            + " which is a relationship but not a changed on the entity itself..")
    public void checkThatDsRevisionsIsNotChangedWithTargetAssignment() {
        final DistributionSet dsA = testdataFactory.createDistributionSet("a");
        testdataFactory.createDistributionSet("b");
        final Target targ = testdataFactory.createTarget("target-id-A");

        assertThat(dsA.getOptLockRevision()).as("lock revision is wrong")
                .isEqualTo(distributionSetManagement.getWithDetails(dsA.getId()).get().getOptLockRevision());

        assignDistributionSet(dsA, Collections.singletonList(targ));

        assertThat(dsA.getOptLockRevision()).as("lock revision is wrong")
                .isEqualTo(distributionSetManagement.getWithDetails(dsA.getId()).get().getOptLockRevision());
    }

    @Test
    @Description("Tests the switch from a soft to hard update by API")
    public void forceSoftAction() {
        // prepare
        final Target target = testdataFactory.createTarget("knownControllerId");
        final DistributionSet ds = testdataFactory.createDistributionSet("a");
        // assign ds to create an action
        final DistributionSetAssignmentResult assignDistributionSet = assignDistributionSet(ds.getId(),
                target.getControllerId(), ActionType.SOFT);
        final Long actionId = getFirstAssignedActionId(assignDistributionSet);
        // verify preparation
        Action findAction = deploymentManagement.findAction(actionId).get();
        assertThat(findAction.getActionType()).as("action type is wrong").isEqualTo(ActionType.SOFT);

        // test
        deploymentManagement.forceTargetAction(actionId);

        // verify test
        findAction = deploymentManagement.findAction(actionId).get();
        assertThat(findAction.getActionType()).as("action type is wrong").isEqualTo(ActionType.FORCED);
    }

    @Test
    @Description("Tests the switch from a hard to hard update by API, e.g. which in fact should not change anything.")
    public void forceAlreadyForcedActionNothingChanges() {
        // prepare
        final Target target = testdataFactory.createTarget("knownControllerId");
        final DistributionSet ds = testdataFactory.createDistributionSet("a");
        // assign ds to create an action
        final DistributionSetAssignmentResult assignDistributionSet = assignDistributionSet(ds.getId(),
                target.getControllerId(), ActionType.FORCED);
        final Long actionId = getFirstAssignedActionId(assignDistributionSet);
        // verify perparation
        Action findAction = deploymentManagement.findAction(actionId).get();
        assertThat(findAction.getActionType()).as("action type is wrong").isEqualTo(ActionType.FORCED);

        // test
        final Action forceTargetAction = deploymentManagement.forceTargetAction(actionId);

        // verify test
        assertThat(forceTargetAction.getActionType()).as("action type is wrong").isEqualTo(ActionType.FORCED);
        findAction = deploymentManagement.findAction(actionId).get();
        assertThat(findAction.getActionType()).as("action type is wrong").isEqualTo(ActionType.FORCED);
    }

    @Test
    @Description("Tests the computation of already assigned entities returned as a result of an assignment")
    public void testAlreadyAssignedAndAssignedActionsInAssignmentResult() {
        // create target1, distributionSet, assign ds to target1 and finish
        // update (close all actions)
        final Action action = prepareFinishedUpdate("target1", "ds", false);
        final Target target2 = testdataFactory.createTarget("target2");
        final Target target3 = testdataFactory.createTarget("target3");

        // assign ds to target2, but don't finish update (actions should be
        // still open)
        assignDistributionSet(action.getDistributionSet().getId(), target2.getControllerId());

        final DistributionSetAssignmentResult assignmentResult = assignDistributionSet(
                action.getDistributionSet().getId(),
                Arrays.asList(action.getTarget().getControllerId(), target3.getControllerId()), ActionType.FORCED);

        assertThat(assignmentResult).isNotNull();
        assertThat(assignmentResult.getTotal()).as("Total count of assigned and already assigned targets").isEqualTo(2);
        assertThat(assignmentResult.getAssigned()).as("Total count of assigned targets").isEqualTo(1);
        assertThat(assignmentResult.getAlreadyAssigned()).as("Total count of already assigned targets").isEqualTo(1);
        assertThat(assignmentResult.getAssignedEntity()).isNotEmpty();
        assertThat(assignmentResult.getAssignedEntity()).allMatch(
                a -> a.getTarget().equals(target3) && a.getDistributionSet().equals(action.getDistributionSet()));
        assertThat(assignmentResult.getAssignedEntity())
                .noneMatch(a -> a.getTarget().getControllerId().equals("target1"));
    }

    @Test
    @Description("Verify that the DistributionSetAssignmentResult not contains already assigned targets.")
    public void verifyDistributionSetAssignmentResultNotContainsAlreadyAssignedTargets() {
        final DistributionSet dsToTargetAssigned = testdataFactory.createDistributionSet("ds-3");

        // create assigned DS
        final Target savedTarget = testdataFactory.createTarget();
        DistributionSetAssignmentResult assignmentResult = assignDistributionSet(dsToTargetAssigned.getId(),
                savedTarget.getControllerId());
        assertThat(assignmentResult.getAssignedEntity()).hasSize(1);

        assignmentResult = assignDistributionSet(dsToTargetAssigned.getId(), savedTarget.getControllerId());
        assertThat(assignmentResult.getAssignedEntity()).hasSize(0);

        assertThat(distributionSetRepository.findAll()).hasSize(1);
    }

    /**
     * Helper methods that creates 2 lists of targets and a list of distribution
     * sets.
     * <p>
     * <b>All created distribution sets are assigned to all targets of the
     * target list deployedTargets.</b>
     *
     * @param undeployedTargetPrefix
     *            prefix to be used as target controller prefix
     * @param noOfUndeployedTargets
     *            number of targets which remain undeployed
     * @param deployedTargetPrefix
     *            prefix to be used as target controller prefix
     * @param noOfDeployedTargets
     *            number of targets to which the created distribution sets
     *            assigned
     * @param noOfDistributionSets
     *            number of distribution sets
     * @param distributionSetPrefix
     *            prefix for the created distribution sets
     * @return the {@link DeploymentResult} containing all created targets, the
     *         distribution sets, the corresponding IDs for later evaluation in
     *         tests
     */
    private DeploymentResult prepareComplexRepo(final String undeployedTargetPrefix, final int noOfUndeployedTargets,
            final String deployedTargetPrefix, final int noOfDeployedTargets, final int noOfDistributionSets,
            final String distributionSetPrefix) {
        final Iterable<Target> nakedTargets = testdataFactory.createTargets(noOfUndeployedTargets,
                undeployedTargetPrefix, "first description");

        List<Target> deployedTargets = testdataFactory.createTargets(noOfDeployedTargets, deployedTargetPrefix,
                "first description");

        // creating 10 DistributionSets
        final Collection<DistributionSet> dsList = testdataFactory.createDistributionSets(distributionSetPrefix,
                noOfDistributionSets);
        String time = String.valueOf(System.currentTimeMillis());
        time = time.substring(time.length() - 5);

        // assigning all DistributionSet to the Target in the list
        // deployedTargets
        for (final DistributionSet ds : dsList) {
            deployedTargets = assignDistributionSet(ds, deployedTargets).getAssignedEntity().stream()
                    .map(Action::getTarget).collect(Collectors.toList());
        }

        return new DeploymentResult(deployedTargets, nakedTargets, dsList, deployedTargetPrefix, undeployedTargetPrefix,
                distributionSetPrefix);

    }

    private class DeploymentResult {
        final List<Long> deployedTargetIDs = new ArrayList<>();
        final List<Long> undeployedTargetIDs = new ArrayList<>();
        final List<Long> distributionSetIDs = new ArrayList<>();

        private final List<Target> undeployedTargets = new ArrayList<>();
        private final List<Target> deployedTargets = new ArrayList<>();
        private final List<DistributionSet> distributionSets = new ArrayList<>();

        public DeploymentResult(final Iterable<Target> deployedTs, final Iterable<Target> undeployedTs,
                final Iterable<DistributionSet> dss, final String deployedTargetPrefix,
                final String undeployedTargetPrefix, final String distributionSetPrefix) {

            Iterables.addAll(deployedTargets, deployedTs);
            Iterables.addAll(undeployedTargets, undeployedTs);
            Iterables.addAll(distributionSets, dss);

            deployedTargets.forEach(t -> deployedTargetIDs.add(t.getId()));

            undeployedTargets.forEach(t -> undeployedTargetIDs.add(t.getId()));

            distributionSets.forEach(ds -> distributionSetIDs.add(ds.getId()));

        }

        public List<Long> getDistributionSetIDs() {
            return distributionSetIDs;
        }

        public List<Long> getDeployedTargetIDs() {
            return deployedTargetIDs;
        }

        public List<Target> getUndeployedTargets() {
            return undeployedTargets;
        }

        public List<DistributionSet> getDistributionSets() {
            return distributionSets;
        }

        public List<Target> getDeployedTargets() {
            return deployedTargets;
        }

        public List<Long> getUndeployedTargetIDs() {
            return undeployedTargetIDs;
        }
    }
}
