/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.rsql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.TargetFields;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.exception.RSQLParameterSyntaxException;
import org.eclipse.hawkbit.repository.exception.RSQLParameterUnsupportedFieldException;
import org.eclipse.hawkbit.repository.jpa.rsql.ParseExceptionWrapper.TokenWrapper;
import org.eclipse.hawkbit.repository.rsql.RsqlValidationOracle;
import org.eclipse.hawkbit.repository.rsql.SuggestToken;
import org.eclipse.hawkbit.repository.rsql.SuggestionContext;
import org.eclipse.hawkbit.repository.rsql.SyntaxErrorContext;
import org.eclipse.hawkbit.repository.rsql.ValidationOracleContext;
import org.eclipse.persistence.exceptions.ConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.util.CollectionUtils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import cz.jirutka.rsql.parser.ParseException;
import cz.jirutka.rsql.parser.RSQLParserException;

/**
 * An implementation of {@link RsqlValidationOracle} which retrieves the
 * exception using the {@link ParseException} to retrieve the suggestions.
 * 
 * The suggestion only works when there are syntax errors existing because the
 * information about current and next tokens in the RSQL syntax are from the
 * {@link ParseException}.
 * 
 * There is a feature request on the GitHub project
 * <a href="https://github.com/jirutka/rsql-parser/issues/22">https://github.com
 * /jirutka/rsql-parser/issues/22</a>
 * 
 */
public class RsqlParserValidationOracle implements RsqlValidationOracle {

    private static final Logger LOGGER = LoggerFactory.getLogger(RsqlParserValidationOracle.class);

    @Autowired
    private TargetManagement targetManagement;

    @Override
    public ValidationOracleContext suggest(final String rsqlQuery, final int cursorPosition) {

        final List<SuggestToken> expectedTokens = new ArrayList<>();
        final ValidationOracleContext context = new ValidationOracleContext();
        context.setSyntaxError(true);
        final SuggestionContext suggestionContext = new SuggestionContext();
        context.setSuggestionContext(suggestionContext);
        final SyntaxErrorContext errorContext = new SyntaxErrorContext();
        context.setSyntaxErrorContext(errorContext);

        try {
            RSQLUtility.isValid(rsqlQuery, TargetFields.class);
            context.setSyntaxError(false);
            suggestionContext.getSuggestions().addAll(getLogicalOperatorSuggestion(rsqlQuery));
        } catch (final RSQLParameterSyntaxException | RSQLParserException ex) {
            setExceptionDetails(rsqlQuery, new Exception(ex.getCause().getCause()), expectedTokens);
            errorContext.setErrorMessage(getCustomMessage(ex.getCause().getMessage(), expectedTokens));
            suggestionContext.setSuggestions(expectedTokens);
            LOGGER.trace("Syntax exception on parsing :", ex);
        } catch (final RSQLParameterUnsupportedFieldException | IllegalArgumentException ex) {
            errorContext.setErrorMessage(getCustomMessage(ex.getMessage(), null));
            LOGGER.trace("Illegal argument on parsing :", ex);
        } catch (@SuppressWarnings("squid:S1166") final ConversionException | JpaSystemException e) {
            // noop
        }
        return context;
    }

    private static Collection<? extends SuggestToken> getLogicalOperatorSuggestion(final String rsqlQuery) {
        if (!rsqlQuery.endsWith(" ")) {
            return Collections.emptyList();
        } else {
            final int currentQueryLength = rsqlQuery.length();
            // only return and/or suggestion when there is a space at the end
            final Collection<String> tokenImages = TokenDescription.getTokenImage(TokenDescription.LOGICAL_OP);
            final List<SuggestToken> logicalOps = new ArrayList<>(tokenImages.size());
            for (final String tokenImage : tokenImages) {
                logicalOps.add(new SuggestToken(currentQueryLength, currentQueryLength + tokenImage.length(), null,
                        tokenImage));
            }
            return logicalOps;
        }
    }

    private static void setExceptionDetails(final String rsqlQuery, final Exception ex,
            final List<SuggestToken> expectedTokens) {
        final ParseException parseException = findParseException(ex);
        if (parseException == null) {
            expectedTokens.addAll(getComparatorOperatorSuggestions(rsqlQuery));
        } else {
            expectedTokens.addAll(getNextTokens(parseException));
        }
    }

