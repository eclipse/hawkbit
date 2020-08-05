/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management.targettable;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.detailslayout.AbstractTableDetailsLayout;
import org.eclipse.hawkbit.ui.common.detailslayout.TargetMetadataDetailsLayout;
import org.eclipse.hawkbit.ui.common.tagdetails.TargetTagToken;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.management.event.TargetTableEvent;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.server.FontAwesome;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Target details layout which is shown on the Deployment View.
 */
public class TargetDetails extends AbstractTableDetailsLayout<Target> {

    private static final long serialVersionUID = 1L;

    private final TargetTagToken targetTagToken;

    private final TargetMetadataDetailsLayout targetMetadataTable;

    private final TargetAddUpdateWindowLayout targetAddUpdateWindowLayout;

    private final transient TargetManagement targetManagement;

    private final TargetMetadataPopupLayout targetMetadataPopupLayout;

    private final UINotification uiNotification;

    private final transient DeploymentManagement deploymentManagement;

    private VerticalLayout assignedDistLayout;

    private VerticalLayout installedDistLayout;

    private final boolean honoSyncEnabled;

    TargetDetails(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final SpPermissionChecker permissionChecker, final ManagementUIState managementUIState,
            final UINotification uiNotification, final TargetTagManagement tagManagement,
            final TargetManagement targetManagement, final TargetMetadataPopupLayout targetMetadataPopupLayout,
            final DeploymentManagement deploymentManagement, final EntityFactory entityFactory,
            final TargetTable targetTable, final boolean honoSyncEnabled) {
        super(i18n, eventBus, permissionChecker, managementUIState);
        this.honoSyncEnabled = honoSyncEnabled;
        this.targetTagToken = new TargetTagToken(permissionChecker, i18n, uiNotification, eventBus, managementUIState,
                tagManagement, targetManagement);
        this.targetAddUpdateWindowLayout = new TargetAddUpdateWindowLayout(i18n, targetManagement, eventBus,
                uiNotification, entityFactory, targetTable);
        this.uiNotification = uiNotification;
        this.targetManagement = targetManagement;
        this.deploymentManagement = deploymentManagement;
        this.targetMetadataPopupLayout = targetMetadataPopupLayout;
        this.targetMetadataTable = new TargetMetadataDetailsLayout(i18n, targetManagement, targetMetadataPopupLayout);
        addDetailsTab();
        restoreState();
    }

    @Override
    protected String getDefaultCaption() {
        return getI18n().getMessage("target.details.header");
    }

    private final void addDetailsTab() {
        getDetailsTab().addTab(getDetailsLayout(), getI18n().getMessage("caption.tab.details"), null);
        getDetailsTab().addTab(getDescriptionLayout(), getI18n().getMessage("caption.tab.description"), null);
        getDetailsTab().addTab(getAttributesLayout(), getI18n().getMessage("caption.attributes.tab"), null);
        getDetailsTab().addTab(createAssignedDistLayout(), getI18n().getMessage("header.target.assigned"), null);
        getDetailsTab().addTab(createInstalledDistLayout(), getI18n().getMessage("header.target.installed"), null);
        getDetailsTab().addTab(getTagsLayout(), getI18n().getMessage("caption.tags.tab"), null);
        getDetailsTab().addTab(getLogLayout(), getI18n().getMessage("caption.logs.tab"), null);
        getDetailsTab().addTab(targetMetadataTable, getI18n().getMessage("caption.metadata"), null);
    }

    private Component createInstalledDistLayout() {
        installedDistLayout = createTabLayout();
        return installedDistLayout;
    }

    private Component createAssignedDistLayout() {
        assignedDistLayout = createTabLayout();
        return assignedDistLayout;
    }

    @Override
    protected void onEdit(final ClickEvent event) {
        if (getSelectedBaseEntity() == null) {
            return;
        }
        openWindow();
    }

    private void openWindow() {
        final Window targetWindow = targetAddUpdateWindowLayout.getWindow(getSelectedBaseEntity().getControllerId());
        if (targetWindow == null) {
            return;
        }
        targetWindow.setCaption(getI18n().getMessage("caption.update", getI18n().getMessage("caption.target")));
        UI.getCurrent().addWindow(targetWindow);
        targetWindow.setVisible(Boolean.TRUE);
    }

    @Override
    protected String getEditButtonId() {
        return UIComponentIdProvider.TARGET_EDIT_ICON;
    }

    @Override
    protected boolean onLoadIsTableMaximized() {
        return getManagementUIState().isTargetTableMaximized();
    }

