package shared.messages;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import shared.log.Logger;

public class SocketMessageTransport implements Runnable {
    private final Socket socket;
    private final MessageBus messageBus;
    private final Logger logger;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean running = true;

    public SocketMessageTransport(Socket socket, MessageBus messageBus, Logger logger) {
        this.socket = socket;
        this.messageBus = messageBus;
        this.logger = logger;
        try {
            this.out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // Important to avoid deadlock on input stream creation
            this.in = new ObjectInputStream(socket.getInputStream());
            logger.debug("Socket transport initialized for " + socket.getRemoteSocketAddress());
        } catch (IOException e) {
            logger.error("Failed to initialize streams", e);
            running = false;
        }
    }

    @Override
    public void run() {
        while (running && !socket.isClosed()) {
            try {
                Object obj = in.readObject();
                if (obj instanceof Message message) {
                    // Forward received message to the message bus
                    logger.debug("Received message: " + message);
                    messageBus.send(message);
                } else {
                    logger.warning("Received non-message object: " + obj.getClass().getName());
                }
            } catch (IOException e) {
                if (running) {
                    logger.debug("Socket connection closed");
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
            logger.debug("Sent message: " + message);
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
                logger.debug("Socket transport closed");
            }
        } catch (IOException e) {
            logger.error("Error closing socket", e);
        }
    }
}