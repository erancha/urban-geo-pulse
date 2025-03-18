package com.urbangeopulse.receiver.controllers;

import com.urbangeopulse.receiver.services.SimulatorDataService;
import com.urbangeopulse.receiver.services.Writer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.urbangeopulse.utils.misc.Logger.logException;

@RestController
@RequestMapping("/urbangeopulse/api/v1/simulator")
public class SimulatorController {

    private static final Logger logger = Logger.getLogger(SimulatorController.class.getName());

    @Value("${URL_TO_EXECUTE_AFTER_STARTUP:#{null}}")
    private String URL_TO_EXECUTE_AFTER_STARTUP;

    @Value("${SIMULATOR_THROTTLE_THROUGHPUT_PRODUCING:#{10000}}")
    private int SIMULATOR_THROTTLE_THROUGHPUT_PRODUCING;

    @Value("${SIMULATOR_INCREMENT_EVERY_N_ITERATIONS:#{5}}")
    private int SIMULATOR_INCREMENT_EVERY_N_ITERATIONS;

    @Value("${SIMULATOR_INCREMENT_VALUE:#{200}}")
    private int SIMULATOR_INCREMENT_VALUE;

    private final SimulatorDataService dataService;

    public SimulatorController(SimulatorDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Simulates geo-location points for specified streets and writes them to a CSV file.
     * This endpoint supports two modes of operation:
     * 1. Single execution (default) - simulates one batch of points when both durationInMin is null and iterationsCount is 1
     * 2. Multiple executions - runs for either a specified duration or number of iterations
     *
     * @param streetName The street name for which to generate points. If null, generates for all streets.
     * @return ResponseEntity with success message
     * @throws InterruptedException if the simulation is interrupted
     */
    @PostMapping("/create-backup")
    public ResponseEntity<String> simulateIntoBackup(@RequestParam(required = false) String streetName) throws InterruptedException {
        dataService.simulateIntoBackup(streetName);
        return ResponseEntity.ok("Simulation completed successfully");
    }

    /**
     * Simulates data from backup file into Kafka.
     * @param iterationsCount number of iterations to simulate from PEOPLE_GEO_LOCATIONS_CSV.
     */
    @GetMapping("/execute-from-backup")
    public void simulateFromBackup(
            @RequestParam(required = false) Short iterationsCount,
            @RequestParam(required = false) Integer throttleThroughput,
            @RequestParam(required = false) Integer incrementEveryNIterations,
            @RequestParam(required = false) Integer incrementValue) {
        dataService.simulateFromBackup(
            iterationsCount,
            throttleThroughput != null ? throttleThroughput : SIMULATOR_THROTTLE_THROUGHPUT_PRODUCING,
            incrementEveryNIterations != null ? incrementEveryNIterations : SIMULATOR_INCREMENT_EVERY_N_ITERATIONS,
            incrementValue != null ? incrementValue : SIMULATOR_INCREMENT_VALUE
        );
    }

    /**
     * execute an url (URL_TO_EXECUTE_AFTER_STARTUP) as soon as the controller is ready to accept requests.
     * @param event
     */
    @EventListener(ContextRefreshedEvent.class)
    void onApplicationReady(ContextRefreshedEvent event) {
        if (URL_TO_EXECUTE_AFTER_STARTUP != null) {
            Thread backgroundThread = new Thread(() -> {
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    final String url = (URL_TO_EXECUTE_AFTER_STARTUP.startsWith("\"")
                            ? URL_TO_EXECUTE_AFTER_STARTUP.substring(1, URL_TO_EXECUTE_AFTER_STARTUP.length() - 1)
                            : URL_TO_EXECUTE_AFTER_STARTUP).replace(" ","%20");
                    HttpRequest request = HttpRequest.newBuilder().uri(new URI(url)).build();
                    logger.info(String.format("Executing %s ..", request));
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    logger.info(String.format("Status code: %d, Response body: %s", response.statusCode(), response.body()));
                    //KafkaUtils.listAllTopicsAndConsumers();
                } catch (IOException | InterruptedException | URISyntaxException ex) {
                    logException(ex, logger);
                }
            });
            backgroundThread.start();
        }
    }
}