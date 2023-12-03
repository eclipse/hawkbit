/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.eclipse.hawkbit.ui.view;

import com.eclipse.hawkbit.ui.HawkbitClient;
import com.eclipse.hawkbit.ui.view.util.Filter;
import com.eclipse.hawkbit.ui.MainLayout;
import com.eclipse.hawkbit.ui.view.util.SelectionGrid;
import com.eclipse.hawkbit.ui.view.util.TableView;
import com.eclipse.hawkbit.ui.view.util.Utils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.eclipse.hawkbit.mgmt.json.model.distributionset.MgmtActionType;
import org.eclipse.hawkbit.mgmt.json.model.distributionset.MgmtDistributionSet;
import org.eclipse.hawkbit.mgmt.json.model.rollout.MgmtRolloutCondition;
import org.eclipse.hawkbit.mgmt.json.model.rollout.MgmtRolloutErrorAction;
import org.eclipse.hawkbit.mgmt.json.model.rollout.MgmtRolloutResponseBody;
import org.eclipse.hawkbit.mgmt.json.model.rollout.MgmtRolloutRestRequestBody;
import org.eclipse.hawkbit.mgmt.json.model.rolloutgroup.MgmtRolloutGroupResponseBody;
import org.eclipse.hawkbit.mgmt.json.model.targetfilter.MgmtTargetFilterQuery;
import org.springframework.util.ObjectUtils;

import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@PageTitle("Rollouts")
@Route(value = "rollouts", layout = MainLayout.class)
@RolesAllowed({"ROLLOUT_READ"})
@Uses(Icon.class)
public class RolloutView extends TableView<MgmtRolloutResponseBody, Long> {

    public RolloutView(final HawkbitClient hawkbitClient) {
        super(
                new RolloutFilter(hawkbitClient),
                new SelectionGrid.EntityRepresentation<>(
                        MgmtRolloutResponseBody.class, MgmtRolloutResponseBody::getRolloutId) {

                    private final RolloutDetails details = new RolloutDetails(hawkbitClient);
                    @Override
                    protected void addColumns(final Grid<MgmtRolloutResponseBody> grid) {
                        grid.addColumn(MgmtRolloutResponseBody::getRolloutId).setHeader("Id").setAutoWidth(true);
                        grid.addColumn(MgmtRolloutResponseBody::getName).setHeader("Name").setAutoWidth(true);
                        grid.addColumn(MgmtRolloutResponseBody::getTotalGroups).setHeader("Group Count").setAutoWidth(true);
                        grid.addColumn(MgmtRolloutResponseBody::getTotalTargets).setHeader("Target Count").setAutoWidth(true);
                        grid.addColumn(MgmtRolloutResponseBody::getTotalTargetsPerStatus).setHeader("Stats").setAutoWidth(true);
                        grid.addColumn(MgmtRolloutResponseBody::getStatus).setHeader("Status").setAutoWidth(true);

                        grid.addComponentColumn(rollout -> new Actions(rollout, grid, hawkbitClient)).setHeader("Actions").setAutoWidth(true);

                            grid.setItemDetailsRenderer(new ComponentRenderer<>(
                                    () -> details, RolloutDetails::setItem));
                        }
                },
                (query, rsqlFilter) -> hawkbitClient.getRolloutRestApi()
                        .getRollouts(
                                query.getOffset(), query.getPageSize(), "name:asc", rsqlFilter, null)
                        .getBody()
                        .getContent()
                        .stream(),
                selectionGrid -> new CreateDialog(hawkbitClient).result(),
                selectionGrid -> {
                    selectionGrid.getSelectedItems().forEach(
                            rollout -> hawkbitClient.getRolloutRestApi().delete(rollout.getRolloutId()));
                    selectionGrid.refreshGrid(false);
                    return CompletableFuture.completedFuture(null);
                });
    }

    private static SelectionGrid<MgmtRolloutGroupResponseBody, Long> createGroupGrid() {
        return new SelectionGrid<>(
                new SelectionGrid.EntityRepresentation<>(MgmtRolloutGroupResponseBody.class, MgmtRolloutGroupResponseBody::getRolloutGroupId) {
                    @Override
                    protected void addColumns(final Grid<MgmtRolloutGroupResponseBody> grid) {
                        grid.addColumn(MgmtRolloutGroupResponseBody::getRolloutGroupId).setHeader("Id").setAutoWidth(true);
                        grid.addColumn(MgmtRolloutGroupResponseBody::getName).setHeader("Name").setAutoWidth(true);
                        grid.addColumn(MgmtRolloutGroupResponseBody::getTotalTargets).setHeader("Target Count").setAutoWidth(true);
                        grid.addColumn(MgmtRolloutGroupResponseBody::getTotalTargetsPerStatus).setHeader("Stats").setAutoWidth(true);
                        grid.addColumn(MgmtRolloutGroupResponseBody::getStatus).setHeader("Status").setAutoWidth(true);
                    }
                });
    }

