package com.urbangeopulse.receiver.services;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import com.urbangeopulse.utils.serialization.JavaSerializer;
import com.urbangeopulse.utils.serialization.JsonException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static com.urbangeopulse.utils.misc.Logger.logException;

@Component
public class ReceiverDataService {

    private final static Logger logger = Logger.getLogger(ReceiverDataService.class.getName());

    @Value("${PEOPLE_GEO_LOCATIONS_TOPIC:people_geo_locations__default,2}")
    private String PEOPLE_GEO_LOCATIONS_TOPIC;
    private KafkaUtils.TopicConfig peopleGeoLocationsTopicConfig;

    @PostConstruct
    private void initialize() {
        try {
            peopleGeoLocationsTopicConfig = KafkaUtils.TopicConfig.from(PEOPLE_GEO_LOCATIONS_TOPIC);
            logger.info(String.format("Creating (if needed) output topic '%s' with %d partitions ...", peopleGeoLocationsTopicConfig.getTopicName(), peopleGeoLocationsTopicConfig.getPartitionsCount()));
            KafkaUtils.checkAndCreateTopic(peopleGeoLocationsTopicConfig.getTopicName(), peopleGeoLocationsTopicConfig.getPartitionsCount());
        } catch (Exception ex) {
            logException(ex, logger);
        }
    }

    /** process a geospatial point.
     * @param cityCode - city code, e.g. NYC for New York City.
     * @param uuid - Universal Unique Identifier of the device that sent the point.
     * @param point - the point to process.
     */
    public void processGeoPoint(String cityCode, String uuid, String point) {
        processGeoPoint(cityCode, uuid, point, System.currentTimeMillis());
    }
    public void processGeoPoint(String cityCode, String uuid, String point, long currentTimeMillis) {
        Map<String, Object> geoLocationEvent = prepareGeoPointEvent(cityCode, uuid, point, currentTimeMillis);
        try {
            KafkaUtils.send(peopleGeoLocationsTopicConfig.getTopicName(), JavaSerializer.write(geoLocationEvent), uuid);
        } catch (JsonException | InitializationException | ExecutionException | InterruptedException e) {
            logException(e, logger);
        }
    }

    public Map<String, Object> prepareGeoPointEvent(String cityCode, String uuid, String point, long currentTimeMillis) {
        return new HashMap<>() {
            {
                put("cityCode", cityCode);
                put("uuid", uuid);
                put("point", point);
                put("eventTimeInMS", currentTimeMillis);
            }
        };
    }
}