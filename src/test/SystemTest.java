package test;

import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import main.client.ImplClient;
import main.server.application.database.Database;
import main.server.proxy.cache.CacheFIFO;
import main.shared.log.Logger;
import main.shared.messages.MessageType;
import main.shared.models.WorkOrder;

public class SystemTest {
    private static final Logger logger = Logger.getLogger();
    private static final int NUM_THREADS = 2;
    private static final int OPERATIONS_PER_THREAD = 3;
    private static final Scanner scanner = new Scanner(System.in);

    // Add tracking maps/sets to keep track of expected database state
    private static final Map<Integer, WorkOrder> expectedDatabaseContents = new ConcurrentHashMap<>();
    private static final Set<Integer> expectedRemovedItems = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        logger.info("Starting comprehensive system test");

        // Run tests with manual advancement
        testDatabase();
        promptToContinue("Database functionality test completed. Press Enter to continue to thread safety test...");

        testDatabaseThreadSafety();
        promptToContinue("Database thread safety test completed. Press Enter to continue to cache test...");

        testCache();
        promptToContinue("Cache functionality test completed. Press Enter to continue to cache thread safety test...");

        testCacheThreadSafety();
        promptToContinue("Cache thread safety test completed. Press Enter to continue to concurrent clients test...");

        testConcurrentClients();
        promptToContinue("All tests completed. Press Enter to exit...");

