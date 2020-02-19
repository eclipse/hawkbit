/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import static org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey.MULTI_ASSIGNMENTS_WEIGHT_DEFAULT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.hawkbit.repository.OffsetBasedPageRequest;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.common.UserDetailsFormatter;
import org.eclipse.hawkbit.ui.components.ProxyDistribution;
import org.eclipse.hawkbit.ui.components.ProxyTargetFilter;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SpringContextHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.util.StringUtils;
import org.vaadin.addons.lazyquerycontainer.AbstractBeanQuery;
import org.vaadin.addons.lazyquerycontainer.QueryDefinition;

/**
 *
 *
 *
 */
public class TargetFilterBeanQuery extends AbstractBeanQuery<ProxyTargetFilter> {

    private static final long serialVersionUID = 1845964596238990987L;
    private Sort sort = new Sort(Direction.ASC, "name");
    private String searchText = null;
    private transient Page<TargetFilterQuery> firstPageTargetFilter = null;
    private transient TargetFilterQueryManagement targetFilterQueryManagement;
    private transient TenantConfigurationManagement configManagement;
    private transient SystemSecurityContext systemSecurityContext;

    /**
     *
     * @param definition
     * @param queryConfig
     * @param sortPropertyIds
     * @param sortStates
     */
    public TargetFilterBeanQuery(final QueryDefinition definition, final Map<String, Object> queryConfig,
            final Object[] sortPropertyIds, final boolean[] sortStates) {
        super(definition, queryConfig, sortPropertyIds, sortStates);
        if (HawkbitCommonUtil.isNotNullOrEmpty(queryConfig)) {
            searchText = (String) queryConfig.get(SPUIDefinitions.FILTER_BY_TEXT);
            if (!StringUtils.isEmpty(searchText)) {
                searchText = String.format("%%%s%%", searchText);
            }
        }

        if (sortStates != null && sortStates.length > 0) {
            // Initalize sort
            sort = new Sort(sortStates[0] ? Direction.ASC : Direction.DESC, (String) sortPropertyIds[0]);
            // Add sort
            for (int tfId = 1; tfId < sortPropertyIds.length; tfId++) {
                sort.and(new Sort(sortStates[tfId] ? Direction.ASC : Direction.DESC, (String) sortPropertyIds[tfId]));
            }
        }
    }

    @Override
    protected ProxyTargetFilter constructBean() {
        return new ProxyTargetFilter();
    }

    @Override
    protected List<ProxyTargetFilter> loadBeans(final int startIndex, final int count) {
        List<ProxyTargetFilter> targetFilterQuery;
        if (startIndex == 0 && firstPageTargetFilter != null) {
            targetFilterQuery = validateWeightWithMultiAssignments(firstPageTargetFilter);
        } else if (StringUtils.isEmpty(searchText)) {
            // if no search filters available
            targetFilterQuery = validateWeightWithMultiAssignments(getTargetFilterQueryManagement()
                    .findAll(new OffsetBasedPageRequest(startIndex / SPUIDefinitions.PAGE_SIZE, count, sort)));
        } else {
            targetFilterQuery = validateWeightWithMultiAssignments(getTargetFilterQueryManagement().findByName(
                    new OffsetBasedPageRequest(startIndex / SPUIDefinitions.PAGE_SIZE, count, sort),
                    searchText));
        }
        return targetFilterQuery;
    }

