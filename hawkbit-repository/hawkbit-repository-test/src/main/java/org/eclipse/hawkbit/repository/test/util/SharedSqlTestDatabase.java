/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import static org.eclipse.hawkbit.repository.test.util.DatasourceContext.SPRING_DATASOURCE_URL_KEY;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a test database configuration for a "shared" database instance across all tests annotated with this extension
 */
public class SharedSqlTestDatabase implements BeforeAllCallback {

    protected static final Logger LOGGER = LoggerFactory.getLogger(SharedSqlTestDatabase.class);
    protected static final AtomicReference<DatasourceContext> CONTEXT = new AtomicReference<>();

    @Override
    public void beforeAll(final ExtensionContext context) {
        if (CONTEXT.get() != null) {
            return;
        }

        final DatasourceContext testDatasourceContext = new DatasourceContext(context.getRequiredTestClass());

        // update CONTEXT only if the current value is null => initialize only
        if (!CONTEXT.compareAndSet(null, testDatasourceContext)) {
            return;
        }

        final AbstractSqlTestDatabase database = matchingDatabase(testDatasourceContext);
        final String randomSchemaUri = database.createRandomSchema().getRandomSchemaUri();
        LOGGER.info("\033[0;33m Random Schema URI is {} \033[0m", randomSchemaUri);
        System.setProperty(SPRING_DATASOURCE_URL_KEY, randomSchemaUri);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.warn("Dropping schema at url {}", randomSchemaUri);
            database.dropRandomSchema();
        }));
    }

    protected static AbstractSqlTestDatabase matchingDatabase(final DatasourceContext context) {
        AbstractSqlTestDatabase database;

        switch (context.getDatabase()) {
            case "H2":
                database = new H2TestDatabase(context);
                break;
            case "SQL_SERVER":
                database = new MsSqlTestDatabase(context);
                break;
            case "MYSQL":
                database = new MySqlTestDatabase(context);
                break;
            case "POSTGRESQL":
                database = new PostgreSqlTestDatabase(context);
                break;
            default:
                throw new IllegalStateException("No supported database found for type " + context.getDatabase());
        }

        return database;
    }

}
