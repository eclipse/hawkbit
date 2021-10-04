/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettag.targettype;

import com.vaadin.data.Binder;
import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetType;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyType;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

/**
 * Builder for Distribution set type window layout component
 */
public class TargetTypeWindowLayoutComponentBuilder {

    private final VaadinMessageSource i18n;
    private final DistributionSetTypeManagement distributionSetTypeManagement;

    /**
     * Constructor for DsTypeWindowLayoutComponentBuilder
     *
     * @param i18n
     *          VaadinMessageSource
     * @param distributionSetTypeManagement
     *          distributionSetTypeManagement
     */
    public TargetTypeWindowLayoutComponentBuilder(final VaadinMessageSource i18n,
                                                  final DistributionSetTypeManagement distributionSetTypeManagement) {
        this.i18n = i18n;
        this.distributionSetTypeManagement = distributionSetTypeManagement;
    }

    /**
     * Create distribution set software module layout
     *
     * @param binder
     *          Vaadin binder
     *
     * @return layout of distribution set software module selection
     */
    public TargetTypeDsTypeSelectLayout createTargetTypeDsSelectLayout(final Binder<ProxyTargetType> binder) {

        final TargetTypeDsTypeSelectLayout targetTypeDsTypeSelectLayout = new TargetTypeDsTypeSelectLayout(i18n, distributionSetTypeManagement);
        targetTypeDsTypeSelectLayout.setRequiredIndicatorVisible(false);

        binder.forField(targetTypeDsTypeSelectLayout)
              .bind(ProxyType::getSelectedSmTypes, ProxyType::setSelectedSmTypes);

        return targetTypeDsTypeSelectLayout;
    }

}
