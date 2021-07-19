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
 * Interface for getting view name
 *
 */
@FunctionalInterface
public interface ViewNameAware {

    /**
     * Provides the name of a view
     *
     * @return view name
     */
    String getViewName();
}
