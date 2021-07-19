/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout.rollout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus.Status;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.eclipse.hawkbit.ui.common.builder.StatusIconBuilder.ActionTypeIconSupplier;
import org.eclipse.hawkbit.ui.common.builder.StatusIconBuilder.RolloutStatusIconSupplier;
import org.eclipse.hawkbit.ui.common.data.mappers.RolloutToProxyRolloutMapper;
import org.eclipse.hawkbit.ui.common.data.providers.RolloutDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyRollout;
import org.eclipse.hawkbit.ui.common.event.CommandTopics;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload.EntityModifiedEventType;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.common.event.LayoutVisibilityEventPayload;
import org.eclipse.hawkbit.ui.common.event.LayoutVisibilityEventPayload.VisibilityType;
import org.eclipse.hawkbit.ui.common.event.SelectionChangedEventPayload.SelectionChangedEventType;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.support.DeleteSupport;
import org.eclipse.hawkbit.ui.common.grid.support.FilterSupport;
import org.eclipse.hawkbit.ui.common.grid.support.SelectionSupport;
import org.eclipse.hawkbit.ui.rollout.DistributionBarHelper;
import org.eclipse.hawkbit.ui.rollout.RolloutManagementUIState;
import org.eclipse.hawkbit.ui.rollout.window.RolloutWindowBuilder;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;

import com.cronutils.utils.StringUtils;
import com.google.common.base.Predicates;
import com.vaadin.data.ValueProvider;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;
import com.vaadin.ui.renderers.HtmlRenderer;

/**
 * Rollout list grid component.
 */
public class RolloutGrid extends AbstractGrid<ProxyRollout, String> {
    private static final long serialVersionUID = 1L;
    private static final String ROLLOUT_CAPTION_MSG_KEY = "caption.rollout";

    private static final String ROLLOUT_LINK_ID = "rollout";
    private static final String DIST_NAME_VERSION_ID = "distNameVersion";
    private static final String STATUS_ID = "status";
    private static final String TOTAL_TARGETS_COUNT_STATUS_ID = "totalTargetsCountStatus";
    private static final String NUMBER_OF_GROUPS_ID = "numberOfGroups";
    private static final String TOTAL_TARGETS_ID = "totalTargets";
    private static final String APPROVAL_DECIDED_BY_ID = "approvalDecidedBy";
    private static final String APPROVAL_REMARK_ID = "approvalRemark";
    private static final String DESC_ID = "description";
    private static final String ACTION_TYPE_ID = "actionType";

    private static final String APPROVE_BUTTON_ID = "approve";
    private static final String RUN_BUTTON_ID = "run";
    private static final String PAUSE_BUTTON_ID = "pause";
    private static final String UPDATE_BUTTON_ID = "update";
    private static final String COPY_BUTTON_ID = "copy";
    private static final String DELETE_BUTTON_ID = "delete";

    private final RolloutStatusIconSupplier<ProxyRollout> rolloutStatusIconSupplier;
    private final ActionTypeIconSupplier<ProxyRollout> actionTypeIconSupplier;

    private final RolloutManagementUIState rolloutManagementUIState;

    private final transient RolloutManagement rolloutManagement;
    private final transient RolloutToProxyRolloutMapper rolloutMapper;
    private final transient RolloutGroupManagement rolloutGroupManagement;
    private final transient TenantConfigurationManagement tenantConfigManagement;
    private final transient SystemSecurityContext systemSecurityContext;

    private final transient RolloutWindowBuilder rolloutWindowBuilder;
    private final UINotification uiNotification;

    private final transient DeleteSupport<ProxyRollout> rolloutDeleteSupport;

