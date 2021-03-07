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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.PluralJoin;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.text.StrLookup;
import org.eclipse.hawkbit.repository.FieldNameProvider;
import org.eclipse.hawkbit.repository.FieldValueConverter;
import org.eclipse.hawkbit.repository.exception.RSQLParameterSyntaxException;
import org.eclipse.hawkbit.repository.exception.RSQLParameterUnsupportedFieldException;
import org.eclipse.hawkbit.repository.rsql.VirtualPropertyReplacer;
import org.eclipse.hawkbit.repository.rsql.VirtualPropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.google.common.collect.Lists;

import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.RSQLParserException;
import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import cz.jirutka.rsql.parser.ast.RSQLOperators;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;

/**
 * A utility class which is able to parse RSQL strings into an spring data
 * {@link Specification} which then can be enhanced sql queries to filter
 * entities. RSQL parser library: https://github.com/jirutka/rsql-parser
 *
 * <ul>
 * <li>{@code Equal to : ==}</li>
 * <li>{@code Not equal to : !=}</li>
 * <li>{@code Less than : =lt= or <}</li>
 * <li>{@code Less than or equal to : =le= or <=}</li>
 * <li>{@code Greater than operator : =gt= or >}</li>
 * <li>{@code Greater than or equal to : =ge= or >=}</li>
 * </ul>
 * <p>
 * Examples of RSQL expressions in both FIQL-like and alternative notation:
 * <ul>
 * <li>{@code version==2.0.0}</li>
 * <li>{@code name==targetId1;description==plugAndPlay}</li>
 * <li>{@code name==targetId1 and description==plugAndPlay}</li>
 * <li>{@code name==targetId1,description==plugAndPlay,updateStatus==UNKNOWN}</li>
 * <li>{@code name==targetId1 or description==plugAndPlay or updateStatus==UNKNOWN}</li>
 * </ul>
 * <p>
 * There is also a mechanism that allows to refer to known macros that can
 * resolved by an optional {@link StrLookup} (cp.
 * {@link VirtualPropertyResolver}).<br>
 * An example that queries for all overdue targets using the ${OVERDUE_TS}
 * placeholder introduced by {@link VirtualPropertyResolver} looks like
 * this:<br>
 * <em>lastControllerRequestAt=le=${OVERDUE_TS}</em><br>
 * It is possible to escape a macro expression by using a second '$':
 * $${OVERDUE_TS} would prevent the ${OVERDUE_TS} token from being expanded.
 *
 */
public final class RSQLUtility {

    private static final Logger LOGGER = LoggerFactory.getLogger(RSQLUtility.class);

    /**
     * private constructor due utility class.
     */
    private RSQLUtility() {

    }

    /**
     * parses an RSQL valid string into an JPA {@link Specification} which then
     * can be used to filter for JPA entities with the given RSQL query.
     *
     * @param rsql
     *            the rsql query
     * @param fieldNameProvider
     *            the enum class type which implements the
     *            {@link FieldNameProvider}
     * @param virtualPropertyReplacer
     *            holds the logic how the known macros have to be resolved; may
     *            be <code>null</code>
     * @param database
     *            in use
     *
     * @return an specification which can be used with JPA
     * @throws RSQLParameterUnsupportedFieldException
     *             if a field in the RSQL string is used but not provided by the
     *             given {@code fieldNameProvider}
     * @throws RSQLParameterSyntaxException
     *             if the RSQL syntax is wrong
     *
     */
    public static <A extends Enum<A> & FieldNameProvider, T> Specification<T> parse(final String rsql,
            final Class<A> fieldNameProvider, final VirtualPropertyReplacer virtualPropertyReplacer,
            final Database database) {
        return new RSQLSpecification<>(rsql.toLowerCase(), fieldNameProvider, virtualPropertyReplacer, database);
    }

    /**
     * Validates the RSQL string
     * @param rsql RSQL string to validate
     * @param fieldNameProvider
     * @return true if valid
     * @throws RSQLParserException if RSQL syntax is invalid
     * @throws RSQLParameterUnsupportedFieldException if RSQL key is not allowed
     */
    public static <A extends Enum<A> & FieldNameProvider> boolean isValid(final String rsql, Class<A> fieldNameProvider){
        Node rootNode = parseRsql(rsql);
        RSQLVisitor<Boolean, String> visitor = new ValidationRSQLVisitor<>(fieldNameProvider);

        if(rootNode.accept(visitor)){
            return true;
        }
        throw new RSQLParameterUnsupportedFieldException(new IllegalArgumentException("Invalid key name or value placeholder"));
    }

