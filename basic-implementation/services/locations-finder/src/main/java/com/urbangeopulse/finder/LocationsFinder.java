package com.urbangeopulse.finder;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.finder.services.LocationFinderDataService;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import com.urbangeopulse.utils.serialization.JavaSerializer;
import com.urbangeopulse.utils.serialization.JsonException;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import static com.urbangeopulse.utils.misc.Logger.logException;

@Service
public class LocationsFinder {
    private static final Logger logger = Logger.getLogger(LocationsFinder.class.getName());

    @Value("${LOCATIONS_FINDER_INPUT_TOPIC:pedestrians_geo_locations__default,2}")
    private String LOCATIONS_FINDER_INPUT_TOPIC;
    private KafkaUtils.TopicConfig inputTopicConfig;

    @Value("${LOCATIONS_FINDER_LOCATION_TYPE:street}")
    private String LOCATIONS_FINDER_LOCATION_TYPE;

    @Value("${LOCATIONS_FINDER_OUTPUT_TOPIC:pedestrians_streets__default,2}")
    private String LOCATIONS_FINDER_OUTPUT_TOPIC;
    private KafkaUtils.TopicConfig outputTopicConfig;

    @Value("${LOCATIONS_FINDER_CONSUMER_THREADS_COUNT:100}") // (short) Math.min(LOCATIONS_FINDER_CONSUMER_THREADS_COUNT, inputTopicConfig.getPartitionsCount());
    private short LOCATIONS_FINDER_CONSUMER_THREADS_COUNT;

    @Value("${DELAY_MANAGER_TOPIC_NAME:delays__default}")
    private String DELAY_MANAGER_TOPIC_NAME;

    @Value("${LOCATIONS_FINDER_AUTO_OFFSET_RESET_CONFIG:latest}")
    private String LOCATIONS_FINDER_AUTO_OFFSET_RESET_CONFIG;

    @Value("${LOCATIONS_FINDER_MAX_POLL_INTERVAL_MINUTES_CONFIG:5}")
    private short LOCATIONS_FINDER_MAX_POLL_INTERVAL_MINUTES_CONFIG;

    @Value("${LOCATIONS_FINDER_SESSION_TIMEOUT_SECONDS_CONFIG:30}")
    private short LOCATIONS_FINDER_SESSION_TIMEOUT_SECONDS_CONFIG;

    // number of minutes after which to artificially trigger rebalancing during startup (by delaying half of the threads).
    @Value("${LOCATIONS_FINDER_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES:0}")
    private short LOCATIONS_FINDER_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES;

    // SRID (Spatial Reference Identifiers) to use when locating geom points.
    @Value("${LOCATIONS_FINDER_INPUT_SRID:26918}")
    private int LOCATIONS_FINDER_INPUT_SRID;

    @Value("${LOCATIONS_FINDER_DELAY_FOR_MISSING_CITY_IN_SEC:60}")
    private short LOCATIONS_FINDER_DELAY_FOR_MISSING_CITY_IN_SEC;

    static final AtomicLong counter = new AtomicLong();

    private final LocationFinderDataService dataService;

    public LocationsFinder(LocationFinderDataService dataService) {
        this.dataService = dataService;
    }

