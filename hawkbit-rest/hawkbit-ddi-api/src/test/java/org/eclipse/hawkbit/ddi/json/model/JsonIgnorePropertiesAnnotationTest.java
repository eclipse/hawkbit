/**
 * Copyright (c) 2019 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hawkbit.ddi.json.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.reflect.ClassPath;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Check DDI api model classes for '@JsonIgnoreProperties' annotation
 */
@Feature("Unit Tests - Direct Device Integration API")
@Story("Serialization of DDI api Models")
public class JsonIgnorePropertiesAnnotationTest {

    @Test
    @Description("This test verifies that all model classes within the 'org.eclipse.hawkbit.ddi.json.model' package are annotated with '@JsonIgnoreProperties(ignoreUnknown = true)'")
    public void shouldCheckAnnotationsForAllModelClasses() throws IOException {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final String packageName = this.getClass().getPackage().getName();

        final Set<ClassPath.ClassInfo> topLevelClasses = ClassPath.from(loader)
                .getTopLevelClasses(packageName);
        for (final ClassPath.ClassInfo classInfo : topLevelClasses) {
            final Class<?> modelClass = classInfo.load();
            if (modelClass.getSimpleName().contains("Test") || modelClass.isEnum()) {
                continue;
            }
            final JsonIgnoreProperties annotation = modelClass.getAnnotation(JsonIgnoreProperties.class);
            assertThat(annotation)
                    .describedAs(
                            "Annotation 'JsonIgnoreProperties' is missing for class: " + modelClass.getSimpleName())
                    .isNotNull();
            assertThat(annotation.ignoreUnknown()).isTrue();
        }
    }
}
