package com.urbangeopulse.delay;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import com.urbangeopulse.utils.serialization.JavaSerializer;
import com.urbangeopulse.utils.serialization.JsonException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DelayUtils {
    private static final Logger logger = Logger.getLogger(DelayUtils.class.getName());

    /**
     * process incoming messages and delegate each message to one of the delay processors, starting from 'lastProcessor' to find the processor that should handle each message, based on the time difference since the event requested to start delaying.
     * @param consumer - of incoming messages
     * @param consumerFromTempTopic - consumer from the temp topic of 'lastProcessor'.
     * @param lastProcessor - the processor to start from when looking.
     */
    static AtomicLong counter = new AtomicLong();
    public static void dispatchDelayEvents(KafkaConsumer<String, String> consumer, KafkaConsumer<String, String> consumerFromTempTopic, DelayProcessor lastProcessor) {
        ConsumerRecords<String, String> kafkaRecords;
        boolean process = true;
        while (process) {
            kafkaRecords = consumer.poll(java.time.Duration.ofSeconds(10));
            Instant lastConsumedTime = Instant.now();
            // logger.fine(String.format("#%-3d: %-30s,\tdelay-processor: %20s,\tconsumer: %10s", counter.incrementAndGet(), Thread.currentThread(), lastProcessor.getListeningTopic(), consumer.hashCode()));

            Iterator<ConsumerRecord<String, String>> iterator = kafkaRecords.iterator();
            process = false;
            while (iterator.hasNext()) {
                process = true;
                ConsumerRecord<String, String> kafkaRecord = iterator.next();
                String kafkaRecordValue = kafkaRecord.value();
                try {
                    Map<String, Object> delayEvent = JavaSerializer.read(kafkaRecordValue, HashMap.class);
                    long remainingTimeToDelayInSec = ((long)delayEvent.get("delayStartTimestamp") + (int)delayEvent.get("delayInSec") * 1000) - System.currentTimeMillis();
                    if (remainingTimeToDelayInSec <= 0) {
                        // send the original event back to the topic in the delayed event.
                        logger.fine(String.format("delayEvent = %s", delayEvent));
                        KafkaUtils.send((String)delayEvent.get("returnToTopic"), (String)delayEvent.get("eventToDelay"), (String)delayEvent.get("partitionKey"));
                    } else {
                        String topicToPublish = findDelayProcessorTopicName(lastProcessor, remainingTimeToDelayInSec);
                        if (topicToPublish != null) {
                            delayEvent.put("__debug__remainingTimeToDelayInSec", remainingTimeToDelayInSec); // for debug purpose.
                            kafkaRecordValue = JavaSerializer.write(delayEvent);
                            if (topicToPublish.equals(lastProcessor.getListeningTopic())) {
                                // in case an event to delay is already in the topic in which it should be handled, it is sent to the '_temp' topic of that topic, and later (down this method) sent back to the current topic, to be processed again.
                                KafkaUtils.send(lastProcessor.getListeningTopic() + "_temp", kafkaRecordValue);
                            } else {
                                // send to the selected delay topic:
                                KafkaUtils.send(topicToPublish, kafkaRecordValue, null);
                                logger.fine(String.format("Delegate message from topic '%s' to topic '%s'.", consumer.subscription(), topicToPublish));
                            }
                        } else {
                            logger.warning("Received Null delay topic from findDelayProcessor(). This shouldn't actually happen as the last processor has lower limit of 0 and any limit below 0 will not call findDelayProcessor().");
                        }
                    }
                } catch (JsonException e) {
                    com.urbangeopulse.utils.misc.Logger.logException(e, "Failed to process message, possibly failed to deserialize the message", logger);
                } catch (Exception e) {
                    com.urbangeopulse.utils.misc.Logger.logException(e, "Failed to process message", logger);
                }

                try {
                    //Committing all topics as a single sync activity
                    consumer.commitSync();
                } catch (Exception e) {
                    com.urbangeopulse.utils.misc.Logger.logException(e, String.format("%s failed to commit offset of topic %s, time elapsed from last consumed: %d seconds.", lastProcessor, consumer.subscription(), Duration.between(lastConsumedTime, Instant.now()).toMillis() / 1000), logger);
                }
            }
        }
        if (consumerFromTempTopic != null) {
            sendBackTempEvents(consumerFromTempTopic, lastProcessor);
        }
    }

    /** find the topic name of the delay processor that should process a message based on 'remainingTimeToDelayInSec'.
     * @param processor - the delayer processor to start, moving to the parents when the current processor is not the right one.
     * @param remainingTimeToDelayInSec - between the current time and the time in which the original event (to be delayed) should've started (before being delayed).
     * @return - topic name. 
     */
    private static String findDelayProcessorTopicName(DelayProcessor processor, Long remainingTimeToDelayInSec) {
        if (processor == null) {
            return null;
        } else if (remainingTimeToDelayInSec > processor.getLowerLimitInSec() * 1000L) {
            logger.fine(String.format("findDelayProcessor returning topic '%s' for time difference %s", processor.getListeningTopic(), remainingTimeToDelayInSec));
            return processor.getListeningTopic();
        } else {
            if (processor.getParentDelayProcessor() != null) {
                return findDelayProcessorTopicName(processor.getParentDelayProcessor(), remainingTimeToDelayInSec);
            } else {
                logger.fine(String.format("findDelayProcessor returning topic '%s' for time difference %s", processor.getListeningTopic(), remainingTimeToDelayInSec));
                return processor.getListeningTopic();
            }
        }
    }

    /**
     * send unconsumed messages from the temp topic to the primary topic of 'delayProcessor'.
     * @param consumerFromTempTopic
     * @param delayProcessor
     */
    private static void sendBackTempEvents(KafkaConsumer<String, String> consumerFromTempTopic, DelayProcessor delayProcessor) {
        ConsumerRecords<String, String> kafkaRecords;
        boolean process = true;
        while (process) {
            kafkaRecords = consumerFromTempTopic.poll(java.time.Duration.ofSeconds(10));
            Iterator<ConsumerRecord<String, String>> iterator = kafkaRecords.iterator();
            process = false;
            while (iterator.hasNext()) {
                process = true;
                ConsumerRecord<String, String> record = iterator.next();
                try {
                    KafkaUtils.send(delayProcessor.getListeningTopic(), record.value());
                } catch (InterruptedException | ExecutionException | InitializationException ex) {
                    com.urbangeopulse.utils.misc.Logger.logException(ex, String.format("Processor: %s failed to process message back", delayProcessor), logger);
                }
            }
            try {
                //Committing all topics as a single sync activity
                consumerFromTempTopic.commitSync();
            } catch (Exception ex) {
                com.urbangeopulse.utils.misc.Logger.logException(ex, String.format("Processor: %s failed to commit offset of topic %s", delayProcessor, consumerFromTempTopic.subscription()), logger, Level.WARNING);
            }
        }
    }

}
