package com.urbangeopulse.aggregator.services;

import java.util.Map;

/**
 * Interface for activity data persistence.
 */
public interface AggregatorDataService {
    void persistWithoutRetries(Map<String, Integer> minuteResolutionMap, String mobilityType, String locationType);
    void persistWithRetries(Map<String, Integer> minuteResolutionMap, String mobilityType, String locationType);
}