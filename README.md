# <font color="LightSeaGreen">UrbanGeoPulse</font>

#### A Big Data Geospatial Application.

(This is a **showcase** of a **software architecture** definition process. The requirements are hypothetical.)

## Background:

The city of New York (NYC) requires **real-time** information on the streets and neighborhoods with the highest concentration of **pedestrians** and **non-pedestrians** (referred to as **mobilized** individuals) at **any desired timeframe**.<br>
This information **will be used to make decisions** regarding transportation budgets, timing of municipal construction work, advertising fees, and more.

## Functional Requirements:

1. **Receive** messages containing **geospatial locations**, e.g. from cell phones of **pedestrians** and **mobilized** individuals.

2. **Identify** each message's source (**pedestrian** vs **mobilized** individual) based on the speed calculated between the last two messages sent from the same device.

3. Allow users to **retrieve** streets and neighborhoods activity **in real time** for any requested timeframe within the last 24 hours.

## Solution:

1. [**Architecture Document**](architecture/architecture-document-phase-1-REST.md):
   - An architecture document has been prepared to outline the proposed system architecture. It provides an overview of the system components, their interactions, and the technologies involved. You can find the document **[here](architecture/architecture-document-phase-1-REST.md)**.
   - The architecture follows the [**12-Factor App methodology**](https://12factor.net).<br><br>
   - Architecture diagram:
     ![Lucid](https://lucid.app/publicSegments/view/6bffea51-c248-49e8-a244-a0a691a3ab9d/image.jpeg 'System diagram')<br><br>
2. [**MVP-level JAVA Spring Boot implementation**](mvp-level-implementation/README.md):
   - A Minimum Viable Product (MVP)-level implementation using JAVA and Spring Boot has been developed. It serves as a starting point for building the UrbanGeoPulse application. The implementation includes the basic functionality required to identify messages from pedestrians or mobilized individuals and retrieve information on streets and neighborhoods based on specified timeframes.<br>
   - The design and implementation follow the [**12-Factor App methodology**](https://12factor.net).
   - For details about the MVP-level implementation, please refer to this [README](mvp-level-implementation/README.md) file.

**Acknowledgments**: [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.

**Contact Information**: http://www.linkedin.com/in/eran-hachmon.
