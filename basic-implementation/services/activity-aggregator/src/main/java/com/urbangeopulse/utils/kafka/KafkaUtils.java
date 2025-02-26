package com.urbangeopulse.utils.kafka;

import com.urbangeopulse.exceptions.InitializationException;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.urbangeopulse.utils.misc.Logger.logException;

public class KafkaUtils {
    static ISettings settings = new DefaultSettings();

    private final static Logger logger = Logger.getLogger(KafkaUtils.class.getName());

    public static void injectDependencies(ISettings newSettings) {
        if (newSettings != null) settings = newSettings;
    }

    /*--------------------------------------------------------------------------------------------------------
     * topics
     ---------------------------------------------------------------------------------------------------------*/
    public static void checkAndCreateTopic(String topicName) {
        checkAndCreateTopic(topicName, 1);
    }

    /**
     * @param topicName
     */
    public static void checkAndCreateTopic(String topicName, int partitionsCount) {
        try {
            if (topicName != null && !topicsExist(Collections.singletonList(topicName)))
                createTopic(topicName, partitionsCount);
        } catch (InitializationException | InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException || e.getCause() instanceof TimeoutException) logger.warning(e.getCause().getMessage());
            else logException(e, logger);
        }
    }

    public static boolean topicsExist(List<String> topicsList) throws InterruptedException, InitializationException {
        return topicsExist(topicsList, settings.getKafkaBroker());
    }

    private static boolean topicsExist(List<String> topicsList, String kafkaBrokers) throws InterruptedException {
        return topicsExist(topicsList, kafkaBrokers, new HashMap());
    }

    private static boolean topicsExist(List<String> topicsList, String kafkaBrokers, Map<String, Object> configs) throws InterruptedException {
        AdminClient adminClient = AdminClient.create(getAdminClientConfig(kafkaBrokers, configs));
        boolean exists = true;
        DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(topicsList);
        KafkaFuture<Map<String, TopicDescription>> result = describeTopicsResult.all();
        try {
            Map<String, TopicDescription> res = result.get();
        } catch (UnknownTopicOrPartitionException e) {
            exists = false;
        } catch (ExecutionException e) {
            if (e.getCause().getClass().equals(UnknownTopicOrPartitionException.class))
                logger.fine("DEBUG topicsExist(java.util.List<java.lang.String>, java.lang.String, java.util.Map<java.lang.String,java.lang.Object>): At least one of the topics in the list: " + topicsList + "  does not exist " + e.getMessage());
            exists = false;
        }
        adminClient.close();
        return exists;
    }

    public static void createTopic(String topicName, int numPartitions) throws InterruptedException, InitializationException, ExecutionException {
        createTopic(topicName, settings.getKafkaBroker(), numPartitions);
    }

    private static void createTopic(String topicName, String kafkaBrokers, int numPartitions) throws InterruptedException, ExecutionException {
        createTopic(topicName, kafkaBrokers, settings.getAdminConfigs(), numPartitions);
    }

    private static void createTopic(String topicName, String kafkaBrokers, Map<String, Object> configs, int numPartitions) throws ExecutionException, InterruptedException {
        AdminClient admin = AdminClient.create(getAdminClientConfig(kafkaBrokers, configs));
        NewTopic newTopic = new NewTopic(topicName, numPartitions, (short) 1)
                /*.config(TopicConfig.RETENTION_BYTES_CONFIG, String.valueOf(retentionBytes))*/;

        Instant startTime = Instant.now();
        try {
            admin.createTopics(Collections.singleton(newTopic)).all().get();
            logger.info(String.format("Topic '%s' created with %d partitions, after %d seconds.", topicName, numPartitions, Duration.between(startTime, Instant.now()).toMillis() / 1000));  
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof TopicExistsException) logger.warning(String.format("Failed to create topic '%s' due to %s, after %d seconds.", topicName, ex.getCause().getClass().getSimpleName(), Duration.between(startTime, Instant.now()).toMillis() / 1000));
            else {
                logException(ex, String.format("Exception after %d seconds", Duration.between(startTime, Instant.now()).toMillis() / 1000), logger);
                throw ex;
            }
        } catch (Exception ex) {
            logException(ex, String.format("Exception after %d seconds", Duration.between(startTime, Instant.now()).toMillis() / 1000), logger);
            throw ex;
        }
    }

    /*--------------------------------------------------------------------------------------------------------
     * admin
     ---------------------------------------------------------------------------------------------------------*/
    private static Map<String, Object> getAdminClientConfig(String kafkaBrokers, Map<String, Object> configs) {
        Map<String, Object> finalConfigs = new HashMap<>();
        finalConfigs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokers);
        if (configs != null)
            finalConfigs.putAll(configs);
        return finalConfigs;
    }

    public static void deleteConsumerGroups(String consumerGroupId) {
        deleteConsumerGroups(consumerGroupId, new HashMap<>());
    }

    public static void deleteConsumerGroups(String consumerGroupId, Map<String, Object> configs) {
        logger.fine(String.format("Deleting consumer groups: '%s' ..", consumerGroupId));
        try (AdminClient adminClient = AdminClient.create(getAdminClientConfig(settings.getKafkaBroker(), configs))) {
            // List all consumer groups
            ListConsumerGroupsResult listConsumerGroupsResult = adminClient.listConsumerGroups(new ListConsumerGroupsOptions());
            Collection<ConsumerGroupListing> consumerGroups = listConsumerGroupsResult.valid().get();

            // Find consumer groups matching the wildcard pattern
            List<String> matchingGroups = consumerGroups.stream()
                    .map(ConsumerGroupListing::groupId)
                    .filter(groupId -> groupId.matches(consumerGroupId.replace("*", ".*")))
                    .collect(Collectors.toList());

            if (matchingGroups.isEmpty()) {
                logger.warning(String.format("No consumer groups found matching the pattern: '%s'.", consumerGroupId));
            } else {
                // Delete the matching consumer groups
                DeleteConsumerGroupsOptions deleteOptions = new DeleteConsumerGroupsOptions();
                DeleteConsumerGroupsResult deleteResult = adminClient.deleteConsumerGroups(matchingGroups, deleteOptions);
                try {
                    deleteResult.all().get();
                    for (String groupId : matchingGroups) {
                        logger.fine(String.format("Consumer group deleted: '%s'.", groupId));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    for (String groupId : matchingGroups) {
                        if (e.getCause() instanceof GroupIdNotFoundException)
                            logger.warning(String.format("Consumer group '%s' doesn't exist.", groupId));
                        else if (e.getCause() instanceof GroupNotEmptyException)
                            logger.severe(String.format("Consumer group '%s' isn't empty.", groupId));
                        else logException(e, logger);
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logException(e, logger);
        }
    }

    public static void listAllTopicsAndConsumers() {
        logger.info("TODO: listAllTopicsAndConsumers()");
//        try (AdminClient adminClient = AdminClient.create(getAdminClientConfig(settings.getKafkaBroker(), new HashMap<>()))) {
//            // List all partitions and their message counts
//            ListTopicsResult topicsResult = adminClient.listTopics();
//            Map<String, TopicDescription> topicDescriptionMap = topicsResult.descriptions().get();
//            for (TopicDescription topicDescription : topicDescriptionMap.values()) {
//                for (TopicPartitionInfo partitionInfo : topicDescription.partitions()) {
//                    long partitionOffset = adminClient.listConsumerGroupOffsets("<consumer-group-id>")
//                            .partitionsToOffsetAndMetadata(Map.of(new TopicPartition(topicDescription.name(), partitionInfo.partition()), OffsetAndMetadata.EARLIEST))
//                            .get().get(new TopicPartition(topicDescription.name(), partitionInfo.partition())).offset();
//                    logger.info(String.format("Topic: %s, Partition: %d, Message Count: %d%n", topicDescription.name(), partitionInfo.partition(), partitionOffset));
//                }
//            }
//
//            // List all consumers, their member count, and lag
//            ListConsumerGroupsResult consumerGroupsResult = adminClient.listConsumerGroups();
//            Collection<ConsumerGroupListing> consumerGroupListings = consumerGroupsResult.all().get();
//            for (ConsumerGroupListing consumerGroupListing : consumerGroupListings) {
//                DescribeConsumerGroupsResult describeConsumerGroupsResult = adminClient.describeConsumerGroups(Collections.singletonList(consumerGroupListing.groupId()));
//                ConsumerGroupDescription consumerGroupDescription = describeConsumerGroupsResult.describedGroups().get(consumerGroupListing.groupId()).get();
//                System.out.println("Consumer Group: " + consumerGroupListing.groupId() + ", Member Count: " + consumerGroupDescription.members().size());
//
//                ListConsumerGroupOffsetsResult consumerGroupOffsetsResult = adminClient.listConsumerGroupOffsets(consumerGroupListing.groupId());
//                Map<TopicPartition, OffsetAndMetadata> offsetsMap = consumerGroupOffsetsResult.partitionsToOffsetAndMetadata().get();
//                for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsetsMap.entrySet()) {
//                    long latestOffset = adminClient.listOffsets(Collections.singletonMap(entry.getKey(), ListOffsetsOptions.latest()))
//                            .partitionsToOffsetAndMetadata()
//                            .get().get(entry.getKey())
//                            .offset();
//                    long lag = latestOffset - entry.getValue().offset();
//                    logger.info(String.format("Topic: %s, Partition: %d, Lag: %d", entry.getKey().topic(), entry.getKey().partition(), lag));
//                }
//            }
//        }
    }

    /*--------------------------------------------------------------------------------------------------------
     * producing
     ---------------------------------------------------------------------------------------------------------*/
    public static void send(String topicName, String value, String key) throws InitializationException, ExecutionException, InterruptedException {
        send(getProducer(), topicName, value, key);
    }

    public static void send(String topicName, String value) throws InitializationException, ExecutionException, InterruptedException {
        send(topicName, value, null);
    }

    private static void send(KafkaProducer<String, String> producer, String topicName, String value, String key) throws InitializationException, InterruptedException, ExecutionException {
        producer.send(new ProducerRecord(topicName, key, value), (arg0, e) -> {
            if (e != null) logger.fine(e.toString());
        });
    }

    /**
     * Utility method to create/return a singleton Kafka producer (note: KafkaProducer is thread safe).
     * This singleton uses double-checked locking design pattern.
     *
     *  Instant startTime = Instant.now();
     *         for (int i = 0; i < 1000; i++) {
     *             KafkaProducer<String, String> kafkaProducer = KafkaUtils.getProducer();
     *         }
     *         System.out.printf("KafkaUtils.getProducer(): %.2f sec\n", (double) Duration.between(startTime, Instant.now()).toMillis()/1000);
     *
     *         startTime = Instant.now();
     *         for (int i = 0; i < 1000; i++) {
     *             KafkaProducer<String, String> kafkaProducer = KafkaUtils.createProducer(Settings.getServicesSettings().getKafkaBroker());
     *         }
     *         System.out.printf("KafkaUtils.createProducer(): %.2f sec\n", (double) Duration.between(startTime, Instant.now()).toMillis()/1000);
     *
     *      ==>
     *
     *          KafkaUtils.getProducer():    0.00 sec
     *          KafkaUtils.createProducer(): 5.95 sec (i.e. ~60ms per call)
     */
    private static volatile KafkaProducer<String, String> producer = null;

    public static KafkaProducer<String, String> getProducer() throws InitializationException {
        if (producer == null) {
            synchronized (KafkaUtils.class) {
                if (producer == null) {
                    producer = createProducer();
                }
            }
        }
        return producer;
    }

    public static KafkaProducer<String, String> createProducer() {
        return createProducer(new HashMap<>());
    }

    private static KafkaProducer<String, String> createProducer(Map<String, Object> configs) {
        return getProducer(getProducerConfig(configs));
    }

    private static KafkaProducer<String, String> getProducer(Map<String, Object> configs) {
        return new KafkaProducer(configs);
    }

    private static Map<String, Object> getProducerConfig() {
        return getProducerConfig(new HashMap());
    }

    private static Map<String, Object> getProducerConfig(Map<String, Object> configs) {
        Map<String, Object> finalConfigs = new HashMap<>();
        finalConfigs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.getKafkaBroker());
        finalConfigs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        finalConfigs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        finalConfigs.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        if (configs != null)
            finalConfigs.putAll(configs);
        return finalConfigs;
    }

    /*--------------------------------------------------------------------------------------------------------
     * consuming
     ---------------------------------------------------------------------------------------------------------*/
    public static KafkaConsumer<String, String> createConsumer(String topic, Map<String, Object> configs) throws InitializationException {
        return createConsumer(topic, configs, null);
    }

    public static KafkaConsumer<String, String> createConsumer(String topic, Map<String, Object> configs, ConsumerRebalanceListener rebalanceListener) throws InitializationException {
        KafkaConsumer<String, String> consumer = createConsumer(configs);

        //subscribe the topic
        List<String> topics = new ArrayList<>();
        topics.add(topic);
        if (rebalanceListener != null) consumer.subscribe(topics, rebalanceListener);
        else consumer.subscribe(topics);

        return consumer;
    }

    public static KafkaConsumer<String, String> createConsumer(Map<String, Object> configs) throws InitializationException {
        return (KafkaConsumer<String, String>) new KafkaConsumer(getConsumerConfig(configs));
    }

    private static Map<String, Object> getConsumerConfig(Map<String, Object> configs) {
        Map<String, Object> finalConfigs = new HashMap<>();
        finalConfigs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.getKafkaBroker());
        finalConfigs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        finalConfigs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        finalConfigs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        if (configs != null)
            finalConfigs.putAll(configs);
        return finalConfigs;
    }
}
