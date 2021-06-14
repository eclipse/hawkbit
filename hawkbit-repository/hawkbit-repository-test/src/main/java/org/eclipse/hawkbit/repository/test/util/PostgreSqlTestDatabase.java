/**
 * Copyright (c) 2020 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestExecutionListener;

/**
 * A {@link TestExecutionListener} for creating and dropping MySql schemas if
 * tests are setup with MySql.
 */
public class PostgreSqlTestDatabase extends AbstractSqlTestDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgreSqlTestDatabase.class);
    private static final String POSTGRESQL_URI_PATTERN = "jdbc:postgresql://{host}:{port}/{db}*";

    public PostgreSqlTestDatabase(final DatasourceContext context) {
        super(context);
    }

    @Override
    protected PostgreSqlTestDatabase createRandomSchema() {
        LOGGER.info("\033[0;33m Creating postgreSql schema {} if not existing \033[0m", context.getRandomSchemaName());
        final String uri = getBaseUri() + "?currentSchema=" + getSchemaName();
        executeStatement(uri, "CREATE SCHEMA IF NOT EXISTS " + context.getRandomSchemaName() + ";");
        return this;
    }

    @Override
    protected void dropRandomSchema() {
        LOGGER.info("\033[0;33m Dropping postgreSql schema {} if not existing \033[0m", context.getRandomSchemaName());
        final String uri = getBaseUri() + "?currentSchema=" + getSchemaName();
        executeStatement(uri, "DROP SCHEMA " + context.getRandomSchemaName() + " CASCADE;");
    }

    @Override
    protected String getRandomSchemaUri() {
        return getBaseUri() + "?currentSchema=" + context.getRandomSchemaName();
    }

    private String getBaseUri() {
        final String uri = context.getDatasourceUrl();
        final Map<String, String> databaseProperties = MATCHER.extractUriTemplateVariables(POSTGRESQL_URI_PATTERN, uri);

        return POSTGRESQL_URI_PATTERN.replace("{host}", databaseProperties.get("host"))
                .replace("{port}", databaseProperties.get("port"))
                .replace("{db}*", getSchemaName());
    }

    private String getSchemaName() {
        return MATCHER.extractUriTemplateVariables(POSTGRESQL_URI_PATTERN, context.getDatasourceUrl())
                .get("db")
                .split("\\?")[0];
    }
}
