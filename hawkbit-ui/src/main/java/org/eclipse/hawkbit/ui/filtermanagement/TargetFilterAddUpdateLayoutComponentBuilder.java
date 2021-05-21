/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import org.eclipse.hawkbit.repository.rsql.RsqlValidationOracle;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.builder.FormComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.data.Binder;
import com.vaadin.data.ValidationResult;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.Sizeable.Unit;
import com.vaadin.ui.Button;
import com.vaadin.ui.Link;
import com.vaadin.ui.TextField;

/**
 * Builder for target filter add update layout component
 */
public class TargetFilterAddUpdateLayoutComponentBuilder {

    public static final String TEXTFIELD_FILTER_NAME = "textfield.name";

    private final VaadinMessageSource i18n;
    private final UiProperties uiProperties;
    private final RsqlValidationOracle rsqlValidationOracle;

    /**
     * Constructor for TargetFilterAddUpdateLayoutComponentBuilder
     *
     * @param i18n
     *            VaadinMessageSource
     * @param uiProperties
     *            UiProperties
     * @param rsqlValidationOracle
     *            RsqlValidationOracle
     */
    public TargetFilterAddUpdateLayoutComponentBuilder(final VaadinMessageSource i18n, final UiProperties uiProperties,
            final RsqlValidationOracle rsqlValidationOracle) {
        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.rsqlValidationOracle = rsqlValidationOracle;
    }

    /**
     * create name field
     * 
     * @param binder
     *            binder the input will be bound to
     * @return input component
     */
    public TextField createNameField(final Binder<ProxyTargetFilterQuery> binder) {
        final TextField filterName = FormComponentBuilder
                .createNameInput(binder, i18n, UIComponentIdProvider.CUSTOM_FILTER_ADD_NAME).getComponent();
        filterName.setWidth(40.0F, Unit.PERCENTAGE);

        return filterName;
    }

    /**
     * Create query field
     *
     * @param binder
     *            Vaadin binder
     *
     * @return Auto complete query field component
     */
    public AutoCompleteTextFieldComponent createQueryField(final Binder<ProxyTargetFilterQuery> binder) {
        final AutoCompleteTextFieldComponent autoCompleteComponent = new AutoCompleteTextFieldComponent(
                rsqlValidationOracle);

        binder.forField(autoCompleteComponent)
                .withValidator((query, context) -> autoCompleteComponent.isValid() ? ValidationResult.ok()
                        : ValidationResult
                                .error(i18n.getMessage(UIMessageIdProvider.MESSAGE_FILTER_QUERY_ERROR_NOTVALIDE)))
                .bind(ProxyTargetFilterQuery::getQuery, ProxyTargetFilterQuery::setQuery);

        return autoCompleteComponent;
    }

    /**
     * Create filter help link
     *
     * @return Filter help link
     */
    public Link createFilterHelpLink() {
        return SPUIComponentProvider.getHelpLink(i18n,
                uiProperties.getLinks().getDocumentation().getTargetfilterView());
    }

    /**
     * Create filter to search targets
     *
     * @return Filter button to search targets
     */
    public Button createSearchTargetsByFilterButton() {
        return SPUIComponentProvider.getButton(UIComponentIdProvider.FILTER_SEARCH_ICON_ID, "",
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_SEARCH), null, false, VaadinIcons.SEARCH,
                SPUIButtonStyleNoBorder.class);
    }

    /**
     * Create save button
     *
     * @return Save button
     */
    public Button createSaveButton() {
        return SPUIComponentProvider.getButton(UIComponentIdProvider.CUSTOM_FILTER_SAVE_ICON,
                UIComponentIdProvider.CUSTOM_FILTER_SAVE_ICON, i18n.getMessage(UIMessageIdProvider.TOOLTIP_SAVE), null,
                false, VaadinIcons.HARDDRIVE, SPUIButtonStyleNoBorder.class);
    }

    public Button createExportButton(){
        return SPUIComponentProvider.getButton(UIComponentIdProvider.CUSTOM_FILTER_CSV_ICON,
                UIComponentIdProvider.CUSTOM_FILTER_CSV_ICON, i18n.getMessage(UIMessageIdProvider.TOOLTIP_EXPORT_CSV),
                null, false, VaadinIcons.FILE_TEXT, SPUIButtonStyleNoBorder.class);
    }
}
