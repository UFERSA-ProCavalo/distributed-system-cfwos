package server.server_location;

import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

public class ServerLocalizationHandler implements Runnable {
    private Socket clientSocket;
    private Map<String, String> serverAddresses;

    public ServerLocalizationHandler(Socket clientSocket, Map<String, String> serverAddresses) {
        this.clientSocket = clientSocket;
        this.serverAddresses = serverAddresses;
    }

    public void run() {

        System.out.println("|Client connected to localization server!");
        System.out.println("|Client information ");
        System.out.println("|Client address: " + clientSocket.getInetAddress().getHostAddress());
        System.out.println("|Client port: " + clientSocket.getPort());
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            String request = (String) in.readObject();
            System.out.println("|Received request: " + request);

            // Redirect to proxy server if request is "START_CONNECTION"
            if (request.equals("START_CONNECTION")) {

                String[] Server = serverAddresses.get("ProxyServer").split(":");
                String address = Server[1];
                int port = Integer.parseInt(Server[2]);
                out.writeObject(address + ":" + port);
                System.out.println("|Sent response: " + address + ":" + port);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}