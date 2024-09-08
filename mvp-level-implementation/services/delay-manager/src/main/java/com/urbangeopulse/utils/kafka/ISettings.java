package com.urbangeopulse.utils.kafka;

import java.util.Map;

public interface ISettings {
    String getKafkaBroker();

    Map<String, Object> getAdminConfigs();
}
