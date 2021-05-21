/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import com.vaadin.server.FileDownloader;
import com.vaadin.server.StreamResource;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.rsql.RsqlValidationOracle;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.AbstractEntityWindowLayout;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow.SaveDialogCloseListener;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.FilterChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.filtermanagement.state.TargetFilterDetailsLayoutUiState;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.SpringContextHolder;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.data.HasValue.ValueChangeEvent;
import com.vaadin.event.ShortcutAction;
import com.vaadin.event.ShortcutListener;
import com.vaadin.server.Sizeable.Unit;
import com.vaadin.shared.Registration;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComponentContainer;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Link;
import com.vaadin.ui.TextField;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Executor;

import static com.vaadin.server.StreamResource.StreamSource;

/**
 * Target add/update window layout.
 */
public class TargetFilterAddUpdateLayout extends AbstractEntityWindowLayout<ProxyTargetFilterQuery> {
    private final VaadinMessageSource i18n;
    private final SpPermissionChecker permChecker;

    private static final String FILTER_QUERY_CAPTION = "textfield.query";

    private final TargetFilterAddUpdateLayoutComponentBuilder filterComponentBuilder;

    private final TextField filterNameInput;
    private final AutoCompleteTextFieldComponent autoCompleteComponent;
    private final Link helpLink;
    private final Button searchButton;
    private final Button saveButton;
    private final TargetFilterDetailsLayoutUiState uiState;
    private final UIEventBus eventBus;
    private final Button exportButton;
    private final TargetManagement targetManagement;
    private FileDownloader fileDownloader;

    private Registration saveListener;

    private static final Logger LOG = LoggerFactory.getLogger(TargetFilterAddUpdateLayout.class);

    /**
     * Constructor for AbstractTagWindowLayout
     * 
     * @param i18n
     *            VaadinMessageSource
     * @param permChecker
     *            Permission checker
     * @param uiProperties
     *            UiProperties
     * @param uiState
     *            TargetFilterDetailsLayoutUiState
     * @param eventBus
     *            UIEventBus
     * @param rsqlValidationOracle
     *            RsqlValidationOracle
     */
    public TargetFilterAddUpdateLayout(final VaadinMessageSource i18n, final SpPermissionChecker permChecker,
            final UiProperties uiProperties, final TargetFilterDetailsLayoutUiState uiState, final UIEventBus eventBus,
            final RsqlValidationOracle rsqlValidationOracle, TargetManagement targetManagement) {
        super();

        this.i18n = i18n;
        this.permChecker = permChecker;
        this.uiState = uiState;
        this.eventBus = eventBus;
        this.filterComponentBuilder = new TargetFilterAddUpdateLayoutComponentBuilder(i18n, uiProperties,
                rsqlValidationOracle);

        this.filterNameInput = filterComponentBuilder.createNameField(binder);
        this.autoCompleteComponent = filterComponentBuilder.createQueryField(binder);
        this.helpLink = filterComponentBuilder.createFilterHelpLink();
        this.searchButton = filterComponentBuilder.createSearchTargetsByFilterButton();
        this.saveButton = filterComponentBuilder.createSaveButton();
        this.exportButton = filterComponentBuilder.createExportButton();
        this.targetManagement = targetManagement;

        addValueChangeListeners();
    }

    @Override
    public ComponentContainer getRootComponent() {
        final FormLayout formLayout = new FormLayout();
        formLayout.setSpacing(true);
        formLayout.setMargin(false);
        formLayout.setWidthFull();

        formLayout.addComponent(filterNameInput);
        autoCompleteComponent.setCaption(i18n.getMessage(FILTER_QUERY_CAPTION));
        autoCompleteComponent.setRequiredIndicatorVisible(true);
        autoCompleteComponent.focus();
        formLayout.addComponent(autoCompleteComponent);

        final HorizontalLayout actionsLayout = new HorizontalLayout();
        actionsLayout.setSpacing(false);
        actionsLayout.setMargin(false);
        actionsLayout.setSizeUndefined();
        actionsLayout.addStyleName(SPUIStyleDefinitions.ADD_UPDATE_FILTER_ACTIONS_LAYOUT);

        actionsLayout.addComponent(helpLink);
        searchButton.setEnabled(false);
        actionsLayout.addComponent(searchButton);
        saveButton.setEnabled(false);
        actionsLayout.addComponent(saveButton);
        actionsLayout.addComponent(exportButton);
        exportButton.setEnabled(false);

        final HorizontalLayout filterQueryLayout = new HorizontalLayout();
        filterQueryLayout.setSpacing(true);
        filterQueryLayout.setMargin(false);
        filterQueryLayout.setWidth(90.0F, Unit.PERCENTAGE);
        filterQueryLayout.addStyleName(SPUIStyleDefinitions.ADD_UPDATE_FILTER_LAYOUT);

        filterQueryLayout.addComponent(formLayout);
        filterQueryLayout.setExpandRatio(formLayout, 1.0F);

        filterQueryLayout.addComponent(actionsLayout);
        filterQueryLayout.setComponentAlignment(actionsLayout, Alignment.BOTTOM_LEFT);

        return filterQueryLayout;
    }

