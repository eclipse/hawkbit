/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.utils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.eclipse.hawkbit.repository.model.AbstractAssignmentResult;
import org.eclipse.hawkbit.repository.model.NamedEntity;
import org.eclipse.hawkbit.repository.model.PollStatus;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.UiProperties.Localization;
import org.eclipse.hawkbit.ui.rollout.StatusFontIcon;
import org.springframework.util.StringUtils;
import org.vaadin.addons.lazyquerycontainer.AbstractBeanQuery;
import org.vaadin.addons.lazyquerycontainer.BeanQueryFactory;
import org.vaadin.addons.lazyquerycontainer.LazyQueryContainer;
import org.vaadin.addons.lazyquerycontainer.LazyQueryDefinition;

import com.vaadin.data.Container;
import com.vaadin.data.Item;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.UI;

/**
 * Common util class.
 */
public final class HawkbitCommonUtil {
    public static final String SP_STRING_PIPE = " | ";

    public static final String HTML_LI_CLOSE_TAG = "</li>";
    public static final String HTML_LI_OPEN_TAG = "<li>";
    public static final String HTML_UL_CLOSE_TAG = "</ul>";
    public static final String HTML_UL_OPEN_TAG = "<ul>";

    public static final String DIV_DESCRIPTION_START = "<div id=\"desc-length\"><p id=\"desciption-p\">";
    public static final String DIV_DESCRIPTION_END = "</p></div>";

    private static final String SM_HIGHLIGHT_CREATE_SCRIPT = "smHighlight = document.createElement('style'); smHighlight.id=\"sm-table-highlight\";  document.head.appendChild(smHighlight); ";
    private static final String SM_HIGHLIGHT_REMOVE_SCRIPT = "var y = document.getElementById('sm-table-highlight'); if(y) { document.head.removeChild(y); } ";
    private static final String SM_HIGHLIGHT_RESET_SCRIPT = SM_HIGHLIGHT_REMOVE_SCRIPT + SM_HIGHLIGHT_CREATE_SCRIPT
            + "document.getElementById('sm-table-highlight').innerHTML =\"\"; ";
    private static final String SM_HIGHLIGHT_SCRIPT_CURRENT = "var smHighlightStyle = document.getElementById('sm-table-highlight').innerHTML; ";
    private static final String SM_HIGHLIGHT_SCRIPT_APPEND = "document.getElementById('sm-table-highlight').innerHTML = smHighlightStyle;";
    private static final String NEW_PREVIEW_COLOR_CREATE_SCRIPT = "newColorPreview = document.createElement('style'); newColorPreview.id=\"new-color-preview\";  document.head.appendChild(newColorPreview); ";
    private static final String NEW_PREVIEW_COLOR_REMOVE_SCRIPT = "var z = document.getElementById('new-color-preview'); if(z) { document.head.removeChild(z); } ";
    private static final String NEW_PREVIEW_COLOR_SET_STYLE_SCRIPT = "document.getElementById('new-color-preview').innerHTML = newColorPreviewStyle;";
    private static final String PREVIEW_BUTTON_COLOR_CREATE_SCRIPT = "tagColorPreview = document.createElement('style'); tagColorPreview.id=\"tag-color-preview\";  document.head.appendChild(tagColorPreview); ";
    private static final String PREVIEW_BUTTON_COLOR_REMOVE_SCRIPT = "var a = document.getElementById('tag-color-preview'); if(a) { document.head.removeChild(a); } ";
    private static final String PREVIEW_BUTTON_COLOR_SET_STYLE_SCRIPT = "document.getElementById('tag-color-preview').innerHTML = tagColorPreviewStyle;";

    private HawkbitCommonUtil() {

    }

    /**
     * Check wether the given map is not {@code null} and not empty.
     *
     * @param mapToCheck
     *            the map to validate
     * @return {@code true} if the given map is not {@code null} and not empty.
     *         Otherwise {@code false}
     */
    public static boolean isNotNullOrEmpty(final Map<?, ?> mapToCheck) {
        return mapToCheck != null && !mapToCheck.isEmpty();
    }

