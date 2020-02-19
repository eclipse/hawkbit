/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.event;

/**
 *
 *
 *
 */
public enum ManagementUIEvent {
    //
    SHOW_COUNT_MESSAGE,
    //
    UPDATE_COUNT,
    //
    UPDATE_FILTERCOUNT_MESSAGE,
    //
    HIDE_TARGET_TAG_LAYOUT,
    //
    SHOW_TARGET_TAG_LAYOUT,
    //
    HIDE_DISTRIBUTION_TAG_LAYOUT,
    //
    SHOW_DISTRIBUTION_TAG_LAYOUT,
    //
    MAX_ACTION_HISTORY,
    //
    MIN_ACTION_HISTORY,
    //
    CLOSE_SAVE_ACTIONS_WINDOW,
    //
    UNASSIGN_TARGET_TAG,
    //
    UNASSIGN_DISTRIBUTION_TAG,
    //
    ASSIGN_TARGET_TAG,
    //
    ASSIGN_DISTRIBUTION_TAG,
    //
    TARGET_TABLE_FILTER,
    //
    RESET_SIMPLE_FILTERS,
    //
    RESET_TARGET_FILTER_QUERY,
    //
    SAVE_TENANT_CONFIGURATION
}
