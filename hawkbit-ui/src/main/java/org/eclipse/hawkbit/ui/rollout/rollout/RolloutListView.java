/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout.rollout;

import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RepositoryProperties;
import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.AbstractGridComponentLayout;
import org.eclipse.hawkbit.ui.rollout.state.RolloutUIState;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.addons.lazyquerycontainer.LazyQueryContainer;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.ui.AbstractOrderedLayout;

/**
 * Rollout list view.
 */
public class RolloutListView extends AbstractGridComponentLayout {
    private static final long serialVersionUID = -2703552177439393208L;

    private final transient RolloutManagement rolloutManagement;
    private final transient RolloutGroupManagement rolloutGroupManagement;
    private final transient TargetManagement targetManagement;
    private final transient EntityFactory entityFactory;
    private final transient TargetFilterQueryManagement targetFilterQueryManagement;
    private final transient QuotaManagement quotaManagement;
    private final transient TenantConfigurationManagement tenantConfigManagement;
    private final transient SystemSecurityContext systemSecurityContext;
    private final transient RepositoryProperties repositoryProperties;

    private final SpPermissionChecker permissionChecker;
    private final RolloutUIState rolloutUIState;
    private final UINotification uiNotification;
    private final UiProperties uiProperties;

    public RolloutListView(final SpPermissionChecker permissionChecker, final RolloutUIState rolloutUIState,
            final UIEventBus eventBus, final RolloutManagement rolloutManagement,
            final TargetManagement targetManagement, final UINotification uiNotification,
            final UiProperties uiProperties, final EntityFactory entityFactory, final VaadinMessageSource i18n,
            final TargetFilterQueryManagement targetFilterQueryManagement,
            final RolloutGroupManagement rolloutGroupManagement, final QuotaManagement quotaManagement,
            final TenantConfigurationManagement tenantConfigManagement,
            final SystemSecurityContext systemSecurityContext,
            final RepositoryProperties repositoryProperties) {
        super(i18n, eventBus);
        this.permissionChecker = permissionChecker;
        this.rolloutUIState = rolloutUIState;
        this.rolloutManagement = rolloutManagement;
        this.rolloutGroupManagement = rolloutGroupManagement;
        this.quotaManagement = quotaManagement;
        this.targetManagement = targetManagement;
        this.uiNotification = uiNotification;
        this.uiProperties = uiProperties;
        this.entityFactory = entityFactory;
        this.targetFilterQueryManagement = targetFilterQueryManagement;
        this.tenantConfigManagement = tenantConfigManagement;
        this.systemSecurityContext = systemSecurityContext;
        this.repositoryProperties = repositoryProperties;

        init();
    }

    @Override
    protected boolean doSubscribeToEventBus() {
        return false;
    }

    @Override
    public AbstractOrderedLayout createGridHeader() {
        return new RolloutListHeader(permissionChecker, rolloutUIState, getEventBus(), rolloutManagement,
                targetManagement, uiNotification, uiProperties, entityFactory, getI18n(), targetFilterQueryManagement,
                rolloutGroupManagement, quotaManagement, tenantConfigManagement, systemSecurityContext,
                repositoryProperties);
    }

    @Override
    public AbstractGrid<LazyQueryContainer> createGrid() {
        return new RolloutListGrid(getI18n(), getEventBus(), rolloutManagement, uiNotification, rolloutUIState,
                permissionChecker, targetManagement, entityFactory, uiProperties, targetFilterQueryManagement,
                rolloutGroupManagement, quotaManagement, tenantConfigManagement, systemSecurityContext,
                repositoryProperties);
    }

}