    /**
     * Trims the text and converts into null in case of an empty string.
     *
     * @param text
     *            text to be trimmed
     * @return null if the text is null or if the text is blank, text.trim() if the
     *         text is not empty.
     */
    public static String trimAndNullIfEmpty(final String text) {
        if (text != null && !text.trim().isEmpty()) {
            return text.trim();
        }
        return null;
    }

    /**
     * Concatenate the given text all the string arguments with the given delimiter.
     *
     * @param delimiter
     *            the delimiter text to be used while concatenation.
     * @param texts
     *            all these string values will be concatenated with the given
     *            delimiter.
     * @return null in case no text arguments to be compared. just concatenation of
     *         all texts arguments if "delimiter" is null or empty. concatenation of
     *         all texts arguments with "delimiter" if it not null.
     */
    public static String concatStrings(final String delimiter, final String... texts) {
        final String delim = delimiter == null ? "" : delimiter;
        final StringBuilder conCatStrBldr = new StringBuilder();
        if (null != texts) {
            for (final String text : texts) {
                conCatStrBldr.append(delim);
                conCatStrBldr.append(text);
            }
        }
        final String conCatedStr = conCatStrBldr.toString();
        return delim.length() > 0 && conCatedStr.startsWith(delim) ? conCatedStr.substring(1) : conCatedStr;
    }

    /**
     * Returns the input text within html bold tag <b>..</b>.
     *
     * @param text
     *            is the text to be converted in to Bold
     * @return null if the input text param is null returns text with <b>...</b>
     *         tags.
     */
    public static String getBoldHTMLText(final String text) {
        String boldStr = null;
        if (text != null) {
            final StringBuilder updatedMsg = new StringBuilder("<b>");
            updatedMsg.append(text);
            updatedMsg.append("</b>");
            boldStr = updatedMsg.toString();
        }
        return boldStr;
    }

    /**
     * Get tool tip for Poll status.
     *
     * @param pollStatus
     * @param i18N
     * @return PollStatusToolTip
     */
    public static String getPollStatusToolTip(final PollStatus pollStatus, final VaadinMessageSource i18N) {
        if (pollStatus != null && pollStatus.getLastPollDate() != null && pollStatus.isOverdue()) {
            final TimeZone tz = SPDateTimeUtil.getBrowserTimeZone();
            return i18N.getMessage(UIMessageIdProvider.TOOLTIP_OVERDUE, SPDateTimeUtil.getDurationFormattedString(
                    pollStatus.getOverdueDate().atZone(SPDateTimeUtil.getTimeZoneId(tz)).toInstant().toEpochMilli(),
                    pollStatus.getCurrentDate().atZone(SPDateTimeUtil.getTimeZoneId(tz)).toInstant().toEpochMilli(),
                    i18N));
        }
        return null;
    }

    /**
     * Null check for text.
     *
     * @param orgText
     *            text to be formatted
     * @return String formatted text
     */
    public static String getFormattedName(final String orgText) {
        return trimAndNullIfEmpty(orgText) == null ? "" : orgText;
    }

    private static float findRequiredSwModuleExtraWidth(final float newBrowserWidth) {
        return newBrowserWidth > SPUIDefinitions.REQ_MIN_UPLOAD_BROWSER_WIDTH
                ? (newBrowserWidth - SPUIDefinitions.REQ_MIN_UPLOAD_BROWSER_WIDTH)
                : 0;
    }

    /**
     * Get artifact upload pop up width.
     *
     * @param newBrowserWidth
     *            new browser width
     * @param minPopupWidth
     *            minimum popup width
     * @return float new pop up width
     */
    public static float getArtifactUploadPopupWidth(final float newBrowserWidth, final int minPopupWidth) {
        final float extraWidth = findRequiredSwModuleExtraWidth(newBrowserWidth);
        if (extraWidth + minPopupWidth > SPUIDefinitions.MAX_UPLOAD_CONFIRMATION_POPUP_WIDTH) {
            return SPUIDefinitions.MAX_UPLOAD_CONFIRMATION_POPUP_WIDTH;
        }
        return extraWidth + minPopupWidth;
    }

