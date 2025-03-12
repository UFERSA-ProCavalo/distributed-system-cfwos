
//TODO Refactor to properly communication
package client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;
import java.util.function.Consumer;

import shared.log.Logger;
import shared.messages.Message;
import shared.messages.MessageBus;
import shared.messages.MessageType;
import shared.messages.SocketMessageTransport;


public class ImplClient implements Runnable {
    private Logger logger;
    private Socket socket;
    private boolean connection = false;
    private Consumer<String> messageDisplay;
    private String username;
    private String password;
    private MessageBus messageBus;
    private SocketMessageTransport transport;
    private String clientId = "Client"; // Default before authentication

    public ImplClient(Socket client, MessageBus messageBus, Logger logger) {
        this.socket = client;
        this.messageBus = messageBus;
        this.logger = logger;
        logger.debug("ImplClient initialized with socket: " + client);

        // Set up listeners for server responses
        messageBus.subscribe(MessageType.AUTH_RESPONSE, this::handleAuthResponse);
        messageBus.subscribe(MessageType.CHAT_MESSAGE, this::handleChatMessage);
        messageBus.subscribe(MessageType.SERVER_INFO, this::handleServerInfo);
        messageBus.subscribe(MessageType.ERROR, this::handleErrorMessage);
    }

    public void setMessageDisplay(Consumer<String> messageDisplay) {
        this.messageDisplay = messageDisplay;
        logger.debug("Message display handler set");
    }

    public void sendMessage(String message) {
        if (message.equalsIgnoreCase("exit")) {
            // Send disconnect message using message bus
            Message disconnectMsg = new Message(
                    MessageType.DISCONNECT,
                    this.clientId,
                    "ServerProxy",
                    "Client disconnecting");

            // Use the transport to send the message
            if (transport != null) {
                transport.sendMessage(disconnectMsg);
                logger.info("Disconnect message sent to server");
            }

            connection = false;
            logger.info("Connection ended by user!");
        } else {
            // Regular message
            if (transport != null) {
                // Create a proper Message object
                Message msg;

                // Check if this is a formatted message (TYPE:content)
                String[] parts = message.split(":", 2);
                if (parts.length == 2 && MessageType.class.getFields().length > 0) {
                    // Try to match with a known message type
                    msg = new Message(
                            parts[0], // Message type
                            this.clientId, // From client
                            "ServerProxy", // To server
                            parts[1] // Message content
                    );
                } else {
                    // Default to chat message
                    msg = new Message(
                            MessageType.CHAT_MESSAGE,
                            this.clientId,
                            "ServerProxy",
                            message);
                }

                // Send using transport
                transport.sendMessage(msg);
                logger.debug("Message sent: " + msg.getType());
            } else {
                logger.error("Cannot send message: transport not initialized");
            }
        }
    }

    public void run() {
        try {
            startConnection();
            Scanner scanner = new Scanner(System.in);
            // Keep the thread alive while connected
            while (connection && !socket.isClosed()) {
                try {
                    // use scanner to read user input
                    logger.info("Waiting for user input...");
                    String userInput = scanner.nextLine();
                    sendMessage(userInput);
                    logger.debug("User input sent: " + userInput);

                    // Sleep for a while to avoid busy waiting
                    // This is just for demonstration; in a real application, you would
                    // likely use a more sophisticated event-driven approach
                    // or a GUI event loop

                    Thread.sleep(500);

                    // log received messages from server

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

    public void startConnection() {
        try {
            // First connect to localization server
            logger.info("Connecting to localization server...");
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            out.writeObject("START_CONNECTION");
            out.flush();

            // Get proxy server details
            String response = (String) in.readObject();
            logger.info("Received proxy server details: " + response);

            String[] responseArray = response.split(":");
            Socket proxySocket = new Socket(responseArray[0], Integer.parseInt(responseArray[1]));
            socket.close();

            logger.debug("Localization socket closed: " + socket.isClosed());
            socket = proxySocket;
            logger.debug("Proxy socket connected: " + !socket.isClosed());

            connection = true;

            // Authenticate with proxy server
            Scanner scanner = new Scanner(System.in);
            logger.info("Authentication required");
            System.out.print("Enter username: ");
            username = scanner.nextLine();
            System.out.print("Enter password: ");
            password = scanner.nextLine();

            // Update the client ID to use the username
            this.clientId = username;

            // Use message transport for communication
            transport = new SocketMessageTransport(socket, messageBus, logger);
            new Thread(transport).start();

            // Send authentication message
            Message authMessage = new Message(
                    MessageType.AUTH_REQUEST,
                    clientId, // From client
                    "ServerProxy", // To the server
                    new String[] { username, password });
            transport.sendMessage(authMessage);

            logger.info("Authentication request sent, waiting for response...");
            // DO NOT close the scanner as it would close System.in
            // scanner.close();

        } catch (Exception e) {
            logger.error("Connection error", e);
            connection = false;
        }
    }

    // Message handlers
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

    public void connect(String username, String password) {
        try {
            this.username = username;
            this.password = password;
            this.clientId = username; // Use username as client ID

            setMessageDisplay(message -> {
                logger.info("Message from server: " + message);
            });

            if (transport != null) {
                // Create authentication message
                Message authMessage = new Message(
                        MessageType.AUTH_REQUEST,
                        clientId, // From client
                        "ServerProxy", // To the server
                        new String[] { username, password });

                // Send directly via transport
                transport.sendMessage(authMessage);
                logger.info("Authentication request sent");
            } else {
                logger.error("Cannot authenticate: transport not initialized");
            }

        } catch (Exception e) {
            logger.error("Authentication error", e);
        }
    }

    public void shutdown() {
        try {
            connection = false;

            // Send a disconnect message if possible
            if (transport != null && !socket.isClosed()) {
                Message disconnectMsg = new Message(
                        MessageType.DISCONNECT,
                        clientId,
                        "ServerProxy",
                        "Client shutting down");
                transport.sendMessage(disconnectMsg);
                transport.close();
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