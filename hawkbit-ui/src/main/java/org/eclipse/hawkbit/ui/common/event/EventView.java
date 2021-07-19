/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.event;

import org.eclipse.hawkbit.ui.artifacts.UploadArtifactView;
import org.eclipse.hawkbit.ui.distributions.DistributionsView;
import org.eclipse.hawkbit.ui.filtermanagement.FilterManagementView;
import org.eclipse.hawkbit.ui.management.DeploymentView;
import org.eclipse.hawkbit.ui.rollout.RolloutView;

/**
 * Enum constants for event view
 */
public enum EventView {
    DEPLOYMENT(DeploymentView.VIEW_NAME), ROLLOUT(RolloutView.VIEW_NAME), TARGET_FILTER(
            FilterManagementView.VIEW_NAME), DISTRIBUTIONS(
                    DistributionsView.VIEW_NAME), UPLOAD(UploadArtifactView.VIEW_NAME);

    private final String viewName;

    /**
     * Constructor.
     * 
     * @param viewName
     *            View name
     */
    EventView(final String viewName) {
        this.viewName = viewName;
    }

    /**
     * Get view name.
     * 
     * @return view name
     */
    public String getViewName() {
        return viewName;
    }

    /**
     * Matches event view with provided view name.
     * 
     * @param viewName
     *            View name to match
     * 
     * @return match
     */
    public boolean matchByViewName(final String viewName) {
        return this.viewName.equals(viewName);
    }
}
