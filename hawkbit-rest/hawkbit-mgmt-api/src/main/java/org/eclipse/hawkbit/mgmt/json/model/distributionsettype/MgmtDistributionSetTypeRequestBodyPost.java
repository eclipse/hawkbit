/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.mgmt.json.model.distributionsettype;

import java.util.List;

import org.eclipse.hawkbit.mgmt.json.model.softwaremoduletype.MgmtSoftwareModuleTypeAssigment;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request Body for DistributionSetType POST.
 *
 */
public class MgmtDistributionSetTypeRequestBodyPost extends MgmtDistributionSetTypeRequestBodyPut {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty(required = true)
    private String key;

    @JsonProperty
    private List<MgmtSoftwareModuleTypeAssigment> mandatorymodules;

    @JsonProperty
    private List<MgmtSoftwareModuleTypeAssigment> optionalmodules;

    @Override
    public MgmtDistributionSetTypeRequestBodyPost setDescription(final String description) {
        super.setDescription(description);
        return this;
    }

    @Override
    public MgmtDistributionSetTypeRequestBodyPost setColour(final String colour) {
        super.setColour(colour);
        return this;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     *
     * @return updated body
     */
    public MgmtDistributionSetTypeRequestBodyPost setName(final String name) {
        this.name = name;
        return this;
    }

    /**
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * @param key
     *            the key to set
     *
     * @return updated body
     */
    public MgmtDistributionSetTypeRequestBodyPost setKey(final String key) {
        this.key = key;
        return this;
    }

    /**
     * @return the mandatory modules
     */
    public List<MgmtSoftwareModuleTypeAssigment> getMandatorymodules() {
        return mandatorymodules;
    }

    /**
     * @param mandatorymodules
     *            the mandatory modules to set
     *
     * @return updated body
     */
    public MgmtDistributionSetTypeRequestBodyPost setMandatorymodules(
            final List<MgmtSoftwareModuleTypeAssigment> mandatorymodules) {
        this.mandatorymodules = mandatorymodules;
        return this;
    }

    /**
     * @return the optional modules
     */
    public List<MgmtSoftwareModuleTypeAssigment> getOptionalmodules() {
        return optionalmodules;
    }

    /**
     * @param optionalmodules
     *            the optional modules to set
     *
     * @return updated body
     */
    public MgmtDistributionSetTypeRequestBodyPost setOptionalmodules(
            final List<MgmtSoftwareModuleTypeAssigment> optionalmodules) {
        this.optionalmodules = optionalmodules;
        return this;
    }

}
