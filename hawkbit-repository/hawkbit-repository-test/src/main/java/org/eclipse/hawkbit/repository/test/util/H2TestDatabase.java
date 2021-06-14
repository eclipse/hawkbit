/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.test.util;

import org.springframework.test.context.TestExecutionListener;

/**
 * A {@link TestExecutionListener} for creating and dropping H2 schemas if
 * tests are setup with H2.
 */
public class H2TestDatabase extends AbstractSqlTestDatabase {

    public H2TestDatabase(final DatasourceContext context) {
        super(context);
    }

    @Override
    public H2TestDatabase createRandomSchema() {
        // do nothing, since H2 is in memory
        return this;
    }

    @Override
    protected void dropRandomSchema() {
        // do nothing, since H2 is in memory
    }

    @Override
    protected String getRandomSchemaUri() {
        return "jdbc:h2:mem:" + context.getRandomSchemaName() +";MODE=MySQL;";
    }
}
