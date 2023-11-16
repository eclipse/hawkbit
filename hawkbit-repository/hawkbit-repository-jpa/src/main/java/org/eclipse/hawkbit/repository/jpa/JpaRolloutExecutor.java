/**
 * Copyright (c) 2021 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository.jpa;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RepositoryProperties;
import org.eclipse.hawkbit.repository.RolloutApprovalStrategy;
import org.eclipse.hawkbit.repository.RolloutExecutor;
import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutHelper;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.event.remote.RolloutGroupDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.RolloutStoppedEvent;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.RolloutIllegalStateException;
import org.eclipse.hawkbit.repository.jpa.executor.AfterTransactionCommitExecutor;
import org.eclipse.hawkbit.repository.jpa.model.JpaAction;
import org.eclipse.hawkbit.repository.jpa.model.JpaRollout;
import org.eclipse.hawkbit.repository.jpa.model.JpaRolloutGroup;
import org.eclipse.hawkbit.repository.jpa.model.RolloutTargetGroup;
import org.eclipse.hawkbit.repository.jpa.rollout.condition.EvaluatorNotConfiguredException;
import org.eclipse.hawkbit.repository.jpa.rollout.condition.RolloutGroupEvaluationManager;
import org.eclipse.hawkbit.repository.jpa.utils.DeploymentHelper;
import org.eclipse.hawkbit.repository.jpa.utils.QuotaHelper;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupErrorCondition;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupSuccessCondition;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.helper.EventPublisherHolder;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.util.StringUtils;

/**
 * A Jpa implementation of {@link RolloutExecutor}
 */
