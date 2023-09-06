package com.urbangeopulse.receiver.services;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;

import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class KafkaProducer implements Writer {
    private String topicName;
    private Logger logger;

    public KafkaProducer(String topicName, Logger logger) {
        this.topicName = topicName;
        this.logger = logger;
    }

    @Override
    public void save(String value, String key) {
        try {
            KafkaUtils.send(topicName, value, key);
        } catch (InitializationException | ExecutionException | InterruptedException e) {
            com.urbangeopulse.utils.misc.Logger.logException(e, logger);
        }
    }
}
