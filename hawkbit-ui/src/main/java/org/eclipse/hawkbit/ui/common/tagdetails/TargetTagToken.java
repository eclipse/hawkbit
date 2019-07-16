/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.tagdetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.management.event.ManagementUIEvent;
import org.eclipse.hawkbit.ui.management.event.TargetTableEvent;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.data.domain.PageRequest;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

/**
 * Implementation of Target tag token.
 *
 *
 */
public class TargetTagToken extends AbstractTargetTagToken<Target> {

    private static final long serialVersionUID = 7124887018280196721L;

    private static final int MAX_TAGS = 500;

    private final transient TargetManagement targetManagement;

    public TargetTagToken(final SpPermissionChecker checker, final VaadinMessageSource i18n,
            final UINotification uinotification, final UIEventBus eventBus, final ManagementUIState managementUIState,
            final TargetTagManagement tagManagement, final TargetManagement targetManagement) {
        super(checker, i18n, uinotification, eventBus, managementUIState, tagManagement);
        this.targetManagement = targetManagement;
    }

    @Override
    protected void assignTag(final TagData tagData) {
        final List<Target> assignedTargets = targetManagement.assignTag(Arrays.asList(selectedEntity.getControllerId()),
                tagData.getId());
        if (checkAssignmentResult(assignedTargets, selectedEntity.getId())) {
            uinotification.displaySuccess(
                    i18n.getMessage("message.target.assigned.one", selectedEntity.getName(), tagData.getName()));
            eventBus.publish(this, ManagementUIEvent.ASSIGN_TARGET_TAG);
            tagPanel.setAssignedTag(tagData);
        }
    }

    @Override
    protected void unassignTag(final TagData tagData) {
        final Target unassignedTarget = targetManagement.unAssignTag(selectedEntity.getControllerId(), tagData.getId());
        if (checkUnassignmentResult(unassignedTarget, selectedEntity.getId())) {
            uinotification.displaySuccess(
                    i18n.getMessage("message.target.unassigned.one", selectedEntity.getName(), tagData.getName()));
            eventBus.publish(this, ManagementUIEvent.UNASSIGN_TARGET_TAG);
            tagPanel.removeAssignedTag(tagData);
        }
    }

    @Override
    protected Boolean isToggleTagAssignmentAllowed() {
        return checker.hasUpdateTargetPermission();
    }

    @Override
    protected List<TagData> getAllAssignableTags() {
        final List<TagData> allTags = new ArrayList<>();
        tagManagement.findAll(PageRequest.of(0, MAX_TAGS))
                .forEach(tag -> allTags.add(new TagData(tag.getId(), tag.getName(), tag.getColour())));
        return allTags;
    }

    @Override
    protected List<TagData> getAssignedTags() {
        final List<TagData> assignedTags = new ArrayList<>();
        if (selectedEntity != null) {
            tagManagement.findByTarget(PageRequest.of(0, MAX_TAGS), selectedEntity.getControllerId())
                    .forEach(tag -> assignedTags.add(new TagData(tag.getId(), tag.getName(), tag.getColour())));
        }
        return assignedTags;
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final TargetTableEvent targetTableEvent) {
        onBaseEntityEvent(targetTableEvent);
    }

}
