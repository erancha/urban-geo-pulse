package com.urbangeopulse.delay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/***
 * Microservice.
 *
 * Responsibilities:
 *      (1) To consume and delay events until the requested time elapses.
 *      (2) To produce the original event (a key/value in the delay event) back to the topic requested in the delay event.
 */
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}