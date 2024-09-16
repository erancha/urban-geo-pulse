# <font color="LightSeaGreen">UrbanGeoPulse</font>

#### A Big Data Geospatial Application.

# Architecture Document

<small>Note: This document is based on an Architecture Document template provided as part of “The complete guide to becoming a great Software Architect” course, by Memi Lavi.
The template is a copyrighted material by Memi Lavi (www.memilavi.com, memi@memilavi.com).</small>

### Table Of Content

<!-- toc -->

  * [Background](#background)
  * [Requirements](#requirements)
    + [Functional Requirements](#functional-requirements)
    + [Non-Functional Requirements](#non-functional-requirements)
  * [Executive Summary](#executive-summary)
  * [Overall Architecture](#overall-architecture)
    + [Detailed diagram](#detailed-diagram)
    + [Services Overview](#services-overview)
    + [Messaging](#messaging)
    + [Technology Stack](#technology-stack)
    + [Non-Functional Attributes](#non-functional-attributes)
      - [High-Performance](#high-performance)
      - [Resiliency](#resiliency)
      - [Security](#security)
      - [Maintainability](#maintainability)
  * [Services Drill Down](#services-drill-down)
    + [Mobile application](#mobile-application)
      - [Role](#role)
    + [Receiver service](#receiver-service)
      - [Role](#role-1)
      - [Implementation Instructions](#implementation-instructions)
      - [APIs](#apis)
    + [Mobilization-sorter service](#mobilization-sorter-service)
      - [Role](#role-2)
      - [Implementation Instructions](#implementation-instructions-1)
    + [Locations-finder service](#locations-finder-service)
      - [Role](#role-3)
      - [Implementation Instructions](#implementation-instructions-2)
      - [Deployment Instructions](#deployment-instructions)
    + [Delay service](#delay-service)
      - [Role](#role-4)
      - [Diagram](#diagram)
    + [Activity-aggregator service](#activity-aggregator-service)
      - [Role](#role-5)
      - [Implementation Instructions](#implementation-instructions-3)
    + [Info service](#info-service)
      - [Role](#role-6)
      - [APIs:](#apis)
- [Appendices](#appendices)
  * [Non-Functional Attributes - definitions](#non-functional-attributes---definitions)
    + [High-Performance:](#high-performance)
      - [Performance](#performance)
      - [Scalability](#scalability)
    + [Resiliency:](#resiliency)
      - [High Availability](#high-availability)
      - [Fault Tolerance](#fault-tolerance)
    + [Security](#security-1)
    + [Maintainability:](#maintainability)
      - [Testability:](#testability)
    + [Extensibility](#extensibility)
  * [Technology Stack - features overview](#technology-stack---features-overview)
    + [JAVA Spring Boot](#java-spring-boot)
    + [Kafka](#kafka)
    + [PostgreSQL](#postgresql)
    + [MongoDB](#mongodb)
    + [Redis](#redis)
    + [React](#react)
  * [OAuth2 and JWT - overview](#oauth2-and-jwt---overview)
  * [12-Factor App methodology](#12-factor-app-methodology)
    + [Codebase:](#codebase)
    + [Dependencies:](#dependencies)
    + [Config:](#config)
    + [Backing Services:](#backing-services)
    + [Build, Release, Run:](#build-release-run)
    + [Processes:](#processes)
    + [Port Binding:](#port-binding)
    + [Concurrency:](#concurrency)
    + [Disposability:](#disposability)
    + [Dev/Prod Parity:](#devprod-parity)
    + [Logs:](#logs)
    + [Admin Processes:](#admin-processes)
  * ["Build to scale" and "Build to fail" methodologies](#build-to-scale-and-build-to-fail-methodologies)
    + [Build to Scale](#build-to-scale)
      - [Key Characteristics:](#key-characteristics)
        * [Scalability:](#scalability)
        * [Microservices Architecture:](#microservices-architecture)
        * [Load Balancing:](#load-balancing)
        * [Auto-scaling:](#auto-scaling)
        * [Performance Testing:](#performance-testing)
    + [Build to Fail](#build-to-fail)
      - [Key Characteristics:](#key-characteristics-1)
        * [Resilience:](#resilience)
        * [Fault Tolerance:](#fault-tolerance)
        * [Chaos Engineering:](#chaos-engineering)
        * [Monitoring and Alerts:](#monitoring-and-alerts)
    + [Summary](#summary)

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

1. [Receive](#receiver-service) messages containing **geospatial locations**, e.g. from cell phones of **pedestrians** and **mobilized** individuals.

2. [Identify](#mobilization-sorter-service) each message's source (**pedestrian** or **mobilized** individual) based on the speed calculated between the last two messages sent from the same device.

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
![Lucid](https://lucid.app/publicSegments/view/e56b4eaa-3f1a-4631-9af4-5b4dabd2b592/image.jpeg 'System diagram')
As can be seen in the diagram, the application comprises a few separate, independent, loosely-coupled **microservices**, each has its own task, and each communicates with the other services using standard protocols.

All the services are stateless, allowing them to **[scale](#scalability)** easily and seamlessly. In addition, the architecture is **[resilient](#resiliency)** - no data is lost if any service suddenly shuts down. The only places for data in the application are Kafka and the Data Store (PostgreSQL and MongoDB), all of them persist the data to the disk, thus protecting data from cases of shutdown.

This architecture, in conjunction with a modern development platform (refer to [MVP-level JAVA Spring Boot implementation](mvp-level-implementation/README.md)), will help create a **modern**, **robust**, **scalable**, **easy to maintain**, and **reliable** system, that can serve NYC successfully for years to come, and help achieve its financial goals.

## Overall Architecture

### [Detailed diagram](https://lucid.app/documents/view/9b48ab81-1cc7-44c1-b8bb-a92ec78b2802)

![Lucid](https://lucid.app/publicSegments/view/6bffea51-c248-49e8-a244-a0a691a3ab9d/image.jpeg 'System diagram')

### Services Overview

The architecture comprises the following key services:

- [Mobile application](#mobile-application) - will collect geospatial locations and send messages to the [Receiver service](#receiver-service). Each message should also contain the city code, e.g. NYC. This will be used by the backend to load the required geospatial into the database, thus allowing the system to be generic, suitable for any city providing the maps.

- [Receiver](#receiver-service) service - will receive messages containing geospatial locations and produce them **immediately** into a Kafka topic _people_geo_locations_ (without any handling, to ensure the high throughput required in the [Non-Functional Requirements](#non-functional-requirements)).
 
- [Mobilization-sorter](#mobilization-sorter-service) service - each service instance will consume geospatial messages from the Reciver's output topic, determine **in-memory** whether a message is from a pedestrian or mobilized individual based on the speed calculated between the last two points with the same UUID, and produce one message for each 2nd consumed message with the same UUID into one of the following topics:
  - _pedestrians_geo_locations_
  - _mobilized_geo_locations_
  <br><br>Note: This service uses [Redis](#redis) to increase [Performance](#performance-1).

  
- [Locations-finder](#locations-finder-service) service - each service instance will consume points from one of the Mobilization-sorter's output topics, find the street or neighborhood name of the consumed point, and produce the location (street or neighborhood) into one of the following topics:
  - _pedestrians_streets_
  - _pedestrians_neighborhoods_
  - _mobilized_streets_
  - _mobilized_neighborhoods_
  <br><br>Note: This service uses [PostgreSQL](#postgresql) datasets provided by the [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop. The database should have read replicas, to increase read scalability.
  
  
- [Activity-aggregator](#activity-aggregator-service) service - each service instance will consume points from one of the Locations-finder's output topics, aggregate **in-memory** the number of messages of each location (street or neighborhood) per minute, and periodically persist the aggregated data into one of the following tables:
  - _agg_streets_activity_
  - _agg_neighborhoods_activity_
    <br><br>Note: This service uses [MongoDB](#mongodb) primarily to ensure higher [Scalability](#scalability-1), and to benefit from Schema Flexibility in future cases (refer to [Extensibility](#extensibility) in the appendix below).


- [Info](#info-service) service - will return data from the tables persisted by the Activity-aggregator service.

### Messaging

- The [Receiver](#receiver-service) service exposes a **REST API**. Since it is the de-facto standard for most of the API consumers, and since this service is going to be used by different types of devices, it’s best to go for the most widely-used messaging method, which is REST API.<br>In [phase 2](architecture-document-phase-2-MQTT.md), **MQTT** will be considered as a alternate messaging method.

- The pipeline services ([Mobilization-sorter](#mobilization-sorter-service), [Locations-finder](#locations-finder-service) and [Activity-aggregator](#activity-aggregator-service)) will communicate thru **[Kafka](#kafka)**. The reason for that is there is no requirement for a synchronous handling of the messages, and the pipeline services do not report back to the Receiver service when the handling is done. In addition, Kafka adds a layer of Fault Tolerance that does not exist in a REST API (all messages are persisted in Kafka logs, and can be consumed and re-consumed in case of failures).

- The [Info](#info-service) service also exposes a **REST API** for similar reasons as the Receiver service. In addition, REST API is best suited for request/response model, which is the way this service will be used.

### Technology Stack
The following tech stack was preferred, partially **due to current experience of the development team** and partially for the following technical benefits: [Technology Stack - features overview](#technology-stack---features-overview).
- JAVA Spring Boot
- Kafka
- PostgreSQL
- MongoDB
- Redis
- React

### Non-Functional Attributes

#### [High-Performance](#high-performance-1)

**[Performance](#performance-1)**: The architecture is designed to handle messages and move them thru the pipeline quickly:

1. Each service uses a data store appropriate for its needs. For example, the [Mobilization-sorter](#mobilization-sorter-service) service uses [Redis](#redis) to increase [Performance](#performance-1), and the [Activity-aggregator](#activity-aggregator-service) service uses [MongoDB](#mongodb) to benefit from its high performance and take load off [PostgreSQL](#postgresql) which is mandatory required by the [Locations-finder](#locations-finder-service) service.
2. Compute resources should be adjusted for each service. For example, the [Mobilization-sorter](#mobilization-sorter-service) service should be assigned compute and memory intensive resources, while the [Locations-finder](#locations-finder-service) and [Activity-aggregator](#activity-aggregator-service) services should be assigned storage intensive resource.  

**[Scalability](#scalability-1)**: The architecture allows to easily scale services as needed:

1. Each service has a specific, single task, and can be scaled independently, either automatically (by container orchestration systems such as Kubernetes) or manually (according to consumer groups lags, which can be viewed by any [Kafka UI](../mvp-level-implementation/scripts/deployment/docker-compose-3rd-party.yml)).
2. For example, the [Mobilization-sorter](#mobilization-sorter-service) service is responsible only to sort geospatial points to either pedestrians or mobilized points - other services are responsible to find streets/neighborhoods and to aggregate the data.
3. The services’ inner code is 100% stateless, allowing scaling to be performed on a live system, without changing any lines of code or shutting down the system.

#### [Resiliency](#resiliency-1)

**[High Availability](#high-availability-1)**: 
1. On-Premises Solutions: Redundant hardware, Clustering, Regular backups, Failover mechanisms, Monitoring and alerts.

2. AWS Solutions: Multi-AZ (Availability Zone) Deployment, Elastic Load Balancing (ELB), Auto Scaling, Amazon S3 for Backups, AWS Route 53 (DNS failover), Health Checks and Monitoring (CloudWatch).

**[Fault Tolerance](#fault-tolerance-1)**: As explained in the [Messaging](#messaging) section, Kafka adds a layer of Fault Tolerance (all messages are persisted in Kafka logs, and can be consumed and re-consumed in case of failures).
Note: **Consumer groups rebalancing** must be handled properly (refer specifically to the note in the [Activity-aggregator](#activity-aggregator-service) service).

#### [Security](#security-1)

The services [Mobile application](#mobile-application), [Receiver](#receiver-service) and [Info](#info-service) should support [OAuth2 and JWT](#oauth2-and-jwt---overview).
Users should be able to authenticate using their Gmail account, for example, i.e. the system should not introduce a self made User Management component.

#### [Maintainability](#maintainability-1)
As mentioned above, each service should hav a specific, single task. This is an important step in making the system easy to understand.
In addition, the development team should take into consideration best practices for code readability and proper documentation, preferring clear, modular and properly named software components rather than over-documenting.

**Logging**: All services should be configured in docker-level (i.e. without changing logging functionality for each service) to redirect their logging into [graylog](https://docs.docker.com/config/containers/logging/gelf/).

**System level testing**: Each service should be **runnable on its own**, with pre-prepared data, and have functionality to compare its output to the given input. For example, the [Receiver](#receiver-service) service in the [mvp-level-implementation](../mvp-level-implementation/README.md) is currently capable to execute on its own from a backup file: [receiver/start-service.cmd](../mvp-level-implementation/services/receiver/start-service.cmd) - refer to the environment variables URL_TO_EXECUTE_AFTER_STARTUP and PEOPLE_GEO_LOCATIONS_CSV.

In addition, each such script should be enhanced to compare its output to the given input, allowing developers to verify the service execution under load (e.g. COPY_FROM_BACKUP=1*1000 for 1 thread * 1,000 iterations in [receiver/start-service.cmd](../mvp-level-implementation/services/receiver/start-service.cmd) above) during CI/CD.

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

#### Diagram
![Lucid](https://lucid.app/publicSegments/view/6c202ede-1dba-4bf7-9603-1b6f48cdab2b/image.jpeg 'System diagram')

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
- This retrieval should be possible **in real time**, allowing users to specify any requested timeframe (in minutes resolution) within the last 24 hours.
- The results should be returned in descending order based on the number of pedestrians or mobilized individuals.
- Users should be able to specify the number of streets (N1) and neighborhoods (N2) they want to retrieve.

#### APIs:

[postman-collection.json](../mvp-level-implementation/services/info/postman-collection.json)

<hr>

# Appendices
The following sections explains the meaning of non-functional attributes and methodology referred and utilized in the preceding sections.

## Non-Functional Attributes - definitions

### High-Performance:

#### Performance
The software's ability to efficiently process data and return results within a specified timeframe:
- Response Time: The duration it takes for the system to respond to a user query or request after it has been submitted.
- Data Processing Efficiency: The capability of the system to handle and process large volumes of incoming data swiftly without delays.
- User Experience: Maintaining optimal performance is crucial for providing a seamless experience for users, enabling them to make timely decisions.

#### Scalability
The software should be able to handle increased demands and growth without significant performance degradation. It should be designed to scale both horizontally (adding more machines to the system) and vertically (adding more resources to a single machine).

### Resiliency:
The software should be reliable and available for use whenever required. It should be able to handle errors, exceptions, and failures gracefully, ensuring minimal disruption to the system.

#### High Availability
The software's ability to remain operational and accessible for a high percentage of time, ideally achieving near-continuous service without significant downtime.

This can be achieved through various strategies, such as load balancing, redundant systems, and automatic failover processes, ensuring that if one component fails, another can take over seamlessly. The goal is to minimize service disruptions for users and maintain consistent performance.

#### Fault Tolerance
The property of a software that allows it to continue functioning properly in the event of a failure of some of its components. This involves the capacity to detect and handle failures without losing data or functionality.

### Security
The software should have robust security measures in place to protect sensitive data, prevent unauthorized access, and mitigate any potential security vulnerabilities.

### Maintainability:
The software should be designed in a way that makes it easy to understand, modify, and maintain over time. This includes considerations for code readability, proper documentation, and adherence to coding best practices.

#### Testability:
The software should be designed in a way that facilitates easy testing, both at unit and system levels. It should have proper logging and debugging mechanisms in place to aid in identifying and resolving issues.

### Extensibility
The software should be designed in a way that facilitates adding new features without modifying the existing system.


## Technology Stack - features overview
The following tech stack was preferred, partially **due to current experience of the development team** and partially for reasons explained below:

### JAVA Spring Boot
- **Rapid Development**: Spring Boot enables developers to quickly build applications with less boilerplate code and simplified configuration, resulting in faster development cycles and increased productivity.

- Robust **Ecosystem**: Spring Boot leverages the extensive Spring ecosystem, providing a wide range of libraries and tools for various functionalities such as security, data access, and web development. This ecosystem enhances development efficiency, code reusability, and overall application quality.

- **Production-Ready** features: Spring Boot includes built-in features for monitoring, logging, metrics, health checks, and configuration management, making it easier to develop and deploy production-ready applications. These features simplify operations, ensure application reliability, and facilitate scalability.

### Kafka
- **High-throughput** and **scalable**: Kafka is designed to handle high volumes of data and can scale horizontally to accommodate growing demands.
- **Real-time** data processing: Kafka enables real-time event **streaming** and data processing, making it suitable for applications that require real-time analytics, data integration, and event-driven architectures.

### PostgreSQL
- **Reliability** and **stability**: PostgreSQL is known for its robustness, stability, and ACID compliance, making it a reliable choice for data storage.
- **Advanced features**: PostgreSQL offers a wide range of advanced features such as JSON support, **spatial data support**, and full-text search capabilities, providing flexibility for various application requirements.
- As this system was inspired by the [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop, the system uses PostgreSQL datasets provided by this workshop.

### MongoDB
- **Scalability**
    - NoSQL databases are designed for **horizontal scaling**, which means they can handle increased loads by adding more servers or nodes to the system. This allows them to distribute data and workload across multiple machines.
    - **Sharding**: NoSQL databases can implement sharding, where data is divided into smaller, more manageable pieces distributed across multiple servers. Each shard can be queried independently, allowing for parallel processing of requests, which increases throughput and reduces latency.
    - High **Write and Read Throughput**:
      NoSQL databases are optimized for high write and read throughput. They can efficiently handle a large number of concurrent operations, making them ideal for applications that generate lots of real-time data, such as the Activity-aggregator service.
      Relational databases can become performance bottlenecks under high load due to their transactional nature and the overhead of maintaining ACID properties (Atomicity, Consistency, Isolation, Durability).
- **Schema Flexibility**:
  NoSQL databases typically feature a schema-less or flexible schema design. This means you can store data without a predefined structure, allowing for rapid changes in data models.   <BR>Relational databases require a strict schema, and changing this schema often involves complex migrations that can lead to downtime and performance degradation.

### Redis
- **High performance**: Redis is an in-memory data store that delivers exceptional performance and low latency, ideal for applications that require fast data access and high-speed caching.
- Versatility: Redis supports various data structures, including strings, lists, sets, and sorted sets, enabling different use cases such as caching, session management, real-time analytics, and pub/sub messaging.

### React
- Component-based architecture: React's component-based approach allows for modular and reusable code, leading to improved development efficiency and code maintainability.
- React **Native**: With React, you can develop cross-platform **mobile** applications using React Native, leveraging code sharing and faster development cycles.


## OAuth2 and JWT - overview 

- [OAuth2](https://oauth.net/2/) is the primary authentication protocol used to grant access to user accounts without sharing the actual login credentials. It allows third-party applications to request access to user data on their behalf.
  During the OAuth2 authentication flow, the Gmail API uses JWT to generate and sign the access tokens.
- [JWT](https://jwt.io/) (JSON Web Tokens) are a compact, URL-safe means of representing claims between two parties. The access token contains information about the user and the permissions granted to the third-party application.

In summary, OAuth2 is used for the overall authentication process, while JWT is used for generating and signing the access tokens that grant access to user data.

(Implementation Instructions: Java Spring Boot has built-in support for these protocols, using the **spring-boot-starter-oauth2-client** and **spring-boot-starter-security** dependencies)


## [12-Factor App methodology](https://12factor.net)

### Codebase:
Summary: There should be a single codebase tracked in a version control system (like Git), which can be deployed to multiple environments.
Example: A web application’s code is stored in a Git repository, and the same code is deployed to development, staging, and production environments.

### Dependencies:
Summary: All dependencies must be explicitly declared and isolated. This ensures that the application runs consistently across different environments.
Example: A Python application lists its required libraries in a requirements.txt file and uses a virtual environment to manage dependencies.

### Config:
Summary: Configuration settings (e.g., database URLs, API keys) should be stored in the environment, not hard-coded in the codebase.
Example: A Node.js application retrieves the database connection string from environment variables using process.env.DB_CONNECTION.

### Backing Services:
Summary: Treat backing services (like databases, message queues) as attached resources that can be swapped out easily.
Example: A web app can use either a local SQLite database in development or a cloud-hosted PostgreSQL database in production without changing the application code.

### Build, Release, Run:
Summary: Separate the build, release, and run stages to enhance the deployment process.
Example: Use a CI/CD pipeline to build the application, package it as a container image, and then deploy it to a cloud service.

### Processes:
Summary: Applications should be executed as stateless processes. Any data that needs to persist should be stored in a backing service.
Example: A web server handles HTTP requests statelessly, retrieving session data from a Redis store rather than storing it in memory.

### Port Binding:
Summary: Applications should be self-contained and expose services via port binding, allowing them to be treated like services.
Example: A web application runs on port 3000 and can be accessed via http://localhost:3000.

### Concurrency:
Summary: Scale the application by running multiple processes or instances of the application.
Example: Using Kubernetes, multiple instances of a microservice can be deployed to handle increased load during peak traffic.

### Disposability:
Summary: Applications should start up quickly and shut down gracefully to facilitate easier scaling and maintenance.
Example: A microservice can be terminated and redeployed in under a minute without any downtime, thanks to the use of containers.

### Dev/Prod Parity:
Summary: Keep development, staging, and production environments as similar as possible to reduce discrepancies and errors.
Example: Using Docker, developers can replicate the production environment locally, ensuring that code runs consistently in all stages.

### Logs:
Summary: Treat logs as event streams that can be aggregated and analyzed, instead of stored in files.
Example: A logging service like ELK Stack (Elasticsearch, Logstash, and Kibana) captures and analyzes logs from multiple microservices in real-time.

### Admin Processes:
Summary: Run admin processes (like database migrations or backup scripts) as one-off tasks in the same environment as the application.
Example: Running a database migration command in the production environment using the same container image as the application ensures consistency.

These factors collectively help create applications that are resilient, scalable, and maintainable in cloud environments. Adopting them can significantly improve the development and operational aspects of your applications.

## "Build to scale" and "Build to fail" methodologies 
The methodologies "build to scale" and "build to fail" reflect different approaches to software development and deployment within cloud-native architectures or microservices environments. Here’s a breakdown of each:

### Build to Scale
Definition:
"Build to scale" focuses on creating applications that are designed from the ground up to handle increased loads and user demand. This involves planning for growth in terms of both performance and infrastructure.

#### Key Characteristics:

##### Scalability: 
Applications are built with scalability in mind, allowing them to handle more users or data without performance degradation.
##### Microservices Architecture: 
Often utilizes microservices to allow individual components of the application to scale independently based on demand.
##### Load Balancing:
Implements load balancing to distribute traffic evenly across multiple instances of services.
##### Auto-scaling:
Uses cloud provider features (like AWS Auto Scaling) to automatically adjust the number of service instances based on traffic.
##### Performance Testing:
Involves regular performance testing and optimization to identify bottlenecks and ensure that the application can handle growth.

### Build to Fail
Definition:
"Build to fail" embraces the idea that failures are inevitable in complex systems and focuses on building applications that are resilient and can quickly recover from failures.

#### Key Characteristics:

##### Resilience:
Applications are designed to handle failures gracefully, allowing them to continue operating even when parts of the system fail.
##### Fault Tolerance:
Implements redundancy and fallback mechanisms to ensure that service remains available.
##### Chaos Engineering:
Involves intentionally injecting failures (e.g., Chaos Monkey) to test how systems respond, allowing teams to improve system resilience.
##### Monitoring and Alerts:
Incorporates robust monitoring and alerting to quickly identify and respond to issues.
Graceful Degradation: Ensures that if certain features fail, the application can still provide basic functionality to users.

### Summary
**Build to Scale** emphasizes creating systems capable of handling growing demands effectively and efficiently, focusing on scalability and performance.

**Build to Fail** focuses on resilience and the ability to recover from failures, accepting that failures will happen and preparing the system to handle them gracefully.

Both methodologies are essential in cloud-native development, as they address different but complementary aspects of building robust applications. A well-designed cloud-native application often incorporates principles from both approaches to ensure it can scale effectively while remaining resilient to failures.
