package main.client.message;

import main.client.ImplClient;
import main.shared.messages.Message;

public class DisconnectHandler implements ServiceMessage {
    @Override
    public void handle(Message message, ImplClient client) {
        String reason = (message.getPayload() != null) ? message.getPayload().toString()
                : "Server initiated disconnect";

        client.getLogger().info("Disconnected from server: {}", reason);

        // Update Lanterna UI
        if (client.getLanternaUI() != null) {
            client.getLanternaUI().updateConnectionStatus("Disconnected", false);
            client.getLanternaUI().updateStatus("Disconnected from server");
            client.getLanternaUI().showAlert("Disconnected: " + reason);
        }

        // Since this will be called from a thread pool thread, we should
        // shutdown in a separate thread to avoid blocking the thread pool
        new Thread(() -> {
            client.shutdown();
        }, "shutdown-thread").start();
    }
}