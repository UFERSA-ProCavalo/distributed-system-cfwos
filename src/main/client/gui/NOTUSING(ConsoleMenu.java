package main.client.gui;

import java.util.Scanner;

import main.client.ImplClient;
import main.client.MenuState;
import main.shared.log.Logger;
import main.shared.messages.MessageType;

public class ConsoleMenu {
    private final ImplClient client;
    private final Scanner scanner;
    private final Logger logger;
    private boolean running = true;
    private MenuState menuState = MenuState.LOGIN;

    public ConsoleMenu(ImplClient client, Scanner scanner, Logger logger) {
        this.client = client;
        this.scanner = scanner;
        this.logger = logger;
        client.setConsoleMenu(this);
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void displayMenu() {
        System.out.println("\033[2J\033[1;1H"); // Clear screen

        switch (menuState) {
            case LOGIN:
                displayLoginMenu();
                break;
            case MAIN_MENU:
                displayMainMenu();
                break;
            case WORK_ORDER_MENU:
                // Currently unused, could be implemented for advanced work order operations
                displayMainMenu(); // Fall back to main menu
                break;
        }
    }

    public void displayLoginMenu() {
        System.out.println("\n===== Client Login =====");
        System.out.println("Please enter your credentials:");
        System.out.println("Login attempts: " + client.getLoginTries().get() + "/3");

        // Start authentication flow
        String[] credentials = getCredentials();
        if (credentials != null) {
            client.sendMessage(MessageType.AUTH_REQUEST, credentials);
        }
    }

    public void displayMainMenu() {
        System.out.println("\n===== Work Order Management System =====");
        System.out.println("Connected to: " + client.getServerAddress());
        System.out.println("\nClient Menu:");
        System.out.println("[1]. Add Work Order");
        System.out.println("[2]. Remove Work Order");
        System.out.println("[3]. Update Work Order");
        System.out.println("[4]. Search Work Order");
        System.out.println("[5]. Show all Work Orders");
        System.out.println("[6]. Show Database Stats");
        System.out.println("[7]. Logout");
        System.out.println("[8]. Exit");
        System.out.println("[0]. Insert 20 sample work orders to cache");
        System.out.println("[9]. Insert 60 sample work orders to database");
        System.out.print("\nChoose an option: ");
    }

    // Called by message handlers to redisplay the prompt after processing
    public void displayPrompt() {
        if (menuState == MenuState.MAIN_MENU) {
            System.out.print("\nChoose an option: ");
        }
    }

    public void processMenuChoice() {
        try {
            // Only process menu choices in MAIN_MENU state
            if (menuState != MenuState.MAIN_MENU) {
                return;
            }

            char choice = scanner.next().charAt(0);
            scanner.nextLine(); // Consume newline

            switch (choice) {
                case '1': // Add work order
                    handleAddWorkOrder();
                    break;
                case '2': // Remove work order
                    handleRemoveWorkOrder();
                    break;
                case '3': // Update work order
                    handleUpdateWorkOrder();
                    break;
                case '4': // Search work order
                    handleSearchWorkOrder();
                    break;
                case '5': // Show all work orders
                    client.sendMessage(MessageType.DATA_REQUEST, "SHOW");
                    break;
                case '6': // Show stats
                    client.sendMessage(MessageType.DATA_REQUEST, "STATS");
                    break;
                case '7': // Logout
                    client.sendMessage(MessageType.LOGOUT_REQUEST, true);
                    break;
                case '8': // Exit
                    logger.info("Exiting application...");
                    running = false;
                    client.shutdown();
                    break;
                case '9': // Insert 60 in database
                    logger.info("Inserting 60 work orders in database...");
                    client.sendMessage(MessageType.DATA_REQUEST, "ADD60");
                    break;
                case '0': // Insert 20 in cache
                    logger.info("Inserting 20 work orders in cache...");
                    client.sendMessage(MessageType.DATA_REQUEST, "ADD20");
                    break;
                default:
                    logger.info("Invalid option. Please try again.");
                    displayMainMenu();
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing menu choice: {}", e.getMessage());
            displayPrompt();
        }
    }

    // Returns username and password as String array
    public String[] getCredentials() {
        try {
            System.out.print("Username: ");
            String username = scanner.nextLine().trim();

            System.out.print("Password: ");
            String password = scanner.nextLine().trim();

            if (username.isEmpty() || password.isEmpty()) {
                logger.error("Username and password cannot be empty");
                return null;
            }

            return new String[] { username, password };
        } catch (Exception e) {
            logger.error("Error getting credentials: {}", e.getMessage());
            return null;
        }
    }

    private void handleAddWorkOrder() {
        try {
            System.out.print("Enter code: ");
            int code = Integer.parseInt(scanner.nextLine());

            System.out.print("Enter name: ");
            String name = scanner.nextLine();

            System.out.print("Enter description: ");
            String description = scanner.nextLine();

            // Format: ADD|code|name|description
            String request = String.format("ADD|%d|%s|%s", code, name, description);
            client.sendMessage(MessageType.DATA_REQUEST, request);

        } catch (NumberFormatException e) {
            logger.error("Invalid code format: " + e.getMessage());
            displayPrompt();
        }
    }

    private void handleRemoveWorkOrder() {
        try {
            System.out.print("Enter code of work order to remove: ");
            int code = Integer.parseInt(scanner.nextLine());

            // Format: REMOVE|code
            String request = String.format("REMOVE|%d", code);
            client.sendMessage(MessageType.DATA_REQUEST, request);

        } catch (NumberFormatException e) {
            logger.error("Invalid code format: " + e.getMessage());
            displayPrompt();
        }
    }

    private void handleUpdateWorkOrder() {
        try {
            System.out.print("Enter code: ");
            int code = Integer.parseInt(scanner.nextLine());

            System.out.print("Enter updated name: ");
            String name = scanner.nextLine();

            System.out.print("Enter updated description: ");
            String description = scanner.nextLine();

            // Format: UPDATE|code|name|description|timestamp
            String request = String.format("UPDATE|%d|%s|%s|%s",
                    code, name, description, java.time.LocalDate.now().toString());
            client.sendMessage(MessageType.DATA_REQUEST, request);

        } catch (NumberFormatException e) {
            logger.error("Invalid code format: " + e.getMessage());
            displayPrompt();
        }
    }

    private void handleSearchWorkOrder() {
        try {
            System.out.print("Enter code to search: ");
            int code = Integer.parseInt(scanner.nextLine());

            // Format: SEARCH|code
            String request = String.format("SEARCH|%d", code);
            client.sendMessage(MessageType.DATA_REQUEST, request);

        } catch (NumberFormatException e) {
            logger.error("Invalid code format: " + e.getMessage());
            displayPrompt();
        }
    }

    public void setMenuState(MenuState state) {
        this.menuState = state;
    }

    public MenuState getMenuState() {
        return menuState;
    }
}