/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.builder;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Rollout.RolloutStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.RolloutGroup.RolloutGroupStatus;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyAction;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyAction.IsActiveDecoration;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyUploadProgress.ProgressSatus;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.rollout.ProxyFontIcon;
import org.eclipse.hawkbit.ui.rollout.RolloutManagementUIState;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.StringUtils;

import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.FontIcon;
import com.vaadin.ui.Label;

/**
 *
 * Generate labels with icons according to entities' states
 *
 */
public final class StatusIconBuilder {

    private StatusIconBuilder() {
    }

    /**
     * Generate labels with icons according to entities' {@link Status}
     *
     * @param <E>
     *            entity type
     */
    public static class ActionStatusIconSupplier<E extends ProxyIdentifiableEntity>
            extends EntityStatusIconBuilderWithGenetaredTooltip<Status, E> {
        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param getEntityStatus
         *            function to get the entitie's state
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public ActionStatusIconSupplier(final VaadinMessageSource i18n, final Function<E, Status> getEntityStatus,
                final String labelIdPrefix) {
            super(i18n, Status.class, getEntityStatus, labelIdPrefix,
                    status -> UIMessageIdProvider.TOOLTIP_ACTION_STATUS_PREFIX + status.toString().toLowerCase());
            addMapping(Status.FINISHED, VaadinIcons.CHECK_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_GREEN);
            addMapping(Status.SCHEDULED, VaadinIcons.HOURGLASS_EMPTY, SPUIStyleDefinitions.STATUS_ICON_PENDING);
            addMapping(Status.RUNNING, VaadinIcons.ADJUST, SPUIStyleDefinitions.STATUS_ICON_PENDING);
            addMapping(Status.RETRIEVED, VaadinIcons.CHECK_CIRCLE_O, SPUIStyleDefinitions.STATUS_ICON_PENDING);
            addMapping(Status.WARNING, VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_ORANGE);
            addMapping(Status.DOWNLOAD, VaadinIcons.CLOUD_DOWNLOAD, SPUIStyleDefinitions.STATUS_ICON_PENDING);
            addMapping(Status.DOWNLOADED, VaadinIcons.CLOUD_DOWNLOAD, SPUIStyleDefinitions.STATUS_ICON_GREEN);
            addMapping(Status.CANCELING, VaadinIcons.CLOSE_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_PENDING);
            addMapping(Status.CANCEL_REJECTED, VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_ORANGE);
            addMapping(Status.CANCELED, VaadinIcons.CLOSE_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_GREEN);
            addMapping(Status.ERROR, VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED);
            addMapping(Status.WAIT_FOR_CONFIRMATION, VaadinIcons.USER_CLOCK, SPUIStyleDefinitions.STATUS_ICON_PENDING);

        }
    }

    /**
     * Generate labels with icons according to entities' {@link RolloutStatus}
     *
     * @param <E>
     *            entity type
     */
    public static class RolloutStatusIconSupplier<E extends ProxyIdentifiableEntity>
            extends EntityStatusIconBuilderWithGenetaredTooltip<RolloutStatus, E> {
        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param getEntityStatus
         *            function to get the entitie's state
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public RolloutStatusIconSupplier(final VaadinMessageSource i18n,
                final Function<E, RolloutStatus> getEntityStatus, final String labelIdPrefix) {
            super(i18n, RolloutStatus.class, getEntityStatus, labelIdPrefix,
                    status -> UIMessageIdProvider.TOOLTIP_ROLLOUT_STATUS_PREFIX + status.toString().toLowerCase());
            addMapping(RolloutStatus.FINISHED, VaadinIcons.CHECK_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_GREEN);
            addMapping(RolloutStatus.PAUSED, VaadinIcons.PAUSE, SPUIStyleDefinitions.STATUS_ICON_BLUE);
            addMapping(RolloutStatus.RUNNING, null, SPUIStyleDefinitions.STATUS_SPINNER_YELLOW);
            addMapping(RolloutStatus.WAITING_FOR_APPROVAL, VaadinIcons.HOURGLASS,
                    SPUIStyleDefinitions.STATUS_ICON_ORANGE);
            addMapping(RolloutStatus.APPROVAL_DENIED, VaadinIcons.CLOSE_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED);
            addMapping(RolloutStatus.READY, VaadinIcons.BULLSEYE, SPUIStyleDefinitions.STATUS_ICON_LIGHT_BLUE);
            addMapping(RolloutStatus.STOPPED, VaadinIcons.STOP, SPUIStyleDefinitions.STATUS_ICON_RED);
            addMapping(RolloutStatus.CREATING, null, SPUIStyleDefinitions.STATUS_SPINNER_GREY);
            addMapping(RolloutStatus.STARTING, null, SPUIStyleDefinitions.STATUS_SPINNER_BLUE);
            addMapping(RolloutStatus.DELETING, null, SPUIStyleDefinitions.STATUS_SPINNER_RED);
            addMapping(RolloutStatus.ERROR_CREATING, VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED);
            addMapping(RolloutStatus.ERROR_DELETING, VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED);
            addMapping(RolloutStatus.ERROR_STARTING, VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED);
        }
    }