    private static List<SuggestToken> getNextTokens(final ParseException parseException) {
        final List<SuggestToken> listTokens = new ArrayList<>();
        final ParseExceptionWrapper parseExceptionWrapper = new ParseExceptionWrapper(parseException);
        final int[][] expectedTokenSequence = parseExceptionWrapper.getExpectedTokenSequence();
        final TokenWrapper currentToken = parseExceptionWrapper.getCurrentToken();
        if (currentToken == null) {
            return Collections.emptyList();
        }
        final TokenWrapper nextToken = currentToken.getNext();
        final int currentTokenKind = currentToken.getKind();
        final String currentTokenImageName = currentToken.getImage();
        final int nextTokenBeginColumn = nextToken.getBeginColumn();
        final int currentTokenEndColumn = currentToken.getEndColumn();

        // token == 5 is the field token, reverse engineering.
        if (currentTokenKind == 5) {
            final Optional<List<SuggestToken>> handleFieldTokenSuggestion = handleFieldTokenSuggestion(
                    currentTokenImageName, nextTokenBeginColumn, currentTokenEndColumn);
            if (handleFieldTokenSuggestion.isPresent()) {
                return handleFieldTokenSuggestion.get();
            }
        }

        for (final int[] is : expectedTokenSequence) {
            addSuggestionOnTokenImage(listTokens, nextTokenBeginColumn, currentTokenEndColumn, is);
        }
        return listTokens;
    }

