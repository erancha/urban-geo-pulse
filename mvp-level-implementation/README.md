## MVP-level implementation
This is a functional (MVP-level) JAVA spring-boot implementation of the [**UrbanGeoPulse** software architect big-data showcase](../README.md).
It serves as a starting point for building the UrbanGeoPulse application. The implementation includes the basic functionality required to identify messages from pedestrians or non-pedestrians (referred to as **mobilized** individuals) and retrieve information on streets and neighborhoods based on specified timeframes.

### Getting Started:
1. Start Docker Desktop.
2. Start PGAdmin (user@gmail.com/pgadminpass, postgis-server-nyc/user/pass) and [load](https://postgis.net/workshops/postgis-intro/loading_data.html) the [data bundle](https://s3.amazonaws.com/s3.cleverelephant.ca/postgis-workshop-2020.zip) of the [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.
3. In [scripts/development/set-sql-env.cmd](scripts/development/set-sql-env.cmd), set PG_CONTAINER_ID to the current id of the docker container *development-postgis-server-nyc-1*.
4. Create additional tables for the UrbanGeoPulse application: [scripts/development/init-sql.cmd](scripts/development/init-sql.cmd)
5. Maven build [workspace/pom.xml](workspace/pom.xml) (either from an IDE or from command line),
6. Execute [scripts/development/start-all.cmd](scripts/development/start-all.cmd).

The folder [scripts/deployment](scripts/deployment) contains necessary files for a fully containerized deployment.

### Data simulation:
The [Receiver](services/receiver/architecture.md) service can also be instructed to simulate a message for each point in one or more streets of the [data bundle](https://s3.amazonaws.com/s3.cleverelephant.ca/postgis-workshop-2020.zip) of the [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.<br>
Each two adjacent points are automatically associated with the same person (i.e. have the same uuid), to simulate a movement.

### Contribution suggestions: 
1. **Web App** for:
   1. Showing results from the [Info](services/info/architecture.md) service, potentially with web-sockets to automatically refresh the UI when the order of higher concentration streets or neighborhood changes.
   2. **Visualizing** the activity data aggregated by the [Activity-aggregator](services/activity-aggregator/architecture.md) service on top of the NYC streets and neighborhood using **vector tiles**.
2. Adding [MQTT messaging](../architecture/architecture-document-phase-1-REST.md#messaging).
3. Preparing a **Kubernetes** configuration.
3. Hardening the implementation for production.
4. ..

**License Information**: This project is licensed under the MIT License.
