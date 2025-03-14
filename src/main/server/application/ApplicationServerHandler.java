package main.server.application;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import main.server.application.database.Database;
import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;
import main.shared.models.WorkOrder;

public class ApplicationServerHandler implements Runnable {
    private boolean connected = true;
    private final String clientId;

    private final Socket clientSocket;
    private final Logger logger;
    private MessageBus messageBus;
    private SocketMessageTransport transport;

    // Singleton database instance - shared across all handlers
    private static final Database database;
    // Thread safety for database operations
    private static final Object databaseLock = new Object();

    // Initialize database
    static {
        database = new Database();
    }

    public ApplicationServerHandler(Socket clientSocket, Logger logger) {
        this.clientSocket = clientSocket;
        this.logger = logger;

        this.clientId = "AppServer-"
                + clientSocket.getInetAddress().getHostAddress()
                + ":"
                + clientSocket.getPort();

        logger.info("New application client connected: {}:{}",
                clientSocket.getInetAddress().getHostAddress(),
                clientSocket.getPort());
    }

    @Override
    public void run() {
        try {
            setupMessageTransport();
            logger.info("Application handler ready for client requests...");

            // Main message processing loop
            while (connected && transport.isRunning() && !clientSocket.isClosed()) {
                boolean messageProcessed = transport.readMessage();

                if (!messageProcessed) {
                    connected = false;
                }
            }

            logger.info("Client disconnected from application server");
        } catch (Exception e) {
            logger.error("Error in application client handler", e);
        } finally {
            cleanup();
        }
    }

    private void setupMessageTransport() {
        String serverComponent = "AppServer-"
                + clientSocket.getInetAddress().getHostAddress()
                + ":"
                + clientSocket.getLocalPort();

        messageBus = new MessageBus(serverComponent, logger);
        transport = new SocketMessageTransport(clientSocket, messageBus, logger, true);

        try {
            // Subscribe only to DATA_REQUEST messages
            messageBus.subscribe(MessageType.DATA_REQUEST, this::handleDataRequest);
        } catch (Exception e) {
            logger.error("Error in message transport setup", e);
        }
    }

    private void handleDataRequest(Message message) {
        try {
            logger.info("Handling DATA_REQUEST message from {}: {}", message.getSender(), message.getPayload());

            Map<String, String> response = new HashMap<>();
            String operation = null;

            // Get the thread name/id for debugging
            String threadInfo = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();
            logger.info("[{}] Requesting database lock", threadInfo);

            // Log the time it takes to acquire the lock
            long startLock = System.currentTimeMillis();

            synchronized (databaseLock) {
                long lockTime = System.currentTimeMillis() - startLock;
                logger.info("[{}] Acquired database lock after {}ms", threadInfo, lockTime);

                if (message.getPayload() == null) {
                    throw new IllegalArgumentException("Request payload cannot be null");
                }

                String[] requestParts = message.getPayload().toString().split("\\|");
                operation = requestParts[0].toUpperCase();

                // Process the data request using the database
                switch (operation) {
                    case "ADD":
                        handleAddOperation(requestParts, response);
                        break;
                    case "REMOVE":
                        handleRemoveOperation(requestParts, response);
                        break;
                    case "UPDATE":
                        handleUpdateOperation(requestParts, response);
                        break;
                    case "SEARCH":
                        handleSearchOperation(requestParts, response);
                        break;
                    case "STATS":
                        handleStatsOperation(response);
                        break;
                    case "SHOW":
                        handleShowOperation(requestParts, response);
                        break;
                    default:
                        response.put("status", "error");
                        response.put("message", "Unknown operation: " + operation);
                }

                logger.info("[{}] Finished database operation", threadInfo);
            }

            // Create response message
            Message responseMsg = new Message(
                    MessageType.DATA_RESPONSE,
                    messageBus.getComponentName(),
                    message.getSender(),
                    response);

            transport.sendMessage(responseMsg);
            logger.info("Sent data response to client for operation: {}", operation);

        } catch (Exception e) {
            logger.error("Error handling DATA_REQUEST", e);

            // Send error message back to client
            try {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", e.getMessage());

                Message errorMsg = new Message(
                        MessageType.DATA_RESPONSE,
                        messageBus.getComponentName(),
                        message.getSender(),
                        errorResponse);

                transport.sendMessage(errorMsg);
            } catch (Exception ex) {
                logger.error("Failed to send error response", ex);
            }
        }
    }

