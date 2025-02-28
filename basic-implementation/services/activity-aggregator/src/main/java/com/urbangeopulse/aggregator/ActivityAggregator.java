package com.urbangeopulse.aggregator;

import com.urbangeopulse.aggregator.services.AggregatorDataService;
import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import com.urbangeopulse.utils.serialization.JavaSerializer;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import static com.urbangeopulse.utils.misc.Logger.logException;

@Service
public class ActivityAggregator {
    private static final Logger logger = Logger.getLogger(ActivityAggregator.class.getName());

    @Value("${ACTIVITY_AGGREGATOR_INPUT_TOPIC:pedestrians_streets__default,2}")
    private String ACTIVITY_AGGREGATOR_INPUT_TOPIC;
    private KafkaUtils.TopicConfig inputTopicConfig;

    // mobility type: pedestrians or mobilized (i.e. non-pedestrians).
    @Value("${ACTIVITY_AGGREGATOR_MOBILITY_TYPE:pedestrians}")
    private String ACTIVITY_AGGREGATOR_MOBILITY_TYPE;

    // location type: street or neighborhood.
    @Value("${ACTIVITY_AGGREGATOR_LOCATION_TYPE:street}")
    private String ACTIVITY_AGGREGATOR_LOCATION_TYPE;

    @Value("${ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT:100}") // = (short) Math.min(ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT, inputTopicConfig.getPartitionsCount());
    private short ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT;

    @Value("${ACTIVITY_AGGREGATOR_AUTO_OFFSET_RESET_CONFIG:latest}")
    private String ACTIVITY_AGGREGATOR_AUTO_OFFSET_RESET_CONFIG;

    @Value("${ACTIVITY_AGGREGATOR_MAX_POLL_INTERVAL_MINUTES_CONFIG:10}")
    private short ACTIVITY_AGGREGATOR_MAX_POLL_INTERVAL_MINUTES_CONFIG;

    @Value("${ACTIVITY_AGGREGATOR_SESSION_TIMEOUT_SECONDS_CONFIG:30}")
    private short ACTIVITY_AGGREGATOR_SESSION_TIMEOUT_SECONDS_CONFIG;

    // interval, in seconds, to persist aggregated data into the database.
    @Value("${ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC:60}")
    private short ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC;

    // max number of aggregated data records to persist into the database.
    @Value("${ACTIVITY_AGGREGATOR_PERSISTENCE_MAX_RECORDS:500}")
    private short ACTIVITY_AGGREGATOR_PERSISTENCE_MAX_RECORDS;

    // number of minutes after which to artificially trigger rebalancing during startup (by delaying half of the threads).
    @Value("${ACTIVITY_AGGREGATOR_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES:0}")
    private short ACTIVITY_AGGREGATOR_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES;

    private final AtomicLong counter = new AtomicLong();
    private final AtomicInteger maxRecordsToAggregate = new AtomicInteger();

    private final AggregatorDataService dataService;

    public ActivityAggregator(AggregatorDataService dataService) {
        this.dataService = dataService;
    }

