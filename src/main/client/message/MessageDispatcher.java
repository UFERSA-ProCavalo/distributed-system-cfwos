package main.client.message;

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

        // Reduce thread pool size - use a smaller number of threads
        this.dispatcherThreadPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "message-dispatcher");
            t.setDaemon(true);
            // Set lower thread priority to reduce CPU usage
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        registerDefaultHandlers();
        logger.info("MessageDispatcher initialized");
    }

    private void registerDefaultHandlers() {
        // Register all default message handlers using enum values
        registerHandler(MessageType.AUTH_RESPONSE, new AuthResponseHandler());
        registerHandler(MessageType.DATA_RESPONSE, new DataResponseHandler());
        registerHandler(MessageType.ERROR, new ErrorHandler());
        registerHandler(MessageType.DISCONNECT, new DisconnectHandler());
        registerHandler(MessageType.SERVER_INFO, new ServerInfoHandler());
        registerHandler(MessageType.LOGOUT_RESPONSE, new LogoutResponseHandler());
        registerHandler(MessageType.RECONNECT, new ReconnectHandler());
    }

    // Updated to use MessageType enum
    public void registerHandler(MessageType messageType, ServiceMessage handler) {
        handlers.put(messageType, handler);
    }

    public void dispatchMessage(Message message) {
        if (shutdownRequested) {
            return;
        }

        // Get the handler first to avoid unnecessary thread creation
        ServiceMessage handler = handlers.get(message.getType());

        if (handler != null) {
            // For simple operations, just handle directly in this thread rather than
            // submitting to thread pool
            // Only use thread pool for potentially long operations
            if (isSimpleMessage(message.getType())) {
                try {
                    handler.handle(message, client);
                } catch (Exception e) {
                    logger.error("Error in message handler: {}", e.getMessage());
                }
            } else {
                // Dispatch to thread pool for more complex operations
                dispatcherThreadPool.submit(() -> {
                    try {
                        handler.handle(message, client);
                    } catch (Exception e) {
                        logger.error("Error in message handler: {}", e.getMessage());
                    }
                });
            }
        } else {
            logger.warning("No handler for message type: {}", message.getType());
        }
    }

    // Helper method to identify simple messages that can be handled synchronously
    private boolean isSimpleMessage(MessageType type) {
        return type == MessageType.PING || type == MessageType.PONG ||
                type == MessageType.DISCONNECT || type == MessageType.ERROR;
    }

    @Override
    public void close() {
        shutdownRequested = true;

        try {
            dispatcherThreadPool.shutdown();
            if (!dispatcherThreadPool.awaitTermination(1, TimeUnit.SECONDS)) {
                dispatcherThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown interrupted", e);
        }
    }

    public boolean isActive() {
        return !shutdownRequested && !dispatcherThreadPool.isShutdown();
    }
}
