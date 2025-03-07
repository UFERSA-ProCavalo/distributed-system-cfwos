package client.gui;

import javax.swing.*;
import java.awt.*;

public class LoginPanel extends JPanel {
    private MainMenu mainMenu;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;

    public LoginPanel(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // Title
        JLabel titleLabel = new JLabel("Login");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(titleLabel, gbc);

        // Username field
        JLabel usernameLabel = new JLabel("Username:");
        usernameField = new JTextField(20);
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        add(usernameLabel, gbc);
        gbc.gridx = 1;
        add(usernameField, gbc);

        // Password field
        JLabel passwordLabel = new JLabel("Password:");
        passwordField = new JPasswordField(20);
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(passwordLabel, gbc);
        gbc.gridx = 1;
        add(passwordField, gbc);

        // Login button
        loginButton = new JButton("Login");
        loginButton.setPreferredSize(new Dimension(200, 40));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        add(loginButton, gbc);

        loginButton.addActionListener(e -> login());
    }

    public void enableLoginButton() {
        loginButton.setEnabled(true);
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (!username.isEmpty() && !password.isEmpty()) {
            loginButton.setEnabled(false);
            if (mainMenu.connectToServer(username, password)) {
                usernameField.setText("");
                passwordField.setText("");
            } else {
                loginButton.setEnabled(true);
                JOptionPane.showMessageDialog(this, 
                    "Invalid credentials", 
                    "Login Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
