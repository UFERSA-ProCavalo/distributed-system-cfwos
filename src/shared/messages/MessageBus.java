package shared.messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import shared.log.Logger;

public class MessageBus {

    private final Logger logger;
    private final Map<String, List<IMessageHandler>> handlers = new HashMap<>();
    private final Map<String, List<Consumer<Message>>> subscribers = new HashMap<>();
    private String componentName;

    public MessageBus(String componentName, Logger logger) {
        this.logger = logger;
        this.componentName = componentName;
        logger.info("MessageBus initialized for component: " + componentName);
    }

    /**
     * Register a handler for specific message types
     */
    public void registerHandler(IMessageHandler handler) {
        synchronized (handlers) {
            for (String type : handler.getHandledTypes()) {
                handlers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
                logger.info("Registered handler for message type: " + type);
            }
        }
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

    }

    /**
     * Send a message and wait for handlers to process it
     */
    public void sendSync(Message message) {
        processMessage(message);
    }

    /**
     * Create and send a new message
     */
    public void send(String type, String recipient, Object payload) {
        send(new Message(type, componentName, recipient, payload));
    }

    private void processMessage(Message message) {
        logger.info("Processing message: " + message);

        // First notify subscribers
        notifySubscribers(message);

        // Then process with handlers if addressed to this component
        if (componentName.equals(message.getRecipient()) ||
                message.getRecipient() == null ||
                message.getRecipient().isEmpty()) {

            processWithHandlers(message);
        }
    }

    private void notifySubscribers(Message message) {
        List<Consumer<Message>> typeSubscribers;
        synchronized (subscribers) {
            typeSubscribers = subscribers.getOrDefault(message.getType(), List.of());
        }

        for (Consumer<Message> subscriber : typeSubscribers) {
            try {
                subscriber.accept(message);
            } catch (Exception e) {
                logger.error("Error in message subscriber", e);
            }
        }
    }

    private void processWithHandlers(Message message) {
        List<IMessageHandler> typeHandlers;
        synchronized (handlers) {
            typeHandlers = handlers.getOrDefault(message.getType(), List.of());
        }

        for (IMessageHandler handler : typeHandlers) {
            if (handler.isValidMessage(message.getType())) {
                try {
                    Message response = handler.handleMessage(message);
                    if (response != null) {
                        send(response);
                    }
                } catch (Exception e) {
                    logger.error("Error in message handler", e);
                }
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
// Compare this snippet from src/server/server_proxy/ServerProxyHandler.java:
// logger.info("ServerProxyHandler cleanup completed");