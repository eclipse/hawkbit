/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import static org.eclipse.hawkbit.repository.jpa.builder.JpaRolloutGroupCreate.addSuccessAndErrorConditionsAndActions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import javax.validation.ConstraintDeclarationException;
import javax.validation.ValidationException;

import org.eclipse.hawkbit.repository.AbstractRolloutManagement;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RolloutApprovalStrategy;
import org.eclipse.hawkbit.repository.RolloutExecutor;
import org.eclipse.hawkbit.repository.RolloutFields;
import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutHelper;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.RolloutStatusCache;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.builder.GenericRolloutUpdate;
import org.eclipse.hawkbit.repository.builder.RolloutCreate;
import org.eclipse.hawkbit.repository.builder.RolloutGroupCreate;
import org.eclipse.hawkbit.repository.builder.RolloutUpdate;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupCreatedEvent;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.exception.EntityReadOnlyException;
import org.eclipse.hawkbit.repository.exception.RolloutIllegalStateException;
import org.eclipse.hawkbit.repository.jpa.configuration.Constants;
import org.eclipse.hawkbit.repository.jpa.executor.AfterTransactionCommitExecutor;
import org.eclipse.hawkbit.repository.jpa.model.JpaRollout;
import org.eclipse.hawkbit.repository.jpa.model.JpaRolloutGroup;
import org.eclipse.hawkbit.repository.jpa.rsql.RSQLUtility;
import org.eclipse.hawkbit.repository.jpa.specifications.RolloutSpecification;
import org.eclipse.hawkbit.repository.jpa.specifications.SpecificationsBuilder;
import org.eclipse.hawkbit.repository.jpa.utils.DeploymentHelper;
import org.eclipse.hawkbit.repository.jpa.utils.QuotaHelper;
import org.eclipse.hawkbit.repository.jpa.utils.WeightValidationHelper;
import org.eclipse.hawkbit.repository.model.BaseEntity;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroupConditions;
import org.eclipse.hawkbit.repository.model.RolloutGroupsValidation;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TotalTargetCountActionStatus;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus;
import org.eclipse.hawkbit.repository.model.helper.EventPublisherHolder;
import org.eclipse.hawkbit.repository.rsql.VirtualPropertyReplacer;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.validation.annotation.Validated;

import com.google.common.collect.Lists;

/**
 * JPA implementation of {@link RolloutManagement}.
 */
@Validated
@Transactional(readOnly = true)
public class JpaRolloutManagement extends AbstractRolloutManagement {
    private static final Logger LOGGER = LoggerFactory.getLogger(JpaRolloutManagement.class);

    private static final List<RolloutStatus> ACTIVE_ROLLOUTS = Arrays.asList(RolloutStatus.CREATING,
            RolloutStatus.DELETING, RolloutStatus.STARTING, RolloutStatus.READY, RolloutStatus.RUNNING);

    @Autowired
    private RolloutRepository rolloutRepository;

    @Autowired
    private RolloutGroupRepository rolloutGroupRepository;

    @Autowired
    private ActionRepository actionRepository;

    @Autowired
    private AfterTransactionCommitExecutor afterCommit;

    @Autowired
    private QuotaManagement quotaManagement;

    @Autowired
    private RolloutStatusCache rolloutStatusCache;

    private final RolloutExecutor rolloutExecutor;

    private final EventPublisherHolder eventPublisherHolder;

    private final Database database;

    public JpaRolloutManagement(final TargetManagement targetManagement, final DeploymentManagement deploymentManagement,
            final RolloutGroupManagement rolloutGroupManagement,
            final DistributionSetManagement distributionSetManagement, final ApplicationContext context,
            final EventPublisherHolder eventPublisherHolder, final VirtualPropertyReplacer virtualPropertyReplacer,
            final PlatformTransactionManager txManager, final TenantAware tenantAware, final LockRegistry lockRegistry,
            final Database database, final RolloutApprovalStrategy rolloutApprovalStrategy,
            final TenantConfigurationManagement tenantConfigurationManagement,
            final SystemSecurityContext systemSecurityContext, final RolloutExecutor rolloutExecutor) {
        super(targetManagement, deploymentManagement, rolloutGroupManagement, distributionSetManagement, context,
                virtualPropertyReplacer, txManager, tenantAware, lockRegistry, rolloutApprovalStrategy,
                tenantConfigurationManagement, systemSecurityContext);
        this.eventPublisherHolder = eventPublisherHolder;
        this.database = database;
        this.rolloutExecutor = rolloutExecutor;
    }

