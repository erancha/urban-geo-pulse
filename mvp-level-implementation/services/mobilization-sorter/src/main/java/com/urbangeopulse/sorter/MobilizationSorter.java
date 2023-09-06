package com.urbangeopulse.sorter;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import com.urbangeopulse.utils.serialization.JavaSerializer;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.locationtech.jts.io.WKTReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

@Service
public class MobilizationSorter {
    private static final Logger logger = Logger.getLogger(MobilizationSorter.class.getName());

    @Value("${MOBILIZATION_SORTER_CONSUMER_THREADS_COUNT:1}")
    private short MOBILIZATION_SORTER_CONSUMER_THREADS_COUNT;

    @Value("${MOBILIZATION_SORTER_AUTO_OFFSET_RESET_CONFIG:latest}")
    private String MOBILIZATION_SORTER_AUTO_OFFSET_RESET_CONFIG;

    @Value("${MOBILIZATION_SORTER_DEBUG_USE_LOCAL_CACHE:false}")
    private boolean MOBILIZATION_SORTER_DEBUG_USE_LOCAL_CACHE;

    @Value("${MOBILIZATION_SORTER_MAX_POLL_INTERVAL_MINUTES_CONFIG:5}")
    private short MOBILIZATION_SORTER_MAX_POLL_INTERVAL_MINUTES_CONFIG;

    @Value("${MOBILIZATION_SORTER_SESSION_TIMEOUT_SECONDS_CONFIG:30}")
    private short MOBILIZATION_SORTER_SESSION_TIMEOUT_SECONDS_CONFIG;

    // number of minutes after which to artificially trigger rebalancing during startup (by delaying half of the threads).
    @Value("${MOBILIZATION_SORTER_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES:0}")
    private short MOBILIZATION_SORTER_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES;

    @Value("${PEOPLE_GEO_LOCATIONS_TOPIC_NAME:people_geo_locations__default}")
    private String PEOPLE_GEO_LOCATIONS_TOPIC_NAME;

    @Value("${PEDESTRIANS_GEO_LOCATIONS_TOPIC_NAME:pedestrians_geo_locations__default}")
    private String PEDESTRIANS_GEO_LOCATIONS_TOPIC_NAME;

    @Value("${MOBILIZED_GEO_LOCATIONS_TOPIC_NAME:mobilized_geo_locations__default}")
    private String MOBILIZED_GEO_LOCATIONS_TOPIC_NAME;

    static final AtomicLong counter = new AtomicLong();