    /**
     * @param newBrowserHeight
     *            new browser height
     * @param minPopupHeight
     *            minimum pop up height
     * @return float new pop up height
     */
    public static float getArtifactUploadPopupHeight(final float newBrowserHeight, final int minPopupHeight) {
        final float extraWidth = findRequiredSwModuleExtraWidth(newBrowserHeight);
        if (extraWidth + minPopupHeight > SPUIDefinitions.MAX_UPLOAD_CONFIRMATION_POPUP_HEIGHT) {
            return SPUIDefinitions.MAX_UPLOAD_CONFIRMATION_POPUP_HEIGHT;
        }
        return extraWidth + minPopupHeight;
    }

    /**
     * Remove the prefix from text.
     *
     * @param text
     *            name
     * @param prefix
     *            text to be removed
     * @return String name
     */
    public static String removePrefix(final String text, final String prefix) {
        if (text != null) {
            return text.replaceFirst(prefix, "");
        }
        return null;
    }

    /**
     * Get formatted label.Appends ellipses if content does not fit the label.
     *
     * @param labelContent
     *            content
     * @return Label
     */
    public static Label getFormatedLabel(final String labelContent) {
        final Label labelValue = new Label(labelContent, ContentMode.TEXT);
        labelValue.setSizeFull();
        labelValue.addStyleName(SPUIDefinitions.TEXT_STYLE);
        labelValue.addStyleName("label-style");
        labelValue.addStyleName("avoid-tooltip");
        return labelValue;
    }

    /**
     * Get concatenated string of software module name and version.
     *
     * @param name
     * @param version
     * @return String concatenated string
     */
    public static String getFormattedNameVersion(final String name, final String version) {
        return name + ":" + version;
    }

    /**
     * @param distName
     * @param distVersion
     * @return DistributionNameAndVersion
     */
    public static String getDistributionNameAndVersion(final String distName, final String distVersion) {
        return new StringBuilder(distName).append(':').append(distVersion).toString();
    }

    /**
     * @param smName
     * @param smVersion
     * @return SoftwareModuleNameAndVersion
     */
    public static String getSoftwareModuleNameAndVersion(final String smName, final String smVersion) {
        return new StringBuilder(smName).append(':').append(smVersion).toString();
    }

    /**
     * @param weight
     *            the weight of an action
     * @param dsNameVersion
     *            the formatted name and version of the distribution set
     * @return WeightDSNameVersion the concatenated string of weight and
     *         distribution set name and version
     */
    public static String getWeightDSNameVersion(final String weight, final String dsNameVersion) {
        return new StringBuilder(dsNameVersion).append(' ').append('(').append(weight).append(')').toString();
    }

    /**
     * Display Target Tag action message.
     *
     * @param tagName
     *            as tag name
     * @param result
     *            as TargetTagAssigmentResult
     * @param i18n
     *            I18N
     * @return message
     */
    public static String createAssignmentMessage(final String tagName,
            final AbstractAssignmentResult<? extends NamedEntity> result, final VaadinMessageSource i18n) {
        final StringBuilder formMsg = new StringBuilder();
        final int assignedCount = result.getAssigned();
        final int alreadyAssignedCount = result.getAlreadyAssigned();
        final int unassignedCount = result.getUnassigned();
        if (assignedCount == 1) {
            formMsg.append(i18n.getMessage("message.target.assigned.one", result.getAssignedEntity().get(0).getName(),
                    tagName)).append("\n");
        } else if (assignedCount > 1) {
            formMsg.append(i18n.getMessage("message.target.assigned.many", assignedCount, tagName)).append("\n");

            if (alreadyAssignedCount > 0) {
                final String alreadyAssigned = i18n.getMessage("message.target.alreadyAssigned", alreadyAssignedCount);
                formMsg.append(alreadyAssigned).append("\n");
            }
        }
        if (unassignedCount == 1) {
            formMsg.append(i18n.getMessage("message.target.unassigned.one",
                    result.getUnassignedEntity().get(0).getName(), tagName)).append("\n");
        } else if (unassignedCount > 1) {
            formMsg.append(i18n.getMessage("message.target.unassigned.many", unassignedCount, tagName)).append("\n");
        }
        return formMsg.toString();
    }

