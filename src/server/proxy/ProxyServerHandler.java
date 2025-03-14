package server.proxy;

import java.net.Socket;
import java.util.Map;
import java.util.Optional;

import server.proxy.auth.AuthService;
import server.proxy.cache.CacheFIFO;
import server.proxy.cache.CacheHandler;
import shared.log.Logger;
import shared.messages.*;
import shared.models.WorkOrder;
import shared.utils.MapUtil;
import shared.utils.TypeUtil;

public class ProxyServerHandler implements Runnable {

    // Detalhes do Proxy
    private Socket clientSocket;
    private AuthService authService;
    private Logger logger;
    private CacheFIFO<WorkOrder> workOrderCache;
    private CacheHandler cacheHandler;

    // Detalhes do cliente
    private MessageBus clientMessageBus;
    private SocketMessageTransport clientTransport;
    private String clientId = null;
    private boolean connected = true;
    private boolean authenticated = false;

    // Detalhes do servidor de aplicação
    private static final String APP_SERVER_HOST = "localhost";
    private static final int APP_SERVER_PORT = 33330;
    private Socket applicationSocket;
    private MessageBus applicationMessageBus;
    private SocketMessageTransport applicationTransport;

    public ProxyServerHandler(Socket client, AuthService authService, Logger logger,
            CacheFIFO<WorkOrder> workOrderCache, CacheHandler cacheHandler) {
        this.clientSocket = client;
        this.authService = authService;
        this.logger = logger;
        this.workOrderCache = workOrderCache;
        this.cacheHandler = cacheHandler;

        // Default temporary ID based on socket address until we get the real client ID
        this.clientId = "Client-" + client.getInetAddress().getHostAddress() + ":" + client.getPort();

        synchronized (ProxyServerHandler.class) {
            ProxyServer.connectionCount++;
            ProxyServer.activeConnections++;
        }

        logger.info("New client handler created. ConnectionCount: {}, ActiveConnections: {}",
                ProxyServer.connectionCount, ProxyServer.activeConnections);

        // Setup client message bus and transport
        setupClientMessageTransport();
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

                if (!authenticated && connected) {
                    logger.warning("Authentication timeout for client: {}", clientId);
                    clientMessageBus.send(new Message(
                            MessageType.ERROR,
                            clientMessageBus.getComponentName(),
                            clientId,
                            "Authentication timeout"));
                    connected = false;
                }

                // If authenticated, keep handler alive as long as the client is connected
                if (authenticated) {
                    connectToApplicationServer();

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
                        Thread.sleep(20);
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

        } catch (Exception e) {
            logger.error("Error setting up client message bus", e);
            connected = false;
        }
    }

    private void connectToApplicationServer() {
        try {
            applicationSocket = new Socket(APP_SERVER_HOST, APP_SERVER_PORT);

            String componentName = "ProxyToApp-" + clientId;
            applicationMessageBus = new MessageBus(componentName, logger);
            applicationTransport = new SocketMessageTransport(applicationSocket, applicationMessageBus, logger);

            // Subscribe to application server responses
            applicationMessageBus.subscribe(MessageType.DATA_RESPONSE, this::handleDataResponse);

            logger.info("Connected to application server for client: {}", clientId);
        } catch (Exception e) {
            logger.error("Failed to connect to application server", e);

            // Inform client about the connection failure
            Message errorMsg = new Message(
                    MessageType.ERROR,
                    clientMessageBus.getComponentName(),
                    clientId,
                    "Failed to connect to application server: " + e.getMessage());
            clientMessageBus.send(errorMsg);
        }
    }

