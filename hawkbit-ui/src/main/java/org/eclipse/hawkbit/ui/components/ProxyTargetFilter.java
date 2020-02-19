/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.components;

import org.eclipse.hawkbit.repository.model.Action.ActionType;

/**
 * 
 *
 *
 */
public class ProxyTargetFilter {

    private static final long serialVersionUID = 1L;

    private String createdDate;

    private String modifiedDate;

    private String name;
    private Long id;
    private String createdBy;
    private String lastModifiedBy;
    private String query;
    private ProxyDistribution autoAssignDistributionSet;
    private ActionType autoAssignActionType;
    private Integer autoAssignWeight;

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final String createdDate) {
        this.createdDate = createdDate;
    }

    /**
     * @return the modifiedDate
     */
    public String getModifiedDate() {
        return modifiedDate;
    }

    /**
     * @param modifiedDate
     *            the modifiedDate to set
     */
    public void setModifiedDate(final String modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(final String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public ProxyDistribution getAutoAssignDistributionSet() {
        return autoAssignDistributionSet;
    }

    public void setAutoAssignDistributionSet(final ProxyDistribution autoAssignDistributionSet) {
        this.autoAssignDistributionSet = autoAssignDistributionSet;
    }

    public ActionType getAutoAssignActionType() {
        return autoAssignActionType;
    }

    public void setAutoAssignActionType(final ActionType autoAssignActionType) {
        this.autoAssignActionType = autoAssignActionType;
    }

    public Integer getAutoAssignWeight() {
        return autoAssignWeight;
    }

    public void setAutoAssignWeight(final Integer autoAssignWeight) {
        this.autoAssignWeight = autoAssignWeight;
    }
}
