/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.model;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * The {@link Target} is the target of all provisioning operations. It contains
 * the currently installed {@link DistributionSet} (i.e. current state). In
 * addition it holds the target {@link DistributionSet} that has to be
 * provisioned next (i.e. target state).
 * </p>
 */
public interface Target extends NamedEntity {
    /**
     * Maximum length of controllerId.
     */
    int CONTROLLER_ID_MAX_SIZE = 256;

    /**
     * Maximum length of securityToken.
     */
    int SECURITY_TOKEN_MAX_SIZE = 128;

    /**
     * Maximum length of address.
     */
    int ADDRESS_MAX_SIZE = 512;

    /**
     * Maximum length of key of controller attribute
     */
    int CONTROLLER_ATTRIBUTE_KEY_SIZE = 128;

    /**
     * Maximum length of value of controller attribute
     */
    int CONTROLLER_ATTRIBUTE_VALUE_SIZE = 128;

    /**
     * @return business identifier of the {@link Target}
     */
    String getControllerId();

    /**
     * @return the securityToken
     */
    String getSecurityToken();

    /**
     * @return {@link TargetWithActionType} with default settings
     */
    default TargetWithActionType getTargetWithActionType() {
        return new TargetWithActionType(getControllerId());
    }

    /**
     * @return the address under which the target can be reached
     */
    URI getAddress();

    /**
     * @return time in {@link TimeUnit#MILLISECONDS} GMT when the {@link Target}
     *         polled the server the last time or <code>null</code> if target
     *         has never queried yet.
     */
    Long getLastTargetQuery();

    /**
     * @return time in {@link TimeUnit#MILLISECONDS} GMT when
     *         {@link #getInstalledDistributionSet()} was applied.
     */
    Long getInstallationDate();

    /**
     * @return current status of the {@link Target}.
     */
    TargetUpdateStatus getUpdateStatus();

    /**
     * @return the poll time which holds the last poll time of the target, the
     *         next poll time and the overdue time. In case the
     *         {@link #lastTargetQuery} is not set e.g. the target never polled
     *         before this method returns {@code null}
     */
    PollStatus getPollStatus();

    /**
     * @return <code>true</code> if the {@link Target} has not jet provided
     *         {@link #getControllerAttributes()}.
     */
    boolean isRequestControllerAttributes();

    Set<TargetTag> getTags();

    Map<String, String> getControllerAttributes();

    DistributionSet getInstalledDistributionSet();

    DistributionSet getAssignedDistributionSet();

    List<TargetMetadata> getMetadata();

}
