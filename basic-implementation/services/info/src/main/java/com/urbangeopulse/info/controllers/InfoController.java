package com.urbangeopulse.info.controllers;

import com.urbangeopulse.info.services.InfoDataService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;

@RestController
@RequestMapping("/urbangeopulse/api/v1/info")
public class InfoController {

    private static final Logger logger = Logger.getLogger(InfoController.class.getName());
    private final InfoDataService dataService;

    public InfoController(InfoDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * @param startTimestampUTC - start time stamp (in UTC time).
     * @param endTimestampUTC   - end time stamp (in UTC time).
     * @param minutesBack    - (alternative to 'startTimestampUTC' and 'endTimestampUTC') minutes ago from the current time, until the current time.
     * @param locationType   - 'street' or 'neighborhood'.
     * @param sortBy         - 'pedestrians' or 'mobilized'.
     * @param recordsCount   - number of records to return.
     * @return the first most active 'recordsCount' streets or neighborhoods (depending on 'locationType') between timestamps 'startTimestampUTC' and 'endTimestampUTC', sorted by 'sortBy'.
     */
    @GetMapping("/locations/activity")
    public List<Map<String, Object>> getActiveLocations(
            @RequestParam(required = false) Timestamp startTimestampUTC,
            @RequestParam(required = false) Timestamp endTimestampUTC,
            @RequestParam(required = false) Short minutesBack,
            @RequestParam(required = false, defaultValue = "street") String locationType,
            @RequestParam(required = false, defaultValue = "pedestrians") String sortBy,
            @RequestParam(required = false, defaultValue = "10") Short recordsCount) {

        if (minutesBack != null) {
            // Use UTC time for minutesBack calculation
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Instant now = Instant.now();
            endTimestampUTC = Timestamp.from(now);
            startTimestampUTC = Timestamp.from(now.minusSeconds(minutesBack * 60L));
        } else if (startTimestampUTC == null || endTimestampUTC == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Either provide startTimestampUTC and endTimestampUTC, or minutesBack"
            );
        }

        logger.info(String.format("Getting active locations for type=%s, sortBy=%s, count=%d", locationType, sortBy, recordsCount));
        return dataService.getActiveLocations(
                startTimestampUTC,
                endTimestampUTC,
                locationType,
                sortBy,
                recordsCount);
    }
}