    @Override
    public Page<Rollout> findAll(final Pageable pageable, final boolean deleted) {
        final Specification<JpaRollout> spec = RolloutSpecification.isDeletedWithDistributionSet(deleted);
        return JpaRolloutHelper.convertPage(rolloutRepository.findAll(spec, pageable), pageable);
    }

    @Override
    public Page<Rollout> findByRsql(final Pageable pageable, final String rsqlParam, final boolean deleted) {
        final List<Specification<JpaRollout>> specList = Lists.newArrayListWithExpectedSize(2);
        specList.add(RSQLUtility.parse(rsqlParam, RolloutFields.class, virtualPropertyReplacer, database));
        specList.add(RolloutSpecification.isDeletedWithDistributionSet(deleted));

        return JpaRolloutHelper.convertPage(findByCriteriaAPI(pageable, specList), pageable);
    }

    /**
     * Executes findAll with the given {@link Rollout} {@link Specification}s.
     */
    private Page<JpaRollout> findByCriteriaAPI(final Pageable pageable,
            final List<Specification<JpaRollout>> specList) {
        if (CollectionUtils.isEmpty(specList)) {
            return rolloutRepository.findAll(pageable);
        }

        return rolloutRepository.findAll(SpecificationsBuilder.combineWithAnd(specList), pageable);
    }

