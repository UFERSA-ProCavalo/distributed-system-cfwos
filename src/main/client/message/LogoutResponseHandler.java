package main.client.message;

import main.client.ImplClient;
import main.shared.messages.Message;

public class LogoutResponseHandler implements ServiceMessage {
    @Override
    public void handle(Message message, ImplClient client) {
        if (!client.isMessageForThisClient(message))
            return;

        boolean success = false;
        if (message.getPayload() instanceof Boolean) {
            success = (Boolean) message.getPayload();
        }

        if (success) {
            client.getLogger().info("Logout successful");

            synchronized (client) {
                client.setAuthenticated(false);
            }

            // Update Lanterna UI
            if (client.getLanternaUI() != null) {
                client.getLanternaUI().updateStatus("Logout successful");
                client.getLanternaUI().showAlert("Logout successful");
                client.getLanternaUI().showLoginScreen(null);
            }
        } else {
            client.getLogger().warning("Logout failed");

            // Update Lanterna UI
            if (client.getLanternaUI() != null) {
                client.getLanternaUI().updateStatus("Logout failed");
                client.getLanternaUI().showError("Logout failed");
            }
        }
    }
}