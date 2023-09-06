package com.urbangeopulse.info.controllers;

import com.urbangeopulse.info.services.InfoDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RestController
public class InfoController {

    private static final Logger logger = Logger.getLogger(InfoController.class.getName());

    @Autowired
    InfoDataService dataService;

    /**
     * @param startTimestamp - start time stamp.
     * @param endTimestamp - end time stamp.
     * @param minutesBack - (alternative to 'startTimestamp' and 'endTimestamp') minutes from the current time, until the current time.
     * @param locationType - 'street' or 'neighborhood'.
     * @param sortBy - 'pedestrians' or 'mobilized'.
     * @param recordsCount - number of records to return.
     * @return the first most active 'recordsCount' streets or neighborhoods (depending on 'locationType') between timestamps 'startTimestamp' and 'endTimestamp', sorted by 'sortBy'.
     */
    @GetMapping("/urbangeopulse/api/info/locations/activity")
    public List<Map<String, Object>> getActiveLocations(@RequestParam(required = false) Timestamp startTimestamp,
                                                        @RequestParam(required = false) Timestamp endTimestamp,
                                                        @RequestParam(required = false) Short minutesBack,
                                                        @RequestParam(required = false, defaultValue = "street") String locationType,
                                                        @RequestParam(required = false, defaultValue = "pedestrians") String sortBy,
                                                        @RequestParam(required = false, defaultValue = "10") Short recordsCount) {
        if (startTimestamp == null || endTimestamp == null) {
            if (minutesBack != null) {
                // Calculate startTimestamp and endTimestamp based on minutesBack
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MINUTE, -minutesBack);
                startTimestamp = new Timestamp(cal.getTimeInMillis());
                endTimestamp = new Timestamp(System.currentTimeMillis());
            } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either provide startTimestamp and endTimestamp, or minutesBack");
        }
        return dataService.getActiveLocations(startTimestamp, endTimestamp, locationType, sortBy, recordsCount);
    }
}