/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import java.io.IOException;
import java.util.Optional;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.ResourcePatternUtils;

/**
 * Holds all database related configuration
 */
public class DatasourceContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatasourceContext.class);

    protected static final String SPRING_DATASOURCE_URL_KEY = "spring.datasource.url";
    protected static final String SPRING_DATABASE_KEY = "spring.jpa.database";
    protected static final String SPRING_DATABASE_USERNAME_KEY = "spring.datasource.username";
    protected static final String SPRING_DATABASE_PASSWORD_KEY = "spring.datasource.password";
    private static final String HAWKBIT_JPA_DEFAULTS_LOCATION = "classpath:hawkbit-jpa-defaults.properties";

    private final String database;
    private final String datasourceUrl;
    private final String username;
    private final String password;
    private final String randomSchemaName = "HAWKBIT_TEST_" + RandomStringUtils.randomAlphanumeric(10);

    /**
     * Constructor
     */
    public DatasourceContext(final String database, final String datasourceUrl, final String username,
            final String password) {
        this.database = database;
        this.datasourceUrl = datasourceUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Constructor
     */
    public DatasourceContext(final Class<?> clazz) {
        database = getFromEnvironmentOrDefault(clazz, SPRING_DATABASE_KEY, "H2");
        datasourceUrl = getFromEnvironmentOrDefault(clazz, SPRING_DATASOURCE_URL_KEY, "jdbc:h2:mem:test;MODE=MySQL;");
        username = getFromEnvironmentOrDefault(clazz, SPRING_DATABASE_USERNAME_KEY, "sa");
        password = getFromEnvironmentOrDefault(clazz, SPRING_DATABASE_PASSWORD_KEY, "");
    }

    /**
     * if system property is set, it will be used, otherwise, property will be loaded from {@link HAWKBIT_JPA_DEFAULTS_LOCATION}
     */
    private static String getFromEnvironmentOrDefault(final Class<?> clazz, final String key,
            final String defaultValue) {
        return Optional.ofNullable(System.getProperty(key))
                .orElseGet(() -> getFromJpaPropertiesOrDefault(clazz.getClassLoader(), key, defaultValue));
    }

    private static String getFromJpaPropertiesOrDefault(final ClassLoader classLoader, final String key,
            final String defaultValue) {
        final DefaultResourceLoader resourceLoader = new DefaultResourceLoader(classLoader);

        try {
            final Resource resource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
                    .getResource(HAWKBIT_JPA_DEFAULTS_LOCATION);
            return PropertiesLoaderUtils.loadProperties(resource).getProperty(key);
        } catch (final IOException e) {
            LOGGER.warn("Got IOException during database properties loading from location {}",
                    HAWKBIT_JPA_DEFAULTS_LOCATION);
            LOGGER.debug("", e);
        }
        return defaultValue;
    }

    public String getDatabase() {
        return database;
    }

    public String getDatasourceUrl() {
        return datasourceUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRandomSchemaName() {
        return randomSchemaName;
    }
}
