package main.shared.messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import main.shared.log.Logger;

public class MessageBus {

    private final Logger logger;
    private final Map<String, List<Consumer<Message>>> subscribers = new HashMap<>();
    private String componentName;

    public MessageBus(String componentName, Logger logger) {
        this.logger = logger;
        this.componentName = componentName;
        logger.info("MessageBus initialized for component: " + componentName);
    }

    /**
     * Subscribe to a specific message type
     */
    public void subscribe(String messageType, Consumer<Message> callback) {
        synchronized (subscribers) {
            if (messageType.equals("*")) {
                for (String type : MessageType.getAllTypes()) {
                    subscribers.computeIfAbsent(type, k -> new ArrayList<>()).add(callback);
                }
                subscribers.computeIfAbsent("*", k -> new ArrayList<>()).add(callback);
                logger.warning("Added wildcard subscription for all message types");
            } else {
                subscribers.computeIfAbsent(messageType, k -> new ArrayList<>()).add(callback);
                logger.info("Added subscription for message type: " + messageType);
            }
        }
    }

    /**
     * Unsubscribe from a specific message type
     */
    public void unsubscribe(String messageType, Consumer<Message> callback) {
        synchronized (subscribers) {
            List<Consumer<Message>> callbacks = subscribers.get(messageType);
            if (callbacks != null) {
                callbacks.remove(callback);
                logger.info("Removed subscription for message type: " + messageType);
            }
        }
    }

    /**
     * Send a message synchronously (previously asynchronous)
     */
    public void send(Message message) {

        processMessage(message); // Now behaves like sendSync()
        logger.info("Sent message: " + message);

    }

    /**
     * Send a message and wait for handlers to process it
     */
    public void receive(Message message) {

        logger.info("Received message: " + message);
        processMessage(message); // Now behaves like sendSync()

    }

    private void processMessage(Message message) {
        logger.info("Processing message: " + message);

        // First notify subscribers
        notifySubscribers(message);

    }

    private void notifySubscribers(Message message) {
        List<Consumer<Message>> typeSubscribers;
        synchronized (subscribers) {
            typeSubscribers = subscribers.getOrDefault(message.getType(), Collections.emptyList());
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
     * Shutdown the message bus (No longer needed, but kept for API compatibility)
     */
    public void shutdown() {
        logger.info("MessageBus shutdown for component: " + componentName);
    }

    public String getComponentName() {
        return componentName;
    }

    // Added for testing purposes
    // TODO refactor in a way that doesn't expose the setter
    public String setComponentName(String componentName) {
        return this.componentName = componentName;
    }
}