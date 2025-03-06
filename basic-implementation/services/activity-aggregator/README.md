# <font color="green">activity-aggregator</font> microservice

Refer to the [Architecture Document](../../../architecture/architecture-document-phase-1-REST.md#activity-aggregator-service).

#### Implementation Instructions

- Each instance of this service aggregates and persists data for one and only **one of the following combinations**:
  1.  **Pedestrians** activity in **streets**.
  2.  **Pedestrians** activity in **neighborhoods**.
  3.  **mobilized** activity in **streets**.
  4.  **mobilized** activity in **neighborhoods**.
- The combination is configurable by an environment variable ACTIVITY_AGGREGATOR_INPUT_TOPIC, in [start-service.cmd](./start-service.cmd) and [set-env.cmd](./set-env.cmd).
- This will allow [scaling](#scalability) specific combinations according to load, similar to the [Locations-finder](../locations-finder/readme.md) service.
- The persistence interval and number of records are configurable using environment variables **ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC** and **ACTIVITY_AGGREGATOR_PERSISTENCE_MAX_RECORDS**.
  - This will provide a way to control the memory consumption and database persistence time.
  - Lower values of ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC: More frequent commits, lower latency, but higher database load and transaction overhead.
  - Higher values: Better batching and throughput, but increased memory usage and potential data loss on crashes.
- Each service instance further allows spawning a few consumer threads using an environment variable **ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT**.
- **Consumer groups rebalancing** are handled properly by this service, to ensure that messages aggregated in-memory aren't lost when the service attempts to commit uncommitted offsets associated with these messages.
