package com.urbangeopulse.sorter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public interface Cache {
    static final Logger logger = Logger.getLogger(Cache.class.getName());

//    public static void main(String[] args) {
//        final short THREADS_COUNT = 40;
//        final int ITERATIONS_COUNT = 10000;
//        logger.info(String.format("Starting %d threads * %d iterations ..", THREADS_COUNT, ITERATIONS_COUNT));
//        final ExecutorService threadPool = Executors.newFixedThreadPool(THREADS_COUNT);
//        //final CountDownLatch latch = new CountDownLatch(THREADS_COUNT);
//        final Instant startTime = Instant.now();
//        for (int i = 0; i < THREADS_COUNT; i++) {
//            int finalI = i;
//            threadPool.submit(() -> {
//                final Cache cache = new RemoteCache/*LocalCache*/();
//                for (int j = 1; j <= ITERATIONS_COUNT; j++) {
//                    final String KEY = String.format("%d--%d", finalI, j);
//                    final String VALUE = String.valueOf(j);
//                    cache.put(KEY, VALUE);
//                    assert (cache.get(KEY).equals(VALUE));
//                    cache.remove(KEY);
//                    //if (j % (ITERATIONS_COUNT / 2) == 0 /*== ITERATIONS_COUNT*/) logger.info(String.format("Iterated %,4d times, in %d seconds.", j, Duration.between(startTime, Instant.now()).toMillis() / 1000));
//                }
//                //latch.countDown();
//            });
//        }
//        try {
//            //latch.await();
//            threadPool.shutdown();
//            threadPool.awaitTermination(Short.MAX_VALUE, TimeUnit.MILLISECONDS);
//        } catch (InterruptedException e) {
//            com.urbangeopulse.utils.misc.Logger.logException(e, logger);
//        }
//        logger.info(String.format("Completed all threads, in %d seconds.", Duration.between(startTime, Instant.now()).toMillis() / 1000));
//    }

    String put(String key, String value);
    String get(String key);
    void remove(String key);
}
