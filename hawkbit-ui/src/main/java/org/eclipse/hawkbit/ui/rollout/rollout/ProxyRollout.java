/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout.rollout;

import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus;
import org.eclipse.hawkbit.ui.customrenderers.client.renderers.RolloutRendererData;

import com.vaadin.server.FontAwesome;

/**
 * Proxy rollout with custom properties.
 *
 */
public class ProxyRollout {

    private String distributionSetNameVersion;

    private String createdDate;

    private String modifiedDate;

    private int numberOfGroups;

    private Boolean isActionRecieved = Boolean.FALSE;

    private String totalTargetsCount;

    private RolloutRendererData rolloutRendererData;

    private Long id;
    private String name;
    private String version;
    private String description;
    private Integer weight;
    private String createdBy;
    private String lastModifiedBy;
    private long forcedTime;
    private RolloutStatus status;
    private TotalTargetCountStatus totalTargetCountStatus;
    private String approvalDecidedBy;
    private String approvalRemark;
    private ActionType actionType;

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(final ActionType actionType) {
        this.actionType = actionType;
    }

    public RolloutRendererData getRolloutRendererData() {
        return rolloutRendererData;
    }

    public void setRolloutRendererData(final RolloutRendererData rendererData) {
        this.rolloutRendererData = rendererData;
    }

    /**
     * @return the distributionSetNameVersion
     */
    public String getDistributionSetNameVersion() {
        return distributionSetNameVersion;
    }

    /**
     * @param distributionSetNameVersion
     *            the distributionSetNameVersion to set
     */
    public void setDistributionSetNameVersion(final String distributionSetNameVersion) {
        this.distributionSetNameVersion = distributionSetNameVersion;
    }

    /**
     * @return the numberOfGroups
     */
    public int getNumberOfGroups() {
        return numberOfGroups;
    }

    /**
     * @param numberOfGroups
     *            the numberOfGroups to set
     */
    public void setNumberOfGroups(final int numberOfGroups) {
        this.numberOfGroups = numberOfGroups;
    }

    /**
     * @return the createdDate
     */
    public String getCreatedDate() {
        return createdDate;
    }

    /**
     * @param createdDate
     *            the createdDate to set
     */
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

    /**
     * @return the isActionRecieved
     */
    public Boolean getIsActionRecieved() {
        return isActionRecieved;
    }

    /**
     * @param isActionRecieved
     *            the isActionRecieved to set
     */
    public void setIsActionRecieved(final Boolean isActionRecieved) {
        this.isActionRecieved = isActionRecieved;
    }

    /**
     * @return the totalTargetsCount
     */
    public String getTotalTargetsCount() {
        return totalTargetsCount;
    }

    /**
     * @param totalTargetsCount
     *            the totalTargetsCount to set
     */
    public void setTotalTargetsCount(final String totalTargetsCount) {
        this.totalTargetsCount = totalTargetsCount;
    }

    public String getAction() {
        return FontAwesome.CIRCLE_O.getHtml();
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(final Integer weight) {
        this.weight = weight;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(final String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public long getForcedTime() {
        return forcedTime;
    }

    public void setForcedTime(final long forcedTime) {
        this.forcedTime = forcedTime;
    }

    public RolloutStatus getStatus() {
        return status;
    }

    public void setStatus(final RolloutStatus status) {
        this.status = status;
    }

    public TotalTargetCountStatus getTotalTargetCountStatus() {
        return totalTargetCountStatus;
    }

    public void setTotalTargetCountStatus(final TotalTargetCountStatus totalTargetCountStatus) {
        this.totalTargetCountStatus = totalTargetCountStatus;
    }

    public String getApprovalDecidedBy() {
        return approvalDecidedBy;
    }

    public void setApprovalDecidedBy(final String approvalDecidedBy) {
        this.approvalDecidedBy = approvalDecidedBy;
    }

    public String getApprovalRemark() {
        return approvalRemark;
    }

    public void setApprovalRemark(final String approvalRemark) {
        this.approvalRemark = approvalRemark;
    }
}
