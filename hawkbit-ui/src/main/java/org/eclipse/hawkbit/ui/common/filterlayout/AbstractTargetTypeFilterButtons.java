/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.filterlayout;

import com.vaadin.ui.Button;
import com.vaadin.ui.Window;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTag;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetType;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.FilterChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.common.filterlayout.AbstractFilterButtonClickBehaviour.ClickBehaviourType;
import org.eclipse.hawkbit.ui.common.state.TagFilterLayoutUiState;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUITagButtonStyle;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Class for defining the type filter buttons.
 */
public abstract class AbstractTargetTypeFilterButtons extends AbstractFilterButtons<ProxyTargetType, Void> {
    private static final long serialVersionUID = 1L;

    private final TagFilterLayoutUiState tagFilterLayoutUiState;

    protected final UINotification uiNotification;
    private final Button noTargetTypeButton;

    private final TargetTypeFilterButtonClick targetTypeFilterButtonClick;

    /**
     * Constructor for AbstractTargetTypeFilterButtons
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param tagFilterLayoutUiState
     *            TagFilterLayoutUiState
     */
    protected AbstractTargetTypeFilterButtons(final CommonUiDependencies uiDependencies,
                                              final TagFilterLayoutUiState tagFilterLayoutUiState) {
        super(uiDependencies.getEventBus(), uiDependencies.getI18n(), uiDependencies.getUiNotification(),
                uiDependencies.getPermChecker());

        this.uiNotification = uiDependencies.getUiNotification();
        this.tagFilterLayoutUiState = tagFilterLayoutUiState;
        this.noTargetTypeButton = buildNoTargetTypeButton();
        this.targetTypeFilterButtonClick = new TargetTypeFilterButtonClick(this::onFilterChangedEvent, this::onNoTagChangedEvent);
    }

    private Button buildNoTargetTypeButton() {
        final Button noTargetType = SPUIComponentProvider.getButton(
                getFilterButtonIdPrefix() + "." + SPUIDefinitions.NO_TARGET_TYPE_BUTTON_ID,
                i18n.getMessage(UIMessageIdProvider.LABEL_NO_TARGET_TYPE),
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_CLICK_TO_FILTER), "button-no-tag", false, null,
                SPUITagButtonStyle.class);

        final ProxyTargetType proxyTargetType = new ProxyTargetType();
        proxyTargetType.setNoTargetType(true);

        noTargetType.addClickListener(event -> getFilterButtonClickBehaviour().processFilterClick(proxyTargetType));

