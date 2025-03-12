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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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

    @Value("${ITERATIONS_TO_SIMULATE_FROM_BACKUP:#{1}}")
    private short ITERATIONS_TO_SIMULATE_FROM_BACKUP;

    @Value("${RECEIVER_THROTTLE_PRODUCING_THROUGHPUT:#{10000}}") // maximum throughput - produced messages per second
    private int RECEIVER_THROTTLE_PRODUCING_THROUGHPUT;
    public static final int THROTTLE_COUNT_CHECK = 1000; // check throttling every N messages.

    public SimulatorDataService(ReceiverDataService receiverDataService, JdbcTemplate jdbcTemplate) {
        this.receiverDataService = receiverDataService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    private void initialize() {
        try {
            peopleGeoLocationsTopicConfig = KafkaUtils.TopicConfig.from(PEOPLE_GEO_LOCATIONS_TOPIC);
            logger.info(String.format("Creating output topic '%s' with %d partitions, if it does not exist yet ...", peopleGeoLocationsTopicConfig.getTopicName(), peopleGeoLocationsTopicConfig.getPartitionsCount()));
            KafkaUtils.checkAndCreateTopic(peopleGeoLocationsTopicConfig.getTopicName(), peopleGeoLocationsTopicConfig.getPartitionsCount());

            if (ITERATIONS_TO_SIMULATE_FROM_BACKUP > 0) simulateFromBackup(ITERATIONS_TO_SIMULATE_FROM_BACKUP);
        } catch (Exception ex) {
            logException(ex, logger);
        }
    }

    /**
     * @param iterationsToSimulateFromBackup number of iterations to simulate from PEOPLE_GEO_LOCATIONS_CSV.
     */
    public void simulateFromBackup(Short iterationsToSimulateFromBackup) {
        if (iterationsToSimulateFromBackup == null) iterationsToSimulateFromBackup = ITERATIONS_TO_SIMULATE_FROM_BACKUP;
        logger.info(String.format("Starting %d iterations to simulate from '%s', with RECEIVER_THROTTLE_PRODUCING_THROUGHPUT %d", iterationsToSimulateFromBackup, PEOPLE_GEO_LOCATIONS_CSV, RECEIVER_THROTTLE_PRODUCING_THROUGHPUT));
        final boolean isBackupFileExist = new File(PEOPLE_GEO_LOCATIONS_CSV).exists();
        if (!isBackupFileExist) logger.severe(String.format("Backup file '%s' does not exist!", PEOPLE_GEO_LOCATIONS_CSV));
        else simulatePointsFromBackup(iterationsToSimulateFromBackup);
    }

    /**
     * Read value and key from a backup .csv file, and produce the messages into a kafka topic.
     * The messages are produced with the current timestamp by adding the delta between the 1st message and the current timestamp to all messages.
     *
     * @param iterationsCount - number of times to read from the backup file.
     */
    public void simulatePointsFromBackup(int iterationsCount) {
        if (peopleGeoLocationsTopicConfig == null) throw new IllegalStateException("Topic configuration is not initialized.");
        long throttleStartTimeMillis = System.currentTimeMillis();
        long producedMessagesCount = 0;
        long deltaFromCurrentTime = 0; // the delta between the current timestamp and the timestamp of the 1st record
        for (int i = 0; i < iterationsCount; i++) {
            final int CURRENT_RECEIVER_THROTTLE_PRODUCING_THROUGHPUT = RECEIVER_THROTTLE_PRODUCING_THROUGHPUT + (i / 2) * 50; // increase the throughput by 50 every 2 iterations
            logger.info(String.format("Starting iteration #%d with CURRENT_RECEIVER_THROTTLE_PRODUCING_THROUGHPUT %d", i+1, CURRENT_RECEIVER_THROTTLE_PRODUCING_THROUGHPUT));
            float targetTimePerMessageMillis = (float) 1000 / CURRENT_RECEIVER_THROTTLE_PRODUCING_THROUGHPUT;
            try (BufferedReader reader = new BufferedReader(new FileReader(PEOPLE_GEO_LOCATIONS_CSV))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2) {
                        String value = parts[0];
                        String key = parts[1];
                        try {
                            final Map currEvent = JavaSerializer.read(value, HashMap.class);
                            final long currEventTimeInMS = (Long) currEvent.get("eventTimeInMS");

                            // adding the delta between the 1st message's timestamp and the current time to all messages:
                            if (deltaFromCurrentTime == 0)
                                deltaFromCurrentTime = System.currentTimeMillis() - currEventTimeInMS;
                            else currEvent.put("eventTimeInMS", currEventTimeInMS + deltaFromCurrentTime);

                            KafkaUtils.send(peopleGeoLocationsTopicConfig.getTopicName(), JavaSerializer.write(currEvent), key);
                            if (++producedMessagesCount % THROTTLE_COUNT_CHECK == 0) {
                                long remainingTimeMillis = (long) (THROTTLE_COUNT_CHECK * targetTimePerMessageMillis) - (System.currentTimeMillis() - throttleStartTimeMillis);
                                if (remainingTimeMillis > 0) {
                                    if (producedMessagesCount % 10000 == 0) logger.info(String.format("Throttle: %,d messages produced to topic %s, delay %d ms for the recent %,d messages.", producedMessagesCount, peopleGeoLocationsTopicConfig.getTopicName(), remainingTimeMillis, THROTTLE_COUNT_CHECK));
                                    Thread.sleep(remainingTimeMillis);
                                } else if (producedMessagesCount % 10000 == 0) logger.info(String.format("%,d messages produced to topic %s.", producedMessagesCount, peopleGeoLocationsTopicConfig.getTopicName()));
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

    /**
     * Creates and returns a Writer instance based on the input parameter.
     * If saveToBackup is true, a FileWriter instance is returned to write data to a CSV file.
     * If saveToBackup is false, a KafkaProducer instance is returned to send data to a Kafka topic.
     * @param saveToBackup a Boolean indicating whether the data should be saved to a backup file (true) or sent to a Kafka topic (false).
     * @return a Writer instance, either FileWriter for backup to a file or KafkaProducer for Kafka messaging.
     */
    public Writer createWriter(Boolean saveToBackup) {
        return saveToBackup
                ? new FileWriter(PEOPLE_GEO_LOCATIONS_CSV, logger)
                : new KafkaProducer(peopleGeoLocationsTopicConfig.getTopicName(), logger);
    }

    /**
     * Simulates points for one or all streets and applies throttling as needed
     *
     * @param streetName - street name for which to generate points. if null - generate for all streets.
     * @param writer     - writes a message into a target.
     */
    public void simulatePointsForStreets(String streetName, Writer writer, ThrottlingContext throttlingContext) {
        Connection connection = null;

        try {
            connection = DataSourceUtils.getConnection(jdbcTemplate.getDataSource());

            if (streetName == null) {
                // Read streets:
                try (PreparedStatement stmt = connection.prepareStatement("select distinct name from nyc_streets;");
                     ResultSet rs = stmt.executeQuery()) {
                    List<String> streets = new ArrayList<>();
                    while (rs.next()) streets.add(rs.getString("name"));
                    logger.fine(String.format("%,d streets returned by query", streets.size()));

                    // Process the streets:
                    for (String currStreetName : streets) simulatePointsForOneStreet(currStreetName, writer, connection, throttlingContext);
                }
            } else {
                simulatePointsForOneStreet(streetName, writer, connection, throttlingContext);
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
     * Class to encapsulate throttling state information
     */
    public static class ThrottlingContext {
        long messageCounter;
        long startTimeMillis;
        float targetTimePerMessageMillis;

        public ThrottlingContext(long messageCounter, long startTimeMillis, float targetTimePerMessageMillis) {
            this.messageCounter = messageCounter;
            this.startTimeMillis = startTimeMillis;
            this.targetTimePerMessageMillis = targetTimePerMessageMillis;
        }
    }

    /**
     * Apply throttling logic to control message production rate
     *
     * @param throttlingContext Current throttling state
     * @param streetName        The name of the street being processed (for logging)
     */
    private void applyThrottling(ThrottlingContext throttlingContext, String streetName) {
        throttlingContext.messageCounter++;

        if (throttlingContext.messageCounter % THROTTLE_COUNT_CHECK == 0) {
            try {
                long remainingTimeMillis = (long) (THROTTLE_COUNT_CHECK * throttlingContext.targetTimePerMessageMillis) - (System.currentTimeMillis() - throttlingContext.startTimeMillis);

                if (remainingTimeMillis > 0) {
                    if (throttlingContext.messageCounter % 10000 == 0) {
                        logger.info(String.format("Throttle: %,d messages produced for street '%s', delay %d ms for the recent %,d messages.",
                                throttlingContext.messageCounter, streetName, remainingTimeMillis, THROTTLE_COUNT_CHECK));
                    }
                    Thread.sleep(remainingTimeMillis);
                } else if (throttlingContext.messageCounter % 10000 == 0) {
                    logger.info(String.format("%,d messages produced for street '%s'.", throttlingContext.messageCounter, streetName));
                }
                throttlingContext.startTimeMillis = System.currentTimeMillis();
            } catch (InterruptedException e) {
                logException(e, logger);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * simulate people locations in one street.
     * Every two sequential points will be simulated as one person, producing two events with timestamps linear to the distance, to simulate walking or being mobilized.
     *
     * @param streetName - street name.
     * @param writer     - writes a message into a target.
     * @param connection - database connection to reuse
     * @param throttlingContext - context for throttling (null if no throttling)
     */
    private void simulatePointsForOneStreet(String streetName, Writer writer, Connection connection, ThrottlingContext throttlingContext) {
        // 'running' values for the 'points.forEach', changing every two points (explained in the method's comment).
        AtomicLong counter = new AtomicLong();
        AtomicReference<UUID>/*AtomicLong*/ runningUuid = new AtomicReference<>/*AtomicLong*/(); // AtomicLong - only for debugging
        AtomicLong runningEventTimeInMS = new AtomicLong();
        AtomicReference<String> runningPointAsText = new AtomicReference<>();

        WKTReader reader = new WKTReader();

        final String query = "select *,ST_AsText((ST_DumpPoints(geom)).geom) as point_geom from nyc_streets where name=?;"; // order by geom - only for debugging
        Instant startTime = Instant.now();

        List<Map<String, Object>> points = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, streetName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> point = new HashMap<>();
                    point.put("point_geom", rs.getString("point_geom"));
                    points.add(point);
                }
            }
        } catch (SQLException e) {
            logException(e, logger);
            return;
        }

        logger.fine(String.format("%,d points returned by query: %s for: '%s' , in %d ms.",
                points.size(), query, streetName, Duration.between(startTime, Instant.now()).toMillis()));

        // Process each point
        for (Map<String, Object> point : points) {
            String currPointAsText = (String) point.get("point_geom");
            String prevPointAsText = runningPointAsText.get();
            counter.incrementAndGet();

            if (prevPointAsText == null) {
                runningPointAsText.set(currPointAsText);
                runningUuid.set(UUID.randomUUID()/*counter.get()*/); // counter.get() - only for debugging
                runningEventTimeInMS.set(System.currentTimeMillis());
            } else {
                // 'runningUuid' is not modified here, to simulate a movement by the same person.
                try {
                    final double distanceBetweenPoints = reader.read(currPointAsText).distance(reader.read(prevPointAsText));
                    final long timeBetweenPointsInMS = (long) (distanceBetweenPoints * 1000) / (new Random().nextInt(10)/*counter.get()*/ % 10 < 8 ? 1 : 10); // counter.get() should be used only for debugging the same specific scenario.
                    runningEventTimeInMS.getAndAdd(-timeBetweenPointsInMS); // for pedestrian (80% of the cases), add 1 second/meter between every two points of the same person; for mobilized (20% of the cases) - 0.1 second/meter.
                    runningPointAsText.set(null);
                } catch (ParseException e) {
                    logException(e, logger);
                }
            }

            // write an event, optionally with throttling:
            final String currUuid = runningUuid.get().toString()/*String.valueOf(runningUuid.get())*/;  // String.valueOf(runningUuid.get()) - only for debugging
            logger.finer(String.format("#%-3d: uuid = %s, currPointAsText = %s", counter.get(), currUuid, currPointAsText));
            Map<String, Object> geoLocationEvent = receiverDataService.prepareGeoPointEvent("NYC", currUuid, currPointAsText, runningEventTimeInMS.get());
            try {
                writer.save(JavaSerializer.write(geoLocationEvent), currUuid);
                if (throttlingContext != null) applyThrottling(throttlingContext, streetName);
            } catch (JsonException e) {
                logException(e, logger);
            }
        }
    }
}