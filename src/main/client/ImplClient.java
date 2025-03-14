//TODO Refactor to properly communication
package main.client;

import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Consumer;

import main.client.gui.ConsoleMenu;
import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;
import main.shared.utils.TypeUtil;

public class ImplClient implements Runnable {
    private Socket socket;
    private boolean connected = false;
    private boolean authenticated = false;
    private Consumer<String> messageDisplay;
    private String username;
    private String password;
    private Scanner userInput;
    private ConsoleMenu consoleMenu;
    private String clientId;
    private boolean testing = false; // FLAG PARA TESTAR

    private MessageBus messageBus = null;
    private SocketMessageTransport transport = null;
    private Logger logger;

    public ImplClient(Socket socket, Logger logger, Boolean testing) {
        this(socket, logger);
        this.testing = testing;
    }

    public ImplClient(Socket socket, Logger logger) {
        this.socket = socket;
        this.logger = logger;

        this.clientId = "Client-"
                + socket.getLocalAddress().getHostAddress()
                + ":"
                + socket.getLocalPort();

        logger.debug("ImplClient initialized with socket: " + socket);
        logger.info("Client ID set: {}", clientId);

        // Initialize scanner for user input
        userInput = new Scanner(System.in);

        // Create the console menu
        consoleMenu = new ConsoleMenu(this, userInput, logger);
    }

    public void run() {
        // At this point, the client has connected to the localization server
        try {
            setupMessageTransport();
            startConnection();

            // Wait for localization server response and redirection
            while (connected && !authenticated && !socket.isClosed()) {
                try {
                    boolean messageProcessed = transport.readMessage();
                    if (!messageProcessed) {
                        logger.error("Failed to process message");
                        throw new Exception("Failed to process message");
                    }
                    if (authenticated) {
                        break;
                    }
                    if (testing) {
                        // Skip startAuth to avoid scanner input
                        continue;
                    }
                    logger.info("Please authenticate:");
                    startAuth();
                    System.out.println("\033[2J\033[1;1H"); // Clear screen
                } catch (Exception e) {
                    logger.error("Client thread interrupted", e);
                    break;
                }
            }

            // Main application loop (authenticated)
            while (connected && authenticated && !socket.isClosed() && consoleMenu.isRunning()) {
                try {
                    // Process messages from server
                    boolean messageProcessed = transport.readMessage();
                    if (!messageProcessed) {
                        if (socket.isClosed()) {
                            break;
                        }
                        logger.error("Failed to process message");
                        throw new Exception("Failed to process message");
                    }
                    if (testing) {
                        // Skip startAuth to avoid scanner input
                        continue;
                    }
                    // Display menu and get user input
                    consoleMenu.displayMenu();
                    consoleMenu.processMenuChoice();

                    // Small delay to prevent CPU hogging
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.error("Client thread interrupted", e);
                    break;
                }
            }

            logger.info("Connection ended.");
        } catch (Exception e) {
            logger.error("Error in client connection", e);
        } finally {
            // Clean up resources
            shutdown();
        }
    }

