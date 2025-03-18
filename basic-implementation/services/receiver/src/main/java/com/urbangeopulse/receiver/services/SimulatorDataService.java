package com.urbangeopulse.receiver.services;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import com.urbangeopulse.utils.serialization.JavaSerializer;
import com.urbangeopulse.utils.serialization.JsonException;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static com.urbangeopulse.utils.misc.Logger.logException;

@Component
public class SimulatorDataService {

    private final static Logger logger = Logger.getLogger(SimulatorDataService.class.getName());

    private final ReceiverDataService receiverDataService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${PEOPLE_GEO_LOCATIONS_TOPIC:people_geo_locations__default,2}")
    private String PEOPLE_GEO_LOCATIONS_TOPIC;
    private KafkaUtils.TopicConfig peopleGeoLocationsTopicConfig;

    @Value("${PEOPLE_GEO_LOCATIONS_CSV:./NYC_people-geo-locations--Duffield_St.csv}")
    private String PEOPLE_GEO_LOCATIONS_CSV;

    @Value("${SIMULATOR_ITERATIONS_FROM_BACKUP:#{0}}")
    private short SIMULATOR_ITERATIONS_FROM_BACKUP;

    @Value("${SIMULATOR_THROTTLE_THROUGHPUT_PRODUCING:#{10000}}") // throughput limit for produced messages per second
    private int SIMULATOR_THROTTLE_THROUGHPUT_PRODUCING;
    public static final int THROTTLE_COUNT_CHECK = 1000; // check throttling every N messages.

    @Value("${SIMULATOR_INCREMENT_EVERY_N_ITERATIONS:#{5}}") // increment the throughput limit by SIMULATOR_INCREMENT_VALUE every SIMULATOR_INCREMENT_EVERY_N_ITERATIONS
    private int SIMULATOR_INCREMENT_EVERY_N_ITERATIONS;

    @Value("${SIMULATOR_INCREMENT_VALUE:#{200}}") // increment the throughput limit by SIMULATOR_INCREMENT_VALUE every SIMULATOR_INCREMENT_EVERY_N_ITERATIONS
    private int SIMULATOR_INCREMENT_VALUE;

    private static final String QUERY_ALL_POINTS_OF_ONE_STREET = "select ST_AsText((ST_DumpPoints(geom)).geom) as point_geom from nyc_streets where name=?;";

