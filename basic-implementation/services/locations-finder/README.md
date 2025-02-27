# <font color="green">locations-finder</font> microservice

Refer to the [Architecture Document](../../../architecture/architecture-document-phase-1-REST.md#locations-finder-service).

#### Implementation Instructions

1. Each instance of this service should handle one and only **one of the following combinations**:
   1. **Pedestrians** in **streets**.
   2. **Pedestrians** in **neighborhoods**.
   3. **mobilized** in **streets**.
   4. **mobilized** in **neighborhoods**.
2. The combination should be configured by two environment variables in [start-service.cmd](./start-service.cmd):

- LOCATIONS_FINDER_INPUT_TOPIC
- LOCATIONS_FINDER_OUTPUT_TOPIC