    /**
     * Generate labels with icons according to entities'
     * {@link RolloutGroupStatus}
     *
     * @param <E>
     *            entity type
     */
    public static class RolloutGroupStatusIconSupplier<E extends ProxyIdentifiableEntity>
            extends EntityStatusIconBuilderWithGenetaredTooltip<RolloutGroupStatus, E> {
        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param getEntityStatus
         *            function to get the entitie's state
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public RolloutGroupStatusIconSupplier(final VaadinMessageSource i18n,
                final Function<E, RolloutGroupStatus> getEntityStatus, final String labelIdPrefix) {
            super(i18n, RolloutGroupStatus.class, getEntityStatus, labelIdPrefix,
                    status -> UIMessageIdProvider.TOOLTIP_ROLLOUT_GROUP_STATUS_PREFIX
                            + status.toString().toLowerCase());
            addMapping(RolloutGroupStatus.FINISHED, VaadinIcons.CHECK_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_GREEN);
            addMapping(RolloutGroupStatus.RUNNING, VaadinIcons.ADJUST, SPUIStyleDefinitions.STATUS_ICON_YELLOW);
            addMapping(RolloutGroupStatus.READY, VaadinIcons.BULLSEYE, SPUIStyleDefinitions.STATUS_ICON_LIGHT_BLUE);
            addMapping(RolloutGroupStatus.ERROR, VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED);
            addMapping(RolloutGroupStatus.SCHEDULED, VaadinIcons.HOURGLASS_START,
                    SPUIStyleDefinitions.STATUS_ICON_PENDING);
        }
    }

