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
    private String server = "AppServer";

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

            // Simplify the loop condition
            while (transport.isRunning() && !clientSocket.isClosed()) {
                if (!transport.readMessage()) {
                    break; // Exit loop instead of using connected flag
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
        // Use only one lock
        synchronized (databaseLock) {
            try {
                logger.info("Handling DATA_REQUEST message from {}: {}", message.getSender(), message.getPayload());

                Map<String, String> response = new HashMap<>();
                String operation = null;

                // Get the thread name/id for debugging
                String threadInfo = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();
                logger.info("[{}] Requesting database lock", threadInfo);

                // Log the time it takes to acquire the lock
                long startLock = System.currentTimeMillis();

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
                        response = handleSearchOperation(requestParts);
                        break;
                    case "STATS":
                        handleStatsOperation(response);
                        break;
                    case "SHOW":
                        handleShowOperation(requestParts, response);
                        break;
                    case "ADD60":
                        add60toDatabase(response);
                        break;
                    case "TESTE":
                        // handleTesteOperation(response);
                    default:
                        response.put("status", "error");
                        response.put("message", "Unknown operation: " + operation);
                }

                logger.info("[{}] Finished database operation", threadInfo);

                // Create response message
                Message responseMsg = new Message(
                        MessageType.DATA_RESPONSE,
                        message.getRecipient(),
                        message.getSender(),
                        response);

                transport.sendMessage(responseMsg);
                logger.info("Sent data response to client for operation: {}", operation);
            } catch (Exception e) {
                logger.error("Error handling data request", e);
                sendErrorResponse(message, "Error processing request: " + e.getMessage());
            }
        }
    }

    private void handleAddOperation(String[] requestParts, Map<String, String> response) {
        // Format: ADD|code|name|description|timestamp
        if (requestParts.length < 4) {
            response.put("status", "error");
            response.put("message", "ADD operation requires at least code, name, and description");
            return;
        }

        Integer code = parseCodeParam(requestParts[1], response);
        if (code == null) {
            return;
        }

        // Verificar se o código já existe
        WorkOrder existing = database.searchWorkOrder(code);
        if (existing != null) {
            response.put("status", "error");
            response.put("message", "Work order with code " + code + " already exists");
            return;
        }

        String name = requestParts[2];
        if (name == null || name.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Name cannot be empty");
            return;
        }

        String description = requestParts[3];
        if (description == null || description.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Description cannot be empty");
            return;
        }

        if (requestParts.length >= 5) {
            String timestamp = requestParts[4];
            if (timestamp == null || timestamp.trim().isEmpty() || !isValidTimestamp(timestamp)) {
                response.put("status", "error");
                response.put("message", "Invalid timestamp format. Use YYYY-MM-DD");
                return;
            }
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
            response.put("status", "error");
            response.put("message", "REMOVE operation requires a code");
            return;
        }

        Integer code = parseCodeParam(requestParts[1], response);
        if (code == null) {
            return;
        }

        // Verificar se o item existe antes de remover
        WorkOrder existingOrder = database.searchWorkOrder(code);
        if (existingOrder == null) {
            response.put("status", "error");
            response.put("message", "Work order with code " + code + " not found");
            return;
        }

        database.removeWorkOrder(code);

        response.put("status", "success");
        response.put("message", "Work order removed successfully");
        response.put("code", String.valueOf(code));
    }

    private void handleUpdateOperation(String[] requestParts, Map<String, String> response) {
        // Format: UPDATE|code|name|description|timestamp
        if (requestParts.length < 5) {
            response.put("status", "error");
            response.put("message", "UPDATE operation requires code, name, description, and timestamp");
            return;
        }

        Integer code = parseCodeParam(requestParts[1], response);
        if (code == null) {
            return;
        }

        // Verificar se o item existe antes de atualizar
        WorkOrder existingOrder = database.searchWorkOrder(code);
        if (existingOrder == null) {
            response.put("status", "error");
            response.put("message", "Work order with code " + code + " not found");
            return;
        }

        String name = requestParts[2];
        if (name == null || name.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Name cannot be empty");
            return;
        }

        String description = requestParts[3];
        if (description == null || description.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Description cannot be empty");
            return;
        }

        String timestamp = requestParts[4];
        if (timestamp == null || timestamp.trim().isEmpty() || !isValidTimestamp(timestamp)) {
            response.put("status", "error");
            response.put("message", "Invalid timestamp format. Use YYYY-MM-DD");
            return;
        }

        database.updateWorkOrder(code, name, description, timestamp);

        response.put("status", "success");
        response.put("message", "Work order updated successfully");
        response.put("code", String.valueOf(code));
    }

    private Map<String, String> handleSearchOperation(String[] requestParts) {
        Map<String, String> response = new HashMap<>();
        // Format: SEARCH|code
        if (requestParts.length < 2) {
            response.put("status", "error");
            response.put("message", "SEARCH operation requires a code");
            return response;
        }

        Integer code = parseCodeParam(requestParts[1], response);
        if (code == null) {
            return response;
        }

        WorkOrder order = database.searchWorkOrder(code);
        if (order == null) {
            response.put("status", "error");
            response.put("message", "Work order with code " + code + " not found");
            return response;
        }

        response.put("status", "success");
        response.put("message", "Work order found");
        response.put("code", String.valueOf(order.getCode()));
        response.put("name", order.getName());
        response.put("description", order.getDescription());
        response.put("timestamp", order.getTimestamp());

        return response;
    }

    // Método auxiliar para validar formato de data
    private boolean isValidTimestamp(String timestamp) {
        // Simple regex check for YYYY-MM-DD format
        return timestamp.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    // Também precisamos modificar o add60toDatabase para evitar conflitos
    private void add60toDatabase(Map<String, String> response) {
        int successCount = 0;
        int offset = 0; // Tentaremos adicionar começando do código 0

        // Tentar adicionar 60 work orders
        while (successCount < 60) {
            if (database.searchWorkOrder(offset) == null) {
                // Código disponível, adicionar
                database.addWorkOrder(offset, "name" + offset, "description" + offset);
                successCount++;
            }
            offset++;
        }

        response.put("status", "success");
        response.put("message", "60 work orders added successfully");
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

    private void sendErrorResponse(Message requestMessage, String errorMessage) {
        try {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", errorMessage);

            Message errorMsg = new Message(
                    MessageType.DATA_RESPONSE,
                    requestMessage.getRecipient(),
                    requestMessage.getSender(),
                    errorResponse);

            transport.sendMessage(errorMsg);
        } catch (Exception ex) {
            logger.error("Failed to send error response", ex);
        }
    }

    private Integer parseCodeParam(String codeStr, Map<String, String> response) {
        try {
            int code = Integer.parseInt(codeStr);
            if (code < 0) {
                response.put("status", "error");
                response.put("message", "Code must be a positive integer");
                return null;
            }
            return code;
        } catch (NumberFormatException e) {
            response.put("status", "error");
            response.put("message", "Invalid code format: " + codeStr + " is not a valid integer");
            return null;
        }
    }
}
