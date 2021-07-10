/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.autoassign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.exception.InvalidAutoAssignDistributionSetException;
import org.eclipse.hawkbit.repository.jpa.AbstractJpaIntegrationTest;
import org.eclipse.hawkbit.repository.jpa.ActionRepository;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;

/**
 * Test class for {@link AutoAssignChecker}.
 *
 */
@Feature("Component Tests - Repository")
@Story("Auto assign checker")
public class AutoAssignCheckerTest extends AbstractJpaIntegrationTest {

    @Autowired
    private AutoAssignChecker autoAssignChecker;

    @Autowired
    private ActionRepository actionRepository;

    @Test
    @Description("Verifies that a running action is auto canceled by a AutoAssignment which assigns another distribution-set.")
    public void autoAssignDistributionSetAndAutoCloseOldActions() {

        tenantConfigurationManagement
                .addOrUpdateConfiguration(TenantConfigurationKey.REPOSITORY_ACTIONS_AUTOCLOSE_ENABLED, true);

        try {
            final String knownControllerId = "controller12345";
            final DistributionSet firstDistributionSet = testdataFactory.createDistributionSet();
            final DistributionSet secondDistributionSet = testdataFactory.createDistributionSet("second");
            testdataFactory.createTarget(knownControllerId);
            final DistributionSetAssignmentResult assignmentResult = assignDistributionSet(firstDistributionSet.getId(),
                    knownControllerId);
            final Long manuallyAssignedActionId = getFirstAssignedActionId(assignmentResult);

            // target filter query that matches all targets
            final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement
                    .create(entityFactory.targetFilterQuery().create().name("filterA").query("name==*"));
            targetFilterQueryManagement.updateAutoAssignDS(entityFactory.targetFilterQuery()
                    .updateAutoAssign(targetFilterQuery.getId()).ds(secondDistributionSet.getId()));
            // Run the check
            autoAssignChecker.check();

            // verify that manually created action is canceled and action
            // created from AutoAssign is running
            final List<Action> actionsByKnownTarget = deploymentManagement.findActionsByTarget(knownControllerId, PAGE)
                    .getContent();
            // should be 2 actions, one manually and one from the AutoAssign
            assertThat(actionsByKnownTarget).hasSize(2);
            // verify that manually assigned action is still running
            assertThat(deploymentManagement.findAction(manuallyAssignedActionId).get().getStatus())
                    .isEqualTo(Status.CANCELED);
            // verify that AutoAssign created action is running
            final Action rolloutCreatedAction = actionsByKnownTarget.stream()
                    .filter(action -> !action.getId().equals(manuallyAssignedActionId)).findAny().get();
            assertThat(rolloutCreatedAction.getStatus()).isEqualTo(Status.RUNNING);
            assertThat(rolloutCreatedAction.getActionType()).isEqualTo(ActionType.FORCED);
        } finally {
            tenantConfigurationManagement
                    .addOrUpdateConfiguration(TenantConfigurationKey.REPOSITORY_ACTIONS_AUTOCLOSE_ENABLED, false);
        }
    }

    @Test
    @Description("Test auto assignment of a DS to filtered targets")
    public void checkAutoAssign() {

        final DistributionSet setA = testdataFactory.createDistributionSet("dsA"); // will
                                                                                   // be
                                                                                   // auto
                                                                                   // assigned
        final DistributionSet setB = testdataFactory.createDistributionSet("dsB");

        // target filter query that matches all targets
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement.updateAutoAssignDS(
                entityFactory.targetFilterQuery()
                        .updateAutoAssign(targetFilterQueryManagement.create(
                                entityFactory.targetFilterQuery().create().name("filterA").query("name==*")).getId())
                        .ds(setA.getId()));

        final String targetDsAIdPref = "targ";
        final List<Target> targets = testdataFactory.createTargets(25, targetDsAIdPref,
                targetDsAIdPref.concat(" description"));
        final int targetsCount = targets.size();

        // assign set A to first 10 targets
        assignDistributionSet(setA, targets.subList(0, 10));
        verifyThatTargetsHaveDistributionSetAssignment(setA, targets.subList(0, 10), targetsCount);

        // assign set B to first 5 targets
        // they have now 2 DS in their action history and should not get updated
        // with dsA
        assignDistributionSet(setB, targets.subList(0, 5));
        verifyThatTargetsHaveDistributionSetAssignment(setB, targets.subList(0, 5), targetsCount);

        // assign set B to next 10 targets
        assignDistributionSet(setB, targets.subList(10, 20));
        verifyThatTargetsHaveDistributionSetAssignment(setB, targets.subList(10, 20), targetsCount);

        // Count the number of targets that will be assigned with setA
        assertThat(targetManagement.countByRsqlAndNonDS(setA.getId(), targetFilterQuery.getQuery())).isEqualTo(15);

        // Run the check
        autoAssignChecker.check();

        verifyThatTargetsHaveDistributionSetAssignment(setA, targets.subList(5, 25), targetsCount);

        // first 5 should keep their dsB, because they already had the dsA once
        verifyThatTargetsHaveDistributionSetAssignment(setB, targets.subList(0, 5), targetsCount);

        verifyThatCreatedActionsAreInitiatedByCurrentUser(targetFilterQuery, setA, targets);
    }

