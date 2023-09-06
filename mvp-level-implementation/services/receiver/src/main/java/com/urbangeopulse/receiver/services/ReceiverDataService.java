package com.urbangeopulse.receiver.services;

import com.urbangeopulse.exceptions.InitializationException;
import com.urbangeopulse.utils.kafka.KafkaUtils;
import com.urbangeopulse.utils.serialization.JavaSerializer;
import com.urbangeopulse.utils.serialization.JsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static com.urbangeopulse.utils.misc.Logger.logException;

@Component
public class ReceiverDataService {

    private final static Logger logger = Logger.getLogger(ReceiverDataService.class.getName());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${PEOPLE_GEO_LOCATIONS_TOPIC_NAME:people_geo_locations__default}")
    private String PEOPLE_GEO_LOCATIONS_TOPIC_NAME;

    /** process a geospatial point.
     * @param uuid - Universal Unique Identifier of the device that sent the point.
     * @param point - the point to process.
     */
    public void processGeoPoint(String uuid, String point) {
        processGeoPoint(uuid, point, System.currentTimeMillis());
    }
    public void processGeoPoint(String uuid, String point, long currentTimeMillis) {
        Map<String, Object> geoLocationEvent = prepareGeoPointEvent(uuid, point, currentTimeMillis);
        try {
            KafkaUtils.send(PEOPLE_GEO_LOCATIONS_TOPIC_NAME, JavaSerializer.write(geoLocationEvent), uuid);
        } catch (JsonException | InitializationException | ExecutionException | InterruptedException e) {
            logException(e, logger);
        }
    }

    public Map<String, Object> prepareGeoPointEvent(String uuid, String point, long currentTimeMillis) {
        Map<String, Object> geoLocationEvent = new HashMap<String, Object>() {
            {
                put("uuid", uuid);
                put("point", point);
                put("eventTimeInMS", currentTimeMillis);
            }
        };
        return geoLocationEvent;
    }
}