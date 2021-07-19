/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.amqp;

import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;

/**
 * An error handler for entity not found exception resulting from AMQP.
 */
public class EntityNotFoundExceptionHandler extends AbstractAmqpErrorHandler<EntityNotFoundException> {

    @Override
    public Class<EntityNotFoundException> getExceptionClass() {
        return EntityNotFoundException.class;
    }
}