    @PostConstruct
    void startBackgroundConsumers() {
        logger.info(String.format("Configuring input topic '%s' and output topic '%s' ...", LOCATIONS_FINDER_INPUT_TOPIC, LOCATIONS_FINDER_OUTPUT_TOPIC));
        inputTopicConfig = KafkaUtils.TopicConfig.from(LOCATIONS_FINDER_INPUT_TOPIC);
        outputTopicConfig = KafkaUtils.TopicConfig.from(LOCATIONS_FINDER_OUTPUT_TOPIC);

        final String FROM_MESSAGE = String.format("from topic '%s' to topic '%s' ..", inputTopicConfig.getTopicName(), outputTopicConfig.getTopicName());
        final Map<String, Object> CONSUMER_CONFIGS =
                new HashMap<String, Object>() {{
                    put(ConsumerConfig.GROUP_ID_CONFIG, String.format("locations-finder-%s-%s-cg", inputTopicConfig.getTopicName(), outputTopicConfig.getTopicName()));
                    put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
                    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, LOCATIONS_FINDER_AUTO_OFFSET_RESET_CONFIG);
                    put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, LOCATIONS_FINDER_MAX_POLL_INTERVAL_MINUTES_CONFIG * 60000);
                    put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, LOCATIONS_FINDER_SESSION_TIMEOUT_SECONDS_CONFIG * 1000);
                }};

        Runnable locationsFinderConsumerThread = () -> {
            final Thread currentThread = Thread.currentThread();
            logger.fine(String.format("Starting '%s' of class '%s' %s..", currentThread.getName(), currentThread.getStackTrace()[1].getClassName(), FROM_MESSAGE));

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
                try (KafkaConsumer<String, String> consumer = KafkaUtils.createConsumer(inputTopicConfig.getTopicName(), CONSUMER_CONFIGS, rebalanceListener)) {
                    // consume, with poll interval between 1,000 and 1 ms, depending on whether records were consumed or not.
                    short pollIntervalInMS = 1000;
                    while (true) {
                        logger.finer(String.format("poll interval = %d MS", pollIntervalInMS));
                        ConsumerRecords<String, String> kafkaRecords = consumer.poll(Duration.ofMillis(pollIntervalInMS));
                        pollIntervalInMS = (short) (kafkaRecords.isEmpty() ? 1000 : Math.max(pollIntervalInMS / 2, 1));
                        for (ConsumerRecord<String, String> kafkaRecord : kafkaRecords) {
                            try {
                                final long counterTemp = counter.incrementAndGet();
                                logger.fine(String.format("#%d: Consumed by '%s', partition = %d, offset = %d, key = %s, value = %s", counterTemp, Thread.currentThread().getName(), kafkaRecord.partition(), kafkaRecord.offset(), kafkaRecord.key(), kafkaRecord.value()));
                                if (counterTemp % 10000 == 0) logger.info(String.format("%,d records consumed %s.", counterTemp, FROM_MESSAGE));

                                final String currKafkaRecordValue = kafkaRecord.value();
                                final Map currEvent = JavaSerializer.read(currKafkaRecordValue, HashMap.class);

                                // get all locations (streets or neighborhoods) for the event's point, and produce events into OUTPUT_TOPIC_NAME for each location.
                                List<String> locationGids = dataService.findLocation((String) currEvent.get("point"), LOCATIONS_FINDER_LOCATION_TYPE, LOCATIONS_FINDER_INPUT_SRID);
                                if (!locationGids.isEmpty()) {
                                    currEvent.remove("point"); // produced events will have the location instead of the point.
                                    locationGids.forEach(locationGid -> {
                                        if (locationGid != null) { // having a null gid returned for a point is a valid scenario, e.g. for 'motorway_link' type:  select * from nyc_streets where ST_Intersects(ST_SetSrid(ST_GeomFromText('POINT(599559.4836523728 4507255.523744515)'),26918),geom);
                                            currEvent.put("location", locationGid);
                                            try {
                                                KafkaUtils.send(outputTopicConfig.getTopicName(), JavaSerializer.write(currEvent));
                                            } catch (InitializationException | ExecutionException | InterruptedException | JsonException ex) {
                                                logException(ex, logger);
                                            }
                                        }
                                    });
                                } else {
                                    // send the event to the delay-manager
                                    Map<String, Object> delayEvent = new HashMap<String, Object>() {
                                        {
                                            put("eventToDelay", JavaSerializer.write(currEvent)); // the content of the event to be delayed.
                                            put("delayStartTimestamp", System.currentTimeMillis()); // the timestamp from which the event should be delayed.
                                            put("delayInSec", LOCATIONS_FINDER_DELAY_FOR_MISSING_CITY_IN_SEC); // the time, in seconds, that the event should be delayed
                                            put("returnToTopic", inputTopicConfig.getTopicName()); // the topic name to which the event should be returned after the delay.
                                            put("partitionKey", kafkaRecord.key()); // the key to use when the delay-manager will return the message back to 'returnToTopic'.
                                        }
                                    };
                                    KafkaUtils.send(DELAY_MANAGER_TOPIC_NAME, JavaSerializer.write(delayEvent));

                                    //TODO: send a command to load the city data
                                }
                            } catch (Exception ex) {
                                logException(ex, logger);
                            }
                            consumer.commitSync();
                        }
                    }
                }
            } catch (InitializationException ex) {
                logException(ex, logger);
            }
            logger.warning("'locationsFinderConsumerThread' completed");
        };

        try {
            logger.info(String.format("Creating input topic '%s' (%d partitions), output topic '%s' (%d partitions), and delay manager topic '%s', if they do not exist yet ... LOCATIONS_FINDER_LOCATION_TYPE is %s", inputTopicConfig.getTopicName(), inputTopicConfig.getPartitionsCount(), outputTopicConfig.getTopicName(), outputTopicConfig.getPartitionsCount(), DELAY_MANAGER_TOPIC_NAME, LOCATIONS_FINDER_LOCATION_TYPE));
            KafkaUtils.checkAndCreateTopic(inputTopicConfig.getTopicName(), inputTopicConfig.getPartitionsCount());
            KafkaUtils.checkAndCreateTopic(outputTopicConfig.getTopicName(), outputTopicConfig.getPartitionsCount());
            KafkaUtils.checkAndCreateTopic(DELAY_MANAGER_TOPIC_NAME);

            // start consumers: (ensure at least one partition per consumer)
            LOCATIONS_FINDER_CONSUMER_THREADS_COUNT = (short) Math.min(LOCATIONS_FINDER_CONSUMER_THREADS_COUNT, inputTopicConfig.getPartitionsCount());
            ExecutorService threadPool = Executors.newFixedThreadPool(LOCATIONS_FINDER_CONSUMER_THREADS_COUNT);
            for (int i = 1; i <= LOCATIONS_FINDER_CONSUMER_THREADS_COUNT; i++) {
                threadPool.submit(locationsFinderConsumerThread);
                if (LOCATIONS_FINDER_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES > 0 && i == Math.round(LOCATIONS_FINDER_CONSUMER_THREADS_COUNT/2)) Thread.sleep(LOCATIONS_FINDER_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES * 60000); // sleep for few minutes to trigger re-balancing (and also comment *_MAX_POLL_INTERVAL_MINUTES_CONFIG and *_SESSION_TIMEOUT_SECONDS_CONFIG ..)
            }
            logger.info(String.format("Started %2d consumer threads from topic '%s' to topic '%s'.", LOCATIONS_FINDER_CONSUMER_THREADS_COUNT, inputTopicConfig.getTopicName(), outputTopicConfig.getTopicName()));
        } catch (Exception ex) {
            logException(ex, logger);
        }
    }
}