    private static Node parseRsql(final String rsql) {
        try {
            LOGGER.debug("parsing rsql string {}", rsql);
            final Set<ComparisonOperator> operators = RSQLOperators.defaultOperators();
            return new RSQLParser(operators).parse(rsql);
        } catch (final IllegalArgumentException e) {
            throw new RSQLParameterSyntaxException("rsql filter must not be null", e);
        } catch (final RSQLParserException e) {
            throw new RSQLParameterSyntaxException(e);
        }
    }


    private static final class ValidationRSQLVisitor<A extends Enum<A> & FieldNameProvider>
            extends AbstractFieldNameRSQLVisitor<A> implements RSQLVisitor<Boolean, String> {


        public ValidationRSQLVisitor(final Class<A> fieldNameProvider) {
            this.fieldNameProvider = fieldNameProvider;
        }

        @Override
        public Boolean visit(AndNode node, String param) {
            return visitNode(node, param);
        }

        @Override
        public Boolean visit(OrNode node, String param) {
            return visitNode(node, param);
        }

        @Override
        public Boolean visit(ComparisonNode node, String param) {
            try {
                final A fieldName = (A) getFieldEnumByName(node);
                getAndValidatePropertyFieldName(fieldName, node);
            } catch (final RSQLParameterUnsupportedFieldException | IllegalArgumentException e) {
                return false;
            }
            return true;
        }

        private Boolean visitNode(LogicalNode node, String param) {
            for (Node child : node.getChildren()) {
                if (!child.accept(this, param))
                    return false;
            }
            return true;
        }
    }

    private static final class RSQLSpecification<A extends Enum<A> & FieldNameProvider, T> implements Specification<T> {

        private static final long serialVersionUID = 1L;

        private final String rsql;
        private final Class<A> enumType;
        private final VirtualPropertyReplacer virtualPropertyReplacer;
        private final Database database;

        private RSQLSpecification(final String rsql, final Class<A> enumType,
                final VirtualPropertyReplacer virtualPropertyReplacer, final Database database) {
            this.rsql = rsql;
            this.enumType = enumType;
            this.virtualPropertyReplacer = virtualPropertyReplacer;
            this.database = database;
        }

        @Override
        public Predicate toPredicate(final Root<T> root, final CriteriaQuery<?> query, final CriteriaBuilder cb) {
            final Node rootNode = parseRsql(rsql);
            query.distinct(true);

            final JpqQueryRSQLVisitor<A, T> jpqQueryRSQLVisitor = new JpqQueryRSQLVisitor<>(root, cb, enumType,
                    virtualPropertyReplacer, database, query);
            final List<Predicate> accept = rootNode.<List<Predicate>, String> accept(jpqQueryRSQLVisitor);

            if (!CollectionUtils.isEmpty(accept)) {
                return cb.and(accept.toArray(new Predicate[accept.size()]));
            }
            return cb.conjunction();

        }
    }

