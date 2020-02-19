/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.actionhistory;

import java.util.Optional;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.AbstractGridComponentLayout;
import org.eclipse.hawkbit.ui.common.grid.DefaultGridHeader;
import org.eclipse.hawkbit.ui.common.grid.DefaultGridHeader.AbstractHeaderMaximizeSupport;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEventType;
import org.eclipse.hawkbit.ui.management.event.ManagementUIEvent;
import org.eclipse.hawkbit.ui.management.event.TargetTableEvent;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.StringUtils;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.ui.UI;

/**
 * Layout responsible for action-history-grid and the corresponding header.
 */
public class ActionHistoryLayout extends AbstractGridComponentLayout {
    private static final long serialVersionUID = -3766179797384539821L;

    private final transient DeploymentManagement deploymentManagement;
    private final UINotification notification;
    private final ManagementUIState managementUIState;

    private transient AbstractGrid<?>.DetailsSupport details;
    private Long masterForDetails;

    private final String actionHistoryCaption;
    private final SpPermissionChecker permChecker;
    private transient TenantConfigurationManagement configManagement;
    private transient SystemSecurityContext systemSecurityContext;

    /**
     * Constructor.
     *
     * @param i18n
     * @param deploymentManagement
     * @param eventBus
     * @param notification
     * @param managementUIState
     * @param permChecker
     */
    public ActionHistoryLayout(final VaadinMessageSource i18n, final DeploymentManagement deploymentManagement,
            final UIEventBus eventBus, final UINotification notification, final ManagementUIState managementUIState,
            final SpPermissionChecker permChecker, final TenantConfigurationManagement configManagement,
            final SystemSecurityContext systemSecurityContext) {
        super(i18n, eventBus);
        this.deploymentManagement = deploymentManagement;
        this.notification = notification;
        this.managementUIState = managementUIState;
        this.permChecker = permChecker;
        this.configManagement = configManagement;
        this.systemSecurityContext = systemSecurityContext;
        actionHistoryCaption = getActionHistoryCaption();
        init();
    }

    private final String getActionHistoryCaption() {
        return getActionHistoryCaption(null);
    }

    private String getActionHistoryCaption(final String targetName) {
        final String caption;
        if (StringUtils.hasText(targetName)) {
            caption = getI18n().getMessage(UIMessageIdProvider.CAPTION_ACTION_HISTORY_FOR, targetName);
        } else {
            caption = getI18n().getMessage(UIMessageIdProvider.CAPTION_ACTION_HISTORY);
        }

        return caption;
    }

    @Override
    public ActionHistoryHeader createGridHeader() {
        return new ActionHistoryHeader(managementUIState).init();
    }

    @Override
    public ActionHistoryGrid createGrid() {
        return new ActionHistoryGrid(getI18n(), deploymentManagement, getEventBus(), notification, managementUIState,
                permChecker, configManagement, systemSecurityContext);
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final TargetTableEvent targetUIEvent) {
        final Optional<Long> targetId = managementUIState.getLastSelectedTargetId();
        if (BaseEntityEventType.SELECTED_ENTITY == targetUIEvent.getEventType()) {
            setData(getI18n().getMessage(UIMessageIdProvider.MESSAGE_DATA_AVAILABLE));
            UI.getCurrent().access(() -> populateActionHistoryDetails(targetUIEvent.getEntity()));
        } else if (BaseEntityEventType.REMOVE_ENTITY == targetUIEvent.getEventType() && targetId.isPresent()
                && targetUIEvent.getEntityIds().contains(targetId.get())) {
            setData(getI18n().getMessage(UIMessageIdProvider.MESSAGE_NO_DATA));
            UI.getCurrent().access(this::populateActionHistoryDetails);
        }
    }

    /**
     * Override default registration for selection propagation in order to interrupt
     * update cascade in minimized state to prevent updates on invisible
     * action-status-grid and message-grid.
     * <p>
     * The master selection is stored and propagation is performed as soon as the
     * state changes to maximize and hence the dependent grids are updated.
     */
    @Override
    public void registerDetails(final AbstractGrid<?>.DetailsSupport details) {
        this.details = details;
        getGrid().addSelectionListener(event -> {
            masterForDetails = (Long) event.getSelected().stream().findFirst().orElse(null);
            if (managementUIState.isActionHistoryMaximized()) {
                details.populateMasterDataAndRecalculateContainer(masterForDetails);
            }
        });
    }

    /**
     * Populate action header and table for the target.
     *
     * @param target
     *            the target
     */
    public void populateActionHistoryDetails(final Target target) {
        if (null != target) {
            ((ActionHistoryHeader) getHeader()).updateActionHistoryHeader(target.getName());
            ((ActionHistoryGrid) getGrid()).populateSelectedTarget(target);
        } else {
            ((ActionHistoryHeader) getHeader()).updateActionHistoryHeader(" ");
        }
    }

    /**
     * Populate empty action header and empty table for empty selection.
     */
    public void populateActionHistoryDetails() {
        ((ActionHistoryHeader) getHeader()).updateActionHistoryHeader(" ");
        ((ActionHistoryGrid) getGrid()).populateSelectedTarget(null);
    }

    /**
     * Header for ActionHistory with maximize-support.
     */
    class ActionHistoryHeader extends DefaultGridHeader {
        private static final long serialVersionUID = 1L;

        /**
         * Constructor.
         *
         * @param managementUIState
         */
        ActionHistoryHeader(final ManagementUIState managementUIState) {
            super(managementUIState, actionHistoryCaption, getI18n());
            this.setHeaderMaximizeSupport(
                    new ActionHistoryHeaderMaxSupport(this, SPUIDefinitions.EXPAND_ACTION_HISTORY));
        }

        /**
         * Initializes the header.
         */
        @Override
        public ActionHistoryHeader init() {
            super.init();
            restorePreviousState();
            return this;
        }

        /**
         * Updates header with target name.
         *
         * @param targetName
         *            name of the target
         */
        public void updateActionHistoryHeader(final String targetName) {
            updateTitle(targetName, UIMessageIdProvider.CAPTION_ACTION_HISTORY_FOR,
                    UIMessageIdProvider.CAPTION_ACTION_HISTORY);
        }

        /**
         * Restores the previous min-max state.
         */
        private void restorePreviousState() {
            if (hasHeaderMaximizeSupport() && managementUIState.isActionHistoryMaximized()) {
                getHeaderMaximizeSupport().showMinIcon();
            }
        }
    }

    /**
     * Min-max support for header.
     */
    class ActionHistoryHeaderMaxSupport extends AbstractHeaderMaximizeSupport {

        private final DefaultGridHeader abstractGridHeader;

        /**
         * Constructor.
         *
         * @param abstractGridHeader
         * @param maximizeButtonId
         */
        protected ActionHistoryHeaderMaxSupport(final DefaultGridHeader abstractGridHeader,
                final String maximizeButtonId) {
            abstractGridHeader.super(maximizeButtonId);
            this.abstractGridHeader = abstractGridHeader;
        }

        @Override
        protected void maximize() {
            details.populateMasterDataAndRecreateContainer(masterForDetails);
            getEventBus().publish(this, ManagementUIEvent.MAX_ACTION_HISTORY);
        }

        @Override
        protected void minimize() {
            getEventBus().publish(this, ManagementUIEvent.MIN_ACTION_HISTORY);
        }

        /**
         * Gets the grid header the maximize support is for.
         *
         * @return grid header
         */
        protected DefaultGridHeader getGridHeader() {
            return abstractGridHeader;
        }
    }

}
