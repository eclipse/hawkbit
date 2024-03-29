/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.dmf.json.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The json message action status.
 */
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public enum DmfActionStatus {

    /**
     * Action requests download by this target which has now started.
     */
    DOWNLOAD,
    /**
     * Action has been send to the target.
     */
    RETRIEVED,
    /**
     * Action is still running for this target.
     */
    RUNNING,
    /**
     * Action is finished successfully for this target.
     */
    FINISHED,
    /**
     * Action has failed for this target.
     */
    ERROR,
    /**
     * Action is still running but with warnings.
     */
    WARNING,
    /**
     * Action has been canceled for this target.
     */
    CANCELED,
    /**
     * Cancellation has been rejected by the target..
     */
    CANCEL_REJECTED,
    /**
     * Action has been downloaded for this target.
     */
    DOWNLOADED,
    /**
     * Action is confirmed by the target.
     */
    CONFIRMED,
    /**
     * Action has been denied by the target.
     */
    DENIED
}