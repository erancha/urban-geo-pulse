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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    @Value("${PEOPLE_GEO_LOCATIONS_TOPIC_NAME:people_geo_locations__default}")
    private String PEOPLE_GEO_LOCATIONS_TOPIC_NAME;

    @Value("${MOBILIZATION_CLASSIFIER_CONSUMER_THREADS_COUNT:1}")
    private short MOBILIZATION_CLASSIFIER_CONSUMER_THREADS_COUNT;

    @Value("${ITERATIONS_TO_SIMULATE_FROM_BACKUP:#{1}}")
    private short ITERATIONS_TO_SIMULATE_FROM_BACKUP;

    @Value("${THROTTLE_PRODUCING_THROUGHPUT:#{10000}}") // maximum throughput - produced messages per second
    private int THROTTLE_PRODUCING_THROUGHPUT;
    public static final int THROTTLE_COUNT_CHECK = 1000; // check throttling every N messages.

    public SimulatorDataService(ReceiverDataService receiverDataService, JdbcTemplate jdbcTemplate) {
        this.receiverDataService = receiverDataService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    private void initialize() {
        try {
            logger.info(String.format("Creating output topic '%s' with %d partitions, if it does not exist yet ...", PEOPLE_GEO_LOCATIONS_TOPIC_NAME, MOBILIZATION_CLASSIFIER_CONSUMER_THREADS_COUNT));
            KafkaUtils.checkAndCreateTopic(PEOPLE_GEO_LOCATIONS_TOPIC_NAME, MOBILIZATION_CLASSIFIER_CONSUMER_THREADS_COUNT); // output topic

            if (ITERATIONS_TO_SIMULATE_FROM_BACKUP > 0) {
                logger.info(String.format("%d ITERATIONS_TO_SIMULATE_FROM_BACKUP from '%s', with THROTTLE_PRODUCING_THROUGHPUT %d", ITERATIONS_TO_SIMULATE_FROM_BACKUP, FileWriter.BACKUP_FILENAME, THROTTLE_PRODUCING_THROUGHPUT));
                final boolean isBackupFileExist =  new File(FileWriter.BACKUP_FILENAME).exists();
                if (!isBackupFileExist) logger.severe(String.format("Backup file '%s' does not exist!", FileWriter.BACKUP_FILENAME));
                else simulatePointsFromBackup(ITERATIONS_TO_SIMULATE_FROM_BACKUP);
            }
        } catch (Exception ex) {
            logException(ex, logger);
        }
    }

    /**
     * Read value and key from a backup .csv file, and produce the messages into a kafka topic.
     * The messages are produced with the current timestamp by adding the delta between the 1st message and the current timestamp to all messages.
     * @param iterationsCount - number of times to read from the backup file.
     */
    public void simulatePointsFromBackup(int iterationsCount) {
        float targetTimePerMessageMillis = (float) 1000 / THROTTLE_PRODUCING_THROUGHPUT;
        long throttleStartTimeMillis = System.currentTimeMillis();
        long counter = 0;
        long deltaFromCurrentTime = 0; // the delta between the current timestamp and the timestamp of the 1st record
        for (int i = 0; i < iterationsCount; i++) {
            try (BufferedReader reader = new BufferedReader(new FileReader(FileWriter.BACKUP_FILENAME))) {
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
                            if (deltaFromCurrentTime == 0) deltaFromCurrentTime = System.currentTimeMillis() - currEventTimeInMS;
                            else currEvent.put("eventTimeInMS", currEventTimeInMS + deltaFromCurrentTime);

                            KafkaUtils.send(PEOPLE_GEO_LOCATIONS_TOPIC_NAME, JavaSerializer.write(currEvent), key);
                            if (++counter % THROTTLE_COUNT_CHECK == 0) {
                                long remainingTimeMillis = (long)(THROTTLE_COUNT_CHECK * targetTimePerMessageMillis) - (System.currentTimeMillis() - throttleStartTimeMillis);
                                if (remainingTimeMillis > 0) {
                                    if (counter % 10000 == 0) logger.info(String.format("Throttle: %,d messages produced to topic %s, delay %d ms for the recent %,d messages.", counter, PEOPLE_GEO_LOCATIONS_TOPIC_NAME, remainingTimeMillis, THROTTLE_COUNT_CHECK));
                                    Thread.sleep(remainingTimeMillis);
                                } else if (counter % 10000 == 0) logger.info(String.format("%,d messages produced to topic %s.", counter, PEOPLE_GEO_LOCATIONS_TOPIC_NAME));
                                throttleStartTimeMillis = System.currentTimeMillis();
                            }
                        } catch (InitializationException | ExecutionException | InterruptedException | JsonException e) {
                            logException(e, logger);
                        }
                    }
                }
            } catch (IOException e) {
                if (e instanceof FileNotFoundException) logger.severe(String.format("%s not found.", FileWriter.BACKUP_FILENAME));
                else logException(e, logger);
            }
        }
    }

    /** create a writer, either to write messages into a file or to produce messages into a kafka topic.
     * @param saveToBackup - which writer to creates.
     * @return
     */
    public Writer createWriter(Boolean saveToBackup) {
        return saveToBackup
                ? new FileWriter(PEOPLE_GEO_LOCATIONS_TOPIC_NAME, logger)
                : new KafkaProducer(PEOPLE_GEO_LOCATIONS_TOPIC_NAME, logger);
    }

    /**
     * @param streetName - street name for which to generate points. if null - generate for all streets.
     * @param writer     - writes a message into a target.
     */
    public void simulatePointsForStreets(String streetName, Writer writer) {
        Instant startTime = Instant.now();
        String query = "select distinct name from nyc_streets where name " + (streetName == null ? "is not null" : "like ?");
        Connection connection = DataSourceUtils.getConnection(jdbcTemplate.getDataSource());
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            if (streetName != null) statement.setString(1, streetName);
            try (ResultSet resultSet = statement.executeQuery()) {
                int count = 0;
                while (resultSet.next()) {
                    String name = resultSet.getString("name");
                    simulatePointsForOneStreet(name, writer);
                    count++;
                }
                logger.fine(String.format("%,d streets returned by query: %s, in %d ms.", count, query, Duration.between(startTime, Instant.now()).toMillis()));
            }
        } catch (Exception e) {
            logException(e, logger);
        } finally {
            DataSourceUtils.releaseConnection(connection, jdbcTemplate.getDataSource());
        }
    }

    /**
     * simulate people locations in one street.
     * Every two sequential points will be simulated as one person, producing two events with timestamps linear to the distance, to simulate walking or being mobilized.
     *
     * @param streetName - street name.
     * @param writer     - writes a message into a target.
     */
    private void simulatePointsForOneStreet(String streetName, Writer writer) {
        // 'running' values for the 'points.forEach', changing every two points (explained in the method's comment).
        AtomicLong counter = new AtomicLong();
        AtomicReference<UUID>/*AtomicLong*/ runningUuid = new AtomicReference<>/*AtomicLong*/(); // AtomicLong - only for debugging
        AtomicLong runningEventTimeInMS = new AtomicLong();
        AtomicReference<String> runningPointAsText = new AtomicReference<>();

        WKTReader reader = new WKTReader();

        final String query = "select *,ST_AsText((ST_DumpPoints(geom)).geom) as point_geom from nyc_streets where name=?;"; // order by geom - only for debugging
        Instant startTime = Instant.now();
        List<Map<String, Object>> points = jdbcTemplate.queryForList(query, streetName);
        logger.fine(String.format("%,d points returned by query: %s for: '%s' , in %d ms.", points.size(), query, streetName, Duration.between(startTime, Instant.now()).toMillis()));
        points.forEach(point -> {
            String currPointAsText = (String) point.get("point_geom");
            String prevPointAsText = runningPointAsText.get();
            counter.incrementAndGet();

            if (prevPointAsText == null) {
                runningPointAsText.set(currPointAsText);
                runningUuid.set(UUID.randomUUID()/*counter.get()*/);
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

            // save an event:
            final String currUuid = runningUuid.get().toString()/*String.valueOf(runningUuid.get())*/;  // String.valueOf(runningUuid.get()) - only for debugging
            logger.finer(String.format("#%-3d: uuid = %s, currPointAsText = %s", counter.get(), currUuid, currPointAsText));
            Map<String, Object> geoLocationEvent = receiverDataService.prepareGeoPointEvent("NYC", currUuid, currPointAsText, runningEventTimeInMS.get());
            try {
                writer.save(JavaSerializer.write(geoLocationEvent), currUuid);
            } catch (JsonException e) {
                logException(e, logger);
            }
        });
    }
}