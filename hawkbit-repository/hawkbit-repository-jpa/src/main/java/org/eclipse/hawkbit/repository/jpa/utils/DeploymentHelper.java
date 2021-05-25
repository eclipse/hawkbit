/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.utils;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.eclipse.hawkbit.repository.jpa.ActionRepository;
import org.eclipse.hawkbit.repository.jpa.TargetRepository;
import org.eclipse.hawkbit.repository.jpa.TransactionExecutionException;
import org.eclipse.hawkbit.repository.jpa.model.JpaAction;
import org.eclipse.hawkbit.repository.jpa.model.JpaTarget;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.security.SecurityContextTenantAware;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Utility class for deployment related topics.
 *
 */
public final class DeploymentHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DeploymentHelper.class);

    private DeploymentHelper() {
        // utility class
    }

    /**
     * This method is called, when cancellation has been successful. It sets the
     * action to canceled, resets the meta data of the target and in case there
     * is a new action this action is triggered.
     *
     * @param action
     *            the action which is set to canceled
     * @param actionRepository
     *            for the operation
     * @param targetRepository
     *            for the operation
     */
    public static void successCancellation(final JpaAction action, final ActionRepository actionRepository,
            final TargetRepository targetRepository) {

        // set action inactive
        action.setActive(false);
        action.setStatus(Status.CANCELED);

        final JpaTarget target = (JpaTarget) action.getTarget();
        final List<Action> nextActiveActions = actionRepository.findByTargetAndActiveOrderByIdAsc(target, true).stream()
                .filter(a -> !a.getId().equals(action.getId())).collect(Collectors.toList());

        if (nextActiveActions.isEmpty()) {
            target.setAssignedDistributionSet(target.getInstalledDistributionSet());
            target.setUpdateStatus(TargetUpdateStatus.IN_SYNC);
        } else {
            target.setAssignedDistributionSet(nextActiveActions.get(0).getDistributionSet());
        }

        targetRepository.save(target);
    }

    /**
     * Executes the modifying action in new transaction
     *
     * @param txManager
     *            transaction manager interface
     * @param transactionName
     *            the name of the new transaction
     * @param action
     *            the callback to execute in new tranaction
     *
     * @return the result of the action
     */
    public static <T> T runInNewTransaction(@NotNull final PlatformTransactionManager txManager,
            final String transactionName, @NotNull final TransactionCallback<T> action)
            throws TransactionExecutionException {
        return runInNewTransaction(txManager, transactionName, Isolation.DEFAULT.value(), action);
    }

    /**
     * Executes the modifying action in new transaction
     *
     * @param txManager
     *            transaction manager interface
     * @param transactionName
     *            the name of the new transaction
     * @param isolationLevel
     *            isolation level of the new transaction
     * @param action
     *            the callback to execute in new tranaction
     *
     * @return the result of the action
     */
    @SuppressWarnings({ "squid:S2221", /* for catching the generic Exception instead of specific ones */
                        "squid:S1162" /* for throwing a checked exception */ })
    public static <T> T runInNewTransaction(@NotNull final PlatformTransactionManager txManager,
            final String transactionName, final int isolationLevel, @NotNull final TransactionCallback<T> action) throws
            TransactionExecutionException {
        final DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(transactionName);
        def.setReadOnly(false);
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setIsolationLevel(isolationLevel);
        final TransactionTemplate transactionTemplate = new TransactionTemplate(txManager, def);

        try {
            return transactionTemplate.execute(action);
        } catch (final Exception e) {
            LOG.error("Caught exception during transaction execution, transactionName: {}!", transactionName, e);
            throw new TransactionExecutionException("Caught exception during transaction execution!", e);
        }
    }

    /**
     * Runs the given handler in a non-system user context. Switches to the user
     * which is provided by the given callback.
     *
     * @param handler
     *            The handler to be invoked in the right user context.
     * @param username
     *            Callback to obtain the real user the user context should be
     *            established for.
     * @param tenantAware
     *            The {@link TenantAware} bean to determine the current tenant
     *            context.
     */
    public static void runInNonSystemContext(@NotNull final Runnable handler, @NotNull final Supplier<String> username,
            @NotNull final TenantAware tenantAware) {
        final String currentUser = tenantAware.getCurrentUsername();
        if (isNonSystemUser(currentUser)) {
            handler.run();
            return;
        }
        final String user = username.get();
        LOG.debug("Switching user context from '{}' to '{}'", currentUser, user);
        tenantAware.runAsTenantAsUser(tenantAware.getCurrentTenant(), user, () -> {
            handler.run();
            return null;
        });
    }

    private static boolean isNonSystemUser(final String user) {
        return (!(StringUtils.isEmpty(user) || SecurityContextTenantAware.SYSTEM_USER.equals(user)));
    }

}
