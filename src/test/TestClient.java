package test;

import java.net.Socket;

import main.client.ImplClient;
import main.shared.log.Logger;
import main.shared.messages.MessageType;

public class TestClient {
    public static void main(String[] args) {
        Logger logger = Logger.getLogger();

        try {
            logger.info("Starting test client...");

            // Connect to the test server
            Socket socket = new Socket("localhost", 11110);
            ImplClient client = new ImplClient(socket, logger);

            // Start client thread
            Thread clientThread = new Thread(client);
            clientThread.start();

            // Wait for connection establishment
            Thread.sleep(1000);

            // Perform authentication
            String[] credentials = { "admin", "admin123" };
            client.sendMessage(MessageType.AUTH_REQUEST, credentials);

            // Wait for auth to complete
            Thread.sleep(1000);

            // Send a test request
            client.sendMessage(MessageType.DATA_REQUEST, "SHOW");

            // Wait a bit
            Thread.sleep(2000);

            // Send another test request
            client.sendMessage(MessageType.DATA_REQUEST, "ADD|999|Test Order|This is a test order");

            // Wait a bit
            Thread.sleep(2000);

            // Search for the added order
            client.sendMessage(MessageType.DATA_REQUEST, "SEARCH|999");

            // Keep the application running
            clientThread.join();

        } catch (Exception e) {
            logger.error("Error in test client", e);
        }
    }
}
