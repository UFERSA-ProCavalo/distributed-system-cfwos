package client.gui;

import javax.swing.*;
import java.awt.*;
import client.Client;

public class MainMenu extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private LoginPanel loginPanel;
    private HomePanel homePanel;
    private Client client;

    public MainMenu() {
        client = new Client(this);
        setTitle("Work Order Service Client");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        loginPanel = new LoginPanel(this);
        homePanel = new HomePanel(this);

        mainPanel.add(loginPanel, "LOGIN");
        mainPanel.add(homePanel, "HOME");

        add(mainPanel);
        showLoginScreen();
    }

    public void showLoginScreen() {
        cardLayout.show(mainPanel, "LOGIN");
    }

    public void showHomeScreen(String username) {
        homePanel.setWelcomeMessage(username);
        cardLayout.show(mainPanel, "HOME");
    }

    public boolean connectToServer(String username, String password) {
        try {
            client = new Client(this);
            boolean connected = client.connect(username, password);
            if (connected && client.getImplClient() != null) {
                homePanel.setClient(client.getImplClient());
                showHomeScreen(username);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            loginPanel.enableLoginButton();
        }
        return false;
    }

    public void disconnect() {
        if (client != null) {
            client.disconnect();
        }
        showLoginScreen();
    }
}
