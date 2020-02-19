/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.rollout.rollout;

import static org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey.MULTI_ASSIGNMENTS_WEIGHT_DEFAULT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.OffsetBasedPageRequest;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.TotalTargetCountStatus;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.common.UserDetailsFormatter;
import org.eclipse.hawkbit.ui.customrenderers.client.renderers.RolloutRendererData;
import org.eclipse.hawkbit.ui.rollout.state.RolloutUIState;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SpringContextHelper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.util.StringUtils;
import org.vaadin.addons.lazyquerycontainer.AbstractBeanQuery;
import org.vaadin.addons.lazyquerycontainer.QueryDefinition;

/**
 *
 * Simple implementation of generics bean query which dynamically loads a batch
 * of {@link ProxyRollout} beans.
 *
 */
public class RolloutBeanQuery extends AbstractBeanQuery<ProxyRollout> {

    private static final long serialVersionUID = 4027879794344836185L;

    private final String searchText;

    private Sort sort = new Sort(Direction.DESC, "lastModifiedAt");

    private transient RolloutManagement rolloutManagement;

    private transient RolloutUIState rolloutUIState;

    private transient TenantConfigurationManagement configManagement;

    private transient SystemSecurityContext systemSecurityContext;

    /**
     * Parametric Constructor.
     *
     * @param definition
     *            as QueryDefinition
     * @param queryConfig
     *            as Config
     * @param sortIds
     *            as sort
     * @param sortStates
     *            as Sort status
     */
    public RolloutBeanQuery(final QueryDefinition definition, final Map<String, Object> queryConfig,
            final Object[] sortIds, final boolean[] sortStates) {
        super(definition, queryConfig, sortIds, sortStates);

        searchText = getSearchText();

        if (sortStates != null && sortStates.length > 0) {

            sort = new Sort(sortStates[0] ? Direction.ASC : Direction.DESC, (String) sortIds[0]);

            for (int targetId = 1; targetId < sortIds.length; targetId++) {
                sort.and(new Sort(sortStates[targetId] ? Direction.ASC : Direction.DESC, (String) sortIds[targetId]));
            }
        }
    }

    private String getSearchText() {
        return getRolloutUIState().getSearchText().map(value -> String.format("%%%s%%", value)).orElse(null);
    }

    @Override
    protected ProxyRollout constructBean() {
        return new ProxyRollout();
    }

    @Override
    protected List<ProxyRollout> loadBeans(final int startIndex, final int count) {
        final List<ProxyRollout> rolloutBeans;
        final PageRequest pageRequest = new OffsetBasedPageRequest(startIndex / SPUIDefinitions.PAGE_SIZE, count, sort);
        if (StringUtils.isEmpty(searchText)) {
            rolloutBeans = validateWeightWithMultiAssignments(
                    getRolloutManagement().findAllWithDetailedStatus(pageRequest, false));
        } else {
            rolloutBeans = validateWeightWithMultiAssignments(
                    getRolloutManagement().findByFiltersWithDetailedStatus(pageRequest, searchText, false));
        }
        return rolloutBeans;
    }

    private static List<ProxyRollout> getProxyRolloutList(final Slice<Rollout> rolloutBeans) {
        return rolloutBeans.getContent().stream().map(RolloutBeanQuery::createProxy).collect(Collectors.toList());
    }

    private List<ProxyRollout> fillDefaultWeightForRolloutsAddedInSingleAssignment(final Slice<Rollout> rollouts) {

        final List<ProxyRollout> finalProxyRollouts = new ArrayList<>();
        for (final Rollout rollout : rollouts) {
            final int defaultWeight = rollout.getWeight()
                    .orElse(getTenantConfigurationManagement()
                            .getConfigurationValue(MULTI_ASSIGNMENTS_WEIGHT_DEFAULT, Integer.class).getValue());
            final ProxyRollout proxyAction = createProxy(rollout);
            if (proxyAction.getWeight() == null) {
                proxyAction.setWeight(defaultWeight);
            }
            finalProxyRollouts.add(proxyAction);
        }

        return finalProxyRollouts;
    }

    private List<ProxyRollout> validateWeightWithMultiAssignments(final Slice<Rollout> rollouts) {
        return isMultiAssignmentEnabled() ? fillDefaultWeightForRolloutsAddedInSingleAssignment(rollouts)
                : getProxyRolloutList(rollouts);
    }

    private static ProxyRollout createProxy(final Rollout rollout) {
        final ProxyRollout proxyRollout = new ProxyRollout();
        proxyRollout.setName(rollout.getName());
        proxyRollout.setDescription(rollout.getDescription());
        proxyRollout.setWeight(rollout.getWeight().orElse(null));
        final DistributionSet distributionSet = rollout.getDistributionSet();
        proxyRollout.setDistributionSetNameVersion(
                HawkbitCommonUtil.getFormattedNameVersion(distributionSet.getName(), distributionSet.getVersion()));
        proxyRollout.setNumberOfGroups(rollout.getRolloutGroupsCreated());
        proxyRollout.setCreatedDate(SPDateTimeUtil.getFormattedDate(rollout.getCreatedAt()));
        proxyRollout.setModifiedDate(SPDateTimeUtil.getFormattedDate(rollout.getLastModifiedAt()));
        proxyRollout.setCreatedBy(UserDetailsFormatter.loadAndFormatCreatedBy(rollout));
        proxyRollout.setLastModifiedBy(UserDetailsFormatter.loadAndFormatLastModifiedBy(rollout));
        proxyRollout.setForcedTime(rollout.getForcedTime());
        proxyRollout.setId(rollout.getId());
        proxyRollout.setStatus(rollout.getStatus());
        proxyRollout.setActionType(rollout.getActionType());
        proxyRollout.setRolloutRendererData(new RolloutRendererData(rollout.getName(), rollout.getStatus().toString()));

        final TotalTargetCountStatus totalTargetCountActionStatus = rollout.getTotalTargetCountStatus();
        proxyRollout.setTotalTargetCountStatus(totalTargetCountActionStatus);
        proxyRollout.setTotalTargetsCount(String.valueOf(rollout.getTotalTargets()));
        proxyRollout.setApprovalDecidedBy(rollout.getApprovalDecidedBy());
        proxyRollout.setApprovalRemark(rollout.getApprovalRemark());
        return proxyRollout;
    }

    @Override
    protected void saveBeans(final List<ProxyRollout> arg0, final List<ProxyRollout> arg1,
            final List<ProxyRollout> arg2) {
        /**
         * CRUD operations on Target will be done through repository methods
         */
    }

    @Override
    public int size() {
        long size = getRolloutManagement().count();
        if (!StringUtils.isEmpty(searchText)) {
            size = getRolloutManagement().countByFilters(searchText);
        }
        return (int) size;
    }

    private RolloutManagement getRolloutManagement() {
        if (null == rolloutManagement) {
            rolloutManagement = SpringContextHelper.getBean(RolloutManagement.class);
        }
        return rolloutManagement;
    }

    private RolloutUIState getRolloutUIState() {
        if (null == rolloutUIState) {
            rolloutUIState = SpringContextHelper.getBean(RolloutUIState.class);
        }
        return rolloutUIState;
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
