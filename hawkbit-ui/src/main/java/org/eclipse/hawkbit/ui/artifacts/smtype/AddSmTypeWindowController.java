/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.artifacts.smtype;

import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.repository.model.SoftwareModuleType;
import org.eclipse.hawkbit.ui.common.AbstractAddEntityWindowController;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.EntityWindowLayout;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySoftwareModule;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyType;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyType.SmTypeAssign;
import org.springframework.util.StringUtils;

/**
 * Controller for Add software module type window
 */
public class AddSmTypeWindowController
        extends AbstractAddEntityWindowController<ProxyType, ProxyType, SoftwareModuleType> {

    private final SoftwareModuleTypeManagement smTypeManagement;
    private final SmTypeWindowLayout layout;

    /**
     * Constructor for AddSmTypeWindowController
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param smTypeManagement
     *            SoftwareModuleTypeManagement
     * @param layout
     *            SmTypeWindowLayout
     */
    public AddSmTypeWindowController(final CommonUiDependencies uiDependencies,
            final SoftwareModuleTypeManagement smTypeManagement, final SmTypeWindowLayout layout) {
        super(uiDependencies);

        this.smTypeManagement = smTypeManagement;
        this.layout = layout;
    }

    @Override
    public EntityWindowLayout<ProxyType> getLayout() {
        return layout;
    }

    @Override
    protected ProxyType buildEntityFromProxy(final ProxyType proxyEntity) {
        // We ignore the method parameter, because we are interested in the
        // empty object, that we can populate with defaults
        return new ProxyType();
    }

    @Override
    protected SoftwareModuleType persistEntityInRepository(final ProxyType entity) {
        final int assignNumber = entity.getSmTypeAssign() == SmTypeAssign.SINGLE ? 1 : Integer.MAX_VALUE;

        return smTypeManagement
                .create(getEntityFactory().softwareModuleType().create().key(entity.getKey()).name(entity.getName())
                        .description(entity.getDescription()).colour(entity.getColour()).maxAssignments(assignNumber));
    }

    @Override
    protected String getDisplayableName(final ProxyType entity) {
        return entity.getName();
    }

    @Override
    protected Long getId(final SoftwareModuleType entity) {
        return entity.getId();
    }

    @Override
    protected Class<? extends ProxyIdentifiableEntity> getEntityClass() {
        return ProxyType.class;
    }

    @Override
    protected Class<? extends ProxyIdentifiableEntity> getParentEntityClass() {
        return ProxySoftwareModule.class;
    }

    @Override
    protected String getDisplayableEntityTypeMessageKey() {
        return "caption.entity.software.module.type";
    }

    @Override
    protected boolean isEntityValid(final ProxyType entity) {
        if (!StringUtils.hasText(entity.getName()) || !StringUtils.hasText(entity.getKey())
                || entity.getSmTypeAssign() == null) {
            displayValidationError("message.error.missing.typenameorkeyorsmtype");
            return false;
        }

        final String trimmedName = StringUtils.trimWhitespace(entity.getName());
        final String trimmedKey = StringUtils.trimWhitespace(entity.getKey());
        if (smTypeManagement.getByName(trimmedName).isPresent()) {
            displayValidationError("message.type.duplicate.check", trimmedName);
            return false;
        }
        if (smTypeManagement.getByKey(trimmedKey).isPresent()) {
            displayValidationError("message.type.key.swmodule.duplicate.check", trimmedKey);
            return false;
        }

        return true;
    }
}
