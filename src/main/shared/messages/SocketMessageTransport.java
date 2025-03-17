package main.shared.messages;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import main.shared.log.Logger;

public class SocketMessageTransport {
    private final Socket socket;
    private final MessageBus messageBus;
    private final Logger logger;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Thread pools for network operations
    private final ExecutorService readerThread;
    private final ExecutorService writerThread;

    // Message queue for outgoing messages
    private final BlockingQueue<Message> outgoingMessages = new LinkedBlockingQueue<>();

    public SocketMessageTransport(Socket socket, MessageBus messageBus, Logger logger) {
        this(socket, messageBus, logger, false);
    }

    public SocketMessageTransport(Socket socket, MessageBus messageBus, Logger logger, Boolean isServer) {
        this.socket = socket;
        this.messageBus = messageBus;
        this.logger = logger;

        // Create single-thread pools for reading and writing
        this.readerThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "socket-reader-" + socket.getLocalPort() + "-" + socket.getPort());
            t.setDaemon(true);
            return t;
        });

        this.writerThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "socket-writer-" + socket.getLocalPort() + "-" + socket.getPort());
            t.setDaemon(true);
            return t;
        });

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

        logger.info("Socket transport initialized: {}", socket);

        // Start the reader and writer threads
        startReaderThread();
        startWriterThread();
    }

    /**
     * Start a dedicated thread for reading from socket
     */
    private void startReaderThread() {
        readerThread.submit(() -> {
            while (running.get() && !socket.isClosed()) {
                try {
                    Object obj = in.readObject();

                    if (obj instanceof Message) {
                        // Forward received message to the message bus
                        Message message = (Message) obj;
                        messageBus.receive(message);
                    } else {
                        logger.warning("Received non-message object: {}",
                                obj.getClass().getName());
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        logger.info("Socket connection closed");
                    }
                    running.set(false);
                    break;
                } catch (ClassNotFoundException e) {
                    logger.error("Error reading from socket: {}", e.getMessage());
                } catch (Exception e) {
                    logger.error("Unexpected error in transport reader", e);
                    running.set(false);
                    break;
                }
            }

            // Make sure we close everything if the reader exits
            if (running.getAndSet(false)) {
                close();
            }
        });
    }

    /**
     * Start a dedicated thread for writing to socket
     */
    private void startWriterThread() {
        writerThread.submit(() -> {
            while (running.get() && !socket.isClosed()) {
                try {
                    // Block until a message is available or interrupted
                    Message message = outgoingMessages.poll(500, TimeUnit.MILLISECONDS);

                    if (message != null) {
                        out.writeObject(message);
                        out.flush();
                        messageBus.send(message);
                    }
                } catch (IOException e) {
                    logger.error("Failed to send message: {}", e.getMessage());
                    running.set(false);
                    break;
                } catch (InterruptedException e) {
                    // Interrupted while waiting for a message
                    if (running.get()) {
                        logger.debug("Writer thread interrupted");
                    }
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Unexpected error in transport writer", e);
                    running.set(false);
                    break;
                }
            }

            // Make sure we close everything if the writer exits
            if (running.getAndSet(false)) {
                close();
            }
        });
    }

    /**
     * Read a message from the socket (now non-blocking)
     * This method is kept for backward compatibility
     * 
     * @return true if connection is alive, false otherwise
     */
    public boolean readMessage() {
        return isRunning();
    }

    /**
     * Send a message over this socket connection
     */
    public synchronized void sendMessage(Message message) {
        if (!running.get() || socket.isClosed()) {
            logger.warning("Cannot send message - connection is closed");
            return;
        }

        try {
            outgoingMessages.put(message);
            logger.debug("Queued message for sending: {}", message.getPayload());
        } catch (InterruptedException e) {
            logger.error("Interrupted while queuing message");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Close this transport
     */
    public void close() {
        if (running.getAndSet(false)) {
            logger.info("Closing socket transport");

            try {
                // Shutdown thread pools
                readerThread.shutdownNow();
                writerThread.shutdownNow();

                // Close streams and socket
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (!socket.isClosed()) {
                    socket.close();
                }

                logger.info("Socket transport closed successfully");
            } catch (IOException e) {
                logger.error("Error while closing transport: {}", e.getMessage());
            }
        }
    }

    /**
     * Check if the transport is running
     */
    public boolean isRunning() {
        return running.get() && !socket.isClosed();
    }
}
