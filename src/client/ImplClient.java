
//TODO Refactor to properly communication
package client;

import java.net.Socket;
import java.util.Scanner;
import java.util.function.Consumer;

import shared.log.Logger;
import shared.messages.Message;
import shared.messages.MessageBus;
import shared.messages.MessageType;
import shared.messages.SocketMessageTransport;

public class ImplClient implements Runnable {
    private Socket socket;
    private boolean connection = false;
    private Consumer<String> messageDisplay;
    private String username;
    private String password;
    private Scanner userInput;
    private Scanner scanner;
    private String clientId; // Default before authentication

    private MessageBus messageBus;
    private SocketMessageTransport transport;
    private Logger logger;

    public ImplClient(Socket socket, MessageBus messageBus, Logger logger) {
        this.socket = socket;
        this.logger = logger;
        this.messageBus = messageBus;
        this.transport = new SocketMessageTransport(socket, messageBus, logger);

        this.clientId = "Client-"
                + socket.getLocalAddress().getHostAddress()
                + ":"
                + socket.getLocalPort();

        logger.debug("ImplClient initialized with socket: " + socket);

        logger.info("Client ID set: {}", clientId);
        // Set up listeners for server responses

    }

    public void run() {
        // At this point, the client has connected to the localization server
        try {
            setupMessageTransport();
            startConnection();
            // Keep the thread alive while connected
            while (connection && !socket.isClosed()) {
                try {
                    System.out.println("Hello there :)");
                    Thread.sleep(1000);

                } catch (InterruptedException e) {
                    logger.debug("Client thread interrupted");
                }
            }

            scanner.close();
            logger.info("Connection ended.");
        } catch (Exception e) {
            logger.error("Error in client connection", e);
        } finally {
            // Clean up resources
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                logger.info("Socket closed: " + socket.isClosed());

            } catch (Exception e) {
                logger.error("Error closing socket", e);
            }
        }
        Thread.currentThread().interrupt();
    }

    private void sendMessage(Message message) {
        if (messageBus != null && !socket.isClosed()) {
            messageBus.send(message);
        } else {
            logger.error("Cannot send message - connection is closed");
        }
        if (transport != null && !socket.isClosed()) {
            transport.sendMessage(message);
        } else {
            logger.error("Cannot send message - transport is closed");

        }
    }

    public void sendMessage(String type, String message) {
        if (connection) {
            Message msg = new Message(
                    type,
                    clientId,
                    "Server-" + socket.getInetAddress().getHostAddress() + ":" + socket.getPort(),
                    message);
            sendMessage(msg);
        } else {
            logger.error("Cannot send message - connection is closed");
        }
    }

    public void startConnection() {
        try {
            logger.info("Connecting to localization server...");
            // Set up updated message bus component name
            messageBus.setComponentName(clientId);
            sendMessage("START_REQUEST", "Ask for connection");

            // DO NOT close the scanner as it would close System.in
            // scanner.close();
            connection = true;

        } catch (Exception e) {
            logger.error("Connection error", e);
            connection = false;
        }
    }

    // Message handlers
    private void setupMessageTransport() {
        try {

            // Subscribe to relevant message types
            messageBus.subscribe(MessageType.AUTH_RESPONSE, this::handleAuthResponse);
            messageBus.subscribe(MessageType.DATA_RESPONSE, this::handleChatMessage);
            messageBus.subscribe(MessageType.START_RESPONSE, this::handleServerInfo);
            messageBus.subscribe(MessageType.ERROR, this::handleErrorMessage);
            // Adicione um novo tipo de mensagem aqui

            connection = true;
            // Wait until disconnected

        } catch (Exception e) {
            logger.error("Error in message transport setup", e);
        }
    }

    private void handleAuthResponse(Message message) {
        // Only process messages meant for us
        if (message.getRecipient() != null &&
                message.getRecipient().equals(clientId)) {

            Boolean success = (Boolean) message.getPayload();
            logger.info("Authentication response: " + (success ? "Success" : "Failed"));

            if (!success) {
                connection = false;

                if (messageDisplay != null) {
                    messageDisplay.accept("Authentication failed. Please restart the client.");
                }
            } else {
                if (messageDisplay != null) {
                    messageDisplay.accept("Successfully logged in as " + username);
                }
            }
        }
    }

    private void handleChatMessage(Message message) {
        // Only process messages meant for us
        if (message.getRecipient() != null &&
                message.getRecipient().equals(clientId)) {

            String chatMsg = (String) message.getPayload();
            logger.info("Chat message from server: " + chatMsg);

            // Display to user if handler is set
            if (messageDisplay != null) {
                messageDisplay.accept("[" + message.getSender() + "]: " + chatMsg);
            }
        }
    }

    private void handleServerInfo(Message message) {
        // Only process messages meant for us
        if (message.getRecipient() != null &&
                message.getRecipient().equals(clientId)) {

            String infoMsg = (String) message.getPayload();
            logger.info("Server info: " + infoMsg);

            // Display to user if handler is set
            if (messageDisplay != null) {
                messageDisplay.accept("[SERVER]: " + infoMsg);
            }
        }
    }

    private void handleErrorMessage(Message message) {
        // Only process messages meant for us
        if (message.getRecipient() != null &&
                message.getRecipient().equals(clientId)) {

            String errorMsg = (String) message.getPayload();
            logger.error("Server error: " + errorMsg);

            // Display to user if handler is set
            if (messageDisplay != null) {
                messageDisplay.accept("[ERROR]: " + errorMsg);
            }
        }
    }

    public String getUsername() {
        return username;
    }

    public void shutdown() {
        try {
            connection = false;

            // Send a disconnect message if possible
            if (messageBus != null && !socket.isClosed()) {
                Message disconnectMsg = new Message(
                        MessageType.LOGOUT_REQUEST,
                        clientId,
                        "ServerProxy",
                        "Client shutting down");
                messageBus.send(disconnectMsg);
                messageBus.shutdown();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            logger.info("ImplClient shutdown completed");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
}