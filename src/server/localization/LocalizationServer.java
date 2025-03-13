package server.localization;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import shared.log.Logger;

public class LocalizationServer {
    private ServerSocket serverSocket;

    private static Map<String, String> serverAddresses;
    private static Boolean running = false;
    private static int processConnections = 0;
    private static int activeConnections = 0;

    private static final String PROXY_IP = "localhost";
    private static final int PROXY_PORT = 22220;
    private static final int LOCALIZATION_PORT = 11110;
    private static final Logger logger = Logger.getLogger();

    public LocalizationServer() {

        logger.info("Starting server localization...");

        serverAddresses = new HashMap<>();
        try {
            serverSocket = new ServerSocket(LOCALIZATION_PORT);
            logger.info("Localization Server started at: {}", serverSocket);
            running = true;
        } catch (Exception e) {
            logger.error("Error while starting Localization Server: " + e.getMessage());
        }
        // Set the server addresses

        serverAddresses.put("Application", "ProxyServer" + ":" + PROXY_IP + ":" + PROXY_PORT);
        logger.info("Known addresses: {}", serverAddresses);

        // runtime shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Server localization shutting down...");
            shutdown();
        }));

        this.run();
    }

    private void run() {
        logger.info("Server localization is running...");
        try {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept(); // ACCEPT CLIENT CONNECTION

                    // Increment process and active connections
                    synchronized (LocalizationServer.class) {
                        processConnections++;
                        activeConnections++;
                    }
                    logger.info("Process connections: {}, Active connections: {}",
                            processConnections, activeConnections);

                    // Handle client requests in a separate thread
                    LocalizationServerHandler handler = new LocalizationServerHandler(clientSocket,
                            logger);

                    Thread thread = new Thread(handler);
                    thread.start();

                } catch (Exception e) {
                    logger.error("Error while accepting client connection: " + e.getMessage());
                    continue;
                }
            }
        } catch (Exception e) {
            logger.error("Error in server localization: " + e.getMessage());
        }
    }

    private void shutdown() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            logger.error("Error while shutting down server localization: " + e.getMessage());
        }
    }

    public static Map<String, String> getServerAddresses() {
        return serverAddresses;
    }

    public static void main(String[] args) {
        new LocalizationServer();
    }
}