    @PostConstruct
    void startBackgroundConsumers() {
        final Map<String, Object> CONSUMER_CONFIGS =
                new HashMap<String, Object>() {{
                    put(ConsumerConfig.GROUP_ID_CONFIG, "mobilization-sorter-cg");
                    put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
                    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, MOBILIZATION_SORTER_AUTO_OFFSET_RESET_CONFIG);
                    put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MOBILIZATION_SORTER_MAX_POLL_INTERVAL_MINUTES_CONFIG * 60000);
                    put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, MOBILIZATION_SORTER_SESSION_TIMEOUT_SECONDS_CONFIG * 1000);
                }};

        Runnable peopleGeoLocationsConsumerThread = () -> {
            final Thread currentThread = Thread.currentThread();
            logger.fine(String.format("Starting '%s' of class '%s' ..", currentThread.getName(), currentThread.getStackTrace()[1].getClassName()));

            try {
                ConsumerRebalanceListener rebalanceListener = new ConsumerRebalanceListener() {
                    @Override
                    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                        if (!partitions.isEmpty()) {
                            logger.fine(String.format("Partitions revoked: %s", partitions));
                        }
                    }

                    @Override
                    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                        if (!partitions.isEmpty()) {
                            logger.fine(String.format("Partitions assigned: %s", partitions));
                        }
                    }
                };
                try (KafkaConsumer<String, String> consumer = KafkaUtils.createConsumer(PEOPLE_GEO_LOCATIONS_TOPIC_NAME, CONSUMER_CONFIGS, rebalanceListener)) {
                    WKTReader reader = new WKTReader();

                    //Note! When using a local cache, if the process crashes and restarts, messages already committed and not yet forwarded (i.e. the first message for a uuid) will be lost.
                    Cache cache = MOBILIZATION_SORTER_DEBUG_USE_LOCAL_CACHE ? new LocalCache() : new RemoteCache();

                    // consume people geospatial locations from one kafka topic and produce the 2nd point of each person to one of two topics (pedestrians or mobilized), depending on the speed between the two points.
                    //1. Each **first** location from a person (UUID) is **cached** into Redis.
                    //2. The **second** location from the same person (UUID) is **compared** with the cached location.
                    //3. The **speed** is calculated by dividing the **distance** between the two locations (using a JAVA-based spatial functionality: org.locationtech.jts.io.WKTReader) by the **time** delta between the two messages.
                    short pollIntervalInMS = 1000; // consume, with poll interval between 1 and 1000 ms - when there're no records, the interval doubles the next time (up to 1000 ms).
                    while (true) {
                        logger.finer(String.format("poll interval = %d MS", pollIntervalInMS));
                        ConsumerRecords<String, String> kafkaRecords = consumer.poll(Duration.ofMillis(pollIntervalInMS));
                        pollIntervalInMS = (short) (kafkaRecords.isEmpty() ? 1000 : Math.max(pollIntervalInMS / 2, 1));
                        for (ConsumerRecord<String, String> kafkaRecord : kafkaRecords) {
                            try {
                                final long counterTemp = counter.incrementAndGet();
                                logger.fine(String.format("#%d: Consumed by '%s', partition = %d, offset = %d, key = %s, value = %s", counterTemp, Thread.currentThread().getName(), kafkaRecord.partition(), kafkaRecord.offset(), kafkaRecord.key(), kafkaRecord.value()));
                                if (counterTemp % 100000 == 0) logger.info(String.format("%,d records consumed from topic '%s'.", counterTemp, PEOPLE_GEO_LOCATIONS_TOPIC_NAME));

                                final String currKafkaRecordValue = kafkaRecord.value();
                                final Map currEvent = JavaSerializer.read(currKafkaRecordValue, HashMap.class);
                                final String currUuid = (String) currEvent.get("uuid");
                                final String prevKafkaRecordValue = cache.get(currUuid);
                                if (prevKafkaRecordValue == null) cache.put(currUuid, currKafkaRecordValue);
                                else {
                                    Map prevEvent = JavaSerializer.read(prevKafkaRecordValue, HashMap.class);
                                    double distanceBetweenPoints = Math.abs(reader.read((String) currEvent.get("point")).distance(reader.read((String) prevEvent.get("point"))));
                                    long timeBetweenPointsInMS = Math.abs((Long) currEvent.get("eventTimeInMS") - (Long) prevEvent.get("eventTimeInMS"));
                                    logger.fine(String.format("currUuid '%s' : distance %.0f meters(?) in %3.1f sec", currUuid, distanceBetweenPoints, (double)timeBetweenPointsInMS / 1000));
                                    KafkaUtils.send(distanceBetweenPoints / ((double) timeBetweenPointsInMS / 1000) <= 10 // <= 10 meter/sec is assumed to be pedestrian
                                                    ? PEDESTRIANS_GEO_LOCATIONS_TOPIC_NAME
                                                    : MOBILIZED_GEO_LOCATIONS_TOPIC_NAME
                                            , currKafkaRecordValue, currUuid);
                                    cache.remove(currUuid);
                                }
                            } catch (Exception ex) {
                                com.urbangeopulse.utils.misc.Logger.logException(ex, logger);
                                //TODO:
                                // (1) Error handling for cache.put,get and remove.
                                // (2) Upgrade jedis version.
                            }
                            consumer.commitSync();
                        }
                    }
                }
            } catch (InitializationException ex) {
                com.urbangeopulse.utils.misc.Logger.logException(ex, logger);
            }
            logger.warning("'peopleGeoLocationsConsumerThread' completed");
        };

        try {
            // create topics:
            KafkaUtils.checkAndCreateTopic(PEOPLE_GEO_LOCATIONS_TOPIC_NAME, MOBILIZATION_SORTER_CONSUMER_THREADS_COUNT); // input topic
            KafkaUtils.checkAndCreateTopic(PEDESTRIANS_GEO_LOCATIONS_TOPIC_NAME); // output topic
            KafkaUtils.checkAndCreateTopic(MOBILIZED_GEO_LOCATIONS_TOPIC_NAME); // output topic

            logger.fine(String.format("Using %s cache", MOBILIZATION_SORTER_DEBUG_USE_LOCAL_CACHE ? "local" : "remote"));

            // start consumers:
            ExecutorService threadPool = Executors.newFixedThreadPool(MOBILIZATION_SORTER_CONSUMER_THREADS_COUNT);
            for (int i = 1; i <= MOBILIZATION_SORTER_CONSUMER_THREADS_COUNT; i++) {
                threadPool.submit(peopleGeoLocationsConsumerThread);
                if (MOBILIZATION_SORTER_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES > 0 && i == Math.round(MOBILIZATION_SORTER_CONSUMER_THREADS_COUNT/2)) Thread.sleep(MOBILIZATION_SORTER_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES * 60000); // sleep for few minutes to trigger re-balancing (and also comment *_MAX_POLL_INTERVAL_MINUTES_CONFIG and *_SESSION_TIMEOUT_SECONDS_CONFIG ..)
            }
            logger.info(String.format("Started %2d consumer threads from topic '%s' to topics '%s' and '%s'.", MOBILIZATION_SORTER_CONSUMER_THREADS_COUNT, PEOPLE_GEO_LOCATIONS_TOPIC_NAME, PEDESTRIANS_GEO_LOCATIONS_TOPIC_NAME, MOBILIZED_GEO_LOCATIONS_TOPIC_NAME));
        } catch (Exception ex) {
            com.urbangeopulse.utils.misc.Logger.logException(ex, logger);
        }
    }
}