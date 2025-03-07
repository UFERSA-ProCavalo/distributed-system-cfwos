package client.gui;

import javax.swing.*;
import java.awt.*;
import client.ImplClient;

public class HomePanel extends JPanel {
    private MainMenu mainMenu;
    private JLabel welcomeLabel;
    private JTextArea messageArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton disconnectButton;
    private ImplClient implClient;

    public HomePanel(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
        setLayout(new BorderLayout(10, 10));
        
        // Welcome panel
        JPanel topPanel = new JPanel();
        welcomeLabel = new JLabel("");
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 18));
        topPanel.add(welcomeLabel);
        
        // Message area
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageArea);
        
        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputField = new JTextField();
        sendButton = new JButton("Send");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        // Button panel
        JPanel buttonPanel = new JPanel();
        disconnectButton = new JButton("Disconnect");
        buttonPanel.add(disconnectButton);

        // Add components
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
        add(buttonPanel, BorderLayout.EAST);

        // Add listeners
        sendButton.addActionListener(e -> sendMessage());
        disconnectButton.addActionListener(e -> mainMenu.disconnect());
        inputField.addActionListener(e -> sendMessage());
    }

    private void sendMessage() {
        if (implClient != null) {
            String message = inputField.getText().trim();
            if (!message.isEmpty()) {
                implClient.sendMessage(message);
                inputField.setText("");
            }
        }
    }

    public void setClient(ImplClient client) {
        this.implClient = client;
        client.setMessageDisplay(this::displayMessage);
    }

    public void displayMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            messageArea.append(message + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    public void setWelcomeMessage(String username) {
        welcomeLabel.setText("Welcome, " + username + "!");
    }
}
