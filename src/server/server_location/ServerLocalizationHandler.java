package server.server_location;

import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

public class ServerLocalizationHandler implements Runnable {
    private Socket clientSocket;
    private Map<String, String> serverAddresses;
    public static int connectionCount = 0;

    public ServerLocalizationHandler(Socket clientSocket, Map<String, String> serverAddresses) {
        this.clientSocket = clientSocket;
        this.serverAddresses = serverAddresses;
    }

    public void run() {

        String clientIP = clientSocket.getInetAddress().getHostAddress();
        int clientPORT = clientSocket.getPort();
        String clientID = clientIP + ":" + clientPORT;
        connectionCount++;

        System.out.println("\n"
                + "[INFO] Client [" + clientID + "] connected to localization server!");
        System.out.println("[INFO] Client information ");

        System.out.println("[INFO] Client           :" + connectionCount);
        System.out.println("[INFO] Client address   : " + clientSocket.getInetAddress().getHostAddress());
        System.out.println("[INFO] Client port      : " + clientSocket.getPort());
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            String request = (String) in.readObject();
            System.out.println("\n"
                    + "[INFO] Received request  : " + request
                    + " from Client [" + clientID + "]");

            // Redirect to proxy server if request is "START_CONNECTION"
            if (request.equals("START_CONNECTION")) {

                String[] Server = serverAddresses.get("ProxyServer").split(":");
                String address = Server[1];
                int port = Integer.parseInt(Server[2]);
                out.writeObject(address + ":" + port);
                System.out.println("[INFO] Sent response     :" + address + ":" + port);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}