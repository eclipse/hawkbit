/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.management;

import static org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey.MULTI_ASSIGNMENTS_WEIGHT_DEFAULT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.MaintenanceScheduleHelper;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.exception.InvalidMaintenanceScheduleException;
import org.eclipse.hawkbit.repository.exception.MultiAssignmentIsNotEnabledException;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.DeploymentRequest;
import org.eclipse.hawkbit.repository.model.DeploymentRequestBuilder;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.DistributionSetAssignmentResult;
import org.eclipse.hawkbit.repository.model.RepositoryModelConstants;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.confirmwindow.layout.ConfirmationTab;
import org.eclipse.hawkbit.ui.common.entity.TargetIdName;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.management.event.PinUnpinEvent;
import org.eclipse.hawkbit.ui.management.event.SaveActionWindowEvent;
import org.eclipse.hawkbit.ui.management.miscs.AbstractActionTypeOptionGroupLayout;
import org.eclipse.hawkbit.ui.management.miscs.AbstractActionTypeOptionGroupLayout.ActionTypeOption;
import org.eclipse.hawkbit.ui.management.miscs.ActionTypeOptionGroupAssignmentLayout;
import org.eclipse.hawkbit.ui.management.miscs.MaintenanceWindowLayout;
import org.eclipse.hawkbit.ui.management.miscs.WeightLayout;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.data.Property;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Link;
import com.vaadin.ui.TextField;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Helper Class for Target Assignment Operations from the Deployment View
 */
public final class TargetAssignmentOperations {

    private static final Logger LOG = LoggerFactory.getLogger(TargetAssignmentOperations.class);

    private TargetAssignmentOperations() {
    }

    /**
     * Save the given distribution set assignments
     * 
     * @param targets
     *            to assign the given distribution sets to
     * @param distributionSets
     *            to assign to the given targets
     * @param managementUIState
     *            the management UI state
     * @param actionTypeOptionGroupLayout
     *            the action Type Option Group Layout
     * @param maintenanceWindowLayout
     *            the Maintenance Window Layout
     * @param deploymentManagement
     *            the Deployment Management
     * @param notification
     *            the UI Notification
     * @param eventBus
     *            the UI Event Bus
     * @param i18n
     *            the Vaadin Message Source for multi language
     * @param eventSource
     *            the source object for sending potential events
     * @throws MultiAssignmentIsNotEnabledException
     */
    public static void saveAllAssignments(final List<Target> targets, final List<DistributionSet> distributionSets,
            final ManagementUIState managementUIState,
            final ActionTypeOptionGroupAssignmentLayout actionTypeOptionGroupLayout,
            final MaintenanceWindowLayout maintenanceWindowLayout, final DeploymentManagement deploymentManagement,
            final UINotification notification, final UIEventBus eventBus, final VaadinMessageSource i18n,
            final Object eventSource, final TenantConfigurationManagement configManagement,
            final SystemSecurityContext systemSecurityContext, final TextField weightField) {

        final ActionType actionType = ((ActionTypeOption) actionTypeOptionGroupLayout.getActionTypeOptionGroup()
                .getValue()).getActionType();

        final long forcedTimeStamp = (((ActionTypeOption) actionTypeOptionGroupLayout.getActionTypeOptionGroup()
                .getValue()) == ActionTypeOption.AUTO_FORCED)
                        ? actionTypeOptionGroupLayout.getForcedTimeDateField().getValue().getTime()
                        : RepositoryModelConstants.NO_FORCE_TIME;

        final String maintenanceSchedule = maintenanceWindowLayout.getMaintenanceSchedule();
        final String maintenanceDuration = maintenanceWindowLayout.getMaintenanceDuration();
        final String maintenanceTimeZone = maintenanceWindowLayout.getMaintenanceTimeZone();

        final Set<Long> dsIds = distributionSets.stream().map(DistributionSet::getId).collect(Collectors.toSet());
               final List<DeploymentRequest> deploymentRequests = new ArrayList<>();
        dsIds.forEach(dsId -> targets.forEach(t -> {
            final DeploymentRequestBuilder request = DeploymentManagement.deploymentRequest(t.getControllerId(), dsId)
                    .setActionType(actionType).setForceTime(forcedTimeStamp);
            if (isMultiAssignmentsEnabled(configManagement, systemSecurityContext)) {
                request.setWeight(Integer.valueOf(weightField.getValue().replace(",", "")));
            }
            if (maintenanceWindowLayout.isEnabled()) {
                request.setMaintenance(maintenanceSchedule, maintenanceDuration, maintenanceTimeZone);
            }
            deploymentRequests.add(request.build());
        }));

        try {
            final List<DistributionSetAssignmentResult> results = deploymentManagement
                    .assignDistributionSets(deploymentRequests);
            // use the last one for the notification box
            final DistributionSetAssignmentResult assignmentResult = results.get(results.size() - 1);
            if (assignmentResult.getAssigned() > 0) {
                notification
                        .displaySuccess(i18n.getMessage("message.target.assignment", assignmentResult.getAssigned()));
            }
            if (assignmentResult.getAlreadyAssigned() > 0) {
                notification.displaySuccess(
                        i18n.getMessage("message.target.alreadyAssigned", assignmentResult.getAlreadyAssigned()));
            }

            final Set<Long> targetIds = targets.stream().map(Target::getId).collect(Collectors.toSet());
            refreshPinnedDetails(dsIds, targetIds, managementUIState, eventBus, eventSource);

            notification.displaySuccess(i18n.getMessage("message.target.ds.assign.success"));
            eventBus.publish(eventSource, SaveActionWindowEvent.SAVED_ASSIGNMENTS);
        } catch (final MultiAssignmentIsNotEnabledException e) {
            notification.displayValidationError(i18n.getMessage("message.target.ds.multiassign.error"));
            LOG.error("UI allowed multiassignment although it is not enabled: {}", e);
        }
    }

