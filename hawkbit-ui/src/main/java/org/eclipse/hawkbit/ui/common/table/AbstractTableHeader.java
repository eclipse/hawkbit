/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.table;

import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.artifacts.state.ArtifactUploadState;
import org.eclipse.hawkbit.ui.common.builder.LabelBuilder;
import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.components.SPUIButton;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.distributions.state.ManageDistUIState;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.StringUtils;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.event.dd.DropHandler;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

/**
 * Parent class for table header.
 */
public abstract class AbstractTableHeader extends VerticalLayout {

    private static final long serialVersionUID = 1L;

    protected VaadinMessageSource i18n;

    protected SpPermissionChecker permChecker;

    protected transient EventBus.UIEventBus eventBus;

    private Label headerCaption;

    private TextField searchField;

    private SPUIButton searchResetIcon;

    private Button showFilterButtonLayout;

    private Button addIcon;

    private Button syncHono;

    private SPUIButton maxMinIcon;

    private HorizontalLayout filterDroppedInfo;

    private Button bulkUploadIcon;

    private final ManagementUIState managementUIState;

    private final ManageDistUIState manageDistUIstate;

    private final ArtifactUploadState artifactUploadState;

    private final boolean honoSyncEnabled;

    protected AbstractTableHeader(final VaadinMessageSource i18n, final SpPermissionChecker permChecker,
            final UIEventBus eventBus, final ManagementUIState managementUIState,
            final ManageDistUIState manageDistUIstate, final ArtifactUploadState artifactUploadState,
            final boolean honoSyncEnabled) {
        this.i18n = i18n;
        this.permChecker = permChecker;
        this.eventBus = eventBus;
        this.managementUIState = managementUIState;
        this.manageDistUIstate = manageDistUIstate;
        this.artifactUploadState = artifactUploadState;
        this.honoSyncEnabled = honoSyncEnabled;
        createComponents();
        buildLayout();
        restoreState();
        if (doSubscribeToEventBus()) {
            eventBus.subscribe(this);
        }
    }

    /**
     * Subscribes the view to the eventBus. Method has to be overriden (return
     * false) if the view does not contain any listener to avoid Vaadin blowing
     * up our logs with warnings.
     */
    protected boolean doSubscribeToEventBus() {
        return true;
    }

    private void createComponents() {
        headerCaption = createHeaderCaption();
        searchField = new TextFieldBuilder(64).id(getSearchBoxId())
                .createSearchField(event -> searchBy(event.getText()));

        searchResetIcon = createSearchResetIcon();

        addIcon = createAddIcon();

        syncHono = createSyncHonoIcon();

        bulkUploadIcon = createBulkUploadIcon();

        showFilterButtonLayout = createShowFilterButtonLayout();
        // Not visible by default.
        showFilterButtonLayout.setVisible(false);

        maxMinIcon = createMaxMinIcon();

        final String onLoadSearchBoxValue = onLoadSearchBoxValue();

        if (onLoadSearchBoxValue != null && onLoadSearchBoxValue.length() > 0) {
            openSearchTextField();
            searchField.setValue(onLoadSearchBoxValue);
        }

    }

    private void restoreState() {

        final String onLoadSearchBoxValue = onLoadSearchBoxValue();
        if (StringUtils.hasText(onLoadSearchBoxValue)) {
            openSearchTextField();
            searchField.setValue(onLoadSearchBoxValue.trim());
        }

        if (onLoadIsTableMaximized()) {
            /**
             * If table is maximized display the minimize icon.
             */
            showMinIcon();
            hideAddAndUploadIcon();
        }

        if (onLoadIsShowFilterButtonDisplayed()) {
            /**
             * Show filter button will be displayed when filter button layout is
             * closed.
             */
            setFilterButtonsIconVisible(true);
        }
        if (isBulkUploadInProgress()) {
            disableBulkUpload();
        }

    }

    private void hideAddAndUploadIcon() {
        addIcon.setVisible(false);
        syncHono.setVisible(false);
        bulkUploadIcon.setVisible(false);
    }

    private void showAddAndUploadIcon() {
        addIcon.setVisible(true);
        syncHono.setVisible(true);
        bulkUploadIcon.setVisible(true);
    }

