package com.urbangeopulse.receiver.services;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import com.urbangeopulse.utils.serialization.JavaSerializer;
import com.urbangeopulse.utils.serialization.JsonException;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static com.urbangeopulse.utils.misc.Logger.logException;

@Component
public class SimulatorDataService {

    private final static Logger logger = Logger.getLogger(SimulatorDataService.class.getName());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${PEOPLE_GEO_LOCATIONS_TOPIC_NAME:people_geo_locations__default}")
    private String PEOPLE_GEO_LOCATIONS_TOPIC_NAME;

    @Value("${COPY_FROM_BACKUP:#{null}}")
    private String COPY_FROM_BACKUP;

    private Writer DEFAULT_WRITER = null;

    @Autowired
    ReceiverDataService receiverDataService;

    @PostConstruct
    private void initialize() {
        try {
            KafkaUtils.checkAndCreateTopic(PEOPLE_GEO_LOCATIONS_TOPIC_NAME);

            if (COPY_FROM_BACKUP != null) {
                logger.info(String.format("COPY_FROM_BACKUP '%s' from '%s', ", COPY_FROM_BACKUP, FileWriter.BACKUP_FILENAME));
                final boolean isBackupFileExist =  new File(FileWriter.BACKUP_FILENAME).exists();
                if (!isBackupFileExist) logger.severe(String.format("Backup file '%s' does not exist!", FileWriter.BACKUP_FILENAME));
                else {
                    DEFAULT_WRITER = createWriter(false);
                    final String[] parts = COPY_FROM_BACKUP.split("\\*");
                    final short THREADS_COUNT = Short.parseShort(parts[0]);
                    final int ITERATIONS_COUNT = Integer.parseInt(parts[1]);
                    ExecutorService threadPool = Executors.newFixedThreadPool(THREADS_COUNT);
                    for (int i = 0; i < THREADS_COUNT; i++) {
                        threadPool.submit(() -> {
                            final Thread currentThread = Thread.currentThread();
                            logger.info("Starting a thread to copy from backup ..");
                            simulatePointsFromBackup(ITERATIONS_COUNT);
                            logger.info("Thread completed.");
                        });
                    }
                    threadPool.shutdown();
                }
            }
        } catch (Exception ex) {
            logException(ex, logger);
        }
    }

    /**
     * read value and key from a backup .csv file, and produce the messages into a kafka topic.
     * the messages are produced with the current timestamp by adding the delta between the 1st message and the current timestamp to all messages.
     * @param iterationsCount - number of times to read from the backup file.
     */
    public void simulatePointsFromBackup(int iterationsCount) {
        long deltaFromCurrentTime = 0; // the delta between the current timestamp and the timestamp of the 1st record
        for (int i = 1; i <= iterationsCount; i++) {
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
                            if (deltaFromCurrentTime == 0)
                                deltaFromCurrentTime = System.currentTimeMillis() - currEventTimeInMS;
                            currEvent.put("eventTimeInMS", currEventTimeInMS + deltaFromCurrentTime);

                            KafkaUtils.send(PEOPLE_GEO_LOCATIONS_TOPIC_NAME, JavaSerializer.write(currEvent), key);
                        } catch (InitializationException | ExecutionException | InterruptedException | JsonException e) {
                            logException(e, logger);
                        }
                    }
                }
            } catch (IOException e) {
                if (e instanceof FileNotFoundException) logger.severe(String.format("%s not found.", FileWriter.BACKUP_FILENAME));
                else logException(e, logger);
            }
            if (i % (iterationsCount/2) == 0) logger.fine(String.format("%,-5d lines read from '%s'.", i, FileWriter.BACKUP_FILENAME));
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
        } catch (SQLException e) {
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
            Map<String, Object> geoLocationEvent = receiverDataService.prepareGeoPointEvent(currUuid, currPointAsText, runningEventTimeInMS.get());
            try {
                writer.save(JavaSerializer.write(geoLocationEvent), currUuid);
            } catch (JsonException e) {
                logException(e, logger);
            }
        });
    }
}