package server.server_proxy;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

import server.server_proxy.auth.AuthService;

public class ServerProxyHandler implements Runnable {

    private Socket socketClient;
    private AuthService authService;
    public static int connectionCount = 0;
    public static int activeConnections = 0;
    private boolean connection = true;
    private Scanner s = null;

    public ServerProxyHandler(Socket client, AuthService authService) {
        this.authService = authService;
        this.socketClient = client;
    }

    public void run() {
        try {
            ObjectInputStream authIn = new ObjectInputStream(socketClient.getInputStream());
            ObjectOutputStream authOut = new ObjectOutputStream(socketClient.getOutputStream());

            String request = (String) authIn.readObject();
            if (request.startsWith("LOGIN:")) {
                String[] credentials = request.split(":", 3);
                String username = credentials[1];
                String password = credentials[2];

                if (authService.authenticate(username, password)) {
                    authOut.writeObject("SUCCESS");
                    handleClientCommunication(username);
                } else {
                    authOut.writeObject("FAIL:Invalid credentials");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClientCommunication(String username) {
        String clientAddress = socketClient.getInetAddress().getHostAddress();
        System.out.println("\n"
                + "--------------------------------------------------------------"
                + "\n"
                + "[INFO] User '" + username + "' authenticated from " + clientAddress);

        try {
            s = new Scanner(socketClient.getInputStream());

            while (connection) {
                if (s.hasNextLine()) {
                    String message = s.nextLine();
                    if (message.equalsIgnoreCase("exit")) {
                        connection = false;
                        System.out.println("Connection ended by user: " + username);
                    } else {
                        System.out.printf("[%s - %s] %s%n", username, clientAddress, message);
                    }
                } else {
                    connection = false;
                }
            }

            s.close();
            socketClient.close();
            activeConnections--;

            System.out.println("\n"
                    + "--------------------------------------------------------------"
                    + "\n"
                    + "|Connection with user " + "'" + username + "'" + "ended. \n"
                    + "|Client disconnected address -> " + clientAddress + "\n"
                    + "|Client disconnected hostname -> " + socketClient.getInetAddress().getHostName() + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}