    private void handleAddOperation(String[] requestParts, Map<String, String> response) {
        // Format: ADD|code|name|description|timestamp
        if (requestParts.length < 4) {
            throw new IllegalArgumentException("ADD operation requires at least code, name, and description");
        }

        int code = Integer.parseInt(requestParts[1]);
        String name = requestParts[2];
        String description = requestParts[3];

        if (requestParts.length >= 5) {
            String timestamp = requestParts[4];
            database.addWorkOrder(code, name, description, timestamp);
        } else {
            database.addWorkOrder(code, name, description);
        }

        response.put("status", "success");
        response.put("message", "Work order added successfully");
        response.put("code", String.valueOf(code));
    }

    private void handleRemoveOperation(String[] requestParts, Map<String, String> response) {
        // Format: REMOVE|code
        if (requestParts.length < 2) {
            throw new IllegalArgumentException("REMOVE operation requires a code");
        }

        int code = Integer.parseInt(requestParts[1]);
        database.removeWorkOrder(code);

        response.put("status", "success");
        response.put("message", "Work order removed successfully");
        response.put("code", String.valueOf(code));
    }

    private void handleUpdateOperation(String[] requestParts, Map<String, String> response) {
        // Format: UPDATE|code|name|description|timestamp
        if (requestParts.length < 5) {
            throw new IllegalArgumentException("UPDATE operation requires code, name, description, and timestamp");
        }

        int code = Integer.parseInt(requestParts[1]);
        String name = requestParts[2];
        String description = requestParts[3];
        String timestamp = requestParts[4];

        database.updateWorkOrder(code, name, description, timestamp);

        response.put("status", "success");
        response.put("message", "Work order updated successfully");
        response.put("code", String.valueOf(code));
    }

    private void handleSearchOperation(String[] requestParts, Map<String, String> response) {
        // Format: SEARCH|code
        if (requestParts.length < 2) {
            throw new IllegalArgumentException("SEARCH operation requires a code");
        }

        int code = Integer.parseInt(requestParts[1]);
        WorkOrder workOrder = database.searchWorkOrder(code);

        if (workOrder != null) {
            response.put("status", "success");
            response.put("code", String.valueOf(workOrder.getCode()));
            response.put("name", workOrder.getName());
            response.put("description", workOrder.getDescription());
            response.put("timestamp", workOrder.getTimestamp());
        } else {
            response.put("status", "not_found");
            response.put("message", "Work order not found");
            response.put("code", String.valueOf(code));
        }
    }

    private void handleStatsOperation(Map<String, String> response) {
        // Format: STATS
        response.put("status", "success");
        response.put("size", String.valueOf(database.getSize()));
        response.put("height", String.valueOf(database.getTreeHeight()));
        response.put("balanceCounter", String.valueOf(database.getBalanceCounter()));
    }

    private void handleShowOperation(String[] requestParts, Map<String, String> response) {
        // Format: SHOW|[REVERSE]
        boolean reverse = requestParts.length > 1 && "REVERSE".equalsIgnoreCase(requestParts[1]);

        // Since database.show methods print to console, we can't capture output
        // directly
        // Instead, we'll just confirm the operation was performed
        if (reverse) {
            database.showDatabaseReverse();
            response.put("message", "Database shown in reverse order (check server console)");
        } else {
            database.showDatabase();
            response.put("message", "Database shown in order (check server console)");
        }

        response.put("status", "success");
    }

    private void cleanup() {
        try {
            // Unsubscribe to prevent more callbacks
            messageBus.unsubscribe(MessageType.DATA_REQUEST, this::handleDataRequest);

            // Close transport
            if (transport != null) {
                transport.close();
            }

            // Update connection count
            ApplicationServer.decrementActiveConnections();

            logger.info("Handler cleanup completed for client {}", clientId);
        } catch (Exception e) {
            logger.error("Error during handler cleanup", e);
        }
    }
}