    @PostConstruct
    void startBackgroundConsumers() {
        inputTopicConfig = KafkaUtils.TopicConfig.from(ACTIVITY_AGGREGATOR_INPUT_TOPIC);

		this.maxRecordsToAggregate.set(ACTIVITY_AGGREGATOR_PERSISTENCE_MAX_RECORDS);

        final Map<String, Object> CONSUMER_CONFIGS =
                new HashMap<String, Object>() {{
                    put(ConsumerConfig.GROUP_ID_CONFIG, String.format("activity-aggregator-%s-cg", inputTopicConfig.getTopicName()));
                    put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
                    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, ACTIVITY_AGGREGATOR_AUTO_OFFSET_RESET_CONFIG);
                    put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, ACTIVITY_AGGREGATOR_MAX_POLL_INTERVAL_MINUTES_CONFIG * 60000);
                    put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, ACTIVITY_AGGREGATOR_SESSION_TIMEOUT_SECONDS_CONFIG * 1000);
                }};

        Runnable aggregatorConsumerThread = () -> {
            final Thread currentThread = Thread.currentThread();
            logger.fine(String.format("Starting '%s' of class '%s' ..", currentThread.getName(), currentThread.getStackTrace()[1].getClassName()));

            final Map<String, Integer> minuteResolutionMap = new HashMap<>(); // string keys representing "<locationName>|time in minute resolution", and Integer values representing count.
            final Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();

            try {
                try (KafkaConsumer<String, String> consumer = KafkaUtils.createConsumer(CONSUMER_CONFIGS)) {
                    consumer.subscribe(Collections.singleton(inputTopicConfig.getTopicName()), new ConsumerRebalanceListener() {
                        @Override
                        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                            if (!partitions.isEmpty()) {
                                consumer.pause(partitions);
                                logger.fine(String.format("Partitions revoked: %s, offsetsToCommit is %s empty.", partitions, offsetsToCommit.isEmpty() ? "" : "not"));
                                if (!offsetsToCommit.isEmpty()) {
                                    Instant startTime = Instant.now();
                                    logger.finer(String.format("offsetsToCommit: %s, minuteResolutionMap: %s.", offsetsToCommit, minuteResolutionMap));
                                    int offsetsToCommitSize = offsetsToCommit.size();
                                    int minuteResolutionMapSize = minuteResolutionMap.size();
                                    persistUncommitted(consumer, offsetsToCommit, minuteResolutionMap);
                                    logger.warning(String.format("Persisted due to re-balancing, offsetsToCommitSize: %d, minuteResolutionMapSize: %d, in %d ms.", offsetsToCommitSize, minuteResolutionMapSize, Duration.between(startTime, Instant.now()).toMillis()));
                                }
                                consumer.resume(partitions);
                            }
                        }

                        @Override
                        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                            if (!partitions.isEmpty()) {
                                logger.fine(String.format("Partitions assigned during rebalancing: %s, offsetsToCommit: %s", partitions, offsetsToCommit));
                            }
                        }
                    });

                    // consume, with poll interval between 1,000 and 1 ms, depending on whether records were consumed or not.
                    short pollIntervalInMS = 1000;
                    ZoneId zoneId = ZoneId.systemDefault();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                    Instant lastPersistenceTime = Instant.now();
                    while (true) {
                        logger.finer(String.format("poll interval = %d MS", pollIntervalInMS));
                        ConsumerRecords<String, String> kafkaRecords = consumer.poll(Duration.ofMillis(pollIntervalInMS));
                        pollIntervalInMS = (short) (kafkaRecords.isEmpty() ? 1000 : Math.max(pollIntervalInMS / 2, 1));

                        // every ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC seconds persist minuteResolutionMap into the database and reset it:
                        if (Duration.between(lastPersistenceTime, Instant.now()).toMillis() >= ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC * 1000) {
                            logger.fine(!minuteResolutionMap.isEmpty()
                                    ? String.format("Persisting data collected since: %s .. offsetsToCommit.size() = %d, minuteResolutionMap.size() = %d", lastPersistenceTime.atZone(zoneId).format(formatter), offsetsToCommit.size(), minuteResolutionMap.size())
                                    : String.format("No data to persist since: %s .. offsetsToCommit.size() = %d, minuteResolutionMap.size() = %d", lastPersistenceTime.atZone(zoneId).format(formatter), offsetsToCommit.size(), minuteResolutionMap.size()));
                            if (!offsetsToCommit.isEmpty()) persistUncommitted(consumer, offsetsToCommit, minuteResolutionMap);
                            lastPersistenceTime = Instant.now();
                        }

                        // aggregate data:
                        for (ConsumerRecord<String, String> kafkaRecord : kafkaRecords) {
                            try {
                                final long counterTemp = counter.incrementAndGet();
                                if (counterTemp % 10000 == 0) logger.info(String.format("%,d records consumed from topic '%s'.", counterTemp, inputTopicConfig.getTopicName()));

                                logger.finer(String.format("#%d: Consumed by '%s', partition = %d, offset = %d, key = %s, value = %s", counterTemp, Thread.currentThread().getName(), kafkaRecord.partition(), kafkaRecord.offset(), kafkaRecord.key(), kafkaRecord.value()));
                                offsetsToCommit.put(
                                        new TopicPartition(kafkaRecord.topic(), kafkaRecord.partition()),
                                        new OffsetAndMetadata(kafkaRecord.offset() + 1));

                                final String currKafkaRecordValue = kafkaRecord.value();
                                final Map currEvent = JavaSerializer.read(currKafkaRecordValue, HashMap.class);

                                final long currentTimeMillis = (long) currEvent.get("eventTimeInMS");
                                final long minuteResolutionMillis = currentTimeMillis - (currentTimeMillis % (60 * 1000));
                                final String locationName = (String) currEvent.get("location");
                                if (locationName == null) logger.severe(String.format("NULL location in event: %s", currEvent));
                                else {
                                    final String key = String.format("%s|%d", locationName, minuteResolutionMillis);
                                    minuteResolutionMap.merge(key, 1, Integer::sum);
                                }
                                if (minuteResolutionMap.size() >= this.maxRecordsToAggregate.get()) persistUncommitted(consumer, offsetsToCommit, minuteResolutionMap);
                            } catch (Exception ex) {
                                logException(ex, logger);
                            }
                        }
                    }
                }
            } catch (InitializationException ex) {
                logException(ex, logger);
            }
            logger.warning("'aggregatorConsumerThread' completed");
        };

        //        Runnable dataServiceTestThread = () -> {
        //            final Map<String, Integer> minuteResolutionMap = new HashMap() {{
        //                put("Washington Park|1690833840000", 10);
        //            }};
        //            dataService.persistWithRetries(minuteResolutionMap, ACTIVITY_AGGREGATOR_MOBILITY_TYPE, "street");
        //        };

        try {
            logger.info(String.format("Creating input topic '%s' (%d partitions), if it does not exist yet ...", inputTopicConfig.getTopicName(), inputTopicConfig.getPartitionsCount()));
            KafkaUtils.checkAndCreateTopic(inputTopicConfig.getTopicName(), inputTopicConfig.getPartitionsCount());

            // start consumers: (ensure at least one partition per consumer)
            ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT = (short) Math.min(ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT, inputTopicConfig.getPartitionsCount());
            ExecutorService threadPool = Executors.newFixedThreadPool(ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT);
            for (int i = 1; i <= ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT; i++) {
                threadPool.submit(aggregatorConsumerThread/*dataServiceTestThread*/);
				if (ACTIVITY_AGGREGATOR_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES > 0 && i == Math.round(ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT/2)) Thread.sleep(ACTIVITY_AGGREGATOR_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES * 60000); // sleep for few minutes to trigger re-balancing (and also comment ACTIVITY_AGGREGATOR_MAX_POLL_INTERVAL_MINUTES_CONFIG and ACTIVITY_AGGREGATOR_SESSION_TIMEOUT_SECONDS_CONFIG, and increase ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC ..)
            }
            logger.info(String.format("Started %2d consumer threads from topic '%s', persisting every %d seconds.", ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT, inputTopicConfig.getTopicName(), ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC));
        } catch (Exception ex) {
            logException(ex, logger);
        }
    }

    /**
     * persist uncommitted aggregated data (minuteResolutionMap) into the database, and commit the offsets for all partitions from which the data was aggregated.
     * @param consumer
     * @param offsetsToCommit
     * @param minuteResolutionMap
     */
    private static final ThreadLocal<Boolean> warningIssuedPerThread = new ThreadLocal<>();
    private void persistUncommitted(KafkaConsumer<String, String> consumer, Map<TopicPartition, OffsetAndMetadata> offsetsToCommit, Map<String, Integer> minuteResolutionMap) {
        assert (!minuteResolutionMap.isEmpty()); // both offsetsToCommit and minuteResolutionMap must always be assigned and cleared together.
        logger.finer(String.format("Persisting, offsetsToCommit: %s, minuteResolutionMap: %s ..", offsetsToCommit, minuteResolutionMap));
        final int offsetsToCommitSize = offsetsToCommit.size();
        Instant startTime = Instant.now();
        try {
            dataService.persistWithRetries(minuteResolutionMap, ACTIVITY_AGGREGATOR_MOBILITY_TYPE, ACTIVITY_AGGREGATOR_LOCATION_TYPE);
            consumer.commitSync(offsetsToCommit);
            offsetsToCommit.clear();
            //TODO: Under high load dataService.persistWithRetries might've complete after a very long time and then consumer.commitSync might throw CommitFailedException.
            //      On the other hand, executing consumer.commitSync before dataService.persistWithRetries might also result in inconsistency if the consumer crashes before dataService.persistWithRetries completes ..
			//TEMP mitigation: Setting maxRecordsToAggregate to the number of aggregated records that exceeds a threshold of 80% x ACTIVITY_AGGREGATOR_MAX_POLL_INTERVAL_MINUTES_CONFIG, to reduce the probability of exceeding ACTIVITY_AGGREGATOR_MAX_POLL_INTERVAL_MINUTES_CONFIG.
            final long elapsedTimeInMS = Duration.between(startTime, Instant.now()).toMillis();
            final long maxPollIntervalInMS = ACTIVITY_AGGREGATOR_MAX_POLL_INTERVAL_MINUTES_CONFIG * 60000;
            boolean warnOnElapsedTime = elapsedTimeInMS >= maxPollIntervalInMS * 0.5;
            if (warnOnElapsedTime) {
                Boolean warningIssued = warningIssuedPerThread.get();
                if (warningIssued == null || !warningIssued) warningIssuedPerThread.set(true);
                else warnOnElapsedTime = false;
            }
            logger.log(warnOnElapsedTime ? Level.WARNING : Level.FINE,
                    String.format("Persisted in %3d sec%s, offsetsToCommit.size(): %d (from %d), minuteResolutionMap.size(): %d.",
                            elapsedTimeInMS / 1000, warnOnElapsedTime ? String.format(" (%d%% from ACTIVITY_AGGREGATOR_MAX_POLL_INTERVAL_MINUTES_CONFIG, maxRecordsToAggregate: %3d)", elapsedTimeInMS * 100 / maxPollIntervalInMS, this.maxRecordsToAggregate.get()) : "",
                            offsetsToCommit.size(), offsetsToCommitSize, minuteResolutionMap.size()));
            if (elapsedTimeInMS >= maxPollIntervalInMS * 0.75) {
                final int prevMaxRecordsToAggregateSize = this.maxRecordsToAggregate.get();
                this.maxRecordsToAggregate.set(Math.min(this.maxRecordsToAggregate.get(), minuteResolutionMap.size()));
                logger.warning(String.format("Threshold exceeded, maxRecordsToAggregate reduced from %d to %d items.",
                        prevMaxRecordsToAggregateSize, this.maxRecordsToAggregate.get()));
            }
			minuteResolutionMap.clear();
        } catch (Exception ex) {
            com.urbangeopulse.utils.misc.Logger.logException(ex, String.format("Failed in %3d sec to persist uncommitted changes: offsetsToCommit.size() = %d (from %d), minuteResolutionMap.size() = %d, from topic '%s'.",
                    Duration.between(startTime, Instant.now()).toMillis() / 1000, offsetsToCommit.size(), offsetsToCommitSize, minuteResolutionMap.size(), inputTopicConfig.getTopicName()), logger);
            if (ex.getCause() instanceof CommitFailedException) {
                offsetsToCommit.clear();
                minuteResolutionMap.clear();
            }
        }
    }
}