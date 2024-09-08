# <font color="LightSeaGreen">UrbanGeoPulse</font>

#### A Big Data Geospatial Application.

# Architecture Document

<small>Note: This document is based on the Architecture Document template provided as part of “The complete guide to becoming a great Software Architect” course, by Memi Lavi.
The template is a copyrighted material by Memi Lavi (www.memilavi.com, memi@memilavi.com).</small>

### Table Of Content

<!-- toc -->

- [Background](#background)
- [Requirements](#requirements)
  * [Functional Requirements](#functional-requirements)
  * [Non-Functional Requirements](#non-functional-requirements)
- [Executive Summary](#executive-summary)
- [Overall Architecture](#overall-architecture)
  * [Diagram](#diagram)
  * [Services](#services)
  * [Messaging](#messaging)
  * [Technology Stack](#technology-stack)
  * [Non-Functional Attributes](#non-functional-attributes)
    + [Reliability](#reliability)
    + [Scalability](#scalability)
    + [Security](#security)
    + [Testability](#testability)
      - [Logging](#logging)
      - [System level testing](#system-level-testing)
    + [Maintainability](#maintainability)
- [Services Drill Down](#services-drill-down)
  * [Mobile application](#mobile-application)
    + [Role](#role)
  * [Receiver service](#receiver-service)
    + [Role](#role-1)
    + [Implementation Instructions](#implementation-instructions)
    + [APIs](#apis)
  * [Mobilization-sorter service](#mobilization-sorter-service)
    + [Role](#role-2)
    + [Implementation Instructions](#implementation-instructions-1)
  * [Locations-finder service](#locations-finder-service)
    + [Role](#role-3)
    + [Implementation Instructions](#implementation-instructions-2)
    + [Deployment Instructions](#deployment-instructions)
  * [Delay service](#delay-service)
    + [Role](#role-4)
    + [Diagram](#diagram-1)
  * [Activity-aggregator service](#activity-aggregator-service)
    + [Role](#role-5)
    + [Implementation Instructions](#implementation-instructions-3)
  * [Info service](#info-service)
    + [Role](#role-6)
    + [APIs:](#apis)

<!-- tocstop -->

## Background

This document describes the **UrbanGeoPulse**'s architecture, a system requested by the city of New York (NYC).<br>
(This is a **showcase** of a **software architecture** definition process. The requirements are hypothetical, inspired by the [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.)

NYC requires **real-time** information on the streets and neighborhoods with the highest concentration of **pedestrians** and **non-pedestrians** (referred to as **mobilized** individuals) at any given time of day. <br>
This information **will be used to make decisions** regarding transportation budgets, timing of municipal construction work, advertising fees, and more.

The architecture comprises technology and modeling decisions, that will ensure the final product will be fast, reliable and easy to maintain.
The document outlines the thought process for every aspect of the architecture, and explains why specific decisions were made.
It’s extremely important for the development team to closely follow the architecture depicted in this document. In any case of doubt please consult the Software Architect.

## Requirements

### Functional Requirements

1. [Receive](#receiver-service) messages containing **geospatial locations**, e.g. from cell phones.<br>
   These messages can be sent by **pedestrians** as well as **mobilized** individuals.

2. [Identify](#mobilization-sorter-service) pedestrians and mobilized messages, based on the speed calculated between the last two messages from the same device.

3. Allow users to [retrieve](#info-service) streets and neighborhoods activity information **in real time** for a desired timeframe within the last 24 hours.

### Non-Functional Requirements

1. Performance: **Response time** of streets and neighborhoods activity [retrieval](#info-service) should not exceed **3 seconds**.
2. **Data volume**:
   - PostgreSQL: ~**10 GB** per **24 hours** [ * 60 minutes * (~20,000 streets + ~150 neighborhoods) * ~0.3 KB per aggregated activity record ].
   - Kafka: TBD (depending on Data Retention configuration).
3. **Load**: 20,000 concurrent requests.
4. **Number of users**: ~5,000,000
5. **Message loss**: < 5%
6. **SLA**: 98%

## Executive Summary

This document describes the architecture of the **UrbanGeoPulse** application, as described in the [Background](#background) section. <br>The information that will be collected by the system **will be used by NYC to make decisions** regarding transportation budgets, timing of municipal construction work, advertising fees, and more.<br><br>
When designing the architecture, a strong emphasis was put on the following qualities:

- The application should be reliable and support very high load (per the population of NYC, specifically during rush hours).
- The application should be fast.
<p>To achieve these qualities, the architecture is based on the most up-to-date best practices and methodologies, ensuring high-availability and performance.</p>

Here is a high-level overview of the architecture:
![Lucid](https://lucid.app/publicSegments/view/6bffea51-c248-49e8-a244-a0a691a3ab9d/image.jpeg 'System diagram')
As can be seen in the diagram, the application comprises a few separate, independent, loosely-coupled **microservices**, each has its own task, and each communicates with the other services using standard protocols.

All the services are stateless, allowing them to [scale](#scalability) easily and seamlessly. In addition, no data is lost if a service is suddenly shutting down. The only places for data in the application are Kafka and the Data Store (PostgreSQL), both of them persist the data to the disk, thus protecting data from cases of shutdown.

This architecture, in conjunction with a modern development platform (JAVA Spring Boot), will help create a **modern**, **robust**, **scalable**, **easy to maintain**, and **reliable** system, that can serve NYC successfully for years to come, and help achieve its financial goals.

## Overall Architecture

### [Diagram](https://lucid.app/documents/view/9b48ab81-1cc7-44c1-b8bb-a92ec78b2802)

![Lucid](https://lucid.app/publicSegments/view/6bffea51-c248-49e8-a244-a0a691a3ab9d/image.jpeg 'System diagram')

### Services

The architecture comprises the following services:

- [Mobile application](#mobile-application) - will collect geospatial locations and send messages to the [Receiver service](#receiver-service). Each message should also contain the city code, e.g. NYC. This will be used by the backend to load the required geospatial into the database, thus allowing the system to be generic, suitable for any city providing the maps.
- [Receiver](#receiver-service) service - will receive messages containing geospatial locations and produce them **immediately** into a Kafka topic _people_geo_locations_ (without any handling, to ensure the high throughput required in the [Non-Functional Requirements](#non-functional-requirements)).
- [Mobilization-sorter](#mobilization-sorter-service) service - each service instance will consume geospatial messages from the Reciver's output topic, determine **in-memory** whether a message is from a pedestrian or mobilized individual based on the speed calculated between the last two points with the same UUID, and produce one message for each 2nd consumed message with the same UUID into one of the following topics:
  - _pedestrians_geo_locations_
  - _mobilized_geo_locations_
- [Locations-finder](#locations-finder-service) service - each service instance will consume points from one of the Mobilization-sorter's output topics, find the street or neighborhood name of the consumed point, and produce the location (street or neighborhood) into one of the following topics:
  - _pedestrians_streets_
  - _pedestrians_neighborhoods_
  - _mobilized_streets_
  - _mobilized_neighborhoods_
- [Activity-aggregator](#activity-aggregator-service) service - each service instance will consume points from one of the Locations-finder's output topics, aggregate **in-memory** the number of messages of each location (street or neighborhood) per minute, and periodically persist the aggregated data into one of the following tables:
  - _agg_streets_activity_
  - _agg_neighborhoods_activity_
- [Info](#info-service) service - will return data from the tables persisted by the Activity-aggregator service.

### Messaging

The various services communicate with each other using various messaging methods. Each method was selected based on the specific requirements from the services. Here are the various messaging methods used in the system:

- The [Receiver](#receiver-service) service exposes a REST API. Since it is the de-facto standard for most of the API consumers, and since this service is going to be used by different types of devices, it’s best to go for the most widely-used messaging method, which is REST API.<br>In [phase 2](architecture-document-phase-2-MQTT.md), MQTT will be considered as a alternate messaging method.

- The pipeline services ([Mobilization-sorter](#mobilization-sorter-service), [Locations-finder](#locations-finder-service) and [Activity-aggregator](#activity-aggregator-service)) will communicate thru Kafka. The reason for that is there is no requirement for a synchronous handling of the messages, and the pipeline services do not report back to the Receiver service when the handling is done. In addition, Kafka adds a layer of Fault Tolerance that does not exist in a REST API (all messages are persisted in Kafka logs, and can be consumed and re-consumed in case of failures).

- The [Info](#info-service) service exposes a REST API for similar reasons as the Receiver service. In addition, REST API is best suited for request/response model, which is the way this service will be used.

### Technology Stack

The following tech stack was preferred, primarily **due to current experience of the development team**:

1. JAVA **Spring Boot**:

   - **Rapid Development**: Spring Boot enables developers to quickly build applications with less boilerplate code and simplified configuration, resulting in faster development cycles and increased productivity.

   - Robust **Ecosystem**: Spring Boot leverages the extensive Spring ecosystem, providing a wide range of libraries and tools for various functionalities such as security, data access, and web development. This ecosystem enhances development efficiency, code reusability, and overall application quality.

   - **Production-Ready** features: Spring Boot includes built-in features for monitoring, logging, metrics, health checks, and configuration management, making it easier to develop and deploy production-ready applications. These features simplify operations, ensure application reliability, and facilitate scalability.

2. **Kafka**:

   - **High-throughput** and **scalable**: Kafka is designed to handle high volumes of data and can scale horizontally to accommodate growing demands.
   - **Real-time** data processing: Kafka enables real-time event **streaming** and data processing, making it suitable for applications that require real-time analytics, data integration, and event-driven architectures.

3. **PostgreSQL**:

   - **Reliability** and **stability**: PostgreSQL is known for its robustness, stability, and ACID compliance, making it a reliable choice for data storage.
   - **Advanced features**: PostgreSQL offers a wide range of advanced features such as JSON support, **spatial data support**, and full-text search capabilities, providing flexibility for various application requirements.

4. **Redis**:

   - **High performance**: Redis is an in-memory data store that delivers exceptional performance and low latency, ideal for applications that require fast data access and high-speed caching.
   - Versatility: Redis supports various data structures, including strings, lists, sets, and sorted sets, enabling different use cases such as caching, session management, real-time analytics, and pub/sub messaging.

5. **React**:
   - Component-based architecture: React's component-based approach allows for modular and reusable code, leading to improved development efficiency and code maintainability.
   - React **Native**: With React, you can develop cross-platform **mobile** applications using React Native, leveraging code sharing and faster development cycles.

### Non-Functional Attributes

#### Reliability

<p>(Definition: The software should be reliable and available for use whenever required. It should be able to handle errors, exceptions, and failures gracefully, ensuring minimal disruption to the system.)</p>

As explained in the [Messaging](#messaging) section, Kafka adds a layer of Fault Tolerance (all messages are persisted in Kafka logs, and can be consumed and re-consumed in case of failures).
Note: **Consumer groups rebalancing** must be handled properly by the services (refer specifically to the note in the [Activity-aggregator](#activity-aggregator-service) service).

#### Scalability

<p>(Definition: The software should be able to handle increased demands and growth without significant performance degradation. It should be designed to scale both vertically (adding more resources to a single machine) and horizontally (adding more machines to the system))</p>

This architecture allows to easily scale services as needed:

1. Each service has a specific, single task, and can be scaled independently, either automatically (by container orchestration systems such as Kubernetes) or manually (according to consumer groups lags, which can be viewed by any [Kafka UI](../mvp-level-implementation/scripts/deployment/docker-compose-3rd-party.yml)).
2. For example, the [Mobilization-sorter](#mobilization-sorter-service) service is responsible only to sort geospatial points to either pedestrians or mobilized points - other services are responsible to find streets/neighborhoods and to aggregate the data.
3. The services’ inner code is 100% stateless, allowing scaling to be performed on a live system, without changing any lines of code or shutting down the system.

#### Security

<p>(Definition: The software should have robust security measures in place to protect sensitive data, prevent unauthorized access, and mitigate any potential security vulnerabilities.)</p>

This external services ([Mobile application](#mobile-application), [Receiver](#receiver-service) and [Info](#info-service)) should support the [OAuth2](https://oauth.net/2/) (Open Authorization 2.0) and [JWT](https://jwt.io/) (JSON Web Tokens) authentication protocols.
Users should be able to authenticate using their Gmail account, for example, i.e. the system should not introduce a self made User Management component.

- [OAuth2](https://oauth.net/2/) is the primary authentication protocol used to grant access to user accounts without sharing the actual login credentials. It allows third-party applications to request access to user data on their behalf.
  During the OAuth2 authentication flow, the Gmail API uses JWT to generate and sign the access tokens.
- [JWT](https://jwt.io/) (JSON Web Tokens) are a compact, URL-safe means of representing claims between two parties. The access token contains information about the user and the permissions granted to the third-party application.
- In summary, OAuth2 is used for the overall authentication process, while JWT is used for generating and signing the access tokens that grant access to user data.

**Implementation Instructions**:

- Java Spring Boot has built-in support for these protocols, using the **spring-boot-starter-oauth2-client** and **spring-boot-starter-security** dependencies.

#### Testability

<p>(Definition: The software should be designed in a way that facilitates easy testing, both at unit and system levels. It should have proper logging and debugging mechanisms in place to aid in identifying and resolving issues)</p>

##### Logging

- Every step in the services should be logged. Since there is no UI for the services, logging is almost the only way of figuring out what’s going on (consumer groups lags can also shed some light on the progress).
- All services should be configured in docker-level (i.e. without changing logging functionality for each service) to redirect their logging into [graylog](https://docs.docker.com/config/containers/logging/gelf/).

##### System level testing

- Each service should be runnable **on its own**, with pre-prepared data, and have functionality to compare its output to the given input.
- For example, the [Receiver](#receiver-service) service in the [mvp-level-implementation](../mvp-level-implementation/README.md) is currently capable to execute on its own from a backup file: [receiver/start-service.cmd](../mvp-level-implementation/services/receiver/start-service.cmd) - refer to the environment variables URL_TO_EXECUTE_AFTER_STARTUP and PEOPLE_GEO_LOCATIONS_CSV.
- In addition, each such script should be enhanced to compare its output to the given input, allowing developers to verify the service execution under load (e.g. COPY_FROM_BACKUP=1*1000 for 1 thread * 1,000 iterations in [receiver/start-service.cmd](../mvp-level-implementation/services/receiver/start-service.cmd) above) during CI/CD.

#### Maintainability

<p>(Definition: The software should be designed in a way that makes it easy to understand, modify, and maintain over time. This includes considerations for code readability, proper documentation, and adherence to coding best practices.)</p>

As mentioned above, each service should hav a specific, single task. This is an important step in making the system easy to understand.
In addition, the development team should take into consideration best practices for code readability and proper documentation, preferring clear, modular and properly named software components rather than over-documenting.

## Services Drill Down

### Mobile application

#### Role

- To **collect geospatial locations**, e.g. from cell phones, and send messages to the [Receiver](#receiver-service) service.
- Each message contains a geospatial **point** of the location in which the data was collected.
- Each message also contains a city code, e.g. NYC. This will be used by the backend to load the required geospatial into the database, thus allowing the system to be generic, suitable for any city providing the maps.

<hr>

### Receiver service

#### Role

- To receive messages containing geospatial locations, e.g. from cell phones of **pedestrians** and **mobilized** individuals. <br>Each message will include the following details:
  1. UUID (Universal Unique Identifier).
  2. Coordinates (geospatial point).
  3. Timestamp.
  4. City code (e.g. NYC). This will be used by the backend to load the required geospatial into the database, thus allowing the system to be generic, suitable for any city providing the maps.
- To push (produce) these messages into a Kafka topic _people_geo_locations_ (from which they will be consumed and processed by the pipeline services).

#### Implementation Instructions

- This service should contain as little code as possible. No logic should take place there, and its only task is to receive messages and produce them into kafka.

#### APIs

[postman-collection.json](../mvp-level-implementation/services/receiver/postman-collection.json)

<hr>

### Mobilization-sorter service

#### Role

- To consume geospatial messages from the topic _people_geo_locations_.
- To determine whether a message is from a **pedestrian** or **mobilized** individual based on the speed calculated between the last two points with the same UUID. If the calculated speed is less than 25 km/h, the message should be classified as coming from a pedestrian, otherwise as coming from a mobilized individual.
- To produce one message for each 2nd consumed message with the same UUID into one of the following topics:
  - _pedestrians_geo_locations_
  - _mobilized_geo_locations_

#### Implementation Instructions

- The service can and should perform the necessary geospatial calculations using a distributed cache (e.g. Redis), i.e. without relying on PostgreSQL GIS capabilities. The motivation for that is to prevent overloading the database unnecessarily
- The service should have a built-in mechanism to perform garbage collection on data cached in Redis.

<hr>

### Locations-finder service

#### Role

1. To consume from one of the following topics:
   1. _pedestrians_geo_locations_
   2. _mobilized_geo_locations_
2. To find a street or neighborhood name by the consumed point on the street's or neighborhood's geometry. This step depends on loading the relevant maps into the database according to the city code contained in each message (refer to the [Receiver](#receiver-service) service for further details).
3. To produce the location (street or neighborhood) into one of the following topics:
   1. _pedestrians_streets_ - each message is a pedestrian and a street name.
   2. _pedestrians_neighborhoods_ - each message is a pedestrian and a neighborhood name.
   3. _mobilized_streets_ - each message is a mobilized (i.e. non-pedestrian person) and a street name.
   4. _mobilized_neighborhoods_ - each message is a mobilized and a neighborhood name.

#### Implementation Instructions

Each instance of this service should handle one and only one combination of mobility type (pedestrians or mobilized) and location type (streets or neighborhoods). This will allow to easily [scale](#scaling) the services accurately according to load (for example, assuming that there're more pedestrians than mobilized, and more streets than neighborhoods, then more replicas for this combination should be started (either automatically or manually) than other combinations).

#### Deployment Instructions

The geospatial points are located within a geometries of streets and neighborhoods of the NYC database.
For better [scalability](#scaling) it is advisable to configure **read-only replicas** of the NYC database, to isolate these queries from the database used for aggregations (by the [Activity-aggregator](#activity-aggregator-service) service).

<hr>

### Delay service

#### Role

To process messages containing events that should be:
1. Delayed for a required duration, and then,
2. Produced to a target topic described in each message.

#### [Diagram](https://lucid.app/documents/view/9b48ab81-1cc7-44c1-b8bb-a92ec78b2802)
![Lucid](https://lucid.app/publicSegments/view/6da4c3f1-3886-4dc8-baea-45d8ade5daac/image.jpeg 'System diagram')

<hr>

### Activity-aggregator service

#### Role

1. To aggregate in-memory the number of pedestrians and mobilized per 1 minute.
2. To periodically persist the aggregated data into the following PostgreSQL tables:
   1. _agg_streets_activity_
   2. _agg_neighborhoods_activity_.

#### Implementation Instructions

1. Each instance of this service should aggregate and persist data for one and only **one combination** of **Pedestrians**/**mobilized** and **streets**/**neighborhoods**.
2. The combination should be configured by two environment variables, that together decide the input topic (one of the output topics of the [Locations-finder service](#locations-finder-service)). This will allow [scaling](#scalability) specific combinations according to the load, as explained in the [Locations-finder](#locations-finder-service) service.
3. The persistence interval and number of records should be configurable by environment variables. This should provide a way to control the memory consumption and database persistence time.
4. **Consumer groups rebalancing must be handled** properly by this service, otherwise messages aggregated in-memory might be lost when the service will attempt to commit the uncommitted offsets associated with these messages.

Further details can be found in the [mvp-level implementation](../mvp-level-implementation/services/activity-aggregator/architecture.md).

<hr>

### Info service

#### Role

- This service should provide the ability to retrieve information on streets and neighborhoods with the highest number of pedestrians or mobilized individuals.
- This retrieval should be possible **in real time**, allowing users to specify the desired timeframe (in minutes resolution) within the last 24 hours.
- The results should be returned in descending order based on the number of pedestrians or mobilized individuals.
- Users should be able to specify the number of streets (N1) and neighborhoods (N2) they want to retrieve.

#### APIs:

[postman-collection.json](../mvp-level-implementation/services/info/postman-collection.json)

<hr>
