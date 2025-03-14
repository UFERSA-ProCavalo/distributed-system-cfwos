package test;

import main.server.proxy.cache.CacheFIFO;
import main.shared.log.Logger;
import main.shared.models.WorkOrder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CacheRaceConditionTest {

    public static void main(String[] args) throws Exception {

        Logger logger = Logger.getLogger();
        // Create a shared cache
        CacheFIFO<WorkOrder> cache = new CacheFIFO<>();

        // Create a start signal
        CountDownLatch startSignal = new CountDownLatch(1);

        // Number of threads to test with
        int threadCount = 100;

        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Submit tasks that will all try to access the cache simultaneously
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Wait for start signal to ensure all threads start at once
                    startSignal.await();

                    // Create a work order
                    WorkOrder order = new WorkOrder(1, "Test Order", "Description");

                    // Half the threads add, half remove
                    if (threadId % 2 == 0) {
                        cache.add(order);
                        logger.debug("Thread " + threadId + " added order" + order);
                        logger.debug(cache.getCacheContentsAsString());
                    } else {
                        cache.remove(order);
                        logger.debug("Thread " + threadId + " removed order");
                        logger.debug(cache.getCacheContentsAsString());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Start all threads at once
        startSignal.countDown();

        // Wait a moment
        Thread.sleep(2000);

        // Check the cache state
        System.out.println("Cache size: " + cache.getSize());
        System.out.println("Cache contents: " + cache.getCacheContentsAsString());

        // Shutdown
        executor.shutdown();
    }
}