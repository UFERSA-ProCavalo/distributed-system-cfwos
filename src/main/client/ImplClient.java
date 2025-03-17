package main.client;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import main.client.gui.LanternaUI;
import main.client.message.MessageDispatcher;
import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageType;

public class ImplClient implements Runnable {
    // Constants
    public static final String AUTH_SUCCESS = "success";
    public static final String AUTH_FAILURE = "failure";

    // Services
    private final ServiceNetwork networkManager;
    private final MessageDispatcher messageDispatcher;

    // State
    private String clientId;
    private String serverAddress;
    private boolean authenticated = false;
    private final AtomicInteger loginTries = new AtomicInteger(0);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    // UI
    private LanternaUI lanternaUI;
    private boolean useLanterna = true; // Default to using Lanterna

    // Utilities
    private final Logger logger;
    private boolean testing = false;

    public ImplClient(Socket initialSocket, Logger logger) {
        this.logger = logger;
        this.clientId = "Client-" + Thread.currentThread().getName();

        // Initialize services
        this.networkManager = new ServiceNetwork(initialSocket, logger, clientId);
        this.messageDispatcher = new MessageDispatcher(this, logger);

        // Register message bus subscriptions
        registerMessageHandlers();
    }

    public ImplClient(Socket initialSocket, Logger logger, boolean useLanterna) {
        this(initialSocket, logger);
        this.useLanterna = useLanterna;

        // Always initialize Lanterna UI
        try {

            this.lanternaUI = new LanternaUI(this, logger);
            new Thread(lanternaUI, "lanterna-ui-thread").start();
            logger.info("Lanterna UI initialized");
        } catch (Exception e) {
            logger.error("Error initializing Lanterna UI: {}", e);
            this.useLanterna = false;
            this.shutdown();
        }
    }

    public ImplClient(Socket socket, Logger logger, Boolean testing) {
        this(socket, logger);
        this.testing = testing;
    }

    private void registerMessageHandlers() {
        // Register all message types to be routed through our dispatcher
        networkManager.registerHandler(MessageType.START_RESPONSE, this::routeMessage);
        networkManager.registerHandler(MessageType.AUTH_RESPONSE, this::routeMessage);
        networkManager.registerHandler(MessageType.DATA_RESPONSE, this::routeMessage);
        networkManager.registerHandler(MessageType.ERROR, this::routeMessage);
        networkManager.registerHandler(MessageType.DISCONNECT, this::routeMessage);
        networkManager.registerHandler(MessageType.SERVER_INFO, this::routeMessage);
        networkManager.registerHandler(MessageType.LOGOUT_RESPONSE, this::routeMessage);
    }

