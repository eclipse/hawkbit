/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.mgmt.rest.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.hawkbit.exception.SpServerError;
import org.eclipse.hawkbit.mgmt.rest.api.MgmtRestConstants;
import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.exception.AssignmentQuotaExceededException;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupSuccessCondition;
import org.eclipse.hawkbit.repository.model.RolloutGroupConditionBuilder;
import org.eclipse.hawkbit.repository.model.RolloutGroupConditions;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.eclipse.hawkbit.rest.util.JsonBuilder;
import org.eclipse.hawkbit.rest.util.MockMvcResultPrinter;
import org.eclipse.hawkbit.rest.util.SuccessCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.MediaType;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;

/**
 * Tests for covering the {@link MgmtRolloutResource}.
 */
@Feature("Component Tests - Management API")
@Story("Rollout Resource")
public class MgmtRolloutResourceTest extends AbstractManagementApiIntegrationTest {

    private static final String HREF_ROLLOUT_PREFIX = "http://localhost/rest/v1/rollouts/";

    @Autowired
    private RolloutManagement rolloutManagement;

    @Autowired
    private RolloutGroupManagement rolloutGroupManagement;

    @Test
    @Description("Testing that creating rollout with wrong body returns bad request")
    public void createRolloutWithInvalidBodyReturnsBadRequest() throws Exception {
        mvc.perform(post("/rest/v1/rollouts").content("invalid body").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("errorCode", equalTo("hawkbit.server.error.rest.body.notReadable")));
    }

    @Test
    @Description("Testing that creating rollout with insufficient permission returns forbidden")
    @WithUser(allSpPermissions = true, removeFromAllPermission = "CREATE_ROLLOUT")
    public void createRolloutWithInsufficientPermissionReturnsForbidden() throws Exception {
        final DistributionSet dsA = testdataFactory.createDistributionSet("");
        mvc.perform(post("/rest/v1/rollouts")
                .content(JsonBuilder.rollout("name", "desc", 10, dsA.getId(), "name==test", null))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().is(403)).andReturn();
    }

    @Test
    @Description("Testing that creating rollout with not exisiting distribution set returns not found")
    public void createRolloutWithNotExistingDistributionSetReturnsNotFound() throws Exception {
        mvc.perform(post("/rest/v1/rollouts").content(JsonBuilder.rollout("name", "desc", 10, 1234, "name==test", null))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isNotFound()).andReturn();
    }

    @Test
    @Description("Testing that creating rollout with not valid formed target filter query returns bad request")
    public void createRolloutWithNotWellFormedFilterReturnsBadRequest() throws Exception {
        final DistributionSet dsA = testdataFactory.createDistributionSet("");
        mvc.perform(post("/rest/v1/rollouts")
                .content(JsonBuilder.rollout("name", "desc", 5, dsA.getId(), "name=test", null))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("errorCode", equalTo("hawkbit.server.error.rest.param.rsqlParamSyntax")))
                .andReturn();
    }

    @Description("Ensures that the repository refuses to create rollout without a defined target filter set.")
    public void missingTargetFilterQueryInRollout() throws Exception {

        final String targetFilterQuery = null;

        final DistributionSet dsA = testdataFactory.createDistributionSet("");
        mvc.perform(post("/rest/v1/rollouts")
                .content(JsonBuilder.rollout("rollout1", "desc", 10, dsA.getId(), targetFilterQuery, null))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("errorCode", equalTo("hawkbit.server.error.rest.param.rsqlParamSyntax")))
                .andReturn();

    }

    @Test
    @Description("Testing that rollout can be created")
    public void createRollout() throws Exception {
        testdataFactory.createTargets(20, "target", "rollout");

        final DistributionSet dsA = testdataFactory.createDistributionSet("");
        postRollout("rollout1", 5, dsA.getId(), "id==target*", 20, Action.ActionType.FORCED);
    }

