package main.client.message;

import main.client.ImplClient;
import main.shared.messages.Message;

public class ServerInfoHandler implements ServiceMessage {
    @Override
    public void handle(Message message, ImplClient client) {
        if (!client.isMessageForThisClient(message))
            return;

        if (message.getPayload() instanceof String[]) {
            String[] serverInfo = (String[]) message.getPayload();
            if (serverInfo.length >= 2) {
                String host = serverInfo[0];

                try {
                    int port = Integer.parseInt(serverInfo[1]);
                    client.getLogger().info("Redirecting to server {}:{}", host, port);

                    // Update Lanterna UI
                    if (client.getLanternaUI() != null) {
                        client.getLanternaUI().updateStatus("Redirecting to server " + host + ":" + port);
                    }
                    // Redirect could take time, so handle in a separate thread
                    // to avoid blocking the thread pool
                    new Thread(() -> {

                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            client.getLogger().error("Error while redirecting", e);
                            e.printStackTrace();
                        }
                        client.redirect(serverInfo);
                    }, "redirect-thread").start();

                } catch (NumberFormatException e) {
                    client.getLogger().error("Invalid port number in server info: {}", serverInfo[1]);

                    // Show error in UI
                    if (client.getLanternaUI() != null) {
                        client.getLanternaUI().showError("Invalid port number: " + serverInfo[1]);
                    }
                }
            } else {
                client.getLogger().error("Incomplete server info");

                // Show error in UI
                if (client.getLanternaUI() != null) {
                    client.getLanternaUI().showError("Incomplete server information received");
                }
            }
        } else {
            client.getLogger().error("Invalid server info format");

            // Show error in UI
            if (client.getLanternaUI() != null) {
                client.getLanternaUI().showError("Invalid server information format");
            }
        }
    }
}