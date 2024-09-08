package com.urbangeopulse.delay;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static com.urbangeopulse.utils.misc.Logger.logException;

public class DelayProcessor implements Runnable {
    private static final Logger logger = Logger.getLogger(DelayProcessor.class.getName());
    private final String listeningTopic;
    private final int pauseInSec;
    private final int lowerLimitInSec;
    private final DelayProcessor parentDelayProcessor;

    public DelayProcessor(String listeningTopic, int pauseInSec, int lowerLimitInSec, DelayProcessor parentDelayProcessor) {
        this.listeningTopic = listeningTopic;
        this.pauseInSec = pauseInSec;
        this.lowerLimitInSec = lowerLimitInSec;
        this.parentDelayProcessor = parentDelayProcessor;
    }

    @Override
    public void run() {
        Map<String, Object> consumerConfig = new HashMap<String, Object>() {{
            put(ConsumerConfig.GROUP_ID_CONFIG, String.format("%s-cg", listeningTopic));
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        }};
        MyRebalanceListener myRebalanceListener = new MyRebalanceListener();
        try {
            // create topics:
            final String LISTENING_TEMP_TOPIC_NAME = listeningTopic + "_temp";
            KafkaUtils.checkAndCreateTopic(listeningTopic);
            KafkaUtils.checkAndCreateTopic(LISTENING_TEMP_TOPIC_NAME);

            try (KafkaConsumer<String, String> consumer = KafkaUtils.createConsumer(listeningTopic, consumerConfig, myRebalanceListener);
                 KafkaConsumer<String, String> consumerFromTempTopic = KafkaUtils.createConsumer(LISTENING_TEMP_TOPIC_NAME, consumerConfig, myRebalanceListener)) {
                long lastTimestamp = System.currentTimeMillis();
                while (true) {
                    if (System.currentTimeMillis() - lastTimestamp > (pauseInSec * 1000L)) {
                        resumeConsumer(consumer);
                        lastTimestamp = System.currentTimeMillis();
                    }

                    DelayUtils.dispatchDelayEvents(consumer, consumerFromTempTopic, this);
                    pauseConsumer(consumer);
                }
            } catch (InitializationException e) {
                logException(e, logger);
            }
        } catch (Exception ex) {
            logException(ex, logger);
        }
    }

    private static class MyRebalanceListener implements ConsumerRebalanceListener {
        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            // This method is called when the consumer is about to lose ownership of the partitions
            // You can perform necessary operations, such as committing offsets or releasing resources

            for (TopicPartition partition : partitions) {
                // Perform necessary pre-rebalance operations for each revoked partition
                logger.warning(String.format("Revoked partition: %s", partition));
            }
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            // This method is called when the consumer is assigned ownership of new partitions
            // You can perform necessary operations, such as initializing resources or resetting offsets

            for (TopicPartition partition : partitions) {
                // Perform necessary post-rebalance operations for each newly assigned partition
                logger.fine(String.format("Assigned partition: %s", partition));
            }
        }
    }

    /**
     * Pause consumer
     * @param consumer
     */
    private void pauseConsumer(KafkaConsumer<String, String> consumer) {
        if (consumer.assignment() != null) {
            logger.fine(String.format("Pausing consumer for topic '%s'", listeningTopic));
            consumer.pause(consumer.assignment());
        }
    }

    /**
     * Resume consumer
     * @param consumer
     */
    private void resumeConsumer(KafkaConsumer<String, String> consumer) {
        if (consumer.assignment() != null) {
            consumer.resume(consumer.assignment());
            logger.fine(String.format("Resumed consumer for topic '%s'", listeningTopic));
        }
    }

    public String getListeningTopic() {
        return listeningTopic;
    }

    public int getLowerLimitInSec() { return lowerLimitInSec; }

    public DelayProcessor getParentDelayProcessor() {
        return parentDelayProcessor;
    }

    @Override
    public String toString() {
        return "DelayProcessor{" +
                "listeningTopic='" + listeningTopic + '\'' +
                ", pauseInSec=" + pauseInSec +
                ", lowerLimitInSec=" + lowerLimitInSec +
                ", parentDelayProcessor=" + parentDelayProcessor +
                '}';
    }
}
