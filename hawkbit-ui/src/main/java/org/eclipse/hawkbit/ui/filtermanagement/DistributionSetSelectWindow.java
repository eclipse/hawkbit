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

import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow;
import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.common.builder.WindowBuilder;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorderWithIcon;
import org.eclipse.hawkbit.ui.filtermanagement.event.CustomFilterUIEvent;
import org.eclipse.hawkbit.ui.management.miscs.AbstractActionTypeOptionGroupLayout.ActionTypeOption;
import org.eclipse.hawkbit.ui.management.miscs.ActionTypeOptionGroupAutoAssignmentLayout;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.data.Validator;
import com.vaadin.data.util.converter.StringToIntegerConverter;
import com.vaadin.data.validator.IntegerRangeValidator;
import com.vaadin.data.validator.NullValidator;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Sizeable;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

/**
 * Creates a dialog window to select the distribution set for a target filter
 * query.
 */
public class DistributionSetSelectWindow implements CommonDialogWindow.SaveDialogCloseListener {

    private final VaadinMessageSource i18n;
    private final UINotification notification;
    private final EventBus.UIEventBus eventBus;
    private final TargetManagement targetManagement;
    private final TargetFilterQueryManagement targetFilterQueryManagement;
    private final EntityFactory entityFactory;
    private final TenantConfigurationManagement configManagement;
    private final SystemSecurityContext systemSecurityContext;

    private CheckBox checkBox;
    private ActionTypeOptionGroupAutoAssignmentLayout actionTypeOptionGroupLayout;
    private DistributionSetSelectComboBox dsCombo;
    private Long tfqId;
    private TextField weight;

    DistributionSetSelectWindow(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final UINotification notification, final TargetManagement targetManagement,
            final TargetFilterQueryManagement targetFilterQueryManagement, final EntityFactory entityFactory,
            final TenantConfigurationManagement configManagement, final SystemSecurityContext systemSecurityContext) {
        this.i18n = i18n;
        this.notification = notification;
        this.eventBus = eventBus;
        this.targetManagement = targetManagement;
        this.targetFilterQueryManagement = targetFilterQueryManagement;
        this.entityFactory = entityFactory;
        this.configManagement = configManagement;
        this.systemSecurityContext = systemSecurityContext;
    }

    private VerticalLayout initView() {
        final Label label = new Label(i18n.getMessage(UIMessageIdProvider.LABEL_AUTO_ASSIGNMENT_DESC));

        checkBox = new CheckBox(i18n.getMessage(UIMessageIdProvider.LABEL_AUTO_ASSIGNMENT_ENABLE));
        checkBox.setId(UIComponentIdProvider.DIST_SET_SELECT_ENABLE_ID);
        checkBox.setImmediate(true);
        checkBox.addValueChangeListener(
                event -> switchAutoAssignmentInputsVisibility((boolean) event.getProperty().getValue()));

        actionTypeOptionGroupLayout = new ActionTypeOptionGroupAutoAssignmentLayout(i18n);
        dsCombo = new DistributionSetSelectComboBox(i18n);

        weight = new TextFieldBuilder(32).id(UIComponentIdProvider.DIST_SET_SELECT_WEIGHT_ID)
                .buildTextComponent();
        weight.setMaxLength(4);
        weight.addValidator(new NullValidator(null, false));
        weight.addValidator(new WeightFieldValidator());
        weight.setConverter(new StringToIntegerConverter());
        weight.setConversionError(i18n.getMessage("message.enter.number"));
        weight.setCaption(i18n.getMessage(UIMessageIdProvider.HEADER_WEIGHT));
        weight.setSizeFull();

        final VerticalLayout verticalLayout = new VerticalLayout();
        verticalLayout.addComponent(label);
        verticalLayout.addComponent(checkBox);
        if (isMultiAssignmentEnabled()) {
        verticalLayout.addComponent(weight);
        }
        verticalLayout.addComponent(actionTypeOptionGroupLayout);
        verticalLayout.addComponent(dsCombo);

        return verticalLayout;
    }

    class WeightFieldValidator implements Validator {
        private static final long serialVersionUID = 1L;
        private static final String MESSAGE_ROLLOUT_FIELD_VALUE_RANGE = "message.rollout.field.value.range";

        @Override
        public void validate(final Object value) {
            if (value != null) {
                new IntegerRangeValidator(i18n.getMessage(MESSAGE_ROLLOUT_FIELD_VALUE_RANGE, 0, 1000), 0, 1000)
                        .validate(Integer.valueOf(value.toString().replace(",", "")));
            }
        }
    }

