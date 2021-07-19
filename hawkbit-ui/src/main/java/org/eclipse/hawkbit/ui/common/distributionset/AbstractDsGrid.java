/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.distributionset;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.filters.DsFilterParams;
import org.eclipse.hawkbit.ui.common.data.mappers.DistributionSetToProxyDistributionMapper;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload.EntityModifiedEventType;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.support.DeleteSupport;
import org.eclipse.hawkbit.ui.common.grid.support.SelectionSupport;
import org.eclipse.hawkbit.ui.common.state.GridLayoutUiState;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;

import com.vaadin.ui.Button;

/**
 * Abstract class of distribution set grid
 *
 * @param <F>
 *            Generic filter type
 */
public abstract class AbstractDsGrid<F extends DsFilterParams> extends AbstractGrid<ProxyDistributionSet, F> {
    private static final long serialVersionUID = 1L;

    protected static final String DS_NAME_ID = "dsName";
    protected static final String DS_VERSION_ID = "dsVersion";
    protected static final String DS_DESC_ID = "dsDescription";
    protected static final String DS_DELETE_BUTTON_ID = "dsDeleteButton";

    protected final GridLayoutUiState distributionSetGridLayoutUiState;
    protected final transient DistributionSetManagement dsManagement;
    protected final transient DistributionSetToProxyDistributionMapper dsToProxyDistributionMapper;
    protected final transient DeleteSupport<ProxyDistributionSet> distributionDeleteSupport;
    protected final transient UINotification notification;

    protected AbstractDsGrid(final CommonUiDependencies uiDependencies, final DistributionSetManagement dsManagement,
            final GridLayoutUiState distributionSetGridLayoutUiState, final EventView view) {
        super(uiDependencies.getI18n(), uiDependencies.getEventBus(), uiDependencies.getPermChecker());

        this.notification = uiDependencies.getUiNotification();
        this.distributionSetGridLayoutUiState = distributionSetGridLayoutUiState;
        this.dsManagement = dsManagement;
        this.dsToProxyDistributionMapper = new DistributionSetToProxyDistributionMapper();

        setSelectionSupport(new SelectionSupport<>(this, eventBus, EventLayout.DS_LIST, view, this::mapIdToProxyEntity,
                this::getSelectedEntityIdFromUiState, this::setSelectedEntityIdToUiState));
        if (distributionSetGridLayoutUiState.isMaximized()) {
            getSelectionSupport().disableSelection();
        } else {
            getSelectionSupport().enableMultiSelection();
        }

        this.distributionDeleteSupport = new DeleteSupport<>(this, i18n, notification, "distribution.details.header",
                "caption.distributionsets", ProxyDistributionSet::getNameVersion, this::deleteDistributionSets,
                UIComponentIdProvider.DS_DELETE_CONFIRMATION_DIALOG);
    }

    @Override
    public void init() {
        super.init();

        addStyleName("grid-row-border");
    }

    /**
     * Map distribution set to proxy entity
     *
     * @param entityId
     *            Entity id
     *
     * @return Distribution set
     */
    public Optional<ProxyDistributionSet> mapIdToProxyEntity(final long entityId) {
        return dsManagement.get(entityId).map(dsToProxyDistributionMapper::map);
    }

    private Long getSelectedEntityIdFromUiState() {
        return distributionSetGridLayoutUiState.getSelectedEntityId();
    }

    private void setSelectedEntityIdToUiState(final Long entityId) {
        distributionSetGridLayoutUiState.setSelectedEntityId(entityId);
    }

    private boolean deleteDistributionSets(final Collection<ProxyDistributionSet> setsToBeDeleted) {
        final Collection<Long> dsToBeDeletedIds = setsToBeDeleted.stream().map(ProxyIdentifiableEntity::getId)
                .collect(Collectors.toList());
        dsManagement.delete(dsToBeDeletedIds);

        eventBus.publish(EventTopics.ENTITY_MODIFIED, this, new EntityModifiedEventPayload(
                EntityModifiedEventType.ENTITY_REMOVED, ProxyDistributionSet.class, dsToBeDeletedIds));

        return true;
    }

    protected Column<ProxyDistributionSet, String> addNameColumn() {
        return GridComponentBuilder.addNameColumn(this, i18n, DS_NAME_ID);
    }

    protected Column<ProxyDistributionSet, String> addVersionColumn() {
        return GridComponentBuilder.addVersionColumn(this, i18n, ProxyDistributionSet::getVersion, DS_VERSION_ID);
    }

    protected Column<ProxyDistributionSet, Button> addDeleteColumn() {
        return GridComponentBuilder.addDeleteColumn(this, i18n, DS_DELETE_BUTTON_ID, distributionDeleteSupport,
                UIComponentIdProvider.DIST_DELET_ICON, e -> permissionChecker.hasDeleteRepositoryPermission());
    }

    @Override
    protected void addMaxColumns() {
        addNameColumn().setExpandRatio(7);
        addVersionColumn();

        GridComponentBuilder.addDescriptionColumn(this, i18n, DS_DESC_ID).setExpandRatio(5);
        GridComponentBuilder.addCreatedAndModifiedColumns(this, i18n);

        addDeleteColumn();

        getColumns().forEach(column -> column.setHidable(true));
    }
}
