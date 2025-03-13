package com.urbangeopulse.aggregator.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import static com.urbangeopulse.utils.misc.Logger.logException;

/**
 * data required by the service.
 */
@Component
public class AggregatorDataService {
    private static final Logger logger = Logger.getLogger(AggregatorDataService.class.getName());

    private final JdbcTemplate jdbcTemplate;

    public AggregatorDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * persist minuteResolutionMap
     *
     * @param minuteResolutionMap - data aggregated over a duration.
     * @param mobilityType        - pedestrians or mobilized (i.e. non-pedestrians).
     * @param locationType        - street or neighborhood.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void persist(Map<String, Integer> minuteResolutionMap, String mobilityType, String locationType) {
        if (!minuteResolutionMap.isEmpty()) {
            // persist:
            String upsertQuery = String.format("INSERT INTO agg_%ss_activity (" +
                            "%s_gid,timestamp_in_sec,%s_count,insertTimestamp,lastUpdateTimestamp) VALUES (?, ?, ?, now(), now()) " +
                            "ON CONFLICT (%s_gid,timestamp_in_sec) DO UPDATE SET %s_count = agg_%ss_activity.%s_count + ?, lastUpdateTimestamp=now()",
                            locationType, locationType, mobilityType, locationType, mobilityType, locationType, mobilityType);
            logger.log(Level.FINE, String.format("%-,4d items to persist: %s", minuteResolutionMap.size(), upsertQuery));
            Instant startTime = Instant.now();
            for (Map.Entry<String, Integer> entry : minuteResolutionMap.entrySet()) {
                String key = entry.getKey();
                Integer value = entry.getValue();
                logger.finer(String.format("Key: '%20s', value: %-5d", key, value));
                final String[] keyPair = key.split("\\|");
                final int locationGid = Integer.parseInt(keyPair[0]);
                final Timestamp eventTimestamp = new Timestamp(Long.parseLong(keyPair[1]));
                jdbcTemplate.update(upsertQuery, locationGid, eventTimestamp, value, value);
                logger.finer(String.format("(%d, %s) --> value: %-5d -- Done", locationGid, eventTimestamp, value));
            }
            logger.log(Level.FINE, String.format("Persisted %,5d %11s %13ss, in %5.1f seconds",
                        minuteResolutionMap.size(), mobilityType, locationType, (double) Duration.between(startTime, Instant.now()).toMillis() / 1000));
        }
    }

    /*
    * When calling persist directly, there're many transactions serialization exceptions.
    * When calling persistWithRetries, there're no exceptions at all (and no retries) ..
    * TODO: Research further ..
     */
    public void persistWithRetries(Map<String, Integer> minuteResolutionMap, String mobilityType, String locationType) {
        final short MAX_RETRY_TIME_IN_MS = 10000;
        boolean isOK = false;
        short retriesCount = 0;
        Instant startTime = Instant.now();
        do {
            try {
                persist(minuteResolutionMap, mobilityType, locationType);
                isOK = true;
            } catch (Exception ex) {
                retriesCount++;
                logger.warning(String.format("AggregatorDataService.persistWithRetries(..): %s", ex.getMessage()));
                try {
                    Thread.sleep((long) (Math.random() * MAX_RETRY_TIME_IN_MS / 3));
                } catch (InterruptedException e) { logException(ex, logger); }
            }
        } while (!isOK && Duration.between(startTime, Instant.now()).toMillis() < MAX_RETRY_TIME_IN_MS);
        logger.finer(String.format("Persisted in %d ms%s", Duration.between(startTime, Instant.now()).toMillis(), retriesCount > 0 ? String.format(", after %d retries", retriesCount) : ""));
    }
}