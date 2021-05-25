/**
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa;

/**
 * Represents an execution exception in the scope of a transaction
 */
public class TransactionExecutionException extends Exception {

    /**
     * Constructor
     */
    public TransactionExecutionException(final String message, final Exception cause) {
        super(message, cause);
    }
}
