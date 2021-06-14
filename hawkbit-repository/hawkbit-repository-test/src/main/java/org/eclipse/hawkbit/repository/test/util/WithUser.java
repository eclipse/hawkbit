/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to run test classes or test methods with a specific user with
 * specific permissions.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@Inherited
public @interface WithUser {

    /**
     * Gets the test principal.
     * 
     * @return test principal
     */
    String principal() default "";

    /**
     * Gets the test credentials.
     * 
     * @return test credentials
     */
    String credentials() default "TestCredentials";

    /**
     * Gets the test tenant id.
     * 
     * @return test tenant id
     */
    String tenantId() default "";

    /**
     * Should tenant auto created.
     * 
     * @return <code>true</code> = auto create <code>false</code> not create
     */
    boolean autoCreateTenant() default true;

    /**
     * Gets the test authorities.
     * 
     * @return authorities
     */
    String[] authorities() default {};

    /**
     * Gets the test all permissions.
     * 
     * @return permissions
     */
    boolean allSpPermissions() default false;

    /**
     * Gets the test removeFromAllPermission.
     * 
     * @return removeFromAllPermission
     */
    String[] removeFromAllPermission() default {};

    boolean controller() default false;
}
