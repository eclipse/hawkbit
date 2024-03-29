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

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.eclipse.hawkbit.repository.event.remote.TargetTagDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetTagCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetTagUpdatedEvent;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetTag;
import org.eclipse.hawkbit.repository.model.helper.EventPublisherHolder;
import org.eclipse.persistence.annotations.CascadeOnDelete;
import org.eclipse.persistence.descriptors.DescriptorEvent;

/**
 * A {@link TargetTag} is used to describe Target attributes and use them also
 * for filtering the target list.
 *
 */
@Entity
@Table(name = "sp_target_tag", indexes = { @Index(name = "sp_idx_target_tag_prim", columnList = "tenant,id"),
        @Index(name = "sp_idx_target_tag_01", columnList = "tenant,name") }, uniqueConstraints = @UniqueConstraint(columnNames = {
                "name", "tenant" }, name = "uk_targ_tag"))
public class JpaTargetTag extends JpaTag implements TargetTag, EventAwareEntity {
    private static final long serialVersionUID = 1L;

    @CascadeOnDelete
    @ManyToMany(mappedBy = "tags", targetEntity = JpaTarget.class, fetch = FetchType.LAZY)
    private List<Target> assignedToTargets;

    /**
     * Constructor.
     *
     * @param name
     *            of {@link TargetTag}
     * @param description
     *            of {@link TargetTag}
     * @param colour
     *            of {@link TargetTag}
     */
    public JpaTargetTag(final String name, final String description, final String colour) {
        super(name, description, colour);
    }

    public JpaTargetTag() {
        // Default constructor for JPA.
    }

    public List<Target> getAssignedToTargets() {
        if (assignedToTargets == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(assignedToTargets);
    }

    @Override
    public void fireCreateEvent(final DescriptorEvent descriptorEvent) {
        EventPublisherHolder.getInstance().getEventPublisher()
                .publishEvent(new TargetTagCreatedEvent(this, EventPublisherHolder.getInstance().getApplicationId()));

    }

    @Override
    public void fireUpdateEvent(final DescriptorEvent descriptorEvent) {
        EventPublisherHolder.getInstance().getEventPublisher()
                .publishEvent(new TargetTagUpdatedEvent(this, EventPublisherHolder.getInstance().getApplicationId()));
    }

    @Override
    public void fireDeleteEvent(final DescriptorEvent descriptorEvent) {
        EventPublisherHolder.getInstance().getEventPublisher().publishEvent(new TargetTagDeletedEvent(getTenant(),
                getId(), getClass(), EventPublisherHolder.getInstance().getApplicationId()));

    }

}
