/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout.rollout;

import static org.eclipse.hawkbit.ui.rollout.DistributionBarHelper.getTooltip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RepositoryProperties;
import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus.Status;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow;
import org.eclipse.hawkbit.ui.common.ConfirmationDialog;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.customrenderers.client.renderers.RolloutRendererData;
import org.eclipse.hawkbit.ui.customrenderers.renderers.AbstractGridButtonConverter;
import org.eclipse.hawkbit.ui.customrenderers.renderers.AbstractHtmlLabelConverter;
import org.eclipse.hawkbit.ui.customrenderers.renderers.GridButtonRenderer;
import org.eclipse.hawkbit.ui.customrenderers.renderers.HtmlLabelRenderer;
import org.eclipse.hawkbit.ui.customrenderers.renderers.RolloutRenderer;
import org.eclipse.hawkbit.ui.management.event.ManagementUIEvent;
import org.eclipse.hawkbit.ui.push.RolloutChangeEventContainer;
import org.eclipse.hawkbit.ui.push.RolloutDeletedEventContainer;
import org.eclipse.hawkbit.ui.push.event.RolloutChangedEvent;
import org.eclipse.hawkbit.ui.rollout.DistributionBarHelper;
import org.eclipse.hawkbit.ui.rollout.StatusFontIcon;
import org.eclipse.hawkbit.ui.rollout.event.RolloutEvent;
import org.eclipse.hawkbit.ui.rollout.state.RolloutUIState;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUILabelDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.addons.lazyquerycontainer.BeanQueryFactory;
import org.vaadin.addons.lazyquerycontainer.LazyQueryContainer;
import org.vaadin.addons.lazyquerycontainer.LazyQueryDefinition;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.data.Item;
import com.vaadin.data.util.GeneratedPropertyContainer;
import com.vaadin.data.util.PropertyValueGenerator;
import com.vaadin.data.util.converter.Converter;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.UI;
import com.vaadin.ui.renderers.ClickableRenderer.RendererClickEvent;
import com.vaadin.ui.renderers.HtmlRenderer;

/**
 * Rollout list grid component.
 */
public class RolloutListGrid extends AbstractGrid<LazyQueryContainer> {

    private static final long serialVersionUID = 1L;

    private static final String ROLLOUT_RENDERER_DATA = "rolloutRendererData";

    private static final String VIRT_PROP_APPROVE = "approve";
    private static final String VIRT_PROP_RUN = "run";
    private static final String VIRT_PROP_PAUSE = "pause";
    private static final String VIRT_PROP_UPDATE = "update";
    private static final String VIRT_PROP_COPY = "copy";
    private static final String VIRT_PROP_DELETE = "delete";
    private static final String STATUS_ICON_DOWNLOAD_ONLY = "statusIconDownloadOnly";
    private static final String STATUS_ICON_SOFT = "statusIconSoft";
    private static final String STATUS_ICON_FORCED = "statusIconForced";
    private static final String PROP_TYPE = "actionType";

    private final transient RolloutManagement rolloutManagement;

    private final transient RolloutGroupManagement rolloutGroupManagement;

    private final transient TenantConfigurationManagement tenantConfigManagement;

    private final transient SystemSecurityContext systemSecurityContext;

    private final AddUpdateRolloutWindowLayout addUpdateRolloutWindow;

    private final UINotification uiNotification;

    private final RolloutUIState rolloutUIState;

    private final AlignCellStyleGenerator alignGenerator;

    private static final List<RolloutStatus> DELETE_COPY_BUTTON_ENABLED = Arrays.asList(RolloutStatus.CREATING,
            RolloutStatus.ERROR_CREATING, RolloutStatus.ERROR_STARTING, RolloutStatus.PAUSED, RolloutStatus.READY,
            RolloutStatus.RUNNING, RolloutStatus.STARTING, RolloutStatus.STOPPED, RolloutStatus.FINISHED,
            RolloutStatus.WAITING_FOR_APPROVAL, RolloutStatus.APPROVAL_DENIED);

    private static final List<RolloutStatus> UPDATE_BUTTON_ENABLED = Arrays.asList(RolloutStatus.CREATING,
            RolloutStatus.ERROR_CREATING, RolloutStatus.ERROR_STARTING, RolloutStatus.PAUSED, RolloutStatus.READY,
            RolloutStatus.RUNNING, RolloutStatus.STARTING, RolloutStatus.STOPPED);

    private static final List<RolloutStatus> PAUSE_BUTTON_ENABLED = Collections.singletonList(RolloutStatus.RUNNING);

    private static final List<RolloutStatus> RUN_BUTTON_ENABLED = Arrays.asList(RolloutStatus.READY,
            RolloutStatus.PAUSED);

    private static final List<RolloutStatus> APPROVE_BUTTON_ENABLED = Collections
            .singletonList(RolloutStatus.WAITING_FOR_APPROVAL);

    private static final Map<RolloutStatus, StatusFontIcon> statusIconMap = new EnumMap<>(RolloutStatus.class);

    private static final List<Object> HIDDEN_COLUMNS = Arrays.asList(PROP_TYPE, SPUILabelDefinitions.VAR_CREATED_DATE,
            SPUILabelDefinitions.VAR_CREATED_USER, SPUILabelDefinitions.VAR_MODIFIED_DATE,
            SPUILabelDefinitions.VAR_MODIFIED_BY, SPUILabelDefinitions.VAR_APPROVAL_DECIDED_BY,
            SPUILabelDefinitions.VAR_APPROVAL_REMARK, SPUILabelDefinitions.VAR_DESC);

