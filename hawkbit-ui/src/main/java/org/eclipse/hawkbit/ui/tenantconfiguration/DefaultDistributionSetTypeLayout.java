/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.tenantconfiguration;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.exception.InsufficientPermissionException;
import org.eclipse.hawkbit.repository.model.DistributionSetType;
import org.eclipse.hawkbit.repository.model.TenantMetaData;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.utils.I18N;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.VerticalLayout;

/**
 * Default DistributionSet Panel.
 */
public class DefaultDistributionSetTypeLayout extends BaseConfigurationView {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDistributionSetTypeLayout.class);

    private final transient SystemManagement systemManagement;

    private Long currentDefaultDisSetType;

    private Long selectedDefaultDisSetType;

    private TenantMetaData tenantMetaData;

    final ComboBox combobox;

    final Label changeIcon;

    DefaultDistributionSetTypeLayout(final SystemManagement systemManagement,
            final DistributionSetManagement distributionSetManagement, final I18N i18n) {
        this.systemManagement = systemManagement;
        combobox = SPUIComponentProvider.getComboBox(null, "330", null, null, false, "", "label.combobox.tag");
        changeIcon = new Label();
        Iterable<DistributionSetType> distributionSetTypeCollection = null;
        final Pageable pageReq = new PageRequest(0, 100);
        try {
            distributionSetTypeCollection = distributionSetManagement.findDistributionSetTypesAll(pageReq);
        } catch (final InsufficientPermissionException ex) {
            LOGGER.warn("Logged-in user does not have any REPOSITORY permission.", ex);
            return;
        }

        final Panel rootPanel = new Panel();
        rootPanel.setSizeFull();
        rootPanel.addStyleName("config-panel");
        final VerticalLayout vlayout = new VerticalLayout();
        vlayout.setMargin(true);
        vlayout.setSizeFull();
        final String disSetTypeTitle = i18n.get("configuration.defaultdistributionset.title");

        final Label headerDisSetType = new Label(disSetTypeTitle);
        headerDisSetType.addStyleName("config-panel-header");
        vlayout.addComponent(headerDisSetType);
        final DistributionSetType currentDistributionSetType = getCurrentDistributionSetType();
        currentDefaultDisSetType = currentDistributionSetType.getId();

        final HorizontalLayout hlayout = new HorizontalLayout();
        hlayout.setSpacing(true);
        hlayout.setStyleName("config-h-panel");

        final Label configurationLabel = new Label(i18n.get("configuration.defaultdistributionset.select.label"));
        hlayout.addComponent(configurationLabel);
        hlayout.setComponentAlignment(configurationLabel, Alignment.MIDDLE_LEFT);

        combobox.setId(UIComponentIdProvider.SYSTEM_CONFIGURATION_DEFAULTDIS_COMBOBOX);
        combobox.setNullSelectionAllowed(false);
        for (final DistributionSetType distributionSetType : distributionSetTypeCollection) {
            combobox.addItem(distributionSetType.getId());
            combobox.setItemCaption(distributionSetType.getId(),
                    distributionSetType.getKey() + " (" + distributionSetType.getName() + ")");

            if (distributionSetType.getId().equals(currentDistributionSetType.getId())) {
                combobox.select(distributionSetType.getId());
            }
        }
        combobox.setImmediate(true);
        combobox.addValueChangeListener(event -> selectDistributionSetValue());
        hlayout.addComponent(combobox);

        changeIcon.setIcon(FontAwesome.CHECK);
        hlayout.addComponent(changeIcon);
        changeIcon.setVisible(false);

        vlayout.addComponent(hlayout);
        rootPanel.setContent(vlayout);
        setCompositionRoot(rootPanel);
    }

    private DistributionSetType getCurrentDistributionSetType() {
        tenantMetaData = systemManagement.getTenantMetadata();
        return tenantMetaData.getDefaultDsType();
    }

    @Override
    public void save() {
        if (!currentDefaultDisSetType.equals(selectedDefaultDisSetType) && selectedDefaultDisSetType != null) {
            tenantMetaData = systemManagement.updateTenantMetadata(selectedDefaultDisSetType);
            currentDefaultDisSetType = selectedDefaultDisSetType;
        }
        changeIcon.setVisible(false);
    }

    @Override
    public void undo() {
        combobox.select(currentDefaultDisSetType);
        selectedDefaultDisSetType = currentDefaultDisSetType;
        changeIcon.setVisible(false);
    }

    /**
     * Method that is called when combobox event is performed.
     */
    public void selectDistributionSetValue() {
        selectedDefaultDisSetType = (Long) combobox.getValue();
        if (!selectedDefaultDisSetType.equals(currentDefaultDisSetType)) {
            changeIcon.setVisible(true);
            notifyConfigurationChanged();
        } else {
            changeIcon.setVisible(false);
        }
    }

}
