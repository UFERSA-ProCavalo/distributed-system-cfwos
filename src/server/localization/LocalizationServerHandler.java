package server.localization;

import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import shared.log.Logger;
import shared.messages.Message;
import shared.messages.MessageBus;

import java.util.Map;
import java.util.Scanner;

public class LocalizationServerHandler implements Runnable {
    private Scanner scanner;
    private boolean connected = true;

    private final Socket clientSocket;
    private final Logger logger;
    private final MessageBus messageBus;
    private ObjectInputStream in;

    public LocalizationServerHandler(Socket clientSocket, Logger logger,
            MessageBus messageBus) {
        this.clientSocket = clientSocket;
        this.logger = logger;
        this.messageBus = messageBus;
        this.scanner = null;

        // Register message handlers for this client connection
        messageBus.subscribe("START_CONNECTION", this::handleStartHandshake);
        messageBus.subscribe("DISCONNECT", this::handleDisconnect);

        // Default temporary ID based on socket address until we get the real client ID
    }

    public void run() {
        try {

            scanner = new Scanner(clientSocket.getInputStream());

            while (connected) {
                logger.info("Waiting for client message...");
                String handshake = scanner.nextLine();
                logger.info(handshake);
            }

            String request = (String) in.readObject();
            System.out.println("|Received request: " + request);

            // Redirect to proxy server if request is "START_CONNECTION"
            if (request.equals("START_CONNECTION")) {

                String[] Server = LocalizationServer.getServerAddresses().get("ProxyServer").split(":");
                String address = Server[1];
                int port = Integer.parseInt(Server[2]);

                Message message = new Message(
                        "START_CONNECTION",
                        "ProxyServer",
                        "Client-"
                                + clientSocket.getInetAddress().getHostAddress()
                                + ":"
                                + clientSocket.getPort(),
                        new String[] { address, ":", String.valueOf(port) });

                messageBus.send(message);

                // out.writeObject(address + ":" + port);
                logger.debug("Sent message: {}", message);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        scanner.close();
    }

    private void handleStartHandshake(Message message) {
        // Handle the start connection message
        logger.info("Handling start connection message: {}", message);
        // Logic to handle the start connection
        try {
            Object obj = in.readObject();
            if (obj instanceof Message) {
                // Forward received message to the message bus
                logger.debug("Received message: " + message);
                messageBus.send(message);
            } else {
                logger.warning("Received non-message object: " + obj.getClass().getName());
            }
        } catch (Exception e) {
            logger.error("Error while handling start connection message", e);
        } finally {
            // Close the scanner and input stream
            if (scanner != null) {
                scanner.close();
            }
            try {
                in.close();
            } catch (Exception e) {
                logger.error("Error closing input stream", e);
            }
            try {
                clientSocket.close();
            } catch (Exception e) {
                logger.error("Error closing client socket", e);
            }
        }

    }

    private void handleDisconnect(Message message) {
        // Handle the disconnect message
        logger.info("Handling disconnect message: {}", message);
        // Logic to handle the disconnect
    }
}