    private void buildLayout() {
        final HorizontalLayout titleFilterIconsLayout = createHeaderFilterIconLayout();

        titleFilterIconsLayout.addComponents(headerCaption, searchField, searchResetIcon, showFilterButtonLayout);
        titleFilterIconsLayout.setComponentAlignment(headerCaption, Alignment.TOP_LEFT);
        titleFilterIconsLayout.setComponentAlignment(searchField, Alignment.TOP_RIGHT);
        titleFilterIconsLayout.setComponentAlignment(searchResetIcon, Alignment.TOP_RIGHT);
        titleFilterIconsLayout.setComponentAlignment(showFilterButtonLayout, Alignment.TOP_RIGHT);
        if (honoSyncEnabled && isHonoSyncAllowed()) {
            titleFilterIconsLayout.addComponent(syncHono);
            titleFilterIconsLayout.setComponentAlignment(syncHono, Alignment.TOP_RIGHT);
        }
        else if (hasCreatePermission() && isAddNewItemAllowed()) {
            titleFilterIconsLayout.addComponent(addIcon);
            titleFilterIconsLayout.setComponentAlignment(addIcon, Alignment.TOP_RIGHT);
        }
        if (hasCreatePermission() && isBulkUploadAllowed()) {
            titleFilterIconsLayout.addComponent(bulkUploadIcon);
            titleFilterIconsLayout.setComponentAlignment(bulkUploadIcon, Alignment.TOP_RIGHT);
        }
        titleFilterIconsLayout.addComponent(maxMinIcon);
        titleFilterIconsLayout.setComponentAlignment(maxMinIcon, Alignment.TOP_RIGHT);
        titleFilterIconsLayout.setExpandRatio(headerCaption, 0.4F);
        titleFilterIconsLayout.setExpandRatio(searchField, 0.6F);

        addComponent(titleFilterIconsLayout);

        final HorizontalLayout dropHintDropFilterLayout = new HorizontalLayout();
        dropHintDropFilterLayout.addStyleName("filter-drop-hint-layout");
        dropHintDropFilterLayout.setWidth(100, Unit.PERCENTAGE);
        if (isDropFilterRequired()) {
            filterDroppedInfo = new HorizontalLayout();
            filterDroppedInfo.setImmediate(true);
            filterDroppedInfo.setStyleName("target-dist-filter-info");
            filterDroppedInfo.setHeightUndefined();
            filterDroppedInfo.setSizeUndefined();
            displayFilterDropedInfoOnLoad();
            final DragAndDropWrapper dropFilterLayout = new DragAndDropWrapper(filterDroppedInfo);
            dropFilterLayout.setId(getDropFilterId());
            dropFilterLayout.setDropHandler(getDropFilterHandler());

            dropHintDropFilterLayout.addComponent(dropFilterLayout);
            dropHintDropFilterLayout.setComponentAlignment(dropFilterLayout, Alignment.TOP_CENTER);
            dropHintDropFilterLayout.setExpandRatio(dropFilterLayout, 1.0F);
        }
        addComponent(dropHintDropFilterLayout);
        setComponentAlignment(dropHintDropFilterLayout, Alignment.TOP_CENTER);
        addStyleName("bordered-layout");
        addStyleName("no-border-bottom");
    }

    /**
     * to be overridden by concrete implementation.
     */
    protected void displayFilterDropedInfoOnLoad() {
        filterDroppedInfo.removeAllComponents();
    }

    private Label createHeaderCaption() {
        return new LabelBuilder().name(getHeaderCaption()).buildCaptionLabel();
    }

