package com.urbangeopulse.receiver.controllers;

import com.urbangeopulse.receiver.services.ReceiverDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
public class ReceiverController {

    private static final Logger logger = Logger.getLogger(ReceiverController.class.getName());

    private final ReceiverDataService dataService;

    public ReceiverController(ReceiverDataService dataService) {
        this.dataService = dataService;
    }

    /** process a geospatial point.
     * @param uuid - Universal Unique Identifier of the device that sent the point.
     * @param point - the point to process.
     */
    @GetMapping("/urbangeopulse/api/receiver/point")
    public void processGeoPoint(@RequestParam String uuid,
                                @RequestParam String point) {
        dataService.processGeoPoint(uuid, point);
    }
}