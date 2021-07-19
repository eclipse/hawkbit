/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.amqp;

import org.eclipse.hawkbit.repository.exception.InvalidTargetAttributeException;

/**
 * An error handler for all invalid target attributes resulting from AMQP.
 */
public class InvalidTargetAttributeExceptionHandler extends AbstractAmqpErrorHandler<InvalidTargetAttributeException> {

    @Override
    public Class<InvalidTargetAttributeException> getExceptionClass() {
        return InvalidTargetAttributeException.class;
    }
}