    private static List<ProxyTargetFilter> createProxyTargetFilters(final Slice<TargetFilterQuery> targetFilterQuery) {
        final List<ProxyTargetFilter> proxyTargetFilter = new ArrayList<>();
        for (final TargetFilterQuery tarFilterQuery : targetFilterQuery) {
            final ProxyTargetFilter proxyTarFilter = new ProxyTargetFilter();
            proxyTarFilter.setName(tarFilterQuery.getName());
            proxyTarFilter.setId(tarFilterQuery.getId());
            proxyTarFilter.setCreatedDate(SPDateTimeUtil.getFormattedDate(tarFilterQuery.getCreatedAt()));
            proxyTarFilter.setCreatedBy(UserDetailsFormatter.loadAndFormatCreatedBy(tarFilterQuery));
            proxyTarFilter.setModifiedDate(SPDateTimeUtil.getFormattedDate(tarFilterQuery.getLastModifiedAt()));
            proxyTarFilter.setLastModifiedBy(UserDetailsFormatter.loadAndFormatLastModifiedBy(tarFilterQuery));
            proxyTarFilter.setAutoAssignWeight(tarFilterQuery.getAutoAssignWeight().orElse(null));
            proxyTarFilter.setQuery(tarFilterQuery.getQuery());

            final DistributionSet distributionSet = tarFilterQuery.getAutoAssignDistributionSet();
            if (distributionSet != null) {
                proxyTarFilter.setAutoAssignDistributionSet(new ProxyDistribution(distributionSet));
                // we need to apply a fallback since the action type field has
                // been added belatedly (and might be null for older filters)
                final ActionType autoAssignActionType = tarFilterQuery.getAutoAssignActionType();
                proxyTarFilter.setAutoAssignActionType(
                        autoAssignActionType != null ? autoAssignActionType : ActionType.FORCED);
            }

            proxyTargetFilter.add(proxyTarFilter);
        }
        return proxyTargetFilter;
    }

    private List<ProxyTargetFilter> fillDefaultWeightForTFQAddedInSingleAssignment(
            final Slice<TargetFilterQuery> targetQueryFilters) {

        final List<ProxyTargetFilter> finalProxyTargetFilters = new ArrayList<>();
        for (final TargetFilterQuery targetQueryFilter : targetQueryFilters) {

            final int defaultWeight = targetQueryFilter.getAutoAssignWeight().orElse(getTenantConfigurationManagement()
                    .getConfigurationValue(MULTI_ASSIGNMENTS_WEIGHT_DEFAULT, Integer.class).getValue());

            final ProxyTargetFilter proxyTargetFilter = createProxyTargetFilters(
                    new SliceImpl<>(Arrays.asList(targetQueryFilter))).get(0);
            if (proxyTargetFilter.getAutoAssignWeight() == null) {
                proxyTargetFilter.setAutoAssignWeight(defaultWeight);
            }
            finalProxyTargetFilters.add(proxyTargetFilter);
        }

        return finalProxyTargetFilters;
    }

    private List<ProxyTargetFilter> validateWeightWithMultiAssignments(
            final Slice<TargetFilterQuery> targetQueryFilters) {
        return isMultiAssignmentEnabled() ? fillDefaultWeightForTFQAddedInSingleAssignment(targetQueryFilters)
                : createProxyTargetFilters(targetQueryFilters);
    }

    @Override
    protected void saveBeans(final List<ProxyTargetFilter> arg0, final List<ProxyTargetFilter> arg1,
            final List<ProxyTargetFilter> arg2) {
        /* CRUD operations on Target will be done through repository methods. */

    }

    @Override
    public int size() {
        if (StringUtils.isEmpty(searchText)) {
            firstPageTargetFilter = getTargetFilterQueryManagement()
                    .findAll(new OffsetBasedPageRequest(0, SPUIDefinitions.PAGE_SIZE, sort));
        } else {
            firstPageTargetFilter = getTargetFilterQueryManagement()
                    .findByName(new OffsetBasedPageRequest(0, SPUIDefinitions.PAGE_SIZE, sort), searchText);
        }
        final long size = firstPageTargetFilter.getTotalElements();

        if (size > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) size;
    }

    private TargetFilterQueryManagement getTargetFilterQueryManagement() {
        if (targetFilterQueryManagement == null) {
            targetFilterQueryManagement = SpringContextHelper.getBean(TargetFilterQueryManagement.class);
        }
        return targetFilterQueryManagement;
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
