/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import java.util.Optional;

import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.model.NamedEntity;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.builder.LabelBuilder;
import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.components.SPUIButton;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.filtermanagement.event.CustomFilterUIEvent;
import org.eclipse.hawkbit.ui.filtermanagement.state.FilterManagementUIState;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.StringUtils;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.event.FieldEvents.BlurListener;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.event.LayoutEvents.LayoutClickListener;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Link;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

/**
 * A Vaadin layout for create or update the target filter.
 */
public class CreateOrUpdateFilterHeader extends VerticalLayout implements Button.ClickListener {

    private static final long serialVersionUID = 7474232427119031474L;

    private static final String BREADCRUMB_CUSTOM_FILTERS = "breadcrumb.target.filter.custom.filters";

    private final VaadinMessageSource i18n;

    private final transient EventBus.UIEventBus eventBus;

    private final FilterManagementUIState filterManagementUIState;

    private final transient TargetFilterQueryManagement targetFilterQueryManagement;

    private final SpPermissionChecker permissionChecker;

    private final UINotification notification;

    private final UiProperties uiProperties;

    private final transient EntityFactory entityFactory;

    private final AutoCompleteTextFieldComponent queryTextField;

    private Button breadcrumbButton;

    private Label breadcrumbName;

    private Label headerCaption;

    private TextField nameTextField;

    private Label nameLabel;

    private SPUIButton closeIcon;

    private Button saveButton;

    private Link helpLink;

    private Button searchIcon;

    private String oldFilterName;

    private String oldFilterQuery;

    private HorizontalLayout titleFilterIconsLayout;

    private HorizontalLayout captionLayout;

    private BlurListener nameTextFieldBlurListener;

    private LayoutClickListener nameLayoutClickListner;

    CreateOrUpdateFilterHeader(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final FilterManagementUIState filterManagementUIState,
            final TargetFilterQueryManagement targetFilterQueryManagement, final SpPermissionChecker permissionChecker,
            final UINotification notification, final UiProperties uiProperties, final EntityFactory entityFactory,
            final AutoCompleteTextFieldComponent queryTextField) {
        this.i18n = i18n;
        this.eventBus = eventBus;
        this.filterManagementUIState = filterManagementUIState;
        this.targetFilterQueryManagement = targetFilterQueryManagement;
        this.permissionChecker = permissionChecker;
        this.notification = notification;
        this.uiProperties = uiProperties;
        this.entityFactory = entityFactory;
        this.queryTextField = queryTextField;

        createComponents();
        createListeners();
        buildLayout();
        restoreOnLoad();
        setUpCaptionLayout(filterManagementUIState.isCreateFilterViewDisplayed());
        eventBus.subscribe(this);
    }

    private void restoreOnLoad() {
        if (filterManagementUIState.isEditViewDisplayed()) {
            populateComponents();
        }
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final CustomFilterUIEvent custFUIEvent) {
        if (custFUIEvent == CustomFilterUIEvent.TARGET_FILTER_DETAIL_VIEW) {
            populateComponents();
            eventBus.publish(this, CustomFilterUIEvent.TARGET_DETAILS_VIEW);
        } else if (custFUIEvent == CustomFilterUIEvent.CREATE_NEW_FILTER_CLICK) {
            setUpCaptionLayout(true);
            resetComponents();
        }
    }

    private void populateComponents() {
        filterManagementUIState.getTfQuery().ifPresent(query -> {
            queryTextField.setValue(query.getQuery());
            nameLabel.setValue(query.getName());
            oldFilterName = query.getName();
            oldFilterQuery = query.getQuery();
        });
        breadcrumbName.setValue(nameLabel.getValue());
        queryTextField.showValidationSuccesIcon(filterManagementUIState.getFilterQueryValue());
        titleFilterIconsLayout.addStyleName(SPUIStyleDefinitions.TARGET_FILTER_CAPTION_LAYOUT);
        headerCaption.setVisible(false);
        setUpCaptionLayout(false);
    }

