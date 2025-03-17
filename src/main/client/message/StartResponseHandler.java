package main.client.message;

import main.client.ImplClient;
import main.shared.messages.Message;

public class StartResponseHandler implements ServiceMessage {
    @Override
    public void handle(Message message, ImplClient client) {
        if (!client.isMessageForThisClient(message))
            return;

        client.getLogger().info("Connected to server: {}", message.getSender());

        synchronized (client) {
            client.setServerAddress(message.getSender());
        }

        // Update Lanterna UI and EXPLICITLY show login screen
        if (client.getLanternaUI() != null) {
            client.getLanternaUI().updateConnectionStatus("Connected to " + message.getSender(), true);
            client.getLanternaUI().updateStatus("Connected to server");

            // Explicitly show login screen with invokeLater for thread safety
            client.getLanternaUI().invokeLater(() -> {
                client.getLanternaUI().showLoginScreen(null);
            });
        }
    }
}