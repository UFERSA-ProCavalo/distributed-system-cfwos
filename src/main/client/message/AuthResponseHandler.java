package main.client.message;

import main.client.ImplClient;
import main.client.MenuState; // Changed from State to MenuState
import main.shared.messages.Message;

public class AuthResponseHandler implements ServiceMessage {
    @Override
    public void handle(Message message, ImplClient client) {
        if (!client.isMessageForThisClient(message))
            return;

        // Check if the payload is a String for simple auth response
        if (message.getPayload() instanceof String) {
            String payload = (String) message.getPayload();
            boolean success = payload.equals(ImplClient.AUTH_SUCCESS);

            // Thread-safe state updates
            synchronized (client) {
                client.setAuthenticated(success);
            }

            // Increment login counter if failed
            if (!success) {
                client.getLoginTries().incrementAndGet();
            }

            client.getLogger().info("Authentication {}", success ? "successful" : "failed");

            // Notify UI of authentication result
            client.notifyUIAuthenticated(success);

            // Handle too many failed attempts
            if (!success && client.getLoginTries().get() >= 3) {
                client.getLogger().error("Too many login attempts. Disconnecting.");

                // Show error in UI before disconnecting
                if (client.getLanternaUI() != null) {
                    client.getLanternaUI().showError("Too many login attempts. Disconnecting.");
                }

                client.shutdown();
            }
        } else {
            client.getLogger().error("Invalid auth response format");

            if (client.getLanternaUI() != null) {
                client.getLanternaUI().showError("Invalid authentication response from server");
            }
        }
    }
}