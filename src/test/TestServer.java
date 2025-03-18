package test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;
import main.shared.models.WorkOrder;

/**
 * Test server that implements thread pool architecture for testing client
 * connections.
 * This server simulates the behavior of the real server for testing purposes.
 */
public class TestServer implements Runnable {
    private final int port;
    private final Logger logger;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    // Thread pools
    private final ExecutorService connectionAcceptorPool;
    private final ExecutorService clientHandlerPool;

    // Client tracking
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);

    // Simple in-memory database for testing
    private final Map<Integer, WorkOrder> workOrderDb = new ConcurrentHashMap<>();

    /**
     * Creates a new test server listening on the specified port
     */
    public TestServer(int port) {
        this.port = port;
        this.logger = Logger.getLogger();

        // Single thread for accepting connections
        this.connectionAcceptorPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "server-acceptor");
            t.setDaemon(true);
            return t;
        });

        // Thread pool for handling client connections
        this.clientHandlerPool = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                r -> {
                    Thread t = new Thread(r, "client-handler");
                    t.setDaemon(true);
                    return t;
                });

        // Initialize with some test data
        initializeTestData();
    }

    /**
     * Add some sample data to the test database
     */
    private void initializeTestData() {
        for (int i = 1; i <= 5; i++) {
            workOrderDb.put(i, new WorkOrder(i,
                    "Sample Order " + i,
                    "This is a test work order " + i,
                    "2025-03-15"));
        }
        logger.info("Initialized test database with {} sample work orders", workOrderDb.size());
    }

    @Override
    public void run() {
        try {
            // Start the server socket
            serverSocket = new ServerSocket(port);
            logger.info("Test server started on port {}", port);

            // Accept connections in a separate thread
            connectionAcceptorPool.submit(this::acceptConnections);

            // Keep the main thread alive until shutdown
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Error starting test server", e);
        } finally {
            shutdown();
        }
    }

    /**
     * Continuously accept new client connections
     */
    private void acceptConnections() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientId = "Client-" + nextClientId.getAndIncrement();

                logger.info("New client connected: {} from {}",
                        clientId, clientSocket.getRemoteSocketAddress());

                // Create and start a handler for this client
                ClientHandler handler = new ClientHandler(clientSocket, clientId);
                connectedClients.put(clientId, handler);

                clientHandlerPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting client connection", e);
                }
            }
        }
    }

    /**
     * Shutdown the server and all resources
     */
    public void shutdown() {
        if (!running)
            return;

        running = false;
        logger.info("Shutting down test server...");

        // Close all client connections
        for (ClientHandler handler : connectedClients.values()) {
            handler.close();
        }
        connectedClients.clear();

        // Close the server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }

        // Shutdown thread pools
        shutdownThreadPool(connectionAcceptorPool, "Connection Acceptor");
        shutdownThreadPool(clientHandlerPool, "Client Handler");

        logger.info("Test server shutdown complete");
    }

    private void shutdownThreadPool(ExecutorService pool, String poolName) {
        try {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("{} thread pool did not terminate in time, forcing shutdown", poolName);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("{} thread pool shutdown interrupted", poolName);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Handler for each client connection
     */
    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final String clientId;
        private SocketMessageTransport transport;
        private MessageBus messageBus;
        private volatile boolean connected = true;
        private boolean authenticated = false;
        private String username;

        public ClientHandler(Socket socket, String clientId) {
            this.clientSocket = socket;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            try {
                setupCommunication();

                // Keep the handler alive while connected
                while (connected && !clientSocket.isClosed()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Error in client handler", e);
            } finally {
                close();
            }
        }

        /**
         * Set up message transport and handlers
         */
        private void setupCommunication() {
            try {
                // Initialize message transport
                String handlerId = "Server-Handler-" + clientId;
                messageBus = new MessageBus(handlerId, logger);
                transport = new SocketMessageTransport(clientSocket, messageBus, logger, true);

                // Register message handlers
                registerMessageHandlers();

                logger.info("Communication setup complete for client {}", clientId);
            } catch (Exception e) {
                logger.error("Failed to set up communication for client {}", clientId, e);
                connected = false;
            }
        }

        /**
         * Register handlers for all message types
         */
        private void registerMessageHandlers() {
            messageBus.subscribe(MessageType.AUTH_REQUEST, this::handleAuthRequest);
            messageBus.subscribe(MessageType.DATA_REQUEST, this::handleDataRequest);
            messageBus.subscribe(MessageType.LOGOUT_REQUEST, this::handleLogoutRequest);
            messageBus.subscribe(MessageType.DISCONNECT, this::handleDisconnect);
        }

        /**
         * Handle authentication requests
         */
        private void handleAuthRequest(Message message) {
            logger.info("Received AUTH_REQUEST from {}", message.getSender());

            try {
                boolean authSuccess = false;

                if (message.getPayload() instanceof String[]) {
                    String[] credentials = (String[]) message.getPayload();

                    if (credentials.length == 2) {
                        // Simple authentication logic
                        if (credentials[0].equals("admin") && credentials[1].equals("admin123")) {
                            authSuccess = true;
                            authenticated = true;
                            username = credentials[0];
                            logger.info("Authentication successful for user: {}", username);
                        } else {
                            logger.info("Authentication failed for user: {}", credentials[0]);
                        }
                    }
                }

                // Send auth response
                Message response = new Message(
                        MessageType.AUTH_RESPONSE,
                        messageBus.getComponentName(),
                        message.getSender(),
                        authSuccess ? "success" : "failure");
                transport.sendMessage(response);

            } catch (Exception e) {
                logger.error("Error handling AUTH_REQUEST", e);
            }
        }

        /**
         * Handle data requests
         */
        private void handleDataRequest(Message message) {
            logger.info("Received DATA_REQUEST from {}: {}", message.getSender(), message.getPayload());

            // Only process if authenticated
            if (!authenticated) {
                sendErrorResponse(message, "Not authenticated");
                return;
            }

            try {
                if (message.getPayload() instanceof String) {
                    String requestStr = (String) message.getPayload();
                    String[] requestParts = requestStr.split("\\|");

                    if (requestParts.length > 0) {
                        String operation = requestParts[0].toUpperCase();
                        Map<String, String> response = new HashMap<>();

                        switch (operation) {
                            case "ADD":
                                handleAddOperation(requestParts, response);
                                break;
                            case "SEARCH":
                                handleSearchOperation(requestParts, response);
                                break;
                            case "REMOVE":
                                handleRemoveOperation(requestParts, response);
                                break;
                            case "UPDATE":
                                handleUpdateOperation(requestParts, response);
                                break;
                            case "SHOW":
                                handleShowOperation(response);
                                break;
                            case "STATS":
                                handleStatsOperation(response);
                                break;
                            case "ADD20":
                                handleAdd20Operation(response);
                                break;
                            case "ADD60":
                                handleAdd60Operation(response);
                                break;
                            default:
                                response.put("status", "error");
                                response.put("message", "Unknown operation: " + operation);
                        }

                        sendDataResponse(message, response);
                    } else {
                        sendErrorResponse(message, "Invalid request format");
                    }
                } else {
                    sendErrorResponse(message, "Invalid request payload type");
                }
            } catch (Exception e) {
                logger.error("Error handling DATA_REQUEST", e);
                sendErrorResponse(message, "Server error: " + e.getMessage());
            }
        }

        /**
         * Handle logout requests
         */
        private void handleLogoutRequest(Message message) {
            logger.info("Received LOGOUT_REQUEST from {}", message.getSender());

            try {
                authenticated = false;
                username = null;

                Message response = new Message(
                        MessageType.LOGOUT_RESPONSE,
                        messageBus.getComponentName(),
                        message.getSender(),
                        true);
                transport.sendMessage(response);

            } catch (Exception e) {
                logger.error("Error handling LOGOUT_REQUEST", e);
            }
        }

        /**
         * Handle disconnect requests
         */
        private void handleDisconnect(Message message) {
            logger.info("Received DISCONNECT from {}", message.getSender());
            close();
        }

        /**
         * Close this client handler
         */
        public void close() {
            if (connected) {
                connected = false;

                try {
                    if (messageBus != null) {
                        messageBus.unsubscribeAll();
                    }

                    if (transport != null) {
                        transport.close();
                    }

                    if (clientSocket != null && !clientSocket.isClosed()) {
                        clientSocket.close();
                    }

                    connectedClients.remove(clientId);
                    logger.info("Client handler closed for {}", clientId);
                } catch (Exception e) {
                    logger.error("Error closing client handler", e);
                }
            }
        }

        /**
         * Send an error response
         */
        private void sendErrorResponse(Message requestMessage, String errorMsg) {
            try {
                Message response = new Message(
                        MessageType.ERROR,
                        messageBus.getComponentName(),
                        requestMessage.getSender(),
                        errorMsg);
                transport.sendMessage(response);
            } catch (Exception e) {
                logger.error("Error sending error response", e);
            }
        }

        /**
         * Send a data response
         */
        private void sendDataResponse(Message requestMessage, Map<String, String> responseData) {
            try {
                Message response = new Message(
                        MessageType.DATA_RESPONSE,
                        messageBus.getComponentName(),
                        requestMessage.getSender(),
                        responseData);
                transport.sendMessage(response);
            } catch (Exception e) {
                logger.error("Error sending data response", e);
            }
        }

        // Operation handlers

        private void handleAddOperation(String[] requestParts, Map<String, String> response) {
            // Format: ADD|code|name|description
            if (requestParts.length < 4) {
                response.put("status", "error");
                response.put("message", "ADD operation requires at least code, name, and description");
                return;
            }

            try {
                int code = Integer.parseInt(requestParts[1]);
                String name = requestParts[2];
                String description = requestParts[3];
                String timestamp = requestParts.length >= 5 ? requestParts[4] : "2025-03-15";

                // Check if already exists
                if (workOrderDb.containsKey(code)) {
                    response.put("status", "error");
                    response.put("message", "Work order with code " + code + " already exists");
                    return;
                }

                // Add to database
                workOrderDb.put(code, new WorkOrder(code, name, description, timestamp));

                response.put("status", "success");
                response.put("message", "Work order added successfully");
                response.put("code", String.valueOf(code));
            } catch (NumberFormatException e) {
                response.put("status", "error");
                response.put("message", "Invalid code format: " + requestParts[1]);
            }
        }

        private void handleSearchOperation(String[] requestParts, Map<String, String> response) {
            // Format: SEARCH|code
            if (requestParts.length < 2) {
                response.put("status", "error");
                response.put("message", "SEARCH operation requires a code");
                return;
            }

            try {
                int code = Integer.parseInt(requestParts[1]);
                WorkOrder workOrder = workOrderDb.get(code);

                if (workOrder != null) {
                    response.put("status", "success");
                    response.put("code", String.valueOf(workOrder.getCode()));
                    response.put("name", workOrder.getName());
                    response.put("description", workOrder.getDescription());
                    response.put("timestamp", workOrder.getTimestamp());
                } else {
                    response.put("status", "error");
                    response.put("message", "Work order not found");
                }
            } catch (NumberFormatException e) {
                response.put("status", "error");
                response.put("message", "Invalid code format: " + requestParts[1]);
            }
        }

        private void handleRemoveOperation(String[] requestParts, Map<String, String> response) {
            // Format: REMOVE|code
            if (requestParts.length < 2) {
                response.put("status", "error");
                response.put("message", "REMOVE operation requires a code");
                return;
            }

            try {
                int code = Integer.parseInt(requestParts[1]);
                WorkOrder removed = workOrderDb.remove(code);

                if (removed != null) {
                    response.put("status", "success");
                    response.put("message", "Work order removed successfully");
                    response.put("code", String.valueOf(code));
                } else {
                    response.put("status", "error");
                    response.put("message", "Work order not found");
                }
            } catch (NumberFormatException e) {
                response.put("status", "error");
                response.put("message", "Invalid code format: " + requestParts[1]);
            }
        }

        private void handleUpdateOperation(String[] requestParts, Map<String, String> response) {
            // Format: UPDATE|code|name|description|timestamp
            if (requestParts.length < 5) {
                response.put("status", "error");
                response.put("message", "UPDATE operation requires code, name, description, and timestamp");
                return;
            }

            try {
                int code = Integer.parseInt(requestParts[1]);
                String name = requestParts[2];
                String description = requestParts[3];
                String timestamp = requestParts[4];

                if (!workOrderDb.containsKey(code)) {
                    response.put("status", "error");
                    response.put("message", "Work order not found");
                    return;
                }

                // Update in database
                workOrderDb.put(code, new WorkOrder(code, name, description, timestamp));

                response.put("status", "success");
                response.put("message", "Work order updated successfully");
                response.put("code", String.valueOf(code));
            } catch (NumberFormatException e) {
                response.put("status", "error");
                response.put("message", "Invalid code format: " + requestParts[1]);
            }
        }

        private void handleShowOperation(Map<String, String> response) {
            StringBuilder sb = new StringBuilder();

            if (workOrderDb.isEmpty()) {
                sb.append("No work orders found");
            } else {
                for (WorkOrder wo : workOrderDb.values()) {
                    sb.append("Code: ").append(wo.getCode())
                            .append(", Name: ").append(wo.getName())
                            .append(", Description: ").append(wo.getDescription())
                            .append(", Timestamp: ").append(wo.getTimestamp())
                            .append("\n");
                }
            }

            response.put("status", "success");
            response.put("message", "Work orders retrieved successfully");
            response.put("workOrders", sb.toString());
        }

        private void handleStatsOperation(Map<String, String> response) {
            response.put("status", "success");
            response.put("message", "Database statistics retrieved successfully");
            response.put("totalOrders", String.valueOf(workOrderDb.size()));
            response.put("memoryUsage", Runtime.getRuntime().totalMemory() / (1024 * 1024) + " MB");
        }

        private void handleAdd20Operation(Map<String, String> response) {
            int baseCode = workOrderDb.size() + 100;

            // Add 20 work orders
            for (int i = 0; i < 20; i++) {
                int code = baseCode + i;
                workOrderDb.put(code, new WorkOrder(code,
                        "Cache Order " + code,
                        "This is a cached work order " + code,
                        "2025-03-15"));
            }

            response.put("status", "success");
            response.put("message", "Added 20 work orders to cache");
        }

        private void handleAdd60Operation(Map<String, String> response) {
            int baseCode = workOrderDb.size() + 200;

            // Add 60 work orders
            for (int i = 0; i < 60; i++) {
                int code = baseCode + i;
                workOrderDb.put(code, new WorkOrder(code,
                        "Bulk Order " + code,
                        "This is a bulk inserted work order " + code,
                        "2025-03-15"));
            }

            response.put("status", "success");
            response.put("message", "Added 60 work orders to database");
        }
    }


    /**
     * Run the server with the specified port
     */
    public static void main(String[] args) {
        int port = 11110; // Default port

        // Allow port override from command line
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + port);
            }
        }

        TestServer server = new TestServer(port);
        new Thread(server, "test-server-main").start();

        // Add shutdown hook for clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down test server...");
            server.shutdown();
        }));

        System.out.println("Test server started on port " + port);
        System.out.println("Press Ctrl+C to stop the server");
    }
}
