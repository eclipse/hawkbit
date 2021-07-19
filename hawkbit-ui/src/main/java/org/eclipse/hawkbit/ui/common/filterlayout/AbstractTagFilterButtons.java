/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.filterlayout;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTag;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload.EntityModifiedEventType;
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

import com.vaadin.ui.Button;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;

/**
 * Class for defining the tag filter buttons.
 */
public abstract class AbstractTagFilterButtons extends AbstractFilterButtons<ProxyTag, Void> {
    private static final long serialVersionUID = 1L;

    private final TagFilterLayoutUiState tagFilterLayoutUiState;

    protected final UINotification uiNotification;
    private final Button noTagButton;
    private final transient TagFilterButtonClick tagFilterButtonClick;

    /**
     * Constructor for AbstractTagFilterButtons
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param tagFilterLayoutUiState
     *            TagFilterLayoutUiState
     */
    protected AbstractTagFilterButtons(final CommonUiDependencies uiDependencies,
            final TagFilterLayoutUiState tagFilterLayoutUiState) {
        super(uiDependencies.getEventBus(), uiDependencies.getI18n(), uiDependencies.getUiNotification(),
                uiDependencies.getPermChecker());

        this.uiNotification = uiDependencies.getUiNotification();
        this.tagFilterLayoutUiState = tagFilterLayoutUiState;
        this.noTagButton = buildNoTagButton();
        this.tagFilterButtonClick = new TagFilterButtonClick(this::onFilterChangedEvent, this::onNoTagChangedEvent);
    }

    private Button buildNoTagButton() {
        final Button noTag = SPUIComponentProvider.getButton(
                getFilterButtonIdPrefix() + "." + SPUIDefinitions.NO_TAG_BUTTON_ID,
                i18n.getMessage(UIMessageIdProvider.LABEL_NO_TAG),
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_CLICK_TO_FILTER), "button-no-tag", false, null,
                SPUITagButtonStyle.class);

        final ProxyTag dummyNoTag = new ProxyTag();
        dummyNoTag.setNoTag(true);

        noTag.addClickListener(event -> getFilterButtonClickBehaviour().processFilterClick(dummyNoTag));

        return noTag;
    }

    @Override
    protected TagFilterButtonClick getFilterButtonClickBehaviour() {
        return tagFilterButtonClick;
    }

    private void onFilterChangedEvent(final Map<Long, String> activeTagIdsWithName) {
        getDataCommunicator().reset();

        publishFilterChangedEvent(activeTagIdsWithName);
    }

    private void publishFilterChangedEvent(final Map<Long, String> activeTagIdsWithName) {
        eventBus.publish(EventTopics.FILTER_CHANGED, this, new FilterChangedEventPayload<>(getFilterMasterEntityType(),
                FilterType.TAG, activeTagIdsWithName.values(), getView()));

        tagFilterLayoutUiState.setClickedTagIdsWithName(activeTagIdsWithName);
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
        final boolean isNoTagActivated = ClickBehaviourType.CLICKED == clickType;

        if (isNoTagActivated) {
            getNoTagButton().addStyleName(SPUIStyleDefinitions.SP_NO_TAG_BTN_CLICKED_STYLE);
        } else {
            getNoTagButton().removeStyleName(SPUIStyleDefinitions.SP_NO_TAG_BTN_CLICKED_STYLE);
        }

        publishNoTagChangedEvent(isNoTagActivated);
    }

    private void publishNoTagChangedEvent(final boolean isNoTagActivated) {
        eventBus.publish(EventTopics.FILTER_CHANGED, this, new FilterChangedEventPayload<>(getFilterMasterEntityType(),
                FilterType.NO_TAG, isNoTagActivated, getView()));

        tagFilterLayoutUiState.setNoTagClicked(isNoTagActivated);
    }

    @Override
    protected boolean deleteFilterButtons(final Collection<ProxyTag> filterButtonsToDelete) {
        // We do not allow multiple deletion of tags yet
        final ProxyTag tagToDelete = filterButtonsToDelete.iterator().next();
        final String tagToDeleteName = tagToDelete.getName();
        final Long tagToDeleteId = tagToDelete.getId();

        final Set<Long> clickedTagIds = getFilterButtonClickBehaviour().getPreviouslyClickedFilterIds();

        if (!CollectionUtils.isEmpty(clickedTagIds) && clickedTagIds.contains(tagToDeleteId)) {
            uiNotification.displayValidationError(i18n.getMessage("message.tag.delete", tagToDeleteName));

            return false;
        } else {
            deleteTag(tagToDelete);

            eventBus.publish(EventTopics.ENTITY_MODIFIED, this,
                    new EntityModifiedEventPayload(EntityModifiedEventType.ENTITY_REMOVED, getFilterMasterEntityType(),
                            ProxyTag.class, tagToDeleteId));

            return true;
        }
    }

    /**
     * Tag deletion operation.
     * 
     * @param tagToDelete
     *            tag to delete
     */
    protected abstract void deleteTag(final ProxyTag tagToDelete);

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

    @Override
    protected void editButtonClickListener(final ProxyTag clickedFilter) {
        final Window updateWindow = getUpdateWindow(clickedFilter);

        updateWindow.setCaption(i18n.getMessage("caption.update", i18n.getMessage("caption.tag")));
        UI.getCurrent().addWindow(updateWindow);
        updateWindow.setVisible(Boolean.TRUE);
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
    public Button getNoTagButton() {
        return noTagButton;
    }

    /**
     * Remove the tag filters
     */
    public void clearTargetTagFilters() {
        if (getFilterButtonClickBehaviour().getPreviouslyClickedFiltersSize() > 0) {
            if (tagFilterLayoutUiState.isNoTagClicked()) {
                tagFilterLayoutUiState.setNoTagClicked(false);
                getNoTagButton().removeStyleName(SPUIStyleDefinitions.SP_NO_TAG_BTN_CLICKED_STYLE);
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
            getNoTagButton().addStyleName(SPUIStyleDefinitions.SP_NO_TAG_BTN_CLICKED_STYLE);
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
