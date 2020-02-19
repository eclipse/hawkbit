/**
 * Copyright (c) Siemens AG, 2018
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.hawkbit.ui.management.miscs;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.MaintenanceScheduleHelper;
import org.eclipse.hawkbit.repository.exception.InvalidMaintenanceScheduleException;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.ui.common.builder.LabelBuilder;
import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.cronutils.descriptor.CronDescriptor;
import com.vaadin.data.Validator;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.event.FieldEvents.TextChangeListener;
import com.vaadin.server.Page;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

/**
 * {@link MaintenanceWindowLayout} defines UI layout that is used to specify the
 * maintenance schedule while assigning distribution set(s) to the target(s).
 */
public class MaintenanceWindowLayout extends VerticalLayout {
    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;

    private static final String CRON_VALIDATION_ERROR = "message.maintenancewindow.schedule.validation.error";

    private TextField schedule;
    private TextField duration;
    private ComboBox timeZone;
    private Label scheduleTranslator;

    /**
     * Constructor for the control to specify the maintenance schedule.
     *
     * @param i18n
     *            (@link VaadinMessageSource} to get the localized resource
     *            strings.
     */
    public MaintenanceWindowLayout(final VaadinMessageSource i18n) {

        this.i18n = i18n;

        createMaintenanceScheduleControl();
        createMaintenanceDurationControl();
        createMaintenanceTimeZoneControl();
        createMaintenanceScheduleTranslatorControl();

        final HorizontalLayout controlContainer = new HorizontalLayout();
        controlContainer.addComponent(schedule);
        controlContainer.addComponent(duration);
        controlContainer.addComponent(timeZone);
        addComponent(controlContainer);

        addComponent(scheduleTranslator);

        setStyleName("dist-window-maintenance-window-layout");
        setId(UIComponentIdProvider.MAINTENANCE_WINDOW_LAYOUT_ID);
    }

    /**
     * Text field to specify the schedule.
     */
    private void createMaintenanceScheduleControl() {
        schedule = new TextFieldBuilder(Action.MAINTENANCE_WINDOW_SCHEDULE_LENGTH)
                .id(UIComponentIdProvider.MAINTENANCE_WINDOW_SCHEDULE_ID)
                .caption(i18n.getMessage("caption.maintenancewindow.schedule")).validator(new CronValidator())
                .prompt("0 0 3 ? * 6").required(true, i18n).buildTextComponent();
        schedule.addTextChangeListener(new CronTranslationListener());
    }

    /**
     * Validates if the maintenance schedule is a valid cron expression.
     */
    private class CronValidator implements Validator {
        private static final long serialVersionUID = 1L;

        // Exception squid:S1166 - Vaadin validation class,
        // InvalidValueException,
        // doesn't have the constructor to pass throwable, but shows the
        // validation
        // errors to the user
        @SuppressWarnings("squid:S1166")
        @Override
        public void validate(final Object value) {
            try {
                MaintenanceScheduleHelper.validateCronSchedule((String) value);
            } catch (final InvalidMaintenanceScheduleException e) {
                throw new InvalidValueException(i18n.getMessage(CRON_VALIDATION_ERROR) + ": " + e.getMessage());
            }
        }
    }

    /**
     * Used for cron expression translation.
     */
    private class CronTranslationListener implements TextChangeListener {
        private static final long serialVersionUID = 1L;

        private final transient CronDescriptor cronDescriptor;

        public CronTranslationListener() {
            cronDescriptor = CronDescriptor.instance(getClientsLocale());
        }

        @Override
        public void textChange(final TextChangeEvent event) {
            scheduleTranslator.setValue(translateCron(event.getText()));
        }

        // Exception squid:S1166 - when the format of the cron expression is not
        // valid, the hint is shown to provide the valid one
        @SuppressWarnings("squid:S1166")
        private String translateCron(final String cronExpression) {
            try {
                return cronDescriptor.describe(MaintenanceScheduleHelper.getCronFromExpression(cronExpression));
            } catch (final IllegalArgumentException ex) {
                return i18n.getMessage(CRON_VALIDATION_ERROR);
            }
        }

        private Locale getClientsLocale() {
            return Page.getCurrent().getWebBrowser().getLocale();
        }
    }

    /**
     * Text field to specify the duration.
     */
    private void createMaintenanceDurationControl() {
        duration = new TextFieldBuilder(Action.MAINTENANCE_WINDOW_DURATION_LENGTH)
                .id(UIComponentIdProvider.MAINTENANCE_WINDOW_DURATION_ID)
                .caption(i18n.getMessage("caption.maintenancewindow.duration")).validator(new DurationValidator())
                .prompt("hh:mm:ss").required(true, i18n).buildTextComponent();
    }

