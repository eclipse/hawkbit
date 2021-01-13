/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RepositoryConstants;
import org.eclipse.hawkbit.repository.jpa.configuration.Constants;
import org.eclipse.hawkbit.repository.jpa.executor.AfterTransactionCommitExecutor;
import org.eclipse.hawkbit.repository.jpa.model.JpaAction;
import org.eclipse.hawkbit.repository.jpa.model.JpaActionStatus;
import org.eclipse.hawkbit.repository.jpa.model.JpaDistributionSet;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget;
import org.eclipse.hawkbit.repository.jpa.specifications.SpecificationsBuilder;
import org.eclipse.hawkbit.repository.jpa.specifications.TargetSpecifications;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.repository.model.TargetWithActionType;
import org.eclipse.hawkbit.repository.model.helper.EventPublisherHolder;

import com.google.common.collect.Lists;

/**
 * AbstractDsAssignmentStrategy for offline assignments, i.e. not managed by
 * hawkBit.
 *
 */
public class OfflineDsAssignmentStrategy extends AbstractDsAssignmentStrategy {

    OfflineDsAssignmentStrategy(final TargetRepository targetRepository,
            final AfterTransactionCommitExecutor afterCommit, final EventPublisherHolder eventPublisherHolder,
            final ActionRepository actionRepository, final ActionStatusRepository actionStatusRepository,
            final QuotaManagement quotaManagement, final BooleanSupplier multiAssignmentsConfig) {
        super(targetRepository, afterCommit, eventPublisherHolder, actionRepository, actionStatusRepository,
                quotaManagement, multiAssignmentsConfig);
    }

    @Override
    void sendTargetUpdatedEvents(final DistributionSet set, final List<JpaTarget> targets) {
        targets.forEach(target -> {
            target.setUpdateStatus(TargetUpdateStatus.IN_SYNC);
            sendTargetUpdatedEvent(target);
        });
    }

    @Override
    public List<JpaTarget> findTargetsForAssignment(final List<String> controllerIDs, final long setId) {
        final Function<List<String>, List<JpaTarget>> mapper;
        if (isMultiAssignmentsEnabled()) {
            mapper = targetRepository::findAllByControllerId;
        } else {
            mapper = ids -> targetRepository.findAll(SpecificationsBuilder.combineWithAnd(
                    Arrays.asList(TargetSpecifications.hasControllerIdAndAssignedDistributionSetIdNot(ids, setId),
                            TargetSpecifications.notEqualToTargetUpdateStatus(TargetUpdateStatus.PENDING))));
        }
        return Lists.partition(controllerIDs, Constants.MAX_ENTRIES_IN_STATEMENT).stream().map(mapper)
                .flatMap(List::stream).collect(Collectors.toList());
    }

    @Override
    public Set<Long> cancelActiveActions(final List<List<Long>> targetIds) {
        return Collections.emptySet();
    }

    @Override
    void closeActiveActions(final List<List<Long>> targetIds) {
        // Not supported by offline case
    }

    @Override
    void closeActiveActions(Long targetId) {
        // Not supported by offline case
    }

    @Override
    void cancelActiveActions(Long targetId) {
        // Not supported by offline case
    }

    @Override
    void setAssignedDistributionSetAndTargetStatus(final JpaDistributionSet set, final List<List<Long>> targetIds,
            final String currentUser) {
        targetIds.forEach(tIds -> targetRepository.setAssignedAndInstalledDistributionSetAndUpdateStatus(
                TargetUpdateStatus.IN_SYNC, set, System.currentTimeMillis(), currentUser, tIds));
    }

    @Override
    void setAssignedDistributionSetAndTargetStatus(DistributionSet dSet, Long targetId, String currentUser) {
        throw new UnsupportedOperationException(
                "Method 'setAssignedDistributionSetAndTargetStatus(DistributionSet dSet, Long targetId, String currentUser)'"
                        + " not supported for Offline Assignment Strategy");
    }

    @Override
    protected JpaAction createTargetAction(final String initiatedBy, final TargetWithActionType targetWithActionType,
            final List<JpaTarget> targets, final JpaDistributionSet set) {
        final JpaAction result = super.createTargetAction(initiatedBy, targetWithActionType, targets, set);
        if (result != null) {
            result.setStatus(Status.FINISHED);
            result.setActive(Boolean.FALSE);
        }
        return result;
    }

    @Override
    protected JpaActionStatus createActionStatus(final JpaAction action, final String actionMessage) {
        final JpaActionStatus result = super.createActionStatus(action, actionMessage);
        result.setStatus(Status.FINISHED);
        result.addMessage(RepositoryConstants.SERVER_MESSAGE_PREFIX + "Action reported as offline deployment");
        return result;
    }

    @Override
    void sendDeploymentEvents(final DistributionSetAssignmentResult assignmentResult) {
        // no need to send deployment events in the offline case
    }

    @Override
    void sendDeploymentEvents(final List<DistributionSetAssignmentResult> assignmentResults) {
        // no need to send deployment events in the offline case
    }

}
