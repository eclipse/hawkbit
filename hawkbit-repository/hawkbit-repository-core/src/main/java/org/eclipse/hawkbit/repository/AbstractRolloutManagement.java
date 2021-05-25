/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.validation.ConstraintDeclarationException;

import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.RolloutGroupsValidation;
import org.eclipse.hawkbit.repository.rsql.VirtualPropertyReplacer;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Core functionality for {@link RolloutManagement} implementations.
 *
 */
public abstract class AbstractRolloutManagement implements RolloutManagement {

    protected final TargetManagement targetManagement;

    protected final DeploymentManagement deploymentManagement;

    protected final RolloutGroupManagement rolloutGroupManagement;

    protected final DistributionSetManagement distributionSetManagement;

    protected final ApplicationContext context;

    protected final VirtualPropertyReplacer virtualPropertyReplacer;

    protected final PlatformTransactionManager txManager;

    protected final TenantAware tenantAware;

    protected final LockRegistry lockRegistry;

    protected final RolloutApprovalStrategy rolloutApprovalStrategy;

    protected final TenantConfigurationManagement tenantConfigurationManagement;

    protected final SystemSecurityContext systemSecurityContext;

    protected AbstractRolloutManagement(final TargetManagement targetManagement,
            final DeploymentManagement deploymentManagement, final RolloutGroupManagement rolloutGroupManagement,
            final DistributionSetManagement distributionSetManagement, final ApplicationContext context,
            final VirtualPropertyReplacer virtualPropertyReplacer, final PlatformTransactionManager txManager,
            final TenantAware tenantAware, final LockRegistry lockRegistry,
            final RolloutApprovalStrategy rolloutApprovalStrategy,
            final TenantConfigurationManagement tenantConfigurationManagement,
            final SystemSecurityContext systemSecurityContext) {
        this.targetManagement = targetManagement;
        this.deploymentManagement = deploymentManagement;
        this.rolloutGroupManagement = rolloutGroupManagement;
        this.distributionSetManagement = distributionSetManagement;
        this.context = context;
        this.virtualPropertyReplacer = virtualPropertyReplacer;
        this.txManager = txManager;
        this.tenantAware = tenantAware;
        this.lockRegistry = lockRegistry;
        this.rolloutApprovalStrategy = rolloutApprovalStrategy;
        this.tenantConfigurationManagement = tenantConfigurationManagement;
        this.systemSecurityContext = systemSecurityContext;
    }

    protected RolloutGroupsValidation validateTargetsInGroups(final List<RolloutGroup> groups, final String baseFilter,
            final long totalTargets) {
        final List<Long> groupTargetCounts = new ArrayList<>(groups.size());
        final Map<String, Long> targetFilterCounts = groups.stream()
                .map(group -> RolloutHelper.getGroupTargetFilter(baseFilter, group)).distinct()
                .collect(Collectors.toMap(Function.identity(), targetManagement::countByRsql));

        long unusedTargetsCount = 0;

        for (int i = 0; i < groups.size(); i++) {
            final RolloutGroup group = groups.get(i);
            final String groupTargetFilter = RolloutHelper.getGroupTargetFilter(baseFilter, group);
            RolloutHelper.verifyRolloutGroupTargetPercentage(group.getTargetPercentage());

            final long targetsInGroupFilter = targetFilterCounts.get(groupTargetFilter);
            final long overlappingTargets = countOverlappingTargetsWithPreviousGroups(baseFilter, groups, group, i,
                    targetFilterCounts);

            final long realTargetsInGroup;
            // Assume that targets which were not used in the previous groups
            // are used in this group
            if (overlappingTargets > 0 && unusedTargetsCount > 0) {
                realTargetsInGroup = targetsInGroupFilter - overlappingTargets + unusedTargetsCount;
                unusedTargetsCount = 0;
            } else {
                realTargetsInGroup = targetsInGroupFilter - overlappingTargets;
            }

            final long reducedTargetsInGroup = Math
                    .round(group.getTargetPercentage() / 100 * (double) realTargetsInGroup);
            groupTargetCounts.add(reducedTargetsInGroup);
            unusedTargetsCount += realTargetsInGroup - reducedTargetsInGroup;

        }

        return new RolloutGroupsValidation(totalTargets, groupTargetCounts);
    }

    private long countOverlappingTargetsWithPreviousGroups(final String baseFilter, final List<RolloutGroup> groups,
            final RolloutGroup group, final int groupIndex, final Map<String, Long> targetFilterCounts) {
        // there can't be overlapping targets in the first group
        if (groupIndex == 0) {
            return 0;
        }
        final List<RolloutGroup> previousGroups = groups.subList(0, groupIndex);
        final String overlappingTargetsFilter = RolloutHelper.getOverlappingWithGroupsTargetFilter(baseFilter,
                previousGroups, group);

        if (targetFilterCounts.containsKey(overlappingTargetsFilter)) {
            return targetFilterCounts.get(overlappingTargetsFilter);
        } else {
            final long overlappingTargets = targetManagement.countByRsql(overlappingTargetsFilter);
            targetFilterCounts.put(overlappingTargetsFilter, overlappingTargets);
            return overlappingTargets;
        }
    }

    protected long calculateRemainingTargets(final List<RolloutGroup> groups, final String targetFilter,
            final Long createdAt) {
        final String baseFilter = RolloutHelper.getTargetFilterQuery(targetFilter, createdAt);
        final long totalTargets = targetManagement.countByRsql(baseFilter);
        if (totalTargets == 0) {
            throw new ConstraintDeclarationException("Rollout target filter does not match any targets");
        }

        final RolloutGroupsValidation validation = validateTargetsInGroups(groups, baseFilter, totalTargets);

        return totalTargets - validation.getTargetsInGroups();
    }
}