    private static List<SuggestToken> getComparatorOperatorSuggestions(final String rsqlQuery) {
        // only return comparator operators suggestions when there is a '=' or
        // '!' symbol at the end
        final String mapKeyOperatorPattern = "(\\w+)\\.\\w+[=!]{1}$";
        final Matcher mapKeyOperatorMatcher = Pattern.compile(mapKeyOperatorPattern).matcher(rsqlQuery);

        if (mapKeyOperatorMatcher.find() && FieldNameDescription.isMap(mapKeyOperatorMatcher.group(1))) {
            final int currentQueryLength = rsqlQuery.length() - 1;
            final Collection<String> tokenImages = TokenDescription.getTokenImage(TokenDescription.COMPARATOR);
            return tokenImages.stream().map(tokenImage -> new SuggestToken(currentQueryLength,
                    currentQueryLength + tokenImage.length(), null, tokenImage)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private static void addSuggestionOnTokenImage(final List<SuggestToken> listTokens, final int nextTokenBeginColumn,
            final int currentTokenEndColumn, final int[] is) {
        for (final int i : is) {
            final Collection<String> tokenImage = TokenDescription.getTokenImage(i);
            if (!CollectionUtils.isEmpty(tokenImage)) {
                tokenImage.forEach(image -> listTokens.add(new SuggestToken(currentTokenEndColumn + 1,
                        nextTokenBeginColumn + image.length(), null, image)));
            }
        }
    }

    private static Optional<List<SuggestToken>> handleFieldTokenSuggestion(final String currentTokenImageName,
            final int nextTokenBeginColumn, final int currentTokenEndColumn) {
        final boolean containsDot = currentTokenImageName.indexOf('.') != -1;
        if (shouldSuggestTopLevelFieldNames(currentTokenImageName, containsDot)) {
            return Optional
                    .of(FieldNameDescription.toTopSuggestToken(nextTokenBeginColumn - currentTokenImageName.length(),
                            nextTokenBeginColumn + currentTokenImageName.length(), currentTokenImageName));
        } else if (shouldSuggestDotToken(currentTokenImageName, containsDot)) {
            return Optional
                    .of(Arrays.asList(new SuggestToken(currentTokenEndColumn, nextTokenBeginColumn + 1, null, ".")));
        } else if (shouldSuggestSubTokenFieldNames(currentTokenImageName, containsDot)) {
            return handleSubtokenSuggestion(currentTokenImageName, nextTokenBeginColumn);
        }
        return Optional.empty();
    }

    private static boolean shouldSuggestSubTokenFieldNames(final String currentTokenImageName,
            final boolean containsDot) {
        return containsDot && !FieldNameDescription.containsValue(currentTokenImageName);
    }

    private static boolean shouldSuggestDotToken(final String currentTokenImageName, final boolean containsDot) {
        return !containsDot && (FieldNameDescription.hasSubEntries(currentTokenImageName)
                || FieldNameDescription.isMap(currentTokenImageName));
    }

    private static boolean shouldSuggestTopLevelFieldNames(final String currentTokenImageName,
            final boolean containsDot) {
        return !containsDot && !FieldNameDescription.containsValue(currentTokenImageName)
                && !FieldNameDescription.hasSubEntries(currentTokenImageName);
    }

    private static Optional<List<SuggestToken>> handleSubtokenSuggestion(final String currentTokenImageName,
            final int nextTokenBeginColumn) {
        final String[] split = currentTokenImageName.split("\\.");
        for (final String string : split) {
            if (FieldNameDescription.containsValue(string)) {
                final String subTokenImage = split.length > 1 ? split[1] : null;
                final int subTokenBegin = nextTokenBeginColumn + currentTokenImageName.indexOf('.') + 1;
                return Optional.of(FieldNameDescription.toSubSuggestToken(subTokenBegin, subTokenBegin + 1, string,
                        subTokenImage));
            }
        }
        return Optional.empty();
    }

    private static ParseException findParseException(final Throwable e) {
        if (e instanceof ParseException) {
            return (ParseException) e;
        } else if (e.getCause() != null) {
            return findParseException(e.getCause());
        }
        return null;
    }

    private static String getCustomMessage(final String message, final List<SuggestToken> expectedTokens) {
        String builder = message;

        if (!message.contains(":")) {
            return builder;
        }

        builder = message.substring(message.indexOf(':') + 1, message.length());
        if (builder.indexOf("Was expecting") != -1) {
            builder = builder.substring(0, builder.lastIndexOf("Was expecting"));
        }

        if (!CollectionUtils.isEmpty(expectedTokens)) {
            final StringBuilder tokens = new StringBuilder();
            expectedTokens.stream().forEach(value -> tokens.append(value.getSuggestion() + ","));
            builder = builder.concat("Was expecting :" + tokens.toString().substring(0, tokens.length() - 1));
        }
        builder = builder.replace('\r', ' ');
        builder = builder.replace('\n', ' ');
        builder = builder.replaceAll(">", " ");
        builder = builder.replaceAll("<", " ");

        return builder;
    }

    // Token map with logical and comparator operator that are used for context
    // sensitive help on search query.
    private static final class TokenDescription {

        private static final Multimap<Integer, String> TOKEN_MAP = ArrayListMultimap.create();

        private static final int LOGICAL_OP = 8;
        private static final int COMPARATOR = 12;

        static {
            TOKEN_MAP.put(LOGICAL_OP, "and");
            TOKEN_MAP.put(LOGICAL_OP, "or");
            TOKEN_MAP.put(COMPARATOR, "==");
            TOKEN_MAP.put(COMPARATOR, "!=");
            TOKEN_MAP.put(COMPARATOR, "=ge=");
            TOKEN_MAP.put(COMPARATOR, "=le=");
            TOKEN_MAP.put(COMPARATOR, "=gt=");
            TOKEN_MAP.put(COMPARATOR, "=lt=");
            TOKEN_MAP.put(COMPARATOR, "=in=");
            TOKEN_MAP.put(COMPARATOR, "=out=");
        }

        private TokenDescription() {

        }

        private static Collection<String> getTokenImage(final int tokenIndex) {
            return TOKEN_MAP.get(tokenIndex);
        }

    }

    private static final class FieldNameDescription {

        private static final Set<String> FIELD_NAMES = Arrays.stream(TargetFields.values())
                .map(field -> field.toString().toLowerCase()).collect(Collectors.toSet());

        private static final Map<String, List<String>> SUB_NAMES = Arrays.stream(TargetFields.values()).collect(
                Collectors.toMap(field -> field.toString().toLowerCase(), TargetFields::getSubEntityAttributes));

        private FieldNameDescription() {

        }

        private static boolean hasSubEntries(final String tokenImageName) {
            String tmpTokenName = tokenImageName;
            if (tokenImageName.contains(".")) {
                final String[] split = tokenImageName.split("\\.");
                if (split.length <= 0) {
                    return false;
                }
                tmpTokenName = split[0];
            }
            final String finalTmpTokenName = tmpTokenName;
            return Arrays.stream(TargetFields.values())
                    .filter(field -> field.toString().equalsIgnoreCase(finalTmpTokenName))
                    .map(TargetFields::getSubEntityAttributes).flatMap(List::stream).count() > 0;
        }

        private static boolean isMap(final String tokenImageName) {
            return Arrays.stream(TargetFields.values())
                    .filter(field -> field.toString().equalsIgnoreCase(tokenImageName)).findFirst()
                    .map(TargetFields::isMap).orElse(false);
        }

        private static List<SuggestToken> toTopSuggestToken(final int beginToken, final int endToken,
                final String tokenImageName) {
            return FIELD_NAMES.stream()
                    .map(field -> new SuggestToken(beginToken, endToken, tokenImageName, field.toLowerCase()))
                    .collect(Collectors.toList());
        }

        private static List<SuggestToken> toSubSuggestToken(final int beginToken, final int endToken,
                final String topToken, final String tokenImageName) {
            return Arrays.stream(TargetFields.values()).filter(field -> field.toString().equalsIgnoreCase(topToken))
                    .map(TargetFields::getSubEntityAttributes).flatMap(List::stream)
                    .map(subentity -> new SuggestToken(beginToken, endToken, tokenImageName, subentity))
                    .collect(Collectors.toList());
        }

        private static boolean containsValue(final String imageName) {
            if (!imageName.contains(".")) {
                return FIELD_NAMES.stream().filter(value -> value.equalsIgnoreCase(imageName)).count() > 0;
            }
            final String[] split = imageName.split("\\.");
            if (split.length > 1 && FIELD_NAMES.contains(split[0].toLowerCase())) {
                return SUB_NAMES.get(split[0].toLowerCase()).stream()
                        .filter(subname -> (split[0] + "." + subname).equalsIgnoreCase(imageName)).count() > 0;
            }
            return FIELD_NAMES.stream().filter(value -> value.equalsIgnoreCase(imageName)).count() > 0;
        }

    }

}
