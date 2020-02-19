/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.actionhistory;

import static org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil.isNotNullOrEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.OffsetBasedPageRequest;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.management.actionhistory.ProxyAction.IsActiveDecoration;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SpringContextHelper;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.vaadin.addons.lazyquerycontainer.AbstractBeanQuery;
import org.vaadin.addons.lazyquerycontainer.QueryDefinition;

/**
 * Simple implementation of generics bean query which dynamically loads
 * {@link ProxyAction} batch of beans.
 *
 */
public class ActionBeanQuery extends AbstractBeanQuery<ProxyAction> {
    private static final long serialVersionUID = 3596912494728552516L;

    private Sort sort = new Sort(Direction.DESC, ProxyAction.PXY_ACTION_ID);
    private transient DeploymentManagement deploymentManagement;
    private transient TenantConfigurationManagement configManagement;
    private transient SystemSecurityContext systemSecurityContext;

    private String currentSelectedControllerId;
    private transient Slice<Action> firstPageActions;

    /**
     * Parametric Constructor.
     *
     * @param definition
     *            QueryDefinition contains the query properties.
     * @param queryConfig
     *            Implementation specific configuration.
     * @param sortPropertyIds
     *            The properties participating in sort.
     * @param sortStates
     *            The ascending or descending state of sort properties.
     */
    public ActionBeanQuery(final QueryDefinition definition, final Map<String, Object> queryConfig,
            final Object[] sortPropertyIds, final boolean[] sortStates) {
        super(definition, queryConfig, sortPropertyIds, sortStates);

        if (isNotNullOrEmpty(queryConfig)) {
            currentSelectedControllerId = (String) queryConfig.get(SPUIDefinitions.ACTIONS_BY_TARGET);
        }

        if (sortStates == null || sortStates.length <= 0) {
            return;
        }

        // Initialize sort
        sort = new Sort(sortStates[0] ? Direction.ASC : Direction.DESC, (String) sortPropertyIds[0]);
        // Add sort
        for (int distId = 1; distId < sortPropertyIds.length; distId++) {
            sort.and(new Sort(sortStates[distId] ? Direction.ASC : Direction.DESC, (String) sortPropertyIds[distId]));
        }
    }

    @Override
    protected ProxyAction constructBean() {
        return new ProxyAction();
    }

    @Override
    protected List<ProxyAction> loadBeans(final int startIndex, final int count) {
        List<ProxyAction> actionBeans;
        if (startIndex == 0) {
            if (firstPageActions == null) {
                firstPageActions = getDeploymentManagement().findActionsByTarget(currentSelectedControllerId,
                        new OffsetBasedPageRequest(0, SPUIDefinitions.PAGE_SIZE, sort));
            }
            actionBeans = sortOnWeightWithMultiAssignments(firstPageActions);
        } else {
            actionBeans = sortOnWeightWithMultiAssignments(
                    getDeploymentManagement().findActionsByTarget(currentSelectedControllerId,
                            new OffsetBasedPageRequest(startIndex / SPUIDefinitions.PAGE_SIZE, SPUIDefinitions.PAGE_SIZE, sort)));
        }
        return actionBeans;
    }

    private List<ProxyAction> sortActionsByWeight(final Slice<Action> firstPageActions) {
        
        final Slice<ProxyAction> weightFilledActions = fillDefaultWeightForActionsAddedInSingleAssignment(
                firstPageActions);

        final List<ProxyAction> runningActions = weightFilledActions.stream()
                .filter(action -> Action.Status.RUNNING == action.getStatus()).collect(Collectors.toList());

        Collections.sort(runningActions, Collections.reverseOrder((final ProxyAction action1,
                final ProxyAction action2) -> action1.getWeight().compareTo(action2.getWeight())));

        final List<ProxyAction> otherActions = weightFilledActions.stream()
                .filter(action -> Action.Status.RUNNING != action.getStatus()).collect(Collectors.toList());
        
        runningActions.addAll(otherActions);

        return runningActions;
    }

    private List<ProxyAction> sortOnWeightWithMultiAssignments(final Slice<Action> actions) {
        return isMultiAssignmentEnabled() ? sortActionsByWeight(actions)
                : createProxyActions(actions);
    }

