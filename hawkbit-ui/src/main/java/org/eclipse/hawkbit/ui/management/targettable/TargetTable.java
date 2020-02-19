/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettable;

import static org.eclipse.hawkbit.ui.management.TargetAssignmentOperations.createAssignmentTab;
import static org.eclipse.hawkbit.ui.management.TargetAssignmentOperations.isMaintenanceWindowValid;
import static org.eclipse.hawkbit.ui.management.TargetAssignmentOperations.saveAllAssignments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.FilterParams;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.event.remote.entity.RemoteEntityEvent;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Tag;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetTag;
import org.eclipse.hawkbit.repository.model.TargetTagAssignmentResult;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.ConfirmationDialog;
import org.eclipse.hawkbit.ui.common.ManagementEntityState;
import org.eclipse.hawkbit.ui.common.UserDetailsFormatter;
import org.eclipse.hawkbit.ui.common.entity.DistributionSetIdName;
import org.eclipse.hawkbit.ui.common.entity.TargetIdName;
import org.eclipse.hawkbit.ui.common.table.AbstractTable;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEventType;
import org.eclipse.hawkbit.ui.dd.criteria.ManagementViewClientCriterion;
import org.eclipse.hawkbit.ui.management.event.ManagementUIEvent;
import org.eclipse.hawkbit.ui.management.event.PinUnpinEvent;
import org.eclipse.hawkbit.ui.management.event.SaveActionWindowEvent;
import org.eclipse.hawkbit.ui.management.event.TargetAddUpdateWindowEvent;
import org.eclipse.hawkbit.ui.management.event.TargetFilterEvent;
import org.eclipse.hawkbit.ui.management.event.TargetTableEvent;
import org.eclipse.hawkbit.ui.management.event.TargetTableEvent.TargetComponentEvent;
import org.eclipse.hawkbit.ui.management.miscs.ActionTypeOptionGroupAssignmentLayout;
import org.eclipse.hawkbit.ui.management.miscs.MaintenanceWindowLayout;
import org.eclipse.hawkbit.ui.management.miscs.WeightLayout;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.management.state.TargetTableFilters;
import org.eclipse.hawkbit.ui.push.CancelTargetAssignmentEventContainer;
import org.eclipse.hawkbit.ui.push.TargetUpdatedEventContainer;
import org.eclipse.hawkbit.ui.utils.AssignInstalledDSTooltipGenerator;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUILabelDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.TableColumn;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.vaadin.addons.lazyquerycontainer.BeanQueryFactory;
import org.vaadin.addons.lazyquerycontainer.LazyQueryContainer;
import org.vaadin.addons.lazyquerycontainer.LazyQueryDefinition;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.google.common.collect.Maps;
import com.vaadin.data.Container;
import com.vaadin.data.Item;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.server.FontAwesome;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.UI;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Concrete implementation of Target table which is displayed on the Deployment
 * View.
 */
