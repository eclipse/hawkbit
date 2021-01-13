/**
 * Copyright (c) 2020 devolo AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

import org.eclipse.hawkbit.repository.TargetFields;
import org.eclipse.hawkbit.repository.rsql.VirtualPropertyReplacer;
import org.eclipse.hawkbit.repository.rsql.VirtualPropertyResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Container object to store and keep the extracted Target field values
 * to be used by RsqlVisitor in order to compare with Target filters
 */
public class TargetFieldData {

    private final List<TargetFieldContent> contentList = new ArrayList<>();

    public void add(TargetFields name, String value) {
        TargetFieldContent content = new TargetFieldContent(name, value);
        contentList.add(content);
    }

    /**
     * Adds Target's field data to the container
     *
     * @param name Target field name
     * @param subKey Field name subkey (optional), e.g. "attribute.device_type", "device_type" is the subkey
     * @param value Target field value
     */
    public void add(TargetFields name, String subKey, String value) {
        TargetFieldContent content = new TargetFieldContent(name, subKey, value);
        contentList.add(content);
    }

    /**
     * Checks if an entry with the given key and value exists
     * @param key Field name
     * @param value Field value
     * @return true if the key-value pair exists, otherwise false
     */
    public boolean hasEntry(String key, String value){
        return contentList.stream()
                .filter(c -> c.fieldName.equalsIgnoreCase(key))
                .anyMatch(c -> c.fieldValue.equalsIgnoreCase(value));
    }

    /**
     * Method to be called by RsqlVisitor
     * Checks whether the object contains field name and value relation described by the operator
     * @param selector RSQL selector (key)
     * @param operator RSQL operator (relation descriptor)
     * @param value RSQL value
     * @return true if he object contains field name and value relation described by the operator, otherwise false
     */
    public boolean request(String selector, String operator, String value) {
        return request(selector, operator, Collections.singletonList(value));
    }

    /**
     * Method to be called by RsqlVisitor
     * Checks whether the object contains field name and value relation described by the operator
     * @param selector RSQL selector (key)
     * @param operator RSQL operator (relation descriptor)
     * @param inputValues RSQL value
     * @return true if he object contains field name and value relation described by the operator, otherwise false
     */
    public boolean request(String selector, String operator, List<String> inputValues) {

        VirtualPropertyReplacer propertyReplacer = new VirtualPropertyResolver();

        List<String> values = inputValues.stream()
                .map(propertyReplacer::replace)
                .collect(Collectors.toList());

        List<TargetFieldContent> fieldContent = contentList.stream()
                .filter(c -> selector.equalsIgnoreCase(c.fieldName))
                .collect(Collectors.toList());

        if("==".equalsIgnoreCase(operator) || "=in=".equalsIgnoreCase(operator)) {
            return containsAnyOf(fieldContent, values);
        }

        if("!=".equalsIgnoreCase(operator) || "=out=".equalsIgnoreCase(operator)) {
            return containsNoneOf(fieldContent, values, withSubKey(selector));
        }

        if("=gt=".equalsIgnoreCase(operator)) {
            return valueGreaterThan(fieldContent, values);
        }

        if("=ge=".equalsIgnoreCase(operator)) {
            return valueGreaterOrEqualTo(fieldContent, values);
        }

        if("=lt=".equalsIgnoreCase(operator)) {
            return valueLessThan(fieldContent, values);
        }

        if("=le=".equalsIgnoreCase(operator)) {
            return valueLessOrEqualTo(fieldContent, values);
        }

        throw new IllegalArgumentException("Unknown operator: {" + operator + "}");
    }

    private static boolean containsWildCard(String input){
        return input.contains("*");
    }

    private static boolean matchWithWildCards(String fieldValue, String inputValue){
        String modifiedValue = inputValue.replaceAll("\\*", ".*");
        return Pattern.matches(modifiedValue, fieldValue);
    }

    private static boolean isEmpty(List<String> input){
        return input.stream().allMatch(String::isEmpty);
    }

    private static boolean withSubKey(String key){
        return key.toLowerCase().startsWith("attribute.") || key.toLowerCase().startsWith("metadata.");
    }

    private static boolean containsAnyOf(List<TargetFieldContent> fieldValues, List<String> inputValues){
        // for cases like [tag == ""]
        if(fieldValues.isEmpty() && isEmpty(inputValues)) {
            return true;
        }

        for(TargetFieldContent content : fieldValues){
            for(String inputValue : inputValues){
                if(containsWildCard(inputValue) && matchWithWildCards(content.fieldValue, inputValue)){
                    return true;
                }
            }

            if(inputValues.stream().anyMatch(content.fieldValue::equalsIgnoreCase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNoneOf(List<TargetFieldContent> fieldValues, List<String> inputValues,
            boolean withSubKey) {
        // must not match non-existent fields with subkey like [attribute.noexist != value]
        // but must match other non-existent fields like [tag != "alpha"]
        if(fieldValues.isEmpty() && withSubKey) {
            return false;
        }

        if(isEmpty(inputValues)) {
            return true;
        }

        for(TargetFieldContent content : fieldValues){
            for(String inputValue : inputValues){
                if(containsWildCard(inputValue) && matchWithWildCards(content.fieldValue, inputValue)){
                    return false;
                }
            }

            if(inputValues.stream().anyMatch(content.fieldValue::equalsIgnoreCase)) {
                return false;
            }
        }
        return true;
    }

    private static boolean valueGreaterThan(List<TargetFieldContent> fieldValues, List<String> inputValues){
        if(fieldValues.isEmpty() || isEmpty(inputValues)) {
            return false;
        }

        return fieldValues.get(0).fieldValue.compareToIgnoreCase(inputValues.get(0)) > 0;
    }

    private static boolean valueGreaterOrEqualTo(List<TargetFieldContent> fieldValues, List<String> inputValues){
        if(fieldValues.isEmpty() || isEmpty(inputValues)) {
            return false;
        }

        return fieldValues.get(0).fieldValue.compareToIgnoreCase(inputValues.get(0)) >= 0;
    }

    private static boolean valueLessThan(List<TargetFieldContent> fieldValues, List<String> inputValues){
        if(fieldValues.isEmpty() || isEmpty(inputValues)) {
            return false;
        }

        return fieldValues.get(0).fieldValue.compareToIgnoreCase(inputValues.get(0)) < 0;
    }

    private static boolean valueLessOrEqualTo(List<TargetFieldContent> fieldValues, List<String> inputValues){
        if(fieldValues.isEmpty() || isEmpty(inputValues)) {
            return false;
        }

        return fieldValues.get(0).fieldValue.compareToIgnoreCase(inputValues.get(0)) <= 0;
    }

    private static class TargetFieldContent{

        protected final String fieldName;
        protected final String fieldValue;

        public TargetFieldContent(TargetFields fieldName, String value){
            this(fieldName, "", value);
        }

        public TargetFieldContent(TargetFields fieldName, String subKey, String value){
            this.fieldName = (subKey.isEmpty() ? fieldName.name() : (fieldName.name() + "." + subKey)).toLowerCase();
            this.fieldValue = value.toLowerCase();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TargetFieldContent content = (TargetFieldContent) o;
            return fieldName.equals(content.fieldName) && fieldValue.equals(content.fieldValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fieldName, fieldValue);
        }
    }
}
