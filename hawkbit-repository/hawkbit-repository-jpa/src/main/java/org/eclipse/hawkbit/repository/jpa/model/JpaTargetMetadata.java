/**
 * Copyright (c) 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.model;

import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetMetadata;

/**
 * Meta data for {@link Target}.
 *
 */
@IdClass(TargetMetadataCompositeKey.class)
@Entity
@Table(name = "sp_target_metadata")
public class JpaTargetMetadata extends AbstractJpaMetaData implements TargetMetadata {
    private static final long serialVersionUID = 1L;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false, updatable = false, foreignKey = @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_metadata_target"))
    private JpaTarget target;

    public JpaTargetMetadata() {
        // default public constructor for JPA
    }

    /**
     * Creates a single metadata entry with the given key and value.
     * 
     * @param key
     *            of the meta data entry
     * @param value
     *            of the meta data entry
     */
    public JpaTargetMetadata(final String key, final String value) {
        super(key, value);
    }

    /**
     * Creates a single metadata entry with the given key and value for the
     * given {@link Target}.
     * 
     * @param key
     *            of the meta data entry
     * @param value
     *            of the meta data entry
     * @param target
     *            the meta data entry is associated with
     */
    public JpaTargetMetadata(final String key, final String value, final Target target) {
        super(key, value);
        this.target = (JpaTarget) target;
    }

    public TargetMetadataCompositeKey getId() {
        return new TargetMetadataCompositeKey(target.getId(), getKey());
    }

    public void setTarget(final Target target) {
        this.target = (JpaTarget) target;
    }

    @Override
    public Target getTarget() {
        return target;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((target == null) ? 0 : target.hashCode());
        return result;
    }

    @Override
    // exception squid:S2259 - obj is checked for null in super
    @SuppressWarnings("squid:S2259")
    public boolean equals(final Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        final JpaTargetMetadata other = (JpaTargetMetadata) obj;
        if (target == null) {
            if (other.target != null) {
                return false;
            }
        } else if (!target.equals(other.target)) {
            return false;
        }
        return true;
    }
}
