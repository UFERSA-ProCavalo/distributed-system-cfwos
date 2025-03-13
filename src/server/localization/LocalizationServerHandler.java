package server.localization;

import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import shared.log.Logger;
import shared.messages.Message;
import shared.messages.MessageBus;
import shared.messages.SocketMessageTransport;

import java.util.Map;
import java.util.Scanner;

public class LocalizationServerHandler implements Runnable {
    private Scanner scanner;
    private boolean connected = true;
    private String clientId;

    private final Socket clientSocket;
    private final Logger logger;
    private MessageBus messageBus;
    private SocketMessageTransport transport;

    public LocalizationServerHandler(Socket clientSocket, Logger logger) {
        this.clientSocket = clientSocket;
        this.logger = logger;
        this.scanner = null;

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

            while (connected) {
                // Wait for messages
                Thread.sleep(1000);
            }
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
            messageBus.subscribe("START_CONNECTION", this::handleStartConnection);
            // Adicione um novo tipo de mensagem aqui

            // Wait until disconnected

        } catch (Exception e) {
            logger.error("Error in message transport setup", e);
        }
    }

    private void handleStartConnection(Message message) {
        try {
            logger.info("Handling START_CONNECTION message: {}", message);

            String[] server = getProxyServerInfo();

            // Respond with server information
            Message response = new Message(
                    "SERVER_ADDRESS",
                    messageBus.getComponentName(),
                    message.getSender(),
                    new String[] { server[0], server[1] });

            transport.sendMessage(response);
            logger.info("Sent proxy server info to client");

        } catch (Exception e) {
            logger.error("Error handling START_CONNECTION", e);
        }
    }

    private String[] getProxyServerInfo() {
        String[] server = new String[2];
        String serverEntry = LocalizationServer.getServerAddresses().get("ProxyServer");

        if (serverEntry != null) {
            String[] parts = serverEntry.split(":");
            if (parts.length >= 3) {
                server[0] = parts[1]; // host
                server[1] = parts[2]; // port
            } else {
                // Default fallback
                server[0] = "localhost";
                server[1] = "11111";
            }
        } else {
            // Default fallback
            server[0] = "localhost";
            server[1] = "11111";
        }

        return server;
    }

    private void cleanup() {
        try {
            // First unsubscribe to prevent more callbacks
            messageBus.unsubscribe("START_CONNECTION", this::handleStartConnection);

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
}