package shared.messages;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import shared.log.Logger;

public class SocketMessageTransport2 implements Runnable {
    private final Socket socket;
    private final MessageBus messageBus;
    private final Logger logger;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean running = true;
    private final boolean isServer;

    public SocketMessageTransport2(Socket socket, MessageBus messageBus, Logger logger) {
        this(socket, messageBus, logger, false);
    }

    public SocketMessageTransport2(Socket socket, MessageBus messageBus, Logger logger, boolean isServer) {
        this.socket = socket;
        this.messageBus = messageBus;
        this.logger = logger;
        this.isServer = isServer;

        if (isServer) {
            try {
                this.out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                this.in = new ObjectInputStream(socket.getInputStream());
            } catch (Exception e) {
                logger.error("Failed to initialize streams", e);
                close();
            }
        } else {
            try {
                this.in = new ObjectInputStream(socket.getInputStream());
                this.out = new ObjectOutputStream(socket.getOutputStream());
            } catch (Exception e) {
                logger.error("Failed to initialize streams", e);
                close();
            }
        }

        logger.info("Socket transport initialized: " + socket);

    }

    @Override
    public void run() {
        while (running && !socket.isClosed()) {
            try {
                Object obj = in.readObject();
                /*
                 * Java 16 feature: Pattern Type Matching in instanceof
                 * https://openjdk.java.net/jeps/394
                 */
                // if (obj instanceof Message message) {
                // Forward received message to the message bus
                if (obj instanceof Message) {
                    // Forward received message to the message bus
                    Message message = (Message) obj;
                    logger.info("Received message: " + message);
                    messageBus.send(message);

                } else {
                    logger.warning("Received non-message object: " + obj.getClass().getName());
                }
            } catch (IOException e) {
                if (running) {
                    logger.info("Socket connection closed");
                }
                running = false;
            } catch (ClassNotFoundException e) {
                logger.error("Error reading from socket: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error in transport", e);
                running = false;
            }
        }

        // Clean up
        close();
    }

    /**
     * Send a message over this socket connection
     */
    public synchronized void sendMessage(Message message) {
        if (!running || socket.isClosed()) {
            logger.warning("Cannot send message - connection is closed");
            return;
        }

        try {
            out.writeObject(message);
            out.flush();
            logger.info("Sent message: " + message);
        } catch (IOException e) {
            logger.error("Failed to send message: " + e.getMessage());
            running = false;
        }
    }

    /**
     * Close this transport
     */
    public void close() {
        running = false;
        try {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("Socket transport closed");
            }
        } catch (IOException e) {
            logger.error("Error closing socket", e);
        }
    }
}