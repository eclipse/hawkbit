/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettable;

import java.util.Collection;

import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.ui.common.data.filters.TargetManagementFilterParams;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.grid.support.FilterSupport;
import org.eclipse.hawkbit.ui.common.layout.AbstractFooterSupport;
import org.eclipse.hawkbit.ui.common.layout.CountAwareComponent;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.vaadin.data.provider.Query;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Label;

/**
 * Count message label which display current filter details and details on
 * pinning.
 */
public class TargetCountMessageLabel extends AbstractFooterSupport implements CountAwareComponent {
    private final VaadinMessageSource i18n;
    private final TargetManagement targetManagement;
    private final FilterSupport<ProxyTarget, TargetManagementFilterParams> gridFilterSupport;

    private final Label targetCountLabel;

    private int totalCount;
    private int filteredCount;

    /**
     * Constructor
     * 
     * @param i18n
     *            I18N
     */
    public TargetCountMessageLabel(final VaadinMessageSource i18n, final TargetManagement targetManagement,
            final FilterSupport<ProxyTarget, TargetManagementFilterParams> gridFilterSupport) {
        this.i18n = i18n;
        this.targetManagement = targetManagement;
        this.gridFilterSupport = gridFilterSupport;
        this.targetCountLabel = new Label();

        init();
    }

    private void init() {
        targetCountLabel.setId(UIComponentIdProvider.COUNT_LABEL);
        targetCountLabel.addStyleName(SPUIStyleDefinitions.SP_LABEL_MESSAGE_STYLE);
        targetCountLabel.setContentMode(ContentMode.HTML);
        targetCountLabel.setIcon(null);
        targetCountLabel.setDescription(null);
    }

    public void updateTotalCount() {
        totalCount = fetchTotalCount();
        updateCountLabel();
    }

    private int fetchTotalCount() {
        return gridFilterSupport.getOriginalDataProvider().size(new Query<>());
    }

    public void updateFilteredCount() {
        if (gridFilterSupport.getFilter().isAnyFilterSelected()) {
            filteredCount = fetchFilteredCount();
        }

        updateCountLabel();
    }

    private int fetchFilteredCount() {
        return gridFilterSupport.getFilterDataProvider().size(new Query<>());
    }

    public void updateTotalAndFilteredCount() {
        totalCount = fetchTotalCount();
        if (gridFilterSupport.getFilter().isAnyFilterSelected()) {
            filteredCount = fetchFilteredCount();
        }

        updateCountLabel();
    }

    private void updateCountLabel() {
        final StringBuilder countMessageBuilder = getTotalTargetsMessage();

        final TargetManagementFilterParams targetFilterParams = gridFilterSupport.getFilter();
        if (targetFilterParams.isAnyFilterSelected()) {
            appendFilteredTargetsMessage(countMessageBuilder, targetFilterParams);
        }

        targetCountLabel.setCaption(countMessageBuilder.toString());
    }

    private StringBuilder getTotalTargetsMessage() {
        final StringBuilder message = new StringBuilder(i18n.getMessage("label.target.filter.count"));
        message.append(": ");
        message.append(totalCount);

        return message;
    }

    private void appendFilteredTargetsMessage(final StringBuilder countMessageBuilder,
            final TargetManagementFilterParams targetFilterParams) {
        countMessageBuilder.append(HawkbitCommonUtil.SP_STRING_PIPE);
        countMessageBuilder.append(i18n.getMessage("label.filter.targets"));
        countMessageBuilder.append(filteredCount);
        countMessageBuilder.append(HawkbitCommonUtil.SP_STRING_PIPE);
        countMessageBuilder.append(getFilterParametersMessage(targetFilterParams));
    }