    private void resetComponents() {
        queryTextField.clear();
        queryTextField.focus();
        headerCaption.setVisible(true);
        breadcrumbName.setValue(headerCaption.getValue());
        nameLabel.setValue("");
        saveButton.setEnabled(false);
        titleFilterIconsLayout.removeStyleName(SPUIStyleDefinitions.TARGET_FILTER_CAPTION_LAYOUT);
    }

    private void createComponents() {

        breadcrumbButton = createBreadcrumbButton();

        headerCaption = new LabelBuilder().name(i18n.getMessage(UIMessageIdProvider.LABEL_CREATE_FILTER))
                .buildCaptionLabel();

        nameLabel = new LabelBuilder().name("").buildLabel();
        nameLabel.setId(UIComponentIdProvider.TARGET_FILTER_QUERY_NAME_LABEL_ID);

        nameTextField = createNameTextField();
        nameTextField.setWidth(380, Unit.PIXELS);

        saveButton = createSaveButton();
        searchIcon = createSearchIcon();

        helpLink = SPUIComponentProvider.getHelpLink(i18n,
                uiProperties.getLinks().getDocumentation().getTargetfilterView());

        closeIcon = createSearchResetIcon();
    }

    private Button createBreadcrumbButton() {
        final Button createFilterViewLink = SPUIComponentProvider.getButton(null, "", "", null, false, null,
                SPUIButtonStyleNoBorder.class);
        createFilterViewLink.setStyleName(ValoTheme.LINK_SMALL + " " + "on-focus-no-border link rollout-caption-links");
        createFilterViewLink.setDescription(i18n.getMessage(BREADCRUMB_CUSTOM_FILTERS));
        createFilterViewLink.setCaption(i18n.getMessage(BREADCRUMB_CUSTOM_FILTERS));
        createFilterViewLink.addClickListener(value -> showCustomFiltersView());

        return createFilterViewLink;
    }

    private TextField createNameTextField() {
        final TextField nameField = new TextFieldBuilder(NamedEntity.NAME_MAX_SIZE)
                .caption(i18n.getMessage("textfield.customfiltername")).required(true, i18n)
                .id(UIComponentIdProvider.CUSTOM_FILTER_ADD_NAME).buildTextComponent();
        nameField.setPropertyDataSource(nameLabel);
        nameField.addTextChangeListener(this::onFilterNameChange);
        return nameField;
    }

    private void createListeners() {
        nameTextFieldBlurListener = event -> {
            if (!StringUtils.isEmpty(nameTextField.getValue())) {
                captionLayout.removeComponent(nameTextField);
                captionLayout.addComponent(nameLabel);
            }
        };
        nameLayoutClickListner = event -> {
            if (event.getClickedComponent() instanceof Label) {
                captionLayout.removeComponent(nameLabel);
                captionLayout.addComponent(nameTextField);
                nameTextField.focus();
            }
        };

        queryTextField.addTextChangeListener((valid, query) -> enableDisableSaveButton(!valid, query));

    }

    private void onFilterNameChange(final TextChangeEvent event) {
        if (isNameAndQueryEmpty(event.getText(), queryTextField.getValue())
                || (event.getText().equals(oldFilterName) && queryTextField.getValue().equals(oldFilterQuery))) {
            saveButton.setEnabled(false);
        } else {
            if (hasSavePermission()) {
                saveButton.setEnabled(true);
            }
        }
    }

