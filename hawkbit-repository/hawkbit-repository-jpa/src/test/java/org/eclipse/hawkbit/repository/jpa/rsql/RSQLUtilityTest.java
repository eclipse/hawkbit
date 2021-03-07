/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.rsql;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.persistence.metamodel.Attribute;

import org.eclipse.hawkbit.repository.DistributionSetFields;
import org.eclipse.hawkbit.repository.FieldNameProvider;
import org.eclipse.hawkbit.repository.SoftwareModuleFields;
import org.eclipse.hawkbit.repository.TargetFields;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.exception.RSQLParameterSyntaxException;
import org.eclipse.hawkbit.repository.exception.RSQLParameterUnsupportedFieldException;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.TenantConfigurationValue;
import org.eclipse.hawkbit.repository.model.helper.TenantConfigurationManagementHolder;
import org.eclipse.hawkbit.repository.rsql.VirtualPropertyReplacer;
import org.eclipse.hawkbit.repository.rsql.VirtualPropertyResolver;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.test.context.junit4.SpringRunner;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

import java.util.Arrays;
import java.util.List;

@RunWith(SpringRunner.class)
@Feature("Component Tests - Repository")
@Story("RSQL search utility")
// TODO: fully document tests -> @Description for long text and reasonable
// method name as short text
public class RSQLUtilityTest {

    @Spy
    private final VirtualPropertyResolver macroResolver = new VirtualPropertyResolver();

    @MockBean
    private TenantConfigurationManagement confMgmt;

    @Mock
    private Root<Object> baseSoftwareModuleRootMock;

    @Mock
    private CriteriaQuery<SoftwareModule> criteriaQueryMock;
    @Mock
    private CriteriaBuilder criteriaBuilderMock;

    @Mock
    private Subquery<SoftwareModule> subqueryMock;
    @Mock
    private Root<SoftwareModule> subqueryRootMock;

    private final Database testDb = Database.H2;

    @Mock
    private Attribute attribute;

    @Configuration
    static class Config {
        @Bean
        TenantConfigurationManagementHolder tenantConfigurationManagementHolder() {
            return TenantConfigurationManagementHolder.getInstance();
        }
    }

    private static final TenantConfigurationValue<String> TEST_POLLING_TIME_INTERVAL = TenantConfigurationValue
            .<String> builder().value("00:05:00").build();
    private static final TenantConfigurationValue<String> TEST_POLLING_OVERDUE_TIME_INTERVAL = TenantConfigurationValue
            .<String> builder().value("00:07:37").build();

    @Test
    @Description("Testing throwing exception in case of not allowed RSQL key")
    public void rsqlUnsupportedFieldExceptionTest(){
        final String rsql1 = "wrongfield == abcd";
        assertThatExceptionOfType(RSQLParameterUnsupportedFieldException.class)
                .isThrownBy(()-> RSQLUtility.isValid(rsql1, TestFieldEnum.class));

        final String rsql2 = "wrongfield == abcd or TESTFIELD_WITH_SUB_ENTITIES.subentity11 == 0123";
        assertThatExceptionOfType(RSQLParameterUnsupportedFieldException.class)
                .isThrownBy(()-> RSQLUtility.isValid(rsql2, TestFieldEnum.class));
    }

    @Test
    @Description("Testing exception in case of not allowed subkey")
    public void rsqlUnsupportedSubkeyThrowException(){
        final String rsql1 = "TESTFIELD_WITH_SUB_ENTITIES.unsupported == abcd and TESTFIELD_WITH_SUB_ENTITIES.subentity22 == 0123";
        assertThatExceptionOfType(RSQLParameterUnsupportedFieldException.class)
                .isThrownBy(()-> RSQLUtility.isValid(rsql1, TestFieldEnum.class));

        final String rsql2 = "TESTFIELD_WITH_SUB_ENTITIES.unsupported == abcd or TESTFIELD_WITH_SUB_ENTITIES.subentity22 == 0123";
        assertThatExceptionOfType(RSQLParameterUnsupportedFieldException.class)
                .isThrownBy(()-> RSQLUtility.isValid(rsql2, TestFieldEnum.class));

        final String rsql3 = "TESTFIELD == abcd or TESTFIELD_WITH_SUB_ENTITIES.unsupported == 0123";
        assertThatExceptionOfType(RSQLParameterUnsupportedFieldException.class)
                .isThrownBy(()-> RSQLUtility.isValid(rsql3, TestFieldEnum.class));
    }