    @Test
    @Description("Verifies that rollout cannot be created if too many rollout groups are specified.")
    public void createRolloutWithTooManyRolloutGroups() throws Exception {

        final int maxGroups = quotaManagement.getMaxRolloutGroupsPerRollout();
        testdataFactory.createTargets(20, "target", "rollout");

        mvc.perform(post("/rest/v1/rollouts")
                .content(JsonBuilder.rollout("rollout1", "rollout1Desc", maxGroups + 1,
                        testdataFactory.createDistributionSet("ds").getId(), "id==target*",
                        new RolloutGroupConditionBuilder().withDefaults().build()))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.exceptionClass", equalTo(AssignmentQuotaExceededException.class.getName())))
                .andExpect(jsonPath("$.errorCode", equalTo(SpServerError.SP_QUOTA_EXCEEDED.getKey())));

    }

    @Test
    @Description("Verifies that rollout cannot be created if the 'max targets per rollout group' quota would be violated for one of the groups.")
    public void createRolloutFailsIfRolloutGroupQuotaIsViolated() throws Exception {

        final int maxTargets = quotaManagement.getMaxTargetsPerRolloutGroup();
        testdataFactory.createTargets(maxTargets + 1, "target", "rollout");

        mvc.perform(post("/rest/v1/rollouts")
                .content(JsonBuilder.rollout("rollout1", "rollout1Desc", 1,
                        testdataFactory.createDistributionSet("ds").getId(), "id==target*",
                        new RolloutGroupConditionBuilder().withDefaults().build()))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.exceptionClass", equalTo(AssignmentQuotaExceededException.class.getName())))
                .andExpect(jsonPath("$.errorCode", equalTo(SpServerError.SP_QUOTA_EXCEEDED.getKey())));

    }

    @Test
    @Description("Testing that rollout can be created with groups")
    public void createRolloutWithGroupDefinitions() throws Exception {
        final DistributionSet dsA = testdataFactory.createDistributionSet("ro");

        final int amountTargets = 10;
        testdataFactory.createTargets(amountTargets, "ro-target", "rollout");

        final float percentTargetsInGroup1 = 20;
        final float percentTargetsInGroup2 = 100;

        final List<RolloutGroup> rolloutGroups = Arrays.asList(
                entityFactory.rolloutGroup().create().name("Group1").description("Group1desc")
                        .targetPercentage(percentTargetsInGroup1).build(),
                entityFactory.rolloutGroup().create().name("Group2").description("Group2desc")
                        .targetPercentage(percentTargetsInGroup2).build());

        final RolloutGroupConditions rolloutGroupConditions = new RolloutGroupConditionBuilder().withDefaults().build();

        mvc.perform(post("/rest/v1/rollouts")
                .content(JsonBuilder.rollout("rollout2", "desc", null, dsA.getId(), "id==ro-target*",
                        rolloutGroupConditions, rolloutGroups))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isCreated()).andReturn();

    }

    @Test
    @Description("Testing that no rollout with groups that have illegal percentages can be created")
    public void createRolloutWithTooLowPercentage() throws Exception {
        final DistributionSet dsA = testdataFactory.createDistributionSet("ro2");

        final int amountTargets = 10;
        testdataFactory.createTargets(amountTargets, "ro-target", "rollout");

        final List<RolloutGroup> rolloutGroups = Arrays.asList(
                entityFactory.rolloutGroup().create().name("Group1").description("Group1desc").targetPercentage(0F)
                        .build(),
                entityFactory.rolloutGroup().create().name("Group2").description("Group2desc").targetPercentage(100F)
                        .build());

        final RolloutGroupConditions rolloutGroupConditions = new RolloutGroupConditionBuilder().withDefaults().build();

        mvc.perform(post("/rest/v1/rollouts")
                .content(JsonBuilder.rollout("rollout4", "desc", null, dsA.getId(), "id==ro-target*",
                        rolloutGroupConditions, rolloutGroups))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("hawkbit.server.error.repo.constraintViolation")));

    }

    @Test
    @Description("Testing that no rollout with groups that have illegal percentages can be created")
    public void createRolloutWithTooHighPercentage() throws Exception {
        final DistributionSet dsA = testdataFactory.createDistributionSet("ro2");

        final int amountTargets = 10;
        testdataFactory.createTargets(amountTargets, "ro-target", "rollout");

        final List<RolloutGroup> rolloutGroups = Arrays.asList(
                entityFactory.rolloutGroup().create().name("Group1").description("Group1desc").targetPercentage(1F)
                        .build(),
                entityFactory.rolloutGroup().create().name("Group2").description("Group2desc").targetPercentage(101F)
                        .build());

        final RolloutGroupConditions rolloutGroupConditions = new RolloutGroupConditionBuilder().withDefaults().build();

        mvc.perform(post("/rest/v1/rollouts")
                .content(JsonBuilder.rollout("rollout4", "desc", null, dsA.getId(), "id==ro-target*",
                        rolloutGroupConditions, rolloutGroups))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("hawkbit.server.error.repo.constraintViolation")));

    }

