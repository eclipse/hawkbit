/**
 * Copyright (c) 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.rest.ddi.documentation;

import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.restdocs.snippet.Attributes.key;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.hawkbit.ddi.rest.api.DdiRestConstants;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.ArtifactUpload;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.eclipse.hawkbit.rest.documentation.AbstractApiRestDocumentation;
import org.eclipse.hawkbit.rest.documentation.ApiModelPropertiesGeneric;
import org.eclipse.hawkbit.rest.util.JsonBuilder;
import org.eclipse.hawkbit.rest.util.MockMvcResultPrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Documentation generation for Direct Device Integration API.
 *
 */
@Feature("Documentation Verfication - Direct Device Integration API")
@Story("Root Resource")
public class RootControllerDocumentationTest extends AbstractApiRestDocumentation {
    private static final String CONTROLLER_ID = "CONTROLLER_ID";

    @Override
    public String getResourceName() {
        return "rootcontroller";
    }

    @BeforeEach
    public void setUp() {
        host = "ddi-api.host";
    }

    @Test
    @Description("This base resource can be regularly polled by the controller on the provisioning target or device "
            + "in order to retrieve actions that need to be executed. In this case including a config pull request and a deployment. The resource supports Etag based modification "
            + "checks in order to save traffic.")
    @WithUser(authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    public void getControllerBaseWithOpenDeplyoment() throws Exception {
        final DistributionSet set = testdataFactory.createDistributionSet("one");

        final Target target = targetManagement.create(entityFactory.target().create().controllerId(CONTROLLER_ID));
        assignDistributionSet(set.getId(), target.getControllerId());

        mockMvc.perform(get(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}",
                tenantAware.getCurrentTenant(), target.getControllerId()).accept(MediaTypes.HAL_JSON_VALUE))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON))
                .andDo(this.document.document(
                        pathParameters(parameterWithName("tenant").description(ApiModelPropertiesGeneric.TENANT),
                                parameterWithName("controllerId").description(DdiApiModelProperties.CONTROLLER_ID)),
                        responseFields(
                                fieldWithPath("config.polling").description(DdiApiModelProperties.TARGET_POLL_TIME),
                                fieldWithPath("config.polling.sleep").description(DdiApiModelProperties.TARGET_SLEEP),
                                fieldWithPath("_links").description(DdiApiModelProperties.TARGET_OPEN_ACTIONS),
                                fieldWithPath("_links.deploymentBase").description(DdiApiModelProperties.DEPLOYMENT),
                                fieldWithPath("_links.configData")
                                        .description(DdiApiModelProperties.TARGET_CONFIG_DATA))));
    }

    @Test
    @Description("This base resource can be regularly polled by the controller on the provisioning target or device "
            + "in order to retrieve actions that need to be executed. In this case including a config pull request and a cancellation. "
            + "Note: as with deployments the cancel action has to be confirmed or rejected in order to move on to the next action.")
    @WithUser(authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    public void getControllerBaseWithOpenDeploymentCancellation() throws Exception {
        final DistributionSet set = testdataFactory.createDistributionSet("one");
        final DistributionSet setTwo = testdataFactory.createDistributionSet("two");

        final Target target = targetManagement.create(entityFactory.target().create().controllerId(CONTROLLER_ID));
        assignDistributionSet(set.getId(), target.getControllerId());
        assignDistributionSet(setTwo.getId(), target.getControllerId());

        mockMvc.perform(get(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}",
                tenantAware.getCurrentTenant(), target.getControllerId()).accept(MediaTypes.HAL_JSON_VALUE))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON))
                .andDo(this.document.document(
                        pathParameters(parameterWithName("tenant").description(ApiModelPropertiesGeneric.TENANT),
                                parameterWithName("controllerId").description(DdiApiModelProperties.CONTROLLER_ID)),
                        responseFields(
                                fieldWithPath("config.polling").description(DdiApiModelProperties.TARGET_POLL_TIME),
                                fieldWithPath("config.polling.sleep").description(DdiApiModelProperties.TARGET_SLEEP),
                                fieldWithPath("_links").description(DdiApiModelProperties.TARGET_OPEN_ACTIONS),
                                fieldWithPath("_links.cancelAction").description(DdiApiModelProperties.DEPLOYMENT),
                                fieldWithPath("_links.configData")
                                        .description(DdiApiModelProperties.TARGET_CONFIG_DATA))));
    }

    @Test
    @Description("The SP server might cancel an operation, e.g. an unfinished update has a successor. "
            + "It is up to the provisioning target to decide either to accept the cancellation or reject it.")
    @WithUser(authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    public void getControllerCancelAction() throws Exception {
        final DistributionSet set = testdataFactory.createDistributionSet("one");

        set.getModules().forEach(module -> {
            final byte random[] = RandomStringUtils.random(5).getBytes();

            artifactManagement.create(
                    new ArtifactUpload(new ByteArrayInputStream(random), module.getId(), "binary.tgz", false, 0));
            artifactManagement.create(
                    new ArtifactUpload(new ByteArrayInputStream(random), module.getId(), "file.signature", false, 0));
        });

        final Target target = targetManagement.create(entityFactory.target().create().controllerId(CONTROLLER_ID));
        final Long actionId = getFirstAssignedActionId(assignDistributionSet(set.getId(), target.getControllerId()));
        final Action cancelAction = deploymentManagement.cancelAction(actionId);

        mockMvc.perform(
                get(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" + DdiRestConstants.CANCEL_ACTION
                        + "/{actionId}", tenantAware.getCurrentTenant(), target.getControllerId(), cancelAction.getId())
                                .accept(MediaTypes.HAL_JSON_VALUE))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON))
                .andDo(this.document.document(
                        pathParameters(parameterWithName("tenant").description(ApiModelPropertiesGeneric.TENANT),
                                parameterWithName("controllerId").description(DdiApiModelProperties.CONTROLLER_ID),
                                parameterWithName("actionId").description(DdiApiModelProperties.ACTION_ID_CANCELED)),

                        responseFields(fieldWithPath("id").description(DdiApiModelProperties.ACTION_ID),
                                fieldWithPath("cancelAction").description(DdiApiModelProperties.CANCEL_ACTION),
                                fieldWithPath("cancelAction.stopId")
                                        .description(DdiApiModelProperties.ACTION_ID_CANCELED)

                        )));
    }

    @Test
    @Description("It is up to the device to decided how much intermediate feedback is "
            + "provided. However, the action will be kept open until the controller on the device reports a "
            + "finished (either successfull or error) or rejects the oprtioan, e.g. the canceled actions have been started already.")
    @WithUser(authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    public void postCancelActionFeedback() throws Exception {
        final DistributionSet set = testdataFactory.createDistributionSet("one");

        final Target target = targetManagement.create(entityFactory.target().create().controllerId(CONTROLLER_ID));
        final Long actionId = getFirstAssignedActionId(assignDistributionSet(set.getId(), target.getControllerId()));
        final Action cancelAction = deploymentManagement.cancelAction(actionId);

        mockMvc.perform(post(
                DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" + DdiRestConstants.CANCEL_ACTION
                        + "/{actionId}/feedback",
                tenantAware.getCurrentTenant(), target.getControllerId(), cancelAction.getId()).content(
                        JsonBuilder.cancelActionFeedback(cancelAction.getId().toString(), "closed", "Some feedback"))
                        .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andDo(this.document.document(
                        pathParameters(parameterWithName("tenant").description(ApiModelPropertiesGeneric.TENANT),
                                parameterWithName("controllerId").description(DdiApiModelProperties.CONTROLLER_ID),
                                parameterWithName("actionId").description(DdiApiModelProperties.ACTION_ID_CANCELED)),
                        requestFields(optionalRequestFieldWithPath("id").description(DdiApiModelProperties.FEEDBACK_ACTION_ID),
                                requestFieldWithPath("status").description(DdiApiModelProperties.TARGET_STATUS),
                                requestFieldWithPath("status.execution")
                                        .description(DdiApiModelProperties.TARGET_EXEC_STATUS).type("enum")
                                        .attributes(key("value").value(
                                                "['closed', 'proceeding', 'download', 'downloaded', 'canceled','scheduled', 'rejected', 'resumed']")),
                                requestFieldWithPath("status.result")
                                        .description(DdiApiModelProperties.TARGET_RESULT_VALUE),
                                requestFieldWithPath("status.result.finished")
                                        .description(DdiApiModelProperties.TARGET_RESULT_FINISHED).type("enum")
                                        .attributes(key("value").value("['success', 'failure', 'none']")),
                                optionalRequestFieldWithPath("status.details")
                                        .description(DdiApiModelProperties.TARGET_RESULT_DETAILS))));
    }

    @Test
    @Description("The usual behaviour is that when a new device resgisters at the server it is "
            + "requested to provide the meta information that will allow the server to identify the device on a "
            + "hardware level (e.g. hardware revision, mac address, serial number etc.).")
    @WithUser(authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    public void putConfigData() throws Exception {
        final Target target = targetManagement.create(entityFactory.target().create().controllerId(CONTROLLER_ID));

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("hwRevision", "2");
        attributes.put("VIN", "JH4TB2H26CC000000");

        mockMvc.perform(
                put(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" + DdiRestConstants.CONFIG_DATA_ACTION,
                        tenantAware.getCurrentTenant(), target.getControllerId())
                                .content(JsonBuilder.configData(attributes, "merge").toString())
                                .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andDo(this.document.document(
                        pathParameters(parameterWithName("tenant").description(ApiModelPropertiesGeneric.TENANT),
                                parameterWithName("controllerId").description(DdiApiModelProperties.CONTROLLER_ID)),
                        requestFields(
                                requestFieldWithPath("data").description(DdiApiModelProperties.TARGET_CONFIG_DATA),
                                optionalRequestFieldWithPath("mode").description(DdiApiModelProperties.UPDATE_MODE)
                                        .type("enum")
                                        .attributes(key("value").value("['merge', 'replace', 'remove']")))));

    }

    @Test
    @Description("Core resource for deployment operations. Contains all information necessary in order to execute the operation.")
    @WithUser(authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    public void getControllerBasedeploymentAction() throws Exception {
        final DistributionSet set = testdataFactory.createDistributionSet("one");

        set.getModules().forEach(module -> {
            final byte random[] = RandomStringUtils.random(5).getBytes();

            artifactManagement.create(
                    new ArtifactUpload(new ByteArrayInputStream(random), module.getId(), "binary.tgz", false, 0));
            artifactManagement.create(
                    new ArtifactUpload(new ByteArrayInputStream(random), module.getId(), "file.signature", false, 0));
        });

        softwareModuleManagement.createMetaData(
                entityFactory.softwareModuleMetadata().create(set.getModules().iterator().next().getId())
                        .key("aMetadataKey").value("Metadata value as defined in software module").targetVisible(true));

        final Target target = targetManagement.create(entityFactory.target().create().controllerId(CONTROLLER_ID));
        final Long actionId = getFirstAssignedActionId(assignDistributionSetWithMaintenanceWindow(set.getId(),
                target.getControllerId(), getTestSchedule(-5), getTestDuration(10), getTestTimeZone()));

        controllerManagement.addInformationalActionStatus(
                entityFactory.actionStatus().create(actionId).message("Started download").status(Status.DOWNLOAD));
        controllerManagement.addInformationalActionStatus(entityFactory.actionStatus().create(actionId)
                .message("Download failed. ErrorCode #5876745. Retry").status(Status.WARNING));
        controllerManagement.addInformationalActionStatus(
                entityFactory.actionStatus().create(actionId).message("Download done").status(Status.DOWNLOADED));
        controllerManagement.addInformationalActionStatus(
                entityFactory.actionStatus().create(actionId).message("Write firmware").status(Status.RUNNING));
        controllerManagement.addInformationalActionStatus(
                entityFactory.actionStatus().create(actionId).message("Reboot").status(Status.RUNNING));

        mockMvc.perform(get(
                DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" + DdiRestConstants.DEPLOYMENT_BASE_ACTION
                        + "/{actionId}?actionHistory=10",
                tenantAware.getCurrentTenant(), target.getControllerId(), actionId).accept(MediaTypes.HAL_JSON_VALUE))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON))
                .andDo(this.document.document(
                        pathParameters(parameterWithName("tenant").description(ApiModelPropertiesGeneric.TENANT),
                                parameterWithName("controllerId").description(DdiApiModelProperties.CONTROLLER_ID),
                                parameterWithName("actionId").description(DdiApiModelProperties.ACTION_ID)),
                        requestParameters(
                                parameterWithName("actionHistory").description(DdiApiModelProperties.ACTION_HISTORY)),
                        responseFields(fieldWithPath("id").description(DdiApiModelProperties.ACTION_ID),
                                fieldWithPath("deployment").description(DdiApiModelProperties.DEPLOYMENT),
                                fieldWithPath("deployment.download")
                                        .description(DdiApiModelProperties.HANDLING_DOWNLOAD).type("enum")
                                        .attributes(key("value").value("['skip', 'attempt', 'forced']")),
                                fieldWithPath("deployment.update").description(DdiApiModelProperties.HANDLING_UPDATE)
                                        .type("enum").attributes(key("value").value("['skip', 'attempt', 'forced']")),
                                fieldWithPath("deployment.maintenanceWindow")
                                        .description(DdiApiModelProperties.MAINTENANCE_WINDOW).type("enum")
                                        .attributes(key("value").value("['available', 'unavailable']")),
                                fieldWithPath("deployment.chunks").description(DdiApiModelProperties.CHUNK),
                                fieldWithPath("deployment.chunks[].metadata")
                                        .description(DdiApiModelProperties.CHUNK_META_DATA).optional(),
                                fieldWithPath("deployment.chunks[].metadata[].key")
                                        .description(DdiApiModelProperties.CHUNK_META_DATA_KEY).optional(),
                                fieldWithPath("deployment.chunks[].metadata[].value")
                                        .description(DdiApiModelProperties.CHUNK_META_DATA_VALUE).optional(),
                                fieldWithPath("deployment.chunks[].part").description(DdiApiModelProperties.CHUNK_TYPE),
                                fieldWithPath("deployment.chunks[].name").description(DdiApiModelProperties.CHUNK_NAME),
                                fieldWithPath("deployment.chunks[].version")
                                        .description(DdiApiModelProperties.CHUNK_VERSION),
                                fieldWithPath("deployment.chunks[].artifacts")
                                        .description(DdiApiModelProperties.ARTIFACTS),
                                fieldWithPath("deployment.chunks[].artifacts[].filename")
                                        .description(DdiApiModelProperties.ARTIFACTS),
                                fieldWithPath("deployment.chunks[].artifacts[].hashes")
                                        .description(DdiApiModelProperties.ARTIFACTS),
                                fieldWithPath("deployment.chunks[].artifacts[].hashes.sha1")
                                        .description(DdiApiModelProperties.ARTIFACT_HASHES_SHA1),
                                fieldWithPath("deployment.chunks[].artifacts[].hashes.md5")
                                        .description(DdiApiModelProperties.ARTIFACT_HASHES_MD5),
                                fieldWithPath("deployment.chunks[].artifacts[].hashes.sha256")
                                        .description(DdiApiModelProperties.ARTIFACT_HASHES_SHA256),
                                fieldWithPath("deployment.chunks[].artifacts[].size")
                                        .description(DdiApiModelProperties.ARTIFACT_SIZE),
                                fieldWithPath("deployment.chunks[].artifacts[]._links.download")
                                        .description(DdiApiModelProperties.ARTIFACT_HTTPS_DOWNLOAD_LINK_BY_CONTROLLER),
                                fieldWithPath("deployment.chunks[].artifacts[]._links.md5sum")
                                        .description(DdiApiModelProperties.ARTIFACT_HTTPS_HASHES_MD5SUM_LINK),
                                fieldWithPath("deployment.chunks[].artifacts[]._links.download-http")
                                        .description(DdiApiModelProperties.ARTIFACT_HTTP_DOWNLOAD_LINK_BY_CONTROLLER),
                                fieldWithPath("deployment.chunks[].artifacts[]._links.md5sum-http")
                                        .description(DdiApiModelProperties.ARTIFACT_HTTP_HASHES_MD5SUM_LINK),
                                fieldWithPath("actionHistory").description(DdiApiModelProperties.ACTION_HISTORY_RESP),
                                fieldWithPath("actionHistory.status")
                                        .description(DdiApiModelProperties.ACTION_HISTORY_RESP_STATUS),
                                fieldWithPath("actionHistory.messages")
                                        .description(DdiApiModelProperties.ACTION_HISTORY_RESP_MESSAGES))));

    }

    @Test
    @Description("Core resource for deployment operations. Contains all information necessary in order to execute the operation. Example with maintenance window where the device is requested to download only as it is not in the maintenance window yet.")
    @WithUser(authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    public void getControllerBasedeploymentActionWithMaintenanceWindow() throws Exception {
        final DistributionSet set = testdataFactory.createDistributionSet("one");

        final Target target = targetManagement.create(entityFactory.target().create().controllerId(CONTROLLER_ID));
        final Long actionId = getFirstAssignedActionId(assignDistributionSetWithMaintenanceWindow(set.getId(),
                target.getControllerId(), getTestSchedule(2), getTestDuration(1), getTestTimeZone()));

        mockMvc.perform(get(
                DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/" + DdiRestConstants.DEPLOYMENT_BASE_ACTION
                        + "/{actionId}?actionHistory=10",
                tenantAware.getCurrentTenant(), target.getControllerId(), actionId).accept(MediaTypes.HAL_JSON_VALUE))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON))
                .andDo(this.document.document(
                        pathParameters(parameterWithName("tenant").description(ApiModelPropertiesGeneric.TENANT),
                                parameterWithName("controllerId").description(DdiApiModelProperties.CONTROLLER_ID),
                                parameterWithName("actionId").description(DdiApiModelProperties.ACTION_ID)),
                        requestParameters(
                                parameterWithName("actionHistory").description(DdiApiModelProperties.ACTION_HISTORY)),
                        responseFields(fieldWithPath("id").description(DdiApiModelProperties.ACTION_ID),
                                fieldWithPath("deployment").description(DdiApiModelProperties.DEPLOYMENT),
                                fieldWithPath("deployment.download")
                                        .description(DdiApiModelProperties.HANDLING_DOWNLOAD),
                                fieldWithPath("deployment.update").description(DdiApiModelProperties.HANDLING_UPDATE)
                                        .type("enum").attributes(key("value").value("['attempt', 'forced']")),
                                fieldWithPath("deployment.maintenanceWindow")
                                        .description(DdiApiModelProperties.MAINTENANCE_WINDOW).type("enum")
                                        .attributes(key("value").value("['available', 'unavailable']")),
                                fieldWithPath("deployment.chunks").description(DdiApiModelProperties.CHUNK),
                                fieldWithPath("deployment.chunks[].part").description(DdiApiModelProperties.CHUNK_TYPE),
                                fieldWithPath("deployment.chunks[].name").description(DdiApiModelProperties.CHUNK_NAME),
                                fieldWithPath("deployment.chunks[].version")
                                        .description(DdiApiModelProperties.CHUNK_VERSION))));

    }

    @Test
    @Description("Feedback channel. It is up to the device to decided how much intermediate feedback is "
            + "provided. However, the action will be kept open until the controller on the device reports a "
            + "finished (either successfull or error).")
    @WithUser(authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    public void postBasedeploymentActionFeedback() throws Exception {
        final DistributionSet set = testdataFactory.createDistributionSet("one");

        final Target target = targetManagement.create(entityFactory.target().create().controllerId(CONTROLLER_ID));
        final Long actionId = getFirstAssignedActionId(assignDistributionSet(set.getId(), target.getControllerId()));

        mockMvc.perform(post(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/"
                + DdiRestConstants.DEPLOYMENT_BASE_ACTION + "/{actionId}/feedback", tenantAware.getCurrentTenant(),
                target.getControllerId(), actionId)
                        .content(
                                JsonBuilder.deploymentActionFeedback(actionId.toString(), "closed", "Feedback message"))
                        .contentType(MediaType.APPLICATION_JSON_UTF8).accept(MediaTypes.HAL_JSON_VALUE))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andDo(this.document.document(
                        pathParameters(parameterWithName("tenant").description(ApiModelPropertiesGeneric.TENANT),
                                parameterWithName("controllerId").description(DdiApiModelProperties.CONTROLLER_ID),
                                parameterWithName("actionId").description(DdiApiModelProperties.ACTION_ID)),

                        requestFields(optionalRequestFieldWithPath("id").description(DdiApiModelProperties.FEEDBACK_ACTION_ID),
                                requestFieldWithPath("status").description(DdiApiModelProperties.TARGET_STATUS),
                                requestFieldWithPath("status.execution")
                                        .description(DdiApiModelProperties.TARGET_EXEC_STATUS).type("enum")
                                        .attributes(key("value").value(
                                                "['closed', 'proceeding', 'download', 'downloaded', 'canceled','scheduled', 'rejected', 'resumed']")),
                                requestFieldWithPath("status.result")
                                        .description(DdiApiModelProperties.TARGET_RESULT_VALUE),
                                requestFieldWithPath("status.result.finished")
                                        .description(DdiApiModelProperties.TARGET_RESULT_FINISHED).type("enum")
                                        .attributes(key("value").value("['success', 'failure', 'none']")),
                                optionalRequestFieldWithPath("status.result.progress")
                                        .description(DdiApiModelProperties.TARGET_RESULT_PROGRESS),
                                optionalRequestFieldWithPath("status.details")
                                        .description(DdiApiModelProperties.TARGET_RESULT_DETAILS))));
    }

    @Test
    @Description("Returns all artifacts whichs is assigned to the software module."
            + "Can be usesfull for the target to double check that its current state matches with the targeted state.")
    @WithUser(authorities = "ROLE_CONTROLLER", allSpPermissions = true)
    public void getSoftwareModulesArtifacts() throws Exception {
        final DistributionSet set = testdataFactory.createDistributionSet("");

        final SoftwareModule module = (SoftwareModule) set.getModules().toArray()[0];

        final byte random[] = RandomStringUtils.random(5).getBytes();
        artifactManagement
                .create(new ArtifactUpload(new ByteArrayInputStream(random), module.getId(), "binaryFile", false, 0));

        final Target target = targetManagement.create(entityFactory.target().create().controllerId(CONTROLLER_ID));
        assignDistributionSet(set.getId(), target.getControllerId());

        mockMvc.perform(
                get(DdiRestConstants.BASE_V1_REQUEST_MAPPING + "/{controllerId}/softwaremodules/{moduleId}/artifacts",
                        tenantAware.getCurrentTenant(), target.getControllerId(), module.getId())
                                .accept(MediaTypes.HAL_JSON_VALUE))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON))
                .andDo(this.document.document(
                        pathParameters(parameterWithName("tenant").description(ApiModelPropertiesGeneric.TENANT),
                                parameterWithName("controllerId").description(DdiApiModelProperties.CONTROLLER_ID),
                                parameterWithName("moduleId").description(DdiApiModelProperties.SOFTWARE_MODUL_ID)),
                        responseFields(fieldWithPath("[]filename").description(DdiApiModelProperties.ARTIFACTS),
                                fieldWithPath("[]hashes").description(DdiApiModelProperties.ARTIFACTS),
                                fieldWithPath("[]hashes.sha1").description(DdiApiModelProperties.ARTIFACT_HASHES_SHA1),
                                fieldWithPath("[]hashes.md5").description(DdiApiModelProperties.ARTIFACT_HASHES_MD5),
                                fieldWithPath("[]hashes.sha256")
                                        .description(DdiApiModelProperties.ARTIFACT_HASHES_SHA256),
                                fieldWithPath("[]size").description(DdiApiModelProperties.ARTIFACT_SIZE),
                                fieldWithPath("[]_links.download")
                                        .description(DdiApiModelProperties.ARTIFACT_HTTPS_DOWNLOAD_LINK_BY_CONTROLLER),
                                fieldWithPath("[]_links.md5sum")
                                        .description(DdiApiModelProperties.ARTIFACT_HTTPS_HASHES_MD5SUM_LINK),
                                fieldWithPath("[]_links.download-http")
                                        .description(DdiApiModelProperties.ARTIFACT_HTTP_DOWNLOAD_LINK_BY_CONTROLLER),
                                fieldWithPath("[]_links.md5sum-http")
                                        .description(DdiApiModelProperties.ARTIFACT_HTTP_HASHES_MD5SUM_LINK))));
    }

}