public class JpaRolloutExecutor extends AbstractRolloutExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JpaRolloutExecutor.class);

    /**
     * Action statuses that result in a terminated action
     */
    private static final List<Status> DEFAULT_ACTION_TERMINATION_STATUSES = Arrays.asList(Status.ERROR, Status.FINISHED,
            Status.CANCELED);
    /**
     * In case of DOWNLOAD_ONLY, actions can be finished with DOWNLOADED status.
     */
    private static final List<Status> DOWNLOAD_ONLY_ACTION_TERMINATION_STATUSES = Arrays.asList(Status.ERROR,
            Status.FINISHED, Status.CANCELED, Status.DOWNLOADED);

    private final RolloutTargetGroupRepository rolloutTargetGroupRepository;
    private final EntityManager entityManager;
    private final RolloutRepository rolloutRepository;
    private final ActionRepository actionRepository;
    private final RolloutGroupRepository rolloutGroupRepository;
    private final AfterTransactionCommitExecutor afterCommit;
    private final TenantAware tenantAware;
    private final RolloutGroupManagement rolloutGroupManagement;
    private final QuotaManagement quotaManagement;
    private final DeploymentManagement deploymentManagement;
    private final TargetManagement targetManagement;
    private final EventPublisherHolder eventPublisherHolder;
    private final PlatformTransactionManager txManager;
    private final RolloutApprovalStrategy rolloutApprovalStrategy;
    private final RolloutGroupEvaluationManager evaluationManager;
    private final RolloutManagement rolloutManagement;

    /**
     * Constructor
     */
    public JpaRolloutExecutor(final RolloutTargetGroupRepository rolloutTargetGroupRepository,
            final EntityManager entityManager, final RolloutRepository rolloutRepository,
            final ActionRepository actionRepository, final RolloutGroupRepository rolloutGroupRepository,
            final AfterTransactionCommitExecutor afterCommit, final TenantAware tenantAware,
            final RolloutGroupManagement rolloutGroupManagement, final QuotaManagement quotaManagement,
            final DeploymentManagement deploymentManagement, final TargetManagement targetManagement,
            final EventPublisherHolder eventPublisherHolder, final PlatformTransactionManager txManager,
            final RolloutApprovalStrategy rolloutApprovalStrategy,
            final RolloutGroupEvaluationManager evaluationManager, final RolloutManagement rolloutManagement,
            final RepositoryProperties repositoryProperties) {
        super(rolloutRepository, rolloutGroupRepository, actionRepository, afterCommit, entityManager,
                eventPublisherHolder, repositoryProperties.getRollouts());
        this.rolloutTargetGroupRepository = rolloutTargetGroupRepository;
        this.entityManager = entityManager;
        this.rolloutRepository = rolloutRepository;
        this.actionRepository = actionRepository;
        this.rolloutGroupRepository = rolloutGroupRepository;
        this.afterCommit = afterCommit;
        this.tenantAware = tenantAware;
        this.rolloutGroupManagement = rolloutGroupManagement;
        this.quotaManagement = quotaManagement;
        this.deploymentManagement = deploymentManagement;
        this.targetManagement = targetManagement;
        this.eventPublisherHolder = eventPublisherHolder;
        this.txManager = txManager;
        this.rolloutApprovalStrategy = rolloutApprovalStrategy;
        this.evaluationManager = evaluationManager;
        this.rolloutManagement = rolloutManagement;
    }

    @Override
    protected void handleCreateRollout(final JpaRollout rollout) {
        LOGGER.debug("handleCreateRollout called for rollout {}", rollout.getId());
        final List<RolloutGroup> rolloutGroups = rolloutGroupManagement.findByRollout(
                PageRequest.of(0, quotaManagement.getMaxRolloutGroupsPerRollout(), Sort.by(Direction.ASC, "id")),
                rollout.getId()).getContent();

        int readyGroups = 0;
        int totalTargets = 0;
        for (final RolloutGroup group : rolloutGroups) {
            if (RolloutGroupStatus.READY == group.getStatus()) {
                readyGroups++;
                totalTargets += group.getTotalTargets();
                continue;
            }

            final RolloutGroup filledGroup = fillRolloutGroupWithTargets(rollout, group);
            if (RolloutGroupStatus.READY == filledGroup.getStatus()) {
                readyGroups++;
                totalTargets += filledGroup.getTotalTargets();
            }
        }

        // When all groups are ready the rollout status can be changed to be
        // ready, too.
        if (readyGroups == rolloutGroups.size()) {
            if (!rolloutApprovalStrategy.isApprovalNeeded(rollout)) {
                rollout.setStatus(RolloutStatus.READY);
                LOGGER.debug("rollout {} creation done. Switch to READY.", rollout.getId());
            } else {
                LOGGER.debug("rollout {} creation done. Switch to WAITING_FOR_APPROVAL.", rollout.getId());
                rollout.setStatus(RolloutStatus.WAITING_FOR_APPROVAL);
                rolloutApprovalStrategy.onApprovalRequired(rollout);
            }
            rollout.setLastCheck(0);
            rollout.setTotalTargets(totalTargets);
            rolloutRepository.save(rollout);
        }
    }

    @Override
    protected void handleDeleteRollout(final JpaRollout rollout) {
        LOGGER.debug("handleDeleteRollout called for {}", rollout.getId());

        // check if there are actions beyond schedule
        boolean hardDeleteRolloutGroups = !actionRepository.existsByRolloutIdAndStatusNotIn(rollout.getId(),
                Status.SCHEDULED);
        if (hardDeleteRolloutGroups) {
            LOGGER.debug("Rollout {} has no actions other than scheduled -> hard delete", rollout.getId());
            hardDeleteRollout(rollout);
            return;
        }
        // clean up all scheduled actions
        deleteScheduledActions(rollout);

        // avoid another scheduler round and re-check if all scheduled actions
        // has been cleaned up. we flush first to ensure that the we include the
        // deletion above
        entityManager.flush();
        final boolean hasScheduledActionsLeft = actionRepository.countByRolloutIdAndStatus(rollout.getId(),
                Status.SCHEDULED) > 0;

        if (hasScheduledActionsLeft) {
            return;
        }

        // only hard delete the rollout if no actions are left for the rollout.
        // In case actions are left, they are probably are running or were
        // running before, so only soft delete.
        hardDeleteRolloutGroups = !actionRepository.existsByRolloutId(rollout.getId());
        if (hardDeleteRolloutGroups) {
            hardDeleteRollout(rollout);
            return;
        }

        // set soft delete
        rollout.setStatus(RolloutStatus.DELETED);
        rollout.setDeleted(true);
        rolloutRepository.save(rollout);

        sendRolloutGroupDeletedEvents(rollout);
    }

    @Override
    protected void handleStopRollout(final JpaRollout rollout) {
        LOGGER.debug("handleStopRollout called for {}", rollout.getId());

        if (!cleanupScheduledActions(rollout)) {
            // could not clean up scheduled actions
            return;
        }

        rolloutGroupRepository.findByRolloutAndStatusNotIn(rollout,
                Arrays.asList(RolloutGroupStatus.FINISHED, RolloutGroupStatus.ERROR)).forEach(rolloutGroup -> {
                    rolloutGroup.setStatus(RolloutGroupStatus.FINISHED);
                    rolloutGroupRepository.save(rolloutGroup);
                });

        rollout.setStatus(RolloutStatus.FINISHED);
        rolloutRepository.save(rollout);

        final List<Long> groupIds = rollout.getRolloutGroups().stream().map(RolloutGroup::getId)
                .collect(Collectors.toList());

        afterCommit.afterCommit(() -> eventPublisherHolder.getEventPublisher().publishEvent(new RolloutStoppedEvent(
                tenantAware.getCurrentTenant(), eventPublisherHolder.getApplicationId(), rollout.getId(), groupIds)));
    }

    @Override
    protected void handleReadyRollout(final JpaRollout rollout) {
        if (rollout.getStartAt() != null && rollout.getStartAt() <= System.currentTimeMillis()) {
            LOGGER.debug(
                    "handleReadyRollout called for rollout {} with autostart beyond define time. Switch to STARTING",
                    rollout.getId());
            rolloutManagement.start(rollout.getId());
        }
    }

    @Override
    protected void handleStartingRollout(final JpaRollout rollout) {
        LOGGER.debug("handleStartingRollout called for rollout {}", rollout.getId());

        if (ensureAllGroupsAreScheduled(rollout)) {
            startFirstRolloutGroup(rollout);
        }
    }

    @Override
    protected void handleRunningRollout(final JpaRollout rollout) {
        LOGGER.debug("handleRunningRollout called for rollout {}", rollout.getId());

        final List<JpaRolloutGroup> rolloutGroupsRunning = rolloutGroupRepository.findByRolloutAndStatus(rollout,
                RolloutGroupStatus.RUNNING);

        if (rolloutGroupsRunning.isEmpty()) {
            // no running rollouts, probably there was an error
            // somewhere at the latest group. And the latest group has
            // been switched from running into error state. So we need
            // to find the latest group which
            executeLatestRolloutGroup(rollout);
        } else {
            LOGGER.debug("Rollout {} has {} running groups", rollout.getId(), rolloutGroupsRunning.size());
            executeRolloutGroups(rollout, rolloutGroupsRunning);
        }

        if (isRolloutComplete(rollout)) {
            LOGGER.info("Rollout {} is finished, setting FINISHED status", rollout);
            rollout.setStatus(RolloutStatus.FINISHED);
            rolloutRepository.save(rollout);
        }
    }

    private void hardDeleteRollout(final JpaRollout rollout) {
        sendRolloutGroupDeletedEvents(rollout);
        rolloutRepository.delete(rollout);
    }

    private void sendRolloutGroupDeletedEvents(final JpaRollout rollout) {
        final List<Long> groupIds = rollout.getRolloutGroups().stream().map(RolloutGroup::getId)
                .collect(Collectors.toList());

        afterCommit.afterCommit(() -> groupIds.forEach(rolloutGroupId -> eventPublisherHolder.getEventPublisher()
                .publishEvent(new RolloutGroupDeletedEvent(tenantAware.getCurrentTenant(), rolloutGroupId,
                        JpaRolloutGroup.class, eventPublisherHolder.getApplicationId()))));
    }

    private boolean isRolloutComplete(final JpaRollout rollout) {
        // ensure that changes in the same transaction count
        entityManager.flush();
        final Long groupsActiveLeft = rolloutGroupRepository.countByRolloutIdAndStatusOrStatus(rollout.getId(),
                RolloutGroupStatus.RUNNING, RolloutGroupStatus.SCHEDULED);
        return groupsActiveLeft == 0;
    }

    private void executeLatestRolloutGroup(final JpaRollout rollout) {
        final List<JpaRolloutGroup> latestRolloutGroup = rolloutGroupRepository
                .findByRolloutAndStatusNotOrderByIdDesc(rollout, RolloutGroupStatus.SCHEDULED);
        if (latestRolloutGroup.isEmpty()) {
            return;
        }
        executeRolloutGroupSuccessAction(rollout, latestRolloutGroup.get(0));
    }

    private void executeRolloutGroups(final JpaRollout rollout, final List<JpaRolloutGroup> rolloutGroups) {
        for (final JpaRolloutGroup rolloutGroup : rolloutGroups) {

            final long targetCount = countTargetsFrom(rolloutGroup);
            if (rolloutGroup.getTotalTargets() != targetCount) {
                updateTotalTargetCount(rolloutGroup, targetCount);
            }

            // error state check, do we need to stop the whole
            // rollout because of error?
            final boolean isError = checkErrorState(rollout, rolloutGroup);
            if (isError) {
                LOGGER.info("Rollout {} {} has error, calling error action", rollout.getName(), rollout.getId());
                callErrorAction(rollout, rolloutGroup);
            } else {
                // not in error so check finished state, do we need to
                // start the next group?
                final RolloutGroupSuccessCondition finishedCondition = rolloutGroup.getSuccessCondition();
                checkFinishCondition(rollout, rolloutGroup, finishedCondition);
                if (isRolloutGroupComplete(rollout, rolloutGroup)) {
                    rolloutGroup.setStatus(RolloutGroupStatus.FINISHED);
                    rolloutGroupRepository.save(rolloutGroup);
                }
            }
        }
    }

    private void updateTotalTargetCount(final JpaRolloutGroup rolloutGroup, final long countTargetsOfRolloutGroup) {
        final JpaRollout jpaRollout = (JpaRollout) rolloutGroup.getRollout();
        final long updatedTargetCount = jpaRollout.getTotalTargets()
                - (rolloutGroup.getTotalTargets() - countTargetsOfRolloutGroup);
        jpaRollout.setTotalTargets(updatedTargetCount);
        rolloutGroup.setTotalTargets((int) countTargetsOfRolloutGroup);
        rolloutRepository.save(jpaRollout);
        rolloutGroupRepository.save(rolloutGroup);
    }

    private long countTargetsFrom(final JpaRolloutGroup rolloutGroup) {
        return rolloutGroupManagement.countTargetsOfRolloutsGroup(rolloutGroup.getId());
    }

    private void callErrorAction(final Rollout rollout, final RolloutGroup rolloutGroup) {
        try {
            evaluationManager.getErrorActionEvaluator(rolloutGroup.getErrorAction()).exec(rollout, rolloutGroup);
        } catch (final EvaluatorNotConfiguredException e) {
            LOGGER.error("Something bad happened when accessing the error action bean {}",
                    rolloutGroup.getErrorAction().name(), e);
        }
    }

    private boolean isRolloutGroupComplete(final JpaRollout rollout, final JpaRolloutGroup rolloutGroup) {
        final Long actionsLeftForRollout = ActionType.DOWNLOAD_ONLY == rollout.getActionType()
                ? actionRepository.countByRolloutAndRolloutGroupAndStatusNotIn(rollout, rolloutGroup,
                        DOWNLOAD_ONLY_ACTION_TERMINATION_STATUSES)
                : actionRepository.countByRolloutAndRolloutGroupAndStatusNotIn(rollout, rolloutGroup,
                        DEFAULT_ACTION_TERMINATION_STATUSES);
        return actionsLeftForRollout == 0;
    }

    private boolean checkErrorState(final Rollout rollout, final RolloutGroup rolloutGroup) {

        final RolloutGroupErrorCondition errorCondition = rolloutGroup.getErrorCondition();

        if (errorCondition == null) {
            // there is no error condition, so return false, don't have error.
            return false;
        }
        try {
            return evaluationManager.getErrorConditionEvaluator(errorCondition).eval(rollout, rolloutGroup,
                    rolloutGroup.getErrorConditionExp());
        } catch (final EvaluatorNotConfiguredException e) {
            LOGGER.error("Something bad happened when accessing the error condition bean {}", errorCondition.name(), e);
            return false;
        }
    }

    private boolean checkFinishCondition(final Rollout rollout, final RolloutGroup rolloutGroup,
            final RolloutGroupSuccessCondition finishCondition) {
        LOGGER.trace("Checking finish condition {} on rolloutgroup {}", finishCondition, rolloutGroup);
        try {
            final boolean isFinished = evaluationManager.getSuccessConditionEvaluator(finishCondition).eval(rollout,
                    rolloutGroup, rolloutGroup.getSuccessConditionExp());
            if (isFinished) {
                LOGGER.debug("Rolloutgroup {} is finished, starting next group", rolloutGroup);
                executeRolloutGroupSuccessAction(rollout, rolloutGroup);
            } else {
                LOGGER.debug("Rolloutgroup {} is still running", rolloutGroup);
            }
            return isFinished;
        } catch (final EvaluatorNotConfiguredException e) {
            LOGGER.error("Something bad happened when accessing the finish condition or success action bean {}",
                    finishCondition.name(), e);
            return false;
        }
    }

    private void executeRolloutGroupSuccessAction(final Rollout rollout, final RolloutGroup rolloutGroup) {
        evaluationManager.getSuccessActionEvaluator(rolloutGroup.getSuccessAction()).exec(rollout, rolloutGroup);
    }

    private void startFirstRolloutGroup(final Rollout rollout) {
        LOGGER.debug("startFirstRolloutGroup called for rollout {}", rollout.getId());
        RolloutHelper.verifyRolloutInStatus(rollout, RolloutStatus.STARTING);
        final JpaRollout jpaRollout = (JpaRollout) rollout;

        final List<JpaRolloutGroup> rolloutGroups = rolloutGroupRepository.findByRolloutOrderByIdAsc(jpaRollout);
        final JpaRolloutGroup rolloutGroup = rolloutGroups.get(0);
        if (rolloutGroup.getParent() != null) {
            throw new RolloutIllegalStateException("First Group is not the first group.");
        }

        deploymentManagement.startScheduledActionsByRolloutGroupParent(rollout.getId(),
                rollout.getDistributionSet().getId(), null);

        rolloutGroup.setStatus(RolloutGroupStatus.RUNNING);
        rolloutGroupRepository.save(rolloutGroup);

        jpaRollout.setStatus(RolloutStatus.RUNNING);
        jpaRollout.setLastCheck(0);
        rolloutRepository.save(jpaRollout);
    }

    private boolean ensureAllGroupsAreScheduled(final Rollout rollout) {
        final JpaRollout jpaRollout = (JpaRollout) rollout;

        final List<JpaRolloutGroup> groupsToBeScheduled = rolloutGroupRepository.findByRolloutAndStatus(rollout,
                RolloutGroupStatus.READY);
        final long scheduledGroups = groupsToBeScheduled.stream()
                .filter(group -> scheduleRolloutGroup(jpaRollout, group)).count();

        return scheduledGroups == groupsToBeScheduled.size();
    }

    private RolloutGroup fillRolloutGroupWithTargets(final JpaRollout rollout, final RolloutGroup group1) {
        RolloutHelper.verifyRolloutInStatus(rollout, RolloutStatus.CREATING);

        final JpaRolloutGroup group = (JpaRolloutGroup) group1;

        final String baseFilter = RolloutHelper.getTargetFilterQuery(rollout);
        final String groupTargetFilter;
        if (StringUtils.isEmpty(group.getTargetFilterQuery())) {
            groupTargetFilter = baseFilter;
        } else {
            groupTargetFilter = baseFilter + ";" + group.getTargetFilterQuery();
        }

        final List<Long> readyGroups = RolloutHelper.getGroupsByStatusIncludingGroup(rollout.getRolloutGroups(),
                RolloutGroupStatus.READY, group);

        long targetsInGroupFilter;
        if (!RolloutHelper.isRolloutRetried(rollout.getTargetFilterQuery())) {
            targetsInGroupFilter = DeploymentHelper.runInNewTransaction(txManager,
                "countAllTargetsByTargetFilterQueryAndNotInRolloutGroups",
                count -> targetManagement.countByRsqlAndNotInRolloutGroupsAndCompatible(readyGroups, groupTargetFilter,
                    rollout.getDistributionSet().getType()));
        } else {
            targetsInGroupFilter = DeploymentHelper.runInNewTransaction(txManager,
                "countByFailedRolloutAndNotInRolloutGroupsAndCompatible",
                count -> targetManagement.countByFailedRolloutAndNotInRolloutGroups(readyGroups,
                    RolloutHelper.getIdFromRetriedTargetFilter(rollout.getTargetFilterQuery())));
        }
        final long expectedInGroup = Math
                .round((double) (group.getTargetPercentage() / 100) * (double) targetsInGroupFilter);
        final long currentlyInGroup = DeploymentHelper.runInNewTransaction(txManager,
                "countRolloutTargetGroupByRolloutGroup",
                count -> rolloutTargetGroupRepository.countByRolloutGroup(group));

        // Switch the Group status to READY, when there are enough Targets in
        // the Group
        if (currentlyInGroup >= expectedInGroup) {
            group.setStatus(RolloutGroupStatus.READY);
            return rolloutGroupRepository.save(group);
        }

        try {

            long targetsLeftToAdd = expectedInGroup - currentlyInGroup;

            do {
                // Add up to TRANSACTION_TARGETS of the left targets
                // In case a TransactionException is thrown this loop aborts
                targetsLeftToAdd -= assignTargetsToGroupInNewTransaction(rollout, group, groupTargetFilter,
                        Math.min(TRANSACTION_TARGETS, targetsLeftToAdd));
            } while (targetsLeftToAdd > 0);

            group.setStatus(RolloutGroupStatus.READY);
            group.setTotalTargets(
                    DeploymentHelper.runInNewTransaction(txManager, "countRolloutTargetGroupByRolloutGroup",
                            count -> rolloutTargetGroupRepository.countByRolloutGroup(group)).intValue());
            return rolloutGroupRepository.save(group);

        } catch (final TransactionException e) {
            LOGGER.warn("Transaction assigning Targets to RolloutGroup failed", e);
            return group;
        }
    }

    private Long assignTargetsToGroupInNewTransaction(final JpaRollout rollout, final RolloutGroup group,
            final String targetFilter, final long limit) {

        return DeploymentHelper.runInNewTransaction(txManager, "assignTargetsToRolloutGroup", status -> {
            final PageRequest pageRequest = PageRequest.of(0, Math.toIntExact(limit));
            final List<Long> readyGroups = RolloutHelper.getGroupsByStatusIncludingGroup(rollout.getRolloutGroups(),
                    RolloutGroupStatus.READY, group);
            Slice<Target> targets;
            if (!RolloutHelper.isRolloutRetried(rollout.getTargetFilterQuery())) {
                targets = targetManagement.findByTargetFilterQueryAndNotInRolloutGroupsAndCompatible(
                    pageRequest, readyGroups, targetFilter, rollout.getDistributionSet().getType());
            } else {
                targets = targetManagement.findByFailedRolloutAndNotInRolloutGroups(
                    pageRequest, readyGroups, RolloutHelper.getIdFromRetriedTargetFilter(rollout.getTargetFilterQuery()));
            }

            createAssignmentOfTargetsToGroup(targets, group);

            return Long.valueOf(targets.getNumberOfElements());
        });
    }

    /**
     * Schedules a group of the rollout. Scheduled Actions are created to achieve
     * this. The creation of those Actions is allowed to fail.
     */
    private boolean scheduleRolloutGroup(final JpaRollout rollout, final JpaRolloutGroup group) {
        final long targetsInGroup = rolloutTargetGroupRepository.countByRolloutGroup(group);
        final long countOfActions = actionRepository.countByRolloutAndRolloutGroup(rollout, group);

        long actionsLeft = targetsInGroup - countOfActions;
        if (actionsLeft > 0) {
            actionsLeft -= createActionsForRolloutGroup(rollout, group);
        }

        if (actionsLeft <= 0) {
            group.setStatus(RolloutGroupStatus.SCHEDULED);
            rolloutGroupRepository.save(group);
            return true;
        }
        return false;
    }

    private long createActionsForRolloutGroup(final Rollout rollout, final RolloutGroup group) {
        long totalActionsCreated = 0;
        try {
            long actionsCreated;
            do {
                actionsCreated = createActionsForTargetsInNewTransaction(rollout.getId(), group.getId(),
                        TRANSACTION_TARGETS);
                totalActionsCreated += actionsCreated;
            } while (actionsCreated > 0);

        } catch (final TransactionException e) {
            LOGGER.warn("Transaction assigning Targets to RolloutGroup failed", e);
            return 0;
        }
        return totalActionsCreated;
    }

    private Long createActionsForTargetsInNewTransaction(final long rolloutId, final long groupId, final int limit) {
        return DeploymentHelper.runInNewTransaction(txManager, "createActionsForTargets", status -> {
            final PageRequest pageRequest = PageRequest.of(0, limit);
            final Rollout rollout = rolloutRepository.findById(rolloutId)
                    .orElseThrow(() -> new EntityNotFoundException(Rollout.class, rolloutId));
            final RolloutGroup group = rolloutGroupRepository.findById(groupId)
                    .orElseThrow(() -> new EntityNotFoundException(RolloutGroup.class, groupId));

            final DistributionSet distributionSet = rollout.getDistributionSet();
            final ActionType actionType = rollout.getActionType();
            final long forceTime = rollout.getForcedTime();

            final Slice<Target> targets = targetManagement.findByInRolloutGroupWithoutAction(pageRequest, groupId);
            if (targets.getNumberOfElements() > 0) {
                createScheduledAction(targets.getContent(), distributionSet, actionType, forceTime, rollout, group);
            }

            return Long.valueOf(targets.getNumberOfElements());
        });
    }

    private void createAssignmentOfTargetsToGroup(final Slice<Target> targets, final RolloutGroup group) {
        targets.forEach(target -> rolloutTargetGroupRepository.save(new RolloutTargetGroup(group, target)));
    }

    /**
     * Creates an action entry into the action repository. In case of existing
     * scheduled actions the scheduled actions gets canceled. A scheduled action is
     * created in-active.
     */
    private void createScheduledAction(final Collection<Target> targets, final DistributionSet distributionSet,
            final ActionType actionType, final Long forcedTime, final Rollout rollout,
            final RolloutGroup rolloutGroup) {
        // cancel all current scheduled actions for this target. E.g. an action
        // is already scheduled and a next action is created then cancel the
        // current scheduled action to cancel. E.g. a new scheduled action is
        // created.
        final List<Long> targetIds = targets.stream().map(Target::getId).collect(Collectors.toList());
        deploymentManagement.cancelInactiveScheduledActionsForTargets(targetIds);
        targets.forEach(target -> {

            assertActionsPerTargetQuota(target, 1);

            final JpaAction action = new JpaAction();
            action.setTarget(target);
            action.setActive(false);
            action.setDistributionSet(distributionSet);
            action.setActionType(actionType);
            action.setForcedTime(forcedTime);
            action.setStatus(Status.SCHEDULED);
            action.setRollout(rollout);
            action.setRolloutGroup(rolloutGroup);
            action.setInitiatedBy(rollout.getCreatedBy());
            rollout.getWeight().ifPresent(action::setWeight);
            actionRepository.save(action);
        });
    }

    /**
     * Enforces the quota defining the maximum number of {@link Action}s per
     * {@link Target}.
     *
     * @param target
     *            The target
     * @param requested
     *            number of actions to check
     */
    private void assertActionsPerTargetQuota(final Target target, final int requested) {
        final int quota = quotaManagement.getMaxActionsPerTarget();
        QuotaHelper.assertAssignmentQuota(target.getId(), requested, quota, Action.class, Target.class,
                actionRepository::countByTargetId);
    }
}
