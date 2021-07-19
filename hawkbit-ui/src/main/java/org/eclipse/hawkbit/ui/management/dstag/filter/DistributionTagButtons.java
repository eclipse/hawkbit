/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.dstag.filter;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.DistributionSetTagManagement;
import org.eclipse.hawkbit.repository.Identifiable;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.mappers.TagToProxyTagMapper;
import org.eclipse.hawkbit.ui.common.data.providers.DistributionSetTagDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTag;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.filterlayout.AbstractTagFilterButtons;
import org.eclipse.hawkbit.ui.common.grid.support.DragAndDropSupport;
import org.eclipse.hawkbit.ui.common.grid.support.assignment.DistributionSetsToTagAssignmentSupport;
import org.eclipse.hawkbit.ui.common.state.TagFilterLayoutUiState;
import org.eclipse.hawkbit.ui.management.dstag.DsTagWindowBuilder;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;

import com.vaadin.ui.Window;

/**
 * Class for defining the tag buttons of the distribution sets on the Deployment
 * View.
 */
public class DistributionTagButtons extends AbstractTagFilterButtons {
    private static final long serialVersionUID = 1L;

    private final transient DistributionSetTagManagement distributionSetTagManagement;
    private final transient DsTagWindowBuilder dsTagWindowBuilder;

    /**
     * Constructor for DistributionTagButtons
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param distributionSetTagManagement
     *            DistributionSetTagManagement
     * @param distributionSetManagement
     *            DistributionSetManagement
     * @param dsTagWindowBuilder
     *            DsTagWindowBuilder
     * @param distributionTagLayoutUiState
     *            TagFilterLayoutUiState
     */
    public DistributionTagButtons(final CommonUiDependencies uiDependencies,
            final DistributionSetTagManagement distributionSetTagManagement,
            final DistributionSetManagement distributionSetManagement, final DsTagWindowBuilder dsTagWindowBuilder,
            final TagFilterLayoutUiState distributionTagLayoutUiState) {
        super(uiDependencies, distributionTagLayoutUiState);

        this.distributionSetTagManagement = distributionSetTagManagement;
        this.dsTagWindowBuilder = dsTagWindowBuilder;

        final DistributionSetsToTagAssignmentSupport distributionSetsToTagAssignment = new DistributionSetsToTagAssignmentSupport(
                uiDependencies, distributionSetManagement);

        setDragAndDropSupportSupport(new DragAndDropSupport<>(this, i18n, uiNotification,
                Collections.singletonMap(UIComponentIdProvider.DIST_TABLE_ID, distributionSetsToTagAssignment),
                eventBus));
        getDragAndDropSupportSupport().ignoreSelection(true);
        getDragAndDropSupportSupport().addDragAndDrop();

        init();
        setDataProvider(new DistributionSetTagDataProvider(distributionSetTagManagement, new TagToProxyTagMapper<>()));
    }

    @Override
    public String getGridId() {
        return UIComponentIdProvider.DISTRIBUTION_TAG_TABLE_ID;
    }

    @Override
    protected String getMessageKeyEntityTypeSing() {
        return UIMessageIdProvider.CAPTION_DISTRIBUTION_TAG;
    }

    @Override
    protected String getMessageKeyEntityTypePlur() {
        return "caption.entity.distribution.tags";
    }

    @Override
    protected String getFilterButtonIdPrefix() {
        return UIComponentIdProvider.DISTRIBUTION_TAG_ID_PREFIXS;
    }

    @Override
    protected Class<? extends ProxyIdentifiableEntity> getFilterMasterEntityType() {
        return ProxyDistributionSet.class;
    }

    @Override
    protected EventView getView() {
        return EventView.DEPLOYMENT;
    }

    @Override
    protected void deleteTag(final ProxyTag tagToDelete) {
        distributionSetTagManagement.delete(tagToDelete.getName());
    }

    @Override
    protected boolean isDeletionAllowed() {
        return permissionChecker.hasDeleteRepositoryPermission();
    }

    @Override
    protected boolean isEditAllowed() {
        return permissionChecker.hasUpdateRepositoryPermission();
    }

    @Override
    protected Window getUpdateWindow(final ProxyTag clickedFilter) {
        return dsTagWindowBuilder.getWindowForUpdate(clickedFilter);
    }

    @Override
    protected Collection<Long> filterExistingTagIds(final Collection<Long> tagIds) {
        return distributionSetTagManagement.get(tagIds).stream().map(Identifiable::getId).collect(Collectors.toSet());
    }
}
