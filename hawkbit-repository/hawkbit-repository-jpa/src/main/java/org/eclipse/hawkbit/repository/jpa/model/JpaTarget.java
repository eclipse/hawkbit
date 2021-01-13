/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.model;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.repository.event.remote.TargetDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.jpa.model.helper.SecurityChecker;
import org.eclipse.hawkbit.repository.jpa.model.helper.SecurityTokenGeneratorHolder;
import org.eclipse.hawkbit.repository.jpa.model.helper.SystemSecurityContextHolder;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.PollStatus;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetMetadata;
import org.eclipse.hawkbit.repository.model.TargetTag;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.model.helper.EventPublisherHolder;
import org.eclipse.hawkbit.repository.model.helper.TenantConfigurationManagementHolder;
import org.eclipse.hawkbit.tenancy.configuration.DurationHelper;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.persistence.annotations.CascadeOnDelete;
import org.eclipse.persistence.annotations.ConversionValue;
import org.eclipse.persistence.annotations.Convert;
import org.eclipse.persistence.annotations.ObjectTypeConverter;
import org.eclipse.persistence.descriptors.DescriptorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA implementation of {@link Target}.
 *
 */
@Entity
@Table(name = "sp_target", indexes = {
        @Index(name = "sp_idx_target_01", columnList = "tenant,name,assigned_distribution_set"),
        @Index(name = "sp_idx_target_03", columnList = "tenant,controller_id,assigned_distribution_set"),
        @Index(name = "sp_idx_target_04", columnList = "tenant,created_at"),
        @Index(name = "sp_idx_target_05", columnList = "tenant,last_modified_at"),
        @Index(name = "sp_idx_target_prim", columnList = "tenant,id") }, uniqueConstraints = @UniqueConstraint(columnNames = {
                "controller_id", "tenant" }, name = "uk_tenant_controller_id"))
// exception squid:S2160 - BaseEntity equals/hashcode is handling correctly for
// sub entities
@SuppressWarnings("squid:S2160")
public class JpaTarget extends AbstractJpaNamedEntity implements Target, EventAwareEntity {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(JpaTarget.class);

    private static final List<String> TARGET_UPDATE_EVENT_IGNORE_FIELDS = Arrays.asList("lastTargetQuery", "address",
            "optLockRevision", "lastModifiedAt", "lastModifiedBy");

    @Column(name = "controller_id", length = Target.CONTROLLER_ID_MAX_SIZE, updatable = false, nullable = false)
    @Size(min = 1, max = Target.CONTROLLER_ID_MAX_SIZE)
    @NotNull
    @Pattern(regexp = "[.\\S]*", message = "has whitespaces which are not allowed")
    private String controllerId;

