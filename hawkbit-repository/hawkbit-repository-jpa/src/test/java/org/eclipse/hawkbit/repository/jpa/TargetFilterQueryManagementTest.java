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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import javax.validation.ConstraintViolationException;

import org.assertj.core.api.Assertions;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetFilterQueryCreatedEvent;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.exception.EntityAlreadyExistsException;
import org.eclipse.hawkbit.repository.exception.InvalidAutoAssignActionTypeException;
import org.eclipse.hawkbit.repository.exception.InvalidAutoAssignDistributionSetException;
import org.eclipse.hawkbit.repository.exception.MultiAssignmentIsNotEnabledException;
import org.eclipse.hawkbit.repository.exception.NoWeightProvidedInMultiAssignmentModeException;
import org.eclipse.hawkbit.repository.exception.RSQLParameterUnsupportedFieldException;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;

/**
 * Test class for {@link TargetFilterQueryManagement}.
 * 
 */
@Feature("Component Tests - Repository")
@Story("Target Filter Query Management")
public class TargetFilterQueryManagementTest extends AbstractJpaIntegrationTest {

    @Test
    @Description("Verifies that management get access reacts as specfied on calls for non existing entities by means "
            + "of Optional not present.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void nonExistingEntityAccessReturnsNotPresent() {
        assertThat(targetFilterQueryManagement.get(NOT_EXIST_IDL)).isNotPresent();
        assertThat(targetFilterQueryManagement.getByName(NOT_EXIST_ID)).isNotPresent();
    }

