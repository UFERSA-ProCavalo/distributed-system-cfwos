package main.shared.messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import main.shared.log.Logger;

public class MessageBus {
    // Thread pool for message processing
    private final ExecutorService messageProcessorPool;
    private final Logger logger;

    // Use ConcurrentHashMap to reduce synchronization overhead
    private final Map<MessageType, List<Consumer<Message>>> subscribers = new ConcurrentHashMap<>();
    private String componentName;
    private volatile boolean shutdownRequested = false;

    // Add a logging level control to reduce excessive logging
    private boolean verboseLogging = false;

    public MessageBus(String componentName, Logger logger) {
        // Use a smaller fixed thread pool to reduce thread context switching
        this(componentName, logger, 2);
    }

    public MessageBus(String componentName, Logger logger, int threadPoolSize) {
        this.logger = logger;
        this.componentName = componentName;

        // Create thread pool with reduced priority threads
        this.messageProcessorPool = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "msgbus-" + componentName);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        logger.info("MessageBus initialized: {}", componentName);
    }

    /**
     * Enable or disable verbose logging
     */
    public void setVerboseLogging(boolean verbose) {
        this.verboseLogging = verbose;
    }

    /**
     * Subscribe to a message type
     */
    public void subscribe(MessageType type, Consumer<Message> handler) {
        subscribers.computeIfAbsent(type, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
    }

    /**
     * Unsubscribe from a specific message type
     */
    public void unsubscribe(MessageType messageType, Consumer<Message> callback) {
        List<Consumer<Message>> callbacks = subscribers.get(messageType);
        if (callbacks != null) {
            callbacks.remove(callback);
        }
    }

    public void unsubscribeAll() {
        subscribers.clear();
        logger.info("Removed all subscriptions");
    }

    /**
     * Send a message asynchronously
     */
    public void send(Message message) {
        if (shutdownRequested) {
            return;
        }

        // Only log if verbose logging is enabled
        if (verboseLogging) {
            messageProcessorPool.submit(() -> {
                try {
                    logger.debug("Outgoing: {} → {}", message.getSender(), message.getRecipient());
                } catch (Exception e) {
                    logger.error("Error logging message", e);
                }
            });
        }
    }

    /**
<<<<<<< HEAD
     * Publish a message to subscribers
     */
    public void publish(Message message) {
        if (message == null || message.getType() == null)
            return;

        List<Consumer<Message>> handlers = subscribers.get(message.getType());
        if (handlers != null && !handlers.isEmpty()) {
            // Process simple messages directly to avoid thread pool overhead
            if (isSimpleMessage(message.getType())) {
                notifySubscribersDirectly(message, handlers);
            } else {
                messageProcessorPool.execute(() -> notifySubscribersDirectly(message, handlers));
            }
        }
    }

    // Helper method to identify simple messages that can be handled synchronously
    private boolean isSimpleMessage(MessageType type) {
        return type == MessageType.PING || type == MessageType.PONG;
    }

    // Process handlers directly without additional thread creation
    private void notifySubscribersDirectly(Message message, List<Consumer<Message>> handlers) {
        for (Consumer<Message> handler : handlers) {
            try {
                handler.accept(message);
            } catch (Exception e) {
                logger.error("Error in handler: {}", e.getMessage());
            }
        }
    }

    /**
=======
>>>>>>> 37a880c (feat(application)!: passive replication)
     * Process a received message asynchronously
     */
    public void receive(Message message) {
        if (shutdownRequested) {
            return;
        }

        // Only log debug info if verbose logging is enabled
        if (verboseLogging) {
            logger.debug("Incoming: {} from {} to {}",
                    message.getType(), message.getSender(), message.getRecipient());
        }

        List<Consumer<Message>> handlers = subscribers.get(message.getType());
        if (handlers != null && !handlers.isEmpty()) {
            // Process simple messages directly
            if (isSimpleMessage(message.getType())) {
                notifySubscribersDirectly(message, handlers);
            } else {
                messageProcessorPool.submit(() -> notifySubscribersDirectly(message, handlers));
            }
        }
    }

    public String setComponentName(String componentName) {
        return this.componentName = componentName;
    }

    public void shutdown() {
        shutdownRequested = true;

        try {
            messageProcessorPool.shutdown();
            if (!messageProcessorPool.awaitTermination(2, TimeUnit.SECONDS)) {
                messageProcessorPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String getComponentName() {
        return componentName;
    }
}