    @Test
    @Description("Test auto assignment of an incomplete DS to filtered targets, that causes failures")
    public void checkAutoAssignWithFailures() {

        // incomplete distribution set that will be assigned
        final DistributionSet setF = distributionSetManagement.create(entityFactory.distributionSet().create()
                .name("dsA").version("1").type(testdataFactory.findOrCreateDefaultTestDsType()));
        final DistributionSet setA = testdataFactory.createDistributionSet("dsA");
        final DistributionSet setB = testdataFactory.createDistributionSet("dsB");

        final String targetDsAIdPref = "targA";
        final String targetDsFIdPref = "targB";

        // target filter query that matches first bunch of targets, that should
        // fail
        assertThatExceptionOfType(InvalidAutoAssignDistributionSetException.class).isThrownBy(() -> {
            final Long filterId = targetFilterQueryManagement.create(
                    entityFactory.targetFilterQuery().create().name("filterA").query("id==" + targetDsFIdPref + "*"))
                    .getId();
            targetFilterQueryManagement
                    .updateAutoAssignDS(entityFactory.targetFilterQuery().updateAutoAssign(filterId).ds(setF.getId()));
        });
        // target filter query that matches failed bunch of targets
        targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create().name("filterB")
                .query("id==" + targetDsAIdPref + "*").autoAssignDistributionSet(setA.getId()));

        final List<Target> targetsF = testdataFactory.createTargets(10, targetDsFIdPref,
                targetDsFIdPref.concat(" description"));

        final List<Target> targetsA = testdataFactory.createTargets(10, targetDsAIdPref,
                targetDsAIdPref.concat(" description"));

        final int targetsCount = targetsA.size() + targetsF.size();

        // assign set B to first 5 targets of fail group
        assignDistributionSet(setB, targetsF.subList(0, 5));
        verifyThatTargetsHaveDistributionSetAssignment(setB, targetsF.subList(0, 5), targetsCount);

        // Run the check
        autoAssignChecker.check();

        // first 5 targets of the fail group should still have setB
        verifyThatTargetsHaveDistributionSetAssignment(setB, targetsF.subList(0, 5), targetsCount);

