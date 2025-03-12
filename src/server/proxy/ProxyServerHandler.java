package server.proxy;

import java.net.Socket;

import server.proxy.auth.AuthService;
import shared.log.Logger;
import shared.messages.*;

public class ProxyServerHandler implements Runnable {
    private Socket clientSocket;
    private AuthService authService;
    private Logger logger;
    private MessageBus messageBus;
    private String clientId = null; // Will be set during authentication
    private boolean connected = true;
    private boolean authenticated = false;

    public ProxyServerHandler(Socket client, AuthService authService, Logger logger) {
        this.clientSocket = client;
        this.authService = authService;
        this.logger = logger;
        // Default temporary ID based on socket address until we get the real client ID
        // from auth
        this.clientId = "Temp-" + client.getInetAddress().getHostAddress() + ":" + client.getPort();

        synchronized (ProxyServerHandler.class) {
            ProxyServer.connectionCount++;
            ProxyServer.activeConnections++;
        }

        logger.info("New client handler created. ConnectionCount: {}, ActiveConnections: {}",
                ProxyServer.connectionCount, ProxyServer.activeConnections);
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

                // Register message handlers for this client connection
                messageBus.subscribe(MessageType.AUTH_REQUEST, this::handleAuthRequest);
                messageBus.subscribe(MessageType.DISCONNECT, this::handleDisconnect);

                // Wait for client authentication - using wait/notify would be better in
                // production
                int authTimeoutSeconds = 30;
                int waitIntervalMs = 500;
                int maxAttempts = (authTimeoutSeconds * 1000) / waitIntervalMs;
                int attempts = 0;

                while (!authenticated && connected && attempts < maxAttempts) {
                    Thread.sleep(waitIntervalMs);
                    attempts++;
                }

                if (!authenticated && connected) {
                    logger.warning("Authentication timeout for client: {}", clientId);
                    messageBus.send(new Message(
                            MessageType.ERROR,
                            "ServerProxy",
                            clientId,
                            "Authentication timeout"));
                    connected = false;
                }

                // If authenticated, keep handler alive as long as the client is connected
                if (authenticated) {
                    while (connected && !clientSocket.isClosed()) {
                        Thread.sleep(1000); // Check connection status periodically
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error in client handler", e);
        } finally {
            cleanup();
        }
    }

    private void handleAuthRequest(Message message) {
        try {
            // Only process if this seems to be intended for our temporary client
            // This is a simplification - in production you'd need a more robust matching
            // mechanism
            if (!message.getRecipient().equals("ServerProxy")) {
                return;
            }

            logger.info("Processing authentication request from {}", message.getSender());

            String[] credentials = (String[]) message.getPayload();
            if (credentials.length >= 2) {
                String username = credentials[0];
                String password = credentials[1];

                // Update clientId to use the username
                this.clientId = username;

                // Authenticate
                boolean success = authService.authenticate(username, password);
                authenticated = success;

                // Send response back to the specific client that sent the request
                Message response = new Message(
                        MessageType.AUTH_RESPONSE,
                        "ServerProxy",
                        clientId,
                        success);

                messageBus.send(response);

                logger.info("Authentication for user '{}': {}", username,
                        (success ? "SUCCESS" : "FAILED"));

                // If authentication failed, we'll disconnect
                if (!success) {
                    connected = false;
                } else {
                    // Send welcome message upon successful auth
                    Message welcomeMsg = new Message(
                            MessageType.SERVER_INFO,
                            "ServerProxy",
                            clientId,
                            "Welcome to the server, " + username + "!");
                    messageBus.send(welcomeMsg);
                }
            } else {
                logger.warning("Invalid authentication request format");
                Message errorMsg = new Message(
                        MessageType.ERROR,
                        "ServerProxy",
                        message.getSender(),
                        "Invalid authentication format");
                messageBus.send(errorMsg);
                connected = false;
            }
        } catch (Exception e) {
            logger.error("Error processing authentication", e);
            connected = false;
        }
    }

    private void handleDisconnect(Message message) {
        if (message.getSender().equals(clientId)) {
            logger.info("Client {} requested disconnect", clientId);
            connected = false;
        }
    }

    // private void handleChatMessage(Message message) {
    // // Only process messages from authenticated clients
    // if (!authenticated)
    // return;

    // if (message.getSender().equals(clientId)) {
    // // Handle chat messages for this client
    // String chatContent = (String) message.getPayload();
    // logger.info("Chat message from {}: {}", clientId, chatContent);

    // // Echo back to sender
    // Message response = new Message(
    // MessageType.CHAT_MESSAGE,
    // "ServerProxy",
    // clientId,
    // "Server received: " + chatContent);
    // messageBus.send(response);

    // // In a real system, you would broadcast to other clients here
    // }
    // }

    private void cleanup() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

            synchronized (ProxyServerHandler.class) {
                ProxyServer.activeConnections--;
            }

            logger.info("Client disconnected: {}. Active connections: {}",
                    clientId, ProxyServer.activeConnections);

        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
        Thread.currentThread().interrupt();
    }

}