    @Test
    @Description("Verifies that management queries react as specfied on calls for non existing entities "
            + " by means of throwing EntityNotFoundException.")
    @ExpectEvents({ @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = TargetFilterQueryCreatedEvent.class, count = 1) })
    public void entityQueriesReferringToNotExistingEntitiesThrowsException() {
        final DistributionSet set = testdataFactory.createDistributionSet();
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement.create(
                entityFactory.targetFilterQuery().create().name("test filter").query("name==PendingTargets001"));

        verifyThrownExceptionBy(() -> targetFilterQueryManagement.delete(NOT_EXIST_IDL), "TargetFilterQuery");

        verifyThrownExceptionBy(
                () -> targetFilterQueryManagement.findByAutoAssignDSAndRsql(PAGE, NOT_EXIST_IDL, "name==*"),
                "DistributionSet");

        verifyThrownExceptionBy(
                () -> targetFilterQueryManagement.update(entityFactory.targetFilterQuery().update(NOT_EXIST_IDL)),
                "TargetFilterQuery");

        verifyThrownExceptionBy(() -> targetFilterQueryManagement.updateAutoAssignDS(
                entityFactory.targetFilterQuery().updateAutoAssign(targetFilterQuery.getId()).ds(NOT_EXIST_IDL)),
                "DistributionSet");

        verifyThrownExceptionBy(
                () -> targetFilterQueryManagement.updateAutoAssignDS(
                        entityFactory.targetFilterQuery().updateAutoAssign(NOT_EXIST_IDL).ds(set.getId())),
                "TargetFilterQuery");

        verifyThrownExceptionBy(() -> targetFilterQueryManagement.updateAutoAssignDS(
                entityFactory.targetFilterQuery().updateAutoAssign(targetFilterQuery.getId()).ds(NOT_EXIST_IDL)),
                "DistributionSet");
    }

    @Test
    @Description("Test creation of target filter query.")
    public void createTargetFilterQuery() {
        final String filterName = "new target filter";
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name(filterName).query("name==PendingTargets001"));
        assertEquals("Retrieved newly created custom target filter", targetFilterQuery,
                targetFilterQueryManagement.getByName(filterName).get());
    }

    @Test
    @Description("Create a target filter query with an auto-assign distribution set and a query string that addresses too many targets.")
    public void createTargetFilterQueryThatExceedsQuota() {

        // create targets
        final int maxTargets = quotaManagement.getMaxTargetsPerAutoAssignment();
        testdataFactory.createTargets(maxTargets + 1, "target%s");
        final DistributionSet set = testdataFactory.createDistributionSet();

        // creation is supposed to work as there is no distribution set
        assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create()
                        .name("testfilter").autoAssignDistributionSet(set.getId()).query("name==target*")));
    }

    @Test
    @Description("Test searching a target filter query.")
    public void searchTargetFilterQuery() {
        final String filterName = "targetFilterQueryName";
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name(filterName).query("name==PendingTargets001"));

        targetFilterQueryManagement.create(
                entityFactory.targetFilterQuery().create().name("someOtherFilter").query("name==PendingTargets002"));

        final List<TargetFilterQuery> results = targetFilterQueryManagement
                .findByRsql(PageRequest.of(0, 10), "name==" + filterName).getContent();
        assertEquals("Search result should have 1 result", 1, results.size());
        assertEquals("Retrieved newly created custom target filter", targetFilterQuery, results.get(0));
    }

    @Test(expected = RSQLParameterUnsupportedFieldException.class)
    @Description("Test searching a target filter query with an invalid filter.")
    public void searchTargetFilterQueryInvalidField() {
        // Should throw an exception
        targetFilterQueryManagement.findByRsql(PageRequest.of(0, 10), "unknownField==testValue").getContent();

    }

    @Test
    @Description("Checks if the EntityAlreadyExistsException is thrown if a targetfilterquery with the same name are created more than once.")
    public void createDuplicateTargetFilterQuery() {
        final String filterName = "new target filter duplicate";
        targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name(filterName).query("name==PendingTargets001"));

        try {
            targetFilterQueryManagement.create(
                    entityFactory.targetFilterQuery().create().name(filterName).query("name==PendingTargets001"));
            fail("should not have worked as query already exists");
        } catch (final EntityAlreadyExistsException e) {

        }
    }

    @Test
    @Description("Test deletion of target filter query.")
    public void deleteTargetFilterQuery() {
        final String filterName = "delete_target_filter_query";
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name(filterName).query("name==PendingTargets001"));
        targetFilterQueryManagement.delete(targetFilterQuery.getId());
        assertFalse("Returns null as the target filter is deleted",
                targetFilterQueryManagement.get(targetFilterQuery.getId()).isPresent());

    }

    @Test
    @Description("Test updation of target filter query.")
    public void updateTargetFilterQuery() {
        final String filterName = "target_filter_01";
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name(filterName).query("name==PendingTargets001"));

        final String newQuery = "status==UNKNOWN";
        targetFilterQueryManagement
                .update(entityFactory.targetFilterQuery().update(targetFilterQuery.getId()).query(newQuery));
        assertEquals("Returns updated target filter query", newQuery,
                targetFilterQueryManagement.getByName(filterName).get().getQuery());

    }

    @Test
    @Description("Test assigning a distribution set for auto assignment with different action types")
    public void assignDistributionSet() {
        final String filterName = "target_filter_02";
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name(filterName).query("name==PendingTargets001"));
        final DistributionSet distributionSet = testdataFactory.createDistributionSet();

        verifyAutoAssignmentWithDefaultActionType(filterName, targetFilterQuery, distributionSet);

        verifyAutoAssignmentWithSoftActionType(filterName, targetFilterQuery, distributionSet);

        verifyAutoAssignmentWithDownloadOnlyActionType(filterName, targetFilterQuery, distributionSet);

        verifyAutoAssignmentWithInvalidActionType(targetFilterQuery, distributionSet);

        verifyAutoAssignmentWithIncompleteDs(targetFilterQuery);

        verifyAutoAssignmentWithSoftDeletedDs(targetFilterQuery);
    }

    @Step
    private void verifyAutoAssignmentWithDefaultActionType(final String filterName,
            final TargetFilterQuery targetFilterQuery, final DistributionSet distributionSet) {
        targetFilterQueryManagement.updateAutoAssignDS(entityFactory.targetFilterQuery()
                .updateAutoAssign(targetFilterQuery.getId()).ds(distributionSet.getId()));
        verifyAutoAssignDsAndActionType(filterName, distributionSet, ActionType.FORCED);
    }

    @Step
    private void verifyAutoAssignmentWithSoftActionType(final String filterName,
            final TargetFilterQuery targetFilterQuery, final DistributionSet distributionSet) {
        targetFilterQueryManagement.updateAutoAssignDS(entityFactory.targetFilterQuery()
                .updateAutoAssign(targetFilterQuery.getId()).ds(distributionSet.getId()).actionType(ActionType.SOFT));
        verifyAutoAssignDsAndActionType(filterName, distributionSet, ActionType.SOFT);
    }

    @Step
    private void verifyAutoAssignmentWithDownloadOnlyActionType(final String filterName,
            final TargetFilterQuery targetFilterQuery, final DistributionSet distributionSet) {
        targetFilterQueryManagement
                .updateAutoAssignDS(entityFactory.targetFilterQuery().updateAutoAssign(targetFilterQuery.getId())
                        .ds(distributionSet.getId()).actionType(ActionType.DOWNLOAD_ONLY));

        verifyAutoAssignDsAndActionType(filterName, distributionSet, ActionType.DOWNLOAD_ONLY);
    }

    @Step
    private void verifyAutoAssignmentWithInvalidActionType(final TargetFilterQuery targetFilterQuery,
            final DistributionSet distributionSet) {
        // assigning a distribution set with TIMEFORCED action is supposed to
        // fail as only FORCED and SOFT action types are allowed

        assertThatExceptionOfType(InvalidAutoAssignActionTypeException.class)
                .isThrownBy(() -> targetFilterQueryManagement.updateAutoAssignDS(
                        entityFactory.targetFilterQuery().updateAutoAssign(targetFilterQuery.getId())
                                .ds(distributionSet.getId()).actionType(ActionType.TIMEFORCED)));
    }

    @Step
    private void verifyAutoAssignmentWithIncompleteDs(final TargetFilterQuery targetFilterQuery) {
        final DistributionSet incompleteDistributionSet = distributionSetManagement
                .create(entityFactory.distributionSet().create().name("incomplete").version("1")
                        .type(testdataFactory.findOrCreateDefaultTestDsType()));

        assertThatExceptionOfType(InvalidAutoAssignDistributionSetException.class)
                .isThrownBy(() -> targetFilterQueryManagement.updateAutoAssignDS(entityFactory.targetFilterQuery()
                        .updateAutoAssign(targetFilterQuery.getId()).ds(incompleteDistributionSet.getId())));
    }

    @Step
    private void verifyAutoAssignmentWithSoftDeletedDs(final TargetFilterQuery targetFilterQuery) {
        final DistributionSet softDeletedDs = testdataFactory.createDistributionSet("softDeleted");
        assignDistributionSet(softDeletedDs, testdataFactory.createTarget("forSoftDeletedDs"));
        distributionSetManagement.delete(softDeletedDs.getId());

        assertThatExceptionOfType(InvalidAutoAssignDistributionSetException.class)
                .isThrownBy(() -> targetFilterQueryManagement.updateAutoAssignDS(entityFactory.targetFilterQuery()
                        .updateAutoAssign(targetFilterQuery.getId()).ds(softDeletedDs.getId())));
    }

    private void verifyAutoAssignDsAndActionType(final String filterName, final DistributionSet distributionSet,
            final ActionType actionType) {
        final TargetFilterQuery tfq = targetFilterQueryManagement.getByName(filterName).get();

        assertEquals("Returns correct distribution set", distributionSet, tfq.getAutoAssignDistributionSet());
        assertEquals("Return correct action type", actionType, tfq.getAutoAssignActionType());
    }

    @Test
    @Description("Assigns a distribution set to an existing filter query and verifies that the quota 'max targets per auto assignment' is enforced.")
    public void assignDistributionSetToTargetFilterQueryThatExceedsQuota() {

        // create targets
        final int maxTargets = quotaManagement.getMaxTargetsPerAutoAssignment();
        testdataFactory.createTargets(maxTargets + 1, "target%s");
        final DistributionSet distributionSet = testdataFactory.createDistributionSet();

        // creation is supposed to work as there is no distribution set
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name("testfilter").query("name==target*"));

        // assigning a distribution set is supposed to fail as the query
        // addresses too many targets

        assertThatExceptionOfType(AssignmentQuotaExceededException.class)
                .isThrownBy(() -> targetFilterQueryManagement.updateAutoAssignDS(entityFactory.targetFilterQuery()
                        .updateAutoAssign(targetFilterQuery.getId()).ds(distributionSet.getId())));
    }

    @Test
    @Description("Updates an existing filter query with a query string that addresses too many targets.")
    public void updateTargetFilterQueryWithQueryThatExceedsQuota() {

        // create targets
        final int maxTargets = quotaManagement.getMaxTargetsPerAutoAssignment();
        testdataFactory.createTargets(maxTargets + 1, "target%s");
        final DistributionSet set = testdataFactory.createDistributionSet();

        // creation is supposed to work as the query does not exceed the quota
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement.create(entityFactory.targetFilterQuery()
                .create().name("testfilter").autoAssignDistributionSet(set.getId()).query("name==foo"));

        // update with a query string that addresses too many targets
        assertThatExceptionOfType(AssignmentQuotaExceededException.class).isThrownBy(() -> targetFilterQueryManagement
                .update(entityFactory.targetFilterQuery().update(targetFilterQuery.getId()).query("name==target*")));
    }

    @Test
    @Description("Test removing distribution set while it has a relation to a target filter query")
    public void removeAssignDistributionSet() {
        final String filterName = "target_filter_03";
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name(filterName).query("name==PendingTargets001"));

        final DistributionSet distributionSet = testdataFactory.createDistributionSet();

        targetFilterQueryManagement.updateAutoAssignDS(entityFactory.targetFilterQuery()
                .updateAutoAssign(targetFilterQuery.getId()).ds(distributionSet.getId()));

        // Check if target filter query is there
        TargetFilterQuery tfq = targetFilterQueryManagement.getByName(filterName).get();
        assertEquals("Returns correct distribution set", distributionSet, tfq.getAutoAssignDistributionSet());
        assertEquals("Return correct action type", ActionType.FORCED, tfq.getAutoAssignActionType());

        distributionSetManagement.delete(distributionSet.getId());

        // Check if auto assign distribution set is null
        tfq = targetFilterQueryManagement.getByName(filterName).get();
        assertNotNull("Returns target filter query", tfq);
        assertNull("Returns distribution set as null", tfq.getAutoAssignDistributionSet());
        assertNull("Returns action type as null", tfq.getAutoAssignActionType());
    }

    @Test
    @Description("Test to implicitly remove the auto assign distribution set when the ds is soft deleted")
    public void implicitlyRemoveAssignDistributionSet() {
        final String filterName = "target_filter_03";
        final DistributionSet distributionSet = testdataFactory.createDistributionSet("dist_set");
        final Target target = testdataFactory.createTarget();

        // Assign the distribution set to an target, to force a soft delete in a
        // later step
        assignDistributionSet(distributionSet.getId(), target.getControllerId());

        final Long filterId = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name(filterName).query("name==PendingTargets001"))
                .getId();
        targetFilterQueryManagement.updateAutoAssignDS(
                entityFactory.targetFilterQuery().updateAutoAssign(filterId).ds(distributionSet.getId()));

        // Check if target filter query is there with the distribution set
        TargetFilterQuery tfq = targetFilterQueryManagement.getByName(filterName).get();
        assertEquals("Returns correct distribution set", distributionSet, tfq.getAutoAssignDistributionSet());
        assertEquals("Return correct action type", ActionType.FORCED, tfq.getAutoAssignActionType());

        distributionSetManagement.delete(distributionSet.getId());

        // Check if distribution set is still in the database with deleted flag
        assertTrue("Distribution set should be deleted",
                distributionSetManagement.get(distributionSet.getId()).get().isDeleted());

        // Check if auto assign distribution set is null
        tfq = targetFilterQueryManagement.getByName(filterName).get();
        assertNotNull("Returns target filter query", tfq);
        assertNull("Returns distribution set as null", tfq.getAutoAssignDistributionSet());
        assertNull("Returns action type as null", tfq.getAutoAssignActionType());
    }

    @Test
    @Description("Test finding and auto assign distribution set")
    public void findFiltersWithDistributionSet() {
        final String filterName = "d";
        assertEquals(0L, targetFilterQueryManagement.count());
        targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create().name("a").query("name==*"));
        targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create().name("b").query("name==*"));
        final DistributionSet distributionSet = testdataFactory.createDistributionSet();
        final DistributionSet distributionSet2 = testdataFactory.createDistributionSet("2");

        final TargetFilterQuery tfq = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name("c").query("name==x")
                        .autoAssignDistributionSet(distributionSet).autoAssignActionType(ActionType.SOFT));
        final TargetFilterQuery tfq2 = targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create()
                .name(filterName).query("name==z*").autoAssignDistributionSet(distributionSet2));
        assertEquals(4L, targetFilterQueryManagement.count());

        // check if find works
        verifyFindByDistributionSetAndRsql(distributionSet, null, tfq);

        targetFilterQueryManagement.updateAutoAssignDS(
                entityFactory.targetFilterQuery().updateAutoAssign(tfq2.getId()).ds(distributionSet.getId()));

        // check if find works for two
        verifyFindByDistributionSetAndRsql(distributionSet, null, tfq, tfq2);

        // check if find works with name filter
        verifyFindByDistributionSetAndRsql(distributionSet, "name==" + filterName, tfq2);

        verifyFindForAllWithAutoAssignDs(tfq, tfq2);
    }

    @Step
    private void verifyFindByDistributionSetAndRsql(final DistributionSet distributionSet, final String rsql,
            final TargetFilterQuery... expectedFilterQueries) {
        final Page<TargetFilterQuery> tfqList = targetFilterQueryManagement
                .findByAutoAssignDSAndRsql(PageRequest.of(0, 500), distributionSet.getId(), rsql);

        verifyExpectedFilterQueriesInList(tfqList, expectedFilterQueries);
    }

    private void verifyExpectedFilterQueriesInList(final Page<TargetFilterQuery> tfqList,
            final TargetFilterQuery... expectedFilterQueries) {
        assertThat(expectedFilterQueries.length).as("Target filter query count")
                .isEqualTo((int) tfqList.getTotalElements());

        assertThat(tfqList.map(TargetFilterQuery::getId)).containsExactly(
                Arrays.stream(expectedFilterQueries).map(TargetFilterQuery::getId).toArray(Long[]::new));
    }

    @Step
    private void verifyFindForAllWithAutoAssignDs(final TargetFilterQuery... expectedFilterQueries) {
        final Page<TargetFilterQuery> tfqList = targetFilterQueryManagement
                .findWithAutoAssignDS(PageRequest.of(0, 500));

        verifyExpectedFilterQueriesInList(tfqList, expectedFilterQueries);
    }

    @Test
    @Description("Creating or updating a target filter query with autoassignment and no-value weight when multi assignment in enabled.")
    public void weightIsRequiredInMultiAssignmentMode() {
        enableMultiAssignments();
        final DistributionSet ds = testdataFactory.createDistributionSet();
        final Long filterId = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name("a").query("name==*")).getId();
        final int weight = 349;

        targetFilterQueryManagement.create(
                entityFactory.targetFilterQuery().create().name("b").query("name==*").autoAssignDistributionSet(ds).autoAssignWeight(weight));
        targetFilterQueryManagement
                .updateAutoAssignDS(entityFactory.targetFilterQuery().updateAutoAssign(filterId).ds(ds.getId()).weight(weight));

        Assertions.assertThatExceptionOfType(NoWeightProvidedInMultiAssignmentModeException.class)
                .isThrownBy(() -> targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create()
                        .name("c").query("name==*").autoAssignDistributionSet(ds)));
        Assertions.assertThatExceptionOfType(NoWeightProvidedInMultiAssignmentModeException.class)
                .isThrownBy(() -> targetFilterQueryManagement.updateAutoAssignDS(
                        entityFactory.targetFilterQuery().updateAutoAssign(filterId).ds(ds.getId())));
    }

    @Test
    @Description("Creating or updating a target filter query with autoassignment with a weight causes an error when multi assignment in disabled.")
    public void weightNotAllowedWhenMultiAssignmentModeNotEnabled() {
        final DistributionSet ds = testdataFactory.createDistributionSet();
        final Long filterId = targetFilterQueryManagement
                .create(entityFactory.targetFilterQuery().create().name("a").query("name==*")).getId();

        Assertions.assertThatExceptionOfType(MultiAssignmentIsNotEnabledException.class)
                .isThrownBy(() -> targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create()
                        .name("b").query("name==*").autoAssignDistributionSet(ds).autoAssignWeight(342)));
        Assertions.assertThatExceptionOfType(MultiAssignmentIsNotEnabledException.class)
                .isThrownBy(() -> targetFilterQueryManagement.updateAutoAssignDS(
                        entityFactory.targetFilterQuery().updateAutoAssign(filterId).ds(ds.getId()).weight(343)));
    }

    @Test
    @Description("Auto assignment can be removed from filter when multi assignment in enabled.")
    public void removeDsFromFilterWhenMultiAssignmentModeNotEnabled() {
        enableMultiAssignments();
        final DistributionSet ds = testdataFactory.createDistributionSet();
        final Long filterId = targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create().name("a")
                .query("name==*").autoAssignDistributionSet(ds).autoAssignWeight(23)).getId();
        targetFilterQueryManagement
                .updateAutoAssignDS(entityFactory.targetFilterQuery().updateAutoAssign(filterId).ds(null).weight(null));
    }

    @Test
    @Description("Weight is validated and saved to the Filter.")
    public void weightValidatedAndSaved() {
        enableMultiAssignments();
        final DistributionSet ds = testdataFactory.createDistributionSet();

        Assertions.assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(
                () -> targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create().name("a")
                        .query("name==*").autoAssignDistributionSet(ds).autoAssignWeight(Action.WEIGHT_MAX + 1)));

        final Long filterId = targetFilterQueryManagement.create(entityFactory.targetFilterQuery().create().name("a")
                .query("name==*").autoAssignDistributionSet(ds).autoAssignWeight(Action.WEIGHT_MAX)).getId();
        assertThat(targetFilterQueryManagement.get(filterId).get().getAutoAssignWeight().get())
                .isEqualTo(Action.WEIGHT_MAX);

        Assertions.assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetFilterQueryManagement.updateAutoAssignDS(entityFactory.targetFilterQuery()
                        .updateAutoAssign(filterId).ds(ds.getId()).weight(Action.WEIGHT_MAX + 1)));
        Assertions.assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> targetFilterQueryManagement.updateAutoAssignDS(entityFactory.targetFilterQuery()
                        .updateAutoAssign(filterId).ds(ds.getId()).weight(Action.WEIGHT_MIN - 1)));
        targetFilterQueryManagement.updateAutoAssignDS(
                entityFactory.targetFilterQuery().updateAutoAssign(filterId).ds(ds.getId()).weight(Action.WEIGHT_MAX));
        targetFilterQueryManagement.updateAutoAssignDS(
                entityFactory.targetFilterQuery().updateAutoAssign(filterId).ds(ds.getId()).weight(Action.WEIGHT_MIN));
        assertThat(targetFilterQueryManagement.get(filterId).get().getAutoAssignWeight().get())
                .isEqualTo(Action.WEIGHT_MIN);
    }
}
