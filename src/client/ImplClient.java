package client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;
import java.util.function.Consumer;

public class ImplClient implements Runnable {

    private Socket socket;
    private boolean connection = false;
    private PrintStream output;
    private Consumer<String> messageDisplay;
    private String username;
    private String password;

    public ImplClient(Socket client) {
        this.socket = client;
    }

    public void setMessageDisplay(Consumer<String> messageDisplay) {
        this.messageDisplay = messageDisplay;
    }

    public void sendMessage(String message) {
        if (message.equalsIgnoreCase("exit")) {
            connection = false;
            System.out.println("Connection ended by user!");
        } else {
            output.println(message);
        }
    }

    public void run() {
        try {
            startConnection();

            while (connection) {
                try {
                    if (messageDisplay != null && output != null) {
                        Thread.sleep(100); // Prevent busy waiting
                    }
                } catch (Exception e) {
                    connection = false;
                    System.out.println("Connection lost. Unable to send message.");
                }
            }

            output.close();
            socket.close();
            System.out.println("Connection ended.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startConnection() {

        try {
            // First connect to localization server
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            out.writeObject("START_CONNECTION");
            out.flush();

            // Get proxy server details
            String response = (String) in.readObject();
            String[] responseArray = response.split(":");
            Socket proxySocket = new Socket(responseArray[0], Integer.parseInt(responseArray[1]));
            socket.close();
            System.out.println("verificando se o socket está fechado");
            System.out.println(socket.isClosed());
            socket = proxySocket;
            System.out.println(socket.isClosed());
            // output = new PrintStream(proxySocket.getOutputStream());
            connection = true;

            // Authenticate with proxy server
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter username: ");
            username = scanner.nextLine();
            System.out.print("Enter password: ");
            password = scanner.nextLine();
            scanner.close();

            // send the credentials to the proxy server
            out = new ObjectOutputStream(proxySocket.getOutputStream());
            out.writeObject("LOGIN:" + username + ":" + password);
            out.flush();

            // receive response from the proxy server
            in = new ObjectInputStream(proxySocket.getInputStream());
            String authResponse = (String) in.readObject();
            if (authResponse.equals("SUCCESS")) {

                System.out.println("Successfully authenticated and connected to proxy server");
            } else {
                throw new Exception("Authentication failed: " + authResponse);
            }

        } catch (Exception e) {
            e.printStackTrace();

            connection = false;
        }
    }

    public String getUsername() {
        return username;
    }

    public void connect(String username, String password) {
        try {

            setMessageDisplay(message -> {
                System.out.println("Message from server: " + message);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