    @Override
    protected void populateDetailsWidget() {
        if (getSelectedBaseEntity() != null) {
            final String controllerId = getSelectedBaseEntity().getControllerId();

            updateAttributesLayout(controllerId);

            updateDetailsLayout(controllerId, getSelectedBaseEntity().getAddress(),
                    getSelectedBaseEntity().getSecurityToken(),
                    SPDateTimeUtil.getFormattedDate(getSelectedBaseEntity().getLastTargetQuery()));

            populateDistributionDtls(assignedDistLayout,
                    deploymentManagement.getAssignedDistributionSet(controllerId).orElse(null));
            populateDistributionDtls(installedDistLayout,
                    deploymentManagement.getInstalledDistributionSet(controllerId).orElse(null));
        } else {
            updateAttributesLayout(null);
            updateDetailsLayout(null, null, null, null);
            populateDistributionDtls(installedDistLayout, null);
            populateDistributionDtls(assignedDistLayout, null);
        }
        populateTags(targetTagToken);
        populateMetadataDetails();
    }

    private void updateDetailsLayout(final String controllerId, final URI address, final String securityToken,
            final String lastQueryDate) {
        final VerticalLayout detailsTabLayout = getDetailsLayout();
        detailsTabLayout.removeAllComponents();

        final Label controllerLabel = SPUIComponentProvider.createNameValueLabel(
                getI18n().getMessage("label.target.id"), controllerId == null ? "" : controllerId);
        controllerLabel.setId(UIComponentIdProvider.TARGET_CONTROLLER_ID);
        detailsTabLayout.addComponent(controllerLabel);

        final Label lastPollDtLabel = SPUIComponentProvider.createNameValueLabel(
                getI18n().getMessage("label.target.lastpolldate"), lastQueryDate == null ? "" : lastQueryDate);
        lastPollDtLabel.setId(UIComponentIdProvider.TARGET_LAST_QUERY_DT);
        detailsTabLayout.addComponent(lastPollDtLabel);

        final Label typeLabel = SPUIComponentProvider.createNameValueLabel(getI18n().getMessage("label.ip"),
                address == null ? "" : address.toString());
        typeLabel.setId(UIComponentIdProvider.TARGET_IP_ADDRESS);
        detailsTabLayout.addComponent(typeLabel);

        if (!honoSyncEnabled) {
            final HorizontalLayout securityTokenLayout = getSecurityTokenLayout(securityToken);
            controllerLabel.setId(UIComponentIdProvider.TARGET_SECURITY_TOKEN);
            detailsTabLayout.addComponent(securityTokenLayout);
        }
    }

    private HorizontalLayout getSecurityTokenLayout(final String securityToken) {
        final HorizontalLayout securityTokenLayout = new HorizontalLayout();

        final Label securityTableLbl = new Label(
                SPUIComponentProvider.getBoldHTMLText(getI18n().getMessage("label.target.security.token")),
                ContentMode.HTML);
        securityTableLbl.addStyleName(SPUIDefinitions.TEXT_STYLE);
        securityTableLbl.addStyleName("label-style");

        final TextField securityTokentxt = new TextField();
        securityTokentxt.addStyleName(ValoTheme.TEXTFIELD_BORDERLESS);
        securityTokentxt.addStyleName(ValoTheme.TEXTFIELD_TINY);
        securityTokentxt.addStyleName("targetDtls-securityToken");
        securityTokentxt.addStyleName(SPUIDefinitions.TEXT_STYLE);
        securityTokentxt.setCaption(null);
        securityTokentxt.setNullRepresentation("");
        securityTokentxt.setValue(securityToken);
        securityTokentxt.setReadOnly(true);

        securityTokenLayout.addComponent(securityTableLbl);
        securityTokenLayout.addComponent(securityTokentxt);
        return securityTokenLayout;
    }

    private void populateDistributionDtls(final VerticalLayout layout, final DistributionSet distributionSet) {
        layout.removeAllComponents();
        layout.addComponent(SPUIComponentProvider.createNameValueLayout(getI18n().getMessage("label.dist.details.name"),
                distributionSet == null ? "" : distributionSet.getName()));

        layout.addComponent(
                SPUIComponentProvider.createNameValueLayout(getI18n().getMessage("label.dist.details.version"),
                        distributionSet == null ? "" : distributionSet.getVersion()));

        if (distributionSet == null) {
            return;
        }
        distributionSet.getModules()
                .forEach(module -> layout.addComponent(getSWModLayout(module.getType().getName(), module)));
    }

    private void updateAttributesLayout(final String controllerId) {
        final VerticalLayout attributesWrapperLayout = getAttributesLayout();
        attributesWrapperLayout.removeAllComponents();

        if (controllerId == null) {
            return;
        }

        final Map<String, String> attributes = targetManagement.getControllerAttributes(controllerId);

        final HorizontalLayout attributesRequestLayout = new HorizontalLayout();
        attributesRequestLayout.setSizeFull();

        final VerticalLayout attributesLayout = new VerticalLayout();
        updateAttributesLabelsList(attributesLayout, attributes);
        updateAttributesUpdateComponents(attributesRequestLayout, attributesLayout, controllerId);

        attributesWrapperLayout.addComponent(attributesRequestLayout);
    }

