---
title: Run hawkBit
parent: Guides
weight: 31
---

In this guide we describe how to run a full featured hawkBit setup based on a production ready infrastructure. It is based on the hawkBit example modules and update server.

<!--more-->

{{% note %}}
The update server can in fact be run stand alone. However, only with an embedded H2, no Device Management Federation API and no artifact storage.
{{% /note %}}

## System Architecture

This guide describes a target architecture that is more like one that you will expect in a production system.

- hawkBit [Update Server](https://github.com/eclipse/hawkbit/tree/master/hawkbit-runtime/hawkbit-update-server).
- [MariaDB](https://mariadb.org) for the repository.
- [RabbitMQ](https://www.rabbitmq.com) for DMF communication.
- For testing and demonstration purposes we will also use:
- [hawkBit Device Simulator](https://github.com/eclipse/hawkbit-examples/tree/master/hawkbit-device-simulator).
- [hawkBit Management API example client](https://github.com/eclipse/hawkbit-examples/tree/master/hawkbit-example-mgmt-feign-client).

## Prerequisites

- You have a working [hawkBit core build](https://github.com/eclipse/hawkbit).
- You have a working [hawkBit examples build](https://github.com/eclipse/hawkbit-examples).

## Adapt hawkBit Update Server and Device Simulator to your environment.

As mentioned you can create your own application with hawkBit inside or adapt the existing example app. The second option will be shown here.

### Set MariaDB dependency to compile in the [update server POM](https://github.com/eclipse/hawkbit/blob/master/hawkbit-runtime/hawkbit-update-server/pom.xml)

```xml
<dependency>
  <groupId>org.mariadb.jdbc</groupId>
  <artifactId>mariadb-java-client</artifactId>
  <scope>compile</scope>
</dependency>
```

### Configure MariaDB/MySQL connection settings.

For this you can either edit the existing _application.properties_ or create a [new profile](http://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-external-config-profile-specific-properties).

```properties
spring.jpa.database=MYSQL
spring.datasource.url=jdbc:mysql://localhost:3306/YOUR_SCHEMA
spring.datasource.username=YOUR_USER
spring.datasource.password=YOUR_PWD
spring.datasource.driverClassName=org.mariadb.jdbc.Driver
```

### Configure RabbitMQ connection settings for update server and device simulator (optional).

We provide already defaults that should work with a standard Rabbit installation. Otherwise configure the following in the `application.properties` of the two services:

```properties
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
spring.rabbitmq.virtualHost=/
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
```

### Set [Eclipse Hono](https://www.eclipse.org/hono/) as hawkBit's device registry

```
hawkbit.dmf.hono.enabled=true
hawkbit.dmf.hono.tenant-list-uri=http://HONO_HOST/v1/tenants
hawkbit.dmf.hono.device-list-uri=http://HONO_HOST/v1/devices/$tenantId
hawkbit.dmf.hono.credentials-list-uri=http://HONO_HOST/v1/credentials/$tenantId/$deviceId
```
`$tenantId` and `$deviceId` are placeholders which will be replaced by hawkBit during the respective requests.

hawkBit currently supports three different methods to authenticate with hono:
- None (`none`; default)
- BasicAuth (`basic`)
- OpenID Connect (`oidc`)

If you intend to use any authentication method other than `none` you must provide these additional properties: 

```
hawkbit.dmf.hono.authentication-method=oidc
hawkbit.dmf.hono.username=USERNAME
hawkbit.dmf.hono.password=PASSWORD

// Only for authentication-method = oidc
hawkbit.dmf.hono.oidc-token-uri=http://OIDC_HOST/auth/realms/REALM/protocol/openid-connect/token
hawkbit.dmf.hono.oidc-client-id=OIDC_CLIENT_ID
hawkbit.dmf.hono.oidc-client-secret=OIDC_CLIENT_SECRET # You can use a oidc client secret instead of username+password
```

hawkBit handles device registry updates through CUD events emitted by Hono over any Spring Cloud Stream supported channel, such as AMQP or Google Cloud Pub/Sub.

In order to have predictable channel names use the following properties:
```
spring.cloud.stream.bindings.device-created.destination=device-registry.device-created
spring.cloud.stream.bindings.device-created.group=hawkBit
spring.cloud.stream.bindings.device-updated.destination=device-registry.device-updated
spring.cloud.stream.bindings.device-updated.group=hawkBit
spring.cloud.stream.bindings.device-deleted.destination=device-registry.device-deleted
spring.cloud.stream.bindings.device-deleted.group=hawkBit
```
For Google Cloud Pub/Sub disable the default Maven profile `hono-amqp` and enable the profile `amqp-gcp-pubsub`.

Additionally, you can specify a field of the device's extension object which will be used as the corresponding target's name:
```
hawkbit.dmf.hono.target-name-field=fancyFieldName
```
If none is specified the device's ID is used as the target's name.


### Adapt hostname of example scenario [creation script](https://github.com/eclipse/hawkbit-examples/blob/master/hawkbit-example-mgmt-simulator/src/main/resources/application.properties)

Should only be necessary if your system does not run on localhost or uses a different port than the example app.

Adapt `application.properties` in this case:

```properties
hawkbit.url=localhost:8080
```

or provide the parameter on command line:

```properties
hawkbit-example-mgmt-simulator-##VERSION##.jar --hawkbit.url=YOUR_HOST:PORT
```

## Compile & Run

### Compile & Run your _"production ready"_ app

see [update server](https://github.com/eclipse/hawkbit/tree/master/hawkbit-runtime/hawkbit-update-server)

### Compile & Run example scenario [creation script](https://github.com/eclipse/hawkbit-examples/tree/master/hawkbit-example-mgmt-simulator) (optional)

This has to be done before the device simulator is started. hawkBit creates the mandatory tenant metadata with first login into either _Management UI_ or API (which is done by this client).

However, this is not done by _DMF_ which is in fact used by the device simulator, i.e. without calling _Management API_ first hawkBit would drop all _DMF_ messages as the tenant is unknown.

### Compile & Run device simulator (optional)

see [device simulator](https://github.com/eclipse/hawkbit-examples/tree/master/hawkbit-device-simulator)

# Enjoy hawkBit with a real database, artifact storage and all [interfaces](../../apis/) available

![](../../images/hawkbit_ui.png)
