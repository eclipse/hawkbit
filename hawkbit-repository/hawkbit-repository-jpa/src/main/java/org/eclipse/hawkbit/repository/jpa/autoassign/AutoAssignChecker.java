/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.autoassign;

import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.PersistenceException;

import org.eclipse.hawkbit.exception.AbstractServerRtException;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.jpa.utils.DeploymentHelper;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.DeploymentRequest;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks if targets need a new distribution set (DS) based on the target filter
 * queries and assigns the new DS when necessary. First all target filter
 * queries are listed. For every target filter query (TFQ) the auto assign DS is
 * retrieved. All targets get listed per target filter query, that match the TFQ
 * and that don't have the auto assign DS in their action history.
 */
public class AutoAssignChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoAssignChecker.class);

    private final TargetFilterQueryManagement targetFilterQueryManagement;

    private final TargetManagement targetManagement;

    private final DeploymentManagement deploymentManagement;

    private final PlatformTransactionManager transactionManager;

    /**
     * Maximum for target filter queries with auto assign DS Maximum for targets
     * that are fetched in one turn
     */
    private static final int PAGE_SIZE = 1000;

    /**
     * The message which is added to the action status when a distribution set
     * is assigned to an target. First %s is the name of the target filter.
     */
    private static final String ACTION_MESSAGE = "Auto assignment by target filter: %s";

    /**
     * Instantiates a new auto assign checker
     *
     * @param targetFilterQueryManagement
     *            to get all target filter queries
     * @param targetManagement
     *            to get targets
     * @param deploymentManagement
     *            to assign distribution sets to targets
     * @param transactionManager
     *            to run transactions
     */
    public AutoAssignChecker(final TargetFilterQueryManagement targetFilterQueryManagement,
            final TargetManagement targetManagement, final DeploymentManagement deploymentManagement,
            final PlatformTransactionManager transactionManager) {
        this.targetFilterQueryManagement = targetFilterQueryManagement;
        this.targetManagement = targetManagement;
        this.deploymentManagement = deploymentManagement;
        this.transactionManager = transactionManager;
    }

    /**
     * Checks all target filter queries with an auto assign distribution set and
     * triggers the check and assignment to targets that don't have the design
     * DS yet
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void check() {
        LOGGER.debug("Auto assigned check call");

        final PageRequest pageRequest = PageRequest.of(0, PAGE_SIZE);

        final Page<TargetFilterQuery> filterQueries = targetFilterQueryManagement.findWithAutoAssignDS(pageRequest);

        // we should ensure that the filter queries are executed
        // in the order of weights
        for (final TargetFilterQuery filterQuery : filterQueries) {
            checkByTargetFilterQueryAndAssignDS(filterQuery);
        }

    }

    /**
     * Fetches the distribution set, gets all controllerIds and assigns the DS
     * to them. Catches PersistenceException and own exceptions derived from
     * AbstractServerRtException
     *
     * @param targetFilterQuery
     *            the target filter query
     */
    private void checkByTargetFilterQueryAndAssignDS(final TargetFilterQuery targetFilterQuery) {
        try {
            final DistributionSet distributionSet = targetFilterQuery.getAutoAssignDistributionSet();

            int count;
            do {

                count = runTransactionalAssignment(targetFilterQuery, distributionSet.getId());

            } while (count == PAGE_SIZE);

        } catch (PersistenceException | AbstractServerRtException e) {
            LOGGER.error("Error during auto assign check of target filter query " + targetFilterQuery.getId(), e);
        }

    }

    /**
     * Runs one page of target assignments within a dedicated transaction
     *
     * @param targetFilterQuery
     *            the target filter query
     * @param dsId
     *            distribution set id to assign
     * @return count of targets
     */
    private int runTransactionalAssignment(final TargetFilterQuery targetFilterQuery, final Long dsId) {
        final String actionMessage = String.format(ACTION_MESSAGE, targetFilterQuery.getName());

        return DeploymentHelper.runInNewTransaction(transactionManager, "autoAssignDSToTargets",
                Isolation.READ_COMMITTED.value(), status -> {
                    final List<DeploymentRequest> deploymentRequests = createAssignmentRequests(
                            targetFilterQuery.getQuery(), dsId, targetFilterQuery.getAutoAssignActionType(),
                            targetFilterQuery.getAutoAssignWeight().orElse(null), PAGE_SIZE);
                    final int count = deploymentRequests.size();
                    if (count > 0) {
                        deploymentManagement.assignDistributionSetsAcceptNullWeight(deploymentRequests, actionMessage);
                    }
                    return count;
                });
    }

    /**
     * Gets all matching targets with the designated action from the target
     * management
     *
     * @param targetFilterQuery
     *            the query the targets have to match
     * @param dsId
     *            dsId the targets are not allowed to have in their action
     *            history
     * @param type
     *            action type for targets auto assignment
     * @param count
     *            maximum amount of targets to retrieve
     * @return list of targets with action type
     */
    private List<DeploymentRequest> createAssignmentRequests(final String targetFilterQuery, final Long dsId,
            final ActionType type, final Integer weight, final int count) {
        final Page<Target> targets = targetManagement.findByTargetFilterQueryAndNonDS(PageRequest.of(0, count), dsId,
                targetFilterQuery);
        // the action type is set to FORCED per default (when not explicitly
        // specified)
        final ActionType autoAssignActionType = type == null ? ActionType.FORCED : type;

        return targets.getContent().stream().map(t -> DeploymentManagement.deploymentRequest(t.getControllerId(), dsId)
                .setActionType(autoAssignActionType).setWeight(weight).build()).collect(Collectors.toList());
    }

}
