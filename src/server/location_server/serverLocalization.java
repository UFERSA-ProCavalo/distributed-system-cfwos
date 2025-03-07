package server.location_server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class serverLocalization {
    private ServerSocket serverSocket;
    private static final int LOCALIZATION_PORT = 11110;
    private Map<String, String> serverAddresses;

    public serverLocalization() {
        serverAddresses = new HashMap<>();
        // Add initial server addresses
        serverAddresses.put("ProxyServer", "ProxyServer:localhost:11111");
        //serverAddresses.put("ApplicationServer", "localhost:11112");
        this.run();
    }

    private void run() {
        try {
            serverSocket = new ServerSocket(LOCALIZATION_PORT);
            System.out.println("Localization server started on port " + LOCALIZATION_PORT + "\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();

                // Handle client requests in a separate thread
                serverLocalizationHandler handler = new serverLocalizationHandler(clientSocket, serverAddresses);
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new serverLocalization();
    }
}