    private String getFilterParametersMessage(final TargetManagementFilterParams targetFilterParams) {
        final StringBuilder filterMessageBuilder = new StringBuilder(i18n.getMessage("label.filter"));

        filterMessageBuilder.append(" ");
        appendStatusMsg(filterMessageBuilder, targetFilterParams.getTargetUpdateStatusList());
        appendOverdueStateMsg(filterMessageBuilder, targetFilterParams.isOverdueState());
        appendTagsMsg(filterMessageBuilder, targetFilterParams.isNoTagClicked(), targetFilterParams.getTargetTags());
        appendSearchMsg(filterMessageBuilder, targetFilterParams.getSearchText());
        appendDsMsg(filterMessageBuilder, targetFilterParams.getDistributionId());
        appendCustomFilterQueryMsg(filterMessageBuilder, targetFilterParams.getTargetFilterQueryId());

        String filterMessage = filterMessageBuilder.toString().trim();
        if (filterMessage.endsWith(",")) {
            filterMessage = filterMessage.substring(0, filterMessage.length() - 1);
        }

        return filterMessage;
    }

    private void appendStatusMsg(final StringBuilder filterMessageBuilder,
            final Collection<TargetUpdateStatus> status) {
        if (!status.isEmpty()) {
            appendFilterMsg(filterMessageBuilder, i18n.getMessage("label.filter.status"));
        }
    }

    private static void appendFilterMsg(final StringBuilder filterMessageBuilder, final String filter) {
        filterMessageBuilder.append(filter);
        filterMessageBuilder.append(", ");
    }

    private void appendOverdueStateMsg(final StringBuilder filterMessageBuilder, final boolean overdueState) {
        if (overdueState) {
            appendFilterMsg(filterMessageBuilder, i18n.getMessage("label.filter.overdue"));
        }
    }

    private void appendTagsMsg(final StringBuilder filterMessageBuilder, final boolean noTargetTagSelected,
            final Collection<String> tags) {
        if (noTargetTagSelected || !CollectionUtils.isEmpty(tags)) {
            appendFilterMsg(filterMessageBuilder, i18n.getMessage("label.filter.tags"));
        }
    }

    private void appendSearchMsg(final StringBuilder filterMessageBuilder, final String search) {
        if (!StringUtils.isEmpty(search)) {
            appendFilterMsg(filterMessageBuilder, i18n.getMessage("label.filter.text"));
        }
    }

    private void appendDsMsg(final StringBuilder filterMessageBuilder, final Long dsId) {
        if (dsId != null) {
            appendFilterMsg(filterMessageBuilder, i18n.getMessage("label.filter.dist"));
        }
    }

    private void appendCustomFilterQueryMsg(final StringBuilder filterMessageBuilder, final Long customFilterQueryId) {
        if (customFilterQueryId != null) {
            appendFilterMsg(filterMessageBuilder, i18n.getMessage("label.filter.custom"));
        }
    }

    /**
     * Update pinning details
     *
     */
    public void updatePinningDetails() {
        final Long pinnedDsId = gridFilterSupport.getFilter().getPinnedDistId();
        if (pinnedDsId == null) {
            targetCountLabel.setValue("");
            return;
        }

        final Long targetsWithAssigedDsCount = targetManagement.countByAssignedDistributionSet(pinnedDsId);
        final Long targetsWithInstalledDsCount = targetManagement.countByInstalledDistributionSet(pinnedDsId);

        final StringBuilder message = new StringBuilder(i18n.getMessage("label.target.count"));
        message.append(" : ");
        message.append("<span class=\"assigned-count-message\">");
        message.append(i18n.getMessage("label.assigned.count", targetsWithAssigedDsCount));
        message.append("</span>, <span class=\"installed-count-message\"> ");
        message.append(i18n.getMessage("label.installed.count", targetsWithInstalledDsCount));
        message.append("</span>");

        targetCountLabel.setValue(message.toString());
    }

    @Override
    protected Label getFooterMessageLabel() {
        return targetCountLabel;
    }

    @Override
    public void updateCountOnEntitiesAdded(final int count) {
        updateTotalAndFilteredCount();
    }

    @Override
    public void updateCountOnEntitiesUpdated() {
        updateFilteredCount();
        updatePinningDetails();
    }

    @Override
    public void updateCountOnEntitiesDeleted(final int count) {
        updateTotalAndFilteredCount();
        updatePinningDetails();
    }
}
