/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.actionhistory;

import java.io.Serializable;

import org.eclipse.hawkbit.repository.model.Action;

/**
 * Proxy for {@link Action}
 */
public class ProxyAction implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String PXY_ACTION_STATUS = "status";
    public static final String PXY_ACTION_IS_ACTIVE = "isActive";
    public static final String PXY_ACTION_IS_ACTIVE_DECO = "isActiveDecoration";
    public static final String PXY_ACTION_ID = "id";
    public static final String PXY_ACTION_DS_NAME_VERSION = "dsNameVersion";
    public static final String PXY_ACTION = "action";
    public static final String PXY_ACTION_LAST_MODIFIED_AT = "lastModifiedAt";
    public static final String PXY_ACTION_ROLLOUT_NAME = "rolloutName";
    public static final String PXY_ACTION_MAINTENANCE_WINDOW = "maintenanceWindow";
    public static final String PXY_ACTION_WEIGHT = "weight";

    private Action.Status status;
    private boolean isActive;
    private IsActiveDecoration isActiveDecoration;
    private Long id;
    private String dsNameVersion;
    private Action action;
    private Long lastModifiedAt;
    private String rolloutName;
    private String maintenanceWindow;
    private Integer weight;

    /**
     * Get maintenance window.
     *
     * @return maintenance window
     */
    public String getMaintenanceWindow() {
        return maintenanceWindow;
    }

    /**
     * Set maintenance window.
     *
     * @param maintenanceWindow
     *            maintenance window.
     */
    public void setMaintenanceWindow(final String maintenanceWindow) {
        this.maintenanceWindow = maintenanceWindow;
    }

    /**
     * Get id for the entry.
     *
     * @return id for the entry.
     */
    public Long getId() {
        return id;
    }

    /**
     * Set the id for the entry.
     *
     * @param id
     *            of the action entry.
     */
    public void setId(final Long id) {
        this.id = id;
    }

    /**
     * Gets the status literal.
     *
     * @return status literal
     */
    public Action.Status getStatus() {
        return status;
    }

    /**
     * Sets the status literal.
     *
     * @param status
     *            literal
     */
    public void setStatus(final Action.Status status) {
        this.status = status;
    }

    /**
     * Flag that indicates if the action is active.
     *
     * @return <code>true</code> if the action is active, otherwise
     *         <code>false</code>
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Sets the flag that indicates if the action is active.
     *
     * @param isActive
     *            <code>true</code> if the action is active, otherwise
     *            <code>false</code>
     */
    public void setActive(final boolean isActive) {
        this.isActive = isActive;
    }

    /**
     * Gets a pre-calculated literal combining <code>ProxyAction#isActive</code>
     * and <code>ProxyAction#getStatus</code> states.
     *
     * @return pre-calculated literal
     */
    public IsActiveDecoration getIsActiveDecoration() {
        return isActiveDecoration;
    }

    /**
     * Sets the pre-calculated literal combining
     * <code>ProxyAction#isActive</code> and <code>ProxyAction#getStatus</code>
     * states.
     *
     * @param isActiveDecoration
     *            pre-calculated literal
     */
    public void setIsActiveDecoration(final IsActiveDecoration isActiveDecoration) {
        this.isActiveDecoration = isActiveDecoration;
    }

    /**
     * Pre-calculated value that is set up by distribution set name and version.
     *
     * @return pre-calculated value combining name and version
     */
    public String getDsNameVersion() {
        return dsNameVersion;
    }

    /**
     * Sets the pre-calculated value combining distribution set name and
     * version.
     *
     * @param dsNameVersion
     *            combined value
     */
    public void setDsNameVersion(final String dsNameVersion) {
        this.dsNameVersion = dsNameVersion;
    }

    /**
     * Gets the action to be evaluated by generators of virtual properties.
     *
     * @return action
     */
    public Action getAction() {
        return action;
    }

    /**
     * Sets the action to be evaluated by generators of virtual properties.
     *
     * @param action
     */
    public void setAction(final Action action) {
        this.action = action;
    }

    /**
     * Get raw long-value for lastModifiedAt-date.
     *
     * @return raw long-value for lastModifiedAt-date
     */
    public Long getLastModifiedAt() {
        return lastModifiedAt;
    }

    /**
     * Set raw long-value for lastModifiedAt-date.
     *
     * @param lastModifiedAt
     *            raw long-value for lastModifiedAt-date
     */
    public void setLastModifiedAt(final Long lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    /**
     * Gets the rolloutName.
     *
     * @return rolloutName
     */
    public String getRolloutName() {
        return rolloutName;
    }

    /**
     * Sets the rolloutName.
     *
     * @param rolloutName
     */
    public void setRolloutName(final String rolloutName) {
        this.rolloutName = rolloutName;
    }

    /**
     * Gets the weight.
     *
     * @return weight
     */
    public Integer getWeight() {
        return weight;
    }

    /**
     * Sets the weight.
     *
     * @param weight
     */
    public void setWeight(final Integer weight) {
        this.weight = weight;
    }

    /**
     * Pre-calculated decoration value combining
     * <code>ProxyAction#isActive</code> and <code>ProxyAction#getStatus</code>
     * states.
     */
    public enum IsActiveDecoration {
        /**
         * Active label decoration type for {@code ProxyAction#isActive()==true}
         */
        ACTIVE,

        /**
         * Active label decoration type for
         * {@code ProxyAction#isActive()==false}
         */
        IN_ACTIVE,

        /**
         * Active label decoration type for {@code ProxyAction#isActive()==true}
         * AND {@code ProxyAction#getStatus()==Action.Status.ERROR}
         */
        IN_ACTIVE_ERROR,

        /**
         * {@code ProxyAction#getStatus()==Action.Status.SCHEDULED}
         */
        SCHEDULED,
    }
}