    // Single method to route all messages through the dispatcher
    private void routeMessage(Message message) {
        messageDispatcher.dispatchMessage(message);

        // Update UI based on message type
        if (lanternaUI != null) {
            logger.debug("Updating UI for message type: {}", message.getType());

            switch (message.getType()) {
                case START_RESPONSE:
                    lanternaUI.updateConnectionStatus("Connected to " + message.getSender(), true);
                    lanternaUI.updateStatus("Connection established");
                    break;

                case SERVER_INFO:
                    lanternaUI.updateStatus("Received server redirection info");
                    break;

                case AUTH_RESPONSE:
                    // Auth updates will be handled by the AuthResponseHandler
                    break;

                case DISCONNECT:
                    lanternaUI.updateConnectionStatus("Disconnected", false);
                    lanternaUI.updateStatus("Connection closed");
                    break;

                case ERROR:
                    String errorMessage = message.getPayload() != null ? message.getPayload().toString()
                            : "Unknown error";
                    lanternaUI.updateStatus("Error: " + errorMessage);

                    // Check for "no proxy servers" error and trigger reconnect
                    if (errorMessage.contains("No proxy servers available")) {
                        logger.info("No proxy servers available, scheduling reconnect attempt");
                        lanternaUI.updateStatus("Will retry in 10 seconds...");

                        // Schedule a delayed reconnect
                        new Thread(() -> {
                            try {
                                Thread.sleep(10000); // Wait 10 seconds before retry
                                requestReconnect();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }, "reconnect-scheduler").start();
                    }
                    break;

                default:
                    // No special UI update for other message types
                    break;
            }
        }
    }

    @Override
    public void run() {
        try {
            startConnection();

            // Wait for disconnection instead of active polling
            final CountDownLatch connectionLatch = new CountDownLatch(1);

            // Register a disconnect listener
            networkManager.registerHandler(MessageType.DISCONNECT, message -> {
                logger.info("Received disconnect message from server");
                connectionLatch.countDown();
            });

            // Wait for disconnection
            try {
                connectionLatch.await();
                logger.info("Connection closed, exiting client run loop");
            } catch (Exception e) {
                logger.info("Client thread interrupted");
                Thread.currentThread().interrupt();

            }
        } catch (Exception e) {
            logger.error("Error in client run loop", e);
        } finally {
            shutdown();
        }
    }

    public void startConnection() {
        try {
            logger.info("Connecting to localization server...");
            networkManager.updateComponentName(clientId);
            sendMessage(MessageType.START_REQUEST, "Ask for connection");
        } catch (Exception e) {
            logger.error("Connection error", e);
            if (lanternaUI != null) {
                lanternaUI.showError("Connection error: " + e.getMessage());
                shutdown();
            }
        }
    }

    public void startAuth(String username, String password) {
        if (networkManager.isConnected()) {
            logger.info("Starting authentication with username: {}", username);
            String[] credentials = new String[] { username, password };
            sendMessage(MessageType.AUTH_REQUEST, credentials);
        } else {
            logger.error("Cannot authenticate - not connected to server");
            if (lanternaUI != null) {
                lanternaUI.showError("Not connected to server");
            }
        }
    }

    public void sendMessage(MessageType type, Object payload) {
        if (networkManager.isConnected()) {
            Message msg = new Message(type, clientId, "Server", payload);
            networkManager.sendMessage(msg);
        } else {
            logger.error("Cannot send message - connection is closed");
            if (lanternaUI != null) {
                lanternaUI.showError("Cannot send message - connection is closed");
            }
        }
    }

    public void sendDataRequest(String request) {
        sendMessage(MessageType.DATA_REQUEST, request);
    }

    public void sendLogoutRequest() {
        sendMessage(MessageType.LOGOUT_REQUEST, true);
    }

    /**
     * Attempt to reconnect to the localization server after a failed redirect
     */
    public void requestReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            logger.error("Maximum reconnect attempts ({}) reached", MAX_RECONNECT_ATTEMPTS);
            if (lanternaUI != null) {
                lanternaUI.showError("Failed to connect after " + MAX_RECONNECT_ATTEMPTS + " attempts");
                lanternaUI.showError("Shutting down client...");
            }
            shutdown();
        }

        reconnectAttempts++;
        logger.info("Requesting reconnection (attempt {}/{})", reconnectAttempts, MAX_RECONNECT_ATTEMPTS);

        if (lanternaUI != null) {
            lanternaUI.updateStatus("Requesting reconnection (attempt " + reconnectAttempts + ")");
        }

