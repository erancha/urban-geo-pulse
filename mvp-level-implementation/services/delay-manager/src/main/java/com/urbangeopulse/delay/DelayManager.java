package com.urbangeopulse.delay;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static com.urbangeopulse.utils.misc.Logger.logException;

/**
 * Process messages containing events that should be:
 * 1. delayed for a required duration,
 * 2. produced to a target topic described in each message.
 */
@Service
public class DelayManager {
    @Value("${DELAY_MANAGER_TOPIC_NAME:delays__default}")
    private String DELAY_MANAGER_TOPIC_NAME;

    @Value("${DELAY_MANAGER_AUTO_OFFSET_RESET_CONFIG:earliest}")
    private String DELAY_MANAGER_AUTO_OFFSET_RESET_CONFIG;

    @Value("${DELAY_MANAGER_SESSION_TIMEOUT_SECONDS_CONFIG:30}")
    private short DELAY_MANAGER_SESSION_TIMEOUT_SECONDS_CONFIG;

    private static final Logger logger = Logger.getLogger(DelayManager.class.getName());
    public static final int PARTITIONS_COUNT = 1;

    @PostConstruct
    void start() {
        //Consumer for the main delay topic
        Map<String, Object> CONSUMER_CONFIGS = new HashMap<String, Object>() {{
            put(ConsumerConfig.GROUP_ID_CONFIG, "delay-manager-cg");
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, DELAY_MANAGER_AUTO_OFFSET_RESET_CONFIG);
            put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, DELAY_MANAGER_SESSION_TIMEOUT_SECONDS_CONFIG * 1000);
        }};

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
        try (KafkaConsumer<String, String> consumer = KafkaUtils.createConsumer(DELAY_MANAGER_TOPIC_NAME, CONSUMER_CONFIGS, rebalanceListener)) {
            // linked (by parentDelayProcessor) list of delay processors:
            DelayProcessor under1Min = new DelayProcessor(DELAY_MANAGER_TOPIC_NAME + "_under_1_min", 10, 0, null);
            DelayProcessor under10Min = new DelayProcessor(DELAY_MANAGER_TOPIC_NAME + "_under_10_min", 60, 60, under1Min);
            DelayProcessor under60Min = new DelayProcessor(DELAY_MANAGER_TOPIC_NAME + "_under_60_min", 600, 600, under10Min);
            logger.fine(String.format("Created processors: %s, %s, %s", under1Min, under10Min, under60Min));

            ExecutorService pool = Executors.newFixedThreadPool(3);
            pool.submit(under1Min);
            pool.submit(under10Min);
            pool.submit(under60Min);
            logger.fine(String.format("Submitting processors to the thread pool: %s", pool));

            while (true) {
                // process incoming message and delegate to one of the delay processors, starting from the 'under60Min' processor to find the processor that should handle each message.
                DelayUtils.dispatchDelayEvents(consumer, null, under60Min);
            }
        } catch (InitializationException e) {
            logException(e, logger);
        }
    }
}