    RolloutGrid(final CommonUiDependencies uiDependencies, final RolloutManagement rolloutManagement,
            final RolloutGroupManagement rolloutGroupManagement,
            final RolloutManagementUIState rolloutManagementUIState,
            final TenantConfigurationManagement tenantConfigManagement, final RolloutWindowBuilder rolloutWindowBuilder,
            final SystemSecurityContext systemSecurityContext) {
        super(uiDependencies.getI18n(), uiDependencies.getEventBus(), uiDependencies.getPermChecker());

        this.rolloutManagementUIState = rolloutManagementUIState;
        this.rolloutManagement = rolloutManagement;
        this.rolloutGroupManagement = rolloutGroupManagement;
        this.tenantConfigManagement = tenantConfigManagement;
        this.systemSecurityContext = systemSecurityContext;
        this.uiNotification = uiDependencies.getUiNotification();
        this.rolloutWindowBuilder = rolloutWindowBuilder;
        this.rolloutMapper = new RolloutToProxyRolloutMapper();

        setSelectionSupport(new SelectionSupport<>(this, eventBus, EventLayout.ROLLOUT_LIST, EventView.ROLLOUT,
                this::mapIdToProxyEntity, this::getSelectedEntityIdFromUiState, this::setSelectedEntityIdToUiState));
        getSelectionSupport().disableSelection();

        this.rolloutDeleteSupport = new DeleteSupport<>(this, i18n, uiNotification, ROLLOUT_CAPTION_MSG_KEY,
                "caption.rollouts", ProxyRollout::getName, this::deleteRollout,
                UIComponentIdProvider.ROLLOUT_DELETE_CONFIRMATION_DIALOG);
        this.rolloutDeleteSupport.setConfirmationQuestionDetailsGenerator(this::getDeletionDetails);

        setFilterSupport(new FilterSupport<>(new RolloutDataProvider(rolloutManagement, rolloutMapper)));
        initFilterMappings();

        rolloutStatusIconSupplier = new RolloutStatusIconSupplier<>(i18n, ProxyRollout::getStatus,
                UIComponentIdProvider.ROLLOUT_STATUS_LABEL_ID);
        actionTypeIconSupplier = new ActionTypeIconSupplier<>(i18n, ProxyRollout::getActionType,
                UIComponentIdProvider.ROLLOUT_ACTION_TYPE_LABEL_ID);
        init();
    }

    private void initFilterMappings() {
        getFilterSupport().<String> addMapping(FilterType.SEARCH, (filter, searchText) -> setSearchFilter(searchText),
                rolloutManagementUIState.getSearchText().orElse(null));
    }

    private void setSearchFilter(final String searchText) {
        getFilterSupport().setFilter(!StringUtils.isEmpty(searchText) ? String.format("%%%s%%", searchText) : null);
    }

    /**
     * Map id to rollout
     *
     * @param entityId
     *            Entity id
     *
     * @return Rollout
     */
    public Optional<ProxyRollout> mapIdToProxyEntity(final long entityId) {
        return rolloutManagement.get(entityId).map(rolloutMapper::map);
    }

    private Long getSelectedEntityIdFromUiState() {
        return rolloutManagementUIState.getSelectedRolloutId();
    }

    private void setSelectedEntityIdToUiState(final Long entityId) {
        rolloutManagementUIState.setSelectedRolloutId(entityId);
    }

    private boolean deleteRollout(final Collection<ProxyRollout> rolloutsToBeDeleted) {
        final Collection<Long> rolloutToBeDeletedIds = rolloutsToBeDeleted.stream().map(ProxyIdentifiableEntity::getId)
                .collect(Collectors.toList());
        rolloutToBeDeletedIds.forEach(rolloutManagement::delete);

        // Rollout is not deleted straight away, but updated to
        // deleting state
        eventBus.publish(EventTopics.ENTITY_MODIFIED, this, new EntityModifiedEventPayload(
                EntityModifiedEventType.ENTITY_UPDATED, ProxyRollout.class, rolloutToBeDeletedIds));

        return true;
    }

    private String getDeletionDetails(final ProxyRollout rollout) {
        final Map<Status, Long> statusTotalCount = rollout.getStatusTotalCountMap();

        Long scheduledActions = statusTotalCount.get(Status.SCHEDULED);
        if (scheduledActions == null) {
            scheduledActions = 0L;
        }
        Long runningActions = statusTotalCount.get(Status.RUNNING);
        if (runningActions == null) {
            runningActions = 0L;
        }

        if ((scheduledActions > 0) || (runningActions > 0)) {
            return i18n.getMessage("message.delete.rollout.details", runningActions, scheduledActions);
        }

        return "";
    }

    private static boolean isDeletionAllowed(final RolloutStatus status) {
        final List<RolloutStatus> statesThatAllowDeletion = Arrays.asList(RolloutStatus.CREATING, RolloutStatus.PAUSED,
                RolloutStatus.READY, RolloutStatus.RUNNING, RolloutStatus.STARTING, RolloutStatus.STOPPED,
                RolloutStatus.FINISHED, RolloutStatus.WAITING_FOR_APPROVAL, RolloutStatus.APPROVAL_DENIED);
        return statesThatAllowDeletion.contains(status);
    }

    private static boolean isCopyingAllowed(final RolloutStatus status) {
        return isDeletionAllowed(status) && status != RolloutStatus.CREATING;
    }

