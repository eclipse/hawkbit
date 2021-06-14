/**
 * Copyright (c) 2020 Microsoft and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import static java.sql.DriverManager.getConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.util.AntPathMatcher;

/**
 * A {@link TestExecutionListener} for creating and dropping SQL schemas if
 * tests are setup with an SQL schema.
 */
public abstract class AbstractSqlTestDatabase extends AbstractTestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSqlTestDatabase.class);
    protected static final AntPathMatcher MATCHER = new AntPathMatcher();

    protected final DatasourceContext context;

    public AbstractSqlTestDatabase(final DatasourceContext context) {
        this.context = context;
    }

    protected abstract AbstractSqlTestDatabase createRandomSchema();

    protected abstract void dropRandomSchema();

    protected abstract String getRandomSchemaUri();

    protected void executeStatement(final String uri, final String statement) {
        LOGGER.trace("\033[0;33m Executing statement {} on uri {} \033[0m", statement, uri);

        try (final Connection connection = getConnection(uri, context.getUsername(), context.getPassword());
             final PreparedStatement preparedStatement = connection.prepareStatement(statement)) {
            preparedStatement.execute();
        } catch (final SQLException e) {
            LOGGER.error("Execution of statement '{}' on uri {} failed!", statement, uri, e);
        }
    }
}