    /**
     * Generate labels with icons according to entities' {@link Status} and the
     * current {@link RolloutGroup}
     *
     * @param <E>
     *            entity type
     */
    public static class RolloutActionStatusIconSupplier<E extends ProxyIdentifiableEntity>
            extends ActionStatusIconSupplier<E> {
        private static final long serialVersionUID = 1L;
        private final transient RolloutGroupManagement rolloutGroupManagement;
        private final RolloutManagementUIState rolloutManagementUIState;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param getEntityStatus
         *            function to get the entitie's state
         * @param labelIdPrefix
         *            to generate the label ID
         * @param rolloutGroupManagement
         *            management to get the current {@link RolloutGroup}
         * @param rolloutManagementUIState
         *            UI state {@link RolloutGroup}
         */
        public RolloutActionStatusIconSupplier(final VaadinMessageSource i18n,
                final Function<E, Status> getEntityStatus, final String labelIdPrefix,
                final RolloutGroupManagement rolloutGroupManagement,
                final RolloutManagementUIState rolloutManagementUIState) {
            super(i18n, getEntityStatus, labelIdPrefix);
            this.rolloutGroupManagement = rolloutGroupManagement;
            this.rolloutManagementUIState = rolloutManagementUIState;
        }

        @Override
        public Label getLabel(final E entity) {
            final Optional<RolloutGroup> group = rolloutGroupManagement
                    .get(rolloutManagementUIState.getSelectedRolloutGroupId());
            final Optional<ProxyFontIcon> optionalIcon = getIcon(entity);

            final ProxyFontIcon icon;
            if (optionalIcon.isPresent()) {
                icon = getFontIconFromStatusMap(optionalIcon.get(), getEntityStatus.apply(entity), group.orElse(null));
            } else {
                icon = buildStatusIcon(group.orElse(null));
            }

            return getLabel(entity, icon);
        }

        private ProxyFontIcon getFontIconFromStatusMap(final ProxyFontIcon foundIcon, final Status status,
                final RolloutGroup group) {
            if (Status.DOWNLOADED == status && isDownloadOnly(group)) {
                return getIconMap().get(Status.FINISHED);
            }

            return foundIcon;
        }

        private static boolean isDownloadOnly(final RolloutGroup group) {
            if (group == null) {
                return false;
            }
            return ActionType.DOWNLOAD_ONLY == group.getRollout().getActionType();
        }

        // Actions are not created for targets when rollout's status is
        // READY and when duplicate assignment is done. In these cases
        // display a appropriate status with description
        private ProxyFontIcon buildStatusIcon(final RolloutGroup rolloutGroup) {
            if (rolloutGroup != null && rolloutGroup.getStatus() == RolloutGroupStatus.READY) {
                return new ProxyFontIcon(VaadinIcons.BULLSEYE, SPUIStyleDefinitions.STATUS_ICON_LIGHT_BLUE,
                        i18n.getMessage(UIMessageIdProvider.TOOLTIP_ROLLOUT_GROUP_STATUS_PREFIX
                                + RolloutGroupStatus.READY.toString().toLowerCase()));
            } else if (rolloutGroup != null && rolloutGroup.getStatus() == RolloutGroupStatus.FINISHED) {
                final DistributionSet dist = rolloutGroup.getRollout().getDistributionSet();
                final String ds = HawkbitCommonUtil.getFormattedNameVersion(dist.getName(), dist.getVersion());
                if (dist.isValid()) {
                    return new ProxyFontIcon(VaadinIcons.MINUS_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_BLUE,
                            i18n.getMessage(UIMessageIdProvider.MESSAGE_DISTRIBUTION_ASSIGNED, ds));
                } else {
                    // invalidated ds, finished rollout but ds wasn't assigned
                    // to target
                    return new ProxyFontIcon(VaadinIcons.BAN, SPUIStyleDefinitions.STATUS_ICON_BLUE,
                            i18n.getMessage(UIMessageIdProvider.MESSAGE_DISTRIBUTION_NOT_ASSIGNED, ds));
                }
            } else {
                return generateUnknwonStateIcon();
            }
        }

    }

    /**
     * Generate labels with icons according to entities'
     * {@link IsActiveDecoration}
     *
     * @param <E>
     *            entity type
     */
    public static class ActiveStatusIconSupplier<E extends ProxyIdentifiableEntity>
            extends EntityStatusIconBuilderWithGenetaredTooltip<IsActiveDecoration, E> {
        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param getEntityStatus
         *            function to get the entitie's state
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public ActiveStatusIconSupplier(final VaadinMessageSource i18n,
                final Function<E, IsActiveDecoration> getEntityStatus, final String labelIdPrefix) {
            super(i18n, IsActiveDecoration.class, getEntityStatus, labelIdPrefix,
                    status -> UIMessageIdProvider.TOOLTIP_ACTIVE_ACTION_STATUS_PREFIX + status.getMsgName());
            addMapping(IsActiveDecoration.ACTIVE, null, SPUIStyleDefinitions.STATUS_ICON_ACTIVE);
            addMapping(IsActiveDecoration.SCHEDULED, VaadinIcons.HOURGLASS_EMPTY,
                    SPUIStyleDefinitions.STATUS_ICON_PENDING);
            addMapping(IsActiveDecoration.IN_ACTIVE, VaadinIcons.CHECK_CIRCLE,
                    SPUIStyleDefinitions.STATUS_ICON_NEUTRAL);
            addMapping(IsActiveDecoration.IN_ACTIVE_ERROR, VaadinIcons.CHECK_CIRCLE,
                    SPUIStyleDefinitions.STATUS_ICON_RED);
        }
    }

