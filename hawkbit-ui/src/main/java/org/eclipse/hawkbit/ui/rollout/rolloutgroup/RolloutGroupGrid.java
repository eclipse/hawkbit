/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout.rolloutgroup;

import java.util.Collection;
import java.util.Optional;

import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupStatus;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.eclipse.hawkbit.ui.common.builder.StatusIconBuilder.RolloutGroupStatusIconSupplier;
import org.eclipse.hawkbit.ui.common.data.mappers.RolloutGroupToProxyRolloutGroupMapper;
import org.eclipse.hawkbit.ui.common.data.providers.RolloutGroupDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyRollout;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyRolloutGroup;
import org.eclipse.hawkbit.ui.common.event.CommandTopics;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.common.event.LayoutVisibilityEventPayload;
import org.eclipse.hawkbit.ui.common.event.LayoutVisibilityEventPayload.VisibilityType;
import org.eclipse.hawkbit.ui.common.event.SelectionChangedEventPayload.SelectionChangedEventType;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.support.FilterSupport;
import org.eclipse.hawkbit.ui.common.grid.support.MasterEntitySupport;
import org.eclipse.hawkbit.ui.common.grid.support.SelectionSupport;
import org.eclipse.hawkbit.ui.rollout.DistributionBarHelper;
import org.eclipse.hawkbit.ui.rollout.RolloutManagementUIState;
import org.eclipse.hawkbit.ui.utils.SPUILabelDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.google.common.base.Predicates;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.renderers.HtmlRenderer;

/**
 * Rollout group list grid component.
 */
public class RolloutGroupGrid extends AbstractGrid<ProxyRolloutGroup, Long> {
    private static final long serialVersionUID = 1L;

    private static final String ROLLOUT_GROUP_LINK_ID = "rolloutGroup";

    private final RolloutManagementUIState rolloutManagementUIState;
    private final transient RolloutGroupManagement rolloutGroupManagement;
    private final transient RolloutGroupToProxyRolloutGroupMapper rolloutGroupMapper;

    private final RolloutGroupStatusIconSupplier<ProxyRolloutGroup> rolloutGroupStatusIconSupplier;

    private final transient MasterEntitySupport<ProxyRollout> masterEntitySupport;

    RolloutGroupGrid(final CommonUiDependencies uiDependencies, final RolloutGroupManagement rolloutGroupManagement,
            final RolloutManagementUIState rolloutManagementUIState) {
        super(uiDependencies.getI18n(), uiDependencies.getEventBus(), uiDependencies.getPermChecker());

        this.rolloutManagementUIState = rolloutManagementUIState;
        this.rolloutGroupManagement = rolloutGroupManagement;
        this.rolloutGroupMapper = new RolloutGroupToProxyRolloutGroupMapper();

        setSelectionSupport(new SelectionSupport<>(this, eventBus, EventLayout.ROLLOUT_GROUP_LIST, EventView.ROLLOUT,
                this::mapIdToProxyEntity, this::getSelectedEntityIdFromUiState, this::setSelectedEntityIdToUiState));
        getSelectionSupport().disableSelection();

        setFilterSupport(new FilterSupport<>(new RolloutGroupDataProvider(rolloutGroupManagement, rolloutGroupMapper)));
        initFilterMappings();

        this.masterEntitySupport = new MasterEntitySupport<>(getFilterSupport());

        rolloutGroupStatusIconSupplier = new RolloutGroupStatusIconSupplier<>(i18n, ProxyRolloutGroup::getStatus,
                UIComponentIdProvider.ROLLOUT_GROUP_STATUS_LABEL_ID);
        init();
    }

    /**
     * Map id to rollout group
     *
     * @param entityId
     *            Entity id
     *
     * @return Rollout group
     */
    public Optional<ProxyRolloutGroup> mapIdToProxyEntity(final long entityId) {
        return rolloutGroupManagement.get(entityId).map(rolloutGroupMapper::map);
    }

    private Long getSelectedEntityIdFromUiState() {
        return rolloutManagementUIState.getSelectedRolloutGroupId();
    }

    private void setSelectedEntityIdToUiState(final Long entityId) {
        rolloutManagementUIState.setSelectedRolloutGroupId(entityId);
    }

    private void initFilterMappings() {
        getFilterSupport().<Long> addMapping(FilterType.MASTER,
                (filter, masterFilter) -> getFilterSupport().setFilter(masterFilter));
    }

    @Override
    public String getGridId() {
        return UIComponentIdProvider.ROLLOUT_GROUP_LIST_GRID_ID;
    }

