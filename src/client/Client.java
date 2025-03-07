package client;

import java.net.InetAddress;
import java.net.Socket;

public class Client {
    Socket socket;
    InetAddress inet;
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 11110;
    private ImplClient implClient;

    public Client() {
        this.run();
    }

    private void run() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            inet = socket.getInetAddress();

            System.out.println("HostAddress -> " + inet.getHostAddress());
            System.out.println("HostName    -> " + inet.getHostName() + "\n");

            implClient = new ImplClient(socket);
            Thread thread = new Thread(implClient);
            thread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public void disconnect() {
        try {
            if (implClient != null) {
                implClient.sendMessage("ACTION_DISCONNECT:" + implClient.getUsername());
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ImplClient getImplClient() {
        return implClient;
    }

    public static void main(String[] args) {
        new Client();
    };
}