    /**
     * Generate labels with icons according to entities' {@link ActionType}
     *
     * @param <E>
     *            entity type
     */
    public static class ActionTypeIconSupplier<E extends ProxyIdentifiableEntity>
            extends EntityStatusIconBuilder<ActionType, E> {
        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param getEntityStatus
         *            function to get the entitie's state
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public ActionTypeIconSupplier(final VaadinMessageSource i18n, final Function<E, ActionType> getEntityStatus,
                final String labelIdPrefix) {
            super(i18n, ActionType.class, getEntityStatus, labelIdPrefix);
            addMapping(ActionType.FORCED, VaadinIcons.BOLT, SPUIStyleDefinitions.STATUS_ICON_FORCED,
                    UIMessageIdProvider.CAPTION_ACTION_FORCED);
            addMapping(ActionType.TIMEFORCED, VaadinIcons.BOLT, SPUIStyleDefinitions.STATUS_ICON_FORCED,
                    UIMessageIdProvider.CAPTION_ACTION_FORCED);
            addMapping(ActionType.SOFT, VaadinIcons.STEP_FORWARD, SPUIStyleDefinitions.STATUS_ICON_SOFT,
                    UIMessageIdProvider.CAPTION_ACTION_SOFT);
            addMapping(ActionType.DOWNLOAD_ONLY, VaadinIcons.DOWNLOAD, SPUIStyleDefinitions.STATUS_ICON_DOWNLOAD_ONLY,
                    UIMessageIdProvider.CAPTION_ACTION_DOWNLOAD_ONLY);
        }
    }

    /**
     * Generate labels with confirmation icons according to entity' {@link ProxyTargetFilterQuery}
     */
    public static class ConfirmationIconSupplier extends AbstractEntityStatusIconBuilder<ProxyTargetFilterQuery> {

        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public ConfirmationIconSupplier(final VaadinMessageSource i18n, final String labelIdPrefix) {
            super(i18n, labelIdPrefix);
        }

        @Override
        public Label getLabel(final ProxyTargetFilterQuery entity) {
            final ProxyFontIcon icon;
            if (entity.isAutoAssignmentEnabled() && entity.getDistributionSetInfo() != null) {
                icon = entity.isConfirmationRequired()
                        ? new ProxyFontIcon(VaadinIcons.USER_CLOCK, SPUIStyleDefinitions.STATUS_ICON_GREEN,
                                i18n.getMessage(UIMessageIdProvider.TOOLTIP_TARGET_FILTER_CONFIRMATION_REQUIRED))
                        : new ProxyFontIcon(VaadinIcons.USER_CHECK, SPUIStyleDefinitions.STATUS_ICON_RED,
                                i18n.getMessage(UIMessageIdProvider.TOOLTIP_TARGET_FILTER_CONFIRMATION_NOT_REQUIRED));
            } else {
                icon = new ProxyFontIcon(VaadinIcons.MINUS_CIRCLE_O, SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                        i18n.getMessage(UIMessageIdProvider.TOOLTIP_TARGET_FILTER_CONFIRMATION_NOT_CONFIGURED));
            }

            return getLabel(entity, icon);
        }
    }

