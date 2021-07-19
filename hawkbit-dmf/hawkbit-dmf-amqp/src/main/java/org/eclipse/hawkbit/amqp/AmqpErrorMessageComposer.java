/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.amqp;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

/**
 * Class that composes a meaningful error message and enhances it with properties from failed message
 */
public final class AmqpErrorMessageComposer {

    private AmqpErrorMessageComposer() {
    }

    /**
     * Constructs an error message based on failed message content
     *
     * @param throwable
     *                  the throwable containing failed message content
     * @return
     *                  meaningful error message
     */
    public static String constructErrorMessage(final Throwable throwable) {
        StringBuilder completeErrorMessage = new StringBuilder();
        final String mainErrorMsg = throwable.getCause().getMessage();

        if (throwable instanceof ListenerExecutionFailedException) {
            Collection<Message> failedMessages = ((ListenerExecutionFailedException) throwable).getFailedMessages();
            // since the intended message content is always on top of the collection, we only extract the first one
            final Message failedMessage = failedMessages.iterator().next();
            final byte[] amqpFailedMsgBody = failedMessage.getBody();
            final Map<String, Object> amqpFailedMsgHeaders = failedMessage.getMessageProperties().getHeaders();

            String amqpFailedMsgConcatenatedHeaders = amqpFailedMsgHeaders.keySet().stream()
                    .map(key -> key + "=" + amqpFailedMsgHeaders.get(key)).collect(Collectors.joining(", ", "{", "}"));
            completeErrorMessage.append(mainErrorMsg).append(new String(amqpFailedMsgBody))
                    .append(amqpFailedMsgConcatenatedHeaders);
            return completeErrorMessage.toString();
        }
        return mainErrorMsg;
    }
}
