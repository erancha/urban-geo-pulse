## Basic implementation

This is a functional basic JAVA spring-boot implementation of the [**UrbanGeoPulse** software architect big-data showcase](../README.md).
It serves as a starting point for building the UrbanGeoPulse application. The implementation includes the basic functionality required to identify messages from pedestrians or non-pedestrians (referred to as **mobilized** individuals) and retrieve information on streets and neighborhoods based on specified timeframes.

### Setup:

1. Start `Docker Desktop`.
2. Execute [scripts/development/start-3rd-party-stack.cmd](scripts/development/start-3rd-party-stack.cmd).
3. In `Docker Desktop` : Copy the container id of `development` \ `postgis-server-nyc-1` into the environment variable `PG\*CONTAINER_ID` in [scripts/development/set-sql-env.cmd](scripts/development/set-sql-env.cmd).
4. Start PGAdmin (http://localhost:8082: Username = `user@gmail.com`, Password = `pgadminpass`, add a new server : Host name/address = `postgis-server-nyc`, Username = `user`, Password = `pass`), right click on the `nyc` database, and [download](https://s3.amazonaws.com/s3.cleverelephant.ca/postgis-workshop-2020.zip) and [`Restore`](https://postgis.net/workshops/postgis-intro/loading_data.html) the data bundle the of the [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.
5. Execute [scripts/development/init-sql.cmd](scripts/development/init-sql.cmd) to create additional tables for the UrbanGeoPulse application.
6. Maven build [workspace/pom.xml](workspace/pom.xml) (from an IDE or from command line : [scripts/development/build-maven.cmd](scripts/development/build-maven.cmd)).

### Startup:

7. Execute [scripts/development/start-all.cmd](scripts/development/start-all.cmd).

### Testing:

8. The [Receiver](services) service can also be set to simulate messages from pedestrians or mobilized individuals, by setting an environment variable `PEOPLE_GEO_LOCATIONS_CSV` in [services/receiver/start-service.cmd](services/receiver/start-service.cmd).<br>
   Each two adjacent points from the CSV file are associated with the same person (i.e. have the same uuid), to simulate a movement of that person.
9. [scripts/development/start-all.cmd](scripts/development/start-all.cmd) from bullet 7 is set by default to simulate a few messages from a CSV file, and is executed automatically when the application starts, expected to complete in a minute or two.

### Deployment:

10. The folder [scripts/deployment](scripts/deployment) contains additional files for a fully containerized deployment.

## Contribution suggestions:

1. **Web App** for:
   1. Showing results from the [Info](services/info/readme.md) service, potentially with web-sockets to automatically refresh the UI when the order of higher concentration streets or neighborhood changes.
   2. **Visualizing** the activity data aggregated by the [Activity-aggregator](services/activity-aggregator/readme) service on top of the NYC streets and neighborhood using **vector tiles**.
2. Adding [MQTT messaging](../architecture/architecture-document-phase-1-REST.md#messaging).
3. Preparing a **Kubernetes** configuration.
4. Hardening the implementation for production.
5. ..

**License Information**: This project is licensed under the MIT License.
