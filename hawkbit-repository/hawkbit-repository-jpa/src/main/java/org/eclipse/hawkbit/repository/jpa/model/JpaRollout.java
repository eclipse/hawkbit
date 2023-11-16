/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository.jpa.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.eclipse.hawkbit.repository.event.remote.RolloutDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutUpdatedEvent;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus;
import org.eclipse.hawkbit.repository.model.helper.EventPublisherHolder;
import org.eclipse.persistence.annotations.CascadeOnDelete;
import org.eclipse.persistence.annotations.ConversionValue;
import org.eclipse.persistence.annotations.Convert;
import org.eclipse.persistence.annotations.ObjectTypeConverter;
import org.eclipse.persistence.descriptors.DescriptorEvent;
import org.eclipse.persistence.queries.UpdateObjectQuery;
import org.eclipse.persistence.sessions.changesets.DirectToFieldChangeRecord;
import org.eclipse.persistence.sessions.changesets.ObjectChangeSet;

/**
 * JPA implementation of a {@link Rollout}.
 *
 */
@Entity
@Table(name = "sp_rollout", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
        "tenant" }, name = "uk_rollout"))
// exception squid:S2160 - BaseEntity equals/hashcode is handling correctly for
// sub entities
@SuppressWarnings("squid:S2160")
public class JpaRollout extends AbstractJpaNamedEntity implements Rollout, EventAwareEntity {

    private static final long serialVersionUID = 1L;

    private static final String DELETED_PROPERTY = "deleted";

    @CascadeOnDelete
    @OneToMany(targetEntity = JpaRolloutGroup.class, fetch = FetchType.LAZY, mappedBy = "rollout")
    private List<JpaRolloutGroup> rolloutGroups;