    private void buildLayout() {
        captionLayout = new HorizontalLayout();
        captionLayout.setDescription(i18n.getMessage("tooltip.click.to.edit"));
        captionLayout.setId(UIComponentIdProvider.TARGET_FILTER_QUERY_NAME_LAYOUT_ID);

        titleFilterIconsLayout = new HorizontalLayout();
        titleFilterIconsLayout.addComponents(headerCaption, captionLayout);
        titleFilterIconsLayout.setSpacing(true);

        final HorizontalLayout breadcrumbLayout = new HorizontalLayout();
        breadcrumbLayout.addComponent(breadcrumbButton);
        breadcrumbLayout.addComponent(new Label(">"));
        breadcrumbName = new LabelBuilder().buildCaptionLabel();
        breadcrumbLayout.addComponent(breadcrumbName);
        breadcrumbName.addStyleName("breadcrumbPaddingLeft");

        final HorizontalLayout titleFilterLayout = new HorizontalLayout();
        titleFilterLayout.setSizeFull();
        titleFilterLayout.addComponents(titleFilterIconsLayout, closeIcon);
        titleFilterLayout.setExpandRatio(titleFilterIconsLayout, 1.0F);
        titleFilterLayout.setComponentAlignment(titleFilterIconsLayout, Alignment.TOP_LEFT);
        titleFilterLayout.setComponentAlignment(closeIcon, Alignment.TOP_RIGHT);

        final HorizontalLayout iconLayout = new HorizontalLayout();
        iconLayout.setSizeUndefined();
        iconLayout.setSpacing(false);
        iconLayout.addComponents(helpLink, searchIcon, saveButton);

        final HorizontalLayout queryLayout = new HorizontalLayout();
        queryLayout.setSizeUndefined();
        queryLayout.setSpacing(true);
        queryLayout.addComponents(queryTextField, iconLayout);

        addComponent(breadcrumbLayout);
        addComponent(titleFilterLayout);
        addComponent(queryLayout);
        setSpacing(true);
        addStyleName(SPUIStyleDefinitions.WIDGET_TITLE);
        addStyleName("bordered-layout");
    }

    private void setUpCaptionLayout(final boolean isCreateView) {
        captionLayout.removeAllComponents();
        if (isCreateView) {
            nameTextField.removeBlurListener(nameTextFieldBlurListener);
            captionLayout.removeLayoutClickListener(nameLayoutClickListner);
            captionLayout.addComponent(nameTextField);
        } else {
            captionLayout.addComponent(nameLabel);
            nameTextField.addBlurListener(nameTextFieldBlurListener);
            captionLayout.addLayoutClickListener(nameLayoutClickListner);
        }
    }

    private void enableDisableSaveButton(final boolean validationFailed, final String query) {
        if (validationFailed || (isNameAndQueryEmpty(nameTextField.getValue(), query)
                || (query.equals(oldFilterQuery) && nameTextField.getValue().equals(oldFilterName)))) {
            saveButton.setEnabled(false);
            searchIcon.setEnabled(false);
        } else {
            if (hasSavePermission()) {
                saveButton.setEnabled(true);
            }
            searchIcon.setEnabled(true);
        }
    }

    private static boolean isNameAndQueryEmpty(final String name, final String query) {
        return StringUtils.isEmpty(name) && StringUtils.isEmpty(query);
    }

    private SPUIButton createSearchResetIcon() {
        final SPUIButton button = (SPUIButton) SPUIComponentProvider.getButton(
                UIComponentIdProvider.CUSTOM_FILTER_CLOSE, "", i18n.getMessage(UIMessageIdProvider.TOOLTIP_CLOSE), null,
                false, FontAwesome.TIMES, SPUIButtonStyleNoBorder.class);
        button.addClickListener(event -> closeFilterLayout());
        return button;
    }

    private void closeFilterLayout() {
        filterManagementUIState.setFilterQueryValue(null);
        filterManagementUIState.setCreateFilterBtnClicked(false);
        filterManagementUIState.setEditViewDisplayed(false);
        filterManagementUIState.setTfQuery(null);
        eventBus.publish(this, CustomFilterUIEvent.EXIT_CREATE_OR_UPDATE_FILTRER_VIEW);
    }

