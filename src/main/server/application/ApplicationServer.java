package main.server.application;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import main.shared.log.Logger;

public class ApplicationServer {
    private static ServerSocket serverSocket;
    private static boolean running = false;
    private static final AtomicInteger processConnections = new AtomicInteger(0);
    private static final AtomicInteger activeConnections = new AtomicInteger(0);

    private static final int APPLICATION_PORT = 33330;
    private static final Logger logger = Logger.getLogger();

    static {
        try {
            serverSocket = new ServerSocket(APPLICATION_PORT);
            logger.info("Application Server started at: {}", serverSocket);
            running = true;
        } catch (Exception e) {
            logger.error("Error while starting Application Server: " + e.getMessage());
        }
    }

    public ApplicationServer() {
        System.out.println("\033[2J\033[1;1H"); // Clear screen
        logger.info("Starting application server...");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Application server shutting down...");
            shutdown();
        }));

        run();
    }

    private void run() {
        logger.info("Application server is running...");
        try {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    // Increment counters
                    processConnections.incrementAndGet();
                    activeConnections.incrementAndGet();

                    logger.info("Process connections: {}, Active connections: {}",
                            processConnections.get(), activeConnections.get());

                    // Handle client in a separate thread
                    ApplicationServerHandler handler = new ApplicationServerHandler(clientSocket, logger);
                    Thread thread = new Thread(handler);
                    thread.start();
                } catch (Exception e) {
                    logger.error("Error while accepting client connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error in application server: " + e.getMessage());
        }
    }

    private void shutdown() {
        try {
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            logger.info("Application server shut down successfully");
        } catch (Exception e) {
            logger.error("Error during server shutdown: " + e.getMessage());
        }
    }

    public static void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    public static void main(String[] args) {
        new ApplicationServer();
    }
}