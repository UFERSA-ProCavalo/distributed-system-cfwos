package server;

import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    ServerSocket socketServer;
    Socket socketClient;

    public static final int SERVER_PORT = 12345;

    public Server() {
        this.run();
    }

    private void run() {
        try {
            socketServer = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port " + SERVER_PORT);

            while (true) {
                socketClient = socketServer.accept();
                System.out.println("Client connected!");

                serverHandler server = new serverHandler(socketClient);
                Thread thread = new Thread(server);
                serverHandler.connectionCount++;
                thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Server();
    }
}