        // all targets of A group should have received setA
        verifyThatTargetsHaveDistributionSetAssignment(setA, targetsA, targetsCount);

    }

    /**
     * @param set
     *            the expected distribution set
     * @param targets
     *            the targets that should have it
     */
    @Step
    private void verifyThatTargetsHaveDistributionSetAssignment(final DistributionSet set, final List<Target> targets,
            final int count) {
        final List<Long> targetIds = targets.stream().map(Target::getId).collect(Collectors.toList());

        final Slice<Target> targetsAll = targetManagement.findAll(PAGE);
        assertThat(targetsAll).as("Count of targets").hasSize(count);

        for (final Target target : targetsAll) {
            if (targetIds.contains(target.getId())) {
                assertThat(deploymentManagement.getAssignedDistributionSet(target.getControllerId()).get())
                        .as("assigned DS").isEqualTo(set);
            }
        }

    }

    @Step
    private void verifyThatCreatedActionsAreInitiatedByCurrentUser(final TargetFilterQuery targetFilterQuery,
            final DistributionSet distributionSet, final List<Target> targets) {
        final Set<String> targetIds = targets.stream().map(Target::getControllerId).collect(Collectors.toSet());

        actionRepository.findByDistributionSetId(Pageable.unpaged(), distributionSet.getId())
                .stream().filter(a -> targetIds.contains(a.getTarget().getControllerId()))
                .forEach(a -> assertThat(a.getInitiatedBy()).as(
                        "Action should be initiated by the user who initiated the auto assignment")
                        .isEqualTo(targetFilterQuery.getAutoAssignInitiatedBy()));
    }

    @Test
    @Description("Test auto assignment of a distribution set with FORCED, SOFT and DOWNLOAD_ONLY action types")
    public void checkAutoAssignWithDifferentActionTypes() {
        final DistributionSet distributionSet = testdataFactory.createDistributionSet();
        final String targetDsAIdPref = "A";
        final String targetDsBIdPref = "B";
        final String targetDsCIdPref = "C";

        final List<Target> targetsA = createTargetsAndAutoAssignDistSet(targetDsAIdPref, 5, distributionSet,
                ActionType.FORCED);
        final List<Target> targetsB = createTargetsAndAutoAssignDistSet(targetDsBIdPref, 10, distributionSet,
                ActionType.SOFT);
        final List<Target> targetsC = createTargetsAndAutoAssignDistSet(targetDsCIdPref, 10, distributionSet,
                ActionType.DOWNLOAD_ONLY);

        final int targetsCount = targetsA.size() + targetsB.size() + targetsC.size();

        autoAssignChecker.check();

        verifyThatTargetsHaveDistributionSetAssignment(distributionSet, targetsA, targetsCount);
        verifyThatTargetsHaveDistributionSetAssignment(distributionSet, targetsB, targetsCount);
        verifyThatTargetsHaveDistributionSetAssignment(distributionSet, targetsC, targetsCount);

        verifyThatTargetsHaveAssignmentActionType(ActionType.FORCED, targetsA);
        verifyThatTargetsHaveAssignmentActionType(ActionType.SOFT, targetsB);
        verifyThatTargetsHaveAssignmentActionType(ActionType.DOWNLOAD_ONLY, targetsC);
    }

    @Step
    private List<Target> createTargetsAndAutoAssignDistSet(final String prefix, final int targetCount,
            final DistributionSet distributionSet, final ActionType actionType) {

        final List<Target> targets = testdataFactory.createTargets(targetCount, "target" + prefix,
                prefix.concat(" description"));
        targetFilterQueryManagement.create(
                entityFactory.targetFilterQuery().create().name("filter" + prefix).query("id==target" + prefix + "*")
                        .autoAssignDistributionSet(distributionSet).autoAssignActionType(actionType));

        return targets;
    }

    @Step
    private void verifyThatTargetsHaveAssignmentActionType(final ActionType actionType, final List<Target> targets) {
        final List<Action> actions = targets.stream().map(Target::getControllerId).flatMap(
                controllerId -> deploymentManagement.findActionsByTarget(controllerId, PAGE).getContent().stream())
                .collect(Collectors.toList());

        assertThat(actions).hasSize(targets.size());
        assertThat(actions).allMatch(action -> action.getActionType().equals(actionType));
    }

    @Test
    @Description("An auto assignment target filter with weight creats actions with weights")
    public void actionsWithWeightAreCreated() throws Exception {
        final int amountOfTargets = 5;
        final DistributionSet ds = testdataFactory.createDistributionSet();
        final int weight = 32;
        enableMultiAssignments();

        targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create().name("a").query("name==*")
                .autoAssignDistributionSet(ds).autoAssignWeight(weight));
        testdataFactory.createTargets(amountOfTargets);
        autoAssignChecker.check();

        final List<Action> actions = deploymentManagement.findActionsAll(PAGE).getContent();
        assertThat(actions).hasSize(amountOfTargets);
        assertThat(actions).allMatch(action -> action.getWeight().get() == weight);
    }

    @Test
    @Description("An auto assignment target filter without weight still works after multi assignment is enabled")
    public void filterWithoutWeightWorksInMultiAssignmentMode() throws Exception {
        final int amountOfTargets = 5;
        final DistributionSet ds = testdataFactory.createDistributionSet();
        targetFilterQueryManagement.create(
                entityFactory.targetFilterQuery().create().name("a").query("name==*").autoAssignDistributionSet(ds));
        enableMultiAssignments();

        testdataFactory.createTargets(amountOfTargets);
        autoAssignChecker.check();

        final List<Action> actions = deploymentManagement.findActionsAll(PAGE).getContent();
        assertThat(actions).hasSize(amountOfTargets);
        assertThat(actions).allMatch(action -> !action.getWeight().isPresent());
    }
}
