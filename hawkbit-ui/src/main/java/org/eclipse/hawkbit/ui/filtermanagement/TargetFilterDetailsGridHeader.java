/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.rsql.RsqlValidationOracle;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.AbstractEntityWindowController;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.common.event.CommandTopics;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.LayoutVisibilityEventPayload;
import org.eclipse.hawkbit.ui.common.event.LayoutVisibilityEventPayload.VisibilityType;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractBreadcrumbGridHeader;
import org.eclipse.hawkbit.ui.common.grid.header.support.CloseHeaderSupport;
import org.eclipse.hawkbit.ui.filtermanagement.state.TargetFilterDetailsLayoutUiState;
import org.eclipse.hawkbit.ui.filtermanagement.state.TargetFilterDetailsLayoutUiState.Mode;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;

/**
 * Layout for Custom Filter view
 */
public class TargetFilterDetailsGridHeader extends AbstractBreadcrumbGridHeader {
    private static final long serialVersionUID = 1L;

    private static final String BREADCRUMB_CUSTOM_FILTERS = "breadcrumb.target.filter.custom.filters";

    private final TargetFilterDetailsLayoutUiState uiState;

    private final transient TargetFilterAddUpdateLayout targetFilterAddUpdateLayout;
    private final transient AddTargetFilterController addTargetFilterController;
    private final transient UpdateTargetFilterController updateTargetFilterController;

    /**
     * Constructor for TargetFilterDetailsGridHeader
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param targetFilterManagement
     *            TargetFilterQueryManagement
     * @param uiProperties
     *            UiProperties
     * @param rsqlValidationOracle
     *            RsqlValidationOracle
     * @param uiState
     *            TargetFilterDetailsLayoutUiState
     */
    public TargetFilterDetailsGridHeader(final CommonUiDependencies uiDependencies,
            final TargetFilterQueryManagement targetFilterManagement, final UiProperties uiProperties,
            final RsqlValidationOracle rsqlValidationOracle, final TargetFilterDetailsLayoutUiState uiState,
            final TargetManagement targetManagement) {
        super(uiDependencies.getI18n(), uiDependencies.getPermChecker(), uiDependencies.getEventBus());

        this.uiState = uiState;

        final BreadcrumbLink targetFilterViewLink = new BreadcrumbLink(i18n.getMessage(BREADCRUMB_CUSTOM_FILTERS),
                i18n.getMessage(BREADCRUMB_CUSTOM_FILTERS), this::closeDetails);
        addBreadcrumbLink(targetFilterViewLink);

        final CloseHeaderSupport closeHeaderSupport = new CloseHeaderSupport(i18n,
                UIComponentIdProvider.CUSTOM_FILTER_CLOSE, this::closeDetails);
        addHeaderSupport(closeHeaderSupport);

        this.targetFilterAddUpdateLayout = new TargetFilterAddUpdateLayout(i18n, permChecker, uiProperties, uiState,
                eventBus, rsqlValidationOracle, targetManagement);
        this.addTargetFilterController = new AddTargetFilterController(uiDependencies, targetFilterManagement,
                targetFilterAddUpdateLayout, this::closeDetails);
        this.updateTargetFilterController = new UpdateTargetFilterController(uiDependencies, targetFilterManagement,
                targetFilterAddUpdateLayout, this::closeDetails);

        buildHeader();
    }

    @Override
    protected void init() {
        super.init();
        setHeightUndefined();
    }

    @Override
    public void buildHeader() {
        super.buildHeader();

        addComponent(targetFilterAddUpdateLayout.getRootComponent());
    }

    private void closeDetails() {
        uiState.setSelectedFilterId(null);
        uiState.setSelectedFilterName("");

        targetFilterAddUpdateLayout.setEntity(null);
        targetFilterAddUpdateLayout.filterTargetListByQuery(null);
        targetFilterAddUpdateLayout.removeDownloaderExtension();

        eventBus.publish(CommandTopics.CHANGE_LAYOUT_VISIBILITY, this, new LayoutVisibilityEventPayload(
                VisibilityType.HIDE, EventLayout.TARGET_FILTER_QUERY_FORM, EventView.TARGET_FILTER));
    }

    /**
     * Show add filter layout
     */
    public void showAddFilterLayout() {
        uiState.setCurrentMode(Mode.CREATE);

        targetFilterAddUpdateLayout.filterTargetListByQuery(null);
        doShowAddFilterLayout(new ProxyTargetFilterQuery());
    }

    private void doShowAddFilterLayout(final ProxyTargetFilterQuery proxyEntity) {
        final String captionMessage = i18n.getMessage(UIMessageIdProvider.LABEL_CREATE_FILTER);
        showAddUpdateFilterLayout(captionMessage, addTargetFilterController, proxyEntity);
    }

    /**
     * Show edit filter layout
     *
     * @param proxyEntity
     *            ProxyTargetFilterQuery
     */
    public void showEditFilterLayout(final ProxyTargetFilterQuery proxyEntity) {
        uiState.setCurrentMode(Mode.EDIT);
        uiState.setSelectedFilterId(proxyEntity.getId());
        uiState.setSelectedFilterName(proxyEntity.getName());

        targetFilterAddUpdateLayout.filterTargetListByQuery(proxyEntity.getQuery());
        doShowEditFilterLayout(proxyEntity.getName(), proxyEntity);
    }

    private void doShowEditFilterLayout(final String caption, final ProxyTargetFilterQuery proxyEntity) {
        showAddUpdateFilterLayout(caption, updateTargetFilterController, proxyEntity);
    }

    private void showAddUpdateFilterLayout(final String captionMessage,
            final AbstractEntityWindowController<ProxyTargetFilterQuery, ProxyTargetFilterQuery, TargetFilterQuery> controller,
            final ProxyTargetFilterQuery proxyEntity) {
        headerCaptionDetails.setValue(captionMessage);
        controller.populateWithData(proxyEntity);
        targetFilterAddUpdateLayout.setSaveCallback(controller.getSaveDialogCloseListener());
        targetFilterAddUpdateLayout.setCSVExportCallback(proxyEntity);
    }

    @Override
    protected String getHeaderCaptionDetailsId() {
        return UIComponentIdProvider.TARGET_FILTER_QUERY_NAME_LABEL_ID;
    }

    @Override
    public void restoreState() {
        final ProxyTargetFilterQuery targetFilterToRestore = restoreEntityFromState();

        if (Mode.EDIT == uiState.getCurrentMode()) {
            doShowEditFilterLayout(uiState.getSelectedFilterName(), targetFilterToRestore);
        } else if (Mode.CREATE == uiState.getCurrentMode()) {
            doShowAddFilterLayout(targetFilterToRestore);
        }
    }

    private ProxyTargetFilterQuery restoreEntityFromState() {
        final ProxyTargetFilterQuery restoredEntity = new ProxyTargetFilterQuery();

        if (Mode.EDIT == uiState.getCurrentMode()) {
            restoredEntity.setId(uiState.getSelectedFilterId());
        }
        restoredEntity.setName(uiState.getNameInput());
        restoredEntity.setQuery(uiState.getFilterQueryValueInput());

        return restoredEntity;
    }

    /**
     * Check validity of target filter query.
     *
     * @return {@code true}: if header form layout is active and the target
     *         filter query is valid {@code false}: otherwise
     */
    public boolean isFilterQueryValid() {
        final Mode currentMode = uiState.getCurrentMode();
        final boolean isHeaderInActiveMode = Mode.CREATE == currentMode || Mode.EDIT == currentMode;

        return isHeaderInActiveMode && targetFilterAddUpdateLayout.isFilterQueryValid();
    }
}