        logger.info("Test suite finished");
        scanner.close();
    }

    private static void promptToContinue(String message) {
        System.out.println("\n" + message);
        scanner.nextLine();
    }

    private static void testDatabase() {
        logger.info("=== Testing Database Functionality ===");
        Database db = new Database();

        // Test adding work orders
        logger.info("Adding work orders");
        db.addWorkOrder(1, "Test Order 1", "Description 1");
        db.addWorkOrder(2, "Test Order 2", "Description 2", "2023-03-15");

        // Test searching
        logger.info("Testing search functionality");
        WorkOrder order1 = db.searchWorkOrder(1);
        if (order1 != null && order1.getName().equals("Test Order 1")) {
            logger.info("Search test passed");
        } else {
            logger.error("Search test failed");
        }

        // Test updating
        logger.info("Testing update functionality");
        db.updateWorkOrder(1, "Updated Order", "Updated Description", "2023-03-16");
        order1 = db.searchWorkOrder(1);
        if (order1 != null && order1.getName().equals("Updated Order")) {
            logger.info("Update test passed");
        } else {
            logger.error("Update test failed");
        }

        // Test removing
        logger.info("Testing remove functionality");
        db.removeWorkOrder(2);
        if (db.searchWorkOrder(2) == null) {
            logger.info("Remove test passed");
        } else {
            logger.error("Remove test failed");
        }

        logger.info("Database content: " + db.getDatabaseContent());
    }

    private static void testDatabaseThreadSafety() {
        logger.info("=== Testing Database Thread Safety ===");
        final Database db = new Database();
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(NUM_THREADS);
        AtomicInteger successCount = new AtomicInteger(0);

        // Clear tracking collections
        expectedDatabaseContents.clear();
        expectedRemovedItems.clear();

        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startSignal.await();

                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        int code = threadId * 1000 + j;
                        String name = "Thread" + threadId + "-Order" + j;
                        String desc = "Created by thread " + threadId;

                        db.addWorkOrder(code, name, desc);

                        // Track what we expect in the database
                        expectedDatabaseContents.put(code, new WorkOrder(code, name, desc));

                        // Verify the add worked
                        WorkOrder found = db.searchWorkOrder(code);
                        if (found != null && found.getName().equals(name)) {
                            successCount.incrementAndGet();
                        }

                        // Update half the orders
                        if (j % 2 == 0) {
                            String updatedName = "Updated-" + name;
                            String updatedDesc = "Updated-" + desc;
                            String timestamp = "2023-03-16";
                            db.updateWorkOrder(code, updatedName, updatedDesc, timestamp);

                            // Update our tracked expected state
                            expectedDatabaseContents.put(code,
                                    new WorkOrder(code, updatedName, updatedDesc, timestamp));
                        }

                        // Remove some orders
                        if (j % 5 == 0) {
                            db.removeWorkOrder(code);
                            expectedDatabaseContents.remove(code);
                            expectedRemovedItems.add(code);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Thread " + threadId + " error: ", e);
                } finally {
                    doneSignal.countDown();
                }
            }).start();
        }

        startSignal.countDown();

        try {
            doneSignal.await(30, TimeUnit.SECONDS);
            logger.info("Database thread safety test completed with " + successCount.get() + " successful operations");
            logger.info("Final database size: " + db.getSize());

            // Check for database inconsistencies
            checkDatabaseConsistency(db);

        } catch (InterruptedException e) {
            logger.error("Test interrupted", e);
        }
    }

    /**
     * Verify database consistency against expected state
     */
    private static void checkDatabaseConsistency(Database db) {
        logger.info("Checking database consistency...");
        int inconsistencies = 0;
        int missingItems = 0;
        int unexpectedItems = 0;

        // Check if all expected items exist with correct data
        for (Map.Entry<Integer, WorkOrder> entry : expectedDatabaseContents.entrySet()) {
            int code = entry.getKey();
            WorkOrder expected = entry.getValue();
            WorkOrder actual = db.searchWorkOrder(code);

            if (actual == null) {
                logger.error("INCONSISTENCY: Item with code {} missing from database", code);
                missingItems++;
                continue;
            }

            // Check all fields match
            if (!expected.getName().equals(actual.getName()) ||
                    !expected.getDescription().equals(actual.getDescription())) {
                logger.error("INCONSISTENCY: Item with code {} has incorrect data", code);
                logger.error("  Expected: {}", expected);
                logger.error("  Actual: {}", actual);
                inconsistencies++;
            }
        }

        // Check that removed items are actually removed
        for (Integer code : expectedRemovedItems) {
            if (db.searchWorkOrder(code) != null) {
                logger.error("INCONSISTENCY: Item with code {} should have been removed", code);
                unexpectedItems++;
            }
        }

        if (inconsistencies == 0 && missingItems == 0 && unexpectedItems == 0) {
            logger.info("Database consistency check: PASSED ✓");
        } else {
            logger.error("Database consistency check: FAILED ✗");
            logger.error("- Data inconsistencies: {}", inconsistencies);
            logger.error("- Missing items: {}", missingItems);
            logger.error("- Items not properly removed: {}", unexpectedItems);
        }
    }

    private static void testCache() {
        logger.info("=== Testing Cache Functionality ===");
        CacheFIFO<WorkOrder> cache = new CacheFIFO<>();

        // Add items to cache
        logger.info("Adding items to cache");
        for (int i = 1; i <= 5; i++) {
            cache.add(new WorkOrder(i, "Cache Order " + i, "Cache Description " + i));
        }

        // Test retrieval
        logger.info("Testing cache retrieval");
        WorkOrder searchCriteria = new WorkOrder(3, null, null);
        WorkOrder found = cache.get(searchCriteria);
        if (found != null && found.getCode() == 3) {
            logger.info("Cache retrieval test passed");
        } else {
            logger.error("Cache retrieval test failed");
        }

        // Test cache size and content
        logger.info("Cache size: " + cache.getSize());
        logger.info("Cache contents: " + cache.getCacheContentsAsString());
    }

    private static void testCacheThreadSafety() {
        logger.info("=== Testing Cache Thread Safety ===");
        final CacheFIFO<WorkOrder> cache = new CacheFIFO<>();
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startSignal.await();

                    for (int j = 0; j < 20; j++) {
                        int code = threadId * 100 + j;
                        WorkOrder order = new WorkOrder(code, "CacheThread" + threadId + "-" + j, "Description");

                        // Add item to cache
                        cache.add(order);

                        // Search for previously added item
                        if (j > 0) {
                            WorkOrder criteria = new WorkOrder(code - 1, null, null);
                            WorkOrder found = cache.get(criteria);
                            if (found != null) {
                                logger.debug("Thread " + threadId + " found order " + found.getCode());
                            }
                        }

                        // Remove every third item
                        if (j % 3 == 0 && j > 0) {
                            WorkOrder toRemove = new WorkOrder(code - 3, null, null);
                            cache.remove(toRemove);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Cache thread " + threadId + " error: ", e);
                } finally {
                    doneSignal.countDown();
                }
            }).start();
        }

        startSignal.countDown();

        try {
            doneSignal.await(30, TimeUnit.SECONDS);
            logger.info("Cache thread safety test completed");
            logger.info("Final cache size: " + cache.getSize());

            // Add cache consistency check
            checkCacheConsistency(cache);

        } catch (InterruptedException e) {
            logger.error("Test interrupted", e);
        }
    }

    /**
     * Verify cache consistency by checking for duplicate entries and internal data
     * structure
     */
    private static void checkCacheConsistency(CacheFIFO<WorkOrder> cache) {
        logger.info("Checking cache consistency...");

        // Get all items from cache for analysis
        List<WorkOrder> allCacheItems = cache.getAllWorkOrders();

        // Check for duplicates (items with same code)
        Set<Integer> uniqueCodes = new HashSet<>();
        Set<Integer> duplicateCodes = new HashSet<>();

        for (WorkOrder item : allCacheItems) {
            if (!uniqueCodes.add(item.getCode())) {
                duplicateCodes.add(item.getCode());
            }
        }

        if (duplicateCodes.isEmpty()) {
            logger.info("Cache duplicate check: PASSED ✓ (No duplicates found)");
        } else {
            logger.error("Cache duplicate check: FAILED ✗");
            logger.error("Found {} duplicate items in cache: {}", duplicateCodes.size(), duplicateCodes);
        }

        // Check internal consistency (size matches actual content)
        if (cache.getSize() == allCacheItems.size()) {
            logger.info("Cache size consistency: PASSED ✓ (Size matches content)");
        } else {
            logger.error("Cache size consistency: FAILED ✗");
            logger.error("Reported size: {}, Actual item count: {}", cache.getSize(), allCacheItems.size());
        }
    }

    private static void testConcurrentClients() {
        logger.info("=== Testing Concurrent Clients ===");
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(NUM_THREADS);
        Thread[] threads = new Thread[NUM_THREADS];

        // Add tracking collections for expected results
        Map<Integer, String> expectedWorkOrders = new ConcurrentHashMap<>();
        Set<Integer> expectedRemovedOrders = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < NUM_THREADS; i++) {
            final int clientId = i;
            threads[i] = new Thread(() -> {
                Socket socket = null;
                ImplClient client = null;

                try {
                    // Connect to server
                    socket = new Socket("localhost", 11110);
                    client = new ImplClient(socket, logger, true);

                    Thread clientThread = new Thread(client);
                    clientThread.start();

                    // Wait for all clients to be ready
                    startSignal.await();

                    // Authenticate

                    client.sendMessage(MessageType.AUTH_REQUEST, new String[] { "admin", "admin123" });
                    Thread.sleep(500);

                    // Each client adds work orders
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        int code = clientId * 1000 + j;
                        client.sendMessage(MessageType.DATA_REQUEST,
                                String.format("ADD|%d|Client%d-Order%d|Concurrent test description", code, clientId,
                                        j));

                        // Track ADD operations
                        expectedWorkOrders.put(code, String.format("Client%d-Order%d", clientId, j));

                        // Every 10 operations, perform some other operations
                        if (j % 10 == 0) {
                            // Search for existing order
                            client.sendMessage(MessageType.DATA_REQUEST, String.format("SEARCH|%d", code - 5));

                            // Update an order
                            if (j > 0) {
                                client.sendMessage(MessageType.DATA_REQUEST,
                                        String.format("UPDATE|%d|Updated%d-Order%d|Updated description",
                                                code - 10, clientId, j - 10));
                                expectedWorkOrders.put(code - 10, String.format("Updated%d-Order%d", clientId, j - 10));
                            }

                            // Remove an order
                            if (j > 20) {
                                client.sendMessage(MessageType.DATA_REQUEST,
                                        String.format("REMOVE|%d", code - 20));
                                expectedWorkOrders.remove(code - 20);
                                expectedRemovedOrders.add(code - 20);
                            }
                        }
                    }

                    // Get database stats
                    client.sendMessage(MessageType.DATA_REQUEST, "STATS");

                    Thread.sleep(1000);

                } catch (Exception e) {
                    logger.error("Client " + clientId + " error: ", e);
                } finally {
                    if (client != null) {
                        client.shutdown();
                    }
                    doneSignal.countDown();
                }
            });

            // Start each thread
            threads[i].start();
        }

        // Start all clients at once
        startSignal.countDown();

        try {
            // Wait for all clients to finish
            boolean completed = doneSignal.await(60, TimeUnit.SECONDS);
            if (completed) {
                logger.info("Concurrent client test completed successfully");
            } else {
                logger.error("Concurrent client test timed out");
            }

            // Add a final check against database state
            logger.info("To fully validate consistency, run a SHOW command on the server and verify:");
            logger.info("1. {} work orders should exist", expectedWorkOrders.size());
            logger.info("2. {} work orders should not exist", expectedRemovedOrders.size());

        } catch (InterruptedException e) {
            logger.error("Test interrupted", e);
        }
    }

    // Database and cache consistency check methods remain unchanged
}