    @Test
    @Description("Testing the empty list is returned if no rollout exists")
    public void noRolloutReturnsEmptyList() throws Exception {
        mvc.perform(get("/rest/v1/rollouts").accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(0))).andExpect(jsonPath("$.total", equalTo(0)));
    }

    @Test
    @Description("Retrieves single rollout from management API including extra data that is delivered only for single rollout access.")
    public void retrieveSingleRollout() throws Exception {
        testdataFactory.createTargets(20, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = rolloutManagement.create(
                entityFactory.rollout().create().name("rollout1").set(dsA.getId())
                        .targetFilterQuery("controllerId==rollout*"),
                4, new RolloutGroupConditionBuilder().withDefaults()
                        .successCondition(RolloutGroupSuccessCondition.THRESHOLD, "100").build());

        retrieveAndVerifyRolloutInCreating(dsA, rollout);
        retrieveAndVerifyRolloutInReady(rollout);
        retrieveAndVerifyRolloutInStarting(rollout);
        retrieveAndVerifyRolloutInRunning(rollout);
    }

    @Step
    private void retrieveAndVerifyRolloutInRunning(final Rollout rollout) throws Exception {
        rolloutManagement.handleRollouts();

        mvc.perform(get("/rest/v1/rollouts/" + rollout.getId()).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.id", equalTo(rollout.getId().intValue())))
                .andExpect(jsonPath("$.name", equalTo("rollout1"))).andExpect(jsonPath("$.status", equalTo("running")))
                .andExpect(jsonPath("$.totalTargetsPerStatus.running", equalTo(5)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.notstarted", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.scheduled", equalTo(15)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.cancelled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.finished", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.error", equalTo(0)))
                .andExpect(jsonPath("$.deleted", equalTo(false)));
    }

    @Step
    private void retrieveAndVerifyRolloutInStarting(final Rollout rollout) throws Exception {
        rolloutManagement.start(rollout.getId());

        mvc.perform(get("/rest/v1/rollouts/" + rollout.getId()).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.id", equalTo(rollout.getId().intValue())))
                .andExpect(jsonPath("$.name", equalTo("rollout1"))).andExpect(jsonPath("$.status", equalTo("starting")))
                .andExpect(jsonPath("$.totalTargetsPerStatus.running", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.notstarted", equalTo(20)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.scheduled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.cancelled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.finished", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.error", equalTo(0)))
                .andExpect(jsonPath("$.deleted", equalTo(false)));
    }

    @Step
    private void retrieveAndVerifyRolloutInReady(final Rollout rollout) throws Exception {
        rolloutManagement.handleRollouts();

        mvc.perform(get("/rest/v1/rollouts/" + rollout.getId()).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.id", equalTo(rollout.getId().intValue())))
                .andExpect(jsonPath("$.name", equalTo("rollout1"))).andExpect(jsonPath("$.status", equalTo("ready")))
                .andExpect(jsonPath("$.lastModifiedBy", equalTo("bumlux")))
                .andExpect(jsonPath("$.lastModifiedAt", not(equalTo(0))))
                .andExpect(jsonPath("$.totalTargetsPerStatus.running", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.notstarted", equalTo(20)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.scheduled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.cancelled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.finished", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.error", equalTo(0)))
                .andExpect(jsonPath("$.deleted", equalTo(false)));
    }

    @Step
    private void retrieveAndVerifyRolloutInCreating(final DistributionSet dsA, final Rollout rollout) throws Exception {
        mvc.perform(get("/rest/v1/rollouts/" + rollout.getId()).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.id", equalTo(rollout.getId().intValue())))
                .andExpect(jsonPath("$.name", equalTo("rollout1"))).andExpect(jsonPath("$.status", equalTo("creating")))
                .andExpect(jsonPath("$.targetFilterQuery", equalTo("controllerId==rollout*")))
                .andExpect(jsonPath("$.distributionSetId", equalTo(dsA.getId().intValue())))
                .andExpect(jsonPath("$.createdBy", equalTo("bumlux")))
                .andExpect(jsonPath("$.createdAt", not(equalTo(0))))
                .andExpect(jsonPath("$.lastModifiedBy", equalTo("bumlux")))
                .andExpect(jsonPath("$.lastModifiedAt", not(equalTo(0))))
                .andExpect(jsonPath("$.totalTargets", equalTo(20)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.running", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.notstarted", equalTo(20)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.scheduled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.cancelled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.finished", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.error", equalTo(0)))
                .andExpect(jsonPath("$._links.self.href", startsWith(HREF_ROLLOUT_PREFIX)))
                .andExpect(jsonPath("$._links.start.href", allOf(startsWith(HREF_ROLLOUT_PREFIX), endsWith("/start"))))
                .andExpect(jsonPath("$._links.pause.href", allOf(startsWith(HREF_ROLLOUT_PREFIX), endsWith("/pause"))))
                .andExpect(
                        jsonPath("$._links.resume.href", allOf(startsWith(HREF_ROLLOUT_PREFIX), endsWith("/resume"))))
                .andExpect(jsonPath("$._links.groups.href",
                        allOf(startsWith(HREF_ROLLOUT_PREFIX), containsString("/deploygroups"))))
                .andExpect(jsonPath("$.deleted", equalTo(false)));
    }

    @Test
    @Description("Testing that rollout paged list contains rollouts")
    public void rolloutPagedListContainsAllRollouts() throws Exception {
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        testdataFactory.createTargets(20, "target", "rollout");

        // setup - create 2 rollouts
        postRollout("rollout1", 5, dsA.getId(), "id==target*", 20, Action.ActionType.FORCED);
        postRollout("rollout2", 5, dsA.getId(), "id==target-0001*", 10, Action.ActionType.FORCED);

        // Run here, because Scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        mvc.perform(get("/rest/v1/rollouts").accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.total", equalTo(2)))
                .andExpect(jsonPath("content[0].name", equalTo("rollout1")))
                .andExpect(jsonPath("content[0].status", equalTo("ready")))
                .andExpect(jsonPath("content[0].targetFilterQuery", equalTo("id==target*")))
                .andExpect(jsonPath("content[0].distributionSetId", equalTo(dsA.getId().intValue())))
                .andExpect(jsonPath("content[0].createdBy", equalTo("bumlux")))
                .andExpect(jsonPath("content[0].createdAt", not(equalTo(0))))
                .andExpect(jsonPath("content[0].lastModifiedBy", equalTo("bumlux")))
                .andExpect(jsonPath("content[0].lastModifiedAt", not(equalTo(0))))
                .andExpect(jsonPath("content[0].totalTargets", equalTo(20)))
                .andExpect(jsonPath("content[0].totalTargetsPerStatus").doesNotExist())
                .andExpect(jsonPath("content[0]._links.self.href", startsWith(HREF_ROLLOUT_PREFIX)))
                .andExpect(jsonPath("content[1].name", equalTo("rollout2")))
                .andExpect(jsonPath("content[1].status", equalTo("ready")))
                .andExpect(jsonPath("content[1].targetFilterQuery", equalTo("id==target-0001*")))
                .andExpect(jsonPath("content[1].distributionSetId", equalTo(dsA.getId().intValue())))
                .andExpect(jsonPath("content[1].createdBy", equalTo("bumlux")))
                .andExpect(jsonPath("content[1].createdAt", not(equalTo(0))))
                .andExpect(jsonPath("content[1].lastModifiedBy", equalTo("bumlux")))
                .andExpect(jsonPath("content[1].lastModifiedAt", not(equalTo(0))))
                .andExpect(jsonPath("content[1].totalTargets", equalTo(10)))
                .andExpect(jsonPath("content[1].totalTargetsPerStatus").doesNotExist())
                .andExpect(jsonPath("content[1]._links.self.href", startsWith(HREF_ROLLOUT_PREFIX)));
    }

    @Test
    @Description("Testing that rollout paged list is limited by the query param limit")
    public void rolloutPagedListIsLimitedToQueryParam() throws Exception {
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        testdataFactory.createTargets(20, "target", "rollout");

        // setup - create 2 rollouts
        postRollout("rollout1", 5, dsA.getId(), "id==target*", 20, Action.ActionType.FORCED);
        postRollout("rollout2", 5, dsA.getId(), "id==target*", 20, Action.ActionType.FORCED);

        // Run here, because Scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        mvc.perform(get("/rest/v1/rollouts?limit=1").accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.total", equalTo(2)));
    }

    @Test
    @Description("Testing that rollout paged list is limited by the query param limit")
    public void retrieveRolloutGroupsForSpecificRollout() throws Exception {
        // setup
        final int amountTargets = 20;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 4, dsA.getId(), "controllerId==rollout*");

        // retrieve rollout groups from created rollout
        mvc.perform(
                get("/rest/v1/rollouts/{rolloutId}/deploygroups", rollout.getId()).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(4))).andExpect(jsonPath("$.total", equalTo(4)))
                .andExpect(jsonPath("$.content[0].status", equalTo("ready")))
                .andExpect(jsonPath("$.content[1].status", equalTo("ready")))
                .andExpect(jsonPath("$.content[2].status", equalTo("ready")))
                .andExpect(jsonPath("$.content[3].status", equalTo("ready")));
    }

    @Test
    @Description("Testing that starting the rollout switches the state to starting and then to running")
    public void startingRolloutSwitchesIntoRunningState() throws Exception {
        // setup
        final int amountTargets = 20;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 4, dsA.getId(), "controllerId==rollout*");

        // starting rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/start", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // check rollout is in starting state
        mvc.perform(get("/rest/v1/rollouts/{rolloutId}", rollout.getId()).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("id", equalTo(rollout.getId().intValue())))
                .andExpect(jsonPath("status", equalTo("starting")));

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // check rollout is in running state
        mvc.perform(get("/rest/v1/rollouts/{rolloutId}", rollout.getId()).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("id", equalTo(rollout.getId().intValue())))
                .andExpect(jsonPath("status", equalTo("running")));
    }

    @Test
    @Description("Testing that pausing the rollout switches the state to paused")
    public void pausingRolloutSwitchesIntoPausedState() throws Exception {
        // setup
        final int amountTargets = 20;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 4, dsA.getId(), "controllerId==rollout*");

        // starting rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/start", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // pausing rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/pause", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // check rollout is in running state
        mvc.perform(get("/rest/v1/rollouts/{rolloutId}", rollout.getId()).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("id", equalTo(rollout.getId().intValue())))
                .andExpect(jsonPath("status", equalTo("paused")));
    }

    @Test
    @Description("Testing that resuming the rollout switches the state to running")
    public void resumingRolloutSwitchesIntoRunningState() throws Exception {
        // setup
        final int amountTargets = 20;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 4, dsA.getId(), "controllerId==rollout*");

        // starting rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/start", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // pausing rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/pause", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // resume rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/resume", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // check rollout is in running state
        mvc.perform(get("/rest/v1/rollouts/{rolloutId}", rollout.getId()).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("id", equalTo(rollout.getId().intValue())))
                .andExpect(jsonPath("status", equalTo("running")));
    }

    @Test
    @Description("Testing that an already started rollout cannot be started again and returns bad request")
    public void startingAlreadyStartedRolloutReturnsBadRequest() throws Exception {
        // setup
        final int amountTargets = 20;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 4, dsA.getId(), "controllerId==rollout*");

        // starting rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/start", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // starting rollout - already started should lead into bad request
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/start", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("errorCode", equalTo("hawkbit.server.error.rollout.illegalstate")));
    }

    @Test
    @Description("Testing that resuming a rollout which is not started leads to bad request")
    public void resumingNotStartedRolloutReturnsBadRequest() throws Exception {
        // setup
        final int amountTargets = 20;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 4, dsA.getId(), "controllerId==rollout*");

        // resume not yet started rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/resume", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("errorCode", equalTo("hawkbit.server.error.rollout.illegalstate")));
    }

    @Test
    @Description("Testing that starting rollout the first rollout group is in running state")
    public void startingRolloutFirstRolloutGroupIsInRunningState() throws Exception {
        // setup
        final int amountTargets = 10;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 2, dsA.getId(), "controllerId==rollout*");

        // starting rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/start", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // retrieve rollout groups from created rollout - 2 groups exists
        // (amountTargets / groupSize = 2)
        mvc.perform(get("/rest/v1/rollouts/{rolloutId}/deploygroups?sort=ID:ASC", rollout.getId())
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.total", equalTo(2)))
                .andExpect(jsonPath("$.content[0].status", equalTo("running")))
                .andExpect(jsonPath("$.content[1].status", equalTo("scheduled")));
    }

    @Test
    @Description("Testing that a single rollout group can be retrieved")
    public void retrieveSingleRolloutGroup() throws Exception {
        // setup
        final int amountTargets = 20;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = rolloutManagement.create(
                entityFactory.rollout().create().name("rollout1").set(dsA.getId())
                        .targetFilterQuery("controllerId==rollout*"),
                4, new RolloutGroupConditionBuilder().withDefaults()
                        .successCondition(RolloutGroupSuccessCondition.THRESHOLD, "100").build());

        final RolloutGroup firstGroup = rolloutGroupManagement
                .findByRollout(PageRequest.of(0, 1, Direction.ASC, "id"), rollout.getId()).getContent().get(0);
        final RolloutGroup secondGroup = rolloutGroupManagement
                .findByRollout(PageRequest.of(1, 1, Direction.ASC, "id"), rollout.getId()).getContent().get(0);

        retrieveAndVerifyRolloutGroupInCreating(rollout, firstGroup);
        retrieveAndVerifyRolloutGroupInReady(rollout, firstGroup);
        retrieveAndVerifyRolloutGroupInRunningAndScheduled(rollout, firstGroup, secondGroup);
    }

    @Step
    private void retrieveAndVerifyRolloutGroupInRunningAndScheduled(final Rollout rollout,
            final RolloutGroup firstGroup, final RolloutGroup secondGroup) throws Exception {
        rolloutManagement.start(rollout.getId());
        rolloutManagement.handleRollouts();
        mvc.perform(get("/rest/v1/rollouts/{rolloutId}/deploygroups/{groupId}", rollout.getId(), firstGroup.getId())
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("status", equalTo("running")))
                .andExpect(jsonPath("$.totalTargetsPerStatus.running", equalTo(5)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.notstarted", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.scheduled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.cancelled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.finished", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.error", equalTo(0)));

        mvc.perform(get("/rest/v1/rollouts/{rolloutId}/deploygroups/{groupId}", rollout.getId(), secondGroup.getId())
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("status", equalTo("scheduled")))
                .andExpect(jsonPath("$.totalTargetsPerStatus.running", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.notstarted", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.scheduled", equalTo(5)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.cancelled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.finished", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.error", equalTo(0)));
    }

    @Step
    private void retrieveAndVerifyRolloutGroupInReady(final Rollout rollout, final RolloutGroup firstGroup)
            throws Exception {
        rolloutManagement.handleRollouts();
        mvc.perform(get("/rest/v1/rollouts/{rolloutId}/deploygroups/{groupId}", rollout.getId(), firstGroup.getId())
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("status", equalTo("ready")))
                .andExpect(jsonPath("$.lastModifiedBy", equalTo("bumlux")))
                .andExpect(jsonPath("$.lastModifiedAt", not(equalTo(0))))
                .andExpect(jsonPath("$.totalTargets", equalTo(5)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.running", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.notstarted", equalTo(5)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.scheduled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.cancelled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.finished", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.error", equalTo(0)));
    }

    @Step
    private void retrieveAndVerifyRolloutGroupInCreating(final Rollout rollout, final RolloutGroup firstGroup)
            throws Exception {
        mvc.perform(get("/rest/v1/rollouts/{rolloutId}/deploygroups/{groupId}", rollout.getId(), firstGroup.getId())
                .accept(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("id", equalTo(firstGroup.getId().intValue())))
                .andExpect(jsonPath("status", equalTo("creating"))).andExpect(jsonPath("name", endsWith("1")))
                .andExpect(jsonPath("description", endsWith("1")))
                .andExpect(jsonPath("$.targetFilterQuery", equalTo("")))
                .andExpect(jsonPath("$.targetPercentage", equalTo(25.0)))
                .andExpect(jsonPath("$.createdBy", equalTo("bumlux")))
                .andExpect(jsonPath("$.createdAt", not(equalTo(0))))
                .andExpect(jsonPath("$.lastModifiedBy", equalTo("bumlux")))
                .andExpect(jsonPath("$.lastModifiedAt", not(equalTo(0))))
                .andExpect(jsonPath("$.totalTargets", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.running", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.notstarted", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.scheduled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.cancelled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.finished", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.error", equalTo(0)))
                .andExpect(jsonPath("$._links.self.href", equalTo(
                        HREF_ROLLOUT_PREFIX + rollout.getId() + "/deploygroups/" + firstGroup.getId().intValue())));
    }

    @Test
    @Description("Testing that the targets of rollout group can be retrieved")
    public void retrieveTargetsFromRolloutGroup() throws Exception {
        // setup
        final int amountTargets = 10;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 2, dsA.getId(), "controllerId==rollout*");

        final RolloutGroup firstGroup = rolloutGroupManagement
                .findByRollout(PageRequest.of(0, 1, Direction.ASC, "id"), rollout.getId()).getContent().get(0);

        // retrieve targets from the first rollout group with known ID
        mvc.perform(
                get("/rest/v1/rollouts/{rolloutId}/deploygroups/{groupId}/targets", rollout.getId(), firstGroup.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(5))).andExpect(jsonPath("$.total", equalTo(5)));
    }

    @Test
    @Description("Testing that the targets of rollout group can be retrieved with rsql query param")
    public void retrieveTargetsFromRolloutGroupWithQuery() throws Exception {
        // setup
        final int amountTargets = 10;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 2, dsA.getId(), "controllerId==rollout*");

        final RolloutGroup firstGroup = rolloutGroupManagement
                .findByRollout(PageRequest.of(0, 1, Direction.ASC, "id"), rollout.getId()).getContent().get(0);

        final String targetInGroup = rolloutGroupManagement.findTargetsOfRolloutGroup(PAGE, firstGroup.getId())
                .getContent().get(0).getControllerId();

        // retrieve targets from the first rollout group with known ID
        mvc.perform(
                get("/rest/v1/rollouts/{rolloutId}/deploygroups/{groupId}/targets", rollout.getId(), firstGroup.getId())
                        .accept(MediaType.APPLICATION_JSON).param("q", "controllerId==" + targetInGroup))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.total", equalTo(1)));
    }

    @Test
    @Description("Testing that the targets of rollout group can be retrieved after the rollout has been started")
    public void retrieveTargetsFromRolloutGroupAfterRolloutIsStarted() throws Exception {
        // setup
        final int amountTargets = 10;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 2, dsA.getId(), "controllerId==rollout*");

        rolloutManagement.start(rollout.getId());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        final RolloutGroup firstGroup = rolloutGroupManagement
                .findByRollout(PageRequest.of(0, 1, Direction.ASC, "id"), rollout.getId()).getContent().get(0);

        // retrieve targets from the first rollout group with known ID
        mvc.perform(
                get("/rest/v1/rollouts/{rolloutId}/deploygroups/{groupId}/targets", rollout.getId(), firstGroup.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(5))).andExpect(jsonPath("$.total", equalTo(5)));
    }

    @Test
    @Description("Start the rollout in async mode")
    public void startingRolloutSwitchesIntoRunningStateAsync() throws Exception {

        final int amountTargets = 50;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 4, dsA.getId(), "controllerId==rollout*");

        // starting rollout
        mvc.perform(post("/rest/v1/rollouts/{rolloutId}/start", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // Run here, because scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        // check if running
        assertThat(doWithTimeout(() -> getRollout(rollout.getId()), this::success, 60_000, 100)).isNotNull();
    }

    @Test
    @Description("Deletion of a rollout")
    public void deleteRollout() throws Exception {
        final int amountTargets = 10;
        testdataFactory.createTargets(amountTargets, "rolloutDelete", "rolloutDelete");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rolloutDelete", 4, dsA.getId(), "controllerId==rolloutDelete*");

        // delete rollout
        mvc.perform(delete("/rest/v1/rollouts/{rolloutid}", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        assertThat(getRollout(rollout.getId()).getStatus()).isEqualTo(RolloutStatus.DELETING);
    }

    @Test
    @Description("Soft deletion of a rollout: soft deletion appears when already running rollout is being deleted")
    public void deleteRunningRollout() throws Exception {
        final Rollout rollout = testdataFactory.createSoftDeletedRollout("softDeletedRollout");

        mvc.perform(get("/rest/v1/rollouts/{rolloutid}", rollout.getId())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk()).andExpect(jsonPath("$.deleted", equalTo(true)));
        assertThat(getRollout(rollout.getId()).getStatus()).isEqualTo(RolloutStatus.DELETED);
    }

    @Test
    @Description("Testing that rollout paged list with rsql parameter")
    public void getRolloutWithRSQLParam() throws Exception {

        final int amountTargetsRollout1 = 25;
        final int amountTargetsRollout2 = 25;
        final int amountTargetsRollout3 = 25;
        final int amountTargetsOther = 25;
        testdataFactory.createTargets(amountTargetsRollout1, "rollout1", "rollout1");
        testdataFactory.createTargets(amountTargetsRollout2, "rollout2", "rollout2");
        testdataFactory.createTargets(amountTargetsRollout3, "rollout3", "rollout3");
        testdataFactory.createTargets(amountTargetsOther, "other1", "other1");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        createRollout("rollout1", 5, dsA.getId(), "controllerId==rollout1*");
        final Rollout rollout2 = createRollout("rollout2", 5, dsA.getId(), "controllerId==rollout2*");
        createRollout("rollout3", 5, dsA.getId(), "controllerId==rollout3*");
        createRollout("other1", 5, dsA.getId(), "controllerId==other1*");

        mvc.perform(get("/rest/v1/rollouts").param(MgmtRestConstants.REQUEST_PARAMETER_SEARCH, "name==*2")
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.total", equalTo(1)))
                .andExpect(jsonPath("$.content[0].name", equalTo(rollout2.getName())));

        mvc.perform(get("/rest/v1/rollouts").accept(MediaType.APPLICATION_JSON)
                .param(MgmtRestConstants.REQUEST_PARAMETER_SEARCH, "name==rollout*"))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(3))).andExpect(jsonPath("$.total", equalTo(3)));

        mvc.perform(get("/rest/v1/rollouts").accept(MediaType.APPLICATION_JSON)
                .param(MgmtRestConstants.REQUEST_PARAMETER_SEARCH, "name==*1")).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.total", equalTo(2)));

    }

    @Test
    @Description("Testing that rolloutgroup paged list with rsql parameter")
    public void retrieveRolloutGroupsForSpecificRolloutWithRSQLParam() throws Exception {
        // setup
        final int amountTargets = 20;
        testdataFactory.createTargets(amountTargets, "rollout", "rollout");
        final DistributionSet dsA = testdataFactory.createDistributionSet("");

        // create rollout including the created targets with prefix 'rollout'
        final Rollout rollout = createRollout("rollout1", 4, dsA.getId(), "controllerId==rollout*");

        // retrieve rollout groups from created rollout
        mvc.perform(get("/rest/v1/rollouts/{rolloutId}/deploygroups", rollout.getId())
                .accept(MediaType.APPLICATION_JSON).param(MgmtRestConstants.REQUEST_PARAMETER_SEARCH, "name==group-1"))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.total", equalTo(1)))
                .andExpect(jsonPath("$.content[0].name", equalTo("group-1")));

        mvc.perform(get("/rest/v1/rollouts/{rolloutId}/deploygroups", rollout.getId())
                .accept(MediaType.APPLICATION_JSON).param(MgmtRestConstants.REQUEST_PARAMETER_SEARCH, "name==group*"))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(4))).andExpect(jsonPath("$.total", equalTo(4)));

        mvc.perform(
                get("/rest/v1/rollouts/{rolloutId}/deploygroups", rollout.getId()).accept(MediaType.APPLICATION_JSON)
                        .param(MgmtRestConstants.REQUEST_PARAMETER_SEARCH, "name==group-1,name==group-2"))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.total", equalTo(2)));

    }

    @Test
    @Description("Verifies that a DOWNLOAD_ONLY rollout is possible")
    public void createDownloadOnlyRollout() throws Exception {
        testdataFactory.createTargets(20, "target", "rollout");

        final DistributionSet dsA = testdataFactory.createDistributionSet("");
        postRollout("rollout1", 5, dsA.getId(), "id==target*", 20, Action.ActionType.DOWNLOAD_ONLY);
    }

    @Test
    @Description("A rollout create request containing a weight is only accepted when weight is valide and multi assignment is on.")
    public void weightValidation() throws Exception {
        testdataFactory.createTargets(4, "rollout", "description");
        final Long dsId = testdataFactory.createDistributionSet().getId();
        final int weight = 66;

        final String invalideWeightRequest = JsonBuilder.rollout("withWeight", "d", 2, dsId, "id==rollout*",
                new RolloutGroupConditionBuilder().withDefaults().build(), null, null, Action.WEIGHT_MIN - 1);
        final String valideWeightRequest = JsonBuilder.rollout("withWeight", "d", 2, dsId, "id==rollout*",
                new RolloutGroupConditionBuilder().withDefaults().build(), null, null, weight);

        mvc.perform(post("/rest/v1/rollouts").content(valideWeightRequest).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("hawkbit.server.error.multiassignmentNotEnabled")));
        enableMultiAssignments();
        mvc.perform(post("/rest/v1/rollouts").content(invalideWeightRequest).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("hawkbit.server.error.repo.constraintViolation")));
        mvc.perform(post("/rest/v1/rollouts").content(valideWeightRequest).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isCreated());

        final List<Rollout> rollouts = rolloutManagement.findAll(PAGE, false).getContent();
        assertThat(rollouts).hasSize(1);
        assertThat(rollouts.get(0).getWeight()).get().isEqualTo(weight);
    }

    protected <T> T doWithTimeout(final Callable<T> callable, final SuccessCondition<T> successCondition,
            final long timeout, final long pollInterval) throws Exception // NOPMD
    {

        if (pollInterval < 0) {
            throw new IllegalArgumentException("pollInterval must non negative");
        }

        long duration = 0;
        Exception exception = null;
        T returnValue = null;
        while (untilTimeoutReached(timeout, duration)) {
            try {
                returnValue = callable.call();
                // clear exception
                exception = null;
            } catch (final Exception ex) {
                exception = ex;
            }
            Thread.sleep(pollInterval);
            duration += pollInterval > 0 ? pollInterval : 1;
            if (exception == null && successCondition.success(returnValue)) {
                return returnValue;
            } else {
                returnValue = null;
            }
        }
        if (exception != null) {
            throw exception;
        }
        return returnValue;
    }

    protected boolean untilTimeoutReached(final long timeout, final long duration) {
        return duration <= timeout || timeout < 0;
    }

    private void postRollout(final String name, final int groupSize, final Long distributionSetId,
            final String targetFilterQuery, final int targets, final Action.ActionType type) throws Exception {
        final String actionType = MgmtRestModelMapper.convertActionType(type).getName();
        final String rollout = JsonBuilder.rollout(name, "desc", groupSize, distributionSetId, targetFilterQuery,
                new RolloutGroupConditionBuilder().withDefaults().build(), null, actionType, null);

        mvc.perform(post("/rest/v1/rollouts").content(rollout).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo(name))).andExpect(jsonPath("$.status", equalTo("creating")))
                .andExpect(jsonPath("$.type", equalTo(actionType)))
                .andExpect(jsonPath("$.targetFilterQuery", equalTo(targetFilterQuery)))
                .andExpect(jsonPath("$.description", equalTo("desc")))
                .andExpect(jsonPath("$.distributionSetId", equalTo(distributionSetId.intValue())))
                .andExpect(jsonPath("$.createdBy", equalTo("bumlux")))
                .andExpect(jsonPath("$.createdAt", not(equalTo(0))))
                .andExpect(jsonPath("$.lastModifiedBy", equalTo("bumlux")))
                .andExpect(jsonPath("$.lastModifiedAt", not(equalTo(0))))
                .andExpect(jsonPath("$.totalTargets", equalTo(targets)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.running", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.notstarted", equalTo(targets)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.scheduled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.cancelled", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.finished", equalTo(0)))
                .andExpect(jsonPath("$.totalTargetsPerStatus.error", equalTo(0)))
                .andExpect(jsonPath("$._links.self.href", startsWith(HREF_ROLLOUT_PREFIX)))
                .andExpect(jsonPath("$._links.start.href", allOf(startsWith(HREF_ROLLOUT_PREFIX), endsWith("/start"))))
                .andExpect(jsonPath("$._links.pause.href", allOf(startsWith(HREF_ROLLOUT_PREFIX), endsWith("/pause"))))
                .andExpect(
                        jsonPath("$._links.resume.href", allOf(startsWith(HREF_ROLLOUT_PREFIX), endsWith("/resume"))))
                .andExpect(jsonPath("$._links.groups.href",
                        allOf(startsWith(HREF_ROLLOUT_PREFIX), containsString("/deploygroups"))));
    }

    private Rollout createRollout(final String name, final int amountGroups, final long distributionSetId,
            final String targetFilterQuery) {
        final Rollout rollout = rolloutManagement.create(
                entityFactory.rollout().create().name(name).set(distributionSetId).targetFilterQuery(targetFilterQuery),
                amountGroups, new RolloutGroupConditionBuilder().withDefaults()
                        .successCondition(RolloutGroupSuccessCondition.THRESHOLD, "100").build());

        // Run here, because Scheduler is disabled during tests
        rolloutManagement.handleRollouts();

        return rolloutManagement.get(rollout.getId()).get();
    }

    protected boolean success(final Rollout result) {
        return result != null && result.getStatus() == RolloutStatus.RUNNING;
    }

    public Rollout getRollout(final Long rolloutId) throws Exception {
        return rolloutManagement.get(rolloutId).get();
    }

}
