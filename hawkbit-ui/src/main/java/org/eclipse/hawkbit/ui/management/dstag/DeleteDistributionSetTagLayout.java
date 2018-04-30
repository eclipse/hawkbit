/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.dstag;

import java.util.List;
import java.util.Optional;

import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.repository.DistributionSetTagManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.model.DistributionSetTag;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEventType;
import org.eclipse.hawkbit.ui.management.event.DistributionSetTagTableEvent;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;

/**
 * Layout for the pop-up window which is created when deleting a distribution
 * set tag on the Deployment View.
 */
public class DeleteDistributionSetTagLayout extends UpdateDistributionSetTagLayout {

    private static final long serialVersionUID = 1L;

    private final transient DistributionSetTagManagement distributionSetTagManagement;

    private final List<String> selectedTags;

    DeleteDistributionSetTagLayout(final VaadinMessageSource i18n,
            final DistributionSetTagManagement distributionSetTagManagement, final EntityFactory entityFactory,
            final UIEventBus eventBus, final SpPermissionChecker permChecker, final UINotification uiNotification,
            final List<String> selectedTags) {
        super(i18n, distributionSetTagManagement, entityFactory, eventBus, permChecker, uiNotification);
        this.distributionSetTagManagement = distributionSetTagManagement;
        this.selectedTags = selectedTags;
    }

    @Override
    public void init() {
        super.init();
        getUpdateCombobox().getComboLabel()
                .setValue(getI18n().getMessage("label.choose.tag", getI18n().getMessage("label.choose.tag.delete")));
    }

    @Override
    protected void buildLayout() {
        super.buildLayout();
        getContentLayout().removeComponent(getColorLabelLayout());
        disableFields();
    }

    @Override
    protected void disableFields() {
        getTagDesc().setEnabled(false);
        getTagName().setEnabled(false);
    }

    // @Override
    // protected void setTagDetails(final String distTagSelected) {
    // super.setTagDetails(distTagSelected);
    // }

    @Override
    protected String getWindowCaption() {
        return getI18n().getMessage("caption.configure", getI18n().getMessage("caption.delete"),
                getI18n().getMessage("caption.tag"));
    }

    @Override
    protected void saveEntity() {
        if (canBeDeleted()) {
            deleteDistributionTag();
        }
    }

    @Override
    protected boolean isUpdateAction() {
        return false;
    }

    @Override
    protected boolean isDeleteAction() {
        return true;
    }

    private void deleteDistributionTag() {
        final String tagNameToDelete = getTagName().getValue();
        final Optional<DistributionSetTag> tagToDelete = distributionSetTagManagement.getByName(tagNameToDelete);
        tagToDelete.ifPresent(tag -> {
            if (selectedTags.contains(tagNameToDelete)) {
                getUiNotification().displayValidationError(getI18n().getMessage("message.tag.delete", tagNameToDelete));
            } else {
                distributionSetTagManagement.delete(tagNameToDelete);
                getEventBus().publish(this, new DistributionSetTagTableEvent(BaseEntityEventType.REMOVE_ENTITY, tag));
                getUiNotification().displaySuccess(getI18n().getMessage("message.delete.success", getTagName()));
                selectedTags.remove(tagNameToDelete);
            }
        });
    }

    private boolean canBeDeleted() {
        if (!getPermChecker().hasDeleteRepositoryPermission()) {
            getUiNotification().displayValidationError(
                    getI18n().getMessage("message.permission.insufficient", SpPermission.DELETE_REPOSITORY));
            return false;
        }
        return true;
    }

}
