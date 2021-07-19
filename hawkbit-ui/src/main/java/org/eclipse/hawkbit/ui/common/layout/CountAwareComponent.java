/** 
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.layout;

/**
 * Interface for count aware component.
 *
 */
public interface CountAwareComponent {

    /**
     * Adapts count on entities added.
     *
     * @param count
     *            added entities count
     */
    void updateCountOnEntitiesAdded(final int count);

    /**
     * Adapts count on entities updated.
     *
     */
    void updateCountOnEntitiesUpdated();

    /**
     * Adapts count on entities deleted.
     *
     * @param count
     *            deleted entities count
     */
    void updateCountOnEntitiesDeleted(final int count);
}
