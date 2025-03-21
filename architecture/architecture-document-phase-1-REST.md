# <font color="LightSeaGreen">UrbanGeoPulse</font>

#### A Big Data Geospatial Application.

# Architecture Document

### Table Of Content

<!-- toc -->

- [Background](#background)
- [Requirements](#requirements)
  - [Functional Requirements](#functional-requirements)
  - [Non-Functional Requirements](#non-functional-requirements)
- [Executive Summary](#executive-summary)
- [Overall Architecture](#overall-architecture)
  - [Detailed diagram](#detailed-diagram)
  - [Services](#services)
  - [Messaging](#messaging)
  - [Technology Stack](#technology-stack)
    - [JAVA _Spring Boot_](#java-_spring-boot_)
    - [_Kafka_](#_kafka_)
    - [_PostgreSQL_](#_postgresql_)
    - [_MongoDB_](#_mongodb_)
    - [_Redis_](#_redis_)
    - [_React_](#_react_)
  - [Non-Functional Attributes](#non-functional-attributes)
    - [High-Performance:](#high-performance)
      - [Performance](#performance)
      - [Scalability](#scalability)
    - [Resiliency:](#resiliency)
      - [High Availability](#high-availability)
      - [Fault Tolerance](#fault-tolerance)
    - [Security:](#security)
    - [Maintainability:](#maintainability)
      - [Testability:](#testability)
        - [Logging](#logging)
        - [System level testing](#system-level-testing)
    - [Extensibility](#extensibility)
- [Services Drill Down](#services-drill-down)
  - [Mobile application](#mobile-application)
    - [Role](#role)
  - [Receiver service](#receiver-service)
    - [Role](#role-1)
    - [Implementation Instructions](#implementation-instructions)
    - [Postman API collections](#postman-api-collections)
  - [Mobilization-classifier service](#mobilization-classifier-service)
    - [Role](#role-2)
    - [Implementation Instructions](#implementation-instructions-1)
  - [Locations-finder service](#locations-finder-service)
    - [Role](#role-3)
    - [Implementation Instructions](#implementation-instructions-2)
    - [Deployment Instructions](#deployment-instructions)
  - [Delay service](#delay-service)
    - [Role](#role-4)
    - [Diagram](#diagram)
  - [Activity-aggregator service](#activity-aggregator-service)
    - [Role](#role-5)
    - [Implementation Instructions](#implementation-instructions-3)
  - [Info service](#info-service)
    - [Role](#role-6)
    - [APIs:](#apis)
- [Appendix: 12-Factor App methodology](#appendix-12-factor-app-methodology)
  - [Conclusion](#conclusion)

<!-- tocstop -->

## Background

This document describes the **UrbanGeoPulse**'s architecture, a system requested by the city of New York (NYC).<br>
(This is a **showcase** of a **software architecture** definition process. The requirements are hypothetical, inspired by the [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.)

NYC requires **real-time** information on the streets and neighborhoods with the highest concentration of **pedestrians** and **non-pedestrians** (referred to as **mobilized** individuals) at any requested timeframe within the last 24 hours.<br>
This information will be used to **make decisions** regarding **real-time** needs such as the deployment of police resources and traffic control, as well as **long-term** considerations like transportation budgets, timing of municipal construction work, and advertising fees.

The architecture comprises technology and modeling decisions that will ensure the final product will be fast, reliable and easy to maintain.
The document outlines the thought process for every aspect of the architecture, and explains why specific decisions were made.
It’s extremely important for the development team to closely follow the architecture depicted in this document. In any case of doubt please consult the Software Architect.

## Requirements

### Functional Requirements

1. [Receive](#receiver-service) messages containing **geospatial locations** from cell phones of **pedestrians** and **mobilized** individuals.

2. [Identify](#mobilization-classifier-service) each message's source (**pedestrian** or **mobilized** individual) based on the speed calculated between the last two messages sent from the same device.

3. Allow users to [retrieve](#info-service) streets and neighborhoods activity **in real time** for any requested timeframe within the last 24 hours.

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

This document describes the architecture of the **UrbanGeoPulse** application, as described in the [Background](#background) section. <br><br>
When designing the architecture, a strong emphasis was put on the following qualities:

- The application should be fast (to support real-time needs such as the deployment of police resources and traffic control).
- The application should be reliable and support very high load (to support the population of NYC, specifically the number of active mobile devices during rush hours).
<p>To achieve these qualities, the architecture is based on the most up-to-date best practices and methodologies, ensuring performance and high-availability.</p>

Here is a high-level overview of the architecture:
![Lucid](https://lucid.app/publicSegments/view/e6d6bb91-8e6e-43b5-9f44-49935994172d/image.jpeg 'System diagram')
As can be seen in the diagram, the application comprises a few separate, independent, loosely-coupled **microservices**, each has its own task, and each communicates with the other services using standard protocols.

All the services are stateless, allowing them to **[scale](#scalability)** easily and seamlessly. In addition, the architecture is **[resilient](#resiliency)** - no data is lost if any service suddenly shuts down. The only places for data in the application are Kafka and the data store (PostgreSQL and MongoDB), all of them persist the data to the disk, thus protecting data from cases of shutdown.

This architecture, in conjunction with a modern development platform (refer to [basic JAVA Spring Boot implementation](../basic-implementation/README.md)) will ensure a modern, **scalable**, **reliable**, and **easy to maintain** system, that can serve NYC successfully for years to come, and help achieve its goals.

## Overall Architecture

### [Detailed diagram](https://lucid.app/publicSegments/view/1146cc57-0419-4bd8-a5ec-75b76874425d/image.jpeg)

![Lucid](https://lucid.app/publicSegments/view/e6d6bb91-8e6e-43b5-9f44-49935994172d/image.jpeg 'System diagram')

- The architecture follows the [**12-Factor App methodology**](https://12factor.net).

### Services

The architecture comprises the following services:

- [Mobile application](#mobile-application) - will collect geospatial locations and send messages to the [Receiver service](#receiver-service). Each message should also contain the city code, e.g. NYC. This will be used by the backend to load the required geospatial into the database, thus allowing the system to be generic, suitable for any city providing the maps.
- [Receiver](#receiver-service) service - will receive messages containing geospatial locations and produce them **immediately** into a Kafka topic _people_geo_locations_ (without any handling, to ensure the high throughput required in the [Non-Functional Requirements](#non-functional-requirements)).
- [Mobilization-classifier](#mobilization-classifier-service) service - each service instance will consume geospatial messages from the Receiver's output topic, determine **in-memory** whether a message is from a pedestrian or mobilized individual based on the speed calculated between the last two points with the same UUID, and produce one message for each 2nd consumed message with the same UUID into one of the following topics:
  - _pedestrians_geo_locations_
  - _mobilized_geo_locations_
- [Locations-finder](#locations-finder-service) service - each service instance will consume points from one of the Mobilization-classifier's output topics, find the street or neighborhood name of the consumed point using geospatial queries, and produce the location (street or neighborhood) into one of the following topics:
  - _pedestrians_streets_
  - _pedestrians_neighborhoods_
  - _mobilized_streets_
  - _mobilized_neighborhoods_
- [Activity-aggregator](#activity-aggregator-service) service - each service instance will consume points from one of the Locations-finder's output topics, aggregate **in-memory** the number of messages of each location (street or neighborhood) per minute, and periodically persist the aggregated data into one of the following [_MongoDB_](#mongodb) tables:
  - _agg_streets_activity_
  - _agg_neighborhoods_activity_
- [Info](#info-service) service - will return data from the tables persisted by the Activity-aggregator service.

### Messaging

- The [Receiver](#receiver-service) service exposes a **REST API**. Since it is the de-facto standard for most of the API consumers, and since this service is going to be used by different types of devices, it’s best to go for the most widely-used messaging method, which is REST API.<br>In [phase 2](architecture-document-phase-2-MQTT.md), **MQTT** will be considered as a alternate messaging method.

- The pipeline services ([Mobilization-classifier](#mobilization-classifier-service), [Locations-finder](#locations-finder-service) and [Activity-aggregator](#activity-aggregator-service)) will communicate thru **Kafka**. The reason for that is there is no requirement for a synchronous handling of the messages, and the pipeline services do not report back to the Receiver service when the handling is done. In addition, Kafka adds a layer of Fault Tolerance that does not exist in a REST API (all messages are persisted in Kafka logs, and can be consumed and re-consumed in case of failures).

- The [Info](#info-service) service also exposes a **REST API** for similar reasons as the Receiver service. In addition, REST API is best suited for request/response model, which is the way this service will be used.

### Technology Stack

The following tech stack was preferred, primarily **due to current experience of the development team**:

#### JAVA _Spring Boot_

- **Rapid Development**: Spring Boot enables developers to quickly build applications with less boilerplate code and simplified configuration, resulting in faster development cycles and increased productivity.

- Robust **Ecosystem**: Spring Boot leverages the extensive Spring ecosystem, providing a wide range of libraries and tools for various functionalities such as security, data access, and web development. This ecosystem enhances development efficiency, code reusability, and overall application quality.

- **Production-Ready** features: Spring Boot includes built-in features for monitoring, logging, metrics, health checks, and configuration management, making it easier to develop and deploy production-ready applications. These features simplify operations, ensure application reliability, and facilitate scalability.

#### _Kafka_

- **High-throughput** and **scalable**: Kafka is designed to handle high volumes of data and can scale horizontally to accommodate growing demands.
- **Real-time** data processing: Kafka enables real-time event **streaming** and data processing, making it suitable for applications that require real-time analytics, data integration, and event-driven architectures.

#### _PostgreSQL_

- **Reliability** and **stability**: PostgreSQL is known for its robustness, stability, and ACID compliance, making it a reliable choice for data storage.
- **Advanced features**: PostgreSQL offers a wide range of relevant advanced features such as **spatial data support**, JSON support, and full-text search capabilities, providing flexibility for various application requirements.

#### _MongoDB_

- The data pattern of the [Activity-aggregator](#activity-aggregator-service) service fits NoSQL well:
  - Time-series aggregated data
  - No complex joins needed (existing joins in [InfoDataService](../basic-implementation/services/info/src/main/java/com/urbangeopulse/info/services/InfoDataService.java) were easily modified to retrieve from postgres only the required names for the relatively small amount of selected records)
  - No geospatial queries required for these specific tables
  - Write-heavy workload (periodic persistence of aggregations)

#### _Redis_

- **High performance**: Redis is an in-memory data store that delivers exceptional performance and low latency, ideal for applications that require fast data access and high-speed caching.
- Versatility: Redis supports various data structures, including strings, lists, sets, and sorted sets, enabling different use cases such as caching, session management, real-time analytics, and pub/sub messaging.

#### _React_

- Component-based architecture: React's component-based approach allows for modular and reusable code, leading to improved development efficiency and code maintainability.
- React **Native**: With React, you can develop cross-platform **mobile** applications using React Native, leveraging code sharing and faster development cycles.

### Non-Functional Attributes

#### High-Performance:

The system's performance is maintained through several key mechanisms:

1. Efficient Data Processing

   - Event-driven in-memory aggregation
   - Minute-resolution state persistence

2. Optimized Storage and Concurrency
   - Thread-safe operations
   - Time-based data consolidation
   - Minimized storage footprint (e.g., high-frequency events consolidated to minute-level statistics)

##### Scalability

`Definition: The software should be able to handle increased demands and growth without significant performance degradation. It should be designed to scale both vertically (adding more resources to a single machine) and horizontally (adding more machines to the system)`

The system's architecture enables seamless scaling through:

1. Service Isolation

   - Single-responsibility services
   - Independent scaling boundaries
   - Stateless processing model

2. Resource Management
   - Container orchestration
   - Event-based scaling
   - Distributed processing

#### Resiliency:

`Definition: The software should be reliable and available for use whenever required. It should be able to handle errors, exceptions, and failures gracefully, ensuring minimal disruption to the system.`

##### High Availability

The system ensures continuous operation through:

1. Service Architecture

   - Independent service scaling
   - Event-driven processing
   - Resource optimization

2. Data Resilience
   - Message persistence
   - State recovery
   - Event replay capability

##### Fault Tolerance

The system maintains reliability through:

1. Message Handling

   - Durable event storage
   - Guaranteed message delivery
   - Consumer group coordination

2. State Management
   - In-memory aggregation
   - Message-based recovery
   - Minute-resolution persistence

#### Security:

`Definition: The software should have robust security measures in place to protect sensitive data, prevent unauthorized access, and mitigate any potential security vulnerabilities.`

The system should implement a delegated security model:

1. Authentication Flow

   - Third-party identity providers
   - Token-based authentication
   - Stateless security model

2. Authorization Strategy
   - Service-level authorization
   - Secure token management
   - Resource-based access control

The services [Mobile application](#mobile-application), [Receiver](#receiver-service) and [Info](#info-service) should support the [OAuth2](https://oauth.net/2/) (Open Authorization 2.0) and [JWT](https://jwt.io/) (JSON Web Tokens) authentication protocols.
Users should be able to authenticate using their Gmail account, for example, i.e. the system should not introduce a self made User Management component.

- [OAuth2](https://oauth.net/2/) is the primary authentication protocol used to grant access to user accounts without sharing the actual login credentials. It allows third-party applications to request access to user data on their behalf.
  During the OAuth2 authentication flow, Google API uses JWT to generate and sign the access tokens.
- [JWT](https://jwt.io/) (JSON Web Tokens) are a compact, URL-safe means of representing claims between two parties. The access token contains information about the user and the permissions granted to the third-party application.
- In summary, OAuth2 is used for the overall authentication process, while JWT is used for generating and signing the access tokens that grant access to user data.

(Implementation Instructions: Java Spring Boot has built-in support for these protocols, using the **spring-boot-starter-oauth2-client** and **spring-boot-starter-security** dependencies)

#### Maintainability:

`Definition: The software should be designed in a way that makes it easy to understand, modify, and maintain over time. This includes considerations for code readability, proper documentation, and adherence to coding best practices.`

The architecture promotes maintainability through:

1. System Design

   - Service boundaries
   - Event-driven interfaces
   - Modular components

2. Operational Visibility
   - Centralized monitoring
   - Service-level metrics
   - Independent testing capabilities

##### Testability:

`Definition: The software should be designed in a way that facilitates easy testing, both at unit and system levels. It should have proper logging and debugging mechanisms in place to aid in identifying and resolving issues.`

The system ensures testability through:

1. Service Isolation

   - Independent testing of microservices
   - Clear interfaces and APIs

2. Operational Support
   - Centralized logging
   - System-wide monitoring
   - Independent service testing

###### Logging

- Every step in the services should be logged. Since there is no UI for the services, logging is almost the only way of figuring out what’s going on (consumer groups lags can also shed some light on the progress).
- All services should be configured in docker-level (i.e. without changing logging functionality for each service) to redirect their logging into [graylog](https://docs.docker.com/config/containers/logging/gelf/).

###### System level testing

- Each service should be runnable **on its own**, with pre-prepared data, and have functionality to compare its output to the given input.
- For example, the [Receiver](#receiver-service) service in the [basic-implementation](../basic-implementation/README.md) is currently capable to execute on its own from a backup file: [receiver/start-service.cmd](../basic-implementation/services/receiver/start-service.cmd) - refer to the environment variables URL_TO_EXECUTE_AFTER_STARTUP and PEOPLE_GEO_LOCATIONS_CSV.
- In addition, each such script should be enhanced to compare its output to the given input, allowing developers to verify the service execution under load (refer to SIMULATOR_ITERATIONS_FROM_BACKUP in [receiver/start-service.cmd](../basic-implementation/services/receiver/start-service.cmd) above) during CI/CD.

#### Extensibility

`Definition: The software should be designed in a way that facilitates adding new features without modifying the existing system`.

The system's architecture enables feature growth through:

1. Service Composition

   - Event-driven integration
   - Loose service coupling
   - Independent deployment

2. Interface Design
   - Message-based contracts
   - Version-tolerant APIs
   - Pluggable components

## Services Drill Down

### Mobile application

#### Role

- To **collect geospatial locations** from cell phones, and send messages to the [Receiver](#receiver-service) service.
- Each message contains a geospatial **point** of the location in which the data was collected.
- Each message also contains a city code, e.g. NYC. This will be used by the backend to load the required geospatial into the database, thus allowing the system to be generic, suitable for any city providing the maps.

<hr>

### Receiver service

#### Role

- To **receive messages** containing geospatial locations from cell phones of **pedestrians** and **mobilized** individuals. <br>Each message will include the following details:
  1. UUID (Universal Unique Identifier).
  2. Coordinates (geospatial point).
  3. Timestamp.
  4. City code (e.g. NYC). This will be used by the backend to load the required geospatial into the database, thus allowing the system to be generic, suitable for any city providing the maps.
- To **normalize** and **produce** these messages into a Kafka topic _people_geo_locations_ (from which they will be consumed and processed by downstream pipeline services).

#### Implementation Instructions

- This service should contain as little code as possible. No logic should take place there, and its only task is to receive messages and produce them into kafka.

#### Postman API collections

- [receiver-postman-collection.json](../basic-implementation/services/receiver/receiver-postman-collection.json)
- [simulator-postman-collection.json](../basic-implementation/services/receiver/simulator-postman-collection.json)

<hr>

### Mobilization-classifier service

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
2. To find a street or neighborhood name by locating the consumed point on the street's or neighborhood's geometry. This step depends on loading the relevant maps into the database according to the city code contained in each message (refer to the [Receiver](#receiver-service) service for further details).
3. To produce the location (street or neighborhood) into one of the following topics:
   1. _pedestrians_streets_ - each message is a pedestrian and a street name.
   2. _pedestrians_neighborhoods_ - each message is a pedestrian and a neighborhood name.
   3. _mobilized_streets_ - each message is a mobilized (i.e. non-pedestrian person) and a street name.
   4. _mobilized_neighborhoods_ - each message is a mobilized and a neighborhood name.

#### Implementation Instructions

Each instance of this service should handle one and only one combination of mobility type (pedestrians or mobilized) and location type (streets or neighborhoods). This will allow to easily [scale](#scaling) the services accurately according to load (for example, assuming that there're more pedestrians than mobilized, and more streets than neighborhoods, then more replicas for this combination chould be started (either automatically or manually) than other combinations).

#### Deployment Instructions

The geospatial points are located within a geometries of streets and neighborhoods of the NYC database.
For better [scalability](#scaling) it is advisable to configure **read-only replicas** of the NYC database.

<hr>

### Delay service

#### Role

To process messages containing events that should be:

1. Delayed for a required duration, and then,
2. Produced to a target topic described in each message.

#### [Diagram](https://lucid.app/publicSegments/view/1146cc57-0419-4bd8-a5ec-75b76874425d/image.jpeg)

![Lucid](https://lucid.app/publicSegments/view/6da4c3f1-3886-4dc8-baea-45d8ade5daac/image.jpeg 'System diagram')

<hr>

### Activity-aggregator service

#### Role

1. To aggregate **in-memory** the number of pedestrians and mobilized per duration (e.g. 1 minute).
2. To periodically persist the aggregated data into the following [_MongoDB_](#mongodb) tables:
   1. _agg_streets_activity_
   2. _agg_neighborhoods_activity_.

#### Implementation Instructions

- Each instance of this service should aggregate and persist data for one and only **one combination** of mobility type (**Pedestrians**/**mobilized**) and location type (**streets**/**neighborhoods**).
- The combination should be configurable by environment variable(s).
- This will allow [scaling](#scalability) specific combinations according to the load, similar to the [Locations-finder](#implementation-instructions-2) service.
- The persistence interval and number of records should be configurable by environment variable(s). This will provide a way to control the memory consumption and database persistence time.
- **Consumer groups rebalancing** must be handled properly by this service, otherwise messages aggregated in-memory might be lost when the service will attempt to commit the uncommitted offsets associated with these messages.

Further details can be found in the [basic-implementation](../basic-implementation/services/activity-aggregator/readme.md).

<hr>

### Info service

#### Role

- This service should provide the ability to retrieve information on streets and neighborhoods with the highest number of pedestrians or mobilized individuals.
- This retrieval should be possible **in real time**, allowing users to specify any requested timeframe (in minutes resolution) within the last 24 hours.
- The results should be returned in descending order based on the number of pedestrians or mobilized individuals.
- Users should be able to specify the number of streets (N1) and neighborhoods (N2) they want to retrieve.

#### APIs:

[postman-collection.json](../basic-implementation/services/info/postman-collection.json)

<hr>

## Appendix: 12-Factor App methodology

[**12-Factor App methodology**](https://12factor.net)

To assess whether the **UrbanGeoPulse** architecture adheres to the 12-Factor App methodology, we'll evaluate the architecture's components against the twelve factors outlined in the methodology:

1. **Codebase**: A single codebase tracked in revision control, with many deploys.

   - The architecture should follow this principle by maintaining a single repository for the microservices.

2. **Dependencies**: Explicitly declare and isolate dependencies.

   - The architecture outlines services that are stateless and communicates through Kafka and REST APIs. Each service should specify its dependencies clearly (e.g., through a `pom.xml` for Java).

3. **Configuration**: Store configuration in the environment.

   - The document mentions the use of environment variables for configuration, especially in the service implementations.

4. **Backing services**: Treat backing services as attached resources.

   - Kafka and PostgreSQL are treated as services that can be swapped out or replaced as needed. This adheres to the principle.

5. **Build, release, run**: Strictly separate the build and run stages.

   - If the deployment process is clearly outlined to separate building (e.g., using CI/CD pipelines) from running applications in production, this is followed. However, the document does not explicitly describe this separation.

6. **Processes**: Execute the app as one or more stateless processes.

   - The architecture emphasizes stateless services, allowing for horizontal scaling.

7. **Port binding**: Export services via port binding.

   - The services are meant to be accessed via APIs, which implies port binding via HTTP and/or Kafka.

8. **Concurrency**: Scale out via the process model.

   - The architecture describes scaling services independently based on load.

9. **Disposability**: Maximize robustness with fast startup and graceful shutdown.

   - While not explicit, if the services are designed for quick startup and can handle graceful shutdown (especially in a cloud-native context), this factor is adhered to.

10. **Dev/prod parity**: Keep development, staging, and production as similar as possible.

    - The architecture should ideally ensure similar environments across dev, test, and production, which is not specifically mentioned in the document but should be a best practice for adherence.

11. **Logs**: Treat logs as event streams.

    - The architecture discusses logging to a central logging system (e.g., Graylog), which aligns with this principle.

12. **Admin processes**: Run administrative/management tasks as one-off processes.
    - The document does not clearly define how admin tasks are managed or executed, which may imply a lack of adherence to this factor.

### Conclusion

Overall, the **UrbanGeoPulse** architecture demonstrates a strong alignment with many of the 12 factors, particularly in service independence, configuration management, and statelessness. However, there are areas where details could be clearer or more explicitly defined, particularly regarding the build/release/run separation and admin processes.
