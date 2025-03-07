## Basic implementation

This is a functional basic JAVA spring-boot implementation of the [**UrbanGeoPulse** software architect big-data showcase](../README.md).
It serves as a starting point for building the UrbanGeoPulse application. The implementation includes the basic functionality required to identify messages from pedestrians or non-pedestrians (referred to as **mobilized** individuals) and retrieve information on streets and neighborhoods based on specified timeframes.

### Development:

#### Setup:

1. Start `Docker Desktop`.
2. Execute [scripts/development/start-3rd-party-stack.cmd](scripts/development/start-3rd-party-stack.cmd) to start 3rd-party infrastucture services (Kafka, Postgres, Redis, etc .. [scripts/development/docker-compose.yml](scripts/development/docker-compose.yml)).
3. Start PGAdmin (http://localhost:8082: Username = `user@gmail.com`, Password = `pgadminpass`, add a new server : Host name/address = `postgis-server-nyc`, Username = `user`, Password = `pass`), right click on the `nyc` database, and [download](https://s3.amazonaws.com/s3.cleverelephant.ca/postgis-workshop-2020.zip) + [`Restore`](https://postgis.net/workshops/postgis-intro/loading_data.html) the data bundle the of the [Introduction to PostGIS](https://postgis.net/workshops/postgis-intro) workshop.
4. Copy the container id of `development` \ `postgis-server-nyc-1` from `Docker Desktop` into the environment variable `PG_CONTAINER_ID` in [scripts/development/set-sql-env.cmd](scripts/development/set-sql-env.cmd).
5. Execute [scripts/development/init-sql.cmd](scripts/development/init-sql.cmd) to create additional tables for the UrbanGeoPulse application.
6. Maven build [workspace/pom.xml](workspace/pom.xml) (from an IDE or from command line : [scripts/development/build-maven.cmd](scripts/development/build-maven.cmd)).

#### Startup:

7. Execute [scripts/development/start-all.cmd](scripts/development/start-all.cmd).

#### Testing:

8. The [Receiver](services) service can be set to simulate messages on startup by setting an environment variable `PEOPLE_GEO_LOCATIONS_CSV` in [services/receiver/start-service.cmd](services/receiver/start-service.cmd).
   - Each two adjacent points from the CSV file are associated with the same person (i.e. have the same uuid), to simulate a movement of that person.
   - The receiver is set by default to simulate messages from [services/receiver/NYC_people-geo-locations--Duffield_St.csv](services/receiver/NYC_people-geo-locations--Duffield_St.csv).
   - Progress can be monitored in [Kafka-UI](http://localhost:7070/ui/clusters/kafka-broker/consumer-groups) and by querying Postgres: [scripts/development/query-sql.cmd](scripts/development/query-sql.cmd).
   - This simulation is expected to complete in few minutes.
9. In addition, a Postman collection is available to initiate further simulation: [services/receiver/simulator-postman-collection.json](services/receiver/simulator-postman-collection.json).

### Deployment:

The application is designed for deployment on AWS EC2 instances using pre-built Docker images.

#### Prerequisites

1. An AWS EC2 instance with:
   - Amazon Linux 2 or Ubuntu 20.04
   - Docker installed and running
   - Sufficient permissions to run Docker commands

2. Clone deployment scripts:
   ```bash
   # Only deployment scripts are needed
   git clone --depth 1 --filter=blob:none --sparse <repository-url>
   cd urban-geo-pulse
   git sparse-checkout set basic-implementation/scripts/deployment
   ```

#### Deployment Steps

1. Set up the EC2 instance:
   ```bash
   cd basic-implementation/scripts/deployment/aws
   # Deploy infrastructure using CloudFormation
   ./deploy-ec2-cloudformation.sh
   ```

2. Deploy services:
   ```bash
   cd ../
   # Deploy third-party services (PostgreSQL, Redis, etc.)
   ./deploy-3rdparty.sh

   # Configure pgAdmin:
   # 1. Access pgAdmin at http://<ec2-public-ip>:8082
   # 2. Login with:
   #    - Email: user@gmail.com
   #    - Password: pgadminpass
   # 3. Add a new server with:
   #    - Host: postgis-server-nyc
   #    - Port: 5432
   #    - Database: nyc
   #    - Username: user
   #    - Password: pass

   # Initialize database tables
   ./init-sql.sh

   # Deploy application services
   ./deploy-app.sh
   ```

3. Verify deployment:
   ```bash
   # Check service status
   docker service ls

   # Run a test query
   ./query-sql.sh query-agg_activity.sql
   ```

Note: Make sure to:
- Configure security groups to allow access to required ports (8082 for pgAdmin, etc.)
- Use the EC2 instance's public IP or DNS when accessing services

## Contribution suggestions:

1. **Web App** for:
   1. Showing results from the [Info](services/info/readme.md) service, potentially with web-sockets to automatically refresh the UI when the order of higher concentration streets or neighborhood changes.
   2. **Visualizing** the activity data aggregated by the [Activity-aggregator](services/activity-aggregator/readme) service on top of the NYC streets and neighborhood using **vector tiles**.
2. Adding [MQTT messaging](../architecture/architecture-document-phase-1-REST.md#messaging).
3. Preparing a **Kubernetes** configuration.
4. Hardening the implementation for production.
5. ..

**License Information**: This project is licensed under the MIT License.
