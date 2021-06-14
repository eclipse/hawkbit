/**
 * Copyright (c) 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestExecutionListener;

/**
 * A {@link TestExecutionListener} for creating and dropping MS SQL Server
 * schemas if tests are setup with MS SQL Server.
 */
public class MsSqlTestDatabase extends AbstractSqlTestDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MsSqlTestDatabase.class);

    public MsSqlTestDatabase(final DatasourceContext context) {
        super(context);
    }

    @Override
    public MsSqlTestDatabase createRandomSchema() {
        final String uri = context.getDatasourceUrl();
        LOGGER.info("\033[0;33m Creating mssql schema {} if not existing \033[0m", context.getRandomSchemaName());

        executeStatement(uri.split(";database=")[0], "CREATE DATABASE " + context.getRandomSchemaName() + ";");
        return this;
    }

    @Override
    protected void dropRandomSchema() {
        final String uri = context.getDatasourceUrl();
        final String dbServerUri = uri.split(";database=")[0];

        // Needed to avoid the DROP is rejected with "database still in use"
        executeStatement(dbServerUri, "ALTER DATABASE " + context.getRandomSchemaName() + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE;");
        executeStatement(dbServerUri, "DROP DATABASE " + context.getRandomSchemaName() + ";");
    }

    @Override
    protected String getRandomSchemaUri() {
        final String uri = context.getDatasourceUrl();
        return uri.substring(0, uri.indexOf(';')) + ";database=" + context.getRandomSchemaName();
    }
}