    private static void refreshPinnedDetails(final Set<Long> dsIds, final Set<Long> targetIds,
            final ManagementUIState managementUIState, final UIEventBus eventBus, final Object eventSource) {
        final Optional<Long> pinnedDist = managementUIState.getTargetTableFilters().getPinnedDistId();
        final Optional<TargetIdName> pinnedTarget = managementUIState.getDistributionTableFilters().getPinnedTarget();

        if (pinnedDist.isPresent()) {
            if (dsIds.contains(pinnedDist.get())) {
                eventBus.publish(eventSource, PinUnpinEvent.PIN_DISTRIBUTION);
            }
        } else if (pinnedTarget.isPresent() && targetIds.contains(pinnedTarget.get().getTargetId())) {
            eventBus.publish(eventSource, PinUnpinEvent.PIN_TARGET);
        }
    }

    /**
     * Check wether the maintenance window is valid or not
     * 
     * @param maintenanceWindowLayout
     *            the maintenance window layout
     * @param notification
     *            the UI Notification
     * @return boolean if maintenance window is valid or not
     */
    public static boolean isMaintenanceWindowValid(final MaintenanceWindowLayout maintenanceWindowLayout,
            final UINotification notification) {
        if (maintenanceWindowLayout.isEnabled()) {
            try {
                MaintenanceScheduleHelper.validateMaintenanceSchedule(maintenanceWindowLayout.getMaintenanceSchedule(),
                        maintenanceWindowLayout.getMaintenanceDuration(),
                        maintenanceWindowLayout.getMaintenanceTimeZone());
            } catch (final InvalidMaintenanceScheduleException e) {
                LOG.error("Maintenance window is not valid", e);
                notification.displayValidationError(e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Create the Assignment Confirmation Tab
     *
     * @param actionTypeOptionGroupLayout
     *            the action Type Option Group Layout
     * @param maintenanceWindowLayout
     *            the Maintenance Window Layout
     * @param saveButtonToggle
     *            The event listener to determine if save button should be
     *            enabled or not
     * @param i18n
     *            the Vaadin Message Source for multi language
     * @param uiProperties
     *            the UI Properties
     * @return the Assignment Confirmation tab
     */
    public static ConfirmationTab createAssignmentTab(
            final ActionTypeOptionGroupAssignmentLayout actionTypeOptionGroupLayout,
            final MaintenanceWindowLayout maintenanceWindowLayout, final Consumer<Boolean> saveButtonToggle,
            final VaadinMessageSource i18n, final UiProperties uiProperties, final boolean isMultiAssigmentsEnabled,
            final WeightLayout weightLayout, final TenantConfigurationManagement configManagement) {

        final CheckBox maintenanceWindowControl = maintenanceWindowControl(i18n, maintenanceWindowLayout,
                saveButtonToggle, isMultiAssigmentsEnabled, weightLayout);
        final Link maintenanceWindowHelpLink = maintenanceWindowHelpLinkControl(uiProperties, i18n);
        final HorizontalLayout layout = createHorizontalLayout(maintenanceWindowControl, maintenanceWindowHelpLink);
        actionTypeOptionGroupLayout.selectDefaultOption();

        initMaintenanceWindow(maintenanceWindowLayout, saveButtonToggle, isMultiAssigmentsEnabled,
                weightLayout.getWeightField());
        addValueChangeListener(actionTypeOptionGroupLayout, maintenanceWindowControl, maintenanceWindowHelpLink);
        return createAssignmentTab(actionTypeOptionGroupLayout, layout, maintenanceWindowLayout,
                isMultiAssigmentsEnabled, weightLayout, configManagement);
    }

    private static HorizontalLayout createHorizontalLayout(final CheckBox maintenanceWindowControl,
            final Link maintenanceWindowHelpLink) {
        final HorizontalLayout layout = new HorizontalLayout();
        layout.addComponent(maintenanceWindowControl);
        layout.addComponent(maintenanceWindowHelpLink);
        return layout;
    }

    private static ConfirmationTab createAssignmentTab(
            final ActionTypeOptionGroupAssignmentLayout actionTypeOptionGroupLayout, final HorizontalLayout layout,
            final MaintenanceWindowLayout maintenanceWindowLayout, final boolean isMultiAssigmentsEnabled,
            final WeightLayout weightLayout, final TenantConfigurationManagement configManagement) {
        final ConfirmationTab assignmentTab = new ConfirmationTab();
        if (isMultiAssigmentsEnabled) {
            weightLayout.getWeightField().clear();
            weightLayout.getWeightField().setValue(String.valueOf(configManagement
                    .getConfigurationValue(MULTI_ASSIGNMENTS_WEIGHT_DEFAULT, Integer.class).getValue()));
            assignmentTab.addComponent(weightLayout);
        }
        assignmentTab.addComponent(actionTypeOptionGroupLayout);
        assignmentTab.addComponent(layout);
        assignmentTab.addComponent(maintenanceWindowLayout);
        return assignmentTab;
    }

    private static void initMaintenanceWindow(final MaintenanceWindowLayout maintenanceWindowLayout,
            final Consumer<Boolean> saveButtonToggle, final boolean isMultiAssigmentsEnabled,
            final TextField weightField) {
        maintenanceWindowLayout.setVisible(false);
        maintenanceWindowLayout.setEnabled(false);
        maintenanceWindowLayout.getScheduleControl().addTextChangeListener(event -> saveButtonToggle.accept(
                saveButtonOnScheduleChange(event, maintenanceWindowLayout, isMultiAssigmentsEnabled, weightField)));
        maintenanceWindowLayout.getDurationControl().addTextChangeListener(event -> saveButtonToggle.accept(
                saveButtonOnDurationChange(event, maintenanceWindowLayout, isMultiAssigmentsEnabled, weightField)));
    }

    private static boolean saveButtonOnScheduleChange(final TextChangeEvent event,
            final MaintenanceWindowLayout maintenanceWindowLayout, final boolean isMultiAssigmentsEnabled,
            final TextField weightField) {
        return validateSaveButtonToggle(maintenanceWindowLayout.onScheduleChange(event), isMultiAssigmentsEnabled,
                weightField);
    }

    private static boolean saveButtonOnDurationChange(final TextChangeEvent event,
            final MaintenanceWindowLayout maintenanceWindowLayout, final boolean isMultiAssigmentsEnabled,
            final TextField weightField) {
        return validateSaveButtonToggle(maintenanceWindowLayout.onDurationChange(event), isMultiAssigmentsEnabled,
                weightField);
    }

    private static boolean validateSaveButtonToggle(final boolean hasValidDurationOrScheduleChange,
            final boolean isMultiAssigmentsEnabled, final TextField weightField) {
        if (hasValidDurationOrScheduleChange && isMultiAssigmentsEnabled) {
            if (weightField.getValue() != null && (Integer.valueOf(weightField.getValue().replace(",", "")) >= 0
                    && Integer.valueOf(weightField.getValue().replace(",", "")) <= 1000)) {
                return isMultiAssigmentsEnabled;
            } else {
                return !isMultiAssigmentsEnabled;
            }
        }
        return hasValidDurationOrScheduleChange;
    }

    private static CheckBox maintenanceWindowControl(final VaadinMessageSource i18n,
            final MaintenanceWindowLayout maintenanceWindowLayout, final Consumer<Boolean> saveButtonToggle,
            final boolean isMultiAssigmentsEnabled, final WeightLayout weightLayout) {
        final CheckBox enableMaintenanceWindow = new CheckBox(i18n.getMessage("caption.maintenancewindow.enabled"));
        enableMaintenanceWindow.setId(UIComponentIdProvider.MAINTENANCE_WINDOW_ENABLED_ID);
        enableMaintenanceWindow.addStyleName(ValoTheme.CHECKBOX_SMALL);
        enableMaintenanceWindow.addStyleName("dist-window-maintenance-window-enable");
        enableMaintenanceWindow.addValueChangeListener(event -> {
            final Boolean isMaintenanceWindowEnabled = enableMaintenanceWindow.getValue();
            maintenanceWindowLayout.setVisible(isMaintenanceWindowEnabled);
            maintenanceWindowLayout.setEnabled(isMaintenanceWindowEnabled);
            if (!isMaintenanceWindowEnabled && isMultiAssigmentsEnabled) {
                final String weightValue = weightLayout.getWeightField().getValue();
                if (weightValue != null && weightLayout.checkWeightValue(weightValue)) {
                    saveButtonToggle.accept(isMultiAssigmentsEnabled);
                } else {
                    saveButtonToggle.accept(!isMultiAssigmentsEnabled);
                }
            } else {
                saveButtonToggle.accept(!isMaintenanceWindowEnabled);
            }
            maintenanceWindowLayout.clearAllControls();
        });
        return enableMaintenanceWindow;
    }

    private static void addValueChangeListener(final ActionTypeOptionGroupAssignmentLayout actionTypeOptionGroupLayout,
            final CheckBox enableMaintenanceWindowControl, final Link maintenanceWindowHelpLinkControl) {
        actionTypeOptionGroupLayout.getActionTypeOptionGroup()
                .addValueChangeListener(new Property.ValueChangeListener() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    // Vaadin is returning object so "==" might not work
                    @SuppressWarnings("squid:S4551")
                    public void valueChange(final Property.ValueChangeEvent event) {

                        if (event.getProperty().getValue()
                                .equals(AbstractActionTypeOptionGroupLayout.ActionTypeOption.DOWNLOAD_ONLY)) {
                            enableMaintenanceWindowControl.setValue(false);
                            enableMaintenanceWindowControl.setEnabled(false);
                            maintenanceWindowHelpLinkControl.setEnabled(false);

                        } else {
                            enableMaintenanceWindowControl.setEnabled(true);
                            maintenanceWindowHelpLinkControl.setEnabled(true);
                        }

                    }
                });
    }

    private static Link maintenanceWindowHelpLinkControl(final UiProperties uiProperties,
            final VaadinMessageSource i18n) {
        final String maintenanceWindowHelpUrl = uiProperties.getLinks().getDocumentation().getMaintenanceWindowView();
        return SPUIComponentProvider.getHelpLink(i18n, maintenanceWindowHelpUrl);
    }

    private static boolean isMultiAssignmentsEnabled(final TenantConfigurationManagement configManagement,
            final SystemSecurityContext systemSecurityContext) {
        return systemSecurityContext.runAsSystem(() -> configManagement
                .getConfigurationValue(TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED, Boolean.class).getValue());
    }
}