    private void setupMessageTransport() {

        String clientComponent = "Client-"
                + socket.getInetAddress().getHostAddress()
                + ":"
                + socket.getLocalPort();

        if (messageBus == null) {
            messageBus = new MessageBus(clientComponent, logger);

            messageBus.subscribe(MessageType.START_RESPONSE, this::handleStartResponse);
            messageBus.subscribe(MessageType.LOGOUT_RESPONSE, this::handleLogoutResponse);
            messageBus.subscribe(MessageType.AUTH_RESPONSE, this::handleAuthResponse);
            messageBus.subscribe(MessageType.DATA_RESPONSE, this::handleDataResponse);
            messageBus.subscribe(MessageType.SERVER_INFO, this::handleServerInfo);
            messageBus.subscribe(MessageType.ERROR, this::handleErrorMessage);
        }
        if (transport == null) {
            transport = new SocketMessageTransport(socket, messageBus, logger);
        }

        // logger.warning("Transport should not be running, something is going to
        // break");
        // logger.warning("Socket: {}", socket);
        // logger.warning("Transport: {}", transport);

        connected = true;
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
            System.out.println("Enter your username: ");
            username = userInput.nextLine();
            System.out.println("Enter your password: ");
            password = userInput.nextLine();

            sendMessage(MessageType.AUTH_REQUEST, new String[] { username, password });
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
            // change to proxy socket
            redirect(response);

            // Display to user if handler is set
            // if (messageDisplay != null) {
            // messageDisplay.accept(response);
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
                    return;
                }
            } else {
                if (messageDisplay != null) {
                    messageDisplay.accept("Successfully logged in as " + username);
                    return;
                }
            }
            authenticated = success;
        } else {
            logger.error("Authentication response message not meant for this client");
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

            Object payload = message.getPayload();

            if (payload instanceof Map<?, ?>) {
                Map<?, ?> responseMap = (Map<?, ?>) payload;

                // First display cache info if present
                if (responseMap.containsKey("cacheInfo")) {
                    String cacheInfo = (String) responseMap.get("cacheInfo");
                    System.out.println(cacheInfo);
                }

                // Then display the main response
                System.out.println("\n=== Response ===");

                // Display status if present
                if (responseMap.containsKey("status")) {
                    System.out.println("Status: " + responseMap.get("status"));
                }

                // Display other fields, excluding cacheInfo
                for (Map.Entry<?, ?> entry : responseMap.entrySet()) {
                    String key = entry.getKey().toString();
                    if (!key.equals("cacheInfo") && !key.equals("status")) {
                        System.out.println(key + ": " + entry.getValue());
                    }
                }

                System.out.println("================\n");
            } else {
                // Handle simple string responses
                System.out.println("\n=== Response ===\n" + payload + "\n================\n");
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

    private void handleServerInfo(Message message) {
        // Only process messages meant for us
        if (message.getRecipient() != null &&
                message.getRecipient().equals(clientId)) {

            String serverInfo = (String) message.getPayload();
            logger.info("Server info: " + serverInfo);

            // Display to user if handler is set
            if (messageDisplay != null) {
                messageDisplay.accept(serverInfo);
            }
        } else {
            logger.error("Server info message not meant for this client");
        }
    }

    private void sendMessage(Message message) {
        if (transport != null && !socket.isClosed()) {
            transport.sendMessage(message);
        } else {
            logger.error("Cannot send message - transport is closed");

        }
    }

    public void sendMessage(String type, Object message) {
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

    public void redirect(String[] server) {

        connected = false;

        if (server == null || server.length < 2) {
            logger.error("Invalid server information: " + server);
            return;

        }

        try {
            socket.close();
            transport.close();
            socket = new Socket(server[0], Integer.parseInt(server[1]));
            logger.info("Connected to server: {} ({})", server[0], server[1]);
            transport = null;
            setupMessageTransport();
            connected = true;

        } catch (Exception e) {
            logger.error("Failed to connect to server", e);
        } finally {
            if (socket != null && socket.isClosed()) {
                logger.error("Socket is closed, cannot connect to server");
            }
        }

    }

    public void shutdown() {
        try {
            if (!connected) {
                logger.info("Client already disconnected");
                return;
            }

            logger.info("Sending logout request to server...");
            connected = false;

            // Send a disconnect message if possible
            if (messageBus != null && transport != null && !socket.isClosed()) {
                Message logoutMsg = new Message(
                        MessageType.LOGOUT_REQUEST,
                        clientId,
                        "Server-" + socket.getInetAddress().getHostAddress() + ":" + socket.getPort(),
                        "Client shutting down");

                // Send directly through transport to ensure delivery
                transport.sendMessage(logoutMsg);

                // Brief wait for response
                try {
                    Thread.sleep(200); // Small delay to allow response
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Now shutdown message bus
                messageBus.shutdown();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("Socket closed");
            }

            logger.info("ImplClient shutdown completed");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}