    private static boolean isEditingAllowed(final RolloutStatus status) {
        final List<RolloutStatus> statesThatAllowEditing = Arrays.asList(RolloutStatus.PAUSED, RolloutStatus.READY,
                RolloutStatus.RUNNING, RolloutStatus.STARTING, RolloutStatus.STOPPED);
        return statesThatAllowEditing.contains(status);
    }

    private static boolean isPausingAllowed(final RolloutStatus status) {
        return RolloutStatus.RUNNING == status;
    }

    private static boolean isApprovingAllowed(final RolloutStatus status) {
        return RolloutStatus.WAITING_FOR_APPROVAL == status;
    }

    private static boolean isStartingAndResumingAllowed(final RolloutStatus status) {
        final List<RolloutStatus> statesThatAllowStartingAndResuming = Arrays.asList(RolloutStatus.READY,
                RolloutStatus.PAUSED);
        return statesThatAllowStartingAndResuming.contains(status);
    }

    /**
     * Update items in rollout grid
     *
     * @param ids
     *            List of rollout id
     */
    public void updateGridItems(final Collection<Long> ids) {
        ids.stream().filter(Predicates.notNull()).map(rolloutManagement::getWithDetailedStatus)
                .forEach(rollout -> rollout.ifPresent(this::updateGridItem));
    }

    private void updateGridItem(final Rollout rollout) {
        final ProxyRollout proxyRollout = rolloutMapper.map(rollout);

        if (rollout.getRolloutGroupsCreated() == 0) {
            final Long groupsCount = rolloutGroupManagement.countByRollout(rollout.getId());
            proxyRollout.setNumberOfGroups(groupsCount.intValue());
        }

        getDataProvider().refreshItem(proxyRollout);
    }

    @Override
    public String getGridId() {
        return UIComponentIdProvider.ROLLOUT_LIST_GRID_ID;
    }

    @Override
    public void addColumns() {
        GridComponentBuilder.addComponentColumn(this, this::buildRolloutLink).setId(ROLLOUT_LINK_ID)
                .setCaption(i18n.getMessage("header.name")).setHidable(false).setExpandRatio(3);

        GridComponentBuilder.addDescriptionColumn(this, i18n, DESC_ID).setHidable(true).setHidden(true);

        GridComponentBuilder.addColumn(this, ProxyRollout::getDsNameVersion).setId(DIST_NAME_VERSION_ID)
                .setCaption(i18n.getMessage("header.distributionset")).setHidable(true).setExpandRatio(2);

        GridComponentBuilder
                .addIconColumn(this, rolloutStatusIconSupplier::getLabel, STATUS_ID, i18n.getMessage("header.status"))
                .setHidable(true);

        GridComponentBuilder
                .addIconColumn(this, actionTypeIconSupplier::getLabel, ACTION_TYPE_ID, i18n.getMessage("header.type"))
                .setHidable(true).setHidden(true);

        addColumn(rollout -> DistributionBarHelper.getDistributionBarAsHTMLString(rollout.getStatusTotalCountMap()),
                new HtmlRenderer()).setId(TOTAL_TARGETS_COUNT_STATUS_ID)
                        .setCaption(i18n.getMessage("header.detail.status"))
                        .setDescriptionGenerator(
                                rollout -> DistributionBarHelper.getTooltip(rollout.getStatusTotalCountMap(), i18n),
                                ContentMode.HTML)
                        .setHidable(true).setExpandRatio(5);

        GridComponentBuilder.addColumn(this, ProxyRollout::getNumberOfGroups).setId(NUMBER_OF_GROUPS_ID)
                .setCaption(i18n.getMessage("header.numberofgroups")).setHidable(true);

        GridComponentBuilder.addColumn(this, ProxyRollout::getTotalTargets).setId(TOTAL_TARGETS_ID)
                .setCaption(i18n.getMessage("header.total.targets")).setHidable(true);

        GridComponentBuilder.addColumn(this, ProxyRollout::getApprovalDecidedBy).setId(APPROVAL_DECIDED_BY_ID)
                .setCaption(i18n.getMessage("header.approvalDecidedBy")).setHidable(true).setHidden(true);
        GridComponentBuilder.addColumn(this, ProxyRollout::getApprovalRemark).setId(APPROVAL_REMARK_ID)
                .setCaption(i18n.getMessage("header.approvalRemark")).setHidable(true).setHidden(true);

        GridComponentBuilder.addCreatedAndModifiedColumns(this, i18n)
                .forEach(col -> col.setHidable(true).setHidden(true));

        addActionColumns();
    }

