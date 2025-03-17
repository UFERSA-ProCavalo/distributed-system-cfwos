package main.client.message;

import main.client.ImplClient;
import main.shared.messages.Message;

public class ErrorHandler implements ServiceMessage {
    @Override
    public void handle(Message message, ImplClient client) {
        String errorMsg = (message.getPayload() != null) ? message.getPayload().toString() : "Unknown error";

        client.getLogger().error("Received error message: {}", errorMsg);

        // Update Lanterna UI
        if (client.getLanternaUI() != null) {
            client.getLanternaUI().updateStatus("Error: " + errorMsg);
            client.getLanternaUI().showError(errorMsg);
        }
    }
}