    /**
     * Create a lazy query container for the given query bean factory with empty
     * configurations.
     *
     * @param queryFactory
     *            is reference of {@link BeanQueryFactory<? extends
     *            AbstractBeanQuery>} on which lazy container should create.
     * @return instance of {@link LazyQueryContainer}.
     */
    public static LazyQueryContainer createLazyQueryContainer(
            final BeanQueryFactory<? extends AbstractBeanQuery<?>> queryFactory) {
        queryFactory.setQueryConfiguration(Collections.emptyMap());
        return new LazyQueryContainer(new LazyQueryDefinition(true, 20, SPUILabelDefinitions.VAR_NAME), queryFactory);
    }

    /**
     * Create lazy query container for DS type.
     *
     * @param queryFactory
     * @return LazyQueryContainer
     */
    public static LazyQueryContainer createDSLazyQueryContainer(
            final BeanQueryFactory<? extends AbstractBeanQuery<?>> queryFactory) {
        queryFactory.setQueryConfiguration(Collections.emptyMap());
        return new LazyQueryContainer(new LazyQueryDefinition(true, 20, "tagIdName"), queryFactory);
    }

    /**
     * Set distribution table column properties.
     *
     * @param container
     *            table container
     */
    public static void getDsTableColumnProperties(final Container container) {
        final LazyQueryContainer lqc = (LazyQueryContainer) container;
        lqc.addContainerProperty(SPUILabelDefinitions.VAR_ID, Long.class, null, false, false);
        lqc.addContainerProperty(SPUILabelDefinitions.VAR_DESC, String.class, "", false, true);
        lqc.addContainerProperty(SPUILabelDefinitions.VAR_VERSION, String.class, null, false, false);
        lqc.addContainerProperty(SPUILabelDefinitions.VAR_NAME, String.class, null, false, true);
        lqc.addContainerProperty(SPUILabelDefinitions.VAR_CREATED_BY, String.class, null, false, true);
        lqc.addContainerProperty(SPUILabelDefinitions.VAR_LAST_MODIFIED_BY, String.class, null, false, true);
        lqc.addContainerProperty(SPUILabelDefinitions.VAR_CREATED_DATE, String.class, null, false, true);
        lqc.addContainerProperty(SPUILabelDefinitions.VAR_LAST_MODIFIED_DATE, String.class, null, false, true);
    }

    /**
     * Reset the software module table rows highlight css.
     *
     * @return javascript to rest software module table rows highlight css.
     */
    public static String getScriptSMHighlightReset() {
        return SM_HIGHLIGHT_RESET_SCRIPT;
    }

    /**
     * Highlight software module rows with the color of sw-type.
     *
     * @param colorCSS
     *            color to generate the css script.
     * @return javascript to append software module table rows with highlighted
     *         color.
     */
    public static String getScriptSMHighlightWithColor(final String colorCSS) {
        return new StringBuilder().append(SM_HIGHLIGHT_SCRIPT_CURRENT)
                .append("smHighlightStyle = smHighlightStyle + \"").append(colorCSS).append("\";")
                .append(SM_HIGHLIGHT_SCRIPT_APPEND).toString();
    }