    @CascadeOnDelete
    @ManyToMany(targetEntity = JpaTargetTag.class)
    @JoinTable(name = "sp_target_target_tag", joinColumns = {
            @JoinColumn(name = "target", nullable = false, updatable = false, foreignKey = @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_targ_targtag_target")) }, inverseJoinColumns = {
                    @JoinColumn(name = "tag", nullable = false, updatable = false, foreignKey = @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_targ_targtag_tag")) })
    private Set<TargetTag> tags;

    @CascadeOnDelete
    @OneToMany(mappedBy = "target", fetch = FetchType.LAZY, targetEntity = JpaAction.class)
    private List<JpaAction> actions;

    /**
     * the security token of the target which allows if enabled to authenticate
     * with this security token.
     */
    @Column(name = "sec_token", updatable = true, nullable = false, length = Target.SECURITY_TOKEN_MAX_SIZE)
    @Size(min = 1, max = Target.SECURITY_TOKEN_MAX_SIZE)
    @NotNull
    private String securityToken;

    @CascadeOnDelete
    @OneToMany(mappedBy = "target", fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST })
    private List<RolloutTargetGroup> rolloutTargetGroup;

    @Column(name = "address", length = Target.ADDRESS_MAX_SIZE)
    @Size(max = Target.ADDRESS_MAX_SIZE)
    private String address;

    @Column(name = "last_target_query")
    private Long lastTargetQuery;

    @Column(name = "install_date")
    private Long installationDate;

    @Column(name = "update_status", nullable = false)
    @ObjectTypeConverter(name = "updateStatus", objectType = TargetUpdateStatus.class, dataType = Integer.class, conversionValues = {
            @ConversionValue(objectValue = "UNKNOWN", dataValue = "0"),
            @ConversionValue(objectValue = "IN_SYNC", dataValue = "1"),
            @ConversionValue(objectValue = "PENDING", dataValue = "2"),
            @ConversionValue(objectValue = "ERROR", dataValue = "3"),
            @ConversionValue(objectValue = "REGISTERED", dataValue = "4") })
    @Convert("updateStatus")
    @NotNull
    private TargetUpdateStatus updateStatus = TargetUpdateStatus.UNKNOWN;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "installed_distribution_set", nullable = true, foreignKey = @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_target_inst_ds"))
    private JpaDistributionSet installedDistributionSet;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_distribution_set", nullable = true, foreignKey = @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_target_assign_ds"))
    private JpaDistributionSet assignedDistributionSet;

    /**
     * Read only on management API. Are committed by controller.
     */
    @CascadeOnDelete
    @ElementCollection
    @Column(name = "attribute_value", length = Target.CONTROLLER_ATTRIBUTE_VALUE_SIZE)
    @MapKeyColumn(name = "attribute_key", nullable = false, length = Target.CONTROLLER_ATTRIBUTE_KEY_SIZE)
    @CollectionTable(name = "sp_target_attributes", joinColumns = {
            @JoinColumn(name = "target_id", nullable = false, updatable = false) }, foreignKey = @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_targ_attrib_target"))
    private Map<String, String> controllerAttributes;

    // set default request controller attributes to true, because we want to
    // request them the first
    // time
    @Column(name = "request_controller_attributes", nullable = false)
    private boolean requestControllerAttributes = true;

    @CascadeOnDelete
    @OneToMany(mappedBy = "target", fetch = FetchType.LAZY, targetEntity = JpaTargetMetadata.class)
    private List<TargetMetadata> metadata;

    /**
     * Constructor.
     *
     * @param controllerId
     *            controller ID of the {@link Target}
     */
    public JpaTarget(final String controllerId) {
        this(controllerId, SecurityTokenGeneratorHolder.getInstance().generateToken());
    }

    /**
     * Constructor.
     *
     * @param controllerId
     *            controller ID of the {@link Target}
     * @param securityToken
     *            for target authentication if enabled
     */
    public JpaTarget(final String controllerId, final String securityToken) {
        this.controllerId = controllerId;
        setName(truncateControllerIdToMaxNameLength(controllerId));
        this.securityToken = securityToken;
    }

    private static String truncateControllerIdToMaxNameLength(final String controllerId) {
        return controllerId != null && controllerId.length() > NAME_MAX_SIZE ? controllerId.substring(0, NAME_MAX_SIZE)
                : controllerId;
    }

    JpaTarget() {
        // empty constructor for JPA.
    }

    @Override
    public DistributionSet getAssignedDistributionSet() {
        return assignedDistributionSet;
    }

    @Override
    public String getControllerId() {
        return controllerId;
    }

    @Override
    public Set<TargetTag> getTags() {
        if (tags == null) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(tags);
    }

    public List<RolloutTargetGroup> getRolloutTargetGroup() {
        if (rolloutTargetGroup == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(rolloutTargetGroup);
    }

    public boolean addTag(final TargetTag tag) {
        if (tags == null) {
            tags = new HashSet<>();
        }

        return tags.add(tag);
    }

    public boolean removeTag(final TargetTag tag) {
        if (tags == null) {
            return false;
        }

        return tags.remove(tag);
    }

    public void setAssignedDistributionSet(final DistributionSet assignedDistributionSet) {
        this.assignedDistributionSet = (JpaDistributionSet) assignedDistributionSet;
    }

    public void setControllerId(final String controllerId) {
        this.controllerId = controllerId;
    }

    public List<Action> getActions() {
        if (actions == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(actions);
    }

    public boolean addAction(final Action action) {
        if (actions == null) {
            actions = new ArrayList<>();
        }

        return actions.add((JpaAction) action);
    }

    /**
     * @return the securityToken if the current security context contains the
     *         necessary permission {@link SpPermission#READ_TARGET_SEC_TOKEN}
     *         or the current context is executed as system code, otherwise
     *         {@code null}.
     */
    @Override
    public String getSecurityToken() {
        if (SystemSecurityContextHolder.getInstance().getSystemSecurityContext().isCurrentThreadSystemCode()
                || SecurityChecker.hasPermission(SpPermission.READ_TARGET_SEC_TOKEN)) {
            return securityToken;
        }
        return null;
    }

    public void setSecurityToken(final String securityToken) {
        this.securityToken = securityToken;
    }

    /**
     * @return the ipAddress
     */
    @Override
    public URI getAddress() {
        if (address == null) {
            return null;
        }
        try {
            return URI.create(address);
        } catch (final IllegalArgumentException e) {
            LOG.warn("Invalid address provided. Cloud not be configured to URI", e);
            return null;
        }
    }

    /**
     * @return the poll time which holds the last poll time of the target, the
     *         next poll time and the overdue time. In case the
     *         {@link #lastTargetQuery} is not set e.g. the target never polled
     *         before this method returns {@code null}
     */
    @Override
    public PollStatus getPollStatus() {
        if (lastTargetQuery == null) {
            return null;
        }
        return SystemSecurityContextHolder.getInstance().getSystemSecurityContext().runAsSystem(() -> {
            final Duration pollTime = DurationHelper.formattedStringToDuration(TenantConfigurationManagementHolder
                    .getInstance().getTenantConfigurationManagement()
                    .getConfigurationValue(TenantConfigurationKey.POLLING_TIME_INTERVAL, String.class).getValue());
            final Duration overdueTime = DurationHelper.formattedStringToDuration(
                    TenantConfigurationManagementHolder.getInstance().getTenantConfigurationManagement()
                            .getConfigurationValue(TenantConfigurationKey.POLLING_OVERDUE_TIME_INTERVAL, String.class)
                            .getValue());
            final LocalDateTime currentDate = LocalDateTime.now();
            final LocalDateTime lastPollDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastTargetQuery),
                    ZoneId.systemDefault());
            final LocalDateTime nextPollDate = lastPollDate.plus(pollTime);
            final LocalDateTime overdueDate = nextPollDate.plus(overdueTime);
            return new PollStatus(lastPollDate, nextPollDate, overdueDate, currentDate);
        });
    }

    @Override
    public Long getLastTargetQuery() {
        return lastTargetQuery;
    }

    @Override
    public Long getInstallationDate() {
        return installationDate;
    }

    @Override
    public TargetUpdateStatus getUpdateStatus() {
        return updateStatus;
    }

    @Override
    public DistributionSet getInstalledDistributionSet() {
        return installedDistributionSet;
    }

    @Override
    public Map<String, String> getControllerAttributes() {
        return controllerAttributes;
    }

    @Override
    public boolean isRequestControllerAttributes() {
        return requestControllerAttributes;
    }

    @Override
    public List<TargetMetadata> getMetadata() {
        if (metadata == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(metadata);
    }

    @Override
    public String toString() {
        return "JpaTarget [controllerId=" + controllerId + ", revision=" + getOptLockRevision() + ", id=" + getId()
                + "]";
    }

    public void setAddress(final String address) {
        this.address = address;
    }

    public void setLastTargetQuery(final Long lastTargetQuery) {
        this.lastTargetQuery = lastTargetQuery;
    }

    public void setInstallationDate(final Long installationDate) {
        this.installationDate = installationDate;
    }

    public void setInstalledDistributionSet(final JpaDistributionSet installedDistributionSet) {
        this.installedDistributionSet = installedDistributionSet;
    }

    public void setUpdateStatus(final TargetUpdateStatus updateStatus) {
        this.updateStatus = updateStatus;
    }

    public void setRequestControllerAttributes(final boolean requestControllerAttributes) {
        this.requestControllerAttributes = requestControllerAttributes;
    }

    @Override
    public void fireCreateEvent(final DescriptorEvent descriptorEvent) {
        EventPublisherHolder.getInstance().getEventPublisher()
                .publishEvent(new TargetCreatedEvent(this, EventPublisherHolder.getInstance().getApplicationId()));
    }

    @Override
    public List<String> getUpdateIgnoreFields() {
        return TARGET_UPDATE_EVENT_IGNORE_FIELDS;
    }

    @Override
    public void fireUpdateEvent(final DescriptorEvent descriptorEvent) {
        EventPublisherHolder.getInstance().getEventPublisher()
                .publishEvent(new TargetUpdatedEvent(this, EventPublisherHolder.getInstance().getApplicationId()));
    }

    @Override
    public void fireDeleteEvent(final DescriptorEvent descriptorEvent) {
        EventPublisherHolder.getInstance().getEventPublisher()
                .publishEvent(new TargetDeletedEvent(getTenant(), getId(), getControllerId(), address,
                        getClass().getName(), EventPublisherHolder.getInstance().getApplicationId()));
    }
}
