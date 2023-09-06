## <font color="LightSeaGreen">UrbanGeoPulse</font>
#### A Big Data Geospatial Application.
( This is a hypothetical **big data** exercise for **software architects**, inspired by the [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop, which focuses on spatial database management using PostGIS )<br>

## Background:
The city of New York (NYC) requires real-time information on the streets and neighborhoods with the highest concentration of **pedestrians** and **non-pedestrians** (referred to as **mobilized** individuals) at any given time of day. 
<p>This information will be used to make decisions regarding transportation budgets, timing of municipal construction work, advertising fees, and more.</p>

## Functional Requirements:
The **UrbanGeoPulse** system should fulfill the following functional requirements:

1. Receive messages containing geospatial data, e.g. from cell phones.<br>
These messages can be sent by **pedestrians** as well as **mobilized** individuals.

2. Identify pedestrians and mobilized individuals messages based on the speed calculated between the last two messages sent from the same device.

3. Retrieve streets and neighborhoods activity information.

## Solution:
Here is the proposed solution for the given requirements:

1. [**Architecture Document**](architecture/architecture-document-phase-1-REST.md):
   - An architecture document has been prepared to outline the proposed system architecture. It provides an overview of the system components, their interactions, and the technologies involved. You can find the document **[here](architecture/architecture-document-phase-1-REST.md)**.
   - [**Architecture Diagram**](https://lucid.app/lucidchart/f63cb019-73ed-40cd-bbc8-c3dde290141b/view):
     ![Lucid](https://lucid.app/publicSegments/view/fe3f96c3-2e63-4cf1-b23a-03835ab8bf11/image.jpeg "System diagram")
     
2. MVP-level JAVA Spring Boot implementation:
   A Minimum Viable Product (MVP)-level implementation using JAVA and Spring Boot has been developed. It serves as a starting point for building the UrbanGeoPulse application. The implementation includes the basic functionality required to identify messages from pedestrians or mobilized individuals and retrieve information on streets and neighborhoods based on specified timeframes.<br>
   For details about the MVP-level implementation, please refer to this [README](mvp-level-implementation/README.md) file.

**Acknowledgments**: [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.

**Contact Information**: http://www.linkedin.com/in/eran-hachmon.

