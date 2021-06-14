/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.integration.listener;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hawkbit.dmf.amqp.api.EventTopic;
import org.eclipse.hawkbit.dmf.amqp.api.MessageHeaderKey;
import org.eclipse.hawkbit.dmf.amqp.api.MessageType;
import org.eclipse.hawkbit.rabbitmq.test.listener.TestRabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class ReplyToListener implements TestRabbitListener {

    public static final String LISTENER_ID = "replyto";
    public static final String REPLY_TO_QUEUE = "reply_queue";

    private final Map<String, Map<EventTopic, List<Message>>> tenantEventMessages = new ConcurrentHashMap<>();
    private final Map<String, List<EventTopic>> tenantEventMessageTopics = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Message>> tenantDeleteMessages = new HashMap<>();
    private final Map<String, Map<String, Message>> tenantPingResponseMessages = new HashMap<>();

    @Override
    @RabbitListener(id = LISTENER_ID, queues = REPLY_TO_QUEUE)
    public void handleMessage(final Message message) {
        final MessageType messageType = MessageType
                .valueOf(message.getMessageProperties().getHeaders().get(MessageHeaderKey.TYPE).toString());
        final String tenant = message.getMessageProperties().getHeader(MessageHeaderKey.TENANT);

        switch (messageType) {
            case EVENT:
                processEventMessage(tenant, message);
                return;
            case THING_DELETED:
                processThingDeletedMessage(message, tenant);
                return;
            case PING_RESPONSE:
                processPingResponseMessage(message, tenant);
                return;
            default:
                // if message type is not EVENT or THING_DELETED something unexpected
                // happened
                fail("Unexpected message type");
        }
    }

    private void processEventMessage(final String tenant, final Message message) {
        final EventTopic eventTopic = EventTopic.valueOf(
                message.getMessageProperties().getHeaders().get(MessageHeaderKey.TOPIC).toString());

        final List<EventTopic> eventTopics = tenantEventMessageTopics.getOrDefault(tenant, new LinkedList<>());
        eventTopics.add(eventTopic);
        tenantEventMessageTopics.put(tenant, eventTopics);

        final Map<EventTopic, List<Message>> eventMessages = tenantEventMessages.getOrDefault(tenant, new ConcurrentHashMap<>());
        final List<Message> messages = eventMessages.getOrDefault(eventTopic, new LinkedList<>());
        messages.add(message);
        eventMessages.put(eventTopic, messages);
        tenantEventMessages.put(tenant, eventMessages);
    }

    private void processThingDeletedMessage(final Message message, final String tenant) {
        final String targetName = message.getMessageProperties().getHeaders().get(MessageHeaderKey.THING_ID)
                .toString();
        final Map<String, Message> deleteMessages = tenantDeleteMessages.getOrDefault(tenant,
                new ConcurrentHashMap<>());
        deleteMessages.put(targetName, message);
        tenantDeleteMessages.put(tenant, deleteMessages);
    }

    private void processPingResponseMessage(final Message message, final String tenant) {
        final Map<String, Message> pingResponseMessages = tenantPingResponseMessages.getOrDefault(tenant,
                new ConcurrentHashMap<>());
        pingResponseMessages.put(message.getMessageProperties().getCorrelationId(), message);
        tenantPingResponseMessages.put(tenant, pingResponseMessages);
    }

    public List<EventTopic> getLatestEventMessageTopics(final String tenant) {
        return tenantEventMessageTopics.get(tenant);
    }

    public void resetLatestEventMessageTopics(final String tenant) {
        tenantEventMessageTopics.remove(tenant);
    }

    public Message getLatestEventMessage(final EventTopic eventTopic, final String tenant) {
        final List<Message> messages = getTenantEventMessages(tenant).get(eventTopic);
        return messages == null ? null : messages.get(messages.size() - 1);
    }

    public Map<EventTopic, List<Message>> getTenantEventMessages(final String tenant) {
        return tenantEventMessages.getOrDefault(tenant, new HashMap<>());
    }

    public Map<String, Message> getTenantDeleteMessages(final String tenant) {
        return tenantDeleteMessages.getOrDefault(tenant, new HashMap<>());
    }

    public Map<String, Message> getTenantPingResponseMessages(final String tenant) {
        return tenantPingResponseMessages.getOrDefault(tenant, new HashMap<>());
    }

}
