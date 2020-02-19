/**
 * Copyright (c) 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.rest.mgmt.documentation;

import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.mgmt.json.model.system.MgmtSystemTenantConfigurationValueRequest;
import org.eclipse.hawkbit.mgmt.rest.api.MgmtRestConstants;
import org.eclipse.hawkbit.repository.model.TenantConfiguration;
import org.eclipse.hawkbit.rest.documentation.AbstractApiRestDocumentation;
import org.eclipse.hawkbit.rest.documentation.ApiModelPropertiesGeneric;
import org.eclipse.hawkbit.rest.documentation.MgmtApiModelProperties;
import org.eclipse.hawkbit.rest.util.MockMvcResultPrinter;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Documentation generation for System API for {@link TenantConfiguration}.
 */
@Feature("Spring Rest Docs Tests - TenantConfiguration")
@Story("TenantConfiguration Resource")
public class TenantResourceDocumentationTest extends AbstractApiRestDocumentation {

    protected static final Map<String, String> CONFIG_ITEM_DESCRIPTIONS = new HashMap<>();

    static {
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.AUTHENTICATION_MODE_GATEWAY_SECURITY_TOKEN_ENABLED,
                "if the authentication mode 'gateway security token' is enabled.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.AUTHENTICATION_MODE_GATEWAY_SECURITY_TOKEN_KEY,
                "the key of the gateway security token.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.AUTHENTICATION_MODE_HEADER_AUTHORITY_NAME,
                "the name of the 'authority header'.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.AUTHENTICATION_MODE_HEADER_ENABLED,
                "if the authentication mode 'authority header' is enabled.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.AUTHENTICATION_MODE_TARGET_SECURITY_TOKEN_ENABLED,
                "if the authentication mode 'target security token' is enabled.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.POLLING_OVERDUE_TIME_INTERVAL,
                "the period of time after the SP server will recognize a target, which is not performing pull requests anymore.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.POLLING_TIME_INTERVAL,
                "the time interval between two poll requests of a target.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.MIN_POLLING_TIME_INTERVAL,
                "the smallest time interval permitted between two poll requests of a target.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.MAINTENANCE_WINDOW_POLL_COUNT,
                "the polling interval so that controller tries to poll at least these many times between the last "
                        + "polling and before start of maintenance window. The polling interval is"
                        + " bounded by configured pollingTime and minPollingTime. The polling"
                        + " interval is modified as per following scheme: pollingTime(@time=t) ="
                        + " (maintenanceWindowStartTime - t)/maintenanceWindowPollCount.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.ANONYMOUS_DOWNLOAD_MODE_ENABLED,
                "if the anonymous download mode is enabled.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.REPOSITORY_ACTIONS_AUTOCLOSE_ENABLED,
                "if autoclose running actions with new Distribution Set assignment is enabled.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.ROLLOUT_APPROVAL_ENABLED,
                "if approval mode for Rollout Management is enabled.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.ACTION_CLEANUP_ENABLED,
                "if automatic cleanup of deployment actions is enabled.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.ACTION_CLEANUP_ACTION_STATUS,
                "the list of action status that should be taken into account for the cleanup.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.ACTION_CLEANUP_ACTION_EXPIRY,
                "the expiry time in milliseconds that needs to elapse before an action may be cleaned up.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED,
                "if multiple distribution sets can be assigned to the same targets.");
        CONFIG_ITEM_DESCRIPTIONS.put(TenantConfigurationKey.MULTI_ASSIGNMENTS_WEIGHT_DEFAULT,
                "tenant specific default weight for actions.");
    }

    @Autowired
    protected TenantConfigurationProperties tenantConfigurationProperties;

    @Override
    @Before
    public void setUp() {
        resourceName = "tenant";
        super.setUp();
    }

    @Test
    @Description("Handles GET request for receiving all tenant specific configurations. Required Permission: "
            + SpPermission.TENANT_CONFIGURATION)
    public void getTenantConfigrations() throws Exception {

        mockMvc.perform(get(MgmtRestConstants.SYSTEM_V1_REQUEST_MAPPING + "/configs/")).andExpect(status().isOk())
                .andDo(MockMvcResultPrinter.print())
                .andDo(this.document.document(responseFields(getTenantConfigurationValuesKeyResponseFields())));
    }

    @Test
    @Description("Handles GET request for receiving a tenant specific configuration. Required Permission: "
            + SpPermission.TENANT_CONFIGURATION)
    public void getTenantConfigration() throws Exception {
        mockMvc.perform(get(MgmtRestConstants.SYSTEM_V1_REQUEST_MAPPING + "/configs/{keyName}/",
                TenantConfigurationKey.AUTHENTICATION_MODE_GATEWAY_SECURITY_TOKEN_KEY)).andExpect(status().isOk())
                .andDo(MockMvcResultPrinter.print())
                .andDo(this.document.document(
                        pathParameters(parameterWithName("keyName").description(MgmtApiModelProperties.CONFIG_PARAM)),
                        responseFields(getTenantConfigurationValueResponseField())));
    }

    @Test
    @Description("Handles PUT request for settings values in tenant specific configuration. Required Permission: "
            + SpPermission.TENANT_CONFIGURATION)
    public void putTenantConfigration() throws Exception {

        final MgmtSystemTenantConfigurationValueRequest bodyPut = new MgmtSystemTenantConfigurationValueRequest();
        bodyPut.setValue("exampleToken");
        final ObjectMapper mapper = new ObjectMapper();
        final String json = mapper.writeValueAsString(bodyPut);

        mockMvc.perform(put(MgmtRestConstants.SYSTEM_V1_REQUEST_MAPPING + "/configs/{keyName}/",
                TenantConfigurationKey.AUTHENTICATION_MODE_GATEWAY_SECURITY_TOKEN_KEY).content(json)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andDo(MockMvcResultPrinter.print())
                .andDo(this.document.document(
                        pathParameters(parameterWithName("keyName").description(MgmtApiModelProperties.CONFIG_PARAM)),
                        requestFields(requestFieldWithPath("value").description(MgmtApiModelProperties.CONFIG_VALUE)),
                        responseFields(getTenantConfigurationValueResponseField())));
    }

    @Test
    @Description("Handles DELETE request deleting a tenant specific configuration. Required Permission: "
            + SpPermission.TENANT_CONFIGURATION)
    public void deleteTenantConfigration() throws Exception {
        mockMvc.perform(delete(MgmtRestConstants.SYSTEM_V1_REQUEST_MAPPING + "/configs/{keyName}/",
                TenantConfigurationKey.AUTHENTICATION_MODE_GATEWAY_SECURITY_TOKEN_KEY)).andExpect(status().isOk())
                .andDo(MockMvcResultPrinter.print()).andDo(this.document.document(
                        pathParameters(parameterWithName("keyName").description(MgmtApiModelProperties.CONFIG_PARAM))));
    }

    private FieldDescriptor[] getTenantConfigurationValuesKeyResponseFields() {
        final List<FieldDescriptor> fields = new ArrayList<>();
        for (final TenantConfigurationKey key : tenantConfigurationProperties.getConfigurationKeys()) {
            fields.add(fieldWithPath("['" + key.getKeyName() + "']").type(key.getDataType().getSimpleName())
                    .description(getTenantConfigurationKeyDescription(key)));
        }
        return fields.toArray(new FieldDescriptor[fields.size()]);
    }

    private FieldDescriptor[] getTenantConfigurationValueResponseField() {
        return new FieldDescriptor[] { fieldWithPath("value").description(MgmtApiModelProperties.CONFIG_VALUE),
                fieldWithPath("global").description(MgmtApiModelProperties.CONFIG_GLOBAL),
                fieldWithPath("createdBy").description(ApiModelPropertiesGeneric.CREATED_BY).type("Number").optional(),
                fieldWithPath("createdAt").description(ApiModelPropertiesGeneric.CREATED_AT).type("String").optional(),
                fieldWithPath("lastModifiedAt").description(ApiModelPropertiesGeneric.LAST_MODIFIED_AT).type("Number")
                        .optional(),
                fieldWithPath("lastModifiedBy").description(ApiModelPropertiesGeneric.LAST_MODIFIED_BY).type("String")
                        .optional(),
                fieldWithPath("_links.self").ignored() };
    }

    private String getTenantConfigurationKeyDescription(final TenantConfigurationKey key) {
        if (!CONFIG_ITEM_DESCRIPTIONS.containsKey(key.getKeyName())) {
            throw new IllegalArgumentException("Description for key " + key.getKeyName() + " is missing.");
        }

        return "The configuration key '" + key.getKeyName() + "' defines "
                + CONFIG_ITEM_DESCRIPTIONS.get(key.getKeyName());
    }

}