    @Test
    @Description("Testing valid RSQL keys based on TestFieldEnum.class")
    public void rsqlFieldValidation(){
        final String rsql1 = "TESTFIELD_WITH_SUB_ENTITIES.subentity11 == abcd and TESTFIELD_WITH_SUB_ENTITIES.subentity22 == 0123";
        final String rsql2 = "TESTFIELD_WITH_SUB_ENTITIES.subentity11 == abcd or TESTFIELD_WITH_SUB_ENTITIES.subentity22 == 0123";
        final String rsql3 = "TESTFIELD_WITH_SUB_ENTITIES.subentity11 == abcd and TESTFIELD_WITH_SUB_ENTITIES.subentity22 == 0123 and TESTFIELD == any";

        assertTrue("All fields should be valid", RSQLUtility.isValid(rsql1, TestFieldEnum.class));
        assertTrue("All fields should be valid", RSQLUtility.isValid(rsql2, TestFieldEnum.class));
        assertTrue("All fields should be valid", RSQLUtility.isValid(rsql3, TestFieldEnum.class));
    }

    @Test
    public void wrongRsqlSyntaxThrowSyntaxException() {
        final String wrongRSQL = "name==abc;d";
        try {
            RSQLUtility.parse(wrongRSQL, SoftwareModuleFields.class, null, testDb)
                    .toPredicate(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
            fail("Missing expected RSQLParameterSyntaxException because of wrong RSQL syntax");
        } catch (final RSQLParameterSyntaxException e) {
        }
    }

    @Test
    public void wrongFieldThrowUnsupportedFieldException() {
        final String wrongRSQL = "unknownField==abc";
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) SoftwareModule.class);
        try {
            RSQLUtility.parse(wrongRSQL, SoftwareModuleFields.class, null, testDb)
                    .toPredicate(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
            fail("Missing an expected RSQLParameterUnsupportedFieldException because of unknown RSQL field");
        } catch (final RSQLParameterUnsupportedFieldException e) {
        }

    }

    @Test
    public void wrongRsqlMapSyntaxThrowSyntaxException() {
        String wrongRSQL = TargetFields.ATTRIBUTE + "==abc";
        try {
            RSQLUtility.parse(wrongRSQL, TargetFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                    criteriaQueryMock, criteriaBuilderMock);
            fail("Missing expected RSQLParameterSyntaxException for target attributes map, caused by wrong RSQL syntax (key was not present)");
        } catch (final RSQLParameterUnsupportedFieldException e) {
        }

        wrongRSQL = TargetFields.ATTRIBUTE + ".unknown.wrong==abc";
        try {
            RSQLUtility.parse(wrongRSQL, TargetFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                    criteriaQueryMock, criteriaBuilderMock);
            fail("Missing expected RSQLParameterSyntaxException for target attributes map, caused by wrong RSQL syntax (key includes dots)");
        } catch (final RSQLParameterUnsupportedFieldException e) {
        }

        wrongRSQL = TargetFields.METADATA + ".unknown.wrong==abc";
        try {
            RSQLUtility.parse(wrongRSQL, TargetFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                    criteriaQueryMock, criteriaBuilderMock);
            fail("Missing expected RSQLParameterSyntaxException for target metadata map, caused by wrong RSQL syntax (key includes dots)");
        } catch (final RSQLParameterUnsupportedFieldException e) {
        }

        wrongRSQL = DistributionSetFields.METADATA + "==abc";
        try {
            RSQLUtility.parse(wrongRSQL, DistributionSetFields.class, null, testDb)
                    .toPredicate(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
            fail("Missing expected RSQLParameterSyntaxException for distribution set metadata map, caused by wrong RSQL syntax (key was not present)");
        } catch (final RSQLParameterUnsupportedFieldException e) {
        }

    }

    @Test
    public void wrongRsqlSubEntitySyntaxThrowSyntaxException() {
        String wrongRSQL = TargetFields.ASSIGNEDDS + "==abc";
        try {
            RSQLUtility.parse(wrongRSQL, TargetFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                    criteriaQueryMock, criteriaBuilderMock);
            fail("Missing expected RSQLParameterSyntaxException because of wrong RSQL syntax");
        } catch (final RSQLParameterUnsupportedFieldException e) {
        }

        wrongRSQL = TargetFields.ASSIGNEDDS + ".unknownField==abc";
        try {
            RSQLUtility.parse(wrongRSQL, TargetFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                    criteriaQueryMock, criteriaBuilderMock);
            fail("Missing expected RSQLParameterSyntaxException because of wrong RSQL syntax");
        } catch (final RSQLParameterUnsupportedFieldException e) {
        }

        wrongRSQL = TargetFields.ASSIGNEDDS + ".unknownField.ToMuch==abc";
        try {
            RSQLUtility.parse(wrongRSQL, TargetFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                    criteriaQueryMock, criteriaBuilderMock);
            fail("Missing expected RSQLParameterSyntaxException because of wrong RSQL syntax");
        } catch (final RSQLParameterUnsupportedFieldException e) {
        }
    }

    @Test
    public <T> void correctRsqlBuildsPredicate() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        final String correctRsql = "name==abc;version==1.2";
        when(baseSoftwareModuleRootMock.get("name")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.get("version")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) SoftwareModule.class);
        when(criteriaBuilderMock.upper(eq(pathOfString(baseSoftwareModuleRootMock))))
                .thenReturn(pathOfString(baseSoftwareModuleRootMock));
        when(criteriaBuilderMock.like(any(Expression.class), anyString(), eq('\\'))).thenReturn(mock(Predicate.class));
        when(criteriaBuilderMock.equal(any(Expression.class), any(String.class))).thenReturn(mock(Predicate.class));