    /**
     * Generate labels with icons according to entities'
     * {@link TargetUpdateStatus}
     *
     * @param <E>
     *            entity type
     */
    public static class TargetStatusIconSupplier<E extends ProxyIdentifiableEntity>
            extends EntityStatusIconBuilderWithGenetaredTooltip<TargetUpdateStatus, E> {
        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param getEntityStatus
         *            function to get the entitie's state
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public TargetStatusIconSupplier(final VaadinMessageSource i18n,
                final Function<E, TargetUpdateStatus> getEntityStatus, final String labelIdPrefix) {
            super(i18n, TargetUpdateStatus.class, getEntityStatus, labelIdPrefix,
                    status -> UIMessageIdProvider.TOOLTIP_TARGET_STATUS_PREFIX + status.toString().toLowerCase());
            addMapping(TargetUpdateStatus.ERROR, VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED);
            addMapping(TargetUpdateStatus.UNKNOWN, VaadinIcons.QUESTION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_BLUE);
            addMapping(TargetUpdateStatus.IN_SYNC, VaadinIcons.CHECK_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_GREEN);
            addMapping(TargetUpdateStatus.PENDING, VaadinIcons.ADJUST, SPUIStyleDefinitions.STATUS_ICON_YELLOW);
            addMapping(TargetUpdateStatus.REGISTERED, VaadinIcons.DOT_CIRCLE,
                    SPUIStyleDefinitions.STATUS_ICON_LIGHT_BLUE);
        }
    }

    /**
     * Generate labels with icons according to entities' {@link ProgressSatus}
     *
     * @param <E>
     *            entity type
     */
    public static class ProgressStatusIconSupplier<E extends ProxyIdentifiableEntity>
            extends EntityStatusIconBuilderWithGenetaredTooltip<ProgressSatus, E> {
        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param getEntityStatus
         *            function to get the entitie's state
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public ProgressStatusIconSupplier(final VaadinMessageSource i18n,
                final Function<E, ProgressSatus> getEntityStatus, final String labelIdPrefix) {
            super(i18n, ProgressSatus.class, getEntityStatus, labelIdPrefix,
                    status -> UIMessageIdProvider.TOOLTIP_UPLOAD_STATUS_PREFIX + status.toString().toLowerCase());
            addMapping(ProgressSatus.INPROGRESS, VaadinIcons.ADJUST, SPUIStyleDefinitions.STATUS_ICON_YELLOW);
            addMapping(ProgressSatus.FINISHED, VaadinIcons.CHECK_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_GREEN);
            addMapping(ProgressSatus.FAILED, VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_RED);
        }
    }

    /**
     * Generate labels with icons according to {@link ProxyTarget}s'
     * PollStatusToolTip
     */
    public static class TargetPollingStatusIconSupplier extends AbstractEntityStatusIconBuilder<ProxyTarget> {
        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public TargetPollingStatusIconSupplier(final VaadinMessageSource i18n, final String labelIdPrefix) {
            super(i18n, labelIdPrefix);
        }

        @Override
        public Label getLabel(final ProxyTarget entity) {
            final String pollStatusToolTip = entity.getPollStatusToolTip();
            final ProxyFontIcon icon = StringUtils.hasText(pollStatusToolTip)
                    ? new ProxyFontIcon(VaadinIcons.EXCLAMATION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                            pollStatusToolTip)
                    : new ProxyFontIcon(VaadinIcons.CLOCK, SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                            i18n.getMessage(UIMessageIdProvider.TOOLTIP_IN_TIME));
            return getLabel(entity, icon);
        }
    }

    /**
     * Generate labels with icons according to {@link ProxyAction}s' timeforced
     * status and current time
     */
    public static class TimeforcedIconSupplier extends AbstractEntityStatusIconBuilder<ProxyAction> {
        private static final long serialVersionUID = 1L;

        /**
         * constructor
         *
         * @param i18n
         *            message source for internationalization
         * @param labelIdPrefix
         *            to generate the label ID
         */
        public TimeforcedIconSupplier(final VaadinMessageSource i18n, final String labelIdPrefix) {
            super(i18n, labelIdPrefix);
        }

        @Override
        public Label getLabel(final ProxyAction action) {
            if (ActionType.TIMEFORCED != action.getActionType()) {
                return null;
            }

            final long currentTimeMillis = System.currentTimeMillis();
            final String style;
            final String description;
            if (action.isHitAutoForceTime(currentTimeMillis)) {
                style = SPUIStyleDefinitions.STATUS_ICON_GREEN;
                final String duration = SPDateTimeUtil.getDurationFormattedString(action.getForcedTime(),
                        currentTimeMillis, i18n);
                description = i18n.getMessage(UIMessageIdProvider.TOOLTIP_TIMEFORCED_FORCED_SINCE, duration);
            } else {
                style = SPUIStyleDefinitions.STATUS_ICON_PENDING;
                final String duration = SPDateTimeUtil.getDurationFormattedString(currentTimeMillis,
                        action.getForcedTime(), i18n);
                description = i18n.getMessage(UIMessageIdProvider.TOOLTIP_TIMEFORCED_FORCED_IN, duration);
            }

            final ProxyFontIcon icon = new ProxyFontIcon(VaadinIcons.TIMER, style, description);
            return getLabel(action, icon);
        }
    }

