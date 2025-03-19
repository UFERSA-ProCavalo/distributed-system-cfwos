package main.server.application;

import java.net.MalformedURLException;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import main.server.application.database.Database;
import main.server.application.replication.DatabaseService;
import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;
import main.shared.models.WorkOrder;

public class ApplicationServerHandler implements Runnable {
    private boolean connected = true;
    private final String clientId;
    private final ApplicationServer server;

    private final Socket clientSocket;
    private final Logger logger;
    private MessageBus messageBus;
    private SocketMessageTransport transport;
    private int RMI_PORT;
    private DatabaseService dbStub;

    // Singleton database instance - shared across all handlers
    private Database database;
    // Thread safety for database operations
    private static final Object databaseLock = new Object();

    public ApplicationServerHandler(Socket clientSocket, Logger logger, ApplicationServer server) {

        this.clientSocket = clientSocket;
        this.logger = logger;
        this.server = server;

        this.database = server.getDatabase();
        RMI_PORT = server.getRMI_PORT();

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
                    case "ADD100":
                        add100toDatabase(response);
                        break;
                    case "TESTE":
                        // handleTesteOperation(response);
                    default:
                        response.put("status", "error");
                        response.put("message", "Unknown operation: " + operation);
                }

                logger.info("[{}] Finished database operation", threadInfo);
            }

            // Create response message
            Message responseMsg = new Message(
                    MessageType.DATA_RESPONSE,
                    message.getRecipient(),
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
                        message.getRecipient(),
                        message.getSender(),
                        errorResponse);

                transport.sendMessage(errorMsg);
            } catch (Exception ex) {
                logger.error("Failed to send error response", ex);
            }
        }
    }

    private void add100toDatabase(Map<String, String> response) {
        for (int i = 1; i < 101; i++) {
            database.addWorkOrder(i, "WorkOrder " + i, "Description " + i);

        }
        response.put("status", "success");
        response.put("message", "100 work orders added successfully");

    }

    private void handleAddOperation(String[] requestParts, Map<String, String> response) {
        int errorCount = 0;
        List<String> errorMessages = new ArrayList<>(); // Store errors dynamically

        // Format: ADD|code|name|description|timestamp
        if (requestParts.length < 4) {
            errorMessages.add("ADD operation requires at least code, name, and description");
            errorCount++;
        }

        if (requestParts.length >= 4) { // Only check these if length is valid
            if (requestParts[1].isEmpty() || requestParts[2].isEmpty() || requestParts[3].isEmpty()) {
                errorMessages.add("Code, name, and description cannot be empty");
                errorCount++;
            }

            // Check if code is a positive integer
            if (!requestParts[1].matches("\\d+")) {
                errorMessages.add("Code must be a positive integer");
                errorCount++;
            } else {
                int code = Integer.parseInt(requestParts[1]);

                // Check if work order with the same code already exists
                if (database.searchWorkOrder(code) != null) {
                    errorMessages.add("Work order with code " + code + " already exists");
                    errorCount++;
                }

                if (code < 0) {
                    errorMessages.add("Code must be a positive integer");
                    errorCount++;
                }
            }
        }

        // If there are errors, stop processing and return errors
        if (errorCount > 0) {
            response.put("status", "error");
            response.put("message", String.valueOf(errorCount) + " errors found\n" +
                    String.join(";\n ", errorMessages));
            return;
        }

        // No errors, process the work order
        int code = Integer.parseInt(requestParts[1]);
        String name = requestParts[2];
        String description = requestParts[3];

        if (requestParts.length == 5) {
            String timestamp = requestParts[4];
            database.addWorkOrder(code, name, description, timestamp);
        } else {
            database.addWorkOrder(code, name, description);
        }

        // replicate add work order
        try {
            server.replicateAddWorkOrder(code, name, description);
        } catch (Exception e) {
            logger.error("Error replicating add work order", e);
        }

        response.put("status", "success");
        response.put("message", "Work order added successfully");
        response.put("code", String.valueOf(code));
        response.put("name", name);
        response.put("description", description);
        response.put("timestamp", database.searchWorkOrder(code).getTimestamp());
    }

    private void handleRemoveOperation(String[] requestParts, Map<String, String> response) {
        // Format: REMOVE|code
        if (requestParts.length < 2) {
            throw new IllegalArgumentException("REMOVE operation requires a code");
        }

        int code = Integer.parseInt(requestParts[1]);

        if (database.searchWorkOrder(code) == null) {
            response.put("status", "error");
            response.put("message", "Work order with code " + code + " not found!");
            return;
        }

        // replicate remove

        WorkOrder removedOrder = database.searchWorkOrder(code);

        response.put("status", "success");
        response.put("message", "Work order removed successfully");
        response.put("code", String.valueOf(code));
        response.put("name", removedOrder.getName());
        response.put("description", removedOrder.getDescription());
        response.put("timestamp", removedOrder.getTimestamp());

        database.removeWorkOrder(code);
    }

    private void handleUpdateOperation(String[] requestParts, Map<String, String> response) {
        // Format: UPDATE|code|name|description
        if (requestParts.length < 4) {
            throw new IllegalArgumentException("UPDATE operation requires code, name, description");
        }

        int code = Integer.parseInt(requestParts[1]);

        if (database.searchWorkOrder(code) == null) {
            response.put("status", "error");
            response.put("message", "Work order with code " + code + " not found!");
            return;

        }

        String name = requestParts[2];
        String description = requestParts[3];

        database.updateWorkOrder(code, name, description);

        // replicate update work order
        try {
            server.replicateUpdateWorkOrder(code, name, description);
        } catch (Exception e) {
            logger.error("Error replicating update work order", e);
        }

        response.put("status", "success");
        response.put("message", "Work order updated successfully");
        response.put("code", String.valueOf(code));
        response.put("name", name);
        response.put("description", description);
        response.put("timestamp", database.searchWorkOrder(code).getTimestamp());

        
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
            response.put("message", "Work order found");
            response.put("code", String.valueOf(workOrder.getCode()));
            response.put("name", workOrder.getName());
            response.put("description", workOrder.getDescription());
            response.put("timestamp", workOrder.getTimestamp());
        } else {
            response.put("status", "error");
            response.put("message", "Work order not found");
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

        // Obter representação em string do banco de dados
        String databaseContent;

        if (reverse) {
            databaseContent = database.getDatabaseContentReverse();
            response.put("message", "Database content in reverse order");
        } else {
            databaseContent = database.getDatabaseContent();
            response.put("message", "Database content in order");
        }

        response.put("status", "success");
        response.put("database_content", databaseContent);

        // Ainda mantém o registro no console do servidor
        if (reverse) {
            database.showDatabaseReverse();
        } else {
            database.showDatabase();
        }
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