    private void addActionColumns() {

        final List<Column<?, ?>> actionColumns = new ArrayList<>();

        final ValueProvider<ProxyRollout, Button> startButton = rollout -> GridComponentBuilder.buildActionButton(i18n,
                clickEvent -> startOrResumeRollout(rollout.getId(), rollout.getName(), rollout.getStatus()),
                VaadinIcons.PLAY, UIMessageIdProvider.TOOLTIP_ROLLOUT_RUN, SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                UIComponentIdProvider.ROLLOUT_RUN_BUTTON_ID + "." + rollout.getId(),
                permissionChecker.hasRolloutHandlePermission() && isStartingAndResumingAllowed(rollout.getStatus()));
        actionColumns.add(GridComponentBuilder.addIconColumn(this, startButton, RUN_BUTTON_ID, null));

        final ValueProvider<ProxyRollout, Button> approveButton = rollout -> GridComponentBuilder.buildActionButton(
                i18n, clickEvent -> approveRollout(rollout), VaadinIcons.HANDSHAKE,
                UIMessageIdProvider.TOOLTIP_ROLLOUT_APPROVE, SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                UIComponentIdProvider.ROLLOUT_APPROVAL_BUTTON_ID + "." + rollout.getId(), isRolloutApprovalEnabled()
                        && permissionChecker.hasRolloutApprovalPermission() && isApprovingAllowed(rollout.getStatus()));
        actionColumns.add(GridComponentBuilder.addIconColumn(this, approveButton, APPROVE_BUTTON_ID, null));

        final ValueProvider<ProxyRollout, Button> pauseButton = rollout -> GridComponentBuilder.buildActionButton(i18n,
                clickEvent -> pauseRollout(rollout.getId(), rollout.getName(), rollout.getStatus()), VaadinIcons.PAUSE,
                UIMessageIdProvider.TOOLTIP_ROLLOUT_PAUSE, SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                UIComponentIdProvider.ROLLOUT_PAUSE_BUTTON_ID + "." + rollout.getId(),
                permissionChecker.hasRolloutHandlePermission() && isPausingAllowed(rollout.getStatus()));
        actionColumns.add(GridComponentBuilder.addIconColumn(this, pauseButton, PAUSE_BUTTON_ID, null));

        final ValueProvider<ProxyRollout, Button> updateButton = rollout -> GridComponentBuilder.buildActionButton(i18n,
                clickEvent -> updateRollout(rollout), VaadinIcons.EDIT, UIMessageIdProvider.TOOLTIP_ROLLOUT_UPDATE,
                SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                UIComponentIdProvider.ROLLOUT_UPDATE_BUTTON_ID + "." + rollout.getId(),
                permissionChecker.hasRolloutUpdatePermission() && isEditingAllowed(rollout.getStatus()));
        actionColumns.add(GridComponentBuilder.addIconColumn(this, updateButton, UPDATE_BUTTON_ID, null));

        final ValueProvider<ProxyRollout, Button> copyButton = rollout -> GridComponentBuilder.buildActionButton(i18n,
                clickEvent -> copyRollout(rollout), VaadinIcons.COPY, UIMessageIdProvider.TOOLTIP_ROLLOUT_COPY,
                SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                UIComponentIdProvider.ROLLOUT_COPY_BUTTON_ID + "." + rollout.getId(),
                permissionChecker.hasRolloutCreatePermission() && isCopyingAllowed(rollout.getStatus()));
        actionColumns.add(GridComponentBuilder.addIconColumn(this, copyButton, COPY_BUTTON_ID, null));

        actionColumns.add(GridComponentBuilder.addDeleteColumn(this, i18n, DELETE_BUTTON_ID, rolloutDeleteSupport,
                UIComponentIdProvider.ROLLOUT_DELETE_BUTTON_ID,
                rollout -> permissionChecker.hasRolloutDeletePermission() && isDeletionAllowed(rollout.getStatus())));

        GridComponentBuilder.joinToActionColumn(i18n, getDefaultHeaderRow(), actionColumns);
    }

    private Button buildRolloutLink(final ProxyRollout rollout) {
        final boolean enableButton = RolloutStatus.CREATING != rollout.getStatus();

        return GridComponentBuilder.buildLink(rollout, "rollout.link", rollout.getName(), enableButton,
                clickEvent -> onClickOfRolloutName(rollout));
    }

    private void onClickOfRolloutName(final ProxyRollout rollout) {
        getSelectionSupport().sendSelectionChangedEvent(SelectionChangedEventType.ENTITY_SELECTED, rollout);
        rolloutManagementUIState.setSelectedRolloutName(rollout.getName());

        showRolloutGroupListLayout();
    }

