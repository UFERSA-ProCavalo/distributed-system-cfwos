package main.client;

import java.net.Socket;
import java.util.Map;
import java.util.function.Consumer;

import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;

/**
 * Handles all network-related operations for the client
 * including socket management, message transport, and connection status
 */
public class ServiceNetwork {
    private Socket socket;
    private SocketMessageTransport transport;
    private MessageBus messageBus;
    private final Logger logger;
    private final String componentName;
    private boolean connected = false;
    private ImplClient client;

    /**
     * Creates a NetworkManager with an existing socket
     */
    public ServiceNetwork(Socket socket, Logger logger, String componentName) {
        this.socket = socket;
        this.logger = logger;
        this.componentName = componentName;

        if (socket != null && !socket.isClosed()) {
            setupMessageTransport();
        }
    }

    /**
     * Sets up the message transport system for the client
     */
    private void setupMessageTransport() {
        try {
            messageBus = new MessageBus(componentName, logger);
            transport = new SocketMessageTransport(socket, messageBus, logger);
            connected = true;
            logger.info("Message transport initialized with component name: {}", componentName);
        } catch (Exception e) {
            logger.error("Failed to initialize message transport", e);
            connected = false;
        }
    }

    /**
     * Establishes a connection to the specified host and port
     */
    public boolean connect(String host, int port) {
        try {
            // Close existing connection if any
            if (socket != null && !socket.isClosed()) {
                close();
            }

            socket = new Socket(host, port);
            setupMessageTransport();
            return connected;
        } catch (Exception e) {
            logger.error("Connection failed to {}:{}", host, port, e);
            return false;
        }
    }

    /**
     * Updates the component name for the message bus
     */
    public void updateComponentName(String newName) {
        if (messageBus != null) {
            messageBus.setComponentName(newName);
            logger.info("Updated component name to: {}", newName);
        }
    }

    /**
     * Sends a message through the transport layer
     */
    public void sendMessage(Message message) {
        try {
            if (transport != null && transport.isRunning()) {
                transport.sendMessage(message);
            } else {
                logger.error("Cannot send message - transport is null or disconnected");

                // Notify about connection loss
                if (client != null) {
                    client.handleConnectionLost();
                }
            }
        } catch (Exception e) {
            logger.error("Error sending message", e);

            // Notify about connection error
            if (client != null) {
                client.handleConnectionLost();
            }
        }
    }

    /**
     * Registers a handler for a specific message type
     */
    public void registerHandler(MessageType messageType, Consumer<Message> handler) {
        if (messageBus != null) {
            messageBus.subscribe(messageType, handler);
            logger.info("Registered handler for message type: {}", messageType);
        }
    }

    /**
     * New method to check if messages are being processed
     */
    public boolean isProcessingMessages() {
        return transport != null && transport.isRunning();
    }

    /**
     * Closes all network resources
     */
    public void close() {
        try {
            connected = false;

            // First close transport to stop thread pools
            if (transport != null) {
                transport.close();
                transport = null;
            }

            // Then close message bus
            if (messageBus != null) {
                messageBus.shutdown(); // New method to properly shutdown thread pool
                messageBus = null;
            }

            // Finally close the socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }

            logger.info("Network resources closed");
        } catch (Exception e) {
            logger.error("Error while closing network resources", e);
        }
    }

    /**
     * Checks if the connection is established
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    /**
     * Gets the underlying MessageBus
     */
    public MessageBus getMessageBus() {
        return messageBus;
    }

    /**
     * Gets the underlying SocketMessageTransport
     */
    public SocketMessageTransport getTransport() {
        return transport;
    }

    /**
     * Sets the client for callbacks
     */
    public void setClient(ImplClient client) {
        this.client = client;
    }
}
