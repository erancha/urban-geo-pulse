package com.urbangeopulse.info.services;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;
import java.util.Date;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@Profile("mongodb")
@Primary  // Mark as primary since MongoDB is our default profile
public class InfoMongoDataService implements InfoDataService {
    private final static Logger logger = Logger.getLogger(InfoMongoDataService.class.getName());
    private final static String STREET_TYPE = "street";
    private final static String NEIGHBORHOOD_TYPE = "neighborhood";

    /**
     * SQL queries for location name lookup, using quoted identifiers for table names
     */
    private static final String STREET_NAME_QUERY = "SELECT name FROM \"nyc_streets\" WHERE gid = ?::integer";
    private static final String NEIGHBORHOOD_NAME_QUERY = "SELECT name FROM \"nyc_neighborhoods\" WHERE gid = ?::integer";

    private final MongoTemplate mongoTemplate;
    private final JdbcTemplate jdbcTemplate;

    public InfoMongoDataService(MongoTemplate mongoTemplate, JdbcTemplate jdbcTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> getActiveLocations(
            Timestamp startTimestamp,
            Timestamp endTimestamp,
            String locationType,
            String sortBy,
            Short recordsCount) {
        
        // Build match criteria
        Criteria criteria = Criteria.where("locationType").is(locationType);
        if (startTimestamp != null && endTimestamp != null) {
            criteria = criteria.and("timestampInSec")
                .gte(new Date(startTimestamp.getTime()))
                .lte(new Date(endTimestamp.getTime()));
        }

        // Create aggregation pipeline
        List<AggregationOperation> operations = new ArrayList<>();
        
        // Match stage
        operations.add(match(criteria));
        
        // Group stage
        operations.add(group("locationGid")
            .sum(ConditionalOperators.when(Criteria.where("mobilityType").is("pedestrians"))
                .then("$count")
                .otherwise(0))
            .as("pedestrians_count")
            .sum(ConditionalOperators.when(Criteria.where("mobilityType").is("mobilized"))
                .then("$count")
                .otherwise(0))
            .as("mobilized_count"));
        
        // Project stage
        operations.add(project()
            .and("_id").as("locationGid")
            .and("pedestrians_count").as("pedestrians_count")
            .and("mobilized_count").as("mobilized_count"));
        
        // Sort stage
        operations.add(sort(Sort.Direction.DESC, sortBy.equals("pedestrians") ? "pedestrians_count" : "mobilized_count")
            .and(Sort.Direction.ASC, "locationGid"));
        
        // Limit stage
        operations.add(limit(recordsCount));

        // Execute aggregation
        Aggregation aggregation = newAggregation(operations);
        AggregationResults<Document> results = mongoTemplate.aggregate(
            aggregation, "activity", Document.class
        );

        // Convert Document to Map<String, Object>
        return results.getMappedResults().stream()
            .map(doc -> {
                Map<String, Object> map = new HashMap<>();
                String locationGid = doc.getLocationGid();
                String locationName = getLocationName(locationGid, locationType);
                map.put("name", locationName != null ? locationName : locationGid);
                map.put("pedestrians_count", doc.getPedestrians_count());
                map.put("mobilized_count", doc.getMobilized_count());
                return map;
            })
            .collect(Collectors.toList());
    }

    /**
     * Retrieves the location name from PostgreSQL based on the location GID and type.
     * 
     * @param locationGid   The GID of the location to look up, must not be null or empty
     * @param locationType  The type of location, must be either 'street' or 'neighborhood'
     * @return The name of the location, or null if:
     *         - locationGid is null or empty
     *         - locationType is invalid
     *         - location is not found in the database
     *         - a database error occurs
     */
    private String getLocationName(String locationGid, String locationType) {
        if (locationGid == null || locationGid.trim().isEmpty()) {
            logger.info("Skipping location name lookup: invalid GID " + locationGid);
            return null;
        }

        if (!STREET_TYPE.equals(locationType) && !NEIGHBORHOOD_TYPE.equals(locationType)) {
            logger.info("Skipping location name lookup: invalid type " + locationType);
            return null;
        }

        try {
            String query = STREET_TYPE.equals(locationType) ? STREET_NAME_QUERY : NEIGHBORHOOD_NAME_QUERY;
            return jdbcTemplate.queryForObject(query, String.class, locationGid);
        } catch (EmptyResultDataAccessException e) {
            logger.info("No " + locationType + " found with GID " + locationGid);
            return null;
        } catch (DataAccessException e) {
            logger.warning("Failed to query " + locationType + " name from PostgreSQL for GID " + locationGid + ": " + e.getMessage());
            return null;
        } catch (Exception e) {
            logger.severe("Unexpected error querying " + locationType + " name from PostgreSQL for GID " + locationGid + ": " + e.getMessage());
            return null;
        }
    }

    private static class Document {
        private String locationGid;
        private Integer pedestrians_count;
        private Integer mobilized_count;

        public String getLocationGid() { return locationGid; }
        public void setLocationGid(String locationGid) { this.locationGid = locationGid; }
        public Integer getPedestrians_count() { return pedestrians_count; }
        public void setPedestrians_count(Integer pedestrians_count) { this.pedestrians_count = pedestrians_count; }
        public Integer getMobilized_count() { return mobilized_count; }
        public void setMobilized_count(Integer mobilized_count) { this.mobilized_count = mobilized_count; }
    }
}
