/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ddi.rest.resource;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.hawkbit.ddi.rest.api.DdiRestConstants;
import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.repository.event.remote.TargetAssignDistributionSetEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetAttributesRequestedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetPollEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetTypeCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleTypeCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.eclipse.hawkbit.repository.test.util.WithSpringAuthorityRule;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.eclipse.hawkbit.rest.util.JsonBuilder;
import org.eclipse.hawkbit.rest.util.MockMvcResultPrinter;
import org.eclipse.hawkbit.security.HawkbitSecurityProperties;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.util.IpUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions.CONTROLLER_ROLE;
import static org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions.CONTROLLER_ROLE_ANONYMOUS;
import static org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions.HAS_AUTH_TENANT_CONFIGURATION;
import static org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions.SYSTEM_ROLE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test the root controller resources.
 */
@Feature("Component Tests - Direct Device Integration API")
@Story("Root Poll Resource")
public class DdiRootControllerTest extends AbstractDDiApiIntegrationTest {

    private static final String TARGET_COMPLETED_INSTALLATION_MSG = "Target completed installation.";
    private static final String TARGET_PROCEEDING_INSTALLATION_MSG = "Target proceeding installation.";
    private static final String TARGET_SCHEDULED_INSTALLATION_MSG = "Target scheduled installation.";
    @Autowired
    private HawkbitSecurityProperties securityProperties;

