package main.server.proxy;

import java.net.Socket;
import java.util.Map;
import java.util.Optional;

import main.server.proxy.auth.AuthService;
import main.server.proxy.cache.CacheFIFO;
import main.shared.log.Logger;
import main.shared.messages.*;
import main.shared.models.WorkOrder;
import main.shared.utils.MapUtil;
import main.shared.utils.TypeUtil;

import java.util.HashMap;

public class ProxyServerHandler implements Runnable {

    // Detalhes do Proxy
    private static final Object lock = new Object();
    private int loginTries = 0;
    private Socket clientSocket;
    private AuthService authService;
    private Logger logger;
    private CacheFIFO<WorkOrder> cache;

    // Detalhes do cliente
    private MessageBus clientMessageBus;
    private SocketMessageTransport clientTransport;
    private boolean connected = true;
    private boolean authenticated = true;

    // Detalhes do servidor de aplicação
    private static final String APP_SERVER_HOST = "localhost";
    private static final int APP_SERVER_PORT = 33330;
    private Socket applicationSocket;
    private MessageBus applicationMessageBus;
    private SocketMessageTransport applicationTransport;

    public ProxyServerHandler(Socket client, AuthService authService, Logger logger,
            CacheFIFO<WorkOrder> workOrderCache) {
        this.clientSocket = client;
        this.authService = authService;
        this.logger = logger;
        this.cache = workOrderCache;

        logger.info("New client handler created. ConnectionCount: {}, ActiveConnections: {}",
                ProxyServer.connectionCount, ProxyServer.activeConnections);

        // Setup client message bus and transport
        synchronized (lock) {
            // connectToApplicationServer();
            setupClientMessageTransport();
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted() && clientSocket != null) {
                // Check if the socket is closed
                if (clientSocket.isClosed()) {
                    logger.info("Client socket is closed, exiting handler thread.");
                    break;
                }
                // Wait for client authentication
                boolean messagedProcessed = clientTransport.readMessage();
                if (!messagedProcessed) {
                    logger.error("Failed to process authentication message");
                    throw new Exception("Failed to process authentication message");
                }

                // If authenticated, keep handler alive as long as the client is connected
                if (authenticated) {
                    if (applicationTransport == null) {
                        connectToApplicationServer();

                    }
                    while (connected && !clientSocket.isClosed()) {
                        // Process messages between client and application server
                        boolean clientMessageProcessed = clientTransport.readMessage();

                        if (!clientMessageProcessed) {
                            connected = false;
                            break;
                        }

                        if (applicationSocket != null && !applicationSocket.isClosed()) {
                            boolean appMessageProcessed = applicationTransport.readMessage();
                            if (!appMessageProcessed) {
                                logger.warning("Lost connection to application server");
                                // Try to reconnect or handle gracefully
                            }
                        }

                        // Small pause to prevent CPU hogging
                        // Thread.sleep(20);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in client handler", e);
        } finally {
            cleanup();
        }
    }

    private void setupClientMessageTransport() {
        synchronized (lock) {
            try {
                String componentName = "Server-"
                        + clientSocket.getInetAddress().getHostAddress()
                        + ":"
                        + clientSocket.getLocalPort();

                clientMessageBus = new MessageBus(componentName, logger);
                clientTransport = new SocketMessageTransport(clientSocket, clientMessageBus, logger, true);

                clientMessageBus.subscribe(MessageType.AUTH_REQUEST, this::handleAuthRequest);
                clientMessageBus.subscribe(MessageType.DATA_REQUEST, this::handleDataRequest);
                clientMessageBus.subscribe(MessageType.DISCONNECT, this::handleDisconnect);
                clientMessageBus.subscribe(MessageType.LOGOUT_REQUEST, this::handleLogoutRequest);

            } catch (Exception e) {
                logger.error("Error setting up client message bus", e);
                connected = false;
            }
        }
    }

    /**
     * Handles client logout requests
     */
    private void handleLogoutRequest(Message message) {
        logger.info("Client {} requested logout", message.getSender());

        // Send acknowledgement back to client
        Message response = new Message(
                MessageType.LOGOUT_RESPONSE,
                message.getRecipient(),
                message.getSender(),
                true);

        try {
            clientTransport.sendMessage(response);
            logger.info("Sent logout confirmation to client {}", message.getSender());
        } catch (Exception e) {
            logger.error("Error sending logout response", e);
        }

        // Set connected to false to trigger cleanup
        connected = false;
    }

    private boolean connectToApplicationServer() {
        synchronized (lock) {
            try {
                applicationSocket = new Socket(APP_SERVER_HOST, APP_SERVER_PORT);

                String componentName = "ProxyToApp-" + Thread.currentThread().getName();
                applicationMessageBus = new MessageBus(componentName, logger);
                applicationTransport = new SocketMessageTransport(applicationSocket, applicationMessageBus, logger);

                // Subscribe to application server responses
                applicationMessageBus.subscribe(MessageType.DATA_RESPONSE, this::handleDataResponse);

                logger.info("Connected to application server for client: {}", Thread.currentThread().getName());
                return true;
            } catch (Exception e) {
                logger.error("Failed to connect to application server: {}", e.getMessage());
                return false;
            }
        }
    }

    private void handleAuthRequest(Message message) {
        synchronized (lock) {
            // Only process if this seems to be intended for our server
            Message response = null;
            if (loginTries > 3) {
                logger.warning("Too many login attempts. Disconnecting client {}", message.getSender());
                response = new Message(
                        MessageType.DISCONNECT,
                        message.getRecipient(),
                        message.getSender(),
                        "Too many login attempts. Disconnecting client");
                clientTransport.sendMessage(message);
                connected = false;

                return;
            }

            try {
                logger.info("Processing authentication request from {}", message.getSender());

                String[] credentials = (String[]) message.getPayload();
                if (credentials.length >= 2) {
                    String username = credentials[0];
                    String password = credentials[1];

                    // Authenticate
                    boolean success = authService.authenticate(username, password);

                    // Send a welcome message to the client
                    response = new Message(
                            MessageType.AUTH_RESPONSE,
                            clientMessageBus.getComponentName(),
                            message.getSender(),
                            success ? "success" : "failed");
                    clientTransport.sendMessage(response);

                    clientTransport.sendMessage(response);

                    if (success) {
                        logger.info("Client {} authenticated successfully", message.getSender());
                        authenticated = true;
                        loginTries = 0;

                        if (applicationTransport == null) {
                            connectToApplicationServer();
                        }
                    } else {
                        loginTries++;
                    }

                } else {
                    logger.warning("Invalid authentication request format");
                    Message errorMsg = new Message(
                            MessageType.ERROR,
                            clientMessageBus.getComponentName(),
                            message.getSender(),
                            "Invalid authentication format");
                    clientMessageBus.send(errorMsg);
                    connected = false;
                }
            } catch (Exception e) {
                logger.error("Error processing authentication", e);
                connected = false;
            }
        }
    }

    private void handleDataRequest(Message message) {
        // Only authenticated clients can make data requests
        synchronized (lock) {
            if (!authenticated) {
                logger.warning("Unauthenticated data request rejected");
                return;
            }

            logger.info("Handling DATA_REQUEST from client {}: {}", message.getSender(), message.getPayload());
            // TODO MOSTRAR A CACHE A CADA OPERAÇÃO
            // CORRIGIR ESCRITA DA CACHE NO ARQUIVO

            logger.info("Forwarding DATA_REQUEST from client {} to application server", message.getSender());
            try {
                try {
                    if (message.getPayload() == null) {
                        logger.warning("Invalid data request payload in DATA_REQUEST");
                        Message errorMsg = new Message(
                                MessageType.ERROR,
                                message.getRecipient(),
                                message.getSender(),
                                "Invalid data request payload in DATA_REQUEST");

                        clientTransport.sendMessage(errorMsg);
                        return;
                    }

                    String requestStr = message.getPayload().toString();
                    String[] requestParts = requestStr.split("\\|");
                    String operation = requestParts[0].toUpperCase();

                    logger.info("Received DATA REQUEST operation: {}", operation);

                    if (operation.equals("ADD20")) {
                        // Adiciona 20 work orders na cache
                        for (int i = 0; i < 20; i++) {
                            cache.add(new WorkOrder(i, "WorkOrder " + i, "Description " + i));
                        }
                        logCacheMetrics();
                        clientTransport.sendMessage(new Message(
                                MessageType.DATA_RESPONSE,
                                message.getRecipient(),
                                message.getSender(),
                                "Added 20 work orders to cache"));
                        return;

                    }

                    if (operation.equals("SEARCH")
                            || operation.equals("UPDATE")
                            || operation.equals("REMOVE")) {
                        // Check cache first
                        WorkOrder workOrder = cache
                                .searchByCode(new WorkOrder(Integer.parseInt(requestParts[1]), null, null));

                        if (workOrder != null) {
                            logger.info("Cache HIT for work order: {}", workOrder);
                            Message forwardedRequest = new Message(
                                    MessageType.DATA_REQUEST,
                                    message.getSender(),
                                    message.getRecipient(),
                                    message.getPayload());

                            Message response = new Message(
                                    MessageType.DATA_REQUEST,
                                    message.getSender(),
                                    message.getRecipient(),
                                    message.getPayload());
                            switch (operation) {
                                case "SEARCH":
                                    // Cache HIT em uma busca
                                    // Pega o formato de envio do servidor
                                    // e envia para o cliente

                                    // Passo 1
                                    Map<String, String> workOrderMap = MapUtil.of(
                                            "status", "success",
                                            "source", message.getRecipient(),
                                            "code", String.valueOf(workOrder.getCode()),
                                            "name", workOrder.getName(),
                                            "description", workOrder.getDescription(),
                                            "timestamp", workOrder.getTimestamp().toString());

                                    // Passo 2
                                    Message cacheResponse = new Message(
                                            MessageType.DATA_RESPONSE,
                                            message.getRecipient(),
                                            message.getSender(),
                                            workOrderMap);

                                    clientTransport.sendMessage(cacheResponse);
                                case "REMOVE":
                                    // Envia a requisição para o servidor
                                    // e em seguida remove da cache

                                    // Passo 1
                                    forwardedRequest = new Message(
                                            MessageType.DATA_REQUEST,
                                            message.getSender(),
                                            message.getRecipient(),
                                            message.getPayload());
                                    applicationTransport.sendMessage(forwardedRequest);
                                    // Passo 2
                                    cache.remove(workOrder);
                                    logger.info("Removed WorkOrder with code {} from cache", workOrder.getCode());
                                    logCacheMetrics();

                                    return;
                                case "UPDATE":
                                    // Atualiza o workOrder na cache
                                    // e envia a requisição para o servidor

                                    // Passo 1
                                    forwardedRequest = new Message(
                                            MessageType.DATA_REQUEST,
                                            message.getSender(),
                                            message.getRecipient(),
                                            message.getPayload());
                                    applicationTransport.sendMessage(forwardedRequest);

                                    // Passo 2
                                    cache.remove(workOrder);
                                    cache.add(new WorkOrder(workOrder.getCode(), requestParts[2], requestParts[3]));
                                    logger.info("Updated WorkOrder with code {} in cache", workOrder.getCode());
                                    logCacheMetrics();
                                    return;
                                default:
                                    break;
                            }
                        }

                        logger.info("Cache MISS for work order: {}", requestParts[1]);
                    }

                } catch (Exception e) {
                    logger.error("Error processing DATA_REQUEST " + e.getStackTrace());
                }

                try {
                    logger.info("Forwarding DATA_REQUEST from client {} to application server: {}", message.getSender(),
                            message.getPayload());

                    // Check if application transport is available
                    if (applicationTransport == null) {
                        logger.error("Cannot forward request - application server connection not established");

                        // Try to reconnect
                        connectToApplicationServer();

                        // Check again after reconnection attempt
                        if (applicationTransport == null) {
                            throw new Exception("Failed to establish connection to application server");
                        }
                    }

                    // Forward the client request to application server
                    Message forwardedRequest = new Message(
                            MessageType.DATA_REQUEST,
                            message.getSender(),
                            message.getRecipient(),
                            message.getPayload());

                    applicationTransport.sendMessage(forwardedRequest);

                } catch (Exception e) {
                    logger.error("Error forwarding data request to application server: {}", e.getMessage());
                    // Rest of the error handling...
                }

            } catch (Exception e) {
                logger.error("Error forwarding data request to application server", e);

                // Send error back to client
                Message errorMsg = new Message(
                        MessageType.ERROR,
                        message.getRecipient(),
                        message.getSender(),
                        "Error processing request: " + e.getMessage());
                clientTransport.sendMessage(errorMsg);
            }
        }
    }

    private void handleDataResponse(Message message) {
        synchronized (lock) {
            try {
                logger.info("Received DATA_RESPONSE from application server for client {}: {}", message.getSender(),
                        message.getPayload());

                Object payload = message.getPayload();

                if (payload instanceof Map<?, ?>) {
                    // Process the response and update the cache as before...
                    Optional<Map<String, String>> responseMapOpt = TypeUtil.safeCastToMap(payload,
                            String.class,
                            String.class);

                    responseMapOpt.ifPresent(responseMap -> {
                        // Existing cache update logic...
                        if ("success".equals(responseMap.get("status")) &&
                                "Work order found".equals(responseMap.get("message")) &&
                                responseMap.containsKey("code") &&
                                responseMap.containsKey("name") &&
                                responseMap.containsKey("description")) {
                            // Update cache logic remains the same...
                            try {
                                int code = Integer.parseInt(responseMap.get("code"));
                                String name = responseMap.get("name");
                                String description = responseMap.get("description");
                                String timestamp = responseMap.get("timestamp");

                                WorkOrder workOrder = new WorkOrder(code, name, description, timestamp);
                                cache.add(workOrder);
                                logCacheMetrics();

                                logger.info("Added WorkOrder with code {} to cache", code);
                            } catch (Exception e) {
                                logger.error("Failed to add search result to cache: {}", e.getMessage());
                            }
                        }

                        // Create a new HashMap with both the original response and cache info
                        Map<String, String> enrichedResponse = new HashMap<>(responseMap);

                        // Add cache information to the response
                        enrichedResponse.put("cacheInfo", cache.getCacheContentsAsString());

                        // Send the enriched response to the client
                        Message forwardedResponse = new Message(
                                MessageType.DATA_RESPONSE,
                                message.getSender(),
                                message.getRecipient(),
                                enrichedResponse);
                        clientTransport.sendMessage(forwardedResponse);
                    });
                    // TODO check all the cases
                    // The original forwardedResponse should be removed to avoid duplicate messages
                } else {

                    // Handle non-map payloads
                    Map<String, Object> enrichedPayload = new HashMap<>();
                    enrichedPayload.put("originalResponse", payload);
                    enrichedPayload.put("cacheInfo", cache.getCacheContentsAsString());

                    // Forward the enriched response to client
                    Message forwardedResponse = new Message(
                            MessageType.DATA_RESPONSE,
                            message.getRecipient(),
                            message.getSender(),
                            enrichedPayload);

                    clientTransport.sendMessage(forwardedResponse);
                }
            } catch (Exception e) {
                logger.error("Error forwarding data response to client", e);
            }
        }
    }

    private void logCacheMetrics() {
        Map<String, Object> metrics = cache.getMetrics();
        logger.info("Cache metrics - Size: {}/{} ({}% full)",
                metrics.get("size"),
                metrics.get("maxSize"),
                metrics.get("usagePercent"));
    }

    private void handleDisconnect(Message message) {
        synchronized (lock) {
            logger.info("Client {} requested disconnect", message.getSender());

            Message response = new Message(
                    MessageType.DISCONNECT,
                    message.getRecipient(),
                    message.getSender(),
                    "Disconnecting client");

            try {
                clientTransport.sendMessage(response);
                logger.info("Sent disconnect confirmation to client {}",
                        message.getSender());
            } catch (Exception e) {
                logger.error("Error sending disconnect response", e);
            }

            connected = false;
        }
    }

    private void cleanup() {
        synchronized (lock) {
            try {
                // Unsubscribe from message handlers to avoid memory leaks
                if (clientMessageBus != null) {
                    clientMessageBus.unsubscribe(MessageType.AUTH_REQUEST, this::handleAuthRequest);
                    clientMessageBus.unsubscribe(MessageType.DATA_REQUEST, this::handleDataRequest);
                    clientMessageBus.unsubscribe(MessageType.DISCONNECT, this::handleDisconnect);
                    clientMessageBus.unsubscribe(MessageType.LOGOUT_REQUEST, this::handleLogoutRequest);
                }

                if (applicationMessageBus != null) {
                    applicationMessageBus.unsubscribe(MessageType.DATA_RESPONSE, this::handleDataResponse);
                }

                // Close client transport and socket
                if (clientTransport != null) {
                    clientTransport.close();
                }

                // Close application transport and socket
                if (applicationTransport != null) {
                    applicationTransport.close();
                }

                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }

                if (applicationSocket != null && !applicationSocket.isClosed()) {
                    applicationSocket.close();
                }

                // Notify the ProxyServer that a client has disconnected
                ProxyServer.clientDisconnected();

                logger.info("Client disconnected: {}. Active connections: {}",
                        Thread.currentThread().getName(), ProxyServer.activeConnections);

            } catch (Exception e) {
                logger.error("Error during cleanup", e);
            }
        }
    }
}