        return noTargetType;
    }

    @Override
    protected TargetTypeFilterButtonClick getFilterButtonClickBehaviour() {
        return targetTypeFilterButtonClick;
    }

    private void onFilterChangedEvent(final Map<Long, String> activeTypeIdsWithName) {
        getDataCommunicator().reset();

        publishFilterChangedEvent(activeTypeIdsWithName);
    }

    private void publishFilterChangedEvent(final Map<Long, String> activeTypeIdsWithName) {
        eventBus.publish(EventTopics.FILTER_CHANGED, this, new FilterChangedEventPayload<>(getFilterMasterEntityType(),
                FilterType.TYPE, activeTypeIdsWithName.values(), getView()));

        tagFilterLayoutUiState.setClickedTagIdsWithName(activeTypeIdsWithName);
    }

    /**
     * Provides type of the master entity.
     * 
     * @return type of the master entity
     */
    protected abstract Class<? extends ProxyIdentifiableEntity> getFilterMasterEntityType();

    /**
     * Provides event view filter.
     * 
     * @return event view filter.
     */
    protected abstract EventView getView();

    private void onNoTagChangedEvent(final ClickBehaviourType clickType) {
        final boolean isNoTypeActivated = ClickBehaviourType.CLICKED == clickType;

        if (isNoTypeActivated) {
            getNoTargetTypeButton().addStyleName(SPUIStyleDefinitions.SP_NO_TAG_BTN_CLICKED_STYLE);
        } else {
            getNoTargetTypeButton().removeStyleName(SPUIStyleDefinitions.SP_NO_TAG_BTN_CLICKED_STYLE);
        }

        publishNoTypeChangedEvent(isNoTypeActivated);
    }

    private void publishNoTypeChangedEvent(final boolean isNoTypeActivated) {
        eventBus.publish(EventTopics.FILTER_CHANGED, this, new FilterChangedEventPayload<>(getFilterMasterEntityType(),
                FilterType.NO_TAG, isNoTypeActivated, getView()));

        tagFilterLayoutUiState.setNoTagClicked(isNoTypeActivated);
    }


    /**
     * Tag deletion operation.
     * 
     * @param tagToDelete
     *            tag to delete
     */
    protected abstract void deleteTag(final ProxyTargetType tagToDelete);

    /**
     * Reset the filter by removing the deleted tags
     *
     * @param deletedTagIds
     *            List of deleted tags Id
     */
    public void resetFilterOnTagsDeleted(final Collection<Long> deletedTagIds) {
        if (isAtLeastOneClickedTagInIds(deletedTagIds)) {
            deletedTagIds.forEach(getFilterButtonClickBehaviour()::removePreviouslyClickedFilter);
            publishFilterChangedEvent(getFilterButtonClickBehaviour().getPreviouslyClickedFilterIdsWithName());
        }
    }

    /**
     * @param tagIds
     *            List of tags Id
     *
     * @return true if at least one tag found in list of clicked tag Ids else
     *         false
     */
    private boolean isAtLeastOneClickedTagInIds(final Collection<Long> tagIds) {
        final Set<Long> clickedTagIds = getFilterButtonClickBehaviour().getPreviouslyClickedFilterIds();

        return !CollectionUtils.isEmpty(clickedTagIds) && !Collections.disjoint(clickedTagIds, tagIds);
    }


    /**
     * Provides the window for updating tag
     *
     * @param clickedFilter
     *            tag to update
     * @return update window
     */
    protected abstract Window getUpdateWindow(final ProxyTag clickedFilter);


    /**
     * @return Button component of no tag
     */
    public Button getNoTargetTypeButton() {
        return noTargetTypeButton;
    }

    /**
     * Remove the tag filters
     */
    public void clearTargetTagFilters() {
        if (getFilterButtonClickBehaviour().getPreviouslyClickedFiltersSize() > 0) {
            if (tagFilterLayoutUiState.isNoTagClicked()) {
                tagFilterLayoutUiState.setNoTagClicked(false);
                getNoTargetTypeButton().removeStyleName(SPUIStyleDefinitions.SP_NO_TAG_BTN_CLICKED_STYLE);
            }

            getFilterButtonClickBehaviour().clearPreviouslyClickedFilters();
            tagFilterLayoutUiState.setClickedTagIdsWithName(Collections.emptyMap());
        }
    }

    @Override
    public void restoreState() {
        final Map<Long, String> tagsToRestore = tagFilterLayoutUiState.getClickedTagIdsWithName();

        if (!CollectionUtils.isEmpty(tagsToRestore)) {
            removeNonExistingTags(tagsToRestore);
            getFilterButtonClickBehaviour().setPreviouslyClickedFilterIdsWithName(tagsToRestore);
        }

        if (tagFilterLayoutUiState.isNoTagClicked()) {
            getNoTargetTypeButton().addStyleName(SPUIStyleDefinitions.SP_NO_TAG_BTN_CLICKED_STYLE);
        }
    }

    private boolean removeNonExistingTags(final Map<Long, String> tagIdsWithName) {
        final Collection<Long> tagIds = tagIdsWithName.keySet();
        final Collection<Long> existingTagIds = filterExistingTagIds(tagIds);
        if (tagIds.size() != existingTagIds.size()) {
            return tagIds.retainAll(existingTagIds);
        }

        return false;
    }

    /**
     * Filters out non-existant tags by ids.
     *
     * @param tagIds
     *            provided tag ids
     * @return filtered list of existing tag ids
     */
    protected abstract Collection<Long> filterExistingTagIds(final Collection<Long> tagIds);

    /**
     * Re-evaluates a filter (usually after view enter).
     *
     */
    public void reevaluateFilter() {
        final Map<Long, String> clickedTags = getFilterButtonClickBehaviour().getPreviouslyClickedFilterIdsWithName();

        if (!CollectionUtils.isEmpty(clickedTags) && removeNonExistingTags(clickedTags)) {
            publishFilterChangedEvent(clickedTags);
        }
    }
}