    @Column(name = "target_filter", length = TargetFilterQuery.QUERY_MAX_SIZE, nullable = false)
    @Size(min = 1, max = TargetFilterQuery.QUERY_MAX_SIZE)
    @NotNull
    private String targetFilterQuery;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distribution_set", nullable = false, updatable = false, foreignKey = @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_rolltout_ds"))
    @NotNull
    private JpaDistributionSet distributionSet;

    @Column(name = "status", nullable = false)
    @ObjectTypeConverter(name = "rolloutstatus", objectType = Rollout.RolloutStatus.class, dataType = Integer.class, conversionValues = {
            @ConversionValue(objectValue = "CREATING", dataValue = "0"),
            @ConversionValue(objectValue = "READY", dataValue = "1"),
            @ConversionValue(objectValue = "PAUSED", dataValue = "2"),
            @ConversionValue(objectValue = "STARTING", dataValue = "3"),
            @ConversionValue(objectValue = "STOPPED", dataValue = "4"),
            @ConversionValue(objectValue = "RUNNING", dataValue = "5"),
            @ConversionValue(objectValue = "FINISHED", dataValue = "6"),
            @ConversionValue(objectValue = "ERROR_CREATING", dataValue = "7"),
            @ConversionValue(objectValue = "ERROR_STARTING", dataValue = "8"),
            @ConversionValue(objectValue = "DELETING", dataValue = "9"),
            @ConversionValue(objectValue = "DELETED", dataValue = "10"),
            @ConversionValue(objectValue = "WAITING_FOR_APPROVAL", dataValue = "11"),
            @ConversionValue(objectValue = "APPROVAL_DENIED", dataValue = "12"),
            @ConversionValue(objectValue = "STOPPING", dataValue = "13") })
    @Convert("rolloutstatus")
    @NotNull
    private RolloutStatus status = RolloutStatus.CREATING;
    @Column(name = "last_check")
    private long lastCheck;

    @Column(name = "action_type", nullable = false)
    @ObjectTypeConverter(name = "actionType", objectType = Action.ActionType.class, dataType = Integer.class, conversionValues = {
            @ConversionValue(objectValue = "FORCED", dataValue = "0"),
            @ConversionValue(objectValue = "SOFT", dataValue = "1"),
            @ConversionValue(objectValue = "TIMEFORCED", dataValue = "2"),
            @ConversionValue(objectValue = "DOWNLOAD_ONLY", dataValue = "3") })
    @Convert("actionType")
    @NotNull
    private ActionType actionType = ActionType.FORCED;

    @Column(name = "forced_time")
    private long forcedTime;

    @Column(name = "total_targets")
    private long totalTargets;

    @Column(name = "rollout_groups_created")
    private int rolloutGroupsCreated;

    @Column(name = "deleted")
    private boolean deleted;

    @Column(name = "start_at")
    private Long startAt;

    @Column(name = "approval_decided_by")
    @Size(min = 1, max = Rollout.APPROVED_BY_MAX_SIZE)
    private String approvalDecidedBy;

    @Column(name = "approval_remark")
    @Size(max = Rollout.APPROVAL_REMARK_MAX_SIZE)
    private String approvalRemark;

    @Column(name = "weight")
    @Min(Action.WEIGHT_MIN)
    @Max(Action.WEIGHT_MAX)
    private Integer weight;

    @Column(name = "retry_count")
    @Min(Action.WEIGHT_MIN)
    private Integer retryCount;

    @Transient
    private transient TotalTargetCountStatus totalTargetCountStatus;

    @Override
    public DistributionSet getDistributionSet() {
        return distributionSet;
    }

    public void setDistributionSet(final DistributionSet distributionSet) {
        this.distributionSet = (JpaDistributionSet) distributionSet;
    }

    public List<RolloutGroup> getRolloutGroups() {
        if (rolloutGroups == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(rolloutGroups);
    }

    @Override
    public String getTargetFilterQuery() {
        return targetFilterQuery;
    }

    public void setTargetFilterQuery(final String targetFilterQuery) {
        this.targetFilterQuery = targetFilterQuery;
    }

    @Override
    public RolloutStatus getStatus() {
        return status;
    }

    public void setStatus(final RolloutStatus status) {
        this.status = status;
    }

    public long getLastCheck() {
        return lastCheck;
    }

    public void setLastCheck(final long lastCheck) {
        this.lastCheck = lastCheck;
    }

    @Override
    public Long getStartAt() {
        return startAt;
    }

    public void setStartAt(final Long startAt) {
        this.startAt = startAt;
    }

    @Override
    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(final ActionType actionType) {
        this.actionType = actionType;
    }

    @Override
    public long getForcedTime() {
        return forcedTime;
    }

    public void setForcedTime(final long forcedTime) {
        this.forcedTime = forcedTime;
    }

    @Override
    public Optional<Integer> getWeight() {
        return Optional.ofNullable(weight);
    }

    @Override
    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(final Integer retryCount) {
        this.retryCount = retryCount;
    }

    public void setWeight(final Integer weight) {
        this.weight = weight;
    }

    @Override
    public long getTotalTargets() {
        return totalTargets;
    }

    public void setTotalTargets(final long totalTargets) {
        this.totalTargets = totalTargets;
    }

    @Override
    public int getRolloutGroupsCreated() {
        return rolloutGroupsCreated;
    }

    public void setRolloutGroupsCreated(final int rolloutGroupsCreated) {
        this.rolloutGroupsCreated = rolloutGroupsCreated;
    }

    @Override
    public TotalTargetCountStatus getTotalTargetCountStatus() {
        if (totalTargetCountStatus == null) {
            totalTargetCountStatus = new TotalTargetCountStatus(totalTargets, actionType);
        }
        return totalTargetCountStatus;
    }

    public void setTotalTargetCountStatus(final TotalTargetCountStatus totalTargetCountStatus) {
        this.totalTargetCountStatus = totalTargetCountStatus;
    }

    @Override
    public String toString() {
        return "Rollout [ targetFilterQuery=" + targetFilterQuery + ", distributionSet=" + distributionSet + ", status="
                + status + ", lastCheck=" + lastCheck + ", getName()=" + getName() + ", getId()=" + getId() + "]";
    }

    @Override
    public void fireCreateEvent(final DescriptorEvent descriptorEvent) {
        EventPublisherHolder.getInstance().getEventPublisher()
                .publishEvent(new RolloutCreatedEvent(this, EventPublisherHolder.getInstance().getApplicationId()));
    }

    @Override
    public void fireUpdateEvent(final DescriptorEvent descriptorEvent) {
        EventPublisherHolder.getInstance().getEventPublisher()
                .publishEvent(new RolloutUpdatedEvent(this, EventPublisherHolder.getInstance().getApplicationId()));

        if (isSoftDeleted(descriptorEvent)) {
            EventPublisherHolder.getInstance().getEventPublisher().publishEvent(new RolloutDeletedEvent(getTenant(),
                    getId(), getClass(), EventPublisherHolder.getInstance().getApplicationId()));
        }
    }

    private static boolean isSoftDeleted(final DescriptorEvent event) {
        final ObjectChangeSet changeSet = ((UpdateObjectQuery) event.getQuery()).getObjectChangeSet();
        final List<DirectToFieldChangeRecord> changes = changeSet.getChanges().stream()
                .filter(DirectToFieldChangeRecord.class::isInstance).map(DirectToFieldChangeRecord.class::cast)
                .collect(Collectors.toList());

        return changes.stream().anyMatch(changeRecord -> DELETED_PROPERTY.equals(changeRecord.getAttribute())
                && Boolean.parseBoolean(changeRecord.getNewValue().toString()));
    }

    @Override
    public void fireDeleteEvent(final DescriptorEvent descriptorEvent) {
        EventPublisherHolder.getInstance().getEventPublisher().publishEvent(new RolloutDeletedEvent(getTenant(),
                getId(), getClass(), EventPublisherHolder.getInstance().getApplicationId()));
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(final boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public String getApprovalDecidedBy() {
        return approvalDecidedBy;
    }

    public void setApprovalDecidedBy(final String approvalDecidedBy) {
        this.approvalDecidedBy = approvalDecidedBy;
    }

    @Override
    public String getApprovalRemark() {
        return approvalRemark;
    }

    public void setApprovalRemark(final String approvalRemark) {
        this.approvalRemark = approvalRemark;
    }
}