public class TargetTable extends AbstractTable<Target> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(TargetTable.class);
    private static final String TARGET_PINNED = "targetPinned";
    private static final int PROPERTY_DEPT = 3;
    private static final String MESSAGE_ASSIGN_TARGET_TO_MULTIPLE_DISTRIBUTIONS = "message.confirm.assign.multiple.entities.multiple.distributions";

    private final transient TargetManagement targetManagement;
    private final transient DistributionSetManagement distributionSetManagement;
    private final transient TargetTagManagement tagManagement;
    private final transient DeploymentManagement deploymentManagement;
    private final transient TenantConfigurationManagement configManagement;
    private final transient SystemSecurityContext systemSecurityContext;

    private final ManagementViewClientCriterion managementViewClientCriterion;
    private final ManagementUIState managementUIState;
    private final UiProperties uiProperties;
    private final ActionTypeOptionGroupAssignmentLayout actionTypeOptionGroupLayout;
    private final MaintenanceWindowLayout maintenanceWindowLayout;
    private final WeightLayout weightLayout;

    private Button targetPinnedBtn;
    private boolean targetPinned;
    private ConfirmationDialog confirmDialog;

    public TargetTable(final UIEventBus eventBus, final VaadinMessageSource i18n, final UINotification notification,
            final TargetManagement targetManagement, final ManagementUIState managementUIState,
            final SpPermissionChecker permChecker, final ManagementViewClientCriterion managementViewClientCriterion,
            final DistributionSetManagement distributionSetManagement, final TargetTagManagement tagManagement,
            final DeploymentManagement deploymentManagement, final TenantConfigurationManagement configManagement,
            final SystemSecurityContext systemSecurityContext, final UiProperties uiProperties) {
        super(eventBus, i18n, notification, permChecker);
        this.targetManagement = targetManagement;
        this.managementViewClientCriterion = managementViewClientCriterion;
        this.managementUIState = managementUIState;
        this.distributionSetManagement = distributionSetManagement;
        this.tagManagement = tagManagement;
        this.deploymentManagement = deploymentManagement;
        this.configManagement = configManagement;
        this.systemSecurityContext = systemSecurityContext;
        this.uiProperties = uiProperties;
        this.actionTypeOptionGroupLayout = new ActionTypeOptionGroupAssignmentLayout(i18n);
        this.maintenanceWindowLayout = new MaintenanceWindowLayout(i18n);
        this.weightLayout = new WeightLayout(i18n, saveButtonToggle(), maintenanceWindowLayout);

        setItemDescriptionGenerator(new AssignInstalledDSTooltipGenerator());
        addNewContainerDS();
        setColumnProperties();
        setDataAvailable(getContainerDataSource().size() != 0);
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onCancelTargetAssignmentEvents(final CancelTargetAssignmentEventContainer eventContainer) {
        // workaround until push is available for action
        // history, re-select
        // the updated target so the action history gets
        // refreshed.
        publishTargetSelectedEntityForRefresh(eventContainer.getEvents().stream());
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onTargetUpdatedEvents(final TargetUpdatedEventContainer eventContainer) {
        @SuppressWarnings("unchecked")
        final List<Long> visibleItemIds = (List<Long>) getVisibleItemIds();
        if (isFilterEnabled()) {
            refreshTargets();
        } else {
            eventContainer.getEvents().stream().filter(event -> visibleItemIds.contains(event.getEntityId()))
                    .filter(Objects::nonNull).forEach(event -> updateVisibleItemOnEvent(event.getEntity()));
        }
        publishTargetSelectedEntityForRefresh(eventContainer.getEvents().stream());
    }

    private void publishTargetSelectedEntityForRefresh(
            final Stream<? extends RemoteEntityEvent<Target>> targetEntityEventStream) {
        targetEntityEventStream.filter(event -> isLastSelectedTarget(event.getEntityId())).filter(Objects::nonNull)
                .findAny().ifPresent(event -> getEventBus().publish(this,
                        new TargetTableEvent(BaseEntityEventType.SELECTED_ENTITY, event.getEntity())));
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final PinUnpinEvent pinUnpinEvent) {
        UI.getCurrent().access(() -> {
            if (pinUnpinEvent == PinUnpinEvent.PIN_DISTRIBUTION) {
                refreshFilter();
                styleTargetTableOnPinning();
            } else if (pinUnpinEvent == PinUnpinEvent.UNPIN_DISTRIBUTION) {
                refreshFilter();
                restoreTargetTableStyle();
            }
        });
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void addOrEditEvent(final TargetAddUpdateWindowEvent targetUIEvent) {
        if (BaseEntityEventType.UPDATED_ENTITY != targetUIEvent.getEventType()) {
            return;
        }
        UI.getCurrent().access(() -> updateTarget(targetUIEvent.getEntity()));
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final TargetFilterEvent filterEvent) {
        UI.getCurrent().access(() -> {
            if (checkFilterEvent(filterEvent)) {
                refreshFilter();
                getEventBus().publish(this, ManagementUIEvent.TARGET_TABLE_FILTER);
            }
        });
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final ManagementUIEvent managementUIEvent) {
        UI.getCurrent().access(() -> {
            if (tableIsFilteredByTagsAndTagWasUnassignedFromTarget(managementUIEvent)
                    || tableIsFilteredByNoTagAndTagWasAssignedToTarget(managementUIEvent)) {
                refreshFilter();
            }
        });
    }

    private boolean tableIsFilteredByTagsAndTagWasUnassignedFromTarget(final ManagementUIEvent managementUIEvent) {
        return managementUIEvent == ManagementUIEvent.UNASSIGN_TARGET_TAG && isFilteredByTags();
    }

    private boolean tableIsFilteredByNoTagAndTagWasAssignedToTarget(final ManagementUIEvent managementUIEvent) {
        return managementUIEvent == ManagementUIEvent.ASSIGN_TARGET_TAG && isFilteredByNoTag();
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final SaveActionWindowEvent event) {
        if (event == SaveActionWindowEvent.SAVED_ASSIGNMENTS) {
            refreshContainer();
        }
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final TargetTableEvent event) {
        onBaseEntityEvent(event);
    }

    @Override
    protected String getTableId() {
        return UIComponentIdProvider.TARGET_TABLE_ID;
    }

    @Override
    protected Container createContainer() {
        // ADD all the filters to the query config
        final Map<String, Object> queryConfig = prepareQueryConfigFilters();
        // Create TargetBeanQuery factory with the query config.
        final BeanQueryFactory<TargetBeanQuery> targetQF = new BeanQueryFactory<>(TargetBeanQuery.class);
        targetQF.setQueryConfiguration(queryConfig);
        // create lazy query container with lazy defination and query
        final LazyQueryContainer targetTableContainer = new LazyQueryContainer(
                new LazyQueryDefinition(true, SPUIDefinitions.PAGE_SIZE, SPUILabelDefinitions.VAR_ID), targetQF);
        targetTableContainer.getQueryView().getQueryDefinition().setMaxNestedPropertyDepth(PROPERTY_DEPT);
        return targetTableContainer;
    }

    @Override
    protected void addContainerProperties(final Container container) {
        final LazyQueryContainer targetTableContainer = (LazyQueryContainer) container;
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.VAR_CONT_ID, String.class, "", false, false);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.VAR_NAME, String.class, "", false, true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.VAR_TARGET_STATUS, TargetUpdateStatus.class,
                TargetUpdateStatus.UNKNOWN, false, false);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.ASSIGNED_DISTRIBUTION_ID, Long.class, null,
                false, false);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.INSTALLED_DISTRIBUTION_ID, Long.class, null,
                false, false);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.ASSIGNED_DISTRIBUTION_NAME_VER, String.class, "",
                false, true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.INSTALLED_DISTRIBUTION_NAME_VER, String.class,
                "", false, true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.LAST_QUERY_DATE, Date.class, null, false, false);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.VAR_CREATED_BY, String.class, null, false, true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.VAR_LAST_MODIFIED_BY, String.class, null, false,
                true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.VAR_CREATED_DATE, String.class, null, false,
                true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.VAR_LAST_MODIFIED_DATE, String.class, null,
                false, true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.VAR_POLL_STATUS_TOOL_TIP, String.class, null,
                false, true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.VAR_DESC, String.class, "", false, true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.ASSIGN_DIST_SET, DistributionSet.class, null,
                false, true);
        targetTableContainer.addContainerProperty(SPUILabelDefinitions.INSTALL_DIST_SET, DistributionSet.class, null,
                false, true);
    }

    @Override
    protected void addCustomGeneratedColumns() {
        addGeneratedColumn(SPUIDefinitions.TARGET_STATUS_PIN_TOGGLE_ICON,
                (source, itemId, columnId) -> getTagetPinButton(itemId));
        addGeneratedColumn(SPUIDefinitions.TARGET_STATUS_POLL_TIME,
                (source, itemId, columnId) -> getTargetPollTime(itemId));
    }

    @Override
    protected Object getItemIdToSelect() {
        return managementUIState.getSelectedTargetId().isEmpty() ? null : managementUIState.getSelectedTargetId();
    }

    @Override
    protected void publishSelectedEntityEvent(final Target selectedLastEntity) {
        getEventBus().publish(this, new TargetTableEvent(BaseEntityEventType.SELECTED_ENTITY, selectedLastEntity));
    }

    @Override
    protected void setLastSelectedEntityId(final Long selectedLastEntityId) {
        managementUIState.setLastSelectedTargetId(selectedLastEntityId);
    }

    @Override
    protected Optional<Target> findEntityByTableValue(final Long lastSelectedId) {
        return targetManagement.get(lastSelectedId);
    }

    @Override
    protected void setManagementEntityStateValues(final Set<Long> values, final Long lastId) {
        managementUIState.setSelectedTargetId(values);
        managementUIState.setLastSelectedTargetId(lastId);
    }

    @Override
    protected ManagementEntityState getManagementEntityState() {
        return null;
    }

    @Override
    protected boolean isMaximized() {
        return managementUIState.isTargetTableMaximized();
    }

    @Override
    protected List<TableColumn> getTableVisibleColumns() {
        final List<TableColumn> columnList = super.getTableVisibleColumns();
        if (!isMaximized()) {
            columnList.add(new TableColumn(SPUIDefinitions.TARGET_STATUS_POLL_TIME, "", 0.0F));
            columnList.add(new TableColumn(SPUIDefinitions.TARGET_STATUS_PIN_TOGGLE_ICON, "", 0.0F));
        }
        return columnList;
    }

    @Override
    public AcceptCriterion getDropAcceptCriterion() {
        return managementViewClientCriterion;
    }

    private Map<String, Object> prepareQueryConfigFilters() {
        final Map<String, Object> queryConfig = Maps.newHashMapWithExpectedSize(7);
        managementUIState.getTargetTableFilters().getSearchText()
                .ifPresent(value -> queryConfig.put(SPUIDefinitions.FILTER_BY_TEXT, value));
        managementUIState.getTargetTableFilters().getDistributionSet()
                .ifPresent(value -> queryConfig.put(SPUIDefinitions.FILTER_BY_DISTRIBUTION, value.getId()));
        managementUIState.getTargetTableFilters().getPinnedDistId()
                .ifPresent(value -> queryConfig.put(SPUIDefinitions.ORDER_BY_DISTRIBUTION, value));
        managementUIState.getTargetTableFilters().getTargetFilterQuery()
                .ifPresent(value -> queryConfig.put(SPUIDefinitions.FILTER_BY_TARGET_FILTER_QUERY, value));

        queryConfig.put(SPUIDefinitions.FILTER_BY_NO_TAG, managementUIState.getTargetTableFilters().isNoTagSelected());

        if (isFilteredByTags()) {
            final List<String> list = new ArrayList<>(managementUIState.getTargetTableFilters().getClickedTargetTags());
            queryConfig.put(SPUIDefinitions.FILTER_BY_TAG, list.toArray(new String[list.size()]));
        }
        if (isFilteredByStatus()) {
            final List<TargetUpdateStatus> statusList = managementUIState.getTargetTableFilters()
                    .getClickedStatusTargetTags();
            queryConfig.put(SPUIDefinitions.FILTER_BY_STATUS, statusList);
        }
        if (managementUIState.getTargetTableFilters().isOverdueFilterEnabled()) {
            queryConfig.put(SPUIDefinitions.FILTER_BY_OVERDUE_STATE, Boolean.TRUE);
        }
        return queryConfig;
    }

    private Label getTargetPollTime(final Object itemId) {
        final Label statusLabel = new Label();
        statusLabel.addStyleName(ValoTheme.LABEL_SMALL);
        statusLabel.setHeightUndefined();
        statusLabel.setContentMode(ContentMode.HTML);
        final String pollStatusToolTip = (String) getContainerDataSource().getItem(itemId)
                .getItemProperty(SPUILabelDefinitions.VAR_POLL_STATUS_TOOL_TIP).getValue();
        if (StringUtils.hasText(pollStatusToolTip)) {
            statusLabel.setValue(FontAwesome.EXCLAMATION_CIRCLE.getHtml());
            statusLabel.setDescription(pollStatusToolTip);
        } else {
            statusLabel.setValue(FontAwesome.CLOCK_O.getHtml());
            statusLabel.setDescription(getI18n().getMessage(UIMessageIdProvider.TOOLTIP_IN_TIME));
        }

        return statusLabel;
    }

    private Button getTagetPinButton(final Object itemId) {
        final Button pinBtn = new Button();
        final String controllerId = (String) getContainerDataSource().getItem(itemId)
                .getItemProperty(SPUILabelDefinitions.VAR_CONT_ID).getValue();
        final TargetIdName pinnedTarget = new TargetIdName((Long) itemId, controllerId);
        final StringBuilder pinBtnStyle = new StringBuilder(ValoTheme.BUTTON_BORDERLESS_COLORED);
        pinBtnStyle.append(' ');
        pinBtnStyle.append(ValoTheme.BUTTON_SMALL);
        pinBtnStyle.append(' ');
        pinBtnStyle.append(ValoTheme.BUTTON_ICON_ONLY);
        pinBtn.setStyleName(pinBtnStyle.toString());
        pinBtn.setHeightUndefined();
        pinBtn.setData(pinnedTarget);
        pinBtn.setId(UIComponentIdProvider.TARGET_PIN_ICON + controllerId);
        pinBtn.addClickListener(this::addPinClickListener);
        pinBtn.setDescription(getI18n().getMessage(UIMessageIdProvider.TOOLTIP_TARGET_PIN));
        if (isPinned(pinnedTarget)) {
            pinBtn.addStyleName(TARGET_PINNED);
            targetPinned = Boolean.TRUE;
            targetPinnedBtn = pinBtn;
            getEventBus().publish(this, PinUnpinEvent.PIN_TARGET);
        }
        pinBtn.addStyleName(SPUIStyleDefinitions.TARGET_STATUS_PIN_TOGGLE);
        HawkbitCommonUtil.applyStatusLblStyle(this, pinBtn, itemId);
        return pinBtn;
    }

    private boolean isPinned(final TargetIdName target) {
        return managementUIState.getDistributionTableFilters().getPinnedTarget()
                .map(pinnedTarget -> pinnedTarget.equals(target)).orElse(false);
    }

    private void addPinClickListener(final ClickEvent event) {
        checkifAlreadyPinned(event.getButton());
        if (targetPinned) {
            pinTarget(event.getButton());
        } else {
            unPinTarget(event.getButton());
        }
    }

    private void checkifAlreadyPinned(final Button eventBtn) {
        final TargetIdName newPinnedTargetItemId = (TargetIdName) eventBtn.getData();
        final TargetIdName targetId = managementUIState.getDistributionTableFilters().getPinnedTarget().orElse(null);

        if (targetId == null) {
            targetPinned = !targetPinned;
            managementUIState.getDistributionTableFilters().setPinnedTarget(newPinnedTargetItemId);
        } else if (targetId.equals(newPinnedTargetItemId)) {
            targetPinned = Boolean.FALSE;
        } else {
            targetPinned = true;
            managementUIState.getDistributionTableFilters().setPinnedTarget(newPinnedTargetItemId);
            if (null != targetPinnedBtn) {
                resetPinStyle(targetPinnedBtn);
            }
        }
        targetPinnedBtn = eventBtn;
    }

    private void pinTarget(final Button eventBtn) {
        /* if distribution set is pinned ,unpin target if pinned */
        managementUIState.getTargetTableFilters().setPinnedDistId(null);
        /* on unpin of target dist table should refresh Dist table restyle */
        getEventBus().publish(this, PinUnpinEvent.PIN_TARGET);
        /* change target table styling */
        styleTargetTable();
        eventBtn.addStyleName(TARGET_PINNED);
        targetPinned = Boolean.FALSE;
    }

    private void unPinTarget(final Button eventBtn) {
        managementUIState.getDistributionTableFilters().setPinnedTarget(null);
        getEventBus().publish(this, PinUnpinEvent.UNPIN_TARGET);
        resetPinStyle(eventBtn);
    }

    private void resetPinStyle(final Button pinBtn) {
        pinBtn.removeStyleName(TARGET_PINNED);
        pinBtn.addStyleName(SPUIStyleDefinitions.TARGET_STATUS_PIN_TOGGLE);
        final TargetIdName targetIdname = (TargetIdName) pinBtn.getData();
        HawkbitCommonUtil.applyStatusLblStyle(this, pinBtn, targetIdname.getTargetId());
    }

    private void styleTargetTable() {
        setCellStyleGenerator((source, itemId, propertyId) -> null);
    }

    @Override
    protected void onDropEventFromTable(final DragAndDropEvent event) {
        assignDsToTarget(event);
    }

    @Override
    protected void onDropEventFromWrapper(final DragAndDropEvent event) {
        if (isNoTagAssigned(event)) {
            tagAssignment(event);
        }
    }

    private Boolean isNoTagAssigned(final DragAndDropEvent event) {
        final String tagName = ((DragAndDropWrapper) (event.getTransferable().getSourceComponent())).getData()
                .toString();
        if (tagName.equals(getI18n().getMessage(UIMessageIdProvider.CAPTION_TARGET_TAG))) {
            getNotification().displayValidationError(getI18n().getMessage("message.tag.cannot.be.assigned",
                    getI18n().getMessage("label.no.tag.assigned")));
            return false;
        }
        return true;
    }

    private void tagAssignment(final DragAndDropEvent event) {
        final List<Long> targetList = new ArrayList<>(getDraggedTargetList(event));

        final String targTagName = HawkbitCommonUtil.removePrefix(event.getTransferable().getSourceComponent().getId(),
                SPUIDefinitions.TARGET_TAG_ID_PREFIXS);
        if (targetList.isEmpty()) {
            final String actionDidNotWork = getI18n().getMessage("message.action.did.not.work");
            getNotification().displayValidationError(actionDidNotWork);
            return;
        }

        final TargetTagAssignmentResult result = toggleTagAssignment(targetList, targTagName);

        final List<String> tagsClickedList = managementUIState.getTargetTableFilters().getClickedTargetTags();
        if (result.getUnassigned() >= 1 && !tagsClickedList.isEmpty()) {
            refreshFilter();
        }
    }

    /**
     * Toggles {@link TargetTag} assignment to given target ids by means that if
     * some (or all) of the targets in the list have the {@link Tag} not yet
     * assigned, they will be. Only if all of them have the tag already assigned
     * they will be removed instead. Additionally a success popup is shown.
     *
     * @param targetIds
     *            to toggle for
     * @param targTagName
     *            to toggle
     * @return TagAssigmentResult with all meta data of the assignment outcome.
     */
    public TargetTagAssignmentResult toggleTagAssignment(final Collection<Long> targetIds, final String targTagName) {
        final List<Target> targets = targetManagement.get(targetIds);
        if (targets.isEmpty()) {
            getNotification().displayWarning(getI18n().getMessage("targets.not.exists"));
            return new TargetTagAssignmentResult(0, Collections.emptyList(), Collections.emptyList(), null);
        }

        final Optional<TargetTag> tag = tagManagement.getByName(targTagName);
        if (!tag.isPresent()) {
            getNotification().displayWarning(getI18n().getMessage("targettag.not.exists", targTagName));
            return new TargetTagAssignmentResult(0, Collections.emptyList(), Collections.emptyList(), null);
        }

        final List<String> controllerIds = targets.stream().map(Target::getControllerId).collect(Collectors.toList());
        final TargetTagAssignmentResult result = targetManagement.toggleTagAssignment(controllerIds, targTagName);

        getNotification().displaySuccess(HawkbitCommonUtil.createAssignmentMessage(targTagName, result, getI18n()));

        return result;
    }

    @Override
    protected boolean validateDragAndDropWrapper(final DragAndDropWrapper wrapperSource) {
        final String tagName = HawkbitCommonUtil.removePrefix(wrapperSource.getId(),
                SPUIDefinitions.TARGET_TAG_ID_PREFIXS);
        if (wrapperSource.getId().startsWith(SPUIDefinitions.TARGET_TAG_ID_PREFIXS)) {
            if (getI18n().getMessage("label.no.tag").equals(tagName)) {
                getNotification()
                        .displayValidationError(getI18n().getMessage(UIMessageIdProvider.MESSAGE_ACTION_NOT_ALLOWED));
                return false;
            }
        } else {
            getNotification()
                    .displayValidationError(getI18n().getMessage(UIMessageIdProvider.MESSAGE_ACTION_NOT_ALLOWED));
            return false;
        }

        return true;
    }

    @Override
    protected String getDropTableId() {
        return UIComponentIdProvider.DIST_TABLE_ID;
    }

    @Override
    protected List<String> hasMissingPermissionsForDrop() {
        return getPermChecker().hasUpdateTargetPermission() ? Collections.emptyList()
                : Arrays.asList(SpPermission.UPDATE_TARGET);
    }

    /**
     * To update target details in the table.
     *
     * @param updatedTarget
     *            as reference
     */
    @SuppressWarnings("unchecked")
    public void updateTarget(final Target updatedTarget) {
        if (updatedTarget != null) {
            final Item item = getItem(updatedTarget.getId());
            // TO DO update SPUILabelDefinitions.ASSIGNED_DISTRIBUTION_NAME_VER
            // &
            // SPUILabelDefinitions.ASSIGNED_DISTRIBUTION_NAME_VER

            /*
             * Update the status which will trigger the value change lister
             * registered for the target update status. That listener will
             * update the new status icon showing for this target in the table.
             */
            item.getItemProperty(SPUILabelDefinitions.VAR_TARGET_STATUS).setValue(updatedTarget.getUpdateStatus());
            /*
             * Update the last query which will trigger the value change lister
             * registered for the target last query column. That listener will
             * update the latest query date for this target in the tooltip.
             */
            item.getItemProperty(SPUILabelDefinitions.LAST_QUERY_DATE).setValue(updatedTarget.getLastTargetQuery());

            item.getItemProperty(SPUILabelDefinitions.VAR_LAST_MODIFIED_BY)
                    .setValue(UserDetailsFormatter.loadAndFormatLastModifiedBy(updatedTarget));
            item.getItemProperty(SPUILabelDefinitions.VAR_LAST_MODIFIED_DATE)
                    .setValue(SPDateTimeUtil.getFormattedDate(updatedTarget.getLastModifiedAt()));
            item.getItemProperty(SPUILabelDefinitions.VAR_DESC).setValue(updatedTarget.getDescription());

            /* Update the new Name, Description and poll date */
            item.getItemProperty(SPUILabelDefinitions.VAR_NAME).setValue(updatedTarget.getName());
        }
    }

    private static boolean checkFilterEvent(final TargetFilterEvent filterEvent) {
        return isNormalFilter(filterEvent) || isRemoveFilterEvent(filterEvent) || isStatusFilterEvent(filterEvent);
    }

    private static boolean isStatusFilterEvent(final TargetFilterEvent filterEvent) {
        return filterEvent == TargetFilterEvent.FILTER_BY_STATUS
                || filterEvent == TargetFilterEvent.REMOVE_FILTER_BY_STATUS;
    }

    private static boolean isRemoveFilterEvent(final TargetFilterEvent filterEvent) {
        return filterEvent == TargetFilterEvent.REMOVE_FILTER_BY_TEXT
                || filterEvent == TargetFilterEvent.REMOVE_FILTER_BY_TAG
                || filterEvent == TargetFilterEvent.REMOVE_FILTER_BY_DISTRIBUTION
                || filterEvent == TargetFilterEvent.REMOVE_FILTER_BY_TARGET_FILTER_QUERY;
    }

    private static boolean isNormalFilter(final TargetFilterEvent filterEvent) {
        return filterEvent == TargetFilterEvent.FILTER_BY_TEXT || filterEvent == TargetFilterEvent.FILTER_BY_TAG
                || filterEvent == TargetFilterEvent.FILTER_BY_DISTRIBUTION
                || filterEvent == TargetFilterEvent.FILTER_BY_TARGET_FILTER_QUERY;
    }

    private String getTargetTableStyle(final Long assignedDistributionSetId, final Long installedDistributionSetId) {
        return managementUIState.getTargetTableFilters().getPinnedDistId().map(distPinned -> {
            if (distPinned.equals(installedDistributionSetId)) {
                return SPUIDefinitions.HIGHLIGHT_GREEN;
            }
            if (distPinned.equals(assignedDistributionSetId)) {
                return SPUIDefinitions.HIGHLIGHT_ORANGE;
            }
            return null;
        }).orElse(null);
    }

    private String createTargetTableStyle(final Object itemId, final Object propertyId) {
        if (propertyId == null) {
            final Item item = getItem(itemId);
            final Long assignedDistributionSetId = (Long) item
                    .getItemProperty(SPUILabelDefinitions.ASSIGNED_DISTRIBUTION_ID).getValue();
            final Long installedDistributionSetId = (Long) item
                    .getItemProperty(SPUILabelDefinitions.INSTALLED_DISTRIBUTION_ID).getValue();
            return getTargetTableStyle(assignedDistributionSetId, installedDistributionSetId);
        }
        return null;
    }

    private void styleTargetTableOnPinning() {
        setCellStyleGenerator(new Table.CellStyleGenerator() {
            private static final long serialVersionUID = 1L;

            @Override
            public String getStyle(final Table source, final Object itemId, final Object propertyId) {
                return createTargetTableStyle(itemId, propertyId);
            }
        });
    }

    private void restoreTargetTableStyle() {
        setCellStyleGenerator(new Table.CellStyleGenerator() {
            private static final long serialVersionUID = 1L;

            @Override
            public String getStyle(final Table source, final Object itemId, final Object propertyId) {
                return null;
            }
        });
    }

    private void refreshTargets() {
        final LazyQueryContainer targetContainer = (LazyQueryContainer) getContainerDataSource();
        final int size = targetContainer.size();
        if (size < SPUIDefinitions.MAX_TABLE_ENTRIES) {
            super.refreshContainer();
        } else {
            // If table is not refreshed , explicitly target total count and
            // truncated count has to be updated
            resetTargetCountDetails();
        }

        if (size != 0) {
            setData(getI18n().getMessage(UIMessageIdProvider.MESSAGE_DATA_AVAILABLE));
        }
        getEventBus().publish(this, new TargetTableEvent(TargetComponentEvent.REFRESH_TARGETS));
    }

    @Override
    public void refreshContainer() {
        super.refreshContainer();
        getEventBus().publish(this, new TargetTableEvent(TargetComponentEvent.REFRESH_TARGETS));
    }

    @SuppressWarnings("unchecked")
    private void updateVisibleItemOnEvent(final Target target) {
        final Long targetId = target.getId();

        final LazyQueryContainer targetContainer = (LazyQueryContainer) getContainerDataSource();
        final Item item = targetContainer.getItem(targetId);

        item.getItemProperty(SPUILabelDefinitions.VAR_TARGET_STATUS).setValue(target.getUpdateStatus());
        item.getItemProperty(SPUILabelDefinitions.VAR_NAME).setValue(target.getName());
        item.getItemProperty(SPUILabelDefinitions.VAR_POLL_STATUS_TOOL_TIP)
                .setValue(HawkbitCommonUtil.getPollStatusToolTip(target.getPollStatus(), getI18n()));
    }

    private boolean isLastSelectedTarget(final Long targetId) {
        final Optional<Long> currentTargetId = managementUIState.getLastSelectedTargetId();
        return currentTargetId.isPresent() && currentTargetId.get().equals(targetId);
    }

    private boolean isFilterEnabled() {
        final TargetTableFilters targetTableFilters = managementUIState.getTargetTableFilters();
        return targetTableFilters.getSearchText().isPresent() || !targetTableFilters.getClickedTargetTags().isEmpty()
                || !targetTableFilters.getClickedStatusTargetTags().isEmpty()
                || targetTableFilters.getTargetFilterQuery().isPresent();
    }

    /**
     * Select all rows in the table.
     */
    @Override
    public void selectAll() {
        // As Vaadin Table only returns the current ItemIds which are visible
        // you don't need to search explicit for them.
        setValue(getItemIds());
    }

    @Override
    protected void setDataAvailable(final boolean available) {
        managementUIState.setNoDataAvailableTarget(!available);
    }

    /**
     * Set total target count and count of targets truncated in target table.
     */
    private void resetTargetCountDetails() {
        final long totalTargetsCount = getTotalTargetsCount();
        managementUIState.setTargetsCountAll(totalTargetsCount);

        final boolean noTagClicked = managementUIState.getTargetTableFilters().isNoTagSelected();
        final Long distributionId = managementUIState.getTargetTableFilters().getDistributionSet()
                .map(DistributionSetIdName::getId).orElse(null);
        final Long pinnedDistId = managementUIState.getTargetTableFilters().getPinnedDistId().orElse(null);
        final String searchText = managementUIState.getTargetTableFilters().getSearchText().map(text -> {
            if (StringUtils.isEmpty(text)) {
                return null;
            }
            return String.format("%%%s%%", text);
        }).orElse(null);

        String[] targetTags = null;
        if (isFilteredByTags()) {
            targetTags = managementUIState.getTargetTableFilters().getClickedTargetTags().toArray(new String[0]);
        }

        Collection<TargetUpdateStatus> status = null;
        if (isFilteredByStatus()) {
            status = managementUIState.getTargetTableFilters().getClickedStatusTargetTags();
        }

        Boolean overdueState = null;
        if (managementUIState.getTargetTableFilters().isOverdueFilterEnabled()) {
            overdueState = managementUIState.getTargetTableFilters().isOverdueFilterEnabled();
        }

        final long size = getTargetsCountWithFilter(totalTargetsCount, pinnedDistId,
                new FilterParams(status, overdueState, searchText, distributionId, noTagClicked, targetTags));

        if (size > SPUIDefinitions.MAX_TABLE_ENTRIES) {
            managementUIState.setTargetsTruncated(size - SPUIDefinitions.MAX_TABLE_ENTRIES);
        }
    }

    private long getTargetsCountWithFilter(final long totalTargetsCount, final Long pinnedDistId,
            final FilterParams filterParams) {
        final Optional<Long> query = managementUIState.getTargetTableFilters().getTargetFilterQuery();

        final long size;
        if (query.isPresent()) {
            size = targetManagement.countByTargetFilterQuery(query.get());
        } else if (noFilterSelected(filterParams.getFilterByStatus(), pinnedDistId,
                filterParams.getSelectTargetWithNoTag(), filterParams.getFilterByTagNames(),
                filterParams.getFilterBySearchText())) {
            size = totalTargetsCount;
        } else {
            size = targetManagement.countByFilters(filterParams.getFilterByStatus(), filterParams.getOverdueState(),
                    filterParams.getFilterBySearchText(), filterParams.getFilterByDistributionId(),
                    filterParams.getSelectTargetWithNoTag(), filterParams.getFilterByTagNames());
        }
        return size;
    }

    private static boolean noFilterSelected(final Collection<TargetUpdateStatus> status, final Long distributionId,
            final Boolean noTagClicked, final String[] targetTags, final String searchText) {
        return CollectionUtils.isEmpty(status) && distributionId == null && StringUtils.isEmpty(searchText)
                && !isTagSelected(targetTags, noTagClicked);
    }

    private static Boolean isTagSelected(final String[] targetTags, final Boolean noTagClicked) {
        return targetTags == null && !noTagClicked;
    }

    private long getTotalTargetsCount() {
        return targetManagement.count();
    }

    private boolean isFilteredByStatus() {
        return !managementUIState.getTargetTableFilters().getClickedStatusTargetTags().isEmpty();
    }

    private boolean isFilteredByTags() {
        return !managementUIState.getTargetTableFilters().getClickedTargetTags().isEmpty();
    }

    private boolean isFilteredByNoTag() {
        return managementUIState.getTargetTableFilters().isNoTagSelected();
    }

    private void assignDsToTarget(final DragAndDropEvent event) {
        final TableTransferable transferable = (TableTransferable) event.getTransferable();
        final AbstractTable<?> source = (AbstractTable<?>) transferable.getSourceComponent();

        final Set<Long> dsIds = filterDistributionSetsToAssign(source.getSelectedEntitiesByTransferable(transferable));
        if (dsIds.isEmpty()) {
            getNotification().displayWarning(getI18n().getMessage(DISTRIBUTIONSET_NOT_EXISTS));
            return;
        }

        selectDraggedEntities(source, dsIds);
        final AbstractSelectTargetDetails dropData = (AbstractSelectTargetDetails) event.getTargetDetails();
        final Object targetItemId = dropData.getItemIdOver();
        LOG.debug("Drop target: {} ", targetItemId);
        if (targetItemId == null) {
            getNotification().displayWarning(getI18n().getMessage(TARGETS_NOT_EXISTS, ""));
            return;
        }

        final Long targetId = (Long) targetItemId;
        selectDroppedEntities(targetId);
        final Optional<Target> target = targetManagement.get(targetId);
        if (!target.isPresent()) {
            getNotification().displayWarning(getI18n().getMessage(TARGETS_NOT_EXISTS, ""));
            return;
        }

        final List<DistributionSet> distributionSets = distributionSetManagement.get(dsIds);
        if (distributionSets.isEmpty()) {
            getNotification().displayWarning(getI18n().getMessage(DISTRIBUTIONSET_NOT_EXISTS));
            return;
        }
        openConfirmationWindowForAssignments(target.get(), distributionSets);
    }

    private Set<Long> filterDistributionSetsToAssign(final Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }
        if (isMultiAssignmentEnabled()) {
            return new HashSet<>(ids);
        }
        return Collections.singleton(ids.iterator().next());
    }

    private void openConfirmationWindowForAssignments(final Target target,
            final List<DistributionSet> distributionSets) {

        final String confirmationMessage = getConfirmationMessage(target, distributionSets);
        final String caption = getI18n().getMessage(CAPTION_ENTITY_ASSIGN_ACTION_CONFIRMBOX);
        final String okLabel = getI18n().getMessage(UIMessageIdProvider.BUTTON_OK);
        final String cancelLabel = getI18n().getMessage(UIMessageIdProvider.BUTTON_CANCEL);

        confirmDialog = new ConfirmationDialog(caption, confirmationMessage, okLabel, cancelLabel, ok -> {
            if (ok && isMaintenanceWindowValid(maintenanceWindowLayout, getNotification())) {
                saveAllAssignments(Collections.singletonList(target), distributionSets, managementUIState,
                        actionTypeOptionGroupLayout, maintenanceWindowLayout, deploymentManagement, getNotification(),
                        getEventBus(), getI18n(), this, configManagement, systemSecurityContext,
                        weightLayout.getWeightField());
            }
        }, createAssignmentTab(actionTypeOptionGroupLayout, maintenanceWindowLayout, saveButtonToggle(), getI18n(),
                uiProperties, isMultiAssignmentEnabled(), weightLayout, configManagement),
                UIComponentIdProvider.DIST_SET_TO_TARGET_ASSIGNMENT_CONFIRM_ID);

        if (isMultiAssignmentEnabled()) {
            saveButtonToggle().accept(!isMultiAssignmentEnabled());
        }
        UI.getCurrent().addWindow(confirmDialog.getWindow());
        confirmDialog.getWindow().bringToFront();
    }

    private String getConfirmationMessage(final Target target, final List<DistributionSet> distributionSets) {
        final String targetName = target.getName();
        final int dsCount = distributionSets.size();
        if (dsCount > 1) {
            return getI18n().getMessage(MESSAGE_ASSIGN_TARGET_TO_MULTIPLE_DISTRIBUTIONS, dsCount, "target", targetName);
        }
        return getI18n().getMessage(MESSAGE_CONFIRM_ASSIGN_ENTITY, distributionSets.get(0).getName(), "target",
                targetName);
    }

    private Consumer<Boolean> saveButtonToggle() {
        return isEnabled -> confirmDialog.getOkButton().setEnabled(isEnabled);
    }

    @Override
    protected void handleOkDelete(final List<Long> entitiesToDelete) {
        targetManagement.delete(entitiesToDelete);
        getEventBus().publish(this, new TargetTableEvent(BaseEntityEventType.REMOVE_ENTITY, entitiesToDelete));
        getNotification().displaySuccess(getI18n().getMessage("message.delete.success",
                entitiesToDelete.size() + " " + getI18n().getMessage("caption.target") + "(s)"));
        managementUIState.getDistributionTableFilters().getPinnedTarget().ifPresent(this::unPinDeletedTarget);
        managementUIState.getSelectedTargetId().clear();
    }

    private void unPinDeletedTarget(final TargetIdName pinnedTarget) {
        final Set<Long> deletedTargetIds = managementUIState.getSelectedTargetId();
        if (deletedTargetIds.contains(pinnedTarget.getTargetId())) {
            managementUIState.getDistributionTableFilters().setPinnedTarget(null);
            getEventBus().publish(this, PinUnpinEvent.UNPIN_TARGET);
        }
    }

    @Override
    protected String getEntityId(final Object itemId) {
        final String targetId = String.valueOf(
                getContainerDataSource().getItem(itemId).getItemProperty(SPUILabelDefinitions.VAR_ID).getValue());
        return "target." + targetId;
    }

    @Override
    protected Set<Long> getSelectedEntities() {
        return managementUIState.getSelectedTargetId();
    }

    @Override
    protected String getEntityType() {
        return getI18n().getMessage("caption.target");
    }

    @Override
    protected boolean hasDeletePermission() {
        return getPermChecker().hasDeleteTargetPermission();
    }

    @Override
    protected String getDeletedEntityName(final Long entityId) {
        final Optional<Target> target = targetManagement.get(entityId);
        if (target.isPresent()) {
            return target.get().getName();
        }
        return "";
    }

    private boolean isMultiAssignmentEnabled() {
        return systemSecurityContext.runAsSystem(() -> configManagement
                .getConfigurationValue(TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED, Boolean.class).getValue());
    }
}