    private Slice<ProxyAction> fillDefaultWeightForActionsAddedInSingleAssignment(
            final Slice<Action> actions) {

        final List<ProxyAction> finalProxyActions = new ArrayList<>();
        for (final Action action : actions) {

            // Retrieve the default weight from the Mgmt API
            final int defaultWeight = getDeploymentManagement().getWeightConsideringDefault(action);

            final ProxyAction proxyAction = createProxyActions(new SliceImpl<>(Arrays.asList(action))).get(0);
            if (proxyAction.getWeight() == null) {
                proxyAction.setWeight(defaultWeight);
            }
            finalProxyActions.add(proxyAction);
        }

        return new SliceImpl<>(finalProxyActions);
    }

    /**
     * Creates a list of {@link ProxyAction}s for presentation layer from slice
     * of {@link Action}s.
     *
     * @param actionBeans
     *            slice of {@link Action}s
     * @return list of {@link ProxyAction}s
     */
    private static List<ProxyAction> createProxyActions(final Slice<Action> actionBeans) {
        final List<ProxyAction> proxyActions = new ArrayList<>();
        for (final Action action : actionBeans) {
            final ProxyAction proxyAction = new ProxyAction();
            final String dsNameVersion = action.getDistributionSet().getName() + ":"
                    + action.getDistributionSet().getVersion();
            proxyAction.setActive(action.isActive());
            proxyAction.setIsActiveDecoration(buildIsActiveDecoration(action));
            proxyAction.setDsNameVersion(dsNameVersion);
            proxyAction.setAction(action);
            proxyAction.setId(action.getId());
            proxyAction.setLastModifiedAt(action.getLastModifiedAt());
            proxyAction.setRolloutName(action.getRollout() != null ? action.getRollout().getName() : "");
            proxyAction.setStatus(action.getStatus());
            proxyAction.setMaintenanceWindow(
                    action.hasMaintenanceSchedule() ? buildMaintenanceWindowDisplayText(action) : "");
            proxyAction.setWeight(action.getWeight().orElse(null));
            proxyActions.add(proxyAction);
        }
        return proxyActions;
    }

    private static String buildMaintenanceWindowDisplayText(final Action action) {
        return action.getMaintenanceWindowSchedule() + "/" + action.getMaintenanceWindowDuration() + "/"
                + action.getMaintenanceWindowTimeZone();
    }

    /**
     * Generates a virtual IsActiveDecoration for the presentation layer that is
     * calculated from {@link Action#isActive()} and
     * {@link Action#getActionStatus()}.
     *
     * @param action
     *            the action combined IsActiveDecoration is calculated from
     * @return IsActiveDecoration combined decoration for the presentation
     *         layer.
     */
    private static IsActiveDecoration buildIsActiveDecoration(final Action action) {
        final Action.Status status = action.getStatus();

        if (status == Action.Status.SCHEDULED) {
            return IsActiveDecoration.SCHEDULED;
        } else if (status == Action.Status.ERROR) {
            return IsActiveDecoration.IN_ACTIVE_ERROR;
        }

        return action.isActive() ? IsActiveDecoration.ACTIVE : IsActiveDecoration.IN_ACTIVE;
    }

    @Override
    protected void saveBeans(final List<ProxyAction> addedBeans, final List<ProxyAction> modifiedBeans,
            final List<ProxyAction> removedBeans) {
        // CRUD operations on Target will be done through repository methods
    }

    @Override
    public int size() {
        long size = 0;

        if (currentSelectedControllerId != null) {
            size = getDeploymentManagement().countActionsByTarget(currentSelectedControllerId);
        }
        if (size > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) size;
    }

    /**
     * Lazy gets deploymentManagement.
     *
     * @return the deploymentManagement
     */
    public DeploymentManagement getDeploymentManagement() {
        if (null == deploymentManagement) {
            deploymentManagement = SpringContextHelper.getBean(DeploymentManagement.class);
        }
        return deploymentManagement;
    }

    private TenantConfigurationManagement getTenantConfigurationManagement() {
        if (null == configManagement) {
            configManagement = SpringContextHelper.getBean(TenantConfigurationManagement.class);
        }
        return configManagement;
    }

    private SystemSecurityContext getSystemSecurityContext() {
        if (null == systemSecurityContext) {
            systemSecurityContext = SpringContextHelper.getBean(SystemSecurityContext.class);
        }
        return systemSecurityContext;
    }

    private boolean isMultiAssignmentEnabled() {
        return getSystemSecurityContext().runAsSystem(() -> getTenantConfigurationManagement()
                .getConfigurationValue(TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED, Boolean.class).getValue());
    }
}