    /**
     * Validates if the duration is specified in expected format.
     */
    private class DurationValidator implements Validator {
        private static final long serialVersionUID = 1L;

        // Exception squid:S1166 - Vaadin validation class,
        // InvalidValueException,
        // doesn't have the constructor to pass throwable, but shows the
        // validation
        // errors to the user
        @SuppressWarnings("squid:S1166")
        @Override
        public void validate(final Object value) {
            try {
                MaintenanceScheduleHelper.validateDuration((String) value);
            } catch (final InvalidMaintenanceScheduleException e) {
                throw new InvalidValueException(i18n.getMessage("message.maintenancewindow.duration.validation.error",
                        e.getDurationErrorIndex()));
            }
        }
    }

    /**
     * Combo box to pick the time zone offset.
     */
    private void createMaintenanceTimeZoneControl() {
        // ComboBoxBuilder cannot be used here, because Builder do
        // 'comboBox.setItemCaptionPropertyId(SPUILabelDefinitions.VAR_NAME);'
        // which interferes our code: 'timeZone.addItems(getAllTimeZones());'
        timeZone = new ComboBox();
        timeZone.setId(UIComponentIdProvider.MAINTENANCE_WINDOW_TIME_ZONE_ID);
        timeZone.setCaption(i18n.getMessage("caption.maintenancewindow.timezone"));
        timeZone.addItems(getAllTimeZones());
        timeZone.setValue(getClientTimeZone());
        timeZone.addStyleName(ValoTheme.COMBOBOX_SMALL);
        timeZone.setTextInputAllowed(false);
        timeZone.setNullSelectionAllowed(false);
    }

    /**
     * Get list of all time zone offsets supported.
     */
    private static List<String> getAllTimeZones() {
        return ZoneId.getAvailableZoneIds().stream()
                .map(id -> ZonedDateTime.now(ZoneId.of(id)).getOffset().getId().replace("Z", "+00:00"))
                .distinct().sorted().collect(Collectors.toList());
    }

    /**
     * Get time zone of the browser client to be used as default.
     */
    private static String getClientTimeZone() {
        return ZonedDateTime.now(SPDateTimeUtil.getTimeZoneId(SPDateTimeUtil.getBrowserTimeZone())).getOffset().getId()
                .replaceAll("Z", "+00:00");
    }

    /**
     * Label to translate the cron schedule to human readable format.
     */
    private void createMaintenanceScheduleTranslatorControl() {
        scheduleTranslator = new LabelBuilder().id(UIComponentIdProvider.MAINTENANCE_WINDOW_SCHEDULE_TRANSLATOR_ID)
                .name(i18n.getMessage(CRON_VALIDATION_ERROR)).buildLabel();
        scheduleTranslator.addStyleName(ValoTheme.LABEL_TINY);
    }

    /**
     * Get the cron expression for maintenance schedule.
     *
     * @return {@link String}.
     */
    public String getMaintenanceSchedule() {
        return schedule.getValue();
    }

    /**
     * Get the maintenance window duration.
     *
     * @return {@link String}.
     */
    public String getMaintenanceDuration() {
        return duration.getValue();
    }

    /**
     * Get the timezone for maintenance window.
     *
     * @return {@link String}.
     */

    public String getMaintenanceTimeZone() {
        return timeZone.getValue().toString();
    }

    /**
     * Set all the controls to their default values.
     */
    public void clearAllControls() {
        schedule.setValue("");
        duration.setValue("");
        timeZone.setValue(getClientTimeZone());
        scheduleTranslator.setValue(i18n.getMessage(CRON_VALIDATION_ERROR));
    }

    /**
     * Method, used for validity check, when schedule text is changed.
     *
     * @param event
     *            (@link TextChangeEvent} the event object after schedule text
     *            change.
     * @return validity of maintenance window controls.
     */
    public boolean onScheduleChange(final TextChangeEvent event) {
        schedule.setValue(event.getText());
        return isScheduleAndDurationValid();
    }

    /**
     * Method, used for validity check, when duration text is changed.
     *
     * @param event
     *            (@link TextChangeEvent} the event object after duration text
     *            change.
     * @return validity of maintenance window controls.
     */
    public boolean onDurationChange(final TextChangeEvent event) {
        duration.setValue(event.getText());
        return isScheduleAndDurationValid();
    }

    public boolean isScheduleAndDurationValid() {
        if (schedule.isEmpty() || duration.isEmpty()) {
            return false;
        }

        return schedule.isValid() && duration.isValid();
    }

    public TextField getScheduleControl() {
        return schedule;
    }

    public TextField getDurationControl() {
        return duration;
    }
}
