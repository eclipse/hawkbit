/**
 * Copyright (c) 2023 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.persistence.EntityManager;

import org.eclipse.hawkbit.repository.RepositoryProperties;
import org.eclipse.hawkbit.repository.RolloutExecutor;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutUpdatedEvent;
import org.eclipse.hawkbit.repository.jpa.executor.AfterTransactionCommitExecutor;
import org.eclipse.hawkbit.repository.jpa.model.JpaAction;
import org.eclipse.hawkbit.repository.jpa.model.JpaRollout;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.helper.EventPublisherHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * Abstract implementation of the {@link RolloutExecutor}. It's implementation
 * {@link RolloutExecutor#execute(Rollout)} to analyze the current rollout
 * execution state and delegate it to corresponding (abstract) methods.
 */
public abstract class AbstractRolloutExecutor implements RolloutExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRolloutExecutor.class);

    /**
     * Max amount of targets that are handled in one transaction.
     */
    protected static final int TRANSACTION_TARGETS = 5_000;

    /**
     * Maximum amount of actions that are deleted in one transaction.
     */
    protected static final int TRANSACTION_ACTIONS = 5_000;

    private final RolloutRepository rolloutRepository;
    private final RolloutGroupRepository rolloutGroupRepository;
    private final ActionRepository actionRepository;
    private final AfterTransactionCommitExecutor afterCommit;
    private final EntityManager entityManager;
    private final EventPublisherHolder eventPublisherHolder;
    private final RepositoryProperties.RolloutsConfig rolloutsConfig;

    /**
     * Constructor
     */
    protected AbstractRolloutExecutor(final RolloutRepository rolloutRepository,
            final RolloutGroupRepository rolloutGroupRepository, final ActionRepository actionRepository,
            final AfterTransactionCommitExecutor afterCommit, final EntityManager entityManager,
            final EventPublisherHolder eventPublisherHolder, final RepositoryProperties.RolloutsConfig rolloutsConfig) {
        this.rolloutRepository = rolloutRepository;
        this.rolloutGroupRepository = rolloutGroupRepository;
        this.actionRepository = actionRepository;
        this.afterCommit = afterCommit;
        this.entityManager = entityManager;
        this.eventPublisherHolder = eventPublisherHolder;
        this.rolloutsConfig = rolloutsConfig;
    }

    @Override
    public void execute(final Rollout rollout) {
        LOGGER.debug("handle rollout {}", rollout.getId());

        final JpaRollout jpaRollout = (JpaRollout) rollout;

        switch (rollout.getStatus()) {
        case CREATING:
            countingCreationRetries(jpaRollout, this::handleCreateRollout, (retries, e) -> {
                LOGGER.warn("Exceeded maxRetries[{}] when trying to create rollout with id [{}] and name '{}' due to: ",
                        retries, rollout.getId(), rollout.getName(), e);
                handleErrorCreating(jpaRollout);
            });
            break;
        case DELETING:
            countingDeletingRetries(jpaRollout, this::handleDeleteRollout, (retries, e) -> {
                LOGGER.warn("Exceeded maxRetries[{}] when trying to delete rollout with id [{}] and name '{}' due to: ",
                        retries, rollout.getId(), rollout.getName(), e);
                handleErrorDeleting(jpaRollout);
            });
            break;
        case READY:
            handleReadyRollout(jpaRollout);
            break;
        case STARTING:
            countingStartingRetries(jpaRollout, this::handleStartingRollout, (retries, e) -> {
                LOGGER.warn("Exceeded maxRetries[{}] when trying to start rollout with id [{}] and name '{}' due to: ",
                        retries, rollout.getId(), rollout.getName(), e);
                handleErrorStarting(jpaRollout);
            });
            break;
        case RUNNING:
            handleRunningRollout(jpaRollout);
            break;
        case STOPPING:
            handleStopRollout(jpaRollout);
            break;
        default:
            LOGGER.error("Rollout in status {} not supposed to be handled!", rollout.getStatus());
            break;
        }
    }

    protected abstract void handleCreateRollout(final JpaRollout rollout);

    protected abstract void handleDeleteRollout(final JpaRollout rollout);

    protected abstract void handleReadyRollout(final JpaRollout rollout);

    protected abstract void handleStartingRollout(final JpaRollout rollout);

    protected abstract void handleRunningRollout(final JpaRollout rollout);

    protected abstract void handleStopRollout(final JpaRollout rollout);

    protected void handleErrorCreating(final JpaRollout rollout) {
        setRolloutErrorState(rollout, Rollout.RolloutStatus.ERROR_CREATING);
    }

    protected void handleErrorStarting(final JpaRollout rollout) {
        setRolloutErrorState(rollout, Rollout.RolloutStatus.ERROR_STARTING);
    }

    protected void handleErrorDeleting(final JpaRollout rollout) {
        setRolloutErrorState(rollout, Rollout.RolloutStatus.ERROR_DELETING);
    }

    private void setRolloutErrorState(final JpaRollout rollout, final Rollout.RolloutStatus errorStatus) {
        ensureIsErrorStatus(errorStatus);
        if (!cleanupScheduledActions(rollout)) {
            // could not clean up scheduled actions
            LOGGER.warn("Could not clean up scheduled actions for rollout with id {}", rollout.getId());
        }
        setErrorStateForRolloutGroups(rollout);
        rollout.setStatus(errorStatus);
        rolloutRepository.save(rollout);
    }

    private void ensureIsErrorStatus(final Rollout.RolloutStatus errorStatus) {
        if (errorStatus != Rollout.RolloutStatus.ERROR_CREATING || errorStatus != Rollout.RolloutStatus.ERROR_STARTING
                || errorStatus != Rollout.RolloutStatus.ERROR_DELETING) {
            throw new IllegalArgumentException("Given rollout status is not an error one.");
        }
    }

    /**
     * @param rollout
     * @return if all scheduled actions could be stopped
     */
    protected boolean cleanupScheduledActions(final JpaRollout rollout) {
        // clean up all scheduled actions
        deleteScheduledActions(rollout);

        // avoid another scheduler round and re-check if all scheduled actions
        // has been cleaned up. we flush first to ensure that the we include the
        // deletion above
        entityManager.flush();
        return actionRepository.countByRolloutIdAndStatus(rollout.getId(), Action.Status.SCHEDULED) == 0;
    }

    protected void deleteScheduledActions(final JpaRollout rollout) {
        final Slice<JpaAction> scheduledActions = findScheduledActionsByRollout(rollout);
        final boolean hasScheduledActions = scheduledActions.getNumberOfElements() > 0;

        if (hasScheduledActions) {
            try {
                final Iterable<JpaAction> iterable = scheduledActions::iterator;
                final List<Long> actionIds = StreamSupport.stream(iterable.spliterator(), false).map(Action::getId)
                        .collect(Collectors.toList());
                actionRepository.deleteByIdIn(actionIds);
                afterCommit.afterCommit(() -> eventPublisherHolder.getEventPublisher()
                        .publishEvent(new RolloutUpdatedEvent(rollout, eventPublisherHolder.getApplicationId())));
            } catch (final RuntimeException e) {
                LOGGER.error("Exception during deletion of actions of rollout {}", rollout, e);
            }
        }
    }

    private Slice<JpaAction> findScheduledActionsByRollout(final JpaRollout rollout) {
        return actionRepository.findByRolloutIdAndStatus(PageRequest.of(0, TRANSACTION_ACTIONS), rollout.getId(),
                Action.Status.SCHEDULED);
    }

    private void setErrorStateForRolloutGroups(final Rollout rollout) {
        rolloutGroupRepository.findByRolloutId(rollout.getId(), Pageable.unpaged()).forEach(rolloutGroup -> {
            rolloutGroup.setStatus(RolloutGroup.RolloutGroupStatus.ERROR);
            rolloutGroupRepository.save(rolloutGroup);
        });
    }

    private void countingCreationRetries(final JpaRollout rollout, final Consumer<JpaRollout> executor,
            final BiConsumer<Integer, Throwable> retriesExceeded) {
        final int maxRetriesCreating = rolloutsConfig.getTenantCreatingMaxRetriesOrDefault(rollout.getTenant());
        countingRetries(rollout, maxRetriesCreating, executor, retriesExceeded);
    }

    private void countingStartingRetries(final JpaRollout rollout, final Consumer<JpaRollout> executor,
            final BiConsumer<Integer, Throwable> retriesExceeded) {
        final int maxRetriesStarting = rolloutsConfig.getTenantStartingMaxRetriesOrDefault(rollout.getTenant());
        countingRetries(rollout, maxRetriesStarting, executor, retriesExceeded);
    }

    private void countingDeletingRetries(final JpaRollout rollout, final Consumer<JpaRollout> executor,
            final BiConsumer<Integer, Throwable> retriesExceeded) {
        final int maxRetriesDeleting = rolloutsConfig.getTenantDeletingMaxRetriesOrDefault(rollout.getTenant());
        countingRetries(rollout, maxRetriesDeleting, executor, retriesExceeded);
    }

    private void countingRetries(final JpaRollout rollout, final int maxRetries, final Consumer<JpaRollout> executor,
            final BiConsumer<Integer, Throwable> retriesExceeded) {
        try {
            executor.accept(rollout);
        } catch (final Exception e) {
            final Integer retryCount = Optional.ofNullable(rollout.getRetryCount()).map(count -> count + 1).orElse(1);
            LOGGER.warn("A problem occurred for rollout id[{}] with name '{}' and retryCount of {} due to:",
                    rollout.getId(), rollout.getName(), retryCount, e);

            rollout.setRetryCount(retryCount);
            rolloutRepository.save(rollout);

            if (retryCount >= maxRetries) {
                retriesExceeded.accept(maxRetries, e);
            }
        }
    }
}
