# <font color="LightSeaGreen">UrbanGeoPulse</font>

#### A Big Data Geospatial Application.

**UrbanGeoPulse** is a Big-Data application built using a microservices architecture and containerization.
This serves as a **demonstration** of a **software architecture** definition process, with hypothetical requirements.

## Preface:

New York City (NYC) requires **real-time** data on the streets and neighborhoods with the highest number of **pedestrians** and **non-pedestrians** (collectively referred to as **mobilized** individuals) at any given moment.
This data will aid in **decision-making** for **immediate needs**, such as deploying police resources and managing traffic, as well as for **long-term** planning like transportation budgets, scheduling municipal construction, and setting advertising rates.

## Functional Requirements:

1. **Receive** messages that include **geospatial locations**, for example, from the cell phones of **pedestrians** and **mobilized** individuals.
2. **Determine** the source of each message (**pedestrian** or **mobilized** individual) by calculating the speed based on the last two messages sent from the same device.
3. Enable users to **access** activity data for streets and neighborhoods **in real-time** for any requested timeframe within the last 24 hours.

## Solution:

1. [**Architecture Document**](architecture/architecture-document-phase-1-REST.md):

   - An architecture document has been created to outline the proposed system's structure. It includes an overview of the system components, their interactions, and the technologies used. The document can be accessed **[here](architecture/architecture-document-phase-1-REST.md)**.
     ![Architecture diagram](https://lucid.app/publicSegments/view/9a85b4c8-aa03-49e9-9268-bddee87edfb8/image.jpeg)

2. [**Basic JAVA Spring Boot Implementation**](basic-implementation/README.md):
   - A Basic implementation has been developed using JAVA and Spring Boot as a foundation for the UrbanGeoPulse application. This implementation includes essential features for identifying messages from pedestrians or mobilized individuals and retrieving street and neighborhood information based on specified timeframes.<br>
   - The design and implementation align with the [12-Factor App methodology](architecture\architecture-document-phase-1-REST.md#appendix-12-factor-app-methodology).
   - For more information about the basic implementation, please refer to this [README](basic-implementation/README.md) file.

**Acknowledgments**: [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.

**Contact Information**: http://www.linkedin.com/in/eran-hachmon.
