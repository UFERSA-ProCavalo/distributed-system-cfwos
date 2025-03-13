
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
    private boolean connected = false;
    private boolean authenticated = false;
    private Consumer<String> messageDisplay;
    private String username;
    private String password;
    private Scanner userInput;
    private Scanner scanner;
    private String clientId; // Default before authentication

    private MessageBus messageBus;
    private SocketMessageTransport transport;
    private Logger logger;

    public ImplClient(Socket socket, Logger logger) {
        this.socket = socket;
        this.logger = logger;

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

            userInput = new Scanner(System.in);
            setupMessageTransport();
            startConnection();

            while (connected
                    && !authenticated
                    && !socket.isClosed()) {
                try {
                    // Read user input
                    boolean messageProcessed = transport.readMessage();
                    Thread.sleep(1000);
                    if (!messageProcessed) {
                        connected = false;
                    }

                } catch (InterruptedException e) {
                    logger.debug("Client thread interrupted");
                }
            }

            while (connected
                    && authenticated
                    && !socket.isClosed()) {
                try {
                    // Read user input
                    System.out.println("Enter your option: ");
                    String message = userInput.nextLine();
                    sendMessage(MessageType.DATA_REQUEST, message);

                    Thread.sleep(1000);

                } catch (InterruptedException e) {
                    logger.debug("Client thread interrupted");
                    shutdown();
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
        if (connected) {
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

    private void setupMessageTransport() {

        String clientComponent = "Client-"
                + socket.getInetAddress().getHostAddress()
                + ":"
                + socket.getLocalPort();

        messageBus = new MessageBus(clientComponent, logger);
        transport = new SocketMessageTransport(socket, messageBus, logger);

        try {
            // Subscribe to relevant message types
            messageBus.subscribe(MessageType.START_RESPONSE, this::handleStartResponse);
            messageBus.subscribe(MessageType.LOGOUT_RESPONSE, this::handleLogoutResponse);
            messageBus.subscribe(MessageType.AUTH_RESPONSE, this::handleAuthResponse);
            messageBus.subscribe(MessageType.DATA_RESPONSE, this::handleDataResponse);
            messageBus.subscribe(MessageType.ERROR, this::handleErrorMessage);
            // Adicione um novo tipo de mensagem aqui

            connected = true;
        } catch (Exception e) {
            logger.error("Error in message transport setup", e);
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
            connected = true;

        } catch (Exception e) {
            logger.error("Connection error", e);
            connected = false;
        }
    }

    public void startAuth() {
        try {
            logger.info("Authenticating with server...");
            // Set up updated message bus component name
            messageBus.setComponentName(clientId);
            sendMessage(MessageType.AUTH_REQUEST, username + ":" + password);

            System.out.println("Enter your username: ");
            username = userInput.nextLine();
            System.out.println("Enter your password: ");
            password = userInput.nextLine();
            // DO NOT close the scanner as it would close System.in
            // scanner.close();
            authenticated = true;

        } catch (Exception e) {
            logger.error("Authentication error", e);
            authenticated = false;
        }
    }

    private void handleStartResponse(Message message) {

        logger.info("Handling START_RESPONSE message: {}", message);
        // Only process messages meant for us
        if (message.getRecipient() != null &&
                message.getRecipient().equals(clientId)) {

            Object payload = message.getPayload();
            String[] response = (String[]) payload;
            String applicationAddress = response[0];
            int applicationPort = Integer.parseInt(response[1]);
            logger.info(applicationAddress);

            //get proxy server info, split using :

            
            logger.info("Start response: " + message);

            // change to proxy socket
            try {
                socket.setReuseAddress(connected);
                socket.close();
                Socket socketProxy = new Socket(applicationAddress, applicationPort);
                socket = socketProxy;

                logger.info("Connected to server: {} ({})", message);
            } catch (Exception e) {
                logger.error("Failed to connect to server", e);
            }

            // Display to user if handler is set
            // if (messageDisplay != null) {
            //     messageDisplay.accept(response);
            // }
        }
    }

    private void handleAuthResponse(Message message) {

        logger.info("Handling AUTH_RESPONSE message: {}", message);
        // Only process messages meant for us
        if (message.getRecipient() != null &&
                message.getRecipient().equals(clientId)) {

            Boolean success = (Boolean) message.getPayload();
            logger.info("Authentication response: " + (success ? "Success" : "Failed"));

            if (!success) {
                connected = false;

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

    private void handleLogoutResponse(Message message) {
        logger.info("Handling LOGOUT_RESPONSE message: {}", message);
        // Only process messages meant for us
        if (message.getRecipient() != null &&
                message.getRecipient().equals(clientId)) {

            Boolean success = (Boolean) message.getPayload();
            logger.info("Logout response: " + (success ? "Success" : "Failed"));

            if (!success) {
                connected = false;

                if (messageDisplay != null) {
                    messageDisplay.accept("Logout failed. Please restart the client.");
                }
            } else {
                if (messageDisplay != null) {
                    messageDisplay.accept("Successfully logged out.");
                }
            }
        }
    }

    private void handleDataResponse(Message message) {
        logger.info("Handling DATA_RESPONSE message: {}", message);
        // Only process messages meant for us
        if (message.getRecipient() != null &&
                message.getRecipient().equals(clientId)) {

            String data = (String) message.getPayload();
            logger.info("Data response: " + data);

            // Display to user if handler is set
            if (messageDisplay != null) {
                messageDisplay.accept(data);
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
            connected = false;

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