    /**
     * An implementation of the {@link RSQLVisitor} to visit the parsed tokens
     * and build jpa where clauses.
     *
     *
     *
     * @param <A>
     *            the enum for providing the field name of the entity field to
     *            filter on.
     * @param <T>
     *            the entity type referenced by the root
     */
    private static final class JpqQueryRSQLVisitor<A extends Enum<A> & FieldNameProvider, T>
            extends AbstractFieldNameRSQLVisitor<A> implements RSQLVisitor<List<Predicate>, String> {
        private static final char ESCAPE_CHAR = '\\';
        private static final List<String> NO_JOINS_OPERATOR = Lists.newArrayList("!=", "=out=");

        public static final Character LIKE_WILDCARD = '*';

        private final Root<T> root;
        private final CriteriaBuilder cb;
        private final CriteriaQuery<?> query;
        private final VirtualPropertyReplacer virtualPropertyReplacer;
        private int level;
        private boolean isOrLevel;
        private final Map<Integer, Set<Join<Object, Object>>> joinsInLevel = new HashMap<>(3);
        private boolean joinsNeeded;

        private final SimpleTypeConverter simpleTypeConverter;

        private final Database database;

        private JpqQueryRSQLVisitor(final Root<T> root, final CriteriaBuilder cb, final Class<A> enumType,
                final VirtualPropertyReplacer virtualPropertyReplacer, final Database database,
                final CriteriaQuery<?> query) {
            this.root = root;
            this.cb = cb;
            this.query = query;
            this.fieldNameProvider = enumType;
            this.virtualPropertyReplacer = virtualPropertyReplacer;
            this.simpleTypeConverter = new SimpleTypeConverter();
            this.database = database;
            this.joinsNeeded = false;
        }

        private void beginLevel(final boolean isOr) {
            level++;
            isOrLevel = isOr;
            joinsInLevel.put(level, new HashSet<>(2));
        }

        private void endLevel() {
            joinsInLevel.remove(level);
            level--;
            isOrLevel = false;
        }

        private Set<Join<Object, Object>> getCurrentJoins() {
            if (level > 0) {
                return joinsInLevel.get(level);
            }
            return Collections.emptySet();
        }

        private Optional<Join<Object, Object>> findCurrentJoinOfType(final Class<?> type) {
            return getCurrentJoins().stream().filter(j -> type.equals(j.getJavaType())).findAny();
        }

        private void addCurrentJoin(final Join<Object, Object> join) {
            if (level > 0) {
                getCurrentJoins().add(join);
            }
        }

        @Override
        public List<Predicate> visit(final AndNode node, final String param) {
            beginLevel(false);
            final List<Predicate> childs = acceptChilds(node);
            endLevel();
            if (!childs.isEmpty()) {
                return toSingleList(cb.and(childs.toArray(new Predicate[childs.size()])));
            }
            return toSingleList(cb.conjunction());
        }

        @Override
        public List<Predicate> visit(final OrNode node, final String param) {
            beginLevel(true);
            final List<Predicate> childs = acceptChilds(node);
            endLevel();
            if (!childs.isEmpty()) {
                return toSingleList(cb.or(childs.toArray(new Predicate[childs.size()])));
            }
            return toSingleList(cb.conjunction());
        }

        private static List<Predicate> toSingleList(final Predicate predicate) {
            return Collections.singletonList(predicate);
        }


        /**
         * Resolves the Path for a field in the persistence layer and joins the
         * required models. This operation is part of a tree traversal through
         * an RSQL expression. It creates for every field that is not part of
         * the root model a join to the foreign model. This behavior is
         * optimized when several joins happen directly under an OR node in the
         * traversed tree. The same foreign model is only joined once.
         *
         * Example: tags.name==M;(tags.name==A,tags.name==B,tags.name==C) This
         * example joins the tags model only twice, because for the OR node in
         * brackets only one join is used.
         *
         * @param enumField
         *            field from a FieldNameProvider to resolve on the
         *            persistence layer
         * @param finalProperty
         *            dot notated field path
         * @return the Path for a field
         */
        private Path<Object> getFieldPath(final A enumField, final String finalProperty) {
            return (Path<Object>) getFieldPath(root, getSubAttributesFrom(finalProperty), enumField.isMap(),
                    this::getJoinFieldPath).orElseThrow(
                            () -> new RSQLParameterUnsupportedFieldException("RSQL field path cannot be empty", null));
        }

        private Path<?> getJoinFieldPath(final Path<?> fieldPath, final String fieldNameSplit) {
            if (fieldPath instanceof PluralJoin) {
                final Join<Object, ?> join = (Join<Object, ?>) fieldPath;
                final From<?, Object> joinParent = join.getParent();
                final Optional<Join<Object, Object>> currentJoinOfType = findCurrentJoinOfType(join.getJavaType());
                if (currentJoinOfType.isPresent() && isOrLevel) {
                    // remove the additional join and use the existing one
                    joinParent.getJoins().remove(join);
                    return currentJoinOfType.get();
                } else {
                    final Join<Object, Object> newJoin = joinParent.join(fieldNameSplit, JoinType.LEFT);
                    addCurrentJoin(newJoin);
                    return newJoin;
                }
            }
            return fieldPath;
        }

        private static Optional<Path<?>> getFieldPath(final Root<?> root, final String[] split,
                final boolean isMapKeyField, final BiFunction<Path<?>, String, Path<?>> joinFieldPathProvider) {
            Path<?> fieldPath = null;
            for (int i = 0; i < split.length; i++) {
                if (!(isMapKeyField && i == (split.length - 1))) {
                    final String fieldNameSplit = split[i];
                    fieldPath = (fieldPath != null) ? fieldPath.get(fieldNameSplit) : root.get(fieldNameSplit);
                    fieldPath = joinFieldPathProvider.apply(fieldPath, fieldNameSplit);
                }
            }
            return Optional.ofNullable(fieldPath);
        }

        @Override
        // Exception squid:S2095 - see
        // https://jira.sonarsource.com/browse/SONARJAVA-1478
        @SuppressWarnings({ "squid:S2095" })
        public List<Predicate> visit(final ComparisonNode node, final String param) {
            A fieldName = null;
            try {
                fieldName = (A) getFieldEnumByName(node);
            } catch (final IllegalArgumentException e) {
                throw createRSQLParameterUnsupportedException(node, e);
            }

            final String finalProperty = getAndValidatePropertyFieldName(fieldName, node);

            final List<String> values = node.getArguments();
            final List<Object> transformedValues = new ArrayList<>();
            final Path<Object> fieldPath = getFieldPath(fieldName, finalProperty);

            for (final String value : values) {
                transformedValues.add(convertValueIfNecessary(node, fieldName, value, fieldPath));
            }

            this.joinsNeeded = this.joinsNeeded || areJoinsNeeded(node);

            return mapToPredicate(node, fieldPath, node.getArguments(), transformedValues, fieldName, finalProperty);
        }

        private static boolean areJoinsNeeded(final ComparisonNode node) {
            return !NO_JOINS_OPERATOR.contains(node.getOperator().getSymbol());
        }

        private Object convertValueIfNecessary(final ComparisonNode node, final A fieldName, final String value,
                final Path<Object> fieldPath) {
            // in case the value of an rsql query e.g. type==application is an
            // enum we need to handle it separately because JPA needs the
            // correct java-type to build an expression. So String and numeric
            // values JPA can do it by it's own but not for classes like enums.
            // So we need to transform the given value string into the enum
            // class.
            final Class<?> javaType = fieldPath.getJavaType();
            if (javaType != null && javaType.isEnum()) {
                return transformEnumValue(node, value, javaType);
            }
            if (fieldName instanceof FieldValueConverter) {
                return convertFieldConverterValue(node, fieldName, value);
            }

            if (Boolean.TYPE.equals(javaType)) {
                return convertBooleanValue(node, value, javaType);
            }

            return value;
        }

        private Object convertBooleanValue(final ComparisonNode node, final String value, final Class<?> javaType) {
            try {
                return simpleTypeConverter.convertIfNecessary(value, javaType);
            } catch (final TypeMismatchException e) {
                throw new RSQLParameterSyntaxException(
                        "The value of the given search parameter field {" + node.getSelector()
                                + "} is not well formed. Only a boolean (true or false) value will be expected {",
                        e);
            }
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private Object convertFieldConverterValue(final ComparisonNode node, final A fieldName, final String value) {
            final Object convertedValue = ((FieldValueConverter) fieldName).convertValue(fieldName, value);
            if (convertedValue == null) {
                throw new RSQLParameterUnsupportedFieldException(
                        "field {" + node.getSelector() + "} must be one of the following values {"
                                + Arrays.toString(((FieldValueConverter) fieldName).possibleValues(fieldName)) + "}",
                        null);
            } else {
                return convertedValue;
            }
        }

        // Exception squid:S2095 - see
        // https://jira.sonarsource.com/browse/SONARJAVA-1478
        @SuppressWarnings({ "rawtypes", "unchecked", "squid:S2095" })
        private static Object transformEnumValue(final ComparisonNode node, final String value,
                final Class<?> javaType) {
            final Class<? extends Enum> tmpEnumType = (Class<? extends Enum>) javaType;
            try {
                return Enum.valueOf(tmpEnumType, value.toUpperCase());
            } catch (final IllegalArgumentException e) {
                // we could not transform the given string value into the enum
                // type, so ignore it and return null and do not filter
                LOGGER.info("given value {} cannot be transformed into the correct enum type {}", value.toUpperCase(),
                        javaType);
                LOGGER.debug("value cannot be transformed to an enum", e);

                throw new RSQLParameterUnsupportedFieldException("field {" + node.getSelector()
                        + "} must be one of the following values {" + Arrays.stream(tmpEnumType.getEnumConstants())
                                .map(v -> v.name().toLowerCase()).collect(Collectors.toList())
                        + "}", e);
            }
        }

        private List<Predicate> mapToPredicate(final ComparisonNode node, final Path<Object> fieldPath,
                final List<String> values, final List<Object> transformedValues, final A enumField,
                final String finalProperty) {

            String value = values.get(0);
            // if lookup is available, replace macros ...
            if (virtualPropertyReplacer != null) {
                value = virtualPropertyReplacer.replace(value);
            }

            final Predicate mapPredicate = mapToMapPredicate(node, fieldPath, enumField);

            final Predicate valuePredicate = addOperatorPredicate(node, getMapValueFieldPath(enumField, fieldPath),
                    transformedValues, value, finalProperty, enumField);

            return toSingleList(mapPredicate != null ? cb.and(mapPredicate, valuePredicate) : valuePredicate);
        }

        private Predicate addOperatorPredicate(final ComparisonNode node, final Path<Object> fieldPath,
                final List<Object> transformedValues, final String value, final String finalProperty,
                final A enumField) {

            // only 'equal' and 'notEqual' can handle transformed value like
            // enums. The JPA API cannot handle object types for greaterThan etc
            // methods.
            final Object transformedValue = transformedValues.get(0);
            final String operator = node.getOperator().getSymbol();

            switch (operator) {
            case "==":
                return getEqualToPredicate(transformedValue, fieldPath);
            case "!=":
                return getNotEqualToPredicate(transformedValue, fieldPath, finalProperty, enumField);
            case "=gt=":
                return cb.greaterThan(pathOfString(fieldPath), value);
            case "=ge=":
                return cb.greaterThanOrEqualTo(pathOfString(fieldPath), value);
            case "=lt=":
                return cb.lessThan(pathOfString(fieldPath), value);
            case "=le=":
                return cb.lessThanOrEqualTo(pathOfString(fieldPath), value);
            case "=in=":
                return getInPredicate(transformedValues, fieldPath);
            case "=out=":
                return getOutPredicate(transformedValues, finalProperty, enumField, fieldPath);
            default:
                throw new RSQLParameterSyntaxException(
                        "operator symbol {" + operator + "} is either not supported or not implemented");
            }
        }

        private Predicate getInPredicate(final List<Object> transformedValues, final Path<Object> fieldPath) {
            final List<String> inParams = new ArrayList<>();
            for (final Object param : transformedValues) {
                if (param instanceof String) {
                    inParams.add(((String) param).toUpperCase());
                }
            }
            if (!inParams.isEmpty()) {
                return cb.upper(pathOfString(fieldPath)).in(inParams);
            } else {
                return fieldPath.in(transformedValues);

            }
        }

        private Predicate getOutPredicate(final List<Object> transformedValues, final String finalProperty,
                final A enumField, final Path<Object> fieldPath) {

            final String[] fieldNames = getSubAttributesFrom(finalProperty);
            final List<String> outParams = transformedValues.stream().filter(String.class::isInstance)
                    .map(String.class::cast).map(String::toUpperCase).collect(Collectors.toList());

            if (isSimpleField(fieldNames, enumField.isMap())) {
                return toNullOrNotInPredicate(fieldPath, transformedValues, outParams);
            }

            clearOuterJoinsIfNotNeeded();

            return toOutWithSubQueryPredicate(fieldNames, transformedValues, enumField, outParams);
        }

        private Predicate toNullOrNotInPredicate(final Path<Object> fieldPath, final List<Object> transformedValues,
                final List<String> outParams) {

            final Path<String> pathOfString = pathOfString(fieldPath);
            final Predicate inPredicate = outParams.isEmpty() ? fieldPath.in(transformedValues)
                    : cb.upper(pathOfString).in(outParams);

            return cb.or(cb.isNull(pathOfString), cb.not(inPredicate));
        }

        private Predicate toOutWithSubQueryPredicate(final String[] fieldNames, final List<Object> transformedValues,
                final A enumField, final List<String> outParams) {
            final Function<Expression<String>, Predicate> inPredicateProvider = expressionToCompare -> outParams
                    .isEmpty() ? cb.upper(expressionToCompare).in(transformedValues)
                            : cb.upper(expressionToCompare).in(outParams);
            return toNotExistsSubQueryPredicate(fieldNames, enumField, inPredicateProvider);
        }

        private Path<Object> getMapValueFieldPath(final A enumField, final Path<Object> fieldPath) {
            final String valueFieldNameFromSubEntity = enumField.getSubEntityMapTuple().map(Entry::getValue)
                    .orElse(null);

            if (!enumField.isMap() || valueFieldNameFromSubEntity == null) {
                return fieldPath;
            }
            return fieldPath.get(valueFieldNameFromSubEntity);
        }

        @SuppressWarnings("unchecked")
        private Predicate mapToMapPredicate(final ComparisonNode node, final Path<Object> fieldPath,
                final A enumField) {
            if (!enumField.isMap()) {
                return null;
            }

            final String[] graph = getSubAttributesFrom(node.getSelector());

            final String keyValue = graph[graph.length - 1];
            if (fieldPath instanceof MapJoin) {
                // Currently we support only string key .So below cast is safe.
                return cb.equal(cb.upper((Expression<String>) (((MapJoin<?, ?, ?>) fieldPath).key())),
                        keyValue.toUpperCase());
            }

            final String keyFieldName = enumField.getSubEntityMapTuple().map(Entry::getKey)
                    .orElseThrow(() -> new UnsupportedOperationException(
                            "For the fields, defined as Map, only Map java type or tuple in the form of SimpleImmutableEntry are allowed. Neither of those could be found!"));

            return cb.equal(cb.upper(fieldPath.get(keyFieldName)), keyValue.toUpperCase());
        }

        private Predicate getEqualToPredicate(final Object transformedValue, final Path<Object> fieldPath) {
            if (transformedValue == null) {
                return cb.isNull(pathOfString(fieldPath));
            }

            if ((transformedValue instanceof String) && !NumberUtils.isCreatable((String) transformedValue)) {
                if (StringUtils.isEmpty(transformedValue)) {
                    return cb.or(cb.isNull(pathOfString(fieldPath)), cb.equal(pathOfString(fieldPath), ""));
                }

                final String sqlValue = toSQL((String) transformedValue);
                return cb.like(cb.upper(pathOfString(fieldPath)), sqlValue, ESCAPE_CHAR);
            }

            return cb.equal(fieldPath, transformedValue);
        }

        private Predicate getNotEqualToPredicate(final Object transformedValue, final Path<Object> fieldPath,
                final String finalProperty, final A enumField) {

            if (transformedValue == null) {
                return toNotNullPredicate(fieldPath);
            }

            if ((transformedValue instanceof String) && !NumberUtils.isCreatable((String) transformedValue)) {
                if (StringUtils.isEmpty(transformedValue)) {
                    return toNotNullAndNotEmptyPredicate(fieldPath);
                }

                final String sqlValue = toSQL((String) transformedValue);
                final String[] fieldNames = getSubAttributesFrom(finalProperty);

                if (isSimpleField(fieldNames, enumField.isMap())) {
                    return toNullOrNotLikePredicate(fieldPath, sqlValue);
                }

                clearOuterJoinsIfNotNeeded();

                return toNotEqualWithSubQueryPredicate(enumField, sqlValue, fieldNames);
            }

            return toNullOrNotEqualPredicate(fieldPath, transformedValue);
        }

        private void clearOuterJoinsIfNotNeeded() {
            if (!joinsNeeded) {
                root.getJoins().clear();
            }
        }

        private Predicate toNotNullPredicate(final Path<Object> fieldPath) {
            return cb.isNotNull(pathOfString(fieldPath));
        }

        private Predicate toNullOrNotLikePredicate(final Path<Object> fieldPath, final String sqlValue) {
            return cb.or(cb.isNull(pathOfString(fieldPath)),
                    cb.notLike(cb.upper(pathOfString(fieldPath)), sqlValue, ESCAPE_CHAR));
        }

        private Predicate toNullOrNotEqualPredicate(final Path<Object> fieldPath, final Object transformedValue) {
            return cb.or(cb.isNull(pathOfString(fieldPath)), cb.notEqual(fieldPath, transformedValue));
        }

        private Predicate toNotNullAndNotEmptyPredicate(final Path<Object> fieldPath) {
            return cb.and(cb.isNotNull(pathOfString(fieldPath)), cb.notEqual(pathOfString(fieldPath), ""));
        }

        private Predicate toNotEqualWithSubQueryPredicate(final A enumField, final String sqlValue,
                final String[] fieldNames) {
            final Function<Expression<String>, Predicate> likePredicateProvider = expressionToCompare -> cb
                    .like(cb.upper(expressionToCompare), sqlValue);
            return toNotExistsSubQueryPredicate(fieldNames, enumField, likePredicateProvider);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private Predicate toNotExistsSubQueryPredicate(final String[] fieldNames, final A enumField,
                final Function<Expression<String>, Predicate> subQueryPredicateProvider) {
            final Class<?> javaType = root.getJavaType();
            final Subquery<?> subquery = query.subquery(javaType);
            final Root subqueryRoot = subquery.from(javaType);
            final Predicate equalPredicate = cb.equal(root.get(enumField.identifierFieldName()),
                    subqueryRoot.get(enumField.identifierFieldName()));
            final Path innerFieldPath = getInnerFieldPath(subqueryRoot, fieldNames, enumField.isMap());
            final Expression<String> expressionToCompare = getExpressionToCompare(innerFieldPath, enumField);
            final Predicate subQueryPredicate = subQueryPredicateProvider.apply(expressionToCompare);
            subquery.select(subqueryRoot).where(cb.and(equalPredicate, subQueryPredicate));
            return cb.not(cb.exists(subquery));
        }

        private static boolean isSimpleField(final String[] split, final boolean isMapKeyField) {
            return split.length == 1 || (split.length == 2 && isMapKeyField);
        }

        private Expression<String> getExpressionToCompare(final Path innerFieldPath, final A enumField) {
            if (!enumField.isMap()) {
                return pathOfString(innerFieldPath);
            }
            if (innerFieldPath instanceof MapJoin) {
                // Currently we support only string key .So below cast
                // is safe.
                return (Expression<String>) (((MapJoin<?, ?, ?>) pathOfString(innerFieldPath)).value());
            }
            final String valueFieldName = enumField.getSubEntityMapTuple().map(Entry::getValue)
                    .orElseThrow(() -> new UnsupportedOperationException(
                            "For the fields, defined as Map, only Map java type or tuple in the form of SimpleImmutableEntry are allowed. Neither of those could be found!"));
            return pathOfString(innerFieldPath).get(valueFieldName);
        }

        private static Path<?> getInnerFieldPath(final Root<?> subqueryRoot, final String[] split,
                final boolean isMapKeyField) {
            return getFieldPath(subqueryRoot, split, isMapKeyField,
                    (fieldPath, fieldNameSplit) -> getInnerJoinFieldPath(subqueryRoot, fieldPath, fieldNameSplit))
                            .orElseThrow(() -> new RSQLParameterUnsupportedFieldException(
                                    "RSQL field path cannot be empty", null));
        }

        private static Path<?> getInnerJoinFieldPath(final Root<?> subqueryRoot, final Path<?> fieldPath,
                final String fieldNameSplit) {
            if (fieldPath instanceof Join) {
                return subqueryRoot.join(fieldNameSplit, JoinType.INNER);
            }
            return fieldPath;
        }

        private String toSQL(final String transformedValue) {
            final String escaped;

            if (database == Database.SQL_SERVER) {
                escaped = transformedValue.replace("%", "[%]").replace("_", "[_]");
            } else {
                escaped = transformedValue.replace("%", ESCAPE_CHAR + "%").replace("_", ESCAPE_CHAR + "_");
            }

            return escaped.replace(LIKE_WILDCARD, '%').toUpperCase();
        }

        @SuppressWarnings("unchecked")
        private static <Y> Path<Y> pathOfString(final Path<?> path) {
            return (Path<Y>) path;
        }

        private List<Predicate> acceptChilds(final LogicalNode node) {
            final List<Node> children = node.getChildren();
            final List<Predicate> childs = new ArrayList<>();
            for (final Node node2 : children) {
                final List<Predicate> accept = node2.accept(this);
                if (!CollectionUtils.isEmpty(accept)) {
                    childs.addAll(accept);
                } else {
                    LOGGER.debug("visit logical node children but could not parse it, ignoring {}", node2);
                }
            }
            return childs;
        }

    }

}