    protected void setCSVExportCallback(final ProxyTargetFilterQuery proxyEntity){
        StreamResource csvResource = getStreamResourceToCSV(proxyEntity);
        fileDownloader = new FileDownloader(csvResource);
        fileDownloader.extend(exportButton);
    }

    protected void removeDownloaderExtension(){
        if(fileDownloader != null){
            fileDownloader.remove();
        }
    }

    private StreamResource getStreamResourceToCSV(final ProxyTargetFilterQuery proxyEntity){

        final StreamSource streamSource = () -> {
            final PipedInputStream in = new PipedInputStream();

            try {
                final PipedOutputStream out = new PipedOutputStream(in);
                final TargetDataCsvExporter csvExporter = new TargetDataCsvExporter(targetManagement, ";",
                        "E MMM dd HH:mm:ss z yyyy");

                SpringContextHolder.getInstance().getBean("uiExecutor", Executor.class)
                        .execute(() -> csvExporter.writeTargetsByFilterId(proxyEntity.getId(), out));

            } catch (final IOException ex) {
                LOG.warn("Creating piped Stream failed: ", ex);
            }
            return in;
        };

        return new StreamResource(streamSource, proxyEntity.getName() + ".csv");
    }

    private void addValueChangeListeners() {
        searchButton.addClickListener(event -> onSearchIconClick());
        autoCompleteComponent.addValidationListener((valid, message) -> {
            searchButton.setEnabled(valid);
            exportButton.setEnabled(valid);
        });
        autoCompleteComponent.addTextfieldChangedListener(this::onFilterQueryTextfieldChanged);
        autoCompleteComponent
                .addShortcutListener(new ShortcutListener("List Filtered Targets", ShortcutAction.KeyCode.ENTER, null) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void handleAction(final Object sender, final Object target) {
                        onSearchIconClick();
                    }
                });
        filterNameInput.addValueChangeListener(this::onFilterNameChanged);

        if (permChecker.hasUpdateTargetPermission()) {
            addValidationListener(saveButton::setEnabled);
        }
    }

    private void onFilterQueryTextfieldChanged(final ValueChangeEvent<String> event) {
        uiState.setFilterQueryValueInput(event.getValue());
    }

    private void onFilterNameChanged(final ValueChangeEvent<String> event) {
        uiState.setNameInput(event.getValue());
    }

    private void onSearchIconClick() {
        if (autoCompleteComponent.isValid()) {
            filterTargetListByQuery(autoCompleteComponent.getValue());
        }
    }

    /**
     * Filter target list by query
     *
     * @param query
     *            Input query value
     */
    public void filterTargetListByQuery(final String query) {
        eventBus.publish(EventTopics.FILTER_CHANGED, this,
                new FilterChangedEventPayload<>(ProxyTarget.class, FilterType.QUERY, query, EventView.TARGET_FILTER));
    }

    /**
     * Save the changes
     *
     * @param saveCallback
     *            SaveDialogCloseListener
     */
    public void setSaveCallback(final SaveDialogCloseListener saveCallback) {
        if (saveListener != null) {
            saveListener.remove();
        }

        saveListener = saveButton.addClickListener(event -> {
            if (!saveCallback.canWindowSaveOrUpdate()) {
                return;
            }

            saveCallback.saveOrUpdate();
        });
    }

    /**
     * Disable search button
     */
    public void disableSearchButton() {
        searchButton.setEnabled(false);
    }

    /**
     * Check validity of target filter query.
     * 
     * @return {@code true}: if the target filter query is valid {@code false}:
     *         otherwise
     */
    public boolean isFilterQueryValid() {
        return autoCompleteComponent.isValid();
    }
}
