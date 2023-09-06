# Architecture Document (phase 2 - MQTT)

<!-- toc -->

- [Background (to be researched further ..)](#background-to-be-researched-further-)
- [Required changes (to be researched further ..)](#required-changes-to-be-researched-further-)

<!-- tocstop -->

## Background (to be researched further ..)
This document enhances the [architecture document](architecture-document-phase-1-REST.md) of the **UrbanGeoPulse**'s application with MQTT messaging. 

Integrating MQTT into the architecture alongside or instead of REST can offer several advantages:

1. Lightweight and Efficient: MQTT is a lightweight messaging protocol designed for resource-constrained devices and networks. It uses a publish-subscribe pattern, where messages are delivered directly to subscribers, resulting in reduced overhead compared to REST API requests.

2. Real-time Communication: MQTT excels in real-time and event-driven scenarios, making it well-suited for applications requiring immediate data updates or notifications. With MQTT, subscribers can receive instant updates as soon as the data is published to the broker, enabling faster and more responsive communication.

3. Asynchronous Communication: MQTT supports asynchronous communication, allowing publishers and subscribers to operate independently without waiting for a response from the server. This asynchronous nature enables efficient data exchange in scenarios where real-time updates are critical and where REST's request-response model may introduce latency.

4. Scalability: MQTT is designed for highly scalable systems as it can handle a large number of clients efficiently. With MQTT's publish-subscribe model, adding new subscribers doesn't impact the publishers or the overall system performance, making it suitable for distributed architectures and IoT applications.

5. Bandwidth Efficiency: MQTT uses a compact binary protocol, which reduces the size of the data packets being exchanged. This efficiency is particularly beneficial in scenarios with limited bandwidth or high data volume, as it minimizes network traffic and reduces data transmission costs.

6. Offline Support: MQTT supports offline messaging, allowing publishers to store messages in the broker for delivery to subscribers when they come back online. This feature is valuable in scenarios where devices or clients have intermittent or unreliable network connectivity.

7. Lower Overhead: Compared to REST, MQTT has lower overhead due to its smaller packet size, reduced HTTP headers, and absence of repeated handshakes for each request-response cycle. This lower overhead can lead to improved network performance and reduced resource consumption.

It's important to note that the suitability of MQTT over REST depends on the specific requirements and characteristics of your application. While MQTT offers advantages in terms of real-time and asynchronous communication, REST may still be preferable for certain use cases that require strict request-response interactions or extensive caching capabilities. It's recommended to evaluate your requirements and consider the strengths of MQTT in your specific architecture.

## Required changes (to be researched further ..)
To incorporate MQTT into the existing architecture in addition to or instead of REST, the architecture would need to make the following changes:

1. MQTT Broker: Introduce an MQTT broker into the architecture. The MQTT broker acts as a central hub that facilitates communication between MQTT clients (publishers and subscribers). There are several MQTT broker options available, such as Mosquitto or RabbitMQ.

2. MQTT Publishers: Modify the [existing component](architecture document - phase 1 - REST.md#mobile-application) that produce data to act as MQTT publishers. Instead of or in addition to sending data via REST APIs, the mobile app will publish data to MQTT topics on the broker. You will need to update the code in these components to utilize MQTT protocol and connect to the MQTT broker.

3. MQTT Subscribers: Introduce MQTT subscribers as new components that consume data from MQTT topics. These subscribers will need to connect to the MQTT broker and subscribe to the relevant topics to receive the data. Again, you will need to update the code in these components to utilize MQTT protocol and connect to the MQTT broker.

4. Message Format: Decide on the message format to be used in MQTT. MQTT messages are typically sent as payloads in the form of byte arrays or strings. You may need to define a structured format for the messages to ensure proper interpretation by the subscribers.

5. Integration: Update the consumer components (e.g., REST API clients or databases) to handle MQTT messages. Depending on your requirements, you may need to modify or replace the existing REST API clients to consume data from MQTT topics instead. Alternatively, you can keep both REST and MQTT interfaces and have the consumer components handle data from both sources.

6. Security and Authentication: Consider the security aspects of using MQTT. Implement authentication and encryption measures to secure the MQTT communication, such as using SSL/TLS for encryption and configuring username/password or certificates for client authentication.

7. Testing and Deployment: Test the updated architecture thoroughly to ensure proper functionality, including end-to-end testing of MQTT communication. Make necessary adjustments and deploy the updated components to your production environment.

Remember, incorporating MQTT into the architecture will require updating the relevant components, configuring the MQTT broker, and ensuring compatibility across the system. It is advisable to consult MQTT documentation and seek expert advice if needed.