    /**
     * Get javascript to reflect new color selection in color picker preview for
     * name and description fields .
     *
     * @param colorPickedPreview
     *            changed color
     * @return javascript for the selected color.
     */
    public static String changeToNewSelectedPreviewColor(final String colorPickedPreview) {
        return new StringBuilder().append(NEW_PREVIEW_COLOR_REMOVE_SCRIPT).append(NEW_PREVIEW_COLOR_CREATE_SCRIPT)
                .append("var newColorPreviewStyle = \".v-app .new-tag-name{ border: solid 3px ")
                .append(colorPickedPreview)
                .append(" !important; width:138px; margin-left:2px !important; box-shadow:none !important; } \"; ")
                .append("newColorPreviewStyle = newColorPreviewStyle + \".v-app .new-tag-desc{ border: solid 3px ")
                .append(colorPickedPreview)
                .append(" !important; width:138px; height:75px !important; margin-top:4px !important; margin-left:2px !important;;box-shadow:none !important;} \"; ")
                .append(NEW_PREVIEW_COLOR_SET_STYLE_SCRIPT).toString();
    }

    /**
     * Get javascript to reflect new color selection for preview button.
     *
     * @param color
     *            changed color
     * @return javascript for the selected color.
     */
    public static String getPreviewButtonColorScript(final String color) {
        return new StringBuilder().append(PREVIEW_BUTTON_COLOR_REMOVE_SCRIPT).append(PREVIEW_BUTTON_COLOR_CREATE_SCRIPT)
                .append("var tagColorPreviewStyle = \".v-app .tag-color-preview{ height: 15px !important; padding: 0 10px !important; border: 0px !important; margin-left:12px !important;  margin-top: 4px !important; border-width: 0 !important; background: ")
                .append(color)
                .append(" } .v-app .tag-color-preview:after{ border-color: none !important; box-shadow:none !important;} \"; ")
                .append(PREVIEW_BUTTON_COLOR_SET_STYLE_SCRIPT).toString();
    }

    /**
     * Apply style for status label in target table.
     *
     * @param targetTable
     *            target table
     * @param pinBtn
     *            pin button used for status display and pin on mouse over
     * @param itemId
     *            id of the tabel row
     */
    public static void applyStatusLblStyle(final Table targetTable, final Button pinBtn, final Object itemId) {
        final Item item = targetTable.getItem(itemId);
        if (item != null) {
            final TargetUpdateStatus updateStatus = (TargetUpdateStatus) item
                    .getItemProperty(SPUILabelDefinitions.VAR_TARGET_STATUS).getValue();
            pinBtn.removeStyleName("statusIconRed statusIconBlue statusIconGreen statusIconYellow statusIconLightBlue");
            if (updateStatus == TargetUpdateStatus.ERROR) {
                pinBtn.addStyleName(SPUIStyleDefinitions.STATUS_ICON_RED);
            } else if (updateStatus == TargetUpdateStatus.UNKNOWN) {
                pinBtn.addStyleName(SPUIStyleDefinitions.STATUS_ICON_BLUE);
            } else if (updateStatus == TargetUpdateStatus.IN_SYNC) {
                pinBtn.addStyleName(SPUIStyleDefinitions.STATUS_ICON_GREEN);
            } else if (updateStatus == TargetUpdateStatus.PENDING) {
                pinBtn.addStyleName(SPUIStyleDefinitions.STATUS_ICON_YELLOW);
            } else if (updateStatus == TargetUpdateStatus.REGISTERED) {
                pinBtn.addStyleName(SPUIStyleDefinitions.STATUS_ICON_LIGHT_BLUE);
            }
        }
    }

    /**
     * Formats the finished percentage of a rollout group into a string with one
     * digit after comma.
     *
     * @param rolloutGroup
     *            the rollout group
     * @param finishedPercentage
     *            the calculated finished percentage of the rolloutgroup
     * @return formatted String value
     */
    public static String formattingFinishedPercentage(final RolloutGroup rolloutGroup, final float finishedPercentage) {
        float tmpFinishedPercentage = 0;
        switch (rolloutGroup.getStatus()) {
        case READY:
        case SCHEDULED:
        case ERROR:
            tmpFinishedPercentage = 0.0F;
            break;
        case FINISHED:
            tmpFinishedPercentage = 100.0F;
            break;
        case RUNNING:
            tmpFinishedPercentage = finishedPercentage;
            break;
        default:
            break;
        }
        return String.format(getCurrentLocale(), "%.1f", tmpFinishedPercentage);
    }

