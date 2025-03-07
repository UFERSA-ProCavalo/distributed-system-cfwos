package client;

import java.net.InetAddress;
import java.net.Socket;
import client.gui.MainMenu;
import javax.swing.SwingUtilities;

public class Client {
    Socket socket;
    InetAddress inet;
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 11110;
    private MainMenu mainMenu;
    private ImplClient implClient;

    public Client(MainMenu mainMenu) {
        this.run();
        this.mainMenu = mainMenu;
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

    public boolean connect(String username, String password) {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            inet = socket.getInetAddress();

            System.out.println("HostAddress -> " + inet.getHostAddress());
            System.out.println("HostName    -> " + inet.getHostName() + "\n");

            implClient = new ImplClient(socket, username, password);
            Thread thread = new Thread(implClient);
            thread.start();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        try {
            if (implClient != null) {
                implClient.sendMessage("exit");
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
        SwingUtilities.invokeLater(() -> {
            MainMenu mainMenu = new MainMenu();
            mainMenu.setVisible(true);
        });
    }
}