    private static class EntityStatusIconBuilderWithGenetaredTooltip<T extends Enum<T>, E extends ProxyIdentifiableEntity>
            extends EntityStatusIconBuilder<T, E> {
        private static final long serialVersionUID = 1L;

        private final transient Function<T, String> tooltipGenerator;

        protected EntityStatusIconBuilderWithGenetaredTooltip(final VaadinMessageSource i18n, final Class<T> statusType,
                final Function<E, T> getEntityStatus, final String labelIdPrefix,
                final Function<T, String> tooltipGenerator) {
            super(i18n, statusType, getEntityStatus, labelIdPrefix);
            this.tooltipGenerator = tooltipGenerator;
        }

        protected void addMapping(final T status, final FontIcon icon, final String style) {
            addMapping(status, icon, style, tooltipGenerator.apply(status));
        }
    }

    private static class EntityStatusIconBuilder<T extends Enum<T>, E extends ProxyIdentifiableEntity>
            extends AbstractEntityStatusIconBuilder<E> {
        private static final long serialVersionUID = 1L;

        private final Map<T, ProxyFontIcon> iconMap;
        protected final transient Function<E, T> getEntityStatus;

        protected EntityStatusIconBuilder(final VaadinMessageSource i18n, final Class<T> statusType,
                final Function<E, T> getEntityStatus, final String labelIdPrefix) {
            super(i18n, labelIdPrefix);
            this.iconMap = new EnumMap<>(statusType);
            this.getEntityStatus = getEntityStatus;
        }

        protected void addMapping(final T status, final FontIcon icon, final String style, final String tooltip) {
            iconMap.put(status, new ProxyFontIcon(icon, style, i18n.getMessage(tooltip)));
        }

        @Override
        public Label getLabel(final E entity) {
            final ProxyFontIcon icon = getIcon(entity).orElse(generateUnknwonStateIcon());
            return getLabel(entity, icon);
        }

        protected Optional<ProxyFontIcon> getIcon(final E entity) {
            final T status = getEntityStatus.apply(entity);
            if (status != null) {
                return Optional.ofNullable(iconMap.getOrDefault(status, null));
            }
            return Optional.empty();
        }

        protected Map<T, ProxyFontIcon> getIconMap() {
            return iconMap;
        }

    }

    private abstract static class AbstractEntityStatusIconBuilder<E extends ProxyIdentifiableEntity>
            implements Serializable {
        private static final long serialVersionUID = 1L;

        protected final VaadinMessageSource i18n;
        protected final String labelIdPrefix;

        protected AbstractEntityStatusIconBuilder(final VaadinMessageSource i18n, final String labelIdPrefix) {
            this.i18n = i18n;
            this.labelIdPrefix = labelIdPrefix;
        }

        protected Label getLabel(final E entity, final ProxyFontIcon icon) {
            final String entityStatusId = new StringBuilder(labelIdPrefix).append(".").append(entity.getId())
                    .toString();
            return SPUIComponentProvider.getLabelIcon(icon, entityStatusId);
        }

        protected ProxyFontIcon generateUnknwonStateIcon() {
            return new ProxyFontIcon(VaadinIcons.QUESTION_CIRCLE, SPUIStyleDefinitions.STATUS_ICON_BLUE,
                    i18n.getMessage(UIMessageIdProvider.LABEL_UNKNOWN));
        }

        /**
         * Generate a label from the entity according to its state
         *
         * @param entity
         *            to read the state from
         * @return the label
         */
        public abstract Label getLabel(final E entity);
    }

}