    @Test
    @Description("Ensure that the root poll resource is available as CBOR")
    public void rootPollResourceCbor() throws Exception {
        mvc.perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant())
                .accept(DdiRestConstants.MEDIA_TYPE_CBOR)).andDo(MockMvcResultPrinter.print())
                .andExpect(content().contentType(DdiRestConstants.MEDIA_TYPE_CBOR_UTF8)).andExpect(status().isOk());
    }

    @Test
    @Description("Ensures that the API returns JSON when no Accept header is specified by the client.")
    public void apiReturnsJSONByDefault() throws Exception {
        final MvcResult result = mvc.perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant()))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_UTF8)).andReturn();

        // verify that we did not specify a content-type in the request, in case
        // there are any default values
        assertThat(result.getRequest().getHeader("Accept")).isNull();
    }

    @Test
    @Description("Ensures that targets cannot be created e.g. in plug'n play scenarios when tenant does not exists but can be created if the tenant exists.")
    @WithUser(tenantId = "tenantDoesNotExists", allSpPermissions = true, authorities = { CONTROLLER_ROLE,
            SYSTEM_ROLE }, autoCreateTenant = false)
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 1),
            @Expect(type = DistributionSetTypeCreatedEvent.class, count = 3),
            @Expect(type = SoftwareModuleTypeCreatedEvent.class, count = 2) })
    public void targetCannotBeRegisteredIfTenantDoesNotExistsButWhenExists() throws Exception {

        mvc.perform(get("/default-tenant/", tenantAware.getCurrentTenant())).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isNotFound());

        // create tenant -- creates softwaremoduletypes and distributionsettypes
        systemManagement.getTenantMetadata("tenantDoesNotExists");

        mvc.perform(get("/{}/controller/v1/aControllerId", tenantAware.getCurrentTenant()))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        // delete tenant again, will also deleted target aControllerId
        systemManagement.deleteTenant("tenantDoesNotExists");

        mvc.perform(get("/{}/controller/v1/aControllerId", tenantAware.getCurrentTenant()))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isBadRequest());
    }

    @Test
    @Description("Ensures that target poll request does not change audit data on the entity.")
    @WithUser(principal = "knownPrincipal", authorities = { SpPermission.READ_TARGET, SpPermission.UPDATE_TARGET,
            SpPermission.CREATE_TARGET }, allSpPermissions = false)
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void targetPollDoesNotModifyAuditData() throws Exception {
        // create target first with "knownPrincipal" user and audit data
        final String knownTargetControllerId = "target1";
        final String knownCreatedBy = "knownPrincipal";
        testdataFactory.createTarget(knownTargetControllerId);
        final Target findTargetByControllerID = targetManagement.getByControllerID(knownTargetControllerId).get();
        assertThat(findTargetByControllerID.getCreatedBy()).isEqualTo(knownCreatedBy);
        assertThat(findTargetByControllerID.getCreatedAt()).isNotNull();

        // make a poll, audit information should not be changed, run as
        // controller principal!
        securityRule.runAs(WithSpringAuthorityRule.withController("controller", CONTROLLER_ROLE_ANONYMOUS), () -> {
            mvc.perform(get("/{tenant}/controller/v1/" + knownTargetControllerId, tenantAware.getCurrentTenant()))
                    .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());
            return null;
        });

        // verify that audit information has not changed
        final Target targetVerify = targetManagement.getByControllerID(knownTargetControllerId).get();
        assertThat(targetVerify.getCreatedBy()).isEqualTo(findTargetByControllerID.getCreatedBy());
        assertThat(targetVerify.getCreatedAt()).isEqualTo(findTargetByControllerID.getCreatedAt());
        assertThat(targetVerify.getLastModifiedBy()).isEqualTo(findTargetByControllerID.getLastModifiedBy());
        assertThat(targetVerify.getLastModifiedAt()).isEqualTo(findTargetByControllerID.getLastModifiedAt());
    }

    @Test
    @Description("Ensures that server returns a not found response in case of empty controlloer ID.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 0) })
    public void rootRsWithoutId() throws Exception {
        mvc.perform(get("/controller/v1/")).andDo(MockMvcResultPrinter.print()).andExpect(status().isNotFound());
    }

    @Test
    @Description("Ensures that the system creates a new target in plug and play manner, i.e. target is authenticated but does not exist yet.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 1) })
    public void rootRsPlugAndPlay() throws Exception {

        final long current = System.currentTimeMillis();

        mvc.perform(get("/default-tenant/controller/v1/4711")).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk()).andExpect(content().contentType(MediaTypes.HAL_JSON_UTF8))
                .andExpect(jsonPath("$.config.polling.sleep", equalTo("00:01:00")));
        assertThat(targetManagement.getByControllerID("4711").get().getLastTargetQuery())
                .isGreaterThanOrEqualTo(current);

        assertThat(targetManagement.getByControllerID("4711").get().getUpdateStatus())
                .isEqualTo(TargetUpdateStatus.REGISTERED);

        // not allowed methods
        mvc.perform(post("/default-tenant/controller/v1/4711")).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(put("/default-tenant/controller/v1/4711")).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(delete("/default-tenant/controller/v1/4711")).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @Description("Ensures that tenant specific polling time, which is saved in the db, is delivered to the controller.")
    @WithUser(principal = "knownpricipal", allSpPermissions = false)
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 1) })
    public void pollWithModifiedGloablPollingTime() throws Exception {
        securityRule.runAs(WithSpringAuthorityRule.withUser("tenantadmin", HAS_AUTH_TENANT_CONFIGURATION), () -> {
            tenantConfigurationManagement.addOrUpdateConfiguration(TenantConfigurationKey.POLLING_TIME_INTERVAL,
                    "00:02:00");
            return null;
        });

        securityRule.runAs(WithSpringAuthorityRule.withUser("controller", CONTROLLER_ROLE_ANONYMOUS), () -> {
            mvc.perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant()))
                    .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaTypes.HAL_JSON_UTF8))
                    .andExpect(jsonPath("$.config.polling.sleep", equalTo("00:02:00")));
            return null;
        });
    }

    @Test
    @Description("Ensures that etag check results in not modified response if provided etag by client is identical to entity in repository.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 6),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 3), @Expect(type = ActionUpdatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 2),
            @Expect(type = ActionCreatedEvent.class, count = 2),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 6) })
    public void rootRsNotModified() throws Exception {
        final String etag = mvc.perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant()))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_UTF8))
                .andExpect(jsonPath("$.config.polling.sleep", equalTo("00:01:00"))).andReturn().getResponse()
                .getHeader("ETag");

        mvc.perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant()).header("If-None-Match", etag))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isNotModified());

        final Target target = targetManagement.getByControllerID("4711").get();
        final DistributionSet ds = testdataFactory.createDistributionSet("");

        assignDistributionSet(ds.getId(), "4711");

        final Action updateAction = deploymentManagement.findActiveActionsByTarget(PAGE, target.getControllerId())
                .getContent().get(0);
        final String etagWithFirstUpdate = mvc
                .perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant())
                        .header("If-None-Match", etag).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.config.polling.sleep", equalTo("00:01:00")))
                .andExpect(jsonPath("$._links.deploymentBase.href",
                        startsWith("http://localhost/" + tenantAware.getCurrentTenant()
                                + "/controller/v1/4711/deploymentBase/" + updateAction.getId())))
                .andReturn().getResponse().getHeader("ETag");

        assertThat(etagWithFirstUpdate).isNotNull();

        mvc.perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant()).header("If-None-Match",
                etagWithFirstUpdate)).andDo(MockMvcResultPrinter.print()).andExpect(status().isNotModified());

        // now lets finish the update
        sendDeploymentActionFeedback(target, updateAction, "closed", null).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        // we are again at the original state
        mvc.perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant()).header("If-None-Match", etag))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isNotModified());

        // Now another deployment
        final DistributionSet ds2 = testdataFactory.createDistributionSet("2");

        assignDistributionSet(ds2.getId(), "4711");

        final Action updateAction2 = deploymentManagement.findActiveActionsByTarget(PAGE, target.getControllerId())
                .getContent().get(0);

        mvc.perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant())
                .header("If-None-Match", etagWithFirstUpdate).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.config.polling.sleep", equalTo("00:01:00")))
                .andExpect(jsonPath("$._links.deploymentBase.href",
                        startsWith("http://localhost/" + tenantAware.getCurrentTenant()
                                + "/controller/v1/4711/deploymentBase/" + updateAction2.getId())))
                .andReturn().getResponse().getHeader("ETag");
    }

    @Test
    @Description("Ensures that the target state machine of a precomissioned target switches from "
            + "UNKNOWN to REGISTERED when the target polls for the first time.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1), @Expect(type = TargetPollEvent.class, count = 1) })
    public void rootRsPrecommissioned() throws Exception {
        testdataFactory.createTarget("4711");

        assertThat(targetManagement.getByControllerID("4711").get().getUpdateStatus())
                .isEqualTo(TargetUpdateStatus.UNKNOWN);

        final long current = System.currentTimeMillis();
        mvc.perform(get("/{tenant}/controller/v1/4711", tenantAware.getCurrentTenant()))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_UTF8))
                .andExpect(jsonPath("$.config.polling.sleep", equalTo("00:01:00")));

        assertThat(targetManagement.getByControllerID("4711").get().getLastTargetQuery())
                .isLessThanOrEqualTo(System.currentTimeMillis());
        assertThat(targetManagement.getByControllerID("4711").get().getLastTargetQuery())
                .isGreaterThanOrEqualTo(current);

        assertThat(targetManagement.getByControllerID("4711").get().getUpdateStatus())
                .isEqualTo(TargetUpdateStatus.REGISTERED);
    }

    @Test
    @Description("Ensures that the source IP address of the polling target is correctly stored in repository")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 1) })
    public void rootRsPlugAndPlayIpAddress() throws Exception {
        // test
        final String knownControllerId1 = "0815";
        final long create = System.currentTimeMillis();

        // make a poll, audit information should be set on plug and play
        securityRule.runAs(WithSpringAuthorityRule.withController("controller", CONTROLLER_ROLE_ANONYMOUS), () -> {
            mvc.perform(
                    get("/{tenant}/controller/v1/{controllerId}", tenantAware.getCurrentTenant(), knownControllerId1))
                    .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());
            return null;
        });

        // verify
        final Target target = targetManagement.getByControllerID(knownControllerId1).get();
        assertThat(target.getAddress()).isEqualTo(IpUtil.createHttpUri("127.0.0.1"));
        assertThat(target.getCreatedBy()).isEqualTo("CONTROLLER_PLUG_AND_PLAY");
        assertThat(target.getCreatedAt()).isGreaterThanOrEqualTo(create);
        assertThat(target.getLastModifiedBy()).isEqualTo("CONTROLLER_PLUG_AND_PLAY");
        assertThat(target.getLastModifiedAt()).isGreaterThanOrEqualTo(create);

    }

    @Test
    @Description("Ensures that the source IP address of the polling target is not stored in repository if disabled")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetPollEvent.class, count = 1) })
    public void rootRsIpAddressNotStoredIfDisabled() throws Exception {
        securityProperties.getClients().setTrackRemoteIp(false);

        // test
        final String knownControllerId1 = "0815";
        mvc.perform(get("/{tenant}/controller/v1/{controllerId}", tenantAware.getCurrentTenant(), knownControllerId1))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        // verify
        final Target target = targetManagement.getByControllerID(knownControllerId1).get();
        assertThat(target.getAddress()).isEqualTo(IpUtil.createHttpUri("***"));

        securityProperties.getClients().setTrackRemoteIp(true);
    }

    @Test
    @Description("Controller trys to finish an update process after it has been finished by an error action status.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1), @Expect(type = ActionUpdatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 2),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3) })
    public void tryToFinishAnUpdateProcessAfterItHasBeenFinished() throws Exception {
        final DistributionSet ds = testdataFactory.createDistributionSet("");
        Target savedTarget = testdataFactory.createTarget("911");
        savedTarget = getFirstAssignedTarget(assignDistributionSet(ds.getId(), savedTarget.getControllerId()));
        final Action savedAction = deploymentManagement.findActiveActionsByTarget(PAGE, savedTarget.getControllerId())
                .getContent().get(0);
        sendDeploymentActionFeedback(savedTarget, savedAction, "proceeding", null).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        sendDeploymentActionFeedback(savedTarget, savedAction, "closed", "failure").andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());

        sendDeploymentActionFeedback(savedTarget, savedAction, "closed", "success").andDo(MockMvcResultPrinter.print())
                .andExpect(status().isGone());
    }

    @Test
    @Description("Controller sends attribute update request after device successfully closed software update.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 2),
            @Expect(type = ActionCreatedEvent.class, count = 2), @Expect(type = ActionUpdatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 6), @Expect(type = TargetPollEvent.class, count = 4),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1) })
    public void attributeUpdateRequestSendingAfterSuccessfulDeployment() throws Exception {
        final DistributionSet ds = testdataFactory.createDistributionSet("1");
        final Target savedTarget = testdataFactory.createTarget("922");
        final Map<String, String> attributes = Collections.singletonMap("AttributeKey", "AttributeValue");
        assertThatAttributesUpdateIsRequested(savedTarget.getControllerId());

        mvc.perform(put("/{tenant}/controller/v1/{controllerId}/configData", tenantAware.getCurrentTenant(),
                savedTarget.getControllerId())
                        .content(JsonBuilder.configData(savedTarget.getControllerId(), attributes, "closed").toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        assertThatAttributesUpdateIsNotRequested(savedTarget.getControllerId());

        assertAttributesUpdateNotRequestedAfterFailedDeployment(savedTarget, ds);

        assertAttributesUpdateRequestedAfterSuccessfulDeployment(savedTarget, ds);
    }

    @Step
    private void assertAttributesUpdateNotRequestedAfterFailedDeployment(Target target, final DistributionSet ds)
            throws Exception {
        target = getFirstAssignedTarget(assignDistributionSet(ds.getId(), target.getControllerId()));
        final Action action = deploymentManagement.findActiveActionsByTarget(PAGE, target.getControllerId())
                .getContent().get(0);
        sendDeploymentActionFeedback(target, action, "closed", "failure").andExpect(status().isOk());
        assertThatAttributesUpdateIsNotRequested(target.getControllerId());
    }

    @Step
    private void assertAttributesUpdateRequestedAfterSuccessfulDeployment(Target target, final DistributionSet ds)
            throws Exception {
        target = getFirstAssignedTarget(assignDistributionSet(ds.getId(), target.getControllerId()));
        final Action action = deploymentManagement.findActiveActionsByTarget(PAGE, target.getControllerId())
                .getContent().get(0);
        sendDeploymentActionFeedback(target, action, "closed", null).andExpect(status().isOk());
        assertThatAttributesUpdateIsRequested(target.getControllerId());
    }

    private void assertThatAttributesUpdateIsRequested(final String targetControllerId) throws Exception {
        mvc.perform(get("/{tenant}/controller/v1/{controllerId}", tenantAware.getCurrentTenant(), targetControllerId)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andExpect(jsonPath("$._links.configData.href").isNotEmpty());
    }

    private void assertThatAttributesUpdateIsNotRequested(final String targetControllerId) throws Exception {
        mvc.perform(get("/{tenant}/controller/v1/{controllerId}", tenantAware.getCurrentTenant(), targetControllerId)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andExpect(jsonPath("$._links.configData").doesNotExist());
    }

    private ResultActions sendDeploymentActionFeedback(final Target target, final Action action, final String execution,
            String finished, String message) throws Exception {
        if (finished == null) {
            finished = "none";
        }
        if (message == null) {
            message = RandomStringUtils.randomAlphanumeric(1000);
        }
        final String feedback = JsonBuilder.deploymentActionFeedback(action.getId().toString(), execution, finished,
                message);
        return mvc.perform(post("/{tenant}/controller/v1/{controllerId}/deploymentBase/{actionId}/feedback",
                tenantAware.getCurrentTenant(), target.getControllerId(), action.getId()).content(feedback)
                        .contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions sendDeploymentActionFeedback(final Target target, final Action action, final String execution,
            final String finished) throws Exception {
        return sendDeploymentActionFeedback(target, action, execution, finished, null);
    }

    @Test
    @Description("Test to verify that only a specific count of messages are returned based on the input actionHistory for getControllerDeploymentActionFeedback endpoint.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1), @Expect(type = ActionUpdatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3) })
    public void testActionHistoryCount() throws Exception {
        final DistributionSet ds = testdataFactory.createDistributionSet("");
        Target savedTarget = testdataFactory.createTarget("911");
        savedTarget = getFirstAssignedTarget(assignDistributionSet(ds.getId(), savedTarget.getControllerId()));
        final Action savedAction = deploymentManagement.findActiveActionsByTarget(PAGE, savedTarget.getControllerId())
                .getContent().get(0);

        sendDeploymentActionFeedback(savedTarget, savedAction, "scheduled", null, TARGET_SCHEDULED_INSTALLATION_MSG)
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        sendDeploymentActionFeedback(savedTarget, savedAction, "proceeding", null, TARGET_PROCEEDING_INSTALLATION_MSG)
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        sendDeploymentActionFeedback(savedTarget, savedAction, "closed", "success", TARGET_COMPLETED_INSTALLATION_MSG)
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        mvc.perform(get("/{tenant}/controller/v1/911/deploymentBase/" + savedAction.getId() + "?actionHistory=3",
                tenantAware.getCurrentTenant()).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.actionHistory.messages",
                        hasItem(containsString(TARGET_PROCEEDING_INSTALLATION_MSG))))
                .andExpect(jsonPath("$.actionHistory.messages",
                        hasItem(containsString(TARGET_SCHEDULED_INSTALLATION_MSG))));
    }

    @Test
    @Description("Test to verify that a zero input value of actionHistory results in no action history appended for getControllerDeploymentActionFeedback endpoint.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1), @Expect(type = ActionUpdatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3) })
    public void testActionHistoryZeroInput() throws Exception {
        final DistributionSet ds = testdataFactory.createDistributionSet("");
        Target savedTarget = testdataFactory.createTarget("911");
        savedTarget = getFirstAssignedTarget(assignDistributionSet(ds.getId(), savedTarget.getControllerId()));
        final Action savedAction = deploymentManagement.findActiveActionsByTarget(PAGE, savedTarget.getControllerId())
                .getContent().get(0);

        sendDeploymentActionFeedback(savedTarget, savedAction, "scheduled", null, TARGET_SCHEDULED_INSTALLATION_MSG)
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        sendDeploymentActionFeedback(savedTarget, savedAction, "proceeding", null, TARGET_PROCEEDING_INSTALLATION_MSG)
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        sendDeploymentActionFeedback(savedTarget, savedAction, "closed", "success", TARGET_COMPLETED_INSTALLATION_MSG)
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        mvc.perform(get("/{tenant}/controller/v1/911/deploymentBase/" + savedAction.getId() + "?actionHistory=-2",
                tenantAware.getCurrentTenant()).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());
    }

    @Test
    @Description("Test to verify that entire action history is returned if the input value for actionHistory is -1, for getControllerDeploymentActionFeedback endpoint.")
    @ExpectEvents({ @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = DistributionSetCreatedEvent.class, count = 1),
            @Expect(type = TargetAssignDistributionSetEvent.class, count = 1),
            @Expect(type = ActionCreatedEvent.class, count = 1), @Expect(type = ActionUpdatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2),
            @Expect(type = TargetAttributesRequestedEvent.class, count = 1),
            @Expect(type = SoftwareModuleCreatedEvent.class, count = 3) })
    public void testActionHistoryNegativeInput() throws Exception {
        final DistributionSet ds = testdataFactory.createDistributionSet("");
        Target savedTarget = testdataFactory.createTarget("911");
        savedTarget = getFirstAssignedTarget(assignDistributionSet(ds.getId(), savedTarget.getControllerId()));
        final Action savedAction = deploymentManagement.findActiveActionsByTarget(PAGE, savedTarget.getControllerId())
                .getContent().get(0);

        sendDeploymentActionFeedback(savedTarget, savedAction, "scheduled", null, TARGET_SCHEDULED_INSTALLATION_MSG)
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        sendDeploymentActionFeedback(savedTarget, savedAction, "proceeding", null, TARGET_PROCEEDING_INSTALLATION_MSG)
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        sendDeploymentActionFeedback(savedTarget, savedAction, "closed", "success", TARGET_COMPLETED_INSTALLATION_MSG)
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        mvc.perform(get("/{tenant}/controller/v1/911/deploymentBase/" + savedAction.getId() + "?actionHistory=-1",
                tenantAware.getCurrentTenant()).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.actionHistory.messages",
                        hasItem(containsString(TARGET_SCHEDULED_INSTALLATION_MSG))))
                .andExpect(jsonPath("$.actionHistory.messages",
                        hasItem(containsString(TARGET_PROCEEDING_INSTALLATION_MSG))))
                .andExpect(jsonPath("$.actionHistory.messages",
                        hasItem(containsString(TARGET_COMPLETED_INSTALLATION_MSG))));
    }

    @Test
    @Description("Test the polling time based on different maintenance window start and end time.")
    public void sleepTimeResponseForDifferentMaintenanceWindowParameters() throws Exception {
        final DistributionSet ds = testdataFactory.createDistributionSet("");

        securityRule.runAs(WithSpringAuthorityRule.withUser("tenantadmin", HAS_AUTH_TENANT_CONFIGURATION), () -> {
            tenantConfigurationManagement.addOrUpdateConfiguration(TenantConfigurationKey.POLLING_TIME_INTERVAL,
                    "00:05:00");
            tenantConfigurationManagement.addOrUpdateConfiguration(TenantConfigurationKey.MIN_POLLING_TIME_INTERVAL,
                    "00:01:00");
            return null;
        });

        final Target savedTarget = testdataFactory.createTarget("1911");
        assignDistributionSetWithMaintenanceWindow(ds.getId(), savedTarget.getControllerId(), getTestSchedule(16),
                getTestDuration(10), getTestTimeZone()).getAssignedEntity().iterator().next();

        mvc.perform(get("/default-tenant/controller/v1/1911/")).andExpect(status().isOk())
                .andExpect(jsonPath("$.config.polling.sleep", greaterThanOrEqualTo("00:05:00")));

        final Target savedTarget1 = testdataFactory.createTarget("2911");
        final DistributionSet ds1 = testdataFactory.createDistributionSet("1");
        assignDistributionSetWithMaintenanceWindow(ds1.getId(), savedTarget1.getControllerId(), getTestSchedule(10),
                getTestDuration(10), getTestTimeZone()).getAssignedEntity().iterator().next();

        mvc.perform(get("/default-tenant/controller/v1/2911/")).andExpect(status().isOk())
                .andExpect(jsonPath("$.config.polling.sleep", lessThan("00:05:00")))
                .andExpect(jsonPath("$.config.polling.sleep", greaterThanOrEqualTo("00:03:00")));

        final Target savedTarget2 = testdataFactory.createTarget("3911");
        final DistributionSet ds2 = testdataFactory.createDistributionSet("2");
        assignDistributionSetWithMaintenanceWindow(ds2.getId(), savedTarget2.getControllerId(), getTestSchedule(5),
                getTestDuration(5), getTestTimeZone()).getAssignedEntity().iterator().next();

        mvc.perform(get("/default-tenant/controller/v1/3911/")).andExpect(status().isOk())
                .andExpect(jsonPath("$.config.polling.sleep", lessThan("00:02:00")));

        final Target savedTarget3 = testdataFactory.createTarget("4911");
        final DistributionSet ds3 = testdataFactory.createDistributionSet("3");
        assignDistributionSetWithMaintenanceWindow(ds3.getId(), savedTarget3.getControllerId(), getTestSchedule(-5),
                getTestDuration(15), getTestTimeZone()).getAssignedEntity().iterator().next();

        mvc.perform(get("/default-tenant/controller/v1/4911/")).andExpect(status().isOk())
                .andExpect(jsonPath("$.config.polling.sleep", equalTo("00:05:00")));

    }

    @Test
    @Description("Test download and update values before maintenance window start time.")
    public void downloadAndUpdateStatusBeforeMaintenanceWindowStartTime() throws Exception {
        Target savedTarget = testdataFactory.createTarget("1911");
        final DistributionSet ds = testdataFactory.createDistributionSet("");
        savedTarget = getFirstAssignedTarget(assignDistributionSetWithMaintenanceWindow(ds.getId(),
                savedTarget.getControllerId(), getTestSchedule(2), getTestDuration(1), getTestTimeZone()));

        mvc.perform(get("/default-tenant/controller/v1/1911/")).andExpect(status().isOk());

        final Action action = deploymentManagement.findActiveActionsByTarget(PAGE, savedTarget.getControllerId())
                .getContent().get(0);

        mvc.perform(get("/{tenant}/controller/v1/1911/deploymentBase/{actionId}", tenantAware.getCurrentTenant(),
                action.getId()).accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk()).andExpect(jsonPath("$.deployment.download", equalTo("forced")))
                .andExpect(jsonPath("$.deployment.update", equalTo("skip")))
                .andExpect(jsonPath("$.deployment.maintenanceWindow", equalTo("unavailable")));
    }

    @Test
    @Description("Test download and update values after maintenance window start time.")
    public void downloadAndUpdateStatusDuringMaintenanceWindow() throws Exception {
        Target savedTarget = testdataFactory.createTarget("1911");
        final DistributionSet ds = testdataFactory.createDistributionSet("");
        savedTarget = getFirstAssignedTarget(assignDistributionSetWithMaintenanceWindow(ds.getId(),
                savedTarget.getControllerId(), getTestSchedule(-5), getTestDuration(10), getTestTimeZone()));

        mvc.perform(get("/default-tenant/controller/v1/1911/")).andExpect(status().isOk());

        final Action action = deploymentManagement.findActiveActionsByTarget(PAGE, savedTarget.getControllerId())
                .getContent().get(0);

        mvc.perform(get("/{tenant}/controller/v1/1911/deploymentBase/{actionId}", tenantAware.getCurrentTenant(),
                action.getId()).accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk()).andExpect(jsonPath("$.deployment.download", equalTo("forced")))
                .andExpect(jsonPath("$.deployment.update", equalTo("forced")))
                .andExpect(jsonPath("$.deployment.maintenanceWindow", equalTo("available")));
    }

    @Test
    @Description("Assign multiple DS in multi-assignment mode. The earliest active Action is exposed to the controller.")
    public void earliestActionIsExposedToControllerInMultiAssignMode() throws Exception {
        enableMultiAssignments();
        final Target target = testdataFactory.createTarget();
        final DistributionSet ds1 = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        final DistributionSet ds2 = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        final Action action1 = getFirstAssignedAction(assignDistributionSet(ds1.getId(), target.getControllerId(), 56));
        final Long action2Id = getFirstAssignedActionId(
                assignDistributionSet(ds2.getId(), target.getControllerId(), 34));

        assertDeploymentActionIsExposedToTarget(target.getControllerId(), action1.getId());
        sendDeploymentActionFeedback(target, action1, "closed", "success");
        assertDeploymentActionIsExposedToTarget(target.getControllerId(), action2Id);

    }

    @Test
    @Description("Assign a DS before multi-assignment mode is enabled. The action without a weight should take the default value to for comparing.")
    public void verifyActionOrderExposedToController() throws Exception {
        final Target target = testdataFactory.createTarget();
        final DistributionSet ds1 = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        final DistributionSet ds2 = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        final DistributionSet ds3 = testdataFactory.createDistributionSet(UUID.randomUUID().toString());
        // old action without weight
        final Action action1 = getFirstAssignedAction(assignDistributionSet(ds1.getId(), target.getControllerId()));
        enableMultiAssignments();
        setTenantDefaultWeightValue(600);
        final Action action2 = getFirstAssignedAction(
                assignDistributionSet(ds2.getId(), target.getControllerId(), 621));
        final Action action3 = getFirstAssignedAction(assignDistributionSet(ds3.getId(), target.getControllerId(), 25));

        assertDeploymentActionIsExposedToTarget(target.getControllerId(), action2.getId());
        sendDeploymentActionFeedback(target, action2, "closed", "success");
        assertDeploymentActionIsExposedToTarget(target.getControllerId(), action1.getId());
        sendDeploymentActionFeedback(target, action1, "closed", "success");
        assertDeploymentActionIsExposedToTarget(target.getControllerId(), action3.getId());
    }

    @Test
    @Description("The system should not create a new target because of a too long controller id.")
    public void rootRsWithInvalidControllerId() throws Exception {
        final String invalidControllerId = RandomStringUtils.randomAlphabetic(Target.CONTROLLER_ID_MAX_SIZE + 1);
        mvc.perform(get("/{tenant}/controller/v1/{controllerId}", tenantAware.getCurrentTenant(), invalidControllerId))
                .andExpect(status().isBadRequest());
    }

    public void assertDeploymentActionIsExposedToTarget(final String controllerId, final long expectedActionId)
            throws Exception {
        final String expectedDeploymentBaseLink = String.format("/%s/controller/v1/%s/deploymentBase/%d",
                tenantAware.getCurrentTenant(), controllerId, expectedActionId);
        mvc.perform(get("/{tenant}/controller/v1/{controllerId}", tenantAware.getCurrentTenant(), controllerId)
                .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$._links.deploymentBase.href", containsString(expectedDeploymentBaseLink)));

    }
}