    @Override
    public void addColumns() {
        GridComponentBuilder.addComponentColumn(this, this::buildRolloutGroupLink).setId(ROLLOUT_GROUP_LINK_ID)
                .setCaption(i18n.getMessage("header.name")).setHidable(false).setExpandRatio(3);

        GridComponentBuilder.addDescriptionColumn(this, i18n, SPUILabelDefinitions.VAR_DESC).setHidable(true)
                .setHidden(true);

        GridComponentBuilder.addIconColumn(this, rolloutGroupStatusIconSupplier::getLabel,
                SPUILabelDefinitions.VAR_STATUS, i18n.getMessage("header.status")).setHidable(true);

        addColumn(rolloutGroup -> DistributionBarHelper
                .getDistributionBarAsHTMLString(rolloutGroup.getTotalTargetCountStatus().getStatusTotalCountMap()),
                new HtmlRenderer()).setId(SPUILabelDefinitions.VAR_TOTAL_TARGETS_COUNT_STATUS)
                        .setCaption(i18n.getMessage("header.detail.status"))
                        .setDescriptionGenerator(
                                rolloutGroup -> DistributionBarHelper.getTooltip(
                                        rolloutGroup.getTotalTargetCountStatus().getStatusTotalCountMap(), i18n),
                                ContentMode.HTML)
                        .setExpandRatio(5).setHidable(true);

        GridComponentBuilder.addColumn(this, ProxyRolloutGroup::getTotalTargetsCount)
                .setId(SPUILabelDefinitions.VAR_TOTAL_TARGETS).setCaption(i18n.getMessage("header.total.targets"))
                .setHidable(true);

        GridComponentBuilder.addColumn(this, group -> group.getFinishedPercentage() + "%")
                .setId(SPUILabelDefinitions.ROLLOUT_GROUP_INSTALLED_PERCENTAGE)
                .setCaption(i18n.getMessage("header.rolloutgroup.installed.percentage")).setHidable(true);

        GridComponentBuilder.addColumn(this, group -> group.getErrorConditionExp() + "%")
                .setId(SPUILabelDefinitions.ROLLOUT_GROUP_ERROR_THRESHOLD)
                .setCaption(i18n.getMessage("header.rolloutgroup.threshold.error")).setHidable(true);

        GridComponentBuilder.addColumn(this, group -> group.getSuccessConditionExp() + "%")
                .setId(SPUILabelDefinitions.ROLLOUT_GROUP_THRESHOLD)
                .setCaption(i18n.getMessage("header.rolloutgroup.threshold")).setHidable(true);

        GridComponentBuilder.addCreatedAndModifiedColumns(this, i18n)
                .forEach(col -> col.setHidable(true).setHidden(true));
    }

    private Button buildRolloutGroupLink(final ProxyRolloutGroup rolloutGroup) {
        final boolean enableButton = RolloutGroupStatus.CREATING != rolloutGroup.getStatus()
                && permissionChecker.hasRolloutTargetsReadPermission();

        return GridComponentBuilder.buildLink(rolloutGroup, "rolloutgroup.link.", rolloutGroup.getName(), enableButton,
                clickEvent -> onClickOfRolloutGroupName(rolloutGroup));
    }

    private void onClickOfRolloutGroupName(final ProxyRolloutGroup rolloutGroup) {
        getSelectionSupport().sendSelectionChangedEvent(SelectionChangedEventType.ENTITY_SELECTED, rolloutGroup);
        rolloutManagementUIState.setSelectedRolloutGroupName(rolloutGroup.getName());

        showRolloutGroupTargetsListLayout();
    }

    private void showRolloutGroupTargetsListLayout() {
        eventBus.publish(CommandTopics.CHANGE_LAYOUT_VISIBILITY, this, new LayoutVisibilityEventPayload(
                VisibilityType.SHOW, EventLayout.ROLLOUT_GROUP_TARGET_LIST, EventView.ROLLOUT));
    }

    /**
     * Update items in rollout group grid
     *
     * @param ids
     *            List of Rollout group id
     */
    public void updateGridItems(final Collection<Long> ids) {
        ids.stream().filter(Predicates.notNull()).map(rolloutGroupManagement::getWithDetailedStatus)
                .forEach(rolloutGroup -> rolloutGroup.ifPresent(this::updateGridItem));
    }

    private void updateGridItem(final RolloutGroup rolloutGroup) {
        getDataProvider().refreshItem(rolloutGroupMapper.map(rolloutGroup));
    }

    @Override
    public void restoreState() {
        final Long masterEntityId = rolloutManagementUIState.getSelectedRolloutId();
        if (masterEntityId != null) {
            getMasterEntitySupport().masterEntityChanged(new ProxyRollout(masterEntityId));
        }
    }

    /**
     * @return Rollout master entity support
     */
    public MasterEntitySupport<ProxyRollout> getMasterEntitySupport() {
        return masterEntitySupport;
    }
}