    private void showRolloutGroupListLayout() {
        eventBus.publish(CommandTopics.CHANGE_LAYOUT_VISIBILITY, this, new LayoutVisibilityEventPayload(
                VisibilityType.SHOW, EventLayout.ROLLOUT_GROUP_LIST, EventView.ROLLOUT));
    }

    private void pauseRollout(final Long rolloutId, final String rolloutName, final RolloutStatus rolloutStatus) {
        if (RolloutStatus.RUNNING != rolloutStatus) {
            return;
        }

        rolloutManagement.pauseRollout(rolloutId);
        uiNotification.displaySuccess(i18n.getMessage("message.rollout.paused", rolloutName));
    }

    private void startOrResumeRollout(final Long rolloutId, final String rolloutName,
            final RolloutStatus rolloutStatus) {
        switch (rolloutStatus) {
        case READY:
            rolloutManagement.start(rolloutId);
            uiNotification.displaySuccess(i18n.getMessage("message.rollout.started", rolloutName));
            break;
        case PAUSED:
            rolloutManagement.resumeRollout(rolloutId);
            uiNotification.displaySuccess(i18n.getMessage("message.rollout.resumed", rolloutName));
            break;
        default:
            break;
        }
    }

    private void approveRollout(final ProxyRollout rollout) {
        final Window approveWindow = rolloutWindowBuilder.getWindowForApproveRollout(rollout);

        approveWindow.setCaption(i18n.getMessage("caption.approve", i18n.getMessage(ROLLOUT_CAPTION_MSG_KEY)));
        UI.getCurrent().addWindow(approveWindow);
        approveWindow.setVisible(Boolean.TRUE);
    }

    private boolean isRolloutApprovalEnabled() {
        return systemSecurityContext.runAsSystem(() -> tenantConfigManagement
                .getConfigurationValue(TenantConfigurationKey.ROLLOUT_APPROVAL_ENABLED, Boolean.class).getValue());
    }

    private void updateRollout(final ProxyRollout rollout) {
        final Window updateWindow = rolloutWindowBuilder.getWindowForUpdate(rollout);

        updateWindow.setCaption(i18n.getMessage("caption.update", i18n.getMessage(ROLLOUT_CAPTION_MSG_KEY)));
        UI.getCurrent().addWindow(updateWindow);
        updateWindow.setVisible(Boolean.TRUE);
    }

    private void copyRollout(final ProxyRollout rollout) {
        final Window copyWindow = rolloutWindowBuilder.getWindowForCopyRollout(rollout);

        copyWindow.setCaption(i18n.getMessage("caption.copy", i18n.getMessage(ROLLOUT_CAPTION_MSG_KEY)));
        UI.getCurrent().addWindow(copyWindow);
        copyWindow.setVisible(Boolean.TRUE);
    }

    /**
     * Used to show the Rollouts List View in case the currently selected
     * Rollout was deleted.
     *
     * @param deletedSelectedRolloutId
     *            id of the deleted Rollout that is currently selected
     */
    public void onSelectedRolloutDeleted(final long deletedSelectedRolloutId) {
        uiNotification.displayWarning(
                i18n.getMessage("rollout.not.exists", rolloutManagementUIState.getSelectedRolloutName()));

        final EventLayout currentLayout = rolloutManagementUIState.getCurrentLayout().orElse(null);
        if (currentLayout == null || currentLayout != EventLayout.ROLLOUT_LIST) {
            showRolloutListLayout();
        }
    }

    private void showRolloutListLayout() {
        eventBus.publish(CommandTopics.CHANGE_LAYOUT_VISIBILITY, this,
                new LayoutVisibilityEventPayload(VisibilityType.SHOW, EventLayout.ROLLOUT_LIST, EventView.ROLLOUT));
    }

    /**
     * Re-fetches and re-selects currently selected rollout in order to update
     * details layouts.
     *
     */
    public void reselectCurrentRollout() {
        final Long selectedRolloutId = rolloutManagementUIState.getSelectedRolloutId();
        if (selectedRolloutId == null) {
            return;
        }

        final Optional<ProxyRollout> refetchedRollout = mapIdToProxyEntity(selectedRolloutId);
        refetchedRollout.ifPresent(rollout -> {
            getSelectionSupport().sendSelectionChangedEvent(SelectionChangedEventType.ENTITY_SELECTED, rollout);
            rolloutManagementUIState.setSelectedRolloutName(rollout.getName());
        });

        if (!refetchedRollout.isPresent()) {
            onSelectedRolloutDeleted(selectedRolloutId);
        }
    }
}
