<img src=hawkbit_logo.png width=533 height=246 />

# Eclipse hawkBit™ - Update Server

Eclipse [hawkBit](http://www.eclipse.org/hawkbit/index.html) is an domain independent back end solution for rolling out software updates to constrained edge devices as well as more powerful controllers and gateways connected to IP based networking infrastructure. Devices can be connected to the hawkBit server either directly through an optimized interface or indirectly through federated device management servers.

Build: [![Circle CI](https://circleci.com/gh/eclipse/hawkbit.svg?style=shield)](https://circleci.com/gh/eclipse/hawkbit)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=org.eclipse.hawkbit%3Ahawkbit-parent&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=org.eclipse.hawkbit%3Ahawkbit-parent)
[![Maven Central](https://img.shields.io/maven-central/v/org.eclipse.hawkbit/hawkbit-parent?color=blue)](https://maven-badges.herokuapp.com/maven-central/org.eclipse.hawkbit/hawkbit-parent)
[![Lines of code](https://img.shields.io/badge/dynamic/xml.svg?label=Lines%20of%20code&url=https%3A%2F%2Fwww.openhub.net%2Fprojects%2Fhawkbit.xml%3Fapi_key%3D30bc3f3fad087c2c5a6a67a8071665ba0fbe3b6236ffbf71b7d20849f4a5e35a&query=%2Fresponse%2Fresult%2Fproject%2Fanalysis%2Ftotal_code_lines&colorB=lightgrey)](https://www.openhub.net/p/hawkbit)

Docker: [![Docker](https://img.shields.io/docker/v/hawkbit/hawkbit-update-server/latest?color=blue)](https://hub.docker.com/r/hawkbit/hawkbit-update-server) [![Docker MYSQL](https://img.shields.io/docker/v/hawkbit/hawkbit-update-server/latest-mysql?color=blue)](https://hub.docker.com/r/hawkbit/hawkbit-update-server)

# Documentation

see [hawkBit Documentation](https://www.eclipse.dev/hawkbit/)

# Contact us

- Having questions about hawkBit? Check [Stack Overflow](https://stackoverflow.com/questions/tagged/eclipse-hawkbit)
- Want to chat with the team behind hawkBit? [![Join the chat at https://gitter.im/eclipse/hawkbit](https://badges.gitter.im/eclipse/hawkbit.svg)](https://gitter.im/eclipse/hawkbit?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
- Having issues with hawkBit? Open a [GitHub issue](https://github.com/eclipse/hawkbit/issues).
- You can also check out our [Project Homepage](https://www.eclipse.dev/hawkbit) for further contact options.

# Examples and Extensions

Next to the hawkBit core hosted here the project maintains as well [examples](https://github.com/eclipse/hawkbit-examples) and [extension](https://github.com/eclipse/hawkbit-extensions) repositories.

# hawkBit sandbox

We offer a sandbox installation that is free for everyone to try out hawkBit. However, keep in mind that the sandbox database will be reset from time to time. It is also not possible to upload any artifacts into the sandbox. But you can use it to try out the Management API and DDI API. Keep in mind as well that you are not permitted to store any kind of personal data in the sandbox.

[https://hawkbit.eclipseprojects.io](https://hawkbit.eclipseprojects.io)

In addition the following vendors offer free trial accounts for their hawkBit compatible products:

- [Bosch IoT Rollouts](https://developer.bosch-iot-suite.com/service/rollouts)
- [Kynetics Update Factory](https://www.kynetics.com/iot-platform-update-factory)

# Device Integration (Client libraries)

hawkBit exposes HTTP/JSON based [Direct Device Integration (API) API](https://www.eclipse.org/hawkbit/apis/ddi_api/) that allow any update client to integrate quite easily.

The [Eclipse Hara subproject](https://projects.eclipse.org/projects/iot.hawkbit.hara) aims to provide a reference agent software implementation of the Eclipse hawkBit device API. The [hara-ddiclient repository](https://github.com/eclipse-hara/hara-ddiclient) provides:

- a Kotlin library that facilitates and speeds up the development of DDI API clients running on the JVM
- a virtual-device application which provides:
    - a reference example on how to use the library
    - a configurable virtual device that can be used for different testing scenarios

The hara-ddiclient library has [reached version 2.x](https://github.com/eclipse-hara/hara-ddiclient/releases), and has been successfully used in production for years.

Additionally, the hawkBit project has the long term goal to provide [Eclipse Hono](https://github.com/eclipse/hono) integration which will provide connectivity through various IoT protocols and as a result will allow a wide range of clients to connect to hawkBit.

## Other open-source hawkBit Clients

There are clients outside of the Eclipse IoT eco system as well, e.g.:

- [SWupdate](https://github.com/sbabic/swupdate) which is a Linux Update agent with focus on a efficient and safe way to update embedded systems.
- [rauc-hawkbit-updater](https://github.com/rauc/rauc-hawkbit-updater) which is a hawkBit client for the [RAUC](https://github.com/rauc/rauc) update framework written in C/glib.
- [rauc-hawkbit](https://github.com/rauc/rauc-hawkbit) which is a python-based hawkBit client demo application and library for the [RAUC](https://github.com/rauc/rauc) update framework.
- [hawkbit-rs](https://github.com/collabora/hawkbit-rs) provides a couple of [Rust](https://www.rust-lang.org) crates to help [implement](https://crates.io/crates/hawkbit) and [test](https://crates.io/crates/hawkbit_mock) hawkBit clients.
- [Zephyr-RTOS](https://docs.zephyrproject.org/apidoc/latest/group__hawkbit.html#details):  The Zephyr OS is a small-footprint kernel designed for use on resource-constrained and embedded systems: from simple embedded environmental sensors and LED wearables to sophisticated embedded controllers, smart watches, and IoT wireless applications.
- [ChirpStack](https://www.chirpstack.io/docs/chirpstack-gateway-os/use/software-update.html): ChirpStack Gateway OS uses [SWUpdate](https://github.com/sbabic/swupdate) for handling updates which can be integrated with Eclipse hawkBit. ChirpStack is an open-source LoRaWAN Network Server which can be used to to setup private or public LoRaWAN networks.

# Runtime dependencies and support

## Java Runtime Environment: 17

## SQL database

| Database                          |                           H2                           |                                MySQL/MariaDB                                |                          MS SQL Server                           |                             PostgreSQL                             |      IBM DB2       |
| --------------------------------- | :----------------------------------------------------: | :-------------------------------------------------------------------------: | :--------------------------------------------------------------: | :----------------------------------------------------------------: | :----------------: |
| DDLs maintained by project        |                   :white_check_mark:                   |                             :white_check_mark:                              |                        :white_check_mark:                        |                         :white_check_mark:                         | :white_check_mark: |
| Test dependencies defined         |                   :white_check_mark:                   |                             :white_check_mark:                              |                        :white_check_mark:                        |                         :white_check_mark:                         |                    |
| Versions tested                   |                          2.1                           |                          MySQL 8.0.23, AWS Aurora                          |                     MS SQL Server 2017/2019                      |                          PostgreSQL 12/13                          |  DB2 Server v11.1  |
| Docker image with driver provided |                   :white_check_mark:                   |                     :white_check_mark: (Tag: "-mysql")                      |                        :white_check_mark:                        |                         :white_check_mark:                         |                    |
| JDBC driver                       | [H2 2.1.214](https://github.com/h2database/h2database) | [MariaDB Connector/J 2.7.8](https://github.com/MariaDB/mariadb-connector-j) | [MSSQL-JDBC 10.2.3.jre8](https://github.com/Microsoft/mssql-jdbc) | [PostgreSQL JDBC Driver 42.3.8](https://github.com/pgjdbc/pgjdbc) |                    |
| Status                            |                       Test, Dev                        |                              Production grade                               |                         Production grade                         |                             Test, Dev                              |     Test, Dev      |

## (Optional) RabbitMQ: 3.6,3.7,3.8

# Getting Started

We are providing a [Spring Boot](https://projects.spring.io/spring-boot/) based reference [Update Server](hawkbit-runtime/hawkbit-update-server) including embedded H2 DB for test and evaluation purposes.
Run with docker:

```bash
docker run -d -p 8080:8080 hawkbit/hawkbit-update-server
```

Open the update server in your browser:

[localhost:8080](http://localhost:8080)

See below for how to build and run the update server on your own. In addition we have a [guide](https://www.eclipse.org/hawkbit/guides/runhawkbit/) for setting up a complete landscape.

**Note**: this docker image supports both DDI and DMF APIs. However, in order to have DMF API working you shall have started additionally RabbitMQ on localhost:5672 with user guest/guest. Then the DMF will use / vhost. See more at [guide](https://www.eclipse.org/hawkbit/guides/runhawkbit/) -> _Configure RabbitMQ connection settings_.

# hawkBit (Spring boot) starters

Next to the [Update Server](hawkbit-runtime/hawkbit-update-server) we are also providing a set of [Spring Boot Starters](hawkbit-starters) to quick start your own [Spring Boot](https://projects.spring.io/spring-boot/) based application.

# Clone, build and run hawkBit

## Build and start hawkBit [Update Server](hawkbit-runtime/hawkbit-update-server)

```bash
git clone https://github.com/eclipse/hawkbit.git
cd hawkbit
mvn clean install
java -jar ./hawkbit-runtime/hawkbit-update-server/target/hawkbit-update-server-#version#.jar
```

## Start hawkBit [Device Simulator](https://github.com/eclipse/hawkbit-examples/tree/master/hawkbit-device-simulator) (optional)

```bash
git clone https://github.com/eclipse/hawkbit-examples.git
cd hawkbit-examples
mvn clean install
```

```bash
java -jar ./hawkbit-device-simulator/target/hawkbit-device-simulator-#version#.jar
```

## Generate getting started data with the [Management API example](https://github.com/eclipse/hawkbit-examples/tree/master/hawkbit-example-mgmt-simulator) (optional)

```bash
java -jar ./hawkbit-example-mgmt-simulator/target/hawkbit-example-mgmt-simulator-#version#-exec.jar
```

# Status and API stability

hawkBit is currently in '0.X' semantic version. That is due to the need that there is still content in hawkBit that is in need for refactoring. That includes the maven module structure, Spring Boot Properties, Spring Boot auto configuration as well as internal Java APIs (e.g. the [repository API](https://github.com/eclipse/hawkbit/issues/197) ).

However, the device facing [DDI API](https://github.com/eclipse/hawkbit/tree/master/hawkbit-rest/hawkbit-ddi-api) is on major version 'v1' and will be kept stable.

Server facing and [DMF API](https://github.com/eclipse/hawkbit/tree/master/hawkbit-dmf/hawkbit-dmf-api) are [Management API](https://github.com/eclipse/hawkbit/tree/master/hawkbit-rest/hawkbit-mgmt-api) are on v1 as well. However, we cannot fully guarantee the same stability during hawkBit's 0.X development but we will try as best we can.
