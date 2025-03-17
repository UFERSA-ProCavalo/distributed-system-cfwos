package main.shared.messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import main.shared.log.Logger;

public class MessageBus {
    // Thread pool for message processing
    private final ExecutorService messageProcessorPool;
    private final Logger logger;
    // Changed from String to MessageType
    private final Map<MessageType, List<Consumer<Message>>> subscribers = new HashMap<>();
    private String componentName;
    private boolean shutdownRequested = false;

    public MessageBus(String componentName, Logger logger) {
        this(componentName, logger, Runtime.getRuntime().availableProcessors());
    }

    public MessageBus(String componentName, Logger logger, int threadPoolSize) {
        this.logger = logger;
        this.componentName = componentName;
        this.messageProcessorPool = Executors.newFixedThreadPool(threadPoolSize);
        logger.info("MessageBus initialized for component: {} with {} threads",
                componentName, threadPoolSize);
    }

    /**
     * Subscribe to a message type
     */
    public void subscribe(MessageType type, Consumer<Message> handler) {
        subscribers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
    }

    /**
     * Unsubscribe from a specific message type
     */
    public void unsubscribe(MessageType messageType, Consumer<Message> callback) {
        synchronized (subscribers) {
            List<Consumer<Message>> callbacks = subscribers.get(messageType);
            if (callbacks != null) {
                callbacks.remove(callback);
                logger.info("Removed subscription for message type: {}", messageType);
            }
        }
    }

    public void unsubscribeAll() {
        synchronized (subscribers) {
            subscribers.clear();
            logger.info("Removed all subscriptions");
        }
    }

    /**
     * Send a message asynchronously
     */
    public void send(Message message) {
        if (shutdownRequested) {
            logger.warning("MessageBus is shutting down, message discarded: {}", message);
            return;
        }

        messageProcessorPool.submit(() -> {
            try {
                logger.info("Processing outgoing message: {} from {} to {}",
                        message.getType(), message.getSender(), message.getRecipient());
                // No need to process outgoing messages
            } catch (Exception e) {
                logger.error("Error processing outgoing message", e);
            }
        });
    }

    /**
     * Publish a message to subscribers
     */
    public void publish(Message message) {
        if (message == null || message.getType() == null)
            return;

        List<Consumer<Message>> handlers = subscribers.get(message.getType());
        if (handlers != null) {
            for (Consumer<Message> handler : handlers) {
                messageProcessorPool.execute(() -> {
                    try {
                        handler.accept(message);
                    } catch (Exception e) {
                        logger.error("Error processing message: {}", e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Process a received message asynchronously
     */
    public void receive(Message message) {
        if (shutdownRequested) {
            logger.warning("MessageBus is shutting down, message discarded: {}", message);
            return;
        }

        messageProcessorPool.submit(() -> {
            try {
                logger.info("Processing incoming message: {} from {} to {}",
                        message.getType(), message.getSender(), message.getRecipient());
                notifySubscribers(message);
            } catch (Exception e) {
                logger.error("Error processing incoming message", e);
            }
        });
    }

    private void notifySubscribers(Message message) {
        List<Consumer<Message>> typeSubscribers;
        synchronized (subscribers) {
            typeSubscribers = new ArrayList<>(
                    subscribers.getOrDefault(message.getType(), Collections.emptyList()));

        }

        for (Consumer<Message> subscriber : typeSubscribers) {
            try {
                subscriber.accept(message);
            } catch (Exception e) {
                logger.error("Error in message subscriber", e);
            }
        }
    }

    /**
     * Set the component name
     */
    public String setComponentName(String componentName) {
        return this.componentName = componentName;
    }

    /**
     * Shutdown the message bus gracefully
     */
    public void shutdown() {
        shutdownRequested = true;

        try {
            logger.info("Shutting down MessageBus thread pool...");
            messageProcessorPool.shutdown();
            if (!messageProcessorPool.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("MessageBus thread pool did not terminate in time, forcing shutdown");
                messageProcessorPool.shutdownNow();
            }
            logger.info("MessageBus thread pool shut down successfully");
        } catch (InterruptedException e) {
            logger.error("MessageBus shutdown interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get the component name
     */
    public String getComponentName() {
        return componentName;
    }
}