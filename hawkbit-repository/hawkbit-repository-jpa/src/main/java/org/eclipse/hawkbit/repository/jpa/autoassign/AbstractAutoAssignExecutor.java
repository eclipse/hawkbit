/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.autoassign;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.autoassign.AutoAssignExecutor;
import org.eclipse.hawkbit.repository.jpa.TransactionExecutionException;
import org.eclipse.hawkbit.repository.jpa.utils.DeploymentHelper;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.DeploymentRequest;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.StringUtils;

/**
 * Abstract implementation of an AutoAssignExecutor
 */
public abstract class AbstractAutoAssignExecutor implements AutoAssignExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAutoAssignExecutor.class);

    /**
     * The message which is added to the action status when a distribution set is
     * assigned to an target. First %s is the name of the target filter.
     */
    private static final String ACTION_MESSAGE = "Auto assignment by target filter: %s";
    
    /**
     * Maximum for target filter queries with auto assign DS activated.
     */
    private static final int PAGE_SIZE = 1000;

    private final TargetFilterQueryManagement targetFilterQueryManagement;

    private final DeploymentManagement deploymentManagement;

    private final PlatformTransactionManager transactionManager;

    private final TenantAware tenantAware;

    /**
     * Constructor
     * 
     * @param targetFilterQueryManagement
     *            to get all target filter queries
     * @param deploymentManagement
     *            to assign distribution sets to targets
     * @param transactionManager
     *            to run transactions
     * @param tenantAware
     *            to handle the tenant context
     */
    protected AbstractAutoAssignExecutor(final TargetFilterQueryManagement targetFilterQueryManagement,
            final DeploymentManagement deploymentManagement, final PlatformTransactionManager transactionManager,
            final TenantAware tenantAware) {
        this.targetFilterQueryManagement = targetFilterQueryManagement;
        this.deploymentManagement = deploymentManagement;
        this.transactionManager = transactionManager;
        this.tenantAware = tenantAware;
    }

    protected TargetFilterQueryManagement getTargetFilterQueryManagement() {
        return targetFilterQueryManagement;
    }

    protected DeploymentManagement getDeploymentManagement() {
        return deploymentManagement;
    }

    protected PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    protected TenantAware getTenantAware() {
        return tenantAware;
    }

    protected void forEachFilterWithAutoAssignDS(final Consumer<TargetFilterQuery> consumer) {
        Page<TargetFilterQuery> filterQueries;
        Pageable query = PageRequest.of(0, PAGE_SIZE);

        do {
            filterQueries = targetFilterQueryManagement.findWithAutoAssignDS(query);

            filterQueries.forEach(filterQuery -> {
                try {
                    runInUserContext(filterQuery, () -> consumer.accept(filterQuery));
                } catch (final RuntimeException ex) {
                    LOGGER.debug(
                            "Exception on forEachFilterWithAutoAssignDS execution for tenant {} with filter id {}. Continue with next filter query.",
                            filterQuery.getTenant(), filterQuery.getId(), ex);
                    LOGGER.error(
                            "Exception on forEachFilterWithAutoAssignDS execution for tenant {} with filter id {} and error message [{}]. "
                                    + "Continue with next filter query.",
                            filterQuery.getTenant(), filterQuery.getId(), ex.getMessage());
                }
            });
        } while ((query = filterQueries.nextPageable()) != Pageable.unpaged());
    }

    /**
     * Runs target assignments within a dedicated transaction for a given list of
     * controllerIDs
     * 
     * @param targetFilterQuery
     *            the target filter query
     * @param controllerIds
     *            the controllerIDs
     * @return count of targets
     */
    protected int runTransactionalAssignment(final TargetFilterQuery targetFilterQuery,
            final List<String> controllerIds) throws TransactionExecutionException {
        final String actionMessage = String.format(ACTION_MESSAGE, targetFilterQuery.getName());

        return DeploymentHelper.runInNewTransaction(getTransactionManager(), "autoAssignDSToTargets",
                Isolation.READ_COMMITTED.value(), status -> {

                    final List<DeploymentRequest> deploymentRequests = mapToDeploymentRequests(controllerIds,
                            targetFilterQuery);

                    final int count = deploymentRequests.size();
                    if (count > 0) {
                        getDeploymentManagement().assignDistributionSets(
                                getAutoAssignmentInitiatedBy(targetFilterQuery), deploymentRequests, actionMessage);
                    }
                    return count;
                });
    }

    /**
     * Creates a list of {@link DeploymentRequest} for given list of controllerIds
     * and {@link TargetFilterQuery}
     *
     * @param controllerIds
     *            list of controllerIds
     * @param filterQuery
     *            the query the targets have to match
     * @return list of deployment request
     */
    protected List<DeploymentRequest> mapToDeploymentRequests(final List<String> controllerIds,
            final TargetFilterQuery filterQuery) {
        // the action type is set to FORCED per default (when not explicitly
        // specified)
        final Action.ActionType autoAssignActionType = filterQuery.getAutoAssignActionType() == null
                ? Action.ActionType.FORCED
                : filterQuery.getAutoAssignActionType();

        return controllerIds.stream()
                .map(controllerId -> DeploymentManagement
                        .deploymentRequest(controllerId, filterQuery.getAutoAssignDistributionSet().getId())
                        .setActionType(autoAssignActionType).setWeight(filterQuery.getAutoAssignWeight().orElse(null))
                        .build())
                .collect(Collectors.toList());
    }

    protected void runInUserContext(final TargetFilterQuery targetFilterQuery, final Runnable handler) {
        DeploymentHelper.runInNonSystemContext(handler,
                () -> Objects.requireNonNull(getAutoAssignmentInitiatedBy(targetFilterQuery)), tenantAware);
    }

    protected static String getAutoAssignmentInitiatedBy(final TargetFilterQuery targetFilterQuery) {
        return StringUtils.isEmpty(targetFilterQuery.getAutoAssignInitiatedBy()) ? targetFilterQuery.getCreatedBy()
                : targetFilterQuery.getAutoAssignInitiatedBy();
    }
}