    private static class Actions extends HorizontalLayout {

        private final long rolloutId;
        private final Grid<MgmtRolloutResponseBody> grid;
        private final HawkbitClient hawkbitClient;

        private Actions(final MgmtRolloutResponseBody rollout, final Grid<MgmtRolloutResponseBody> grid, final HawkbitClient hawkbitClient) {
            this.rolloutId = rollout.getRolloutId();
            this.grid = grid;
            this.hawkbitClient = hawkbitClient;
            init(rollout);
        }

        private void init(final MgmtRolloutResponseBody rollout) {
            if ("READY".equalsIgnoreCase(rollout.getStatus())) {
                add(Utils.tooltip(new Button(VaadinIcon.START_COG.create()) {{
                    addClickListener(v -> {
                        hawkbitClient.getRolloutRestApi().start(rollout.getRolloutId());
                        refresh();
                    });
                }}, "Start"));
            } else if ("RUNNING".equalsIgnoreCase(rollout.getStatus())) {
                add(Utils.tooltip(new Button(VaadinIcon.PAUSE.create()) {{
                    addClickListener(v -> {
                        hawkbitClient.getRolloutRestApi().pause(rollout.getRolloutId());
                        refresh();
                    });
                }}, "Pause"));
            } else if ("PAUSED".equalsIgnoreCase(rollout.getStatus())) {
                add(Utils.tooltip(new Button(VaadinIcon.START_COG.create()) {{
                    addClickListener(v -> {
                        hawkbitClient.getRolloutRestApi().resume(rollout.getRolloutId());
                        refresh();
                    });
                }}, "Resume"));
            }
            add(Utils.tooltip(new Button(VaadinIcon.TRASH.create()) {{
                addClickListener(v -> {
                    hawkbitClient.getRolloutRestApi().delete(rollout.getRolloutId());
                    grid.getDataProvider().refreshAll();
                });
            }}, "Cancel and Remove"));
        }

        private void refresh() {
            removeAll();
            init(hawkbitClient.getRolloutRestApi().getRollout(rolloutId).getBody());
        }
    }

    private static class RolloutFilter implements Filter.Rsql {

        private final TextField name = Utils.textField("Name");

        private RolloutFilter(final HawkbitClient hawkbitClient) {
            name.setPlaceholder("<name filter>");
        }

        @Override
        public List<Component> components() {
            return List.of(name);
        }

        @Override
        public String filter() {
            return Filter.filter(Map.of("name", name.getOptionalValue()));
        }
    }

    private static class RolloutDetails extends FormLayout {

        private final HawkbitClient hawkbitClient;

        private final TextArea description = new TextArea("Description");
        private final TextField createdBy = Utils.textField("Created by");
        private final TextField createdAt = Utils.textField("Created at");
        private final TextField lastModifiedBy = Utils.textField("Last modified by");
        private final TextField lastModifiedAt = Utils.textField("Last modified at");
        private final TextField targetFilter = Utils.textField("Target Filter");
        private final TextField distributionSet = Utils.textField("Distribution Set");
        private final TextField actonType = Utils.textField("Action Type");
        private final TextField startAt = Utils.textField("Start At");
        private final SelectionGrid<MgmtRolloutGroupResponseBody, Long> groupGrid;

        private RolloutDetails(final HawkbitClient hawkbitClient) {
            this.hawkbitClient = hawkbitClient;

            description.setMinLength(2);
            groupGrid = createGroupGrid();
            Stream.of(
                    description,
                    createdBy, createdAt,
                    lastModifiedBy, lastModifiedAt,
                    targetFilter, distributionSet,
                    actonType, startAt)
                    .forEach(field -> {
                        field.setReadOnly(true);
                        add(field);
                    });
            add(groupGrid);

            setResponsiveSteps(new ResponsiveStep("0", 2));
            setColspan(description, 2);
            setColspan(groupGrid, 2);
        }

