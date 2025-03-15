package main.client.gui;

import java.util.Scanner;

import main.client.ImplClient;
import main.server.proxy.ProxyServer;
import main.shared.log.Logger;
import main.shared.messages.MessageType;
import main.shared.models.WorkOrder;

public class ConsoleMenu {
    private final ImplClient client;
    private final Scanner scanner;
    private final Logger logger;
    private boolean running = true;

    public ConsoleMenu(ImplClient client, Scanner scanner, Logger logger) {
        this.client = client;
        this.scanner = scanner;
        this.logger = logger;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void displayMenu() {
        System.out.println("\nClient Menu:");
        System.out.println("[1]. Add Work Order");
        System.out.println("[2]. Remove Work Order");
        System.out.println("[3]. Update Work Order");
        System.out.println("[4]. Search Work Order");
        System.out.println("[5]. Show all Work Orders");
        System.out.println("[6]. Show Database Stats");
        System.out.println("[7]. Exit");
        System.out.print("Choose an option: ");
    }

    public void processMenuChoice() {
        try {
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
                case '7': // Exit
                    logger.info("Exiting application...");
                    running = false;
                    break;
                case '9':
                    logger.info("Inserting 60 work orders in database...");
                    insert60inDatabase();
                    break;
                case '0':
                    logger.info("Inserting 20 work orders in cache...");
                    insert20inCache();
                    break;
                default:
                    logger.info("Invalid option. Please try again.");
                    break;
            }
            System.out.println("\033[2J\033[1;1H"); // Clear screen
        } catch (Exception e) {
            logger.error("Error processing menu choice: {}", e.getMessage());
        }
    }

    private void insert60inDatabase() {
        client.sendMessage(MessageType.DATA_REQUEST, String.format("ADD60"));
    }

    private void insert20inCache() {
        for (int i = 0; i < 20; i++) {
            client.sendMessage(MessageType.DATA_REQUEST, String.format("ADD20"));
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
                    code, name, description, java.time.LocalDateTime.now().toString());
            client.sendMessage(MessageType.DATA_REQUEST, request);

        } catch (NumberFormatException e) {
            logger.error("Invalid code format: " + e.getMessage());
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
        }
    }
}