    /**
     * Shows a distribution set select window for the given target filter query
     *
     * @param tfqId
     *            target filter query id
     */
    public void showForTargetFilter(final Long tfqId) {
        this.tfqId = tfqId;
        final TargetFilterQuery tfq = targetFilterQueryManagement.get(tfqId)
                .orElseThrow(() -> new EntityNotFoundException(TargetFilterQuery.class, tfqId));

        final VerticalLayout verticalLayout = initView();

        final DistributionSet distributionSet = tfq.getAutoAssignDistributionSet();
        final ActionType actionType = tfq.getAutoAssignActionType();
        final Optional<Integer> autoAssignWeight = tfq.getAutoAssignWeight();

        setInitialControlValues(distributionSet, actionType,
                autoAssignWeight.isPresent() ? autoAssignWeight.get() : null);

        // build window after values are set to view elements
        final CommonDialogWindow window = new WindowBuilder(SPUIDefinitions.CREATE_UPDATE_WINDOW)
                .caption(i18n.getMessage(UIMessageIdProvider.CAPTION_SELECT_AUTO_ASSIGN_DS)).content(verticalLayout)
                .layout(verticalLayout).i18n(i18n).saveDialogCloseListener(this).buildCommonDialogWindow();
        window.setId(UIComponentIdProvider.DIST_SET_SELECT_WINDOW_ID);

        window.setWidth(40.0F, Sizeable.Unit.PERCENTAGE);
        UI.getCurrent().addWindow(window);
        window.setVisible(true);
    }

    private void setInitialControlValues(final DistributionSet distributionSet, final ActionType actionType,
            final Integer autoAssignWeight) {
        checkBox.setValue(distributionSet != null);
        switchAutoAssignmentInputsVisibility(distributionSet != null);

        final ActionTypeOption actionTypeToSet = ActionTypeOption.getOptionForActionType(actionType)
                .orElse(ActionTypeOption.FORCED);
        actionTypeOptionGroupLayout.getActionTypeOptionGroup().select(actionTypeToSet);

        if (distributionSet != null) {
            final String initialFilterString = HawkbitCommonUtil.getFormattedNameVersion(distributionSet.getName(),
                    distributionSet.getVersion());
            final int filteredSize = dsCombo.setInitialValueFilter(initialFilterString);

            if (filteredSize <= 0) {
                notification.displayValidationError(
                        i18n.getMessage(UIMessageIdProvider.MESSAGE_SELECTED_DS_NOT_FOUND, initialFilterString));
                dsCombo.refreshContainer();
                dsCombo.setValue(null);
                return;
            }
        }
        dsCombo.setValue(distributionSet != null ? distributionSet.getId() : null);
        if (isMultiAssignmentEnabled()) {
            if (autoAssignWeight != null) {
                weight.setValue(String.valueOf(autoAssignWeight));
            } else {
                weight.setValue(String.valueOf(configManagement
                        .getConfigurationValue(MULTI_ASSIGNMENTS_WEIGHT_DEFAULT, Integer.class).getValue()));
            }
        }
    }

    private void switchAutoAssignmentInputsVisibility(final boolean autoAssignmentEnabled) {
        actionTypeOptionGroupLayout.setVisible(autoAssignmentEnabled);

        dsCombo.setVisible(autoAssignmentEnabled);
        dsCombo.setEnabled(autoAssignmentEnabled);
        dsCombo.setRequired(autoAssignmentEnabled);

        weight.setVisible(autoAssignmentEnabled && isMultiAssignmentEnabled());
        weight.setEnabled(autoAssignmentEnabled && isMultiAssignmentEnabled());
        weight.setRequired(autoAssignmentEnabled && isMultiAssignmentEnabled());
    }

    /**
     * Is triggered when the save button is clicked
     *
     * @return whether the click should be allowed
     */
    @Override
    public boolean canWindowSaveOrUpdate() {
        return isFormularValid();
    }

    private boolean isFormularValid() {
        return isAutoAssignmentDisabled() || isAutoAssignmentEnabledAndDistributionSetSelected();
    }

    private boolean isAutoAssignmentEnabledAndDistributionSetSelected() {
        return checkBox.getValue() && dsCombo.getValue() != null;
    }

    private boolean isAutoAssignmentDisabled() {
        return !checkBox.getValue();
    }

    /**
     * Is called when the new value should be saved after the save button has
     * been clicked
     */
    @Override
    public void saveOrUpdate() {
        if (checkBox.getValue() && dsCombo.getValue() != null) {
            final ActionType autoAssignActionType = ((ActionTypeOption) actionTypeOptionGroupLayout
                    .getActionTypeOptionGroup().getValue()).getActionType();
            // check for weight field visibility which in turn checks for auto
            // assignment and multi assignment
            final Integer autoAssignWeight = weight.isVisible() ? Integer.valueOf(weight.getValue().replace(",", ""))
                    : null;
            updateTargetFilterQueryDS(tfqId, (Long) dsCombo.getValue(), autoAssignActionType, autoAssignWeight);
        } else if (!checkBox.getValue()) {
            updateTargetFilterQueryDS(tfqId, null, null, null);
        }
    }

    private void updateTargetFilterQueryDS(final Long targetFilterQueryId, final Long dsId,
            final ActionType actionType, final Integer autoAssignWeight) {
        final TargetFilterQuery tfq = targetFilterQueryManagement.get(targetFilterQueryId)
                .orElseThrow(() -> new EntityNotFoundException(TargetFilterQuery.class, targetFilterQueryId));

        if (dsId != null) {
            confirmWithConsequencesDialog(tfq, dsId, actionType, autoAssignWeight);
        } else {
            targetFilterQueryManagement.updateAutoAssignDS(
                    entityFactory.targetFilterQuery().updateAutoAssign(targetFilterQueryId).ds(null));
            eventBus.publish(this, CustomFilterUIEvent.UPDATED_TARGET_FILTER_QUERY);
        }
    }