    private void handleAuthRequest(Message message) {

        try {
            // Only process if this seems to be intended for our server
            if (!message.getRecipient().equals(clientMessageBus.getComponentName())) {
                logger.warning("Ignoring authentication request not intended for this server");
                return;
            }

            logger.info("Processing authentication request from {}", message.getSender());

            String[] credentials = (String[]) message.getPayload();
            if (credentials.length >= 2) {
                String username = credentials[0];
                String password = credentials[1];

                // Update clientId to use the username
                // this.clientId = username;
                this.clientId = message.getSender();

                // Authenticate
                boolean success = authService.authenticate(username, password);
                logger.info("Authentication for user '{}': {}",
                        username, (success ? "SUCCESS" : "FAILED"));

                authenticated = success;

                // Send response back to the client
                Message response = new Message(
                        MessageType.AUTH_RESPONSE,
                        clientMessageBus.getComponentName(),
                        message.getSender(),
                        success);

                clientTransport.sendMessage(response);

                logger.info("Authentication for user '{}': {}",
                        username, (success ? "SUCCESS" : "FAILED"));

                // If authentication failed, we'll disconnect
                if (!success) {
                    connected = false;
                } else {
                    // Send welcome message upon successful auth
                    Message welcomeMsg = new Message(
                            MessageType.SERVER_INFO,
                            clientMessageBus.getComponentName(),
                            clientId,
                            "Welcome to the server, " + username + "!");
                    clientTransport.sendMessage(welcomeMsg);
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

    private void handleDataRequest(Message message) {
        // Only authenticated clients can make data requests
        if (!authenticated) {
            logger.warning("Unauthenticated data request rejected");
            return;
        }
        //TODO MOSTRAR A CACHE A CADA OPERAÇÃO
        // CORRIGIR ESCRITA DA CACHE NO ARQUIVO

        logger.info("Forwarding DATA_REQUEST from client {} to application server", clientId);
        try {
            try {
                if (message.getPayload() == null) {
                    logger.warning("Invalid data request payload in DATA_REQUEST");
                    Message errorMsg = new Message(
                            MessageType.ERROR,
                            clientMessageBus.getComponentName(),
                            clientId,
                            "Invalid data request payload in DATA_REQUEST");

                    clientTransport.sendMessage(errorMsg);
                    return;
                }

                String requestStr = message.getPayload().toString();
                String[] requestParts = requestStr.split("\\|");
                String operation = requestParts[0].toUpperCase();

                logger.info("Received DATA REQUEST operation: {}", operation);

                if (operation.equals("SEARCH")) {
                    // Check cache first
                    WorkOrder workOrder = workOrderCache
                            .searchByCode(new WorkOrder(Integer.parseInt(requestParts[1]), null, null));

                    if (workOrder != null) {
                        logger.info("Cache HIT for work order: {}", workOrder);

                        Map<String, String> workOrderMap = MapUtil.of(
                                "status", "success",
                                "source", message.getRecipient(),
                                "code", String.valueOf(workOrder.getCode()),
                                "name", workOrder.getName(),
                                "description", workOrder.getDescription(),
                                "timestamp", workOrder.getTimestamp().toString());

                        // Send cached response back to client
                        Message cacheResponse = new Message(
                                MessageType.DATA_RESPONSE,
                                message.getRecipient(),
                                clientId,
                                workOrderMap);
                        clientTransport.sendMessage(cacheResponse);
                        return;
                    }

                    logger.info("Cache MISS for work order: {}", requestParts[1]);
                }

            } catch (Exception e) {
                logger.error("Error processing SEARCH request", e);
            }

            // Forward the client request to application server
            Message forwardedRequest = new Message(
                    MessageType.DATA_REQUEST,
                    clientId,
                    "ApplicationServer",
                    message.getPayload());

            applicationTransport.sendMessage(forwardedRequest);

        } catch (Exception e) {
            logger.error("Error forwarding data request to application server", e);

            // Send error back to client
            Message errorMsg = new Message(
                    MessageType.ERROR,
                    clientMessageBus.getComponentName(),
                    clientId,
                    "Error processing request: " + e.getMessage());
            clientTransport.sendMessage(errorMsg);
        }
    }

    private void handleDataResponse(Message message) {
        try {
            logger.info("Received DATA_RESPONSE from application server for client {}", clientId);

            Object payload = message.getPayload();

            if (payload instanceof Map<?, ?>) {

                Optional<Map<String, String>> responseMapOpt = TypeUtil.safeCastToMap(payload,
                        String.class,
                        String.class);

                responseMapOpt.ifPresent(responseMap -> {

                    if ("success".equals(responseMap.get("status")) &&
                            responseMap.containsKey("code") &&
                            responseMap.containsKey("name") &&
                            responseMap.containsKey("description")) {

                        // É uma resposta de busca bem-sucedida, adicionamos à cache
                        try {
                            int code = Integer.parseInt(responseMap.get("code"));
                            String name = responseMap.get("name");
                            String description = responseMap.get("description");
                            String timestamp = responseMap.get("timestamp");

                            WorkOrder workOrder = new WorkOrder(code, name, description, timestamp);
                            workOrderCache.add(workOrder);

                            // Salva a cache após cada atualização
                            cacheHandler.saveCache();

                            logger.info("Added WorkOrder with code {} to cache", code);
                        } catch (Exception e) {
                            logger.error("Failed to add search result to cache: {}", e.getMessage());
                        }
                    }
                });
            }
            // Forward the application server response to client
            Message forwardedResponse = new Message(
                    MessageType.DATA_RESPONSE,
                    "ApplicationServer",
                    clientId,
                    message.getPayload());
            System.out.println(message.getPayload());
            clientTransport.sendMessage(forwardedResponse);

        } catch (Exception e) {
            logger.error("Error forwarding data response to client", e);
        }
    }

    private void handleDisconnect(Message message) {
        if (message.getSender().equals(clientId)) {
            logger.info("Client {} requested disconnect", clientId);
            connected = false;
        }
    }

    private void cleanup() {
        try {
            // Unsubscribe from message handlers to avoid memory leaks
            if (clientMessageBus != null) {
                clientMessageBus.unsubscribe(MessageType.AUTH_REQUEST, this::handleAuthRequest);
                clientMessageBus.unsubscribe(MessageType.DATA_REQUEST, this::handleDataRequest);
                clientMessageBus.unsubscribe(MessageType.DISCONNECT, this::handleDisconnect);
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

            synchronized (ProxyServerHandler.class) {
                ProxyServer.activeConnections--;
            }

            logger.info("Client disconnected: {}. Active connections: {}",
                    clientId, ProxyServer.activeConnections);

        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }
}