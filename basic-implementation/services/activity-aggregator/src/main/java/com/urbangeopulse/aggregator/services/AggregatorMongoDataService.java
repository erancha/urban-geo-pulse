package com.urbangeopulse.aggregator.services;

import com.mongodb.bulk.BulkWriteResult;
import com.urbangeopulse.aggregator.model.MongoActivityDocument;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
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
 *    - Uses upsert for atomic operations
 *    - $inc handles both new and existing documents
 *    - No duplicate key errors (like Postgres)
 *    - Logs any unexpected errors at SEVERE level
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
        if (minuteResolutionMap.isEmpty()) {
            return;
        }

        Instant startTime = Instant.now();
        logger.log(Level.FINE, String.format("%-,4d items to persist", minuteResolutionMap.size()));
        
        // Create bulk operations object
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkMode.UNORDERED, MongoActivityDocument.class);
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
            
            // Create query to find document
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

            // Execute batch if size limit reached
            if (batchCount >= BATCH_SIZE) {
                executeBulkOperations(bulkOps);
                bulkOps = mongoTemplate.bulkOps(BulkMode.UNORDERED, MongoActivityDocument.class);
                batchCount = 0;
            }
            
            logger.finer(String.format("(%s, %s) --> value: %-5d -- Done", locationGid, eventTimestamp, value));
        }

        // Execute remaining operations
        if (batchCount > 0) {
            executeBulkOperations(bulkOps);
        }

        Duration duration = Duration.between(startTime, Instant.now());
        logger.log(Level.FINE, String.format("Persisted %,5d %11s %13ss, in %5.1f seconds",
            minuteResolutionMap.size(), mobilityType, locationType,
            (double) duration.toMillis() / 1000));
    }

    /**
     * This method exists to maintain interface compatibility with the PostgreSQL implementation.
     * 
     * Unlike PostgreSQL which needs retries due to transaction serialization exceptions,
     * MongoDB doesn't require this retry mechanism because:
     * 1. MongoDB's upsert operations are atomic by default
     * 2. The $inc operator used for count updates is atomic
     * 3. MongoDB's write concern and retry mechanisms are handled at the driver level
     */
    @Override
    public void persistWithRetries(Map<String, Integer> minuteResolutionMap, String mobilityType, String locationType) {
        try {
            persistWithoutRetries(minuteResolutionMap, mobilityType, locationType);
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("Failed to persist events: %s", e.getMessage()));
            throw e;
        }
    }

    private void executeBulkOperations(BulkOperations bulkOps) {
        try {
            BulkWriteResult result = bulkOps.execute();
            logger.log(Level.FINE, String.format("Bulk write completed. Matched: %d, Inserted: %d, Modified: %d", 
                result.getMatchedCount(), result.getInsertedCount(), result.getModifiedCount()));
        } catch (Exception e) {
            // Any errors here are unexpected since we're using upsert
            logger.log(Level.SEVERE, String.format("Unexpected error during bulk operation: %s", e.getMessage()));
            throw e;
        }
    }
}