    private static final String[] centerAlignedColumns = new String[] { PROP_TYPE };

    static {
        statusIconMap.put(RolloutStatus.FINISHED,
                new StatusFontIcon(FontAwesome.CHECK_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_GREEN));
        statusIconMap.put(RolloutStatus.PAUSED,
                new StatusFontIcon(FontAwesome.PAUSE, SPUIStyleDefinitions.STATUS_ICON_BLUE));
        statusIconMap.put(RolloutStatus.RUNNING, new StatusFontIcon(null, SPUIStyleDefinitions.STATUS_SPINNER_YELLOW));
        statusIconMap.put(RolloutStatus.WAITING_FOR_APPROVAL,
                new StatusFontIcon(FontAwesome.HOURGLASS_HALF, SPUIStyleDefinitions.STATUS_ICON_ORANGE));
        statusIconMap.put(RolloutStatus.APPROVAL_DENIED,
                new StatusFontIcon(FontAwesome.TIMES_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED));
        statusIconMap.put(RolloutStatus.READY,
                new StatusFontIcon(FontAwesome.DOT_CIRCLE_O, SPUIStyleDefinitions.STATUS_ICON_LIGHT_BLUE));
        statusIconMap.put(RolloutStatus.STOPPED,
                new StatusFontIcon(FontAwesome.STOP, SPUIStyleDefinitions.STATUS_ICON_RED));
        statusIconMap.put(RolloutStatus.CREATING, new StatusFontIcon(null, SPUIStyleDefinitions.STATUS_SPINNER_GREY));
        statusIconMap.put(RolloutStatus.STARTING, new StatusFontIcon(null, SPUIStyleDefinitions.STATUS_SPINNER_BLUE));
        statusIconMap.put(RolloutStatus.ERROR_CREATING,
                new StatusFontIcon(FontAwesome.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED));
        statusIconMap.put(RolloutStatus.ERROR_STARTING,
                new StatusFontIcon(FontAwesome.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED));
        statusIconMap.put(RolloutStatus.DELETING, new StatusFontIcon(null, SPUIStyleDefinitions.STATUS_SPINNER_RED));
    }

    RolloutListGrid(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final RolloutManagement rolloutManagement, final UINotification uiNotification,
            final RolloutUIState rolloutUIState, final SpPermissionChecker permissionChecker,
            final TargetManagement targetManagement, final EntityFactory entityFactory, final UiProperties uiProperties,
            final TargetFilterQueryManagement targetFilterQueryManagement,
            final RolloutGroupManagement rolloutGroupManagement, final QuotaManagement quotaManagement,
            final TenantConfigurationManagement tenantConfigManagement,
            final SystemSecurityContext systemSecurityContext, final RepositoryProperties repositoryProperties) {
        super(i18n, eventBus, permissionChecker);
        this.rolloutManagement = rolloutManagement;
        this.rolloutGroupManagement = rolloutGroupManagement;
        this.tenantConfigManagement = tenantConfigManagement;
        this.systemSecurityContext = systemSecurityContext;
        this.addUpdateRolloutWindow = new AddUpdateRolloutWindowLayout(rolloutManagement, targetManagement,
                uiNotification, uiProperties, entityFactory, i18n, eventBus, targetFilterQueryManagement,
                rolloutGroupManagement, quotaManagement, tenantConfigManagement, systemSecurityContext,
                repositoryProperties);
        this.uiNotification = uiNotification;
        this.rolloutUIState = rolloutUIState;
        alignGenerator = new AlignCellStyleGenerator(null, centerAlignedColumns, null);

        setGeneratedPropertySupport(new RolloutGeneratedPropertySupport());
        init();
        hideColumnsDueToInsufficientPermissions();
    }

