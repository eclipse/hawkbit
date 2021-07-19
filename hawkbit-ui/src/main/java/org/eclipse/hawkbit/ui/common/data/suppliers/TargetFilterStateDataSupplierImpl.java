/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.data.suppliers;

import java.io.Serializable;

import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.ui.common.data.mappers.TargetToProxyTargetMapper;
import org.eclipse.hawkbit.ui.common.data.providers.TargetFilterStateDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;

import com.vaadin.data.provider.DataCommunicator;
import com.vaadin.data.provider.DataProvider;

/**
 * Target backend data retrieval provider for Filter View default
 * implementation.
 *
 */
public class TargetFilterStateDataSupplierImpl implements TargetFilterStateDataSupplier, Serializable {
    private static final long serialVersionUID = 1L;

    private final TargetFilterStateDataProvider dataProvider;
    private final DataCommunicator<ProxyTarget> dataCommunicator;

    /**
     * Constructor.
     *
     * @param targetManagement
     *            Target Management
     * @param targetToProxyTargetMapper
     *            Backend to UI Proxy target entity mapper
     */
    public TargetFilterStateDataSupplierImpl(final TargetManagement targetManagement,
            final TargetToProxyTargetMapper targetToProxyTargetMapper) {
        this.dataProvider = new TargetFilterStateDataProvider(targetManagement, targetToProxyTargetMapper);
        this.dataCommunicator = new DataCommunicator<>();
    }

    @Override
    public DataProvider<ProxyTarget, String> dataProvider() {
        return dataProvider;
    }

    @Override
    public DataCommunicator<ProxyTarget> dataCommunicator() {
        return dataCommunicator;
    }

}
