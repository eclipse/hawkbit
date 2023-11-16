/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hawkbit.repository.event.remote.TargetPollEvent;
import org.eclipse.hawkbit.repository.model.ActionStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the repository.
 *
 */
@ConfigurationProperties("hawkbit.server.repository")
public class RepositoryProperties {

    /**
     * Set to <code>true</code> if the repository has to reject {@link ActionStatus}
     * entries for actions that are closed. This is especially useful if the action
     * status feedback channel order from the device cannot be guaranteed.
     *
     * Note: if this is enforced you have to make sure that the feedback channel
     * from the devices is in order.
     */
    private boolean rejectActionStatusForClosedAction;

    /**
     * Set to <code>true</code> if the repository should publish
     * {@link TargetPollEvent}s in case a target connects to the repository.
     * Activated by default but may be worth to disable if not needed.
     */
    private boolean publishTargetPollEvent = true;

    /**
     * Maximum number of poll operations queued before flush.
     */
    private int pollPersistenceQueueSize = 10_000;

    /**
     * Maximum time before queue is flushed in {@link TimeUnit#MILLISECONDS}.
     */
    private long pollPersistenceFlushTime = TimeUnit.SECONDS.toMillis(10);

    /**
     * Set to true to persist polls immediately.
     */
    private boolean eagerPollPersistence;

    /**
     * If an {@link Action} has a weight of null this value is used as weight.
     */
    private int actionWeightIfAbsent = 1000;

    /**
     * Defines a timeout for the lock during invalidation of distribution sets (in
     * seconds).
     */
    private long dsInvalidationLockTimeout = 5;

    /**
     * Define configurations which are related for the rollout feature
     */
    private RolloutsConfig rollouts = new RolloutsConfig();

    public boolean isEagerPollPersistence() {
        return eagerPollPersistence;
    }

    public void setEagerPollPersistence(final boolean eagerPollPersistence) {
        this.eagerPollPersistence = eagerPollPersistence;
    }

    public long getPollPersistenceFlushTime() {
        return pollPersistenceFlushTime;
    }

    public void setPollPersistenceFlushTime(final long pollPersistenceFlushTime) {
        this.pollPersistenceFlushTime = pollPersistenceFlushTime;
    }

    public int getPollPersistenceQueueSize() {
        return pollPersistenceQueueSize;
    }

    public void setPollPersistenceQueueSize(final int pollPersistenceQueueSize) {
        this.pollPersistenceQueueSize = pollPersistenceQueueSize;
    }

    public boolean isRejectActionStatusForClosedAction() {
        return rejectActionStatusForClosedAction;
    }

    public void setRejectActionStatusForClosedAction(final boolean rejectActionStatusForClosedAction) {
        this.rejectActionStatusForClosedAction = rejectActionStatusForClosedAction;
    }

    public boolean isPublishTargetPollEvent() {
        return publishTargetPollEvent;
    }

    public void setPublishTargetPollEvent(final boolean publishTargetPollEvent) {
        this.publishTargetPollEvent = publishTargetPollEvent;
    }

    public int getActionWeightIfAbsent() {
        return actionWeightIfAbsent;
    }

    public void setActionWeightIfAbsent(final int actionWeightIfAbsent) {
        this.actionWeightIfAbsent = actionWeightIfAbsent;
    }

    public long getDsInvalidationLockTimeout() {
        return dsInvalidationLockTimeout;
    }

    public void setDsInvalidationLockTimeout(final long dsInvalidationLockTimeout) {
        this.dsInvalidationLockTimeout = dsInvalidationLockTimeout;
    }

    public RolloutsConfig getRollouts() {
        return rollouts;
    }

    public void setRollouts(final RolloutsConfig rollouts) {
        this.rollouts = rollouts;
    }

    public static class RolloutsConfig {

        /**
         * Override {@link RolloutsConfig} properties for a specific tenant
         */
        private Map<String, RolloutsConfig> tenant = new HashMap<>();

        /**
         * Max tries of creating a rollout before letting it fail with state
         * {@link org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus#ERROR_CREATING}
         */
        private int creatingMaxRetries = 3;

        /**
         * Max tries of starting a rollout before letting it fail with state
         * {@link org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus#ERROR_STARTING}
         */
        private int startingMaxRetries = 3;

        /**
         * Max tries of deleting a rollout before letting it fail with state
         * {@link org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus#ERROR_DELETING}
         */
        private int deletingMaxRetries = 3;

        public int getCreatingMaxRetries() {
            return creatingMaxRetries;
        }

        public void setCreatingMaxRetries(final int creatingMaxRetries) {
            this.creatingMaxRetries = creatingMaxRetries;
        }

        public int getStartingMaxRetries() {
            return startingMaxRetries;
        }

        public void setStartingMaxRetries(final int startingMaxRetries) {
            this.startingMaxRetries = startingMaxRetries;
        }

        public int getDeletingMaxRetries() {
            return deletingMaxRetries;
        }

        public void setDeletingMaxRetries(final int deletingMaxRetries) {
            this.deletingMaxRetries = deletingMaxRetries;
        }

        public int getTenantCreatingMaxRetriesOrDefault(final String t) {
            return Optional.ofNullable(this.tenant.get(t)).map(RolloutsConfig::getCreatingMaxRetries)
                    .orElse(creatingMaxRetries);
        }

        public int getTenantStartingMaxRetriesOrDefault(final String t) {
            return Optional.ofNullable(this.tenant.get(t)).map(RolloutsConfig::getStartingMaxRetries)
                  .orElse(startingMaxRetries);
        }

        public int getTenantDeletingMaxRetriesOrDefault(final String t) {
            return Optional.ofNullable(this.tenant.get(t)).map(RolloutsConfig::getDeletingMaxRetries)
                  .orElse(deletingMaxRetries);
        }

        public void setTenant(final Map<String, RolloutsConfig> tenant) {
            this.tenant = tenant;
        }

    }

}