    /**
     * Handles the RolloutEvent to refresh Grid.
     *
     */
    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final RolloutEvent event) {
        switch (event) {
        case FILTER_BY_TEXT:
        case CREATE_ROLLOUT:
        case UPDATE_ROLLOUT:
        case SHOW_ROLLOUTS:
            refreshContainer();
            break;
        default:
            break;
        }
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onSaveTenantConfigEvent(final ManagementUIEvent tenantConfigSaveEvent) {
        if (tenantConfigSaveEvent == ManagementUIEvent.SAVE_TENANT_CONFIGURATION && isMultiAssignmentEnabled()) {
            addContainerProperties();
            setColumnProperties();
            setColumnExpandRatio();
            getColumn(SPUILabelDefinitions.VAR_WEIGHT).setHeaderCaption(i18n.getMessage("header.weight"));
            refreshContainer();
        }
    }

    /**
     * Handles the RolloutDeletedEvent to refresh the grid.
     *
     * @param eventContainer
     *            container which holds the rollout delete event
     */
    @EventBusListenerMethod(scope = EventScope.UI)
    public void onRolloutDeletedEvent(final RolloutDeletedEventContainer eventContainer) {
        refreshContainer();
    }

    /**
     * Handles the RolloutChangeEvent to refresh the item in the grid.
     *
     * @param eventContainer
     *            container which holds the rollout change event
     */
    @EventBusListenerMethod(scope = EventScope.UI)
    public void onRolloutChangeEvent(final RolloutChangeEventContainer eventContainer) {
        eventContainer.getEvents().forEach(this::handleEvent);
    }

    private void handleEvent(final RolloutChangedEvent rolloutChangeEvent) {
        if (!rolloutUIState.isShowRollOuts() || rolloutChangeEvent.getRolloutId() == null) {
            return;
        }
        final Optional<Rollout> rollout = rolloutManagement.getWithDetailedStatus(rolloutChangeEvent.getRolloutId());

        if (!rollout.isPresent()) {
            return;
        }
        final GeneratedPropertyContainer rolloutContainer = (GeneratedPropertyContainer) getContainerDataSource();
        final Item item = rolloutContainer.getItem(rolloutChangeEvent.getRolloutId());
        if (item == null) {
            refreshContainer();
            return;
        }

        updateItem(rollout.get(), item);
    }

    private void updateItem(final Rollout rollout, final Item item) {
        final TotalTargetCountStatus totalTargetCountStatus = rollout.getTotalTargetCountStatus();
        item.getItemProperty(SPUILabelDefinitions.VAR_STATUS).setValue(rollout.getStatus());
        item.getItemProperty(PROP_TYPE).setValue(rollout.getActionType());
        item.getItemProperty(SPUILabelDefinitions.VAR_TOTAL_TARGETS_COUNT_STATUS).setValue(totalTargetCountStatus);
        final Long groupCount = Long
                .valueOf((Integer) item.getItemProperty(SPUILabelDefinitions.VAR_NUMBER_OF_GROUPS).getValue());
        final int groupsCreated = rollout.getRolloutGroupsCreated();
        item.getItemProperty(ROLLOUT_RENDERER_DATA)
                .setValue(new RolloutRendererData(rollout.getName(), rollout.getStatus().toString()));

        if (groupsCreated != 0) {
            item.getItemProperty(SPUILabelDefinitions.VAR_NUMBER_OF_GROUPS).setValue(Integer.valueOf(groupsCreated));
            return;
        }

        final Long size = rolloutGroupManagement.countTargetsOfRolloutsGroup(rollout.getId());
        if (!size.equals(groupCount)) {
            item.getItemProperty(SPUILabelDefinitions.VAR_NUMBER_OF_GROUPS).setValue(size.intValue());
        }
    }

    @Override
    protected LazyQueryContainer createContainer() {
        final BeanQueryFactory<RolloutBeanQuery> rolloutQf = new BeanQueryFactory<>(RolloutBeanQuery.class);
        return new LazyQueryContainer(
                new LazyQueryDefinition(true, SPUIDefinitions.PAGE_SIZE, SPUILabelDefinitions.VAR_ID), rolloutQf);
    }

    @Override
    protected void addContainerProperties() {
        final LazyQueryContainer rolloutGridContainer = getGeneratedPropertySupport().getRawContainer();

        rolloutGridContainer.addContainerProperty(ROLLOUT_RENDERER_DATA, RolloutRendererData.class, null, false, false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_DESC, String.class, null, false, false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_STATUS, RolloutStatus.class, null, false,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_DIST_NAME_VERSION, String.class, null, false,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_CREATED_DATE, String.class, null, false,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_APPROVAL_DECIDED_BY, String.class, null,
                false, false);
        rolloutGridContainer.addContainerProperty(PROP_TYPE, ActionType.class, null, false, false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_APPROVAL_REMARK, String.class, null, false,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_MODIFIED_DATE, String.class, null, false,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_CREATED_USER, String.class, null, false,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_MODIFIED_BY, String.class, null, false,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_NUMBER_OF_GROUPS, Integer.class, 0, false,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_TOTAL_TARGETS, String.class, "0", false,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_TOTAL_TARGETS_COUNT_STATUS,
                TotalTargetCountStatus.class, null, false, false);

        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_STATUS, RolloutStatus.class, null, true,
                false);
        rolloutGridContainer.addContainerProperty(SPUILabelDefinitions.VAR_WEIGHT, Integer.class, null,
                true, false);
    }

    @Override
    protected void setColumnExpandRatio() {

        getColumn(ROLLOUT_RENDERER_DATA).setMinimumWidth(40);
        getColumn(ROLLOUT_RENDERER_DATA).setMaximumWidth(300);

        getColumn(SPUILabelDefinitions.VAR_DIST_NAME_VERSION).setMinimumWidth(40);
        getColumn(SPUILabelDefinitions.VAR_DIST_NAME_VERSION).setMaximumWidth(300);

        getColumn(SPUILabelDefinitions.VAR_STATUS).setMinimumWidth(40);
        getColumn(SPUILabelDefinitions.VAR_STATUS).setMaximumWidth(60);

        getColumn(SPUILabelDefinitions.VAR_TOTAL_TARGETS).setMinimumWidth(40);
        getColumn(SPUILabelDefinitions.VAR_TOTAL_TARGETS).setMaximumWidth(60);

        getColumn(SPUILabelDefinitions.VAR_NUMBER_OF_GROUPS).setMinimumWidth(40);
        getColumn(SPUILabelDefinitions.VAR_NUMBER_OF_GROUPS).setMaximumWidth(60);

        getColumn(PROP_TYPE).setMinimumWidth(45);
        getColumn(PROP_TYPE).setMaximumWidth(45);

        getColumn(VIRT_PROP_RUN).setMinimumWidth(25);
        getColumn(VIRT_PROP_RUN).setMaximumWidth(25);

        getColumn(VIRT_PROP_APPROVE).setMinimumWidth(25);
        getColumn(VIRT_PROP_APPROVE).setMaximumWidth(70);

        getColumn(VIRT_PROP_PAUSE).setMinimumWidth(25);
        getColumn(VIRT_PROP_PAUSE).setMaximumWidth(25);

        getColumn(VIRT_PROP_UPDATE).setMinimumWidth(25);
        getColumn(VIRT_PROP_UPDATE).setMaximumWidth(25);

        getColumn(VIRT_PROP_COPY).setMinimumWidth(25);
        getColumn(VIRT_PROP_COPY).setMaximumWidth(25);

        getColumn(VIRT_PROP_DELETE).setMinimumWidth(25);
        getColumn(VIRT_PROP_DELETE).setMaximumWidth(40);

        getColumn(SPUILabelDefinitions.VAR_TOTAL_TARGETS_COUNT_STATUS).setMinimumWidth(280);

        if (isMultiAssignmentEnabled()) {
            getColumn(SPUILabelDefinitions.VAR_WEIGHT).setMinimumWidth(50);
            getColumn(SPUILabelDefinitions.VAR_WEIGHT).setMaximumWidth(60);
        }
    }

    @Override
    protected void setColumnHeaderNames() {
        getColumn(ROLLOUT_RENDERER_DATA).setHeaderCaption(i18n.getMessage("header.name"));
        getColumn(SPUILabelDefinitions.VAR_DIST_NAME_VERSION)
                .setHeaderCaption(i18n.getMessage("header.distributionset"));
        getColumn(SPUILabelDefinitions.VAR_NUMBER_OF_GROUPS).setHeaderCaption(i18n.getMessage("header.numberofgroups"));
        getColumn(SPUILabelDefinitions.VAR_TOTAL_TARGETS).setHeaderCaption(i18n.getMessage("header.total.targets"));
        getColumn(PROP_TYPE).setHeaderCaption(i18n.getMessage("header.type"));
        getColumn(SPUILabelDefinitions.VAR_CREATED_DATE).setHeaderCaption(i18n.getMessage("header.createdDate"));
        getColumn(SPUILabelDefinitions.VAR_CREATED_USER).setHeaderCaption(i18n.getMessage("header.createdBy"));
        getColumn(SPUILabelDefinitions.VAR_MODIFIED_DATE).setHeaderCaption(i18n.getMessage("header.modifiedDate"));
        getColumn(SPUILabelDefinitions.VAR_MODIFIED_BY).setHeaderCaption(i18n.getMessage("header.modifiedBy"));
        getColumn(SPUILabelDefinitions.VAR_APPROVAL_REMARK).setHeaderCaption(i18n.getMessage("header.approvalRemark"));
        getColumn(SPUILabelDefinitions.VAR_APPROVAL_DECIDED_BY)
                .setHeaderCaption(i18n.getMessage("header.approvalDecidedBy"));
        getColumn(SPUILabelDefinitions.VAR_DESC).setHeaderCaption(i18n.getMessage("header.description"));
        getColumn(SPUILabelDefinitions.VAR_TOTAL_TARGETS_COUNT_STATUS)
                .setHeaderCaption(i18n.getMessage("header.detail.status"));
        getColumn(SPUILabelDefinitions.VAR_STATUS).setHeaderCaption(i18n.getMessage("header.status"));
        getColumn(SPUILabelDefinitions.VAR_STATUS).setHeaderCaption(i18n.getMessage("header.status"));
        if (isMultiAssignmentEnabled()) {
            getColumn(SPUILabelDefinitions.VAR_WEIGHT).setHeaderCaption(i18n.getMessage("header.weight"));
        }

        getColumn(VIRT_PROP_RUN).setHeaderCaption(i18n.getMessage("header.action.run"));
        getColumn(VIRT_PROP_APPROVE).setHeaderCaption(i18n.getMessage("header.action.approve"));
        getColumn(VIRT_PROP_PAUSE).setHeaderCaption(i18n.getMessage("header.action.pause"));
        getColumn(VIRT_PROP_UPDATE).setHeaderCaption(i18n.getMessage("header.action.update"));
        getColumn(VIRT_PROP_COPY).setHeaderCaption(i18n.getMessage("header.action.copy"));
        getColumn(VIRT_PROP_DELETE).setHeaderCaption(i18n.getMessage("header.action.delete"));

        joinColumns().setText(i18n.getMessage("header.action"));
    }

    private HeaderCell joinColumns() {

        return getDefaultHeaderRow().join(VIRT_PROP_RUN, VIRT_PROP_APPROVE, VIRT_PROP_PAUSE, VIRT_PROP_UPDATE,
                VIRT_PROP_COPY, VIRT_PROP_DELETE);
    }

    @Override
    protected String getGridId() {
        return UIComponentIdProvider.ROLLOUT_LIST_GRID_ID;
    }

    @Override
    protected void setColumnProperties() {
        final List<String> columnsToShowInOrder = Arrays.asList(ROLLOUT_RENDERER_DATA,
                SPUILabelDefinitions.VAR_DIST_NAME_VERSION, SPUILabelDefinitions.VAR_STATUS,
                SPUILabelDefinitions.VAR_TOTAL_TARGETS_COUNT_STATUS, SPUILabelDefinitions.VAR_NUMBER_OF_GROUPS,
                SPUILabelDefinitions.VAR_TOTAL_TARGETS, PROP_TYPE, VIRT_PROP_APPROVE, VIRT_PROP_RUN, VIRT_PROP_PAUSE,
                VIRT_PROP_UPDATE, VIRT_PROP_COPY, VIRT_PROP_DELETE, SPUILabelDefinitions.VAR_CREATED_DATE,
                SPUILabelDefinitions.VAR_CREATED_USER, SPUILabelDefinitions.VAR_MODIFIED_DATE,
                SPUILabelDefinitions.VAR_MODIFIED_BY, SPUILabelDefinitions.VAR_APPROVAL_DECIDED_BY,
                SPUILabelDefinitions.VAR_APPROVAL_REMARK, SPUILabelDefinitions.VAR_DESC);

        final List<String> addWeightColumnToShowInOrder = new ArrayList<>();
        addWeightColumnToShowInOrder.addAll(columnsToShowInOrder);

        if (isMultiAssignmentEnabled()) {
            addWeightColumnToShowInOrder.add(6, SPUILabelDefinitions.VAR_WEIGHT);
            setColumns(addWeightColumnToShowInOrder.toArray());
        } else {
            setColumns(columnsToShowInOrder.toArray());
        }

        setCellStyleGenerator(alignGenerator);
    }

    @Override
    protected void setHiddenColumns() {
        for (final Object propertyId : HIDDEN_COLUMNS) {
            getColumn(propertyId).setHidden(true);
        }

        getColumn(VIRT_PROP_RUN).setHidable(false);
        getColumn(VIRT_PROP_APPROVE).setHidable(false);
        getColumn(VIRT_PROP_PAUSE).setHidable(false);
        getColumn(VIRT_PROP_DELETE).setHidable(false);
        getColumn(VIRT_PROP_UPDATE).setHidable(false);
        getColumn(VIRT_PROP_COPY).setHidable(false);
    }

    @Override
    protected CellDescriptionGenerator getDescriptionGenerator() {
        return this::getDescription;
    }

    @Override
    protected void addColumnRenderers() {
        getColumn(SPUILabelDefinitions.VAR_NUMBER_OF_GROUPS).setRenderer(new HtmlRenderer(),
                new TotalTargetGroupsConverter());
        getColumn(SPUILabelDefinitions.VAR_TOTAL_TARGETS_COUNT_STATUS).setRenderer(new HtmlRenderer(),
                new TotalTargetCountStatusConverter());

        getColumn(SPUILabelDefinitions.VAR_STATUS).setRenderer(new HtmlLabelRenderer(), new RolloutStatusConverter());

        getColumn(PROP_TYPE).setRenderer(new HtmlLabelRenderer(),
                new RolloutTypeConverter(this::createTypeLabelAdapter));

        final RolloutRenderer customObjectRenderer = new RolloutRenderer(RolloutRendererData.class);
        customObjectRenderer.addClickListener(this::onClickOfRolloutName);
        getColumn(ROLLOUT_RENDERER_DATA).setRenderer(customObjectRenderer);

        getColumn(VIRT_PROP_RUN).setRenderer(
                new GridButtonRenderer(clickEvent -> startOrResumeRollout((Long) clickEvent.getItemId())),
                new RolloutGridButtonConverter(this::createRunButtonMetadata));
        getColumn(VIRT_PROP_APPROVE).setRenderer(
                new GridButtonRenderer(clickEvent -> approveRollout((Long) clickEvent.getItemId())),
                new RolloutGridButtonConverter(this::createApprovalButtonMetadata));
        getColumn(VIRT_PROP_PAUSE).setRenderer(
                new GridButtonRenderer(clickEvent -> pauseRollout((Long) clickEvent.getItemId())),
                new RolloutGridButtonConverter(this::createPauseButtonMetadata));
        getColumn(VIRT_PROP_UPDATE).setRenderer(
                new GridButtonRenderer(clickEvent -> updateRollout((Long) clickEvent.getItemId())),
                new RolloutGridButtonConverter(this::createUpdateButtonMetadata));
        getColumn(VIRT_PROP_COPY).setRenderer(
                new GridButtonRenderer(clickEvent -> copyRollout((Long) clickEvent.getItemId())),
                new RolloutGridButtonConverter(this::createCopyButtonMetadata));
        getColumn(VIRT_PROP_DELETE).setRenderer(
                new GridButtonRenderer(clickEvent -> deleteRollout((Long) clickEvent.getItemId())),
                new RolloutGridButtonConverter(this::createDeleteButtonMetadata));
    }

    /**
     * Generator class responsible to retrieve a Rollout from the grid data in
     * order to generate a virtual property.
     */
    class GenericPropertyValueGenerator extends PropertyValueGenerator<RolloutStatus> {
        private static final long serialVersionUID = 1L;

        @Override
        public RolloutStatus getValue(final Item item, final Object itemId, final Object propertyId) {
            return (RolloutStatus) item.getItemProperty(SPUILabelDefinitions.VAR_STATUS).getValue();
        }

        @Override
        public Class<RolloutStatus> getType() {
            return RolloutStatus.class;
        }
    }

    /**
     * Adds support for virtual properties (aka generated properties)
     */
    class RolloutGeneratedPropertySupport extends AbstractGeneratedPropertySupport {

        @Override
        public GeneratedPropertyContainer getDecoratedContainer() {
            return (GeneratedPropertyContainer) getContainerDataSource();
        }

        @Override
        public LazyQueryContainer getRawContainer() {
            return (LazyQueryContainer) getDecoratedContainer().getWrappedContainer();
        }

        @Override
        protected GeneratedPropertyContainer addGeneratedContainerProperties() {
            final GeneratedPropertyContainer decoratedContainer = getDecoratedContainer();

            decoratedContainer.addGeneratedProperty(VIRT_PROP_RUN, new GenericPropertyValueGenerator());
            decoratedContainer.addGeneratedProperty(VIRT_PROP_APPROVE, new GenericPropertyValueGenerator());
            decoratedContainer.addGeneratedProperty(VIRT_PROP_PAUSE, new GenericPropertyValueGenerator());
            decoratedContainer.addGeneratedProperty(VIRT_PROP_UPDATE, new GenericPropertyValueGenerator());
            decoratedContainer.addGeneratedProperty(VIRT_PROP_COPY, new GenericPropertyValueGenerator());
            decoratedContainer.addGeneratedProperty(VIRT_PROP_DELETE, new GenericPropertyValueGenerator());

            return decoratedContainer;
        }
    }

    /**
     * Concrete grid-button converter that handles Rollouts Status.
     */
    class RolloutGridButtonConverter extends AbstractGridButtonConverter<RolloutStatus> {

        private static final long serialVersionUID = 1L;

        /**
         * Constructor that sets the appropriate adapter.
         *
         * @param adapter
         *            adapts <code>RolloutStatus</code> to
         *            <code>StatusFontIcon</code>
         */
        public RolloutGridButtonConverter(final GridButtonAdapter<RolloutStatus> adapter) {
            addAdapter(adapter);
        }

        @Override
        public Class<RolloutStatus> getModelType() {
            return RolloutStatus.class;
        }
    }

    private void onClickOfRolloutName(final RendererClickEvent event) {
        rolloutUIState.setRolloutId((long) event.getItemId());
        final String rolloutName = (String) getContainerDataSource().getItem(event.getItemId())
                .getItemProperty(SPUILabelDefinitions.VAR_NAME).getValue();
        rolloutUIState.setRolloutName(rolloutName);
        final String ds = (String) getContainerDataSource().getItem(event.getItemId())
                .getItemProperty(SPUILabelDefinitions.VAR_DIST_NAME_VERSION).getValue();
        rolloutUIState.setRolloutDistributionSet(ds);
        eventBus.publish(this, RolloutEvent.SHOW_ROLLOUT_GROUPS);
    }

    private void pauseRollout(final Long rolloutId) {
        final Item row = getContainerDataSource().getItem(rolloutId);

        final RolloutStatus rolloutStatus = (RolloutStatus) row.getItemProperty(SPUILabelDefinitions.VAR_STATUS)
                .getValue();

        if (RolloutStatus.RUNNING != rolloutStatus) {
            return;
        }

        final String rolloutName = (String) row.getItemProperty(SPUILabelDefinitions.VAR_NAME).getValue();

        rolloutManagement.pauseRollout(rolloutId);
        uiNotification.displaySuccess(i18n.getMessage("message.rollout.paused", rolloutName));
    }

    private void startOrResumeRollout(final Long rolloutId) {
        final Item row = getContainerDataSource().getItem(rolloutId);

        final RolloutStatus rolloutStatus = (RolloutStatus) row.getItemProperty(SPUILabelDefinitions.VAR_STATUS)
                .getValue();
        final String rolloutName = (String) row.getItemProperty(SPUILabelDefinitions.VAR_NAME).getValue();

        if (RolloutStatus.READY == rolloutStatus) {
            rolloutManagement.start(rolloutId);
            uiNotification.displaySuccess(i18n.getMessage("message.rollout.started", rolloutName));
            return;
        }

        if (RolloutStatus.PAUSED == rolloutStatus) {
            rolloutManagement.resumeRollout(rolloutId);
            uiNotification.displaySuccess(i18n.getMessage("message.rollout.resumed", rolloutName));
        }
    }

    private void approveRollout(final Long rolloutId) {
        final CommonDialogWindow addTargetWindow = addUpdateRolloutWindow.getWindow(rolloutId, false);
        addTargetWindow.setCaption(i18n.getMessage("caption.rollout.approve"));
        UI.getCurrent().addWindow(addTargetWindow);
        addTargetWindow.setVisible(Boolean.TRUE);
    }

    private void updateRollout(final Long rolloutId) {
        final CommonDialogWindow addTargetWindow = addUpdateRolloutWindow.getWindow(rolloutId, false);
        addTargetWindow.setCaption(i18n.getMessage("caption.update", i18n.getMessage("caption.rollout")));
        UI.getCurrent().addWindow(addTargetWindow);
        addTargetWindow.setVisible(Boolean.TRUE);
    }

    private void copyRollout(final Long rolloutId) {
        final CommonDialogWindow addTargetWindow = addUpdateRolloutWindow.getWindow(rolloutId, true);
        addTargetWindow.setCaption(i18n.getMessage("caption.create.new", i18n.getMessage("caption.rollout")));
        UI.getCurrent().addWindow(addTargetWindow);
        addTargetWindow.setVisible(Boolean.TRUE);
    }

    private void deleteRollout(final Long rolloutId) {
        final Optional<Rollout> rollout = rolloutManagement.getWithDetailedStatus(rolloutId);

        if (!rollout.isPresent()) {
            return;
        }

        final String formattedConfirmationQuestion = getConfirmationQuestion(rollout.get());
        final ConfirmationDialog confirmationDialog = new ConfirmationDialog(
                i18n.getMessage("caption.entity.delete.action.confirmbox"), formattedConfirmationQuestion,
                i18n.getMessage(UIMessageIdProvider.BUTTON_OK), i18n.getMessage(UIMessageIdProvider.BUTTON_CANCEL),
                ok -> {
                    if (!ok) {
                        return;
                    }
                    final Item row = getContainerDataSource().getItem(rolloutId);
                    final String rolloutName = (String) row.getItemProperty(SPUILabelDefinitions.VAR_NAME).getValue();
                    rolloutManagement.delete(rolloutId);
                    uiNotification.displaySuccess(i18n.getMessage("message.rollout.deleted", rolloutName));
                }, UIComponentIdProvider.ROLLOUT_DELETE_CONFIRMATION_DIALOG);
        UI.getCurrent().addWindow(confirmationDialog.getWindow());
        confirmationDialog.getWindow().bringToFront();
    }

    private String getConfirmationQuestion(final Rollout rollout) {

        final Map<Status, Long> statusTotalCount = rollout.getTotalTargetCountStatus().getStatusTotalCountMap();
        Long scheduledActions = statusTotalCount.get(Status.SCHEDULED);
        if (scheduledActions == null) {
            scheduledActions = 0L;
        }
        final Long runningActions = statusTotalCount.get(Status.RUNNING);
        String rolloutDetailsMessage = "";
        if (scheduledActions > 0 || runningActions > 0) {
            rolloutDetailsMessage = i18n.getMessage("message.delete.rollout.details", runningActions, scheduledActions);
        }

        return i18n.getMessage("message.delete.rollout", rollout.getName(), rolloutDetailsMessage);
    }

    private String getDescription(final CellReference cell) {

        String description = null;

        if (SPUILabelDefinitions.VAR_STATUS.equals(cell.getPropertyId())) {
            final String status = cell.getProperty().getValue().toString().toLowerCase();
            description = i18n.getMessage(UIMessageIdProvider.TOOLTIP_ROLLOUT_STATUS_PREFIX + status);
        } else if (getActionLabeltext().equals(cell.getPropertyId())) {
            description = getActionLabeltext().toLowerCase();
        } else if (ROLLOUT_RENDERER_DATA.equals(cell.getPropertyId())) {
            description = ((RolloutRendererData) cell.getProperty().getValue()).getName();
        } else if (SPUILabelDefinitions.VAR_TOTAL_TARGETS_COUNT_STATUS.equals(cell.getPropertyId())) {
            description = getTooltip(((TotalTargetCountStatus) cell.getValue()).getStatusTotalCountMap(), i18n);
        }

        return description;
    }

    private static boolean hasToBeDisabled(final RolloutStatus currentRolloutStatus,
            final List<RolloutStatus> expectedRolloutStatus) {
        return !expectedRolloutStatus.contains(currentRolloutStatus);
    }

    private StatusFontIcon createApprovalButtonMetadata(final RolloutStatus rolloutStatus) {
        final boolean isDisabled = hasToBeDisabled(rolloutStatus, APPROVE_BUTTON_ENABLED);
        return new StatusFontIcon(FontAwesome.GAVEL, null, i18n.getMessage(UIMessageIdProvider.TOOLTIP_ROLLOUT_APPROVE),
                UIComponentIdProvider.ROLLOUT_APPROVAL_BUTTON_ID, isDisabled);
    }

    private StatusFontIcon createRunButtonMetadata(final RolloutStatus rolloutStatus) {
        final boolean isDisabled = hasToBeDisabled(rolloutStatus, RUN_BUTTON_ENABLED);
        return new StatusFontIcon(FontAwesome.PLAY, null, i18n.getMessage(UIMessageIdProvider.TOOLTIP_ROLLOUT_RUN),
                UIComponentIdProvider.ROLLOUT_RUN_BUTTON_ID, isDisabled);
    }

    private StatusFontIcon createPauseButtonMetadata(final RolloutStatus rolloutStatus) {
        final boolean isDisabled = hasToBeDisabled(rolloutStatus, PAUSE_BUTTON_ENABLED);
        return new StatusFontIcon(FontAwesome.PAUSE, null, i18n.getMessage(UIMessageIdProvider.TOOLTIP_ROLLOUT_PAUSE),
                UIComponentIdProvider.ROLLOUT_PAUSE_BUTTON_ID, isDisabled);
    }

    private StatusFontIcon createCopyButtonMetadata(final RolloutStatus rolloutStatus) {
        final boolean isDisabled = hasToBeDisabled(rolloutStatus, DELETE_COPY_BUTTON_ENABLED);
        return new StatusFontIcon(FontAwesome.COPY, null, i18n.getMessage(UIMessageIdProvider.TOOLTIP_ROLLOUT_COPY),
                UIComponentIdProvider.ROLLOUT_COPY_BUTTON_ID, isDisabled);
    }

    private StatusFontIcon createUpdateButtonMetadata(final RolloutStatus rolloutStatus) {
        final boolean isDisabled = hasToBeDisabled(rolloutStatus, UPDATE_BUTTON_ENABLED);
        return new StatusFontIcon(FontAwesome.EDIT, null, i18n.getMessage(UIMessageIdProvider.TOOLTIP_ROLLOUT_UPDATE),
                UIComponentIdProvider.ROLLOUT_UPDATE_BUTTON_ID, isDisabled);
    }

    private StatusFontIcon createDeleteButtonMetadata(final RolloutStatus rolloutStatus) {
        final boolean isDisabled = hasToBeDisabled(rolloutStatus, DELETE_COPY_BUTTON_ENABLED);
        return new StatusFontIcon(FontAwesome.TRASH_O, null, i18n.getMessage(UIMessageIdProvider.TOOLTIP_DELETE),
                UIComponentIdProvider.ROLLOUT_DELETE_BUTTON_ID, isDisabled);
    }

    /**
     *
     * Converter to convert {@link RolloutStatus} to string.
     *
     */
    class RolloutStatusConverter implements Converter<String, RolloutStatus> {

        private static final long serialVersionUID = 1L;

        @Override
        public RolloutStatus convertToModel(final String value, final Class<? extends RolloutStatus> targetType,
                final Locale locale) {
            return null;
        }

        @Override
        public String convertToPresentation(final RolloutStatus value, final Class<? extends String> targetType,
                final Locale locale) {
            return convertRolloutStatusToString(value);
        }

        @Override
        public Class<RolloutStatus> getModelType() {
            return RolloutStatus.class;
        }

        @Override
        public Class<String> getPresentationType() {
            return String.class;
        }

        private String convertRolloutStatusToString(final RolloutStatus value) {
            StatusFontIcon statusFontIcon = statusIconMap.get(value);
            if (statusFontIcon == null) {
                statusFontIcon = new StatusFontIcon(FontAwesome.QUESTION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_BLUE);
            }
            final String codePoint = HawkbitCommonUtil.getCodePoint(statusFontIcon);
            return HawkbitCommonUtil.getStatusLabelDetailsInString(codePoint, statusFontIcon.getStyle(),
                    UIComponentIdProvider.ROLLOUT_STATUS_LABEL_ID);
        }
    }

    /**
     *
     * Converter to convert {@link RolloutStatus} to string.
     *
     */

    class RolloutTypeConverter extends AbstractHtmlLabelConverter<ActionType> {

        private static final long serialVersionUID = 1L;

        RolloutTypeConverter(final LabelAdapter<ActionType> adapter) {
            addAdapter(adapter);
        }

        @Override
        public Class<ActionType> getModelType() {
            return ActionType.class;
        }

    }

    private StatusFontIcon createTypeLabelAdapter(final ActionType actionType) {
        if (ActionType.FORCED == actionType) {
            return new StatusFontIcon(FontAwesome.BOLT, STATUS_ICON_FORCED,
                    i18n.getMessage(UIMessageIdProvider.CAPTION_ACTION_FORCED),
                    UIComponentIdProvider.ACTION_HISTORY_TABLE_TYPE_LABEL_ID);
        }
        if (ActionType.TIMEFORCED == actionType) {
            return new StatusFontIcon(FontAwesome.HISTORY, STATUS_ICON_FORCED,
                    i18n.getMessage(UIMessageIdProvider.CAPTION_ACTION_TIME_FORCED),
                    UIComponentIdProvider.ACTION_HISTORY_TABLE_TYPE_LABEL_ID);
        }
        if (ActionType.SOFT == actionType) {
            return new StatusFontIcon(FontAwesome.STEP_FORWARD, STATUS_ICON_SOFT,
                    i18n.getMessage(UIMessageIdProvider.CAPTION_ACTION_SOFT),
                    UIComponentIdProvider.ACTION_HISTORY_TABLE_TYPE_LABEL_ID);
        }
        if (ActionType.DOWNLOAD_ONLY == actionType) {
            return new StatusFontIcon(FontAwesome.DOWNLOAD, STATUS_ICON_DOWNLOAD_ONLY,
                    i18n.getMessage(UIMessageIdProvider.CAPTION_ACTION_DOWNLOAD_ONLY),
                    UIComponentIdProvider.ACTION_HISTORY_TABLE_TYPE_LABEL_ID);
        }
        return null;
    }

    /**
     * Converter to convert {@link TotalTargetCountStatus} to formatted string
     * with status and count details.
     *
     */
    class TotalTargetCountStatusConverter implements Converter<String, TotalTargetCountStatus> {

        private static final long serialVersionUID = 1L;

        @Override
        public TotalTargetCountStatus convertToModel(final String value,
                final Class<? extends TotalTargetCountStatus> targetType, final Locale locale) {
            return null;
        }

        @Override
        public String convertToPresentation(final TotalTargetCountStatus value,
                final Class<? extends String> targetType, final Locale locale) {
            return DistributionBarHelper.getDistributionBarAsHTMLString(value.getStatusTotalCountMap());
        }

        @Override
        public Class<TotalTargetCountStatus> getModelType() {
            return TotalTargetCountStatus.class;
        }

        @Override
        public Class<String> getPresentationType() {
            return String.class;
        }
    }

    /**
     * Converter to convert 0 to empty, if total target groups is zero.
     *
     */
    class TotalTargetGroupsConverter implements Converter<String, Integer> {

        private static final long serialVersionUID = 1L;

        @Override
        public Integer convertToModel(final String value, final Class<? extends Integer> targetType,
                final Locale locale) {
            return null;
        }

        @Override
        public String convertToPresentation(final Integer value, final Class<? extends String> targetType,
                final Locale locale) {
            if (value == 0) {
                return "";
            }
            return value.toString();
        }

        @Override
        public Class<Integer> getModelType() {
            return Integer.class;
        }

        @Override
        public Class<String> getPresentationType() {
            return String.class;
        }
    }

    private void hideColumnsDueToInsufficientPermissions() {

        final List<Object> modifiableColumnsList = getColumns().stream().map(Column::getPropertyId)
                .collect(Collectors.toList());

        if (!permissionChecker.hasRolloutUpdatePermission()) {
            modifiableColumnsList.remove(VIRT_PROP_UPDATE);
        }
        if (!permissionChecker.hasRolloutCreatePermission()) {
            modifiableColumnsList.remove(VIRT_PROP_COPY);
        }
        if (!permissionChecker.hasRolloutApprovalPermission() || !tenantConfigManagement
                .getConfigurationValue(TenantConfigurationKey.ROLLOUT_APPROVAL_ENABLED, Boolean.class).getValue()) {
            modifiableColumnsList.remove(VIRT_PROP_APPROVE);
        }
        if (!permissionChecker.hasRolloutDeletePermission()) {
            modifiableColumnsList.remove(VIRT_PROP_DELETE);
        }
        if (!permissionChecker.hasRolloutHandlePermission()) {
            modifiableColumnsList.remove(VIRT_PROP_PAUSE);
            modifiableColumnsList.remove(VIRT_PROP_RUN);
        }

        setColumns(modifiableColumnsList.toArray());
    }

    private boolean isMultiAssignmentEnabled() {
        return systemSecurityContext.runAsSystem(() -> tenantConfigManagement
                .getConfigurationValue(TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED, Boolean.class).getValue());
    }
}