    private SPUIButton createSearchResetIcon() {
        final SPUIButton button = (SPUIButton) SPUIComponentProvider.getButton(getSearchRestIconId(), "",
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_SEARCH), null, false, FontAwesome.SEARCH,
                SPUIButtonStyleNoBorder.class);
        button.addClickListener(event -> onSearchResetClick());
        button.setData(Boolean.FALSE);
        return button;
    }

    private Button createAddIcon() {
        final Button button = SPUIComponentProvider.getButton(getAddIconId(), "",
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_ADD), null, false, FontAwesome.PLUS,
                SPUIButtonStyleNoBorder.class);
        button.addClickListener(this::addNewItem);
        return button;
    }

    private Button createSyncHonoIcon() {
        final Button button = SPUIComponentProvider.getButton(getSyncIconId(), "",
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_SYNC_HONO), null, false, FontAwesome.REFRESH,
                SPUIButtonStyleNoBorder.class);
        button.addClickListener(this::syncHono);
        return button;
    }

    private Button createBulkUploadIcon() {
        final Button button = SPUIComponentProvider.getButton(getBulkUploadIconId(), "",
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_BULK_UPLOAD), null, false, FontAwesome.UPLOAD,
                SPUIButtonStyleNoBorder.class);
        button.addClickListener(this::bulkUpload);
        return button;
    }

    private Button createShowFilterButtonLayout() {
        final Button button = SPUIComponentProvider.getButton(getShowFilterButtonLayoutId(), null,
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_SHOW_TAGS), null, false, FontAwesome.TAGS,
                SPUIButtonStyleNoBorder.class);
        button.setVisible(false);
        button.addClickListener(event -> showFilterButtonsIconClicked());
        return button;
    }

    private SPUIButton createMaxMinIcon() {
        final SPUIButton button = (SPUIButton) SPUIComponentProvider.getButton(getMaxMinIconId(), "",
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_MAXIMIZE), null, false, FontAwesome.EXPAND,
                SPUIButtonStyleNoBorder.class);
        button.addClickListener(event -> maxMinButtonClicked());
        return button;
    }

    private void onSearchResetClick() {
        final Boolean flag = isSearchFieldOpen();
        if (flag == null || Boolean.FALSE.equals(flag)) {
            // Clicked on search Icon
            openSearchTextField();
        } else {
            // Clicked on rest icon
            closeSearchTextField();
        }
    }

    protected Boolean isSearchFieldOpen() {
        return (Boolean) searchResetIcon.getData();
    }

    private void openSearchTextField() {
        searchResetIcon.addStyleName(SPUIDefinitions.FILTER_RESET_ICON);
        searchResetIcon.toggleIcon(FontAwesome.TIMES);
        searchResetIcon.setData(Boolean.TRUE);
        searchResetIcon.setDescription(i18n.getMessage(UIMessageIdProvider.TOOLTIP_RESET));
        searchField.removeStyleName(SPUIDefinitions.FILTER_BOX_HIDE);
        searchField.focus();
    }

    private void closeSearchTextField() {
        searchField.setValue("");
        searchField.addStyleName(SPUIDefinitions.FILTER_BOX_HIDE);
        searchResetIcon.setDescription(i18n.getMessage(UIMessageIdProvider.TOOLTIP_SEARCH));
        searchResetIcon.removeStyleName(SPUIDefinitions.FILTER_RESET_ICON);
        searchResetIcon.toggleIcon(FontAwesome.SEARCH);
        searchResetIcon.setData(Boolean.FALSE);
        resetSearchText();
    }

    private void maxMinButtonClicked() {
        final Boolean flag = (Boolean) maxMinIcon.getData();
        if (flag == null || Boolean.FALSE.equals(flag)) {
            // Clicked on max Icon
            maximizedTableView();
        } else {
            // Clicked on min icon
            minimizeTableView();
        }
    }

    private void maximizedTableView() {
        showMinIcon();
        hideAddAndUploadIcon();
        maximizeTable();
    }

    private void minimizeTableView() {
        showMaxIcon();
        showAddAndUploadIcon();
        minimizeTable();
    }

    private void showMinIcon() {
        maxMinIcon.toggleIcon(FontAwesome.COMPRESS);
        maxMinIcon.setData(Boolean.TRUE);
        maxMinIcon.setDescription(i18n.getMessage(UIMessageIdProvider.TOOLTIP_MINIMIZE));
    }

    private void showMaxIcon() {
        maxMinIcon.toggleIcon(FontAwesome.EXPAND);
        maxMinIcon.setData(Boolean.FALSE);
        maxMinIcon.setDescription(i18n.getMessage(UIMessageIdProvider.TOOLTIP_MAXIMIZE));
    }

    private static HorizontalLayout createHeaderFilterIconLayout() {
        final HorizontalLayout titleFilterIconsLayout = new HorizontalLayout();
        titleFilterIconsLayout.addStyleName(SPUIStyleDefinitions.WIDGET_TITLE);
        titleFilterIconsLayout.setSpacing(false);
        titleFilterIconsLayout.setMargin(false);
        titleFilterIconsLayout.setSizeFull();
        return titleFilterIconsLayout;
    }

    private void showFilterButtonsIconClicked() {
        /* Remove the show filter Buttons button */
        setFilterButtonsIconVisible(false);
        /* Show the filter buttons layout */
        showFilterButtonsLayout();
    }

    protected void setFilterButtonsIconVisible(final boolean isVisible) {
        showFilterButtonLayout.setVisible(isVisible);
    }

    protected HorizontalLayout getFilterDroppedInfo() {
        return filterDroppedInfo;
    }

    protected void enableBulkUpload() {
        bulkUploadIcon.setEnabled(true);
    }

    protected void disableBulkUpload() {
        bulkUploadIcon.setEnabled(false);
    }

    /**
     * Resets search text and closed search field when complex filters are
     * applied.
     */
    protected void resetSearch() {
        closeSearchTextField();
    }

    /**
     * Once user switches to custom filters search functionality is re-enabled.
     */
    protected void disableSearch() {
        searchResetIcon.setEnabled(false);
    }

    /**
     * Once user switches to simple filters search functionality is re-enabled.
     */
    protected void reEnableSearch() {
        searchResetIcon.setEnabled(true);
    }

    /**
     * Get the Id value of the drop filter in the table header.
     * 
     * @return Id of the drop filter if filter is displayed, otherwise returns
     *         null.
     */
    protected abstract String getDropFilterId();

    /**
     * Get style of the filter Icon.
     * 
     * @return style of the filter Icon
     */
    protected abstract String getFilterIconStyle();

    /**
     * Get the Id value of the drop filter wrapper in the table header.
     * 
     * @return Id of the drop filter if filter is displayed, otherwise returns
     *         null.
     */
    protected abstract String getDropFilterWrapperId();

    protected abstract DropHandler getDropFilterHandler();

    /**
     * Check if drop hits required to display and user has permissions for that.
     * 
     * @return true if drop hits should be displayed and user has permission,
     *         otherwise false.
     */
    protected abstract boolean isDropHintRequired();

    /**
     * Check if drop filter is required.
     * 
     * @return true if drop filter is required to display, otherwise return
     *         false.
     */
    protected abstract boolean isDropFilterRequired();

    /**
     * Checks if the creation of a new item is allowed. Default is true.
     * 
     * @return true if the creation of a new item is allowed, otherwise returns
     *         false.
     */
    protected abstract Boolean isAddNewItemAllowed();

    /**
     * Checks if the synchronization with Hono is allowed. Default is true.
     *
     * @return true if the synchronization with hono is allowed, otherwise returns
     *         false.
     */
    protected abstract Boolean isHonoSyncAllowed();

    /**
     * Get Id of bulk upload Icon.
     * 
     * @return String of bulk upload Icon.
     */
    protected abstract String getBulkUploadIconId();

    /**
     * Gets the flag if bulk upload is allowed. Default is false
     * 
     * @return true if bulk upload is allowed, otherwise returns false.
     */
    protected abstract Boolean isBulkUploadAllowed();

    /**
     * Checks if bulk upload is in progress. Default is false.
     * 
     * @return true if bulk upload is in progress, otherwise returns false.
     */
    protected abstract boolean isBulkUploadInProgress();

    /**
     * Performs the bulk upload
     * 
     * @param event
     *            Event of type ClickEvent
     */
    protected abstract void bulkUpload(final ClickEvent event);

    /**
     * Get the title of the table.
     * 
     * @return title as String.
     */
    protected abstract String getHeaderCaption();

    /**
     * get Id of search text field.
     * 
     * @return Id of the text field.
     */
    protected abstract String getSearchBoxId();

    /**
     * Get search reset Icon Id.
     * 
     * @return Id of search reset icon.
     */
    protected abstract String getSearchRestIconId();

    /**
     * Get Id of add Icon.
     *
     * @return String of add Icon.
     */
    protected abstract String getAddIconId();

    /**
     * Get Id of sync Icon.
     *
     * @return String of sync Icon.
     */
    protected abstract String getSyncIconId();

    /**
     * Get search box on load text value.
     * 
     * @return value of search box.
     */
    protected abstract String onLoadSearchBoxValue();

    /**
     * Check if logged in user has create permission..
     * 
     * @return true of user has create permission, otherwise return false.
     */
    protected abstract boolean hasCreatePermission();

    /**
     * Get Id of the show filter buttons layout.
     * 
     * @return Id of the show filter buttons Icon.
     */
    protected abstract String getShowFilterButtonLayoutId();

    /**
     * Show the filter buttons layout logic. This method will be called when
     * show filter buttons Icon is clicked displayed on the header.
     */
    protected abstract void showFilterButtonsLayout();

    /**
     * This method will be called when user resets the search text means on
     * click of (X) icon.
     */
    protected abstract void resetSearchText();

    /**
     * Get the Id of min/max button for the table.
     * 
     * @return Id of min/max button.
     */
    protected abstract String getMaxMinIconId();

    /**
     * Called when table is maximized.
     */
    public abstract void maximizeTable();

    /**
     * Called when table is minimized.
     */
    public abstract void minimizeTable();

    /**
     * Get the max/min icon state on load.
     * 
     * @return true if table should be maximized.
     */
    public abstract Boolean onLoadIsTableMaximized();

    /**
     * On load show filter button is displayed.
     * 
     * @return true if requires to delete, otherwise false.
     */
    public abstract Boolean onLoadIsShowFilterButtonDisplayed();

    protected abstract void searchBy(String newSearchText);

    protected abstract void addNewItem(final Button.ClickEvent event);

    protected abstract void syncHono(final Button.ClickEvent event);

    protected ManagementUIState getManagementUIState() {
        return managementUIState;
    }

    protected ManageDistUIState getManageDistUIstate() {
        return manageDistUIstate;
    }

    protected ArtifactUploadState getArtifactUploadState() {
        return artifactUploadState;
    }

}
