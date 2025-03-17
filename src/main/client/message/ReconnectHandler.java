package main.client.message;

import main.client.ImplClient;
import main.shared.messages.Message;

/**
 * Handles RECONNECT responses from the server
 */
public class ReconnectHandler implements ServiceMessage {
    @Override
    public void handle(Message message, ImplClient client) {
        if (!client.isMessageForThisClient(message)) {
            return;
        }

        client.getLogger().info("Processing reconnection response");
        
        // Nothing specific to do here - the SERVER_INFO message will trigger
        // the redirect flow, we just log the response
        
        if (client.getLanternaUI() != null) {
            client.getLanternaUI().updateStatus("Reconnection response received");
        }
    }
}