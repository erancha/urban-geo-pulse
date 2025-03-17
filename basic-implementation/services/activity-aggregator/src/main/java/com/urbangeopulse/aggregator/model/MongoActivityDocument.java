package com.urbangeopulse.aggregator.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.sql.Timestamp;

/**
 * MongoDB Activity Document with optimized indexes:
 * 
 * 1. Compound Index for Top-N Queries:
 *    Index: {locationType: 1, timestampInSec: 1, locationGid: 1, count: 1}
 *    
 *    This index optimizes the following operations:
 *    a) Filter by locationType (e.g., 'street' or 'neighborhood')
 *       - Uses index prefix for instant filtering
 *       - Avoids scanning documents of wrong type
 *    
 *    b) Time range queries within the filtered type
 *       - Uses second index field for efficient time scans
 *       - Maintains sort order within time ranges
 *    
 *    c) Group by location and sum counts
 *       - Index already orders by locationGid
 *       - Count field included in index (covered query)
 *       - No need to fetch full documents
 * 
 * 2. Compound Index for Upserts:
 *    Index: {locationGid: 1, timestampInSec: 1, mobilityType: 1, locationType: 1}
 *    Unique: true
 *    
 *    This index optimizes the following operations:
 *    a) Upsert operations
 *       - Quickly finds existing documents
 *       - Enforces data consistency (no duplicates)
 *       - Supports bulk write operations
 *    
 *    b) Unique constraint ensures one record per:
 *       - Location (locationGid)
 *       - Time (timestampInSec)
 *       - Mobility type (pedestrian/mobilized)
 *       - Location type (street/neighborhood)
 * 
 * Note: No need for single-field indexes since:
 * - locationGid queries use activity_update_idx (locationGid is first field)
 * - timestampInSec queries use activity_query_idx (timestampInSec is second field)
 * - MongoDB can use index prefixes efficiently
 * 
 * Collection Structure:
 * {
 *   locationGid: String,   // Location identifier (street/neighborhood gid)
 *   locationType: String,  // 'street' or 'neighborhood'
 *   timestampInSec: Date,  // Event timestamp
 *   mobilityType: String,  // 'pedestrian' or 'mobilized'
 *   count: Integer,        // Activity count
 *   insertTimestamp: Date, // When document was first created
 *   lastUpdateTimestamp: Date // Last modification time
 * }
 * 
 * Note: MongoDB automatically adds an '_id' field as the primary key.
 * We don't need to declare it since we identify documents by their business keys
 * using the unique compound index activity_update_idx.
 * 
 * Sample queries:
        {  
            locationType: 'street',
            mobilityType: 'pedestrians',
            count: {$gt: 1},
            "timestampInSec": {
                "$gte": ISODate("2025-03-15T07:30:00Z")
            }
        }
 */
@Document(collection = "activity")
@CompoundIndexes({
    @CompoundIndex(name = "activity_query_idx", 
        def = "{'locationType': 1, 'timestampInSec': 1, 'locationGid': 1, 'count': 1}"),
    @CompoundIndex(name = "activity_update_idx",
        def = "{'locationGid': 1, 'timestampInSec': 1, 'mobilityType': 1, 'locationType': 1}",
        unique = true)
})
public class MongoActivityDocument {
    private String locationGid;
    
    private String locationType;
    
    private Timestamp timestampInSec;
    
    private String mobilityType;
    
    private Integer count;
    
    private Timestamp insertTimestamp;
    
    private Timestamp lastUpdateTimestamp;

    public MongoActivityDocument() {}

    public MongoActivityDocument(String locationGid, String locationType, Timestamp timestampInSec, 
                          String mobilityType, Integer count) {
        this.locationGid = locationGid;
        this.locationType = locationType;
        this.timestampInSec = timestampInSec;
        this.mobilityType = mobilityType;
        this.count = count;
        this.insertTimestamp = new Timestamp(System.currentTimeMillis());
        this.lastUpdateTimestamp = this.insertTimestamp;
    }

    public String getLocationGid() {
        return locationGid;
    }

    public void setLocationGid(String locationGid) {
        this.locationGid = locationGid;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }

    public Timestamp getTimestampInSec() {
        return timestampInSec;
    }

    public void setTimestampInSec(Timestamp timestampInSec) {
        this.timestampInSec = timestampInSec;
    }

    public String getMobilityType() {
        return mobilityType;
    }

    public void setMobilityType(String mobilityType) {
        this.mobilityType = mobilityType;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Timestamp getInsertTimestamp() {
        return insertTimestamp;
    }

    public void setInsertTimestamp(Timestamp insertTimestamp) {
        this.insertTimestamp = insertTimestamp;
    }

    public Timestamp getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    public void setLastUpdateTimestamp(Timestamp lastUpdateTimestamp) {
        this.lastUpdateTimestamp = lastUpdateTimestamp;
    }
}
