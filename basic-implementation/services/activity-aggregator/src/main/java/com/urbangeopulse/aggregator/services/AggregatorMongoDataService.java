package com.urbangeopulse.aggregator.services;

import com.urbangeopulse.aggregator.model.MongoActivityDocument;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MongoDB AggregatorDataService Implementation:
 * 
 * Performance Optimizations:
 * 1. Atomic Operations:
 *    - Uses $inc for atomic count updates
 *    - Uses $setOnInsert for immutable fields
 *    - Uses $set for mutable fields
 *    - Ensures consistency during concurrent updates
 * 
 * 2. Bulk Write Operations:
 *    - Groups multiple updates into batches of 1000
 *    - Uses Spring's BulkOperations for efficient batching
 *    - Unordered execution for parallel processing
 *    - Reduces network round trips and server load
 * 
* 3. Memory Management:
 *    - Fixed batch size (1000) to control memory usage
 *    - Clears batch after each execution
 *    - Allows for partial progress in case of failures
 *    - Efficient for both small and large datasets
 * 
 * 4. Error Handling:
 *    - Logs warnings for invalid location names
 *    - Tracks processing time for monitoring
 *    - Reports batch sizes and completion status
 *    - Maintains interface compatibility with PostgreSQL
 */
@Component
@Profile("mongodb")
public class AggregatorMongoDataService implements AggregatorDataService {
    private static final Logger logger = Logger.getLogger(AggregatorMongoDataService.class.getName());
    private static final int BATCH_SIZE = 1000;

    private final MongoTemplate mongoTemplate;

    public AggregatorMongoDataService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void persistWithoutRetries(Map<String, Integer> minuteResolutionMap, String mobilityType, String locationType) {
        if (!minuteResolutionMap.isEmpty()) {
            logger.log(Level.FINE, String.format("%-,4d items to persist", minuteResolutionMap.size()));
            Instant startTime = Instant.now();
            
            // Create bulk operations object
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MongoActivityDocument.class);
            int batchCount = 0;
            
            // minuteResolutionMap:
            // ====================
            for (Map.Entry<String, Integer> entry : minuteResolutionMap.entrySet()) {
                String key = entry.getKey();
                Integer value = entry.getValue();
                logger.finer(String.format("Key: '%20s', value: %-5d", key, value));
                
                final String[] keyPair = key.split("\\|");
                final String locationGid = keyPair[0];
                if (locationGid == null) {
                    logger.warning(String.format("NULL location, key: '%s', value: %d", key, value));
                    continue;
                }
                final Timestamp eventTimestamp = new Timestamp(Long.parseLong(keyPair[1]));

                // MongoDB:
                // ========

                // Create filter that will use the activity_update_idx index
                Query query = new Query(Criteria.where("locationGid").is(locationGid)
                        .and("timestampInSec").is(eventTimestamp)
                        .and("mobilityType").is(mobilityType)
                        .and("locationType").is(locationType));

                // Create update document
                Update update = new Update()
                        .setOnInsert("locationGid", locationGid)
                        .setOnInsert("timestampInSec", eventTimestamp)
                        .setOnInsert("mobilityType", mobilityType)
                        .setOnInsert("locationType", locationType)
                        .inc("count", value)
                        .set("lastUpdateTimestamp", new Timestamp(System.currentTimeMillis()));

                // Add to bulk operations
                bulkOps.upsert(query, update);
                batchCount++;
                
                // Execute in batches to optimize memory usage and allow partial progress
                if (batchCount >= BATCH_SIZE) {
                    bulkOps.execute();
                    bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MongoActivityDocument.class);
                    batchCount = 0;
                }

                logger.finer(String.format("(%s, %s) --> value: %-5d -- Done", locationGid, eventTimestamp, value));
            }
            
            // Execute any remaining operations
            if (batchCount > 0) {
                bulkOps.execute();
            }
            
            logger.log(Level.FINE, String.format("Persisted %,5d %11s %13ss, in %5.1f seconds",
                    minuteResolutionMap.size(), mobilityType, locationType,
                    (double) Duration.between(startTime, Instant.now()).toMillis() / 1000));
        }
    }

    /**
     * This method exists to maintain interface compatibility with the PostgreSQL implementation.
     * 
     * Unlike PostgreSQL which needs retries due to transaction serialization exceptions,
     * MongoDB doesn't require this retry mechanism because:
     * 1. MongoDB's upsert operations are atomic by default
     * 2. The $inc operator used for count updates is atomic
     * 3. MongoDB's write concern and retry mechanisms are handled at the driver level
     * 4. We don't use @Transactional since MongoDB handles atomicity differently
     */
    @Override
    public void persistWithRetries(Map<String, Integer> minuteResolutionMap, String mobilityType, String locationType) {
        persistWithoutRetries(minuteResolutionMap, mobilityType, locationType);
    }
}
