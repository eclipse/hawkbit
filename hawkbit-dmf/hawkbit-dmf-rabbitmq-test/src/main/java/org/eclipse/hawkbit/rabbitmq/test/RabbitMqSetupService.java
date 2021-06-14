/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.rabbitmq.test;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.UUID;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.junit.BrokerRunningSupport;
import org.springframework.util.StringUtils;

import com.google.common.base.Throwables;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.domain.UserPermissions;

/**
 * Creates and deletes a new virtual host if the rabbit mq management api is
 * available.
 */
// exception squid:S2068 - Test instance passwd
@SuppressWarnings("squid:S2068")
public class RabbitMqSetupService {

    private static final String VIRTUAL_HOST = UUID.randomUUID().toString();
    private static final com.rabbitmq.client.ConnectionFactory connectionFactory;
    private static final String HOSTNAME;
    private static final String USERNAME;
    private static final String PASSWORD;

    private final Client rabbitmqHttpClient = createRabbitmqHttpClient();

    static {
        BrokerRunningSupport brokerSupport = BrokerRunningSupport.isRunning();
        connectionFactory = brokerSupport.getConnectionFactory();
        HOSTNAME = brokerSupport.getHostName();
        USERNAME = brokerSupport.getUser();
        PASSWORD = brokerSupport.getPassword();
        Runtime.getRuntime().addShutdownHook(new Thread(RabbitMqSetupService::deleteVirtualHost));
    }

    private static Client createRabbitmqHttpClient() {
        try {
            return new Client(getHttpApiUrl(), USERNAME, PASSWORD);
        } catch (MalformedURLException | URISyntaxException e) {
            throw Throwables.propagate(e);
        }
    }

    private static String getHttpApiUrl() {
        return "http://" + HOSTNAME + ":15672/api/";
    }

    public ConnectionFactory newVirtualHostWithConnectionFactory() {
        rabbitmqHttpClient.createVhost(VIRTUAL_HOST);
        rabbitmqHttpClient.updatePermissions(VIRTUAL_HOST, USERNAME, createUserPermissionsFullAccess());
        connectionFactory.setVirtualHost(VIRTUAL_HOST);
        return new CachingConnectionFactory(connectionFactory);
    }

    public static void deleteVirtualHost() {
        if (StringUtils.isEmpty(VIRTUAL_HOST)) {
            return;
        }
        createRabbitmqHttpClient().deleteVhost(VIRTUAL_HOST);
    }

    private static UserPermissions createUserPermissionsFullAccess() {
        final UserPermissions permissions = new UserPermissions();
        permissions.setVhost(VIRTUAL_HOST);
        permissions.setRead(".*");
        permissions.setConfigure(".*");
        permissions.setWrite(".*");
        return permissions;
    }
}
