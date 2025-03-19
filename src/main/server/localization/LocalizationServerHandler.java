package main.server.localization;

import java.net.Socket;
import java.util.concurrent.TimeUnit;

import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;

/**
 * Handler for client connections to the localization server
 */
public class LocalizationServerHandler implements Runnable {
    private final Socket clientSocket;
    private String clientId;
    private final LocalizationServer server;
    private final Logger logger;

    private SocketMessageTransport transport;
    private MessageBus messageBus;
    private volatile boolean connected = true;
    private volatile boolean messageProcessed = false;
    private static final long CONNECTION_TIMEOUT_MS = 30000; // 30 seconds timeout

    /**
     * Create a new handler for a client connection
     */
    public LocalizationServerHandler(Socket socket, String clientId, LocalizationServer server) {
        this.clientSocket = socket;
        this.clientId = clientId;
        this.server = server;
        this.logger = server.getLogger();
    }

    /**
     * Get client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Get the message transport
     */
    public SocketMessageTransport getTransport() {
        return transport;
    }

    /**
     * Check if this handler's connection is still active
     */
    public boolean isConnected() {
        return connected && transport != null && !clientSocket.isClosed();
    }

    /**
     * Send a message through this handler
     */
    public void sendMessage(Message message) {
        try {
            if (transport != null) {
                transport.sendMessage(message);
            }
        } catch (Exception e) {
            logger.error("Error sending message through handler", e);
        }
    }

    @Override
    public void run() {
        try {
            setupCommunication();

            while (connected && !messageProcessed) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Error in client handler", e);
        } finally {
            if (!messageProcessed) {
                logger.warning("Client {} connection timed out", clientId);
            }
            close();
        }
    }

    /**
     * Set up message transport and handlers
     */
    private void setupCommunication() {
        try {
            // Create message bus for this handler
            messageBus = new MessageBus("LocalizationHandler-" + clientId, logger);
            transport = new SocketMessageTransport(clientSocket, messageBus, logger, true);

            // Subscribe to message types
            messageBus.subscribe(MessageType.START_REQUEST, this::handleStartRequest);
            messageBus.subscribe(MessageType.RECONNECT, this::handleReconnectRequest);
            messageBus.subscribe(MessageType.DISCONNECT, this::handleDisconnect);

            // Add handler for proxy registration and peer info
            messageBus.subscribe(MessageType.PROXY_REGISTRATION_REQUEST, this::handleProxyRegistration);
            messageBus.subscribe(MessageType.PONG, this::handlePong);

            logger.debug("Communication setup complete for client {}", clientId);
        } catch (Exception e) {
            logger.error("Failed to set up communication for client {}", clientId, e);
            connected = false;
        }
    }

    /**
     * Handle initial connection request - redirect to a proxy server
     */
    private void handleStartRequest(Message message) {
        logger.info("Received START_REQUEST from {}", message.getSender());

        // Use a separate thread for refresh to avoid blocking the message handler
        new Thread(() -> {
            try {
                // Refresh proxy list
                server.refreshProxyServers();

                // Now redirect
                redirectToProxyServer(message.getSender());
            } catch (Exception e) {
                logger.error("Error processing START_REQUEST", e);
                sendErrorMessage(message.getSender(), "Error processing request: " + e.getMessage());
            }
        }, "refresh-proxies-thread").start();
    }

    /**
     * Handle reconnection request
     */
    private void handleReconnectRequest(Message message) {
        logger.info("Received RECONNECT request from {}", message.getSender());

        // Use a separate thread for refresh to avoid blocking the message handler
        new Thread(() -> {
            try {
                // Refresh proxy list
                server.refreshProxyServers();

                // Redirect to available proxy
                redirectToProxyServer(message.getSender());
            } catch (Exception e) {
                logger.error("Error processing RECONNECT", e);
                sendErrorMessage(message.getSender(), "Error processing reconnect: " + e.getMessage());
            }
        }, "reconnect-proxies-thread").start();
    }

