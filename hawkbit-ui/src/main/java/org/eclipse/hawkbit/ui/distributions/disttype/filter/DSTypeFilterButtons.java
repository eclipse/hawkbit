/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.distributions.disttype.filter;

import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.model.DistributionSetType;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.mappers.TypeToProxyTypeMapper;
import org.eclipse.hawkbit.ui.common.data.providers.DistributionSetTypeDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyType;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.filterlayout.AbstractTypeFilterButtons;
import org.eclipse.hawkbit.ui.common.state.TypeFilterLayoutUiState;
import org.eclipse.hawkbit.ui.distributions.disttype.DsTypeWindowBuilder;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.ui.Window;

/**
 * Distribution Set Type filter buttons.
 */
public class DSTypeFilterButtons extends AbstractTypeFilterButtons {
    private static final long serialVersionUID = 1L;

    private final transient DistributionSetTypeManagement distributionSetTypeManagement;
    private final transient DsTypeWindowBuilder dsTypeWindowBuilder;
    private final transient SystemManagement systemManagement;

    /**
     * Constructor
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param distributionSetTypeManagement
     *            DistributionSetTypeManagement
     * @param systemManagement
     *            SystemManagement
     * @param dsTypeWindowBuilder
     *            DsTypeWindowBuilder
     * @param typeFilterLayoutUiState
     *            TypeFilterLayoutUiState
     */
    public DSTypeFilterButtons(final CommonUiDependencies uiDependencies,
            final DistributionSetTypeManagement distributionSetTypeManagement, final SystemManagement systemManagement,
            final DsTypeWindowBuilder dsTypeWindowBuilder, final TypeFilterLayoutUiState typeFilterLayoutUiState) {
        super(uiDependencies, typeFilterLayoutUiState);

        this.distributionSetTypeManagement = distributionSetTypeManagement;
        this.dsTypeWindowBuilder = dsTypeWindowBuilder;
        this.systemManagement = systemManagement;

        init();
        setDataProvider(
                new DistributionSetTypeDataProvider<>(distributionSetTypeManagement, new TypeToProxyTypeMapper<>()));
    }

    @Override
    public String getGridId() {
        return UIComponentIdProvider.DISTRIBUTION_SET_TYPE_TABLE_ID;
    }

    @Override
    protected String getMessageKeyEntityTypeSing() {
        return "caption.entity.distribution.type";
    }

    @Override
    protected String getMessageKeyEntityTypePlur() {
        return "caption.entity.distribution.types";
    }

    @Override
    protected String getFilterButtonIdPrefix() {
        return UIComponentIdProvider.DISTRIBUTION_SET_TYPE_ID_PREFIXS;
    }

    @Override
    protected boolean isDefaultType(final ProxyType type) {
        final DistributionSetType defaultDsType = systemManagement.getTenantMetadata().getDefaultDsType();

        return defaultDsType != null && defaultDsType.getName().equals(type.getName());
    }

    @Override
    protected Class<? extends ProxyIdentifiableEntity> getFilterMasterEntityType() {
        return ProxyDistributionSet.class;
    }

    @Override
    protected EventView getView() {
        return EventView.DISTRIBUTIONS;
    }

    @Override
    protected void deleteType(final ProxyType typeToDelete) {
        distributionSetTypeManagement.delete(typeToDelete.getId());
    }

    @Override
    protected Window getUpdateWindow(final ProxyType clickedFilter) {
        return dsTypeWindowBuilder.getWindowForUpdate(clickedFilter);
    }

    @Override
    protected boolean typeExists(final Long typeId) {
        return distributionSetTypeManagement.exists(typeId);
    }
}
