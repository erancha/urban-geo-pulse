package com.urbangeopulse.utils.kafka;

import org.apache.kafka.clients.admin.AdminClientConfig;

import java.util.HashMap;
import java.util.Map;

public class DefaultSettings implements ISettings {
    private String getKafkaBrokerHost() {
        final String KAFKA_BROKER_HOST_NAME = System.getenv("KAFKA_BROKER_HOST_NAME");
        return KAFKA_BROKER_HOST_NAME != null ? KAFKA_BROKER_HOST_NAME : "localhost";
    }

    private short getKafkaBrokerPort() {
        final String KAFKA_BROKER_PORT = System.getenv("KAFKA_BROKER_PORT");
        return KAFKA_BROKER_PORT != null ? Short.parseShort(KAFKA_BROKER_PORT) : 29092;
    }

    public String getKafkaBroker() {
        return String.format("%s:%d", getKafkaBrokerHost(), getKafkaBrokerPort());
    }

    @Override
    public Map<String, Object> getAdminConfigs() {
        return new HashMap(){{put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");}};
    }

}