    private Button createSaveButton() {
        saveButton = SPUIComponentProvider.getButton(UIComponentIdProvider.CUSTOM_FILTER_SAVE_ICON,
                UIComponentIdProvider.CUSTOM_FILTER_SAVE_ICON, i18n.getMessage(UIMessageIdProvider.TOOLTIP_SAVE), null,
                false, FontAwesome.SAVE, SPUIButtonStyleNoBorder.class);
        saveButton.addClickListener(this);
        saveButton.setEnabled(false);
        return saveButton;
    }

    private Button createSearchIcon() {
        searchIcon = SPUIComponentProvider.getButton(UIComponentIdProvider.FILTER_SEARCH_ICON_ID, "",
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_SEARCH), null, false, FontAwesome.SEARCH,
                SPUIButtonStyleNoBorder.class);
        searchIcon.addClickListener(event -> onSearchIconClick());
        searchIcon.setEnabled(false);
        searchIcon.setData(false);
        return searchIcon;
    }

    private void onSearchIconClick() {

        if (queryTextField.isValidationError()) {
            return;
        }

        queryTextField.showValidationInProgress();
        queryTextField.getExecutor().execute(queryTextField.new StatusCircledAsync(UI.getCurrent()));

    }

    @Override
    public void buttonClick(final ClickEvent event) {
        if (UIComponentIdProvider.CUSTOM_FILTER_SAVE_ICON.equals(event.getComponent().getId())
                && manadatoryFieldsPresent()) {
            if (filterManagementUIState.isCreateFilterViewDisplayed() && !doesAlreadyExists()) {
                createTargetFilterQuery();
            } else {
                updateCustomFilter();
            }
        }
    }

    private void createTargetFilterQuery() {
        final TargetFilterQuery targetFilterQuery = targetFilterQueryManagement.create(entityFactory.targetFilterQuery()
                .create().name(nameTextField.getValue()).query(queryTextField.getValue()));
        notification.displaySuccess(i18n.getMessage("message.create.filter.success", targetFilterQuery.getName()));
        eventBus.publish(this, CustomFilterUIEvent.CREATE_TARGET_FILTER_QUERY);
    }

    private void updateCustomFilter() {
        final Optional<TargetFilterQuery> tfQuery = filterManagementUIState.getTfQuery();
        if (!tfQuery.isPresent()) {
            return;
        }
        final TargetFilterQuery targetFilterQuery = tfQuery.get();
        final TargetFilterQuery updatedTargetFilter = targetFilterQueryManagement
                .update(entityFactory.targetFilterQuery().update(targetFilterQuery.getId())
                        .name(nameTextField.getValue()).query(queryTextField.getValue()));
        filterManagementUIState.setTfQuery(updatedTargetFilter);
        oldFilterName = nameTextField.getValue();
        oldFilterQuery = queryTextField.getValue();
        notification.displaySuccess(i18n.getMessage("message.update.filter.success"));
        eventBus.publish(this, CustomFilterUIEvent.UPDATED_TARGET_FILTER_QUERY);
    }

    private boolean hasSavePermission() {
        if (filterManagementUIState.isCreateFilterViewDisplayed()) {
            return permissionChecker.hasCreateTargetFilterPermission();
        } else {
            return permissionChecker.hasUpdateTargetFilterPermission();
        }
    }

    private boolean doesAlreadyExists() {
        if (targetFilterQueryManagement.getByName(nameTextField.getValue()).isPresent()) {
            notification.displayValidationError(
                    i18n.getMessage("message.target.filter.duplicate", nameTextField.getValue()));
            return true;
        }
        return false;
    }

    private boolean manadatoryFieldsPresent() {
        if (StringUtils.isEmpty(nameTextField.getValue())
                || StringUtils.isEmpty(filterManagementUIState.getFilterQueryValue())) {
            notification.displayValidationError(i18n.getMessage("message.target.filter.validation"));
            return false;
        }
        return true;
    }

    private void showCustomFiltersView() {
        eventBus.publish(this, CustomFilterUIEvent.SHOW_FILTER_MANAGEMENT);
    }

}
