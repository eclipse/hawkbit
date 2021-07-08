---
title: Getting Started
weight: 20
---

## On Sandbox

We offer a sandbox installation that is free for everyone to try out hawkBit's [Management UI](/hawkbit/ui/), 
[Management API](/hawkbit/apis/management_api/), and [Direct Device Integration API](/hawkbit/apis/ddi_api/): &nbsp;
**[<i class="fas fa-desktop">&nbsp;</i> https://hawkbit.eclipse.org](https://hawkbit.eclipse.org)**

{{% warning %}}
The sandbox is a shared installation that will be reset from time to time. Therefore, it is not allowed to upload
any personal data.
{{% /warning %}}

In addition, the following vendors offer free trial accounts for their Eclipse hawkBit compatible products:

* [Bosch IoT Rollouts](https://www.bosch-iot-suite.com/rollouts/#plans) (by [Bosch.IO GmbH](https://bosch.io/))
* [Kynetics Update Factory](https://www.kynetics.com/update-factory) (by [Kynetics LLC](https://www.kynetics.com/))


## From Docker Image

### Overview

| Service / Container | A | B | C |
|---|---|---|---|
| hawkBit Update Server |  &#10003; | &#10003; | &#10003; | 
| hawkBit Device Simulator |   |  | &#10003; | 
| MySQL |  | &#10003; | &#10003; | 
| RabbitMQ |  | &#10003; | &#10003; | 

HawkBit Update Server docker container login credentials:

| Username | Password |
|---|---|
| admin | admin |
| hawkBit Update Server

### A: Run hawkBit Update Server as Docker Container

Start the hawkBit Update Server as a single container

```bash
$ docker run -p 8080:8080 hawkbit/hawkbit-update-server:latest
```

### B: Run hawkBit Update Server with services as Docker Compose

Start the hawkBit Update Server together with an MySQL and RabbitMQ instance as containers

```bash
$ git clone https://github.com/eclipse/hawkbit.git
$ cd hawkbit/hawkbit-runtime/docker
$ docker-compose up -d
```

### C: Run hawkBit Update Server with services as Docker Stack

Start the hawkBit Update Server and Device Simulator together with an MySQL and RabbitMQ instance as services within a swarm

```bash
$ git clone https://github.com/eclipse/hawkbit.git
$ cd hawkbit/hawkbit-runtime/docker
$ docker swarm init
$ docker stack deploy -c docker-compose-stack.yml hawkbit
```

## From Sources

### 1: Clone and build hawkBit
```sh
$ git clone https://github.com/eclipse/hawkbit.git
$ cd hawkbit
$ mvn clean install
```

### 2: Start hawkBit [update server](https://github.com/eclipse/hawkbit/tree/master/hawkbit-runtime/hawkbit-update-server)

```sh
$ java -jar ./hawkbit-runtime/hawkbit-update-server/target/hawkbit-update-server-#version#-SNAPSHOT.jar
```

### 3: Build hawkBit examples

```sh
$ git clone https://github.com/eclipse/hawkbit-examples.git
$ cd hawkbit-examples
$ mvn clean install
```

### 4: Start hawkBit [Device Simulator](https://github.com/eclipse/hawkbit-examples/tree/master/hawkbit-device-simulator)
```sh
$ java -jar ./hawkbit-device-simulator/target/hawkbit-device-simulator-#version#.jar
```

### 5: Generate Getting Started data with [Example Management API Client](https://github.com/eclipse/hawkbit-examples/tree/master/hawkbit-example-mgmt-simulator)

```sh
$ java -jar ./hawkbit-example-mgmt-simulator/target/hawkbit-example-mgmt-simulator-#version#.jar
```
