/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.amqp;

/**
 * Interface declaration of {@link AmqpErrorHandler} that handles errors based on the
 * types of exception.
 */
@FunctionalInterface
public interface AmqpErrorHandler {

    /**
     * Handles the error based on the type of exception
     *
     * @param throwable
     *            the throwable
     * @param chain
     *            an {@link AmqpErrorHandlerChain}
     */
void doHandle(final Throwable throwable, final AmqpErrorHandlerChain chain);

}
