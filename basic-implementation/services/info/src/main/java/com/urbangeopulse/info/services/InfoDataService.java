package com.urbangeopulse.info.services;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public interface InfoDataService {
    List<Map<String, Object>> getActiveLocations(
            Timestamp startTimestamp,
            Timestamp endTimestamp,
            String locationType,
            String sortBy,
            Short recordsCount);
}
