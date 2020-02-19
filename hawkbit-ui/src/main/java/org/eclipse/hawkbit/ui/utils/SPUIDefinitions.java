/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.utils;

import org.springframework.data.domain.Sort.Direction;

/**
 * Class to provide the unchanged constants.
 */
public final class SPUIDefinitions {
    /**
     * Locale cookie name.
     */
    public static final String COOKIE_NAME = "BOSCHSI_UII_LOCALE";
    /**
     * Lazy query container page size.
     */
    public static final int PAGE_SIZE = 50;

    /**
     * Lazy query container Distribution Set Type size.
     */
    public static final int DIST_TYPE_SIZE = 8;

    /**
     * Action history active hidden column. This is using to generate active
     * icons under active column.
     */
    public static final String ACTION_HIS_TBL_ACTIVE_HIDDEN = "Active_Hidden";

    /**
     * Action history Action Id hidden column.
     */
    public static final String ACTION_HIS_TBL_ACTION_ID_HIDDEN = "DistributionsetId";

    /**
     * Action history hidden column of messages of particular action update.
     */
    public static final String ACTION_HIS_TBL_MSGS_HIDDEN = "Messages_Hidden";

    /**
     * /** Action history status hidden column. This is using to generate status
     * icons under status coloumn.
     */
    public static final String ACTION_HIS_TBL_STATUS_HIDDEN = "Status_Hidden";

    /**
     * Filter by status key.
     */
    public static final String FILTER_BY_STATUS = "FilterByStatus";

    /**
     * Filter by overdue state key.
     */
    public static final String FILTER_BY_OVERDUE_STATE = "FilterByOverdueState";

    /**
     * Filter by tag key.
     */
    public static final String FILTER_BY_TAG = "FilterByTag";
    /**
     * Filter by no tag button.
     */
    public static final String FILTER_BY_NO_TAG = "FilterByNoTag";
    /**
     * Filter by target filter query.
     */
    public static final String FILTER_BY_TARGET_FILTER_QUERY = "FilterByTargetFilterQuery";

    /**
     * Filter by distribution key.
     */
    public static final String FILTER_BY_DISTRIBUTION = "FilterByDistribution";
    /**
     * Filter by distributionSet Type.
     */
    public static final String FILTER_BY_DISTRIBUTION_SET_TYPE = "FilterByDistributionSetType";
    /**
     * Order by pinnedTarget.
     */
    public static final String ORDER_BY_PINNED_TARGET = "OrderByPinnedTarget";
    /**
     * Filter by text key.
     */
    public static final String FILTER_BY_TEXT = "FilterByText";
    /**
     * Text field style.
     */
    public static final String TEXT_STYLE = "text-style";
    /**
     * Show actions for a target.
     */
    public static final String ACTIONS_BY_TARGET = "ActionsByTarget";
    /**
     * Show action-states for a action.
     */
    public static final String ACTIONSTATES_BY_ACTION = "ActionStatesByAction";
    /**
     * Show messages for a action-status.
     */
    public static final String MESSAGES_BY_ACTIONSTATUS = "MessagesByActionStatus";
    /**
     * Key for no-message MessageProxy.
     */
    public static final String NO_MSG_PROXY = "NoMessageProxy";

    /**
     * Style to highlight row in orange color.
     */
    public static final String HIGHLIGHT_ORANGE = "highlight-orange";

    /**
     * Style to highlight row in green color.
     */
    public static final String HIGHLIGHT_GREEN = "highlight-green";

    /**
     * The Combo box specific style for Distribution set.
     */
    public static final String COMBO_BOX_SPECIFIC_STYLE = "combo-specific-style";

    /**
     * Target and distribution column width in save popup window.
     */
    public static final float TARGET_DISTRIBUTION_COLUMN_WIDTH = 2.8F;
    /**
     * Discard column width in save window popup.
     */
    public static final float DISCARD_COLUMN_WIDTH = 1.2F;
    /**
     * Target tag name.
     */
    public static final String TAG_NAME = "target-tag-name";
    /**
     * Target tag desc.
     */
    public static final String TAG_DESC = "target-tag-desc";
    /**
     * Software type name.
     */
    public static final String TYPE_NAME = "type-name";
    /**
     * DistributionSet type name.
     */
    public static final String DIST_SET_TYPE_NAME = "dist-set-type-name";

    /**
     * DiscriptionSet type desc.
     */
    public static final String DIST_SET_TYPE_DESC = "dist-set-type-desc";

    /**
     * DistributionSet type key.
     */
    public static final String DIST_SET_TYPE_KEY = "dist-set-type-key";
    /**
     * Software type desc.
     */
    public static final String TYPE_DESC = "type-desc";

    /**
     * Software type key.
     */
    public static final String TYPE_KEY = "type-key";

    /**
     * Minimum width required to show tags for both target and distribution
     * table without horizontal scroll. In case browser width is less than this
     * width, the tags will get hidden automatically and also horizontal scroll
     * bars get displayed. Used for Responsive UI.
     */
    public static final int REQ_MIN_BROWSER_WIDTH = 1200;

    /**
     * Target tag button id prefix.
     */
    public static final String TARGET_TAG_ID_PREFIXS = "target.tag.";
    /**
     * Distribution tag button id prefix.
     */
    public static final String DISTRIBUTION_TAG_ID_PREFIXS = "dist.tag.";

    /**
     * Space.
     */
    public static final String SPACE = "&nbsp;";

    /**
     * Distribution tag button id prefix.
     */
    public static final String SOFTWARE_MODULE_TAG_ID_PREFIXS = "swmodule.type.";

