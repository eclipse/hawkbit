/**
 * Copyright (c) 2019 Bosch Software Innovations GmbH and others.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.tenantconfiguration.repository;

import static org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED;
import static org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey.MULTI_ASSIGNMENTS_WEIGHT_DEFAULT;

import java.io.Serializable;
import java.util.Optional;

import org.eclipse.hawkbit.repository.RepositoryProperties;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.builder.LabelBuilder;
import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.tenantconfiguration.generic.AbstractBooleanTenantConfigurationItem;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.vaadin.data.Validator;
import com.vaadin.data.util.converter.StringToIntegerConverter;
import com.vaadin.data.validator.IntegerRangeValidator;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Link;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

/**
 * This class represents the UI item for enabling /disabling the
 * Multi-Assignments feature as part of the repository configuration view.
 */
public class MultiAssignmentsConfigurationItem extends AbstractBooleanTenantConfigurationItem {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiAssignmentsConfigurationItem.class);

    private static final long serialVersionUID = 1L;

    private static final String MSG_KEY_CHECKBOX = "label.configuration.repository.multiassignments";
    private static final String MSG_KEY_NOTICE = "label.configuration.repository.multiassignments.notice";
    private static final String MSG_KEY_DEFAULT_WEIGHT = "label.configuration.repository.multiassignments.default.notice";
    private static final String MSG_KEY_DEFAULT_WEIGHT_INPUT_HINT = "prompt.weight.min.max";
    private static final String MSG_KEY_DEFAULT_WEIGHT_INPUT_INVALID = "label.configuration.repository.multiassignments.default.invalid";
    private final transient RepositoryProperties repositoryProperties;
    private final UiProperties uiProperties;
    private final VaadinMessageSource i18n;
    private VerticalLayout container;
    private TextField defaultWeightTextField;
    private Label msgBoxLabel;

    private boolean isMultiAssignmentsEnabled;
    private boolean multiAssignmentsEnabledChanged;

    /**
     * Constructor.
     *
     * @param tenantConfigurationManagement to read /write tenant-specific configuration properties
     * @param i18n                          to obtain localized strings
     */
    public MultiAssignmentsConfigurationItem(final TenantConfigurationManagement tenantConfigurationManagement,
                                             final VaadinMessageSource i18n, final UiProperties uiProperties,
                                             final RepositoryProperties repositoryProperties) {
        super(MULTI_ASSIGNMENTS_ENABLED, tenantConfigurationManagement, i18n);
        this.i18n = i18n;
        this.repositoryProperties = repositoryProperties;
        this.uiProperties = uiProperties;
        setImmediate(true);
        createMsgBoxLabel();
        isMultiAssignmentsEnabled = isConfigEnabled();

        createContainer();
        setSettingsVisible(isMultiAssignmentsEnabled);

    }

    private void createContainer() {
        container = new VerticalLayout();
        container.setImmediate(true);
        createComponents();
    }

    private void createMsgBoxLabel() {
        msgBoxLabel = newLabel(MSG_KEY_CHECKBOX);
        addComponent(msgBoxLabel);
    }

    private int getWeightForTenantOrDefault() {
        return readConfigValue(MULTI_ASSIGNMENTS_WEIGHT_DEFAULT, Integer.class)
                .orElse(repositoryProperties.getActionWeightIfAbsent());
    }

    private void createComponents() {
        final HorizontalLayout row1 = newHorizontalLayout();
        final Label defaultWeightHintLabel = newLabel(MSG_KEY_DEFAULT_WEIGHT);
        row1.addComponent(defaultWeightHintLabel);
        row1.setComponentAlignment(defaultWeightHintLabel, Alignment.MIDDLE_CENTER);
        row1.addComponent(createIntegerTextField(MSG_KEY_DEFAULT_WEIGHT_INPUT_HINT,
                getWeightForTenantOrDefault()));

        final Link linkToDefaultWeightHelp = SPUIComponentProvider.getHelpLink(i18n,
                uiProperties.getLinks().getDocumentation().getMultiAssignments());
        linkToDefaultWeightHelp.setId("weight-help-link");
        row1.addComponent(linkToDefaultWeightHelp);
        row1.setComponentAlignment(linkToDefaultWeightHelp, Alignment.MIDDLE_LEFT);

        container.addComponent(row1);

        final HorizontalLayout row2 = new HorizontalLayout();
        row2.addComponent(newLabel(MSG_KEY_NOTICE));
        container.addComponent(row2);
    }

    private static HorizontalLayout newHorizontalLayout() {
        final HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(true);
        layout.setImmediate(true);
        return layout;
    }

    @Override
    public boolean isUserInputValid() {
        return !isMultiAssignmentsEnabled || defaultWeightTextField.isValid();
    }

    @Override
    public void configEnable() {
        if (!isMultiAssignmentsEnabled) {
            multiAssignmentsEnabledChanged = true;
        }
        isMultiAssignmentsEnabled = true;
        setSettingsVisible(true);
    }

    @Override
    public void configDisable() {
        if (isMultiAssignmentsEnabled) {
            multiAssignmentsEnabledChanged = true;
        }
        isMultiAssignmentsEnabled = false;
        setSettingsVisible(false);
    }

    @Override
    public void setEnabled(final boolean enable) {
        this.msgBoxLabel.setEnabled(enable);
        this.container.setEnabled(true);
    }

    @Override
    public void save() {
        if (multiAssignmentsEnabledChanged) {
            writeConfigValue(MULTI_ASSIGNMENTS_ENABLED, isMultiAssignmentsEnabled);
            multiAssignmentsEnabledChanged = false;
        }
        updateDefaultWeight();
    }

    private void updateDefaultWeight(){
        if (isMultiAssignmentsEnabled) {
            writeConfigValue(MULTI_ASSIGNMENTS_WEIGHT_DEFAULT, getWeightFromTextField());
        }
    }

    private int getWeightFromTextField() {
        return Integer.parseInt(defaultWeightTextField.getValue().replaceAll("[^0-9]", ""));
    }

    public boolean weightFromInputChanged() {
        return (getWeightFromTextField() == getWeightForTenantOrDefault());
    }

    private <T extends Serializable> void writeConfigValue(final String key, final T value) {
        LOGGER.debug("Write tenant configuration value: key={} value={}", key,value);
        getTenantConfigurationManagement().addOrUpdateConfiguration(key, value);
    }


    private <T extends Serializable> Optional<T> readConfigValue(final String key, final Class<T> valueType) {
        return Optional.ofNullable(getTenantConfigurationManagement().getConfigurationValue(key, valueType).getValue());
    }

    @Override
    public void undo() {
        isMultiAssignmentsEnabled = readConfigValue(MULTI_ASSIGNMENTS_ENABLED, Boolean.class).orElse(false);
        defaultWeightTextField.setValue(String.valueOf(getWeightForTenantOrDefault()));
    }

    private void setSettingsVisible(final boolean visible) {
        if (visible) {
            addComponent(container);
        } else {
            removeComponent(container);
        }
    }

    private Label newLabel(final String msgKey) {
        final Label label = new LabelBuilder().name(i18n.getMessage(msgKey)).buildLabel();
        return label;
    }

    private TextField createTextField(final String in18Key, final int maxLength) {
        return new TextFieldBuilder(maxLength).prompt(i18n.getMessage(in18Key)).buildTextComponent();
    }

    private TextField createIntegerTextField(final String in18Key, final Integer value) {
        defaultWeightTextField = createTextField(i18n.getMessage(in18Key, Action.WEIGHT_MIN, Action.WEIGHT_MAX), 5);
        defaultWeightTextField.setConverter(new StringToIntegerConverter());
        defaultWeightTextField.addValidator(new ActionWeightValidator(i18n.getMessage(MSG_KEY_DEFAULT_WEIGHT_INPUT_INVALID)));
        defaultWeightTextField.setWidthUndefined();
        defaultWeightTextField.setDescription("(0 - 1000)");
        defaultWeightTextField.setValue(value.toString());
        return defaultWeightTextField;

    }

    public TextField getWeightTextField() {
        return defaultWeightTextField;
    }

    public int getdefaultWeight() {
        return getWeightForTenantOrDefault();
    }

    static class ActionWeightValidator implements Validator {

        private static final long serialVersionUID = 1L;
        private final String message;
        private final Validator integerRangeValidator;

        ActionWeightValidator(final String message) {
            this.message = message;
            this.integerRangeValidator = new IntegerRangeValidator(message, Action.WEIGHT_MIN, Action.WEIGHT_MAX);
        }

        @Override
        public void validate(final Object value) {
            if (StringUtils.isEmpty(value)) {
                throw new InvalidValueException(message);
            }
            try {
                integerRangeValidator.validate(Integer.parseInt(value.toString()));
            } catch (final RuntimeException e) {
                LOGGER.debug("Integer range validation failed", e);
                throw new InvalidValueException(message);
            }
        }

    }
}
