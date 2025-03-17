package main.client.message;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import main.client.ImplClient;
import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageType;

public class MessageDispatcher implements AutoCloseable {
    // Changed from String to MessageType
    private final Map<MessageType, ServiceMessage> handlers = new ConcurrentHashMap<>();
    private final ImplClient client;
    private final Logger logger;
    private final ExecutorService dispatcherThreadPool;
    private boolean shutdownRequested = false;

    public MessageDispatcher(ImplClient client, Logger logger) {
        this.client = client;
        this.logger = logger;

        // Create thread pool based on available processors
        this.dispatcherThreadPool = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2), // At least 2 threads
                r -> {
                    Thread t = new Thread(r, "message-dispatcher");
                    t.setDaemon(true); // Use daemon threads
                    return t;
                });

        registerDefaultHandlers();
        logger.info("MessageDispatcher initialized with thread pool");
    }

    private void registerDefaultHandlers() {
        // Register all default message handlers using enum values
        registerHandler(MessageType.START_RESPONSE, new StartResponseHandler());
        registerHandler(MessageType.AUTH_RESPONSE, new AuthResponseHandler());
        registerHandler(MessageType.DATA_RESPONSE, new DataResponseHandler());
        registerHandler(MessageType.ERROR, new ErrorHandler());
        registerHandler(MessageType.DISCONNECT, new DisconnectHandler());
        registerHandler(MessageType.SERVER_INFO, new ServerInfoHandler());
        registerHandler(MessageType.LOGOUT_RESPONSE, new LogoutResponseHandler());
        registerHandler(MessageType.RECONNECT, new ReconnectHandler()); // Add this line
    }

    // Updated to use MessageType enum
    public void registerHandler(MessageType messageType, ServiceMessage handler) {
        handlers.put(messageType, handler);
        // logger.debug("Registered handler for message type: {}", messageType);
    }

    public void dispatchMessage(Message message) {
        if (shutdownRequested) {
            logger.warning("MessageDispatcher is shutting down, message discarded: {}", message);
            return;
        }

        // Get the handler first to avoid unnecessary thread creation
        ServiceMessage handler = handlers.get(message.getType());

        if (handler != null) {
            // Dispatch to thread pool
            dispatcherThreadPool.submit(() -> {
                try {
                    logger.debug("Dispatching message of type {} to handler", message.getType());
                    handler.handle(message, client);
                } catch (Exception e) {
                    logger.error("Error in message handler for type {}: {}",
                            message.getType(), e.getMessage(), e);
                }
            });
        } else {
            logger.warning("No handler registered for message type: {}", message.getType());
        }
    }

    @Override
    public void close() {
        shutdownRequested = true;

        try {
            logger.info("Shutting down MessageDispatcher thread pool...");
            dispatcherThreadPool.shutdown();

            if (!dispatcherThreadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                logger.warning("MessageDispatcher thread pool did not terminate in time, forcing shutdown");
                dispatcherThreadPool.shutdownNow();
            }

            logger.info("MessageDispatcher thread pool shut down successfully");
        } catch (InterruptedException e) {
            logger.error("MessageDispatcher shutdown interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if the dispatcher is still active
     */
    public boolean isActive() {
        return !shutdownRequested && !dispatcherThreadPool.isShutdown();
    }
}