    private void updateAttributesLabelsList(final VerticalLayout attributesLayout,
            final Map<String, String> attributes) {
        final TreeMap<String, String> sortedAttributes = new TreeMap<>((key1, key2) -> key1.compareToIgnoreCase(key2));
        sortedAttributes.putAll(attributes);
        sortedAttributes.forEach((key, value) -> {
            final HorizontalLayout conAttributeLayout = SPUIComponentProvider.createNameValueLayout(key.concat("  :  "),
                    value == null ? "" : value);
            //After Vaadin 8 migration: Enable tooltip again, currently it is set to [null] to avoid cross site scripting.
            conAttributeLayout.setDescription(null);
            conAttributeLayout.addStyleName("label-style");
            attributesLayout.addComponent(conAttributeLayout);
        });
    }

    private void updateAttributesUpdateComponents(final HorizontalLayout attributesRequestLayout,
            final VerticalLayout attributesLayout, final String controllerId) {
        final boolean isRequestAttributes = targetManagement.isControllerAttributesRequested(controllerId);

        if (isRequestAttributes) {
            attributesLayout.addComponent(buildAttributesUpdateLabel(), 0);
        }

        attributesRequestLayout.addComponent(attributesLayout);
        attributesRequestLayout.setExpandRatio(attributesLayout, 1.0F);
        attributesRequestLayout.addComponent(buildRequestAttributesUpdateButton(controllerId, isRequestAttributes));
    }

    private Label buildAttributesUpdateLabel() {
        final Label attributesUpdateLabel = new Label();
        attributesUpdateLabel.setStyleName(ValoTheme.LABEL_SMALL);
        attributesUpdateLabel.setValue(getI18n().getMessage("label.target.attributes.update.pending"));

        return attributesUpdateLabel;
    }

    private Button buildRequestAttributesUpdateButton(final String controllerId, final boolean isRequestAttributes) {
        final Button requestAttributesUpdateButton = SPUIComponentProvider.getButton(
                UIComponentIdProvider.TARGET_ATTRIBUTES_UPDATE, "", "", "", false, FontAwesome.REFRESH,
                SPUIButtonStyleNoBorder.class);

        requestAttributesUpdateButton.addClickListener(e -> targetManagement.requestControllerAttributes(controllerId));

        if (isRequestAttributes) {
            requestAttributesUpdateButton
                    .setDescription(getI18n().getMessage("tooltip.target.attributes.update.requested"));
            requestAttributesUpdateButton.setEnabled(false);
        } else {
            requestAttributesUpdateButton
                    .setDescription(getI18n().getMessage("tooltip.target.attributes.update.request"));
            requestAttributesUpdateButton.setEnabled(true);
        }

        return requestAttributesUpdateButton;
    }

    /**
     * Create Label for SW Module.
     * 
     * @param labelName
     *            as Name
     * @param swModule
     *            as Module (JVM|OS|AH)
     * @return Label as UI
     */
    private static HorizontalLayout getSWModLayout(final String labelName, final SoftwareModule swModule) {
        return SPUIComponentProvider.createNameValueLayout(labelName + " : ", swModule.getName(),
                swModule.getVersion());
    }

    @Override
    protected boolean hasEditPermission() {
        return getPermissionChecker().hasUpdateTargetPermission();
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final TargetTableEvent targetTableEvent) {
        onBaseEntityEvent(targetTableEvent);
    }

    @Override
    protected String getTabSheetId() {
        return UIComponentIdProvider.TARGET_DETAILS_TABSHEET;
    }

    @Override
    protected String getDetailsHeaderCaptionId() {
        return UIComponentIdProvider.TARGET_DETAILS_HEADER_LABEL_ID;
    }

    @Override
    protected void showMetadata(final ClickEvent event) {
        final Optional<Target> target = targetManagement.get(getSelectedBaseEntityId());
        if (!target.isPresent()) {
            uiNotification.displayWarning(getI18n().getMessage("targets.not.exists"));
            return;
        }
        UI.getCurrent().addWindow(targetMetadataPopupLayout.getWindow(target.get(), null));
    }

    @Override
    protected void populateMetadataDetails() {
        targetMetadataTable.populateMetadata(getSelectedBaseEntity());
    }

    @Override
    protected String getMetadataButtonId() {
        return UIComponentIdProvider.TARGET_METADATA_BUTTON;
    }

}
