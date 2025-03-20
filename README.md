# <font color="LightSeaGreen">UrbanGeoPulse</font>

#### A Big Data Geospatial Application.

**UrbanGeoPulse** is a Big Data application built using a microservices architecture with docker containers. The application is a demonstration of a software architecture definition process, with hypothetical requirements..

## Preface

New York City (NYC) requires **real-time** data on the streets and neighborhoods with the highest number of **pedestrians** and **non-pedestrians** (collectively referred to as **mobilized** individuals) at any given moment.
This data will aid in **decision-making** for **immediate needs**, such as deploying police resources and managing traffic, as well as for **long-term** planning like transportation budgets, scheduling municipal construction, and setting advertising rates.

## Functional Requirements

1. **Receive** messages that include **geospatial locations** from cell phones of **pedestrians** and **mobilized** individuals.
2. **Determine** the source of each message (**pedestrian** or **mobilized** individual) by calculating the speed between the last two messages sent from a same device.
3. Provide **activity** data for streets and neighborhoods **in real-time** for any requested timeframe within the last 7 days.

## Solution

1. [**Architecture Document**](architecture/architecture-document-phase-1-REST.md):

   - An architecture document has been created to outline the proposed system's structure. It includes an overview of the system components, their interactions, and the technologies used. The document can be accessed **[here](architecture/architecture-document-phase-1-REST.md)**.

     <img src="https://lucid.app/publicSegments/view/e6d6bb91-8e6e-43b5-9f44-49935994172d/image.jpeg" alt="Architecture diagram" width="80%" height="80%">

2. [**Basic JAVA Spring Boot Implementation**](basic-implementation/README.md):
   - A Basic implementation has been developed using JAVA and Spring Boot as a foundation for the UrbanGeoPulse application.
   - The implementation includes essential features for classifying messages from pedestrians or mobilized individuals and retrieving streets and neighborhoods activity data for required timeframes.<br>
   - The design and implementation align with the [12-Factor App methodology](architecture/architecture-document-phase-1-REST.md#appendix-12-factor-app-methodology).
   - The implementation demonstrates using [Prometheus](https://prometheus.io/) and [Grafana](https://grafana.com/) for monitoring and reporting, e.g. for [Kafka](https://kafka.apache.org/) metrics:  
     <img src="https://erancha-misc-images.s3.eu-central-1.amazonaws.com/UGP-Grafana-sample.jpg" alt="Kafka metrics" width="80%" height="80%">
   - For more information about the basic implementation, please refer to this [README](basic-implementation/README.md) file.

**Acknowledgments**: [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.

**License**: This project is licensed under the Creative Commons Attribution-NonCommercial 4.0 International License.<BR>You can view the full license [here](https://creativecommons.org/licenses/by-nc/4.0/).

**Contact Information**: http://www.linkedin.com/in/eran-hachmon.