    /**
     * Handle proxy server registration requests
     */
    private void handleProxyRegistration(Message message) {
        logger.info("Received PROXY_REGISTRATION_REQUEST from {}", message.getSender());

        if (message.getPayload() instanceof String[]) {
            String[] registrationInfo = (String[]) message.getPayload();
            if (registrationInfo.length >= 3) {
                String serverId = registrationInfo[0];

                // Ensure proxy IDs start with "Proxy-"
                if (!serverId.startsWith("Proxy-")) {
                    serverId = "Proxy-" + serverId;
                }

                String host = registrationInfo[1];
                String port = registrationInfo[2];

                logger.info("Processing registration request from proxy: {} on port {}", serverId, port);

                // Enable the proxy refresh logic
                // server.refreshProxyServers();

                // Check if this server ID is already registered
                if (LocalizationServer.isProxyRegistered(serverId)) {
                    // Send "already taken" response
                    logger.warning("Proxy ID {} is already registered", serverId);
                    sendRegistrationResponse(message.getSender(), "ALREADY_TAKEN");
                } else {
                    // Register the new proxy server
                    LocalizationServer.registerProxyServer(serverId, host, port, logger);

                    // Update the client ID in the server's map to match the proxy ID
                    server.updateClientId(clientId, serverId);

                    // Update this handler's client ID
                    this.clientId = serverId;

                    // Send success response
                    sendRegistrationResponse(message.getSender(), "SUCCESS");
                }
            } else {
                logger.warning("Invalid registration info from {}, insufficient data", message.getSender());
                sendRegistrationResponse(message.getSender(), "INVALID_REQUEST");
            }
        }
    }

    private void handlePong(Message message) {
        if (message.getType() == MessageType.PONG) {
            String senderId = message.getSender();
            logger.info("Received PONG from proxy: {}", senderId);

            // Forward to main server to process this PONG
            server.handleProxyPong(senderId, message.getPayload());
        }
    }

    /**
     * Send registration response back to proxy server
     */
    private void sendRegistrationResponse(String recipient, String status) {
        try {
            Message response = new Message(
                    MessageType.PROXY_REGISTRATION_RESPONSE,
                    "LocalizationServer",
                    recipient,
                    status);

            transport.sendMessage(response);
            logger.debug("Sent registration response '{}' to {}", status, recipient);
        } catch (Exception e) {
            logger.error("Error sending registration response to {}", recipient, e);
        }
    }

    /**
     * Common method to redirect a client to an available proxy server
     */
    private void redirectToProxyServer(String recipient) {
        try {
            String[] serverInfo = server.selectProxyServer();

            if (serverInfo != null) {
                // Create a response with the proxy server info
                Message response = new Message(
                        MessageType.SERVER_INFO,
                        "LocalizationServer",
                        recipient,
                        serverInfo);

                transport.sendMessage(response);
                logger.info("Redirected {} to proxy server at {}:{}",
                        recipient, serverInfo[0], serverInfo[1]);
            } else {
                // No servers available
                sendErrorMessage(recipient, "No proxy servers available");
            }
        } catch (Exception e) {
            logger.error("Error redirecting client to proxy server", e);
            sendErrorMessage(recipient, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Handle disconnect requests
     */
    private void handleDisconnect(Message message) {
        logger.info("Received DISCONNECT from {}", message.getSender());
        messageProcessed = true;
        close();
    }

    /**
     * Send an error message to the client
     */
    private void sendErrorMessage(String recipient, String errorMessage) {
        try {
            Message errorMsg = new Message(
                    MessageType.ERROR,
                    "LocalizationServer",
                    recipient,
                    errorMessage);

            transport.sendMessage(errorMsg);

        } catch (Exception e) {
            logger.error("Error sending error message", e);
        }
    }

    /**
     * Close this client handler
     */
    public void close() {
        if (connected) {
            connected = false;
            try {
                if (transport != null) {
                    transport.close();
                }

                if (messageBus != null) {
                    messageBus.unsubscribeAll();
                }

                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }

                // Remove from server's tracking
                server.removeClient(clientId);

                logger.debug("Handler for client {} closed", clientId);
            } catch (Exception e) {
                logger.error("Error closing client handler", e);
            }
        }
    }
}