    /**
     * DistributionSet Type tag button id prefix.
     */
    public static final String DISTRIBUTION_SET_TYPE_ID_PREFIXS = "dist.set.type.";
    /**
     * Target last query date format .
     */
    public static final String LAST_QUERY_DATE_FORMAT = "EEE MMM d HH:mm:ss z yyyy";
    /**
     * Target last query date format .
     */
    public static final String LAST_QUERY_DATE_FORMAT_SHORT = "MMM d HH:mm z ''yy";
    /**
     * Item Id used in drop comparisons.
     */
    public static final String ITEMID = "itemId";
    /**
     * Expand action history.
     */
    public static final String EXPAND_ACTION_HISTORY = "expand.action.history";
    /**
     * Filter by distribution key.
     */
    public static final String ORDER_BY_DISTRIBUTION = "OrderByDistribution";

    /** Artifact upload related entries - start **/
    /**
     * Artifact details by Base software module id.
     */
    public static final String BY_BASE_SOFTWARE_MODULE = "ByBaseSoftwareModule";

    /**
     * Software module type.
     */
    public static final String BY_SOFTWARE_MODULE_TYPE = "softwareModuleType";

    /**
     * Minimum width required to display ui without horizontal scroll. In case
     * browser width is less than this width, the type layout will get hidden
     * automatically and also horizontal scroll bars get displayed. Used for
     * Responsive UI.
     */
    public static final int REQ_MIN_UPLOAD_BROWSER_WIDTH = 1250;

    public static final int MIN_UPLOAD_CONFIRMATION_POPUP_WIDTH = 1000;

    public static final int MIN_UPLOAD_CONFIRMATION_POPUP_HEIGHT = 310;

    public static final int MAX_UPLOAD_CONFIRMATION_POPUP_WIDTH = 1050;

    public static final int MAX_UPLOAD_CONFIRMATION_POPUP_HEIGHT = 360;

    /** Artifact upload related entries - end. **/

    public static final int MIN_DASHBOARD_HEIGHT = 600;

    public static final int MIN_DASHBOARD_WIDTH = 1100;

    /* Target Header Filter Box */
    public static final String FILTER_BOX_HIDE = "filter-box-hide";
    public static final String FILTER_RESET_ICON = "filter-reset-icon";

    /* Action History */
    public static final String DISABLE_DISTRIBUTION = "incomplete-distribution";

    /**
     * Filter by type layout width.
     */
    public static final float FILTER_BY_TYPE_WIDTH = 150.0F;

    /**
     * Confirmation jukebox type.
     */
    public static final String CONFIRMATION_WINDOW = "confirmation-window";

    /**
     * Create/Update window type.
     */
    public static final String CREATE_UPDATE_WINDOW = "create-update-window";

    /**
     * Defines the maximum entries in the target table before the table
     * truncates it. This protects to endless scroll to very high page numbers
     * which is very in performant.
     */
    public static final int MAX_TABLE_ENTRIES = 5000;

    /**
     * Distribution Type Twin Table selected table Id.
     */
    public static final String TWIN_TABLE_SELECTED_ID = "dist.type.selected.id";

    /**
     * Distribution Type Twin Table source table Id.
     */
    public static final String TWIN_TABLE_SOURCE_ID = "dist.type.source.id";

    /**
     * Target table status/pin icon column Id.
     */
    public static final String TARGET_STATUS_PIN_TOGGLE_ICON = "targetStatusPinToggle";

    /**
     * Target table poll time icon column Id.
     */
    public static final String TARGET_STATUS_POLL_TIME = "targetPollTime";

    /**
     * Default green color for filters.
     */
    public static final String DEFAULT_COLOR = "rgb(44,151,32)";

    /**
     * Id f "NO TAG" button.
     */
    public static final String NO_TAG_BUTTON_ID = "no_tag_button";

    /**
     * DELETE column/button.
     */
    public static final String DELETE = "Delete";

    /**
     * EDIT column/button.
     */
    public static final String EDIT = "Edit";

    /**
     * Bulk upload DS combo style.
     */
    public static final String BULK_UPLOD_DS_COMBO_STYLE = "bulk-upload-ds-combo";

    /**
     * Filter by target filter query.
     */
    public static final String FILTER_BY_QUERY = "FilterByTargetFilterQueryText";

    /**
     * Filter by invalid target filter query.
     */
    public static final String FILTER_BY_INVALID_QUERY = "FilterByInvalidFilterQueryText";

    /**
     * Filter by distribution set complete.
     */
    public static final String FILTER_BY_DS_COMPLETE = "FilterByDistributionSetComplete";

    /**
     * Sort order of column - created at in target table.
     */
    public static final Direction TARGET_TABLE_LASTMODIFIED_AT_SORT_ORDER = Direction.DESC;

    /**
     * BUTTON- STATUS.
     */
    public static final String SP_BUTTON_STATUS_STYLE = "targetStatusBtn";

    public static final String ACTION_HIS_TBL_MAINTENANCE_WINDOW = "Maintenance Window";

    public static final String ACTION_WEIGHT_COLUMN = "Weight";

    /**
     * id for delete icon in entity table
     */
    public static final String DELETE_ENTITY = "deleteEntity";

    public static final String UPDATE_FILTER_BUTTON_COLUMN = "updateFilterButton";

    public static final String DELETE_FILTER_BUTTON_COLUMN = "deleteFilterButton";

    /**
     * /** Constructor.
     */
    private SPUIDefinitions() {

    }
}
