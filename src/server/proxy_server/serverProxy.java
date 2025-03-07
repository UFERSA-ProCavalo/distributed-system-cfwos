package server.proxy_server;

import java.net.ServerSocket;
import java.net.Socket;

public class serverProxy {
    private ServerSocket socketServer;
    private Socket socketClient;
    private AuthenticationService authService;

    public static final int SERVER_PORT = 11111;

    public serverProxy() {
        this.run();
    }

    private void run() {
        try {
            socketServer = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port " + SERVER_PORT);

            while (true) {
                socketClient = socketServer.accept();
                System.out.println("Client connected!");

                serverProxyHandler server = new serverProxyHandler(socketClient, authService);
                Thread thread = new Thread(server);
                serverProxyHandler.connectionCount++;
                serverProxyHandler.activeConnections++;
                thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new serverProxy();
    }
}
