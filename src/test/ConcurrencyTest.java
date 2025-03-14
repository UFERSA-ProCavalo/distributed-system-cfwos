package test;

import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import main.client.ImplClient;
import main.shared.log.Logger;
import main.shared.messages.MessageType;

public class ConcurrencyTest {
    private static final Logger logger = Logger.getLogger();
    private static final int NUM_CLIENTS = 3;

    public static void main(String[] args) throws Exception {
        // Create array to hold thread references
        Thread[] clientThreads = new Thread[NUM_CLIENTS];
        CountDownLatch latch = new CountDownLatch(NUM_CLIENTS);

        // Start multiple clients simultaneously
        for (int i = 0; i < NUM_CLIENTS; i++) {
            final int clientId = i;

            // Create thread with runnable
            clientThreads[i] = new Thread(() -> {
                try {
                    // Connect to localization server
                    Socket socket = new Socket("localhost", 11110);
                    ImplClient client = new ImplClient(socket, logger, true);

                    // Start client thread
                    Thread implClientThread = new Thread(client);
                    implClientThread.start();

                    // Wait until all clients are ready
                    latch.countDown();
                    latch.await();

                    // Wait for authentication
                    Thread.sleep(1000);

                    // Authenticate
                    if (clientId % 2 == 0) {
                        client.sendMessage(MessageType.AUTH_REQUEST, new String[] { "admin", "admin123" });
                    } else {
                        client.sendMessage(MessageType.AUTH_REQUEST, new String[] { "teste", "teste" });
                    }

                    // Wait for authentication
                    Thread.sleep(1000);

                    // Each client performs operations for the same work orders
                    for (int j = 1; j <= 3; j++) {
                        // Multiple clients try to add the same work order
                        client.sendMessage(MessageType.DATA_REQUEST,
                                String.format("ADD|%d|TestOrder%d|Concurrent test description", j, j));
                        Thread.sleep(100);

                        client.sendMessage(MessageType.DATA_REQUEST, String.format("SHOW|%d", j));
                        Thread.sleep(100);

                        // Multiple clients try to search for the same work order
                        client.sendMessage(MessageType.DATA_REQUEST, String.format("SEARCH|%d", j));
                        Thread.sleep(100);

                        client.sendMessage(MessageType.DATA_REQUEST, String.format("SHOW|%d", j));
                        Thread.sleep(100);

                        // Multiple clients try to update the same work order
                        client.sendMessage(MessageType.DATA_REQUEST,
                                String.format("UPDATE|%d|UpdatedOrder%d|Updated by client %d",
                                        j, j, clientId));
                        Thread.sleep(100);

                        client.sendMessage(MessageType.DATA_REQUEST, String.format("SHOW|%d", j));
                        Thread.sleep(100);
                    }

                    // Display database stats
                    client.sendMessage(MessageType.DATA_REQUEST, "STATS");

                    // Wait for stats
                    Thread.sleep(3000);
                    // Clean up
                    client.shutdown();

                } catch (Exception e) {
                    logger.error("Error in test client " + clientId, e);
                }
            }); // Name the thread for better debugging

            // Start the thread
            clientThreads[i].start();
        }

        // Wait for all threads to complete (equivalent to executor.awaitTermination)
        for (Thread thread : clientThreads) {
            thread.join();
        }

        logger.info("All test clients have completed");
    }
}