        // test
        RSQLUtility.parse(correctRsql, SoftwareModuleFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                criteriaQueryMock, criteriaBuilderMock);

        // verification
        verify(criteriaBuilderMock, times(1)).and(any(Predicate.class));
    }

    @Test
    public void correctRsqlBuildsSimpleNotLikePredicate() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        final String correctRsql = "name!=abc";
        when(baseSoftwareModuleRootMock.get("name")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) SoftwareModule.class);

        when(criteriaBuilderMock.isNull(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(criteriaBuilderMock.notLike(any(Expression.class), anyString(), eq('\\')))
                .thenReturn(mock(Predicate.class));
        when(criteriaBuilderMock.upper(eq(pathOfString(baseSoftwareModuleRootMock))))
                .thenReturn(pathOfString(baseSoftwareModuleRootMock));

        // test
        RSQLUtility.parse(correctRsql, SoftwareModuleFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                criteriaQueryMock, criteriaBuilderMock);

        // verification
        verify(criteriaBuilderMock, times(1)).or(any(Predicate.class), any(Predicate.class));
        verify(criteriaBuilderMock, times(1)).isNull(eq(pathOfString(baseSoftwareModuleRootMock)));
        verify(criteriaBuilderMock, times(1)).notLike(eq(pathOfString(baseSoftwareModuleRootMock)),
                eq("abc".toUpperCase()), eq('\\'));
    }

    @Test
    public void correctRsqlBuildsNotSimpleNotLikePredicate() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        // with this query a subquery has to be made, so it is no simple query
        final String correctRsql = "type!=abc";
        when(baseSoftwareModuleRootMock.get(anyString())).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) SoftwareModule.class);

        final Path pathMock = mock(Path.class);
        when(pathMock.get(anyString())).thenReturn(pathMock);
        when(subqueryRootMock.get(anyString())).thenReturn(pathMock);

        when(criteriaBuilderMock.and(any(), any())).thenReturn(mock(Predicate.class));

        when(criteriaQueryMock.subquery(SoftwareModule.class)).thenReturn(subqueryMock);

        when(subqueryMock.from(SoftwareModule.class)).thenReturn(subqueryRootMock);
        when(subqueryMock.select(subqueryRootMock)).thenReturn(subqueryMock);

        // test
        RSQLUtility.parse(correctRsql, SoftwareModuleFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                criteriaQueryMock, criteriaBuilderMock);

        // verification
        verify(criteriaBuilderMock, times(1)).not(criteriaBuilderMock.exists(eq(subqueryMock)));
    }

    @Test
    public void correctRsqlBuildsLikePredicateWithPercentage() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        final String correctRsql = "name==a%";
        when(baseSoftwareModuleRootMock.get("name")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) SoftwareModule.class);
        when(criteriaBuilderMock.like(any(Expression.class), anyString(), eq('\\'))).thenReturn(mock(Predicate.class));
        when(criteriaBuilderMock.<String> greaterThanOrEqualTo(any(Expression.class), any(String.class)))
                .thenReturn(mock(Predicate.class));
        when(criteriaBuilderMock.upper(eq(pathOfString(baseSoftwareModuleRootMock))))
                .thenReturn(pathOfString(baseSoftwareModuleRootMock));
        // test
        RSQLUtility.parse(correctRsql, SoftwareModuleFields.class, null, Database.H2)
                .toPredicate(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);

        // verification
        verify(criteriaBuilderMock, times(1)).and(any(Predicate.class));
        verify(criteriaBuilderMock, times(1)).like(eq(pathOfString(baseSoftwareModuleRootMock)),
                eq("a\\%".toUpperCase()), eq('\\'));
    }

    @Test
    public void correctRsqlBuildsLikePredicateWithPercentageSQLServer() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        final String correctRsql = "name==a%";
        when(baseSoftwareModuleRootMock.get("name")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) SoftwareModule.class);
        when(criteriaBuilderMock.upper(eq(pathOfString(baseSoftwareModuleRootMock))))
                .thenReturn(pathOfString(baseSoftwareModuleRootMock));
        when(criteriaBuilderMock.like(any(Expression.class), anyString(), eq('\\'))).thenReturn(mock(Predicate.class));
        when(criteriaBuilderMock.<String> greaterThanOrEqualTo(any(Expression.class), any(String.class)))
                .thenReturn(mock(Predicate.class));

        // test
        RSQLUtility.parse(correctRsql, SoftwareModuleFields.class, null, Database.SQL_SERVER)
                .toPredicate(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);

        // verification
        verify(criteriaBuilderMock, times(1)).and(any(Predicate.class));
        verify(criteriaBuilderMock, times(1)).like(eq(pathOfString(baseSoftwareModuleRootMock)),
                eq("a[%]".toUpperCase()), eq('\\'));
    }

    @Test
    public void correctRsqlBuildsLessThanPredicate() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        final String correctRsql = "name=lt=abc";
        when(baseSoftwareModuleRootMock.get("name")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) SoftwareModule.class);
        when(criteriaBuilderMock.lessThan(any(Expression.class), anyString())).thenReturn(mock(Predicate.class));
        when(criteriaBuilderMock.<String> greaterThanOrEqualTo(any(Expression.class), any(String.class)))
                .thenReturn(mock(Predicate.class));
        // test
        RSQLUtility.parse(correctRsql, SoftwareModuleFields.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                criteriaQueryMock, criteriaBuilderMock);

        // verification
        verify(criteriaBuilderMock, times(1)).and(any(Predicate.class));
        verify(criteriaBuilderMock, times(1)).lessThan(eq(pathOfString(baseSoftwareModuleRootMock)), eq("abc"));
    }

    @Test
    public void correctRsqlWithEnumValue() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        final String correctRsql = "testfield==bumlux";
        when(baseSoftwareModuleRootMock.get("testfield")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) TestValueEnum.class);
        when(criteriaBuilderMock.equal(any(Root.class), any(TestValueEnum.class))).thenReturn(mock(Predicate.class));

        // test
        RSQLUtility.parse(correctRsql, TestFieldEnum.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                criteriaQueryMock, criteriaBuilderMock);

        // verification
        verify(criteriaBuilderMock, times(1)).and(any(Predicate.class));
        verify(criteriaBuilderMock, times(1)).equal(eq(baseSoftwareModuleRootMock), eq(TestValueEnum.BUMLUX));
    }

    @Test
    public void wrongRsqlWithWrongEnumValue() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        final String correctRsql = "testfield==unknownValue";
        when(baseSoftwareModuleRootMock.get("testfield")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) TestValueEnum.class);
        when(criteriaBuilderMock.equal(any(Root.class), anyString())).thenReturn(mock(Predicate.class));

        try {
            // test
            RSQLUtility.parse(correctRsql, TestFieldEnum.class, null, testDb).toPredicate(baseSoftwareModuleRootMock,
                    criteriaQueryMock, criteriaBuilderMock);
            fail("missing RSQLParameterUnsupportedFieldException for wrong enum value");
        } catch (final RSQLParameterUnsupportedFieldException e) {
            // nope expected
        }
    }

    @Test
    @Description("Tests the resolution of overdue_ts placeholder in context of a RSQL expression.")
    public void correctRsqlWithOverdueMacro() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        final String overdueProp = "overdue_ts";
        final String overduePropPlaceholder = "${" + overdueProp + "}";
        final String correctRsql = "testfield=le=" + overduePropPlaceholder;
        when(baseSoftwareModuleRootMock.get("testfield")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) String.class);
        when(criteriaBuilderMock.upper(eq(pathOfString(baseSoftwareModuleRootMock))))
                .thenReturn(pathOfString(baseSoftwareModuleRootMock));
        when(criteriaBuilderMock.like(any(Expression.class), anyString(), eq('\\'))).thenReturn(mock(Predicate.class));
        when(criteriaBuilderMock.<String> lessThanOrEqualTo(any(Expression.class), eq(overduePropPlaceholder)))
                .thenReturn(mock(Predicate.class));

        // test
        RSQLUtility.parse(correctRsql, TestFieldEnum.class, setupMacroLookup(), testDb)
                .toPredicate(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);

        // verification
        verify(macroResolver).lookup(overdueProp);
        // the macro is already replaced when passed to #lessThanOrEqualTo ->
        // the method is never invoked with the
        // placeholder:
        verify(criteriaBuilderMock, never()).lessThanOrEqualTo(eq(pathOfString(baseSoftwareModuleRootMock)),
                eq(overduePropPlaceholder));
    }

    @Test
    @Description("Tests RSQL expression with an unknown placeholder.")
    public void correctRsqlWithUnknownMacro() {
        reset(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);
        final String overdueProp = "unknown";
        final String overduePropPlaceholder = "${" + overdueProp + "}";
        final String correctRsql = "testfield=le=" + overduePropPlaceholder;
        when(baseSoftwareModuleRootMock.get("testfield")).thenReturn(baseSoftwareModuleRootMock);
        when(baseSoftwareModuleRootMock.getJavaType()).thenReturn((Class) String.class);
        when(criteriaBuilderMock.equal(any(Root.class), anyString())).thenReturn(mock(Predicate.class));
        when(criteriaBuilderMock.<String> lessThanOrEqualTo(any(Expression.class), eq(overduePropPlaceholder)))
                .thenReturn(mock(Predicate.class));

        // test
        RSQLUtility.parse(correctRsql, TestFieldEnum.class, setupMacroLookup(), testDb)
                .toPredicate(baseSoftwareModuleRootMock, criteriaQueryMock, criteriaBuilderMock);

        // verification
        verify(macroResolver).lookup(overdueProp);
        // the macro is unknown and hence never replaced -> #lessThanOrEqualTo
        // is invoked with the placeholder:
        verify(criteriaBuilderMock).lessThanOrEqualTo(eq(pathOfString(baseSoftwareModuleRootMock)),
                eq(overduePropPlaceholder));
    }

    public VirtualPropertyReplacer setupMacroLookup() {
        when(confMgmt.getConfigurationValue(TenantConfigurationKey.POLLING_TIME_INTERVAL, String.class))
                .thenReturn(TEST_POLLING_TIME_INTERVAL);
        when(confMgmt.getConfigurationValue(TenantConfigurationKey.POLLING_OVERDUE_TIME_INTERVAL, String.class))
                .thenReturn(TEST_POLLING_OVERDUE_TIME_INTERVAL);

        return macroResolver;
    }

    @SuppressWarnings("unchecked")
    private <Y> Path<Y> pathOfString(final Path<?> path) {
        return (Path<Y>) path;
    }

    private enum TestFieldEnum implements FieldNameProvider {
        TESTFIELD("testfield"),
        TESTFIELD_WITH_SUB_ENTITIES("testfieldWithSubEntities", "subentity11", "subentity22");

        private final String fieldName;
        private List<String> subEntityAttributes;

        TestFieldEnum(final String fieldName){
            this(fieldName,  new String[0]);
        }

        TestFieldEnum(final String fieldName, final String... subEntityAttributes) {
            this.fieldName = fieldName;
            this.subEntityAttributes = (Arrays.asList(subEntityAttributes));
        }

        @Override
        public String getFieldName() {
            return this.fieldName;
        }

        @Override
        public List<String> getSubEntityAttributes() {
            return subEntityAttributes;
        }
    }

    private enum TestValueEnum {
        BUMLUX;
    }
}
