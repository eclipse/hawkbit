/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout;

import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RepositoryProperties;
import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.AbstractHawkbitUI;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.rollout.event.RolloutEvent;
import org.eclipse.hawkbit.ui.rollout.rollout.RolloutListView;
import org.eclipse.hawkbit.ui.rollout.rolloutgroup.RolloutGroupsListView;
import org.eclipse.hawkbit.ui.rollout.rolloutgrouptargets.RolloutGroupTargetsListView;
import org.eclipse.hawkbit.ui.rollout.state.RolloutUIState;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.spring.annotation.UIScope;
import com.vaadin.ui.VerticalLayout;

/**
 * Rollout management view.
 */
@UIScope
@SpringView(name = RolloutView.VIEW_NAME, ui = AbstractHawkbitUI.class)
public class RolloutView extends VerticalLayout implements View {

    private static final long serialVersionUID = -6199789714170913988L;

    public static final String VIEW_NAME = "rollout";

    private final SpPermissionChecker permChecker;

    private final RolloutListView rolloutListView;

    private final RolloutGroupsListView rolloutGroupsListView;

    private final RolloutGroupTargetsListView rolloutGroupTargetsListView;

    private final RolloutUIState rolloutUIState;

    private final transient RolloutManagement rolloutManagement;

    private final transient EventBus.UIEventBus eventBus;

    @Autowired
    RolloutView(final SpPermissionChecker permissionChecker, final RolloutUIState rolloutUIState,
            final UIEventBus eventBus, final RolloutManagement rolloutManagement,
            final RolloutGroupManagement rolloutGroupManagement, final TargetManagement targetManagement,
            final UINotification uiNotification, final UiProperties uiProperties, final EntityFactory entityFactory,
            final VaadinMessageSource i18n, final TargetFilterQueryManagement targetFilterQueryManagement,
            final QuotaManagement quotaManagement, final TenantConfigurationManagement tenantConfigManagement,
            final SystemSecurityContext systemSecurityContext,
            final RepositoryProperties repositoryProperties) {
        this.permChecker = permissionChecker;
        this.rolloutManagement = rolloutManagement;
        this.rolloutListView = new RolloutListView(permissionChecker, rolloutUIState, eventBus, rolloutManagement,
                targetManagement, uiNotification, uiProperties, entityFactory, i18n, targetFilterQueryManagement,
                rolloutGroupManagement, quotaManagement, tenantConfigManagement, systemSecurityContext,
                repositoryProperties);
        this.rolloutGroupsListView = new RolloutGroupsListView(i18n, eventBus, rolloutGroupManagement, rolloutUIState,
                permissionChecker);
        this.rolloutGroupTargetsListView = new RolloutGroupTargetsListView(eventBus, i18n, rolloutUIState);
        this.rolloutUIState = rolloutUIState;
        this.eventBus = eventBus;
    }

    @PostConstruct
    void init() {
        setSizeFull();
        if (!(rolloutUIState.isShowRollOuts() || rolloutUIState.isShowRolloutGroups()
                || rolloutUIState.isShowRolloutGroupTargets())) {
            rolloutUIState.setShowRollOuts(true);
        }
        buildLayout();
        eventBus.subscribe(this);
    }

    @PreDestroy
    void destroy() {
        eventBus.unsubscribe(this);
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final RolloutEvent event) {
        if (event == RolloutEvent.SHOW_ROLLOUTS) {
            rolloutUIState.setShowRollOuts(true);
            rolloutUIState.setShowRolloutGroups(false);
            rolloutUIState.setShowRolloutGroupTargets(false);
            buildLayout();
        } else if (event == RolloutEvent.SHOW_ROLLOUT_GROUPS) {
            rolloutUIState.setShowRollOuts(false);
            rolloutUIState.setShowRolloutGroups(true);
            rolloutUIState.setShowRolloutGroupTargets(false);
            buildLayout();
        } else if (event == RolloutEvent.SHOW_ROLLOUT_GROUP_TARGETS) {
            rolloutUIState.setShowRollOuts(false);
            rolloutUIState.setShowRolloutGroups(false);
            rolloutUIState.setShowRolloutGroupTargets(true);
            buildLayout();
        }
    }

    private void buildLayout() {
        if (permChecker.hasRolloutReadPermission() && rolloutUIState.isShowRollOuts()) {
            showRolloutListView();
        } else if (permChecker.hasRolloutReadPermission() && rolloutUIState.isShowRolloutGroups()) {
            showRolloutGroupListView();
        } else if (permChecker.hasRolloutTargetsReadPermission() && rolloutUIState.isShowRolloutGroupTargets()) {
            showRolloutGroupTargetsListView();
        }
    }

    private void showRolloutGroupTargetsListView() {
        if (isRolloutDeleted()) {
            showRolloutListView();
            return;
        }

        rolloutGroupTargetsListView.setVisible(true);
        if (rolloutListView.isVisible()) {
            rolloutListView.setVisible(false);
        }
        if (rolloutGroupsListView.isVisible()) {
            rolloutGroupsListView.setVisible(false);
        }
        addComponent(rolloutGroupTargetsListView);
        setExpandRatio(rolloutGroupTargetsListView, 1.0F);
    }

    private void showRolloutGroupListView() {
        if (isRolloutDeleted()) {
            showRolloutListView();
            return;
        }

        rolloutGroupsListView.setVisible(true);
        if (rolloutListView.isVisible()) {
            rolloutListView.setVisible(false);
        }
        if (rolloutGroupTargetsListView.isVisible()) {
            rolloutGroupTargetsListView.setVisible(false);
        }
        addComponent(rolloutGroupsListView);
        setExpandRatio(rolloutGroupsListView, 1.0F);
    }

    private boolean isRolloutDeleted() {
        final Optional<Long> rolloutIdInState = rolloutUIState.getRolloutId();
        if (!rolloutIdInState.isPresent()) {
            return true;
        }

        final Optional<Rollout> rollout = rolloutManagement.get(rolloutIdInState.get());
        return !rollout.isPresent() || rollout.get().isDeleted();
    }

    private void showRolloutListView() {
        rolloutListView.setVisible(true);
        if (rolloutGroupsListView.isVisible()) {
            rolloutGroupsListView.setVisible(false);
        }
        if (rolloutGroupTargetsListView.isVisible()) {
            rolloutGroupTargetsListView.setVisible(false);
        }
        addComponent(rolloutListView);
        setExpandRatio(rolloutListView, 1.0F);
    }

    @Override
    public void enter(final ViewChangeEvent event) {
        // This view is constructed in the init() method()
    }

}