        private void setItem(final MgmtRolloutResponseBody rollout) {
            description.setValue(rollout.getDescription());
            createdBy.setValue(rollout.getCreatedBy());
            createdAt.setValue(new Date(rollout.getCreatedAt()).toString());
            lastModifiedBy.setValue(rollout.getLastModifiedBy());
            lastModifiedAt.setValue(new Date(rollout.getLastModifiedAt()).toString());
            targetFilter.setValue(rollout.getTargetFilterQuery());
            final MgmtDistributionSet distributionSetMgmt = hawkbitClient.getDistributionSetRestApi()
                    .getDistributionSet(rollout.getDistributionSetId()).getBody();
            distributionSet.setValue(distributionSetMgmt.getName() + ":" + distributionSetMgmt.getVersion());
            actonType.setValue(switch (rollout.getType()) {
                case SOFT -> "Soft";
                case FORCED -> "Forced";
                case DOWNLOAD_ONLY -> "Download Only";
                case TIMEFORCED -> "Scheduled at " + new Date(rollout.getForcetime());
            });
            startAt.setValue(ObjectUtils.isEmpty(rollout.getStartAt()) ? "" : new Date(rollout.getStartAt()).toString());

            groupGrid.setItems(query ->
                    hawkbitClient.getRolloutRestApi()
                            .getRolloutGroups(
                                    rollout.getRolloutId(),
                                    query.getOffset(), query.getPageSize(),
                                    null, null, null)
                            .getBody().getContent().stream()
                            .skip(query.getOffset())
                            .limit(query.getPageSize()));
            groupGrid.setSelectionMode(Grid.SelectionMode.NONE);
        }
    }

    private static class CreateDialog extends Utils.BaseDialog<Void> {

        private enum StartType {
            MANUAL, AUTO, SCHEDULED
        }

        private final TextField name;
        private final Select<MgmtDistributionSet> distributionSet;
        private final Select<MgmtTargetFilterQuery> targetFilter;
        private final TextArea description;
        private final Select<MgmtActionType> actionType;
        private final DateTimePicker forceTime = new DateTimePicker("Force Time");
        private final Select<StartType> startType;
        private final DateTimePicker startAt = new DateTimePicker("Start At");
        private final NumberField groupNumber;
        private final NumberField triggerThreshold;
        private final NumberField errorThreshold;

        private final Button create = new Button("Create");