    @Override
    public Optional<Rollout> get(final long rolloutId) {
        return rolloutRepository.findById(rolloutId).map(r -> (Rollout) r);
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Rollout create(final RolloutCreate rollout, final int amountGroup, final RolloutGroupConditions conditions) {
        RolloutHelper.verifyRolloutGroupParameter(amountGroup, quotaManagement);
        final JpaRollout savedRollout = createRollout((JpaRollout) rollout.build());
        return createRolloutGroups(amountGroup, conditions, savedRollout);
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Rollout create(final RolloutCreate rollout, final List<RolloutGroupCreate> groups,
            final RolloutGroupConditions conditions) {
        RolloutHelper.verifyRolloutGroupParameter(groups.size(), quotaManagement);
        final JpaRollout savedRollout = createRollout((JpaRollout) rollout.build());
        return createRolloutGroups(groups, conditions, savedRollout);
    }

    private JpaRollout createRollout(final JpaRollout rollout) {
        WeightValidationHelper.usingContext(systemSecurityContext, tenantConfigurationManagement).validate(rollout);
        final Long totalTargets = targetManagement.countByRsql(rollout.getTargetFilterQuery());
        if (totalTargets == 0) {
            throw new ValidationException("Rollout does not match any existing targets");
        }
        rollout.setTotalTargets(totalTargets);
        return rolloutRepository.save(rollout);
    }

    private Rollout createRolloutGroups(final int amountOfGroups, final RolloutGroupConditions conditions,
            final JpaRollout rollout) {
        RolloutHelper.verifyRolloutInStatus(rollout, RolloutStatus.CREATING);
        RolloutHelper.verifyRolloutGroupConditions(conditions);

        final JpaRollout savedRollout = rollout;

        // we can enforce the 'max targets per group' quota right here because
        // we want to distribute the targets equally to the different groups
        assertTargetsPerRolloutGroupQuota(rollout.getTotalTargets() / amountOfGroups);

        RolloutGroup lastSavedGroup = null;
        for (int i = 0; i < amountOfGroups; i++) {
            final String nameAndDesc = "group-" + (i + 1);
            final JpaRolloutGroup group = new JpaRolloutGroup();
            group.setName(nameAndDesc);
            group.setDescription(nameAndDesc);
            group.setRollout(savedRollout);
            group.setParent(lastSavedGroup);
            group.setStatus(RolloutGroupStatus.CREATING);

            addSuccessAndErrorConditionsAndActions(group, conditions);

            group.setTargetPercentage(1.0F / (amountOfGroups - i) * 100);

            lastSavedGroup = rolloutGroupRepository.save(group);
            publishRolloutGroupCreatedEventAfterCommit(lastSavedGroup, rollout);
        }

        savedRollout.setRolloutGroupsCreated(amountOfGroups);
        return rolloutRepository.save(savedRollout);
    }

    private Rollout createRolloutGroups(final List<RolloutGroupCreate> groupList,
            final RolloutGroupConditions conditions, final Rollout rollout) {
        RolloutHelper.verifyRolloutInStatus(rollout, RolloutStatus.CREATING);
        final JpaRollout savedRollout = (JpaRollout) rollout;

        // prepare the groups
        final List<RolloutGroup> groups = groupList.stream()
                .map(group -> JpaRolloutHelper.prepareRolloutGroupWithDefaultConditions(group, conditions))
                .collect(Collectors.toList());
        groups.forEach(RolloutHelper::verifyRolloutGroupHasConditions);

        RolloutHelper.verifyRemainingTargets(
                calculateRemainingTargets(groups, savedRollout.getTargetFilterQuery(), savedRollout.getCreatedAt()));

        // check if we need to enforce the 'max targets per group' quota
        if (quotaManagement.getMaxTargetsPerRolloutGroup() > 0) {
            validateTargetsInGroups(groups, savedRollout.getTargetFilterQuery(), savedRollout.getCreatedAt())
                    .getTargetsPerGroup().forEach(this::assertTargetsPerRolloutGroupQuota);
        }

        // create and persist the groups (w/o filling them with targets)
        RolloutGroup lastSavedGroup = null;
        for (final RolloutGroup srcGroup : groups) {
            final JpaRolloutGroup group = new JpaRolloutGroup();
            group.setName(srcGroup.getName());
            group.setDescription(srcGroup.getDescription());
            group.setRollout(savedRollout);
            group.setParent(lastSavedGroup);
            group.setStatus(RolloutGroupStatus.CREATING);

            group.setTargetPercentage(srcGroup.getTargetPercentage());
            if (srcGroup.getTargetFilterQuery() != null) {
                group.setTargetFilterQuery(srcGroup.getTargetFilterQuery());
            } else {
                group.setTargetFilterQuery("");
            }

            addSuccessAndErrorConditionsAndActions(group, srcGroup.getSuccessCondition(),
                    srcGroup.getSuccessConditionExp(), srcGroup.getSuccessAction(), srcGroup.getSuccessActionExp(),
                    srcGroup.getErrorCondition(), srcGroup.getErrorConditionExp(), srcGroup.getErrorAction(),
                    srcGroup.getErrorActionExp());

            lastSavedGroup = rolloutGroupRepository.save(group);
            publishRolloutGroupCreatedEventAfterCommit(lastSavedGroup, rollout);
        }

        savedRollout.setRolloutGroupsCreated(groups.size());
        return rolloutRepository.save(savedRollout);
    }

    private void publishRolloutGroupCreatedEventAfterCommit(final RolloutGroup group, final Rollout rollout) {
        afterCommit.afterCommit(() -> eventPublisherHolder.getEventPublisher().publishEvent(
                new RolloutGroupCreatedEvent(group, rollout.getId(), eventPublisherHolder.getApplicationId())));
    }

    @Override
    @Async
    public ListenableFuture<RolloutGroupsValidation> validateTargetsInGroups(final List<RolloutGroupCreate> groups,
            final String targetFilter, final Long createdAt) {

        final String baseFilter = RolloutHelper.getTargetFilterQuery(targetFilter, createdAt);
        final long totalTargets = targetManagement.countByRsql(baseFilter);
        if (totalTargets == 0) {
            throw new ConstraintDeclarationException("Rollout target filter does not match any targets");
        }

        return new AsyncResult<>(validateTargetsInGroups(
                groups.stream().map(RolloutGroupCreate::build).collect(Collectors.toList()), baseFilter, totalTargets));
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Rollout approveOrDeny(final long rolloutId, final Rollout.ApprovalDecision decision) {
        return this.approveOrDeny(rolloutId, decision, null);
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Rollout approveOrDeny(final long rolloutId, final Rollout.ApprovalDecision decision, final String remark) {
        LOGGER.debug("approveOrDeny rollout called for rollout {} with decision {}", rolloutId, decision);
        final JpaRollout rollout = getRolloutAndThrowExceptionIfNotFound(rolloutId);
        RolloutHelper.verifyRolloutInStatus(rollout, RolloutStatus.WAITING_FOR_APPROVAL);
        switch (decision) {
        case APPROVED:
            rollout.setStatus(RolloutStatus.READY);
            break;
        case DENIED:
            rollout.setStatus(RolloutStatus.APPROVAL_DENIED);
            break;
        default:
            throw new IllegalArgumentException("Unknown approval decision: " + decision);
        }
        rollout.setApprovalDecidedBy(rolloutApprovalStrategy.getApprovalUser(rollout));
        if (remark != null) {
            rollout.setApprovalRemark(remark);
        }
        return rolloutRepository.save(rollout);
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Rollout start(final long rolloutId) {
        LOGGER.debug("startRollout called for rollout {}", rolloutId);

        final JpaRollout rollout = getRolloutAndThrowExceptionIfNotFound(rolloutId);
        RolloutHelper.checkIfRolloutCanStarted(rollout, rollout);
        rollout.setStatus(RolloutStatus.STARTING);
        rollout.setLastCheck(0);
        return rolloutRepository.save(rollout);
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public void pauseRollout(final long rolloutId) {
        final JpaRollout rollout = getRolloutAndThrowExceptionIfNotFound(rolloutId);
        if (RolloutStatus.RUNNING != rollout.getStatus()) {
            throw new RolloutIllegalStateException("Rollout can only be paused in state running but current state is "
                    + rollout.getStatus().name().toLowerCase());
        }
        // setting the complete rollout only in paused state. This is sufficient
        // due the currently running groups will be completed and new groups are
        // not started until rollout goes back to running state again. The
        // periodically check for running rollouts will skip rollouts in pause
        // state.
        rollout.setStatus(RolloutStatus.PAUSED);
        rolloutRepository.save(rollout);
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public void resumeRollout(final long rolloutId) {
        final JpaRollout rollout = getRolloutAndThrowExceptionIfNotFound(rolloutId);
        if (RolloutStatus.PAUSED != rollout.getStatus()) {
            throw new RolloutIllegalStateException("Rollout can only be resumed in state paused but current state is "
                    + rollout.getStatus().name().toLowerCase());
        }
        rollout.setStatus(RolloutStatus.RUNNING);
        rolloutRepository.save(rollout);
    }

    @Override
    // No transaction, will be created per handled rollout
    @Transactional(propagation = Propagation.NEVER)
    public void handleRollouts() {
        final List<Long> rollouts = rolloutRepository.findByStatusIn(ACTIVE_ROLLOUTS);

        if (rollouts.isEmpty()) {
            return;
        }

        final String tenant = tenantAware.getCurrentTenant();

        final String handlerId = tenant + "-rollout";
        final Lock lock = lockRegistry.obtain(handlerId);
        if (!lock.tryLock()) {
            return;
        }

        try {
            rollouts.forEach(rolloutId -> {
                try {
                    DeploymentHelper.runInNewTransaction(txManager, handlerId + "-" + rolloutId,
                            status -> handleRollout(rolloutId));
                } catch (TransactionExecutionException e) {
                    LOGGER.error("Caught exception during rollout handling", e);
                }
            });
        } finally {
            lock.unlock();
        }
    }

    private long handleRollout(final long rolloutId) {
        final JpaRollout rollout = rolloutRepository.findById(rolloutId)
                .orElseThrow(() -> new EntityNotFoundException(Rollout.class, rolloutId));
        runInUserContext(rollout, () -> rolloutExecutor.execute(rollout));
        return 0;
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public void delete(final long rolloutId) {
        final JpaRollout jpaRollout = rolloutRepository.findById(rolloutId)
                .orElseThrow(() -> new EntityNotFoundException(Rollout.class, rolloutId));

        if (jpaRollout == null) {
            throw new EntityNotFoundException(Rollout.class, rolloutId);
        }

        if (RolloutStatus.DELETING == jpaRollout.getStatus()) {
            return;
        }

        jpaRollout.setStatus(RolloutStatus.DELETING);
        rolloutRepository.save(jpaRollout);
    }

    @Override
    public long count() {
        return rolloutRepository.count(RolloutSpecification.isDeletedWithDistributionSet(false));
    }

    @Override
    public long countByFilters(final String searchText) {
        return rolloutRepository.count(JpaRolloutHelper.likeNameOrDescription(searchText, false));
    }

    @Override
    public Slice<Rollout> findByFiltersWithDetailedStatus(final Pageable pageable, final String searchText,
            final boolean deleted) {
        final Slice<JpaRollout> findAll = findByCriteriaAPI(pageable,
                Collections.singletonList(JpaRolloutHelper.likeNameOrDescription(searchText, deleted)));
        setRolloutStatusDetails(findAll);
        return JpaRolloutHelper.convertPage(findAll, pageable);
    }

    @Override
    public Optional<Rollout> getByName(final String rolloutName) {
        return rolloutRepository.findByName(rolloutName);
    }

    @Override
    @Transactional
    @Retryable(include = {
            ConcurrencyFailureException.class }, maxAttempts = Constants.TX_RT_MAX, backoff = @Backoff(delay = Constants.TX_RT_DELAY))
    public Rollout update(final RolloutUpdate u) {
        final GenericRolloutUpdate update = (GenericRolloutUpdate) u;
        final JpaRollout rollout = getRolloutAndThrowExceptionIfNotFound(update.getId());

        checkIfDeleted(update.getId(), rollout.getStatus());
        update.getName().ifPresent(rollout::setName);
        update.getDescription().ifPresent(rollout::setDescription);
        update.getActionType().ifPresent(rollout::setActionType);
        update.getForcedTime().ifPresent(rollout::setForcedTime);
        update.getWeight().ifPresent(rollout::setWeight);
        // reseting back to manual start is done by setting start at time to
        // null
        rollout.setStartAt(update.getStartAt().orElse(null));
        update.getSet().ifPresent(setId -> {
            final DistributionSet set = distributionSetManagement.get(setId)
                    .orElseThrow(() -> new EntityNotFoundException(DistributionSet.class, setId));

            rollout.setDistributionSet(set);
        });
        if (rolloutApprovalStrategy.isApprovalNeeded(rollout)) {
            rollout.setStatus(RolloutStatus.WAITING_FOR_APPROVAL);
            rollout.setApprovalDecidedBy(null);
            rollout.setApprovalRemark(null);
        }

        return rolloutRepository.save(rollout);
    }

    private static void checkIfDeleted(final Long rolloutId, final RolloutStatus status) {
        if (RolloutStatus.DELETING == status || RolloutStatus.DELETED == status) {
            throw new EntityReadOnlyException("Rollout " + rolloutId + " is soft deleted and cannot be changed");
        }
    }

    private JpaRollout getRolloutAndThrowExceptionIfNotFound(final Long rolloutId) {
        return rolloutRepository.findById(rolloutId)
                .orElseThrow(() -> new EntityNotFoundException(Rollout.class, rolloutId));
    }

    @Override
    public Page<Rollout> findAllWithDetailedStatus(final Pageable pageable, final boolean deleted) {
        Page<JpaRollout> rollouts;
        final Specification<JpaRollout> spec = RolloutSpecification.isDeletedWithDistributionSet(deleted);
        rollouts = rolloutRepository.findAll(spec, pageable);
        setRolloutStatusDetails(rollouts);
        return JpaRolloutHelper.convertPage(rollouts, pageable);
    }

    @Override
    public Optional<Rollout> getWithDetailedStatus(final long rolloutId) {
        final Optional<Rollout> rollout = get(rolloutId);

        if (!rollout.isPresent()) {
            return rollout;
        }

        List<TotalTargetCountActionStatus> rolloutStatusCountItems = rolloutStatusCache.getRolloutStatus(rolloutId);

        if (CollectionUtils.isEmpty(rolloutStatusCountItems)) {
            rolloutStatusCountItems = actionRepository.getStatusCountByRolloutId(rolloutId);
            rolloutStatusCache.putRolloutStatus(rolloutId, rolloutStatusCountItems);
        }

        final TotalTargetCountStatus totalTargetCountStatus = new TotalTargetCountStatus(rolloutStatusCountItems,
                rollout.get().getTotalTargets(), rollout.get().getActionType());
        ((JpaRollout) rollout.get()).setTotalTargetCountStatus(totalTargetCountStatus);
        return rollout;
    }

    @Override
    public boolean exists(final long rolloutId) {
        return rolloutRepository.existsById(rolloutId);
    }

    private Map<Long, List<TotalTargetCountActionStatus>> getStatusCountItemForRollout(final List<Long> rollouts) {
        if (rollouts.isEmpty()) {
            return null;
        }

        final Map<Long, List<TotalTargetCountActionStatus>> fromCache = rolloutStatusCache.getRolloutStatus(rollouts);

        final List<Long> rolloutIds = rollouts.stream().filter(id -> !fromCache.containsKey(id))
                .collect(Collectors.toList());

        if (!rolloutIds.isEmpty()) {
            final List<TotalTargetCountActionStatus> resultList = actionRepository
                    .getStatusCountByRolloutId(rolloutIds);
            final Map<Long, List<TotalTargetCountActionStatus>> fromDb = resultList.stream()
                    .collect(Collectors.groupingBy(TotalTargetCountActionStatus::getId));

            rolloutStatusCache.putRolloutStatus(fromDb);

            fromCache.putAll(fromDb);
        }

        return fromCache;
    }

    private void setRolloutStatusDetails(final Slice<JpaRollout> rollouts) {
        final List<Long> rolloutIds = rollouts.getContent().stream().map(Rollout::getId).collect(Collectors.toList());
        final Map<Long, List<TotalTargetCountActionStatus>> allStatesForRollout = getStatusCountItemForRollout(
                rolloutIds);

        if (allStatesForRollout != null) {
            rollouts.forEach(rollout -> {
                final TotalTargetCountStatus totalTargetCountStatus = new TotalTargetCountStatus(
                        allStatesForRollout.get(rollout.getId()), rollout.getTotalTargets(), rollout.getActionType());
                rollout.setTotalTargetCountStatus(totalTargetCountStatus);
            });
        }
    }

    /**
     * Enforces the quota defining the maximum number of {@link Target}s per
     * {@link RolloutGroup}.
     *
     * @param group
     *            The rollout group
     * @param requested
     *            number of targets to check
     */
    private void assertTargetsPerRolloutGroupQuota(final long requested) {
        final int quota = quotaManagement.getMaxTargetsPerRolloutGroup();
        QuotaHelper.assertAssignmentQuota(requested, quota, Target.class, RolloutGroup.class);
    }

    private void runInUserContext(final BaseEntity rollout, final Runnable handler) {
        DeploymentHelper.runInNonSystemContext(handler, () -> Objects.requireNonNull(rollout.getCreatedBy()), tenantAware);
    }

}
