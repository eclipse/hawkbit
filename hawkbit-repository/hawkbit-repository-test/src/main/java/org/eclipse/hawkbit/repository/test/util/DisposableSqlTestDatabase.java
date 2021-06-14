/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import static org.eclipse.hawkbit.repository.test.util.DatasourceContext.*;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Provides a convenient way to generate a test database that can be used, and disposed of after the test is executed.
 */
public class DisposableSqlTestDatabase extends SharedSqlTestDatabase implements AfterAllCallback {

    private DatasourceContext datasourceContext = null;

    @Override
    public void beforeAll(final ExtensionContext context) {
        super.beforeAll(context);
        final DatasourceContext sharedContext = CONTEXT.get();
        datasourceContext = new DatasourceContext(sharedContext.getDatabase(), sharedContext.getDatasourceUrl(),
                sharedContext.getUsername(), sharedContext.getPassword());
        final AbstractSqlTestDatabase database = matchingDatabase(datasourceContext);
        final String randomSchemaUri = database.createRandomSchema().getRandomSchemaUri();
        LOGGER.info("\033[0;33m Random Schema URI is {} \033[0m", randomSchemaUri);
        System.setProperty(SPRING_DATASOURCE_URL_KEY, randomSchemaUri);
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        matchingDatabase(datasourceContext).dropRandomSchema();
        System.setProperty(SPRING_DATASOURCE_URL_KEY, matchingDatabase(CONTEXT.get()).getRandomSchemaUri());
    }
}
