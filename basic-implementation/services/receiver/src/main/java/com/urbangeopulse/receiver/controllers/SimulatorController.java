package com.urbangeopulse.receiver.controllers;

import com.urbangeopulse.receiver.services.KafkaProducer;
import com.urbangeopulse.receiver.services.SimulatorDataService;
import com.urbangeopulse.receiver.services.Writer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
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

    @Value("${RECEIVER_THROTTLE_PRODUCING_THROUGHPUT:#{10000}}") // maximum throughput - produced messages per second
    private int RECEIVER_THROTTLE_PRODUCING_THROUGHPUT;

    private final SimulatorDataService dataService;

    public SimulatorController(SimulatorDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * @param streetName - street name for which to generate points. if null - generate for all streets.
     * @param saveToBackup - if true, simulated messages will be produced into a backup file, instead of into a kafka topic.
     */
    @GetMapping("/streets/points")
    public void simulatePointsForStreets(@RequestParam(required = false) String streetName,
                                         @RequestParam(required = false) Short threadsCount,
                                         @RequestParam(required = false) Short durationInMin,
                                         @RequestParam(required = false, defaultValue = "1") Integer iterationsCount,
                                         @RequestParam(required = false, defaultValue = "false") Boolean saveToBackup) throws InterruptedException {
        // Multithreaded execution
        if (!saveToBackup && threadsCount != null && threadsCount > 1) {
            Runnable simulatorThread = () -> {
                Instant startTime = Instant.now();
                logger.info(String.format("Starting 'simulatorThread' ..  durationInMin = %d, iterationsCount = %d", durationInMin, iterationsCount));
                executeDurationOrIterations(streetName, durationInMin, iterationsCount, dataService.createWriter(saveToBackup));
                final long elapsedTimeInMS = Duration.between(startTime, Instant.now()).toMillis();
                logger.info(String.format("'simulatorThread' completed, in %,d ms (== %.1f minutes).", elapsedTimeInMS, (double)elapsedTimeInMS / 60000));
            };

            Instant startTime = Instant.now();
            ExecutorService threadPool = Executors.newFixedThreadPool(threadsCount);
            for (int i = 0; i < threadsCount; i++) threadPool.submit(simulatorThread);
            threadPool.shutdown();
            threadPool.awaitTermination(Optional.ofNullable(durationInMin).orElse(Short.MAX_VALUE), TimeUnit.MINUTES);
            final long elapsedTimeInMS = Duration.between(startTime, Instant.now()).toMillis();
            logger.info(String.format("All 'simulatorThread' completed, in %,d ms (== %.1f minutes).", elapsedTimeInMS, (double)elapsedTimeInMS / 60000));
        }
        // Single execution - run once
        else if (durationInMin == null && iterationsCount == 1) {
            dataService.simulatePointsForStreets(streetName, dataService.createWriter(saveToBackup), null);
        }
        // Run for duration or iterations
        else {
            executeDurationOrIterations(streetName, durationInMin, iterationsCount, dataService.createWriter(saveToBackup));
        }
    }

    private void executeDurationOrIterations(String streetName, Short durationInMin, Integer iterationsCount, Writer writer) {
        assert (durationInMin != null || iterationsCount != null);

        // Only initialize throttling parameters for KafkaProducer
        boolean shouldThrottle = writer instanceof KafkaProducer;
        float targetTimePerMessageMillis = shouldThrottle ? (float) 1000 / RECEIVER_THROTTLE_PRODUCING_THROUGHPUT : 0;
        long throttleStartTimeMillis = shouldThrottle ? System.currentTimeMillis() : 0;
        SimulatorDataService.ThrottlingContext throttlingContext = shouldThrottle ? new SimulatorDataService.ThrottlingContext(0, throttleStartTimeMillis, targetTimePerMessageMillis) : null;

        // For duration-based execution
        if (durationInMin != null && durationInMin > 0) {
            Instant startTime = Instant.now();
            do {
                dataService.simulatePointsForStreets(streetName, writer, throttlingContext);
            } while (Duration.between(startTime, Instant.now()).toMinutes() < durationInMin);
        }
        // For iteration-based execution
        else {
            final int LOG_PROGRESS_EVERY_N_ITERATIONS = Math.max(1, iterationsCount / 10);
            for (int i = 1; i <= iterationsCount; i++) {
                dataService.simulatePointsForStreets(streetName, writer, throttlingContext);

                if (i % LOG_PROGRESS_EVERY_N_ITERATIONS == 0) {
                    logger.info(String.format("Executed %,-3d iterations (%s).", i,
                            streetName != null ? String.format("street name: %s", streetName) : "all streets"));
                }
            }
        }
    }

    /**
     * @param iterationsToSimulateFromBackup number of iterations to simulate from PEOPLE_GEO_LOCATIONS_CSV.
     */
    @GetMapping("")
    public void simulateFromBackup(@RequestParam(required = false) Short iterationsToSimulateFromBackup) {
        dataService.simulateFromBackup(iterationsToSimulateFromBackup);
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