    public SimulatorDataService(ReceiverDataService receiverDataService, JdbcTemplate jdbcTemplate) {
        this.receiverDataService = receiverDataService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    private void initialize() {
        try {
            peopleGeoLocationsTopicConfig = KafkaUtils.TopicConfig.from(PEOPLE_GEO_LOCATIONS_TOPIC);
            logger.info(String.format("Creating (if needed) output topic '%s' with %d partitions ...", peopleGeoLocationsTopicConfig.getTopicName(), peopleGeoLocationsTopicConfig.getPartitionsCount()));
            KafkaUtils.checkAndCreateTopic(peopleGeoLocationsTopicConfig.getTopicName(), peopleGeoLocationsTopicConfig.getPartitionsCount());

            if (SIMULATOR_ITERATIONS_FROM_BACKUP > 0) simulateFromBackup(SIMULATOR_ITERATIONS_FROM_BACKUP, SIMULATOR_THROTTLE_THROUGHPUT_PRODUCING, SIMULATOR_INCREMENT_EVERY_N_ITERATIONS, SIMULATOR_INCREMENT_VALUE);
        } catch (Exception ex) {
            logException(ex, logger);
        }
    }

    /**
     * Simulates points for one or all streets and applies throttling as needed
     *
     * @param streetName - street name for which to generate points. if null - generate for all streets.
     */
    public void simulateIntoBackup(String streetName) {
        Connection connection = null;
        final Writer writer = new FileWriter(PEOPLE_GEO_LOCATIONS_CSV, logger);

        try {
            connection = DataSourceUtils.getConnection(jdbcTemplate.getDataSource());
            final long simulationStartTimeInMS = System.currentTimeMillis();

            if (streetName == null) {
                // Read streets:
                try (PreparedStatement stmt = connection.prepareStatement("select distinct name from nyc_streets where name is not null;"); ResultSet rs = stmt.executeQuery()) {
                    List<String> streets = new ArrayList<>();
                    while (rs.next()) streets.add(rs.getString("name"));
                    logger.info(String.format("%,d streets returned by query", streets.size()));

                    // Process the streets:
                    for (String currStreetName : streets)
                        simulatePointsForOneStreet(currStreetName, writer, connection, simulationStartTimeInMS);
                }
            } else {
                simulatePointsForOneStreet(streetName, writer, connection, simulationStartTimeInMS);
            }
        } catch (SQLException e) {
            logException(e, logger);
        } finally {
            if (connection != null) {
                DataSourceUtils.releaseConnection(connection, jdbcTemplate.getDataSource());
            }
        }
    }

    /**
     * Simulates people locations in one street.
     * Every two sequential points will be simulated as one person, producing two events with timestamps linear to the distance, to simulate walking or being mobilized.
     *
     * @param streetName - street name.
     * @param writer     - writes a message into a target.
     * @param connection - database connection to reuse
     * @param simulationStartTimeInMS - start time of the entire simulation
     * @throws IllegalArgumentException if streetName is null
     */
    private void simulatePointsForOneStreet(String streetName, Writer writer, Connection connection, long simulationStartTimeInMS) {
        if (streetName == null) {
            throw new IllegalArgumentException("Street name cannot be null");
        }
        
        long counter = 0;
        String personUuid = null;
        long 
            runningEventTimeInMS = System.currentTimeMillis() - simulationStartTimeInMS,
            personFirstEventTimeInMS = 0, 
            currEventTimeInMS = 0;
        String 
            personFirstPointAsText = null;
        Map<String, Object> personFirstGeoLocationEvent = null;

        WKTReader wktReader = new WKTReader();

        try {
            final PreparedStatement statement = connection.prepareStatement(QUERY_ALL_POINTS_OF_ONE_STREET);
            statement.setString(1, streetName);
            final ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                final String currQueriedPointAsText = resultSet.getString(1);
                if (counter % 2 == 0) {
                    // first point of a person:
                    personUuid = String.valueOf(UUID.randomUUID());
                    personFirstPointAsText = currQueriedPointAsText;
                    currEventTimeInMS = personFirstEventTimeInMS = runningEventTimeInMS;
                    personFirstGeoLocationEvent = receiverDataService.prepareGeoPointEvent("NYC", personUuid, currQueriedPointAsText, currEventTimeInMS);
                }
                else {
                    // second point of a person:
                    try {
                        // Calculate time between points based on distance:
                        //      1 second per meter for pedestrians (80% chance)
                        //      0.1 seconds per meter for vehicles (20% chance)
                        final double distanceBetweenPointsInMeters = wktReader.read(currQueriedPointAsText).distance(wktReader.read(personFirstPointAsText));
                        final long timeBetweenPointsInMS = (long) (distanceBetweenPointsInMeters * 1000) / (new Random().nextInt(10)/*counter.get()*/ % 10 < 8 ? 1 : 10); // counter.get() should be used only for debugging the same specific
                        if (timeBetweenPointsInMS > Duration.ofMinutes(5).toMillis()) logger.fine(String.format("%s, point #%d: Time %,d seconds between two points (%,f meters) is more than 5 minutes !", streetName, counter, timeBetweenPointsInMS, distanceBetweenPointsInMeters));
                        else {
                            currEventTimeInMS = personFirstEventTimeInMS + timeBetweenPointsInMS;
                            runningEventTimeInMS = System.currentTimeMillis() - simulationStartTimeInMS;
                            writer.save(JavaSerializer.write(personFirstGeoLocationEvent), personUuid);
                            personFirstGeoLocationEvent = null;
                            Map<String, Object> geoLocationEvent = receiverDataService.prepareGeoPointEvent("NYC", personUuid, currQueriedPointAsText, currEventTimeInMS);
                            writer.save(JavaSerializer.write(geoLocationEvent), personUuid);
                        }
                    } catch (ParseException | JsonException e) {
                        logException(e, logger);
                    }
                }

                counter++;
            }
        } catch (SQLException e) {
            logException(e, logger);
        }
    }

