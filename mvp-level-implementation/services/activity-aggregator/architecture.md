# <font color="green">activity-aggregator</font> microservice

Refer to the [Architecture Document](../../../architecture/architecture-document-phase-1-REST.md#activity-aggregator-service).

#### Implementation Instructions
1. Each instance of this service should aggregate and persist data for one and only **one of the following combinations**:
    1. **Pedestrians** activity in **streets**.
    2. **Pedestrians** activity in **neighborhoods**.
    3. **mobilized** activity in **streets**.
    4. **mobilized** activity in **neighborhoods**.
2. The combination should be configured by two environment variables, that together decide the input topic (one of the output topics of the [Locations-finder service](#locations-finder-service)):
    1. ACTIVITY_AGGREGATOR_MOBILITY_TYPE - **pedestrians** or **mobilized**.
    2. ACTIVITY_AGGREGATOR_LOCATION_TYPE - **street** or **neighborhood**.
3. This will allow [scaling](#scalability) specific combinations according to the load, as explained in the [Locations-finder](#locations-finder-service) service.
4. The persistence interval and number of records should be configurable by environment variables: ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC and ACTIVITY_AGGREGATOR_PERSISTENCE_MAX_RECORDS. This will provide a way to control the memory consumption and database persistence time.
5. Each service instance should further allow spawning a few consumer threads, using an environment variable: ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT.
6. The number of partitions in the input topic should be greater or equal to the number of consumer threads. The reason for that is to ensure that all consumers will actually consume and process messages.
7. **Consumer groups rebalancing must be handled** properly by this service, otherwise messages aggregated in-memory might be lost when the service will attempt to commit the uncommitted offsets associated with these messages.
