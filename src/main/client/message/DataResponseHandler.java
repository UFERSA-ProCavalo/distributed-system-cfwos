package main.client.message;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import main.client.ImplClient;
import main.client.gui.LanternaUI;
import main.shared.messages.Message;
import main.shared.utils.TypeUtil;

public class DataResponseHandler implements ServiceMessage {
    @Override
    public void handle(Message message, ImplClient client) {
        if (!client.isMessageForThisClient(message))
            return;

        Object payload = message.getPayload();
        client.getLogger().debug("Received data response: {}", payload);

        // Handle map responses (most common case)
        if (payload instanceof Map) {
            Optional<Map<String, String>> responseMapOpt = TypeUtil.safeCastToMap(
                    payload, String.class, String.class);

            if (responseMapOpt.isPresent()) {
                Map<String, String> responseMap = responseMapOpt.get();
                displayResponseMapInUI(responseMap, client);
            } else {
                if (client.getLanternaUI() != null) {
                    client.getLanternaUI().showError("Invalid response format");
                }
                client.getLogger().error("Failed to cast response map");
            }
        } else {
            // Handle string or other payload types
            String content = payload != null ? payload.toString() : "No content";

            // Display in Lanterna UI
            if (client.getLanternaUI() != null) {
                client.getLanternaUI().showResponse(content); // Replace displayResponse with showResponse
                client.getLanternaUI().updateStatus("Received data response"); // Instead of addLogMessage
            } else {
                client.getLogger().info("Received data response: {}", content);
            }
        }
    }

    private void displayResponseMapInUI(Map<String, String> responseMap, ImplClient client) {
        // Get status and message if present
        String status = responseMap.getOrDefault("status", "unknown");
        String message = responseMap.getOrDefault("message", null);

        // Format the response
        StringBuilder responseContent = new StringBuilder();

        if (message != null) {
            responseContent.append("Message: ").append(message).append("\n\n");
        }

        // Work order details
        if (responseMap.containsKey("code")) {
            responseContent.append("Work Order Details:\n");
            responseContent.append("  Code: ").append(responseMap.get("code")).append("\n");
            responseContent.append("  Name: ").append(responseMap.get("name")).append("\n");
            responseContent.append("  Description: ").append(responseMap.get("description")).append("\n");
            responseContent.append("  Timestamp: ").append(responseMap.get("timestamp")).append("\n");
        }

        // Display cache info if present
        if (responseMap.containsKey("cacheInfo")) {
            responseContent.append("\nCache Contents:\n");
            responseContent.append(responseMap.get("cacheInfo")).append("\n");
        }

        // Display work orders if present
        if (responseMap.containsKey("workOrders")) {
            responseContent.append("\nWork Orders:\n");
            responseContent.append(responseMap.get("workOrders")).append("\n");
        }

        // If there's nothing specific, just show all fields
        if (!responseMap.containsKey("code") && !responseMap.containsKey("cacheInfo") &&
                !responseMap.containsKey("workOrders") && message == null) {

            responseContent.append("Response Data:\n");
            for (Map.Entry<String, String> entry : responseMap.entrySet()) {
                responseContent.append("  ").append(entry.getKey())
                        .append(": ").append(entry.getValue()).append("\n");
            }
        }

        // Display in UI
        if (client.getLanternaUI() != null) {
            String title = status.equalsIgnoreCase("error") ? "Error" : "Success";
            client.getLanternaUI().displayResponse(title, responseContent.toString());

            // Update status bar
            if (status.equalsIgnoreCase("error")) {
                client.getLanternaUI().updateStatus("Error: " + (message != null ? message : "Request failed"));
            } else {
                client.getLanternaUI().updateStatus("Request completed successfully");
            }
        } else {
            client.getLogger().info("Response data: {}", responseContent.toString());
        }
    }
}