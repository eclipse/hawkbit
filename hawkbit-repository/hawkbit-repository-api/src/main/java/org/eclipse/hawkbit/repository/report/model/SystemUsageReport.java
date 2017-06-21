/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.report.model;

/**
 * Bean for holding the system usage stats.
 *
 */
public class SystemUsageReport {
    private final long overallTargets;
    private final long overallArtifacts;
    private final long overallArtifactVolumeInBytes;
    private final long overallActions;

    /**
     * Constructor.
     *
     * @param overallTargets
     *            of the system
     * @param overallArtifacts
     *            of the system
     * @param overallActions
     *            of the system
     * @param overallArtifactVolumeInBytes
     *            of the system
     */
    public SystemUsageReport(final long overallTargets, final long overallArtifacts, final long overallActions,
            final long overallArtifactVolumeInBytes) {
        this.overallTargets = overallTargets;
        this.overallArtifacts = overallArtifacts;
        this.overallActions = overallActions;

        this.overallArtifactVolumeInBytes = overallArtifactVolumeInBytes;
    }

    /**
     * @return overallTargets in the system
     */
    public long getOverallTargets() {
        return overallTargets;
    }

    /**
     * @return overallArtifacts in the system
     */
    public long getOverallArtifacts() {
        return overallArtifacts;
    }

    /**
     * @return overallArtifactVolumeInBytes of the system
     */
    public long getOverallArtifactVolumeInBytes() {
        return overallArtifactVolumeInBytes;
    }

    /**
     * @return actions of system
     */
    public long getOverallActions() {
        return overallActions;
    }

}