    /**
     * @param iterationsCount number of iterations to simulate from PEOPLE_GEO_LOCATIONS_CSV.
     * @param throttleThroughput throughput limit for produced messages per second
     * @param incrementEveryNIterations increment the throughput limit every N iterations
     * @param incrementValue amount to increment the throughput by
     */
    public void simulateFromBackup(Short iterationsCount, int throttleThroughput, int incrementEveryNIterations, int incrementValue) {
        if (iterationsCount == null) iterationsCount = SIMULATOR_ITERATIONS_FROM_BACKUP;
        logger.info(String.format("Starting %d iterations to simulate from '%s', with throttleThroughput %d + incrementEveryNIterations %d * incrementValue %d", iterationsCount, PEOPLE_GEO_LOCATIONS_CSV, throttleThroughput, incrementEveryNIterations, incrementValue));
        final boolean isBackupFileExist = new File(PEOPLE_GEO_LOCATIONS_CSV).exists();
        if (!isBackupFileExist)
            logger.severe(String.format("Backup file '%s' does not exist!", PEOPLE_GEO_LOCATIONS_CSV));
        else {
            if (peopleGeoLocationsTopicConfig == null)
                throw new IllegalStateException("Topic configuration is not initialized.");
            long throttleStartTimeMillis = System.currentTimeMillis();
            long producedMessagesCount = 0;
            for (int i = 0; i < iterationsCount; i++) {
                final int currentThrottleThroughput = throttleThroughput + (i / incrementEveryNIterations) * incrementValue;
                logger.info(String.format("Starting iteration #%d with throttle throughput %d", i + 1, currentThrottleThroughput));
                float targetTimePerMessageMillis = (float) 1000 / currentThrottleThroughput;
                final long iterationStartTime = System.currentTimeMillis();
                try (BufferedReader reader = new BufferedReader(new FileReader(PEOPLE_GEO_LOCATIONS_CSV))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\\|");
                        if (parts.length == 2) {
                            String value = parts[0];
                            String key = parts[1];
                            try {
                                final Map currEvent = JavaSerializer.read(value, HashMap.class);
                                final long currEventTimeInMS = (Integer) currEvent.get("eventTimeInMS");
                                final long currEventAlignedTimeInMS = currEventTimeInMS + iterationStartTime; // aligns event time to the simulation start time
                                currEvent.put("eventTimeInMS", currEventAlignedTimeInMS);

                                // Check if the event time is more than 5.5 minutes ahead of the current time:
                                long currentTimeInMS = System.currentTimeMillis();
                                if (currEventAlignedTimeInMS > currentTimeInMS + Duration.ofMinutes(5).plusSeconds(30).toMillis()) logger.warning(String.format("Event time %d (%s) is more than 5 minutes ahead of current time %d (%s)", currEventAlignedTimeInMS, Instant.ofEpochMilli(currEventAlignedTimeInMS), currentTimeInMS, Instant.ofEpochMilli(currentTimeInMS)));

                                KafkaUtils.send(peopleGeoLocationsTopicConfig.getTopicName(), JavaSerializer.write(currEvent), key);
                                if (++producedMessagesCount % THROTTLE_COUNT_CHECK == 0) {
                                    long remainingTimeMillis = (long) (THROTTLE_COUNT_CHECK * targetTimePerMessageMillis) - (System.currentTimeMillis() - throttleStartTimeMillis);
                                    if (remainingTimeMillis > 0) {
                                        if (producedMessagesCount % 10000 == 0)
                                            logger.info(String.format("Throttle: %,d messages produced to topic %s, delay %d ms for the recent %,d messages.", producedMessagesCount, peopleGeoLocationsTopicConfig.getTopicName(), remainingTimeMillis, THROTTLE_COUNT_CHECK));
                                        Thread.sleep(remainingTimeMillis);
                                    } else if (producedMessagesCount % 10000 == 0)
                                        logger.info(String.format("%,d messages produced to topic %s.", producedMessagesCount, peopleGeoLocationsTopicConfig.getTopicName()));
                                    throttleStartTimeMillis = System.currentTimeMillis();
                                }
                            } catch (InitializationException | ExecutionException | InterruptedException |
                                     JsonException e) {
                                logException(e, logger);
                            }
                        }
                    }
                } catch (IOException e) {
                    if (e instanceof FileNotFoundException)
                        logger.severe(String.format("%s not found.", PEOPLE_GEO_LOCATIONS_CSV));
                    else logException(e, logger);
                }
            }
        }
    }
}