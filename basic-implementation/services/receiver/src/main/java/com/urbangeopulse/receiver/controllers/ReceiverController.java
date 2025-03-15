package com.urbangeopulse.receiver.controllers;

import com.urbangeopulse.receiver.services.ReceiverDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
@RequestMapping("/urbangeopulse/api/v1/receiver")
public class ReceiverController {

    private static final Logger logger = Logger.getLogger(ReceiverController.class.getName());

    private final ReceiverDataService dataService;

    public ReceiverController(ReceiverDataService dataService) {
        this.dataService = dataService;
    }

    /** process a geospatial point.
     * @param cityCode - city code, e.g. NYC for New York City.
     * @param uuid - Universal Unique Identifier of the device that sent the point.
     * @param point - the point to process.
     */
    @GetMapping("/point")
    public void processGeoPoint(@RequestParam String cityCode,
                                @RequestParam String uuid,
                                @RequestParam String point) {
        dataService.processGeoPoint(cityCode, uuid, point);
    }
}