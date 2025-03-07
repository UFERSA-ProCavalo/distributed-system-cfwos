package server;

import java.net.Socket;
import java.util.Scanner;

public class serverHandler implements Runnable {

    private Socket socketClient;
    public static int connectionCount = 0;
    // private static final int SERVER_PORT = 12345;
    private boolean connection = true;
    private Scanner s = null;

    public serverHandler(Socket client) {
        socketClient = client;
    }

    public void run() {

        String message;

        System.out.println("Connection -> " + connectionCount + "\n"
                + "Client connected address -> " + socketClient.getInetAddress().getHostAddress() + "\n"
                + "Client connected hostname -> " + socketClient.getInetAddress().getHostName() + "\n");

        try {
            s = new Scanner(socketClient.getInputStream());

            while (connection) {
                message = s.nextLine();

                if (message.equalsIgnoreCase("exit")) {
                    connection = false;
                    System.out.println("Connection ended by user:");
                } else{
                    System.out.println(message);
                }

                //System.out.println("Client says: " + message);
            }

            s.close();
            socketClient.close();

            System.out.println("Connection ended. \n"
                    + "Client disconnected address -> " + socketClient.getInetAddress().getHostAddress() + "\n"
                    + "Client disconnected hostname -> " + socketClient.getInetAddress().getHostName() + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}