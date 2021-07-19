/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.amqp;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * An abstract error handler for errors resulting from AMQP.
 */
public abstract class AbstractAmqpErrorHandler<T> implements AmqpErrorHandler{

    @Override
    public void doHandle(Throwable throwable, AmqpErrorHandlerChain chain) {
        // retrieving the cause of throwable as it contains the actual class of
        // exception
        final Throwable cause = throwable.getCause();
        if (getExceptionClass().isAssignableFrom(cause.getClass())) {
            throw new AmqpRejectAndDontRequeueException(getErrorMessage(throwable));
        } else {
            chain.handle(throwable);
        }
    }

    /**
     * Returns the class of the exception.
     *
     * @return
     *          the exception class
     */
    public abstract Class<T> getExceptionClass();

    /**
     * Returns the customized error message.
     *
     * @return
     *          the customized error message
     */
    public String getErrorMessage(Throwable throwable){
      return AmqpErrorMessageComposer.constructErrorMessage(throwable);
    }

}