    private void confirmWithConsequencesDialog(final TargetFilterQuery tfq, final Long dsId,
            final ActionType actionType, final Integer autoAssignWeight) {
        final ConfirmConsequencesDialog dialog = new ConfirmConsequencesDialog(tfq, dsId, accepted -> {
            if (accepted) {
                targetFilterQueryManagement
                        .updateAutoAssignDS(entityFactory.targetFilterQuery().updateAutoAssign(tfq.getId()).ds(dsId)
                                .actionType(actionType).weight(autoAssignWeight));
                eventBus.publish(this, CustomFilterUIEvent.UPDATED_TARGET_FILTER_QUERY);
            }
        });

        dialog.setWidth(40.0F, Sizeable.Unit.PERCENTAGE);

        UI.getCurrent().addWindow(dialog);
        dialog.setVisible(true);
    }

    private boolean isMultiAssignmentEnabled() {
        return systemSecurityContext.runAsSystem(() -> configManagement
                .getConfigurationValue(TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED, Boolean.class).getValue());
    }

    /**
     * A dialog that displays how many targets will be assigned immediately with
     * the
     */
    private class ConfirmConsequencesDialog extends Window implements Button.ClickListener {
        private static final long serialVersionUID = 1L;

        private final TargetFilterQuery targetFilterQuery;
        private final Long distributionSetId;
        private final transient Consumer<Boolean> callback;

        public ConfirmConsequencesDialog(final TargetFilterQuery targetFilterQuery, final Long dsId,
                final Consumer<Boolean> callback) {
            super(i18n.getMessage(UIMessageIdProvider.CAPTION_CONFIRM_AUTO_ASSIGN_CONSEQUENCES));

            this.callback = callback;
            this.targetFilterQuery = targetFilterQuery;
            this.distributionSetId = dsId;

            init();
        }

        private void init() {
            setId(UIComponentIdProvider.DIST_SET_SELECT_CONS_WINDOW_ID);
            setModal(true);
            setResizable(false);

            final VerticalLayout layout = new VerticalLayout();
            layout.setSpacing(true);
            layout.setMargin(true);
            setContent(layout);

            final Long targetsCount = targetManagement.countByRsqlAndNonDS(distributionSetId,
                    targetFilterQuery.getQuery());
            Label mainTextLabel;
            if (targetsCount == 0) {
                mainTextLabel = new Label(
                        i18n.getMessage(UIMessageIdProvider.MESSAGE_CONFIRM_AUTO_ASSIGN_CONSEQUENCES_NONE));
            } else {
                mainTextLabel = new Label(i18n
                        .getMessage(UIMessageIdProvider.MESSAGE_CONFIRM_AUTO_ASSIGN_CONSEQUENCES_TEXT, targetsCount));
            }

            layout.addComponent(mainTextLabel);

            final HorizontalLayout buttonsLayout = new HorizontalLayout();
            buttonsLayout.setSizeFull();
            buttonsLayout.setSpacing(true);
            buttonsLayout.addStyleName("actionButtonsMargin");
            layout.addComponent(buttonsLayout);

            final Button okButton = SPUIComponentProvider.getButton(UIComponentIdProvider.SAVE_BUTTON,
                    i18n.getMessage(UIMessageIdProvider.BUTTON_OK), "", "", true, FontAwesome.SAVE,
                    SPUIButtonStyleNoBorderWithIcon.class);
            okButton.setSizeUndefined();
            okButton.addStyleName("default-color");
            okButton.addClickListener(this);
            buttonsLayout.addComponent(okButton);
            buttonsLayout.setComponentAlignment(okButton, Alignment.MIDDLE_RIGHT);
            buttonsLayout.setExpandRatio(okButton, 1.0F);

            final Button cancelButton = SPUIComponentProvider.getButton(UIComponentIdProvider.CANCEL_BUTTON,
                    i18n.getMessage(UIMessageIdProvider.BUTTON_CANCEL), "", "", true, FontAwesome.TIMES,
                    SPUIButtonStyleNoBorderWithIcon.class);
            cancelButton.setSizeUndefined();
            cancelButton.addStyleName("default-color");
            cancelButton.addClickListener(this);
            buttonsLayout.addComponent(cancelButton);
            buttonsLayout.setComponentAlignment(cancelButton, Alignment.MIDDLE_LEFT);
            buttonsLayout.setExpandRatio(cancelButton, 1.0F);
        }

        @Override
        public void buttonClick(final Button.ClickEvent event) {
            if (event.getButton().getId().equals(UIComponentIdProvider.SAVE_BUTTON)) {
                callback.accept(true);
            } else {
                callback.accept(false);
            }

            close();
        }
    }
}
