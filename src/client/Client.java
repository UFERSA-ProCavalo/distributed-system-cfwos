package client;

import java.net.InetAddress;
import java.net.Socket;

import shared.log.Logger;
import shared.messages.Message;
import shared.messages.MessageBus;
import shared.messages.MessageType;
import shared.messages.SocketMessageTransport;

public class Client {
    private final Logger logger = Logger.getLogger();
    private Socket socket;
    private InetAddress inet;
    private final String SERVER_IP = "localhost";
    private final int SERVER_PORT = 11110;
    private ImplClient implClient;
    private MessageBus messageBus;
    private SocketMessageTransport transport;
    private int clientId;

    public Client() {
        this.clientId = 0;
        // Initialize the message bus with this component's name
        this.messageBus = new MessageBus("Client", logger);

        // Subscribe to relevant message types

        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Client shutting down...");
            disconnect();
            messageBus.shutdown();
        }));

        logger.info("Client initialized, connecting to server...");
        this.run();
    }

    private void run() {
        try {
            logger.info("Connecting to server at {}:{}", SERVER_IP, SERVER_PORT);
            socket = new Socket(SERVER_IP, SERVER_PORT);
            inet = socket.getInetAddress();

            logger.info("Connected to server: {} ({})", inet.getHostAddress(), inet.getHostName());

            implClient = new ImplClient(socket, messageBus, logger);
            Thread thread = new Thread(implClient);
            thread.start();
        } catch (Exception e) {
            logger.error("Failed to connect to server", e);
        } finally {
            if (socket != null && socket.isClosed()) {
                logger.error("Socket is closed, cannot connect to server");
            }
        }
    }

    public void disconnect() {
        try {
            if (implClient != null) {
                // Send disconnect message using message bus
                // messageBus.send(MessageType.DISCONNECT, "ProxyServer",
                // implClient.getUsername());
                implClient.shutdown();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("Disconnected from server");
            }
        } catch (Exception e) {
            logger.error("Error while disconnecting", e);
        }
    }

    public ImplClient getImplClient() {
        return implClient;
    }

    public static void main(String[] args) {
        new Client();
    }
}