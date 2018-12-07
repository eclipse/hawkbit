/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Collection of regular expression characters to check strings
 */
public class RegexCharacterCollection {

    private final EnumSet<RegexChar> characters;
    private final Pattern atLeastOneCharacterOfCollection;

    public RegexCharacterCollection(final RegexChar... characters) {
        this.characters = EnumSet.copyOf(Arrays.asList(characters));
        this.atLeastOneCharacterOfCollection = getPatternAtLeastOneCharacterOfCollection();
    }

    public boolean stringContainsCharacter(final String stringToCheck) {
        return atLeastOneCharacterOfCollection.matcher(stringToCheck).matches();
    }

    private Pattern getPatternAtLeastOneCharacterOfCollection() {
        final StringBuilder charBuilder = new StringBuilder();
        characters.forEach(character -> charBuilder.append(character.regExp));
        final String regularExpression = String.format(".*[%s]+.*", charBuilder.toString());
        return Pattern.compile(regularExpression);
    }

    public enum RegexChar {
        WHITESPACE("\\s", "character.whitespace"), DIGITS("0-9", "character.digits"), QUOTATION_MARKS("'\"",
                "character.quotationMarks"), SLASHES("\\/\\\\", "character.slashes"), GREATER_THAN(
                        ">"), LESS_THAN("<"), EQUALS_SYMBOL("="), EXCLAMATION_MARK("!"), QUESTION_MARK("?"), COLON(":");

        public final String regExp;
        public final Optional<String> l18nReferenceDescription;

        private RegexChar(final String character) {
            this(character, null);
        }
    
        private RegexChar(final String regExp, final String l18nReferenceDescription) {
            this.regExp = regExp;
            this.l18nReferenceDescription = Optional.ofNullable(l18nReferenceDescription);
        }
    }
}