        // Try to connect to the localization server
        if (networkManager.connect("localhost", 11110)) { // Use the localization server address
            // Re-register message handlers for the new connection
            registerMessageHandlers();

            // Send RECONNECT message
            sendMessage(MessageType.RECONNECT, "Requesting new server");
        } else {
            logger.error("Failed to connect to localization server for reconnection");
            if (lanternaUI != null) {
                lanternaUI.showError("Failed to connect to localization server for reconnection");
            }
        }
    }

    /**
     * Modified redirect method with reconnect handling
     */
    public void redirect(String[] serverInfo) {
        if (serverInfo != null && serverInfo.length >= 2) {
            // Extract host and port from server info - ensure we're using correct indexes
            // Format should be [serverId, host, port]
            String host = serverInfo[0]; // Host is at index 1
            int port;

            try {
                port = Integer.parseInt(serverInfo[1]); // Port is at index 2
                logger.info("Redirecting to {}:{}", host, port);

                if (lanternaUI != null) {
                    lanternaUI.updateStatus("Redirecting to " + host + ":" + port);
                }

                // Disconnect from current server
                // networkManager.close();

                // Connect to the new server
                try {
                    if (!networkManager.connect(host, port)) {
                        logger.error("Failed to connect to redirected server");
                        if (lanternaUI != null) {
                            lanternaUI.showError("Failed to connect to redirected server");
                            lanternaUI.updateStatus("Attempting to reconnect...");
                        }
                        requestReconnect();

                    } else {
                        logger.info("Connected to redirected server {}:{}", host, port);
                        serverAddress = host + ":" + port;
                        reconnectAttempts = 0; // Reset reconnect attempts on success;
                        // Re-register message handlers for the new connection
                        registerMessageHandlers();

                        if (lanternaUI != null) {
                            lanternaUI.updateConnectionStatus("Connected to " + host + ":" + port, true);
                            lanternaUI.updateStatus("Connected to proxy server. Please log in.");

                            // Change ui to login screen
                            lanternaUI.showLoginScreen(null);

                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to connect to redirected server: {}", e.getMessage());
                    if (lanternaUI != null) {
                        lanternaUI.showError("Failed to connect to redirected server: " + e.getMessage());
                        lanternaUI.updateStatus("Attempting to reconnect...");
                    }

                    // Request reconnect on failure
                    requestReconnect();
                }

            } catch (NumberFormatException e) {
                logger.error("Invalid port number in server info: {}", serverInfo[2]);
                if (lanternaUI != null) {
                    lanternaUI.showError("Invalid port number received in redirect");
                }

                // Request reconnect on failure
                requestReconnect();
            }
        } else {
            logger.error("Invalid server info format - insufficient data");
            if (lanternaUI != null) {
                lanternaUI.showError("Invalid server information received");
            }

            // Request reconnect on failure
            requestReconnect();
        }
    }

    /**
     * Regular shutdown method - calls UI.shutdown() which calls us back
     */
    public void shutdown() {
        // Guard against recursive calls
        if (!shuttingDown.compareAndSet(false, true)) {
            logger.debug("Shutdown already in progress, ignoring call");
            return;
        }

        logger.info("Shutting down client...");

        try {
            // Close network resources
            closeNetworkResources();

            // Notify UI to shut down - but only if we weren't called BY the UI
            if (lanternaUI != null) {
                lanternaUI.shutdown(); // This will eventually end the process
            } else {
                // If no UI exists, just exit
                System.exit(0);
            }
        } catch (Exception e) {
            logger.error("Error during client shutdown", e);
            System.exit(1);
        }
    }

    /**
     * Special shutdown method called only by the UI to prevent infinite loop
     */
    public void shutdownWithoutUI() {
        // Guard against recursive calls
        if (!shuttingDown.compareAndSet(false, true)) {
            logger.debug("Shutdown already in progress, ignoring call");
            return;
        }

        logger.info("Shutting down client resources...");

        try {
            // Close only network resources, don't call back to UI
            closeNetworkResources();
        } catch (Exception e) {
            logger.error("Error closing network resources", e);
        }
    }

    /**
     * Helper to close network resources
     */
    private void closeNetworkResources() {
        logger.info("Closing socket transport");
        if (networkManager != null) {
            networkManager.close();
        }
        // Your existing network cleanup code
        logger.info("Network resources closed");
    }

    // Utility method to check if a message is for this client
    public boolean isMessageForThisClient(Message message) {
        return message != null && message.getRecipient() != null &&
                message.getRecipient().equals(clientId);
    }

    // Getters and setters
    public boolean isConnected() {
        return networkManager.isConnected();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public LanternaUI getLanternaUI() {
        return lanternaUI;
    }

    public void setLanternaUI(LanternaUI lanternaUI) {
        this.lanternaUI = lanternaUI;
    }

    public String getClientId() {
        return clientId;
    }

    public AtomicInteger getLoginTries() {
        return loginTries;
    }

    public Logger getLogger() {
        return logger;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    // Add UI notification methods
    public void notifyUIAuthenticated(boolean success) {
        if (lanternaUI != null) {
            String message = success ? "Login successful" : "Login failed (Attempt " + loginTries.get() + "/3)";
            lanternaUI.notifyAuthenticationResult(success, message);

            if (success) {
                lanternaUI.updateStatus("Login successful");
            } else {
                lanternaUI.updateStatus("Login failed");
            }
        }
    }

    // For testing purposes
    public boolean isUsingLanterna() {
        return useLanterna && lanternaUI != null;
    }
}
