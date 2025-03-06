package server.localization_server;

import java.net.ServerSocket;
import java.net.Socket;

public class LocalizationServer {
    ServerSocket serverSocket;
    Socket clienSocket;
    int port;

    public LocalizationServer(int port) {
        this.port = port;
        this.run();
    }

    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server is running on port " + port);
            while (true) {
                clienSocket = serverSocket.accept();
                System.out.println("Client connected");

                ImplServidor server = new ImplServidor(clienSocket);

                Thread t = new Thread(server);
                t.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
