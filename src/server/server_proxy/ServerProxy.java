package server.server_proxy;

import java.net.ServerSocket;
import java.net.Socket;

import server.server_proxy.auth.AuthService;

public class ServerProxy {
    private ServerSocket socketServer;
    private Socket socketClient;
    private AuthService authService;

    public static final int SERVER_PORT = 11111;

    public ServerProxy() {
        this.authService = AuthService.getInstance();
        this.run();
    }

    private void run() {
        try {
            socketServer = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port " + SERVER_PORT);

            while (true) {
                socketClient = socketServer.accept();
                System.out.println("Client connected!");

                ServerProxyHandler server = new ServerProxyHandler(socketClient, authService);
                Thread thread = new Thread(server);
                ServerProxyHandler.connectionCount++;
                ServerProxyHandler.activeConnections++;
                thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new ServerProxy();
    }
}