    /**
     * Returns a formatted string as needed by label custom render .This string
     * holds the properties of a status label.
     *
     * @param value
     *            label value
     * @param style
     *            label style
     * @param id
     *            label id
     * @return formatted string
     */
    public static String getStatusLabelDetailsInString(final String value, final String style, final String id) {
        final StringBuilder val = new StringBuilder();
        if (!StringUtils.isEmpty(value)) {
            val.append("value:").append(value).append(",");
        }
        if (!StringUtils.isEmpty(style)) {
            val.append("style:").append(style).append(",");
        }
        return val.append("id:").append(id).toString();
    }

    /**
     * Receive the code point of a given StatusFontIcon.
     *
     * @param statusFontIcon
     *            the status font icon
     * @return the code point of the StatusFontIcon
     */
    public static String getCodePoint(final StatusFontIcon statusFontIcon) {
        if (statusFontIcon == null) {
            return null;
        }
        return statusFontIcon.getFontIcon() != null ? Integer.toString(statusFontIcon.getFontIcon().getCodepoint())
                : null;
    }

    /**
     * Gets the locale of the current Vaadin UI. If the locale can not be
     * determined, the default locale is returned instead.
     *
     * @return the current locale, never {@code null}.
     * @see com.vaadin.ui.UI#getLocale()
     * @see java.util.Locale#getDefault()
     */
    public static Locale getCurrentLocale() {
        final UI currentUI = UI.getCurrent();
        return currentUI == null ? Locale.getDefault() : currentUI.getLocale();
    }

    /**
     * Creates caption for table headers
     * 
     * @param caption
     * @return formatted text for table caption
     */
    public static String getCaptionText(final String caption) {
        return DIV_DESCRIPTION_START + caption + DIV_DESCRIPTION_END;
    }

    /**
     * Creates the caption of the Artifact Details table
     * 
     * @param name
     *            name of the software module, if one is selected
     * @param i18n
     *            VaadinMessageSource
     * @return complete caption text of the table
     */
    public static String getArtifactoryDetailsLabelId(final String name, final VaadinMessageSource i18n) {
        final String caption;
        if (StringUtils.hasText(name)) {
            caption = i18n.getMessage(UIMessageIdProvider.CAPTION_ARTIFACT_DETAILS_OF);
        } else {
            caption = i18n.getMessage(UIMessageIdProvider.CAPTION_ARTIFACT_DETAILS);
        }
        return caption;
    }

    /**
     * Determine the language that should be used considering localization
     * properties and a desired Locale
     * 
     * @param localizationProperties
     *            UI Localization settings
     * @param desiredLocale
     *            desired Locale
     * @return Locale to be used according to UI and properties
     */
    public static Locale getLocaleToBeUsed(final UiProperties.Localization localizationProperties,
            final Locale desiredLocale) {
        final List<Locale> availableLocals = localizationProperties.getAvailableLocals();
        // ckeck if language code of UI locale matches an available local.
        // Country, region and variant are ignored. "availableLocals" must only
        // contain language codes without country or other extensions.
        if (availableLocals.contains(desiredLocale)) {
            return desiredLocale;
        }
        return localizationProperties.getDefaultLocal();
    }

    /**
     * Set localization considering properties and UI settings.
     * 
     * @param ui
     *            UI to setup
     * @param localizationProperties
     *            UI localization settings
     * @param i18n
     *            Localization message source
     */
    public static void initLocalization(final UI ui, final Localization localizationProperties,
            final VaadinMessageSource i18n) {
        ui.setLocale(HawkbitCommonUtil.getLocaleToBeUsed(localizationProperties, ui.getSession().getLocale()));
        ui.getReconnectDialogConfiguration()
                .setDialogText(i18n.getMessage(UIMessageIdProvider.VAADIN_SYSTEM_TRYINGRECONNECT));
    }

}
