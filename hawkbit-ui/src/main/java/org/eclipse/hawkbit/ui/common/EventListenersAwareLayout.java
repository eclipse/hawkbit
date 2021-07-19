/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common;

/**
 * Interface for event aware event listeners
 *
 */
public interface EventListenersAwareLayout {

    /**
     * Restore components state
     */
    default void restoreState() {
    }

    /**
     * Update components on view enter
     */
    default void onViewEnter() {
    }

    /**
     * Subscribe event listeners
     */
    default void subscribeListeners() {
    }

    /**
     * Unsubscribe event listeners
     */
    default void unsubscribeListeners() {
    }
}