        private CreateDialog(final HawkbitClient hawkbitClient) {
            super("Create Rollout");

            name = Utils.textField("Name", this::readyToCreate);
            name.focus();
            distributionSet = new Select<>(
                    "Distribution Set",
                    this::readyToCreate,
                    hawkbitClient.getDistributionSetRestApi()
                            .getDistributionSets(0, 30, "name:asc", null)
                            .getBody()
                            .getContent()
                            .toArray(new MgmtDistributionSet[0]));
            distributionSet.setRequiredIndicatorVisible(true);
            distributionSet.setItemLabelGenerator(distributionSet ->
                    distributionSet.getName() + ":" + distributionSet.getVersion());
            distributionSet.setWidthFull();
            targetFilter = new Select<>(
                    "Target Filter",
                    this::readyToCreate,
                    hawkbitClient.getTargetFilterQueryRestApi()
                            .getFilters(0, 30, "name:asc", null, null)
                            .getBody()
                            .getContent()
                            .toArray(new MgmtTargetFilterQuery[0]));
            targetFilter.setRequiredIndicatorVisible(true);
            targetFilter.setItemLabelGenerator(MgmtTargetFilterQuery::getName);
            targetFilter.setWidthFull();
            description = new TextArea("Description");
            description.setMinLength(2);
            description.setWidthFull();

            actionType = new Select<>();
            actionType.setLabel("Action Type");
            actionType.setItems(MgmtActionType.values());
            actionType.setValue(MgmtActionType.FORCED);
            final ComponentRenderer<Component, MgmtActionType> actionTypeRenderer = new ComponentRenderer<>(actionType ->
                    switch (actionType) {
                        case SOFT -> new Text("Soft");
                        case FORCED -> new Text("Forced");
                        case DOWNLOAD_ONLY -> new Text("Download Only");
                        case TIMEFORCED -> forceTime;
                    });
            actionType.addValueChangeListener(e -> actionType.setRenderer(actionTypeRenderer));
            actionType.setItemLabelGenerator(startType ->
                    switch (startType) {
                        case SOFT -> "Soft";
                        case FORCED -> "Forced";
                        case DOWNLOAD_ONLY -> "Download Only";
                        case TIMEFORCED -> "Time Forced at " + (forceTime.isEmpty() ? "" : " " + forceTime.getValue());
                    });
            actionType.setWidthFull();
            startType = new Select<>();
            startType.setValue(StartType.MANUAL);
            startType.setLabel("Start Type");
            startType.setItems(StartType.values());
            startType.setValue(StartType.MANUAL);
            final ComponentRenderer<Component, StartType> startTypeRenderer = new ComponentRenderer<>(startType ->
                    switch (startType) {
                        case MANUAL -> new Text("Manual");
                        case AUTO -> new Text("Auto");
                        case SCHEDULED -> startAt;
                    });
            startType.setRenderer(startTypeRenderer);
            startType.addValueChangeListener(e -> startType.setRenderer(startTypeRenderer));
            startType.setItemLabelGenerator(startType ->
                    switch (startType) {
                        case MANUAL -> "Manual";
                        case AUTO -> "Auto";
                        case SCHEDULED -> "Scheduled" + (startAt.isEmpty() ? "" : "  at " + startAt.getValue());
                    });
            startType.setWidthFull();

            final Div percentSuffix = new Div();
            percentSuffix.setText("%");
            groupNumber = Utils.numberField("Group number", this::readyToCreate);
            groupNumber.setMin(1);
            groupNumber.setValue(1.0);
            triggerThreshold = Utils.numberField("Trigger Threshold", this::readyToCreate);
            triggerThreshold.setMin(0);
            triggerThreshold.setMax(100);
            triggerThreshold.setValue(100.0);
            triggerThreshold.setSuffixComponent(percentSuffix);
            errorThreshold = Utils.numberField("Error Threshold", this::readyToCreate);
            errorThreshold.setMin(1);
            errorThreshold.setMax(100);
            errorThreshold.setValue(10.0);
            errorThreshold.setSuffixComponent(percentSuffix);

            create.setEnabled(false);
            addCreateClickListener(hawkbitClient);
            final Button cancel = Utils.tooltip(new Button("Cancel"), "Cancel (Esc)");
            cancel.addClickListener(e -> close());
            cancel.addClickShortcut(Key.ESCAPE);
            final HorizontalLayout actions = new HorizontalLayout(create, cancel);
            actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            actions.setSizeFull();

            final VerticalLayout layout = new VerticalLayout();
            layout.setSizeFull();
            layout.setSpacing(false);
            layout.add(
                    name, distributionSet, targetFilter, description,
                    actionType, startType,
                    groupNumber, triggerThreshold, errorThreshold,
                    actions);
            add(layout);
            open();
        }

        private void readyToCreate(final Object v) {
            final boolean createEnabled = !name.isEmpty() &&
                    !distributionSet.isEmpty() &&
                    !targetFilter.isEmpty() &&
                    !groupNumber.isEmpty() &&
                    !triggerThreshold.isEmpty() &&
                    !errorThreshold.isEmpty();
            if (create.isEnabled() != createEnabled) {
                create.setEnabled(createEnabled);
            }
        }

        private void addCreateClickListener(final HawkbitClient hawkbitClient) {
            create.addClickListener(e -> {
                close();
                final MgmtRolloutRestRequestBody request = new MgmtRolloutRestRequestBody();
                request.setName(name.getValue());
                request.setDistributionSetId(distributionSet.getValue().getDsId());
                request.setTargetFilterQuery(targetFilter.getValue().getName());
                request.setDescription(description.getValue());

                request.setType(actionType.getValue());
                if (actionType.getValue() == MgmtActionType.FORCED) {
                    request.setForcetime(forceTime.getValue().toEpochSecond(ZoneOffset.UTC) * 1000);
                }
                switch (startType.getValue()) {
                    case AUTO -> request.setStartAt(System.currentTimeMillis());
                    case SCHEDULED -> request.setStartAt(startAt.getValue().toEpochSecond(ZoneOffset.UTC) * 1000);
                } // else - manual, do not start

                request.setAmountGroups(groupNumber.getValue().intValue());
                request.setSuccessCondition(
                        new MgmtRolloutCondition(
                                MgmtRolloutCondition.Condition.THRESHOLD,
                                triggerThreshold.getValue().intValue() + "%"));
                request.setErrorCondition(
                        new MgmtRolloutCondition(
                                MgmtRolloutCondition.Condition.THRESHOLD,
                                errorThreshold.getValue().intValue() + "%"));
                request.setErrorAction(
                        new MgmtRolloutErrorAction(
                                MgmtRolloutErrorAction.ErrorAction.PAUSE, ""));
                hawkbitClient.getRolloutRestApi().create(request).getBody();
            });
        }
    }
}
