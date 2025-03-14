package main.server.localization;

import java.net.Socket;

import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;

public class LocalizationServerHandler implements Runnable {
    private boolean connected = true;
    private String clientId;

    private final Socket clientSocket;
    private final Logger logger;
    private MessageBus messageBus;
    private SocketMessageTransport transport;

    public LocalizationServerHandler(Socket clientSocket, Logger logger) {
        this.clientSocket = clientSocket;
        this.logger = logger;

        this.clientId = "Client-"
                + clientSocket.getInetAddress().getHostAddress()
                + ":"
                + clientSocket.getPort();
        logger.info("ImplClient initialized with socket: " + clientSocket);

        logger.info("New client connected: {}:{}",
                clientSocket.getInetAddress().getHostAddress(),
                clientSocket.getPort());

        logger.info("Client connected: {}:{}",
                clientSocket.getInetAddress().getHostAddress(),
                clientSocket.getPort());

    }

    public void run() {
        try {
            setupMessageTransport();
            logger.info("Waiting for client message...");

            // Main message processing loop - single threaded
            while (connected && transport.isRunning() && !clientSocket.isClosed()) {
                // This will block until a message is available or connection closes
                boolean messageProcessed = transport.readMessage();

                // If the message couldn't be processed (connection closed)
                if (!messageProcessed) {
                    connected = false;
                }
            }

            logger.info("Client disconnected");
        } catch (Exception e) {
            logger.error("Error in client handler", e);
        } finally {
            cleanup();
        }
    }

    private void setupMessageTransport() {
        String ServerComponent = "Server-"
                + clientSocket.getInetAddress().getHostAddress()
                + ":"
                + clientSocket.getLocalPort();

        messageBus = new MessageBus(ServerComponent, logger);
        transport = new SocketMessageTransport(clientSocket, messageBus, logger, true);

        try {
            // Subscribe to relevant message types
            messageBus.subscribe(MessageType.START_REQUEST, this::handleStartRequest);
            // Adicione um novo tipo de mensagem aqui
        } catch (Exception e) {
            logger.error("Error in message transport setup", e);
        }
    }

    private void handleStartRequest(Message message) {
        try {
            logger.info("Handling START_REQUEST message: {}", message);

            String[] server = getProxyServerInfo();

            // Respond with server information
            Message response = new Message(
                    MessageType.START_RESPONSE,
                    messageBus.getComponentName(),
                    message.getSender(),
                    new String[] { server[0], server[1] });

            transport.sendMessage(response);
            logger.info("Sent proxy server info to client");

        } catch (Exception e) {
            logger.error("Error handling START_REQUEST", e);
        }
    }

    private String[] getProxyServerInfo() {
        String[] server = new String[2];
        String serverEntry = LocalizationServer.getServerAddresses().get("ApplicationProxy");

        if (serverEntry != null) {
            String[] parts = serverEntry.split(":");
            if (parts.length >= 3) {
                server[0] = parts[1]; // host
                server[1] = parts[2]; // port
            } else {
                // Default fallback
                logger.error("Invalid server entry: {}", serverEntry);
            }
        } else {
            // Default fallback
            logger.error("No server entry found for 'ApplicationServer'");
        }

        return server;
    }

    private void cleanup() {
        try {
            // First unsubscribe to prevent more callbacks
            messageBus.unsubscribe(MessageType.START_REQUEST, this::handleStartRequest);

            // Close transport
            if (transport != null) {
                transport.close();
            }

            // No need to close socket directly as transport will do it

            logger.info("Handler cleanup completed for client {}", clientId);
        } catch (Exception e) {
            logger.error("Error during handler cleanup", e);
        }
    }

    public static void shutdown() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'shutdown'");
    }
}