package shared.messages;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import shared.log.Logger;

public class SocketMessageTransport {
    private final Socket socket;
    private final MessageBus messageBus;
    private final Logger logger;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean running = true;

    public SocketMessageTransport(Socket socket, MessageBus messageBus, Logger logger) {
        this(socket, messageBus, logger, false);
    }

    public SocketMessageTransport(Socket socket, MessageBus messageBus, Logger logger, Boolean isServer) {
        this.socket = socket;
        this.messageBus = messageBus;
        this.logger = logger;
        // Initialize the streams based on client/server role
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

    /**
     * Read a message from the socket (blocking)
     * 
     * @return true if a message was processed, false if connection closed
     */
    public boolean readMessage() {
        if (!running || socket.isClosed()) {
            return false;
        }

        try {
            Object obj = in.readObject();

            if (obj instanceof Message) {
                // Forward received message to the message bus
                Message message = (Message) obj;
                messageBus.receive(message);
                return true;
            } else {
                logger.warning("Received non-message object: " + obj.getClass().getName());
                return true;
            }
        } catch (IOException e) {
            if (running) {
                logger.info("Socket connection closed");
            }
            running = false;
            return false;
        } catch (ClassNotFoundException e) {
            logger.error("Error reading from socket: " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error in transport", e);
            running = false;
            return false;
        }
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
            messageBus.send(message);
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

        } catch (IOException e) {
            logger.error("Error while closing transport: " + e.getMessage());
        }
    }

    /**
     * Check if the transport is running
     */
    public boolean isRunning() {
        return running && !socket.isClosed();
    }
}
