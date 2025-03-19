package main.client.gui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import main.client.ImplClient;
import main.shared.log.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LanternaUI implements Runnable {
    private final ImplClient client;
    private final Logger logger;

    // UI Components
    private Screen screen;
    private WindowBasedTextGUI gui;
    private BasicWindow mainWindow;
    private Panel mainPanel;

    // Status display components
    private Label statusLabel;
    private Label connectionLabel;

    // Message queue for thread-safe UI updates
    private final BlockingQueue<UIUpdate> uiUpdateQueue = new LinkedBlockingQueue<>();

    // Atomic flags
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    // Executor for GUI updates
    private final Executor guiUpdateExecutor = Executors.newSingleThreadExecutor();

    // Add this field
    private TextBox resultsArea;

    /**
     * Creates a new Lanterna UI attached to the specified client
     */
    public LanternaUI(ImplClient client, Logger logger) {
        this.client = client;
        this.logger = logger;
        client.setLanternaUI(this);
    }

    /**
     * Main UI thread entrypoint
     */
    @Override
    public void run() {
        try {
            initializeUI();

            // Start the UI update processing thread
            startUIUpdateProcessor();

            // Event loop - THIS IS THE CRITICAL CHANGE
            while (running.get()) {
                try {
                    // Process input BEFORE updating screen
                    gui.processInput();
                    gui.updateScreen();

                    // Force a refresh
                    if (screen != null) {
                        screen.refresh();
                    }

                    // Small pause to prevent CPU hogging
                    Thread.sleep(50);
                } catch (IOException | InterruptedException e) {
                    logger.error("Error in UI loop", e);
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        client.shutdownWithoutUI();
                        shutdown();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error initializing Lanterna UI", e);
        } finally {
            cleanup();
        }
    }

    /**
     * Initialize the Lanterna UI
     */
    private void initializeUI() throws IOException {
        // Set up terminal and screen with specific options
        DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory()
                .setInitialTerminalSize(new TerminalSize(80, 24)) // Set a reasonable initial size
                .setTerminalEmulatorTitle("Work Order Management System");

        Terminal terminal = terminalFactory.createTerminal();
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null); // Hide the cursor when not in text boxes

        // Create GUI with configured theme
        gui = new MultiWindowTextGUI(
                screen,
                new DefaultWindowManager(),
                new EmptySpace(TextColor.ANSI.BLACK));

        mainWindow = new BasicWindow("Work Order Management System");
        mainWindow.setHints(Arrays.asList(Window.Hint.CENTERED));

        // Add window listener for close events instead of using setCloseWindowHandler
        mainWindow.addWindowListener(new WindowListener() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                // Handle ESC key to close
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    logger.info("ESC key pressed, shutting down client...");
                    shutdown();
                    hasBeenHandled.set(true);
                }
            }

            @Override
            public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
                // Not used, but required by interface
            }

            @Override
            public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {
                // Not used, but required by interface
            }

            @Override
            public void onMoved(Window window, TerminalPosition oldPosition, TerminalPosition newPosition) {
                // Not used, but required by interface
            }
        });

        // Create the main layout
        createMainLayout();

        // Show initial status
        updateStatus("Starting up...");
        updateConnectionStatus("Connecting...", false);

        // Add the window to the GUI
        gui.addWindow(mainWindow);
    }

    /**
     * Create the main UI layout
     */
    private void createMainLayout() {
        mainPanel = new Panel(new LinearLayout(Direction.VERTICAL));

        // Add a header
        Label headerLabel = new Label("Work Order Management Client")
                .addStyle(SGR.BOLD).setForegroundColor(TextColor.ANSI.BLUE);
        mainPanel.addComponent(headerLabel);
        mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

        // Add status indicators
        Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
        statusLabel = new Label("Starting...");
        statusPanel.addComponent(new Label("Status: "));
        statusPanel.addComponent(statusLabel);
        mainPanel.addComponent(statusPanel);

        Panel connectionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
        connectionLabel = new Label("Connecting...")
                .setForegroundColor(TextColor.ANSI.YELLOW);
        connectionPanel.addComponent(new Label("Connection: "));
        connectionPanel.addComponent(connectionLabel);
        mainPanel.addComponent(connectionPanel);

        // Add a separator
        mainPanel.addComponent(new Separator(Direction.HORIZONTAL)
                .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)));

        // Set the main panel as the window content
        mainWindow.setComponent(mainPanel);
    }

    /**
     * Start a thread to process UI updates from the message queue
     */
    private void startUIUpdateProcessor() {
        Thread updateThread = new Thread(() -> {
            while (running.get()) {
                try {
                    UIUpdate update = uiUpdateQueue.take();
                    processUIUpdate(update);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error processing UI update", e);
                }
            }
        }, "ui-update-processor");

        updateThread.setDaemon(true);
        updateThread.start();
    }

    /**
     * Process a UI update from the queue
     */
    private void processUIUpdate(UIUpdate update) {
        try {
            switch (update.type) {
                case STATUS:
                    statusLabel.setText(update.message);
                    break;

                case CONNECTION:
                    TextColor color = update.connected ? TextColor.ANSI.GREEN : TextColor.ANSI.RED;
                    connectionLabel.setForegroundColor(color).setText(update.message);
                    break;

                case AUTHENTICATION:
                    boolean success = update.connected;
                    if (success) {
                        updateStatus("Authenticated");
                        showWorkOrderMenu();
                    } else {
                        updateStatus("Authentication failed");
                        showLoginScreen(update.message);
                    }
                    break;

                case ALERT:
                    MessageDialog.showMessageDialog(gui, "Alert", update.message);
                    break;

                case ERROR:
                    MessageDialog.showMessageDialog(gui, "Error", update.message,
                            MessageDialogButton.OK);
                    break;

                case SHOW_LOGIN:
                    showLoginScreen(null);
                    break;

                case SHOW_WORK_ORDERS:
                    showWorkOrderMenu();
                    break;

                case SERVER_RESPONSE:
                    if (resultsArea != null) {
                        resultsArea.setText(update.message);
                    } else {
                        showServerResponse(update.message);
                    }
                    break;
            }

        } catch (Exception e) {
            logger.error("Error applying UI update", e);
        }
    }

    /**
     * Show the login screen
     */
    public void showLoginScreen(String errorMessage) {
        logger.info("Showing login screen");

        invokeLater(() -> {
            try {
                // Clear existing components
                mainPanel.removeAllComponents();

                // Re-add header
                Label headerLabel = new Label("Work Order Management Client")
                        .addStyle(SGR.BOLD).setForegroundColor(TextColor.ANSI.BLUE);
                mainPanel.addComponent(headerLabel);
                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Add connection status
                Panel connectionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                connectionPanel.addComponent(new Label("Connection: "));
                connectionPanel.addComponent(connectionLabel);
                mainPanel.addComponent(connectionPanel);

                // Add a separator
                mainPanel.addComponent(new Separator(Direction.HORIZONTAL));

                // Login form
                mainPanel.addComponent(new Label("Please log in to continue"));

                if (errorMessage != null) {
                    mainPanel.addComponent(new Label(errorMessage)
                            .setForegroundColor(TextColor.ANSI.RED));
                }

                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Username field - FOCUS CHANGES HERE
                Panel usernamePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                usernamePanel.addComponent(new Label("Username: ").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox usernameField = new TextBox(new TerminalSize(20, 1));
                usernamePanel.addComponent(usernameField);
                mainPanel.addComponent(usernamePanel);

                // Password field
                Panel passwordPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                passwordPanel.addComponent(new Label("Password: ").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox passwordField = new TextBox(new TerminalSize(20, 1))
                        .setMask('*');
                passwordPanel.addComponent(passwordField);
                mainPanel.addComponent(passwordPanel);

                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Login button
                Button loginButton = new Button("Login", () -> {
                    String username = usernameField.getText();
                    String password = passwordField.getText();

                    if (username.isEmpty() || password.isEmpty()) {
                        queueUIUpdate(UIUpdate.createAlert("Username and password cannot be empty"));
                        return;
                    }

                    // Send authentication request
                    updateStatus("Authenticating...");
                    client.startAuth(username, password);
                });

                Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                buttonPanel.addComponent(loginButton);
                buttonPanel.addComponent(new Button("Exit", this::shutdown));
                mainPanel.addComponent(buttonPanel.setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));

                // Set initial focus after screen is fully built
                mainWindow.setFocusedInteractable(usernameField);

                logger.debug("Login screen displayed");
            } catch (Exception e) {
                logger.error("Error showing login screen", e);
            }
        });
    }

    /**
     * Show the work order menu
     */
    private void showWorkOrderMenu() {
        logger.info("Showing work order menu");

        invokeLater(() -> {
            try {
                // Clear existing components
                mainPanel.removeAllComponents();

                // Re-add header
                Label headerLabel = new Label("Work Order Management Client")
                        .addStyle(SGR.BOLD).setForegroundColor(TextColor.ANSI.BLUE);
                mainPanel.addComponent(headerLabel);
                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Add status displays
                Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                statusPanel.addComponent(new Label("Status: "));
                statusPanel.addComponent(statusLabel);
                mainPanel.addComponent(statusPanel);

                Panel connectionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                connectionPanel.addComponent(new Label("Connection: "));
                connectionPanel.addComponent(connectionLabel);
                mainPanel.addComponent(connectionPanel);

                // Add a separator
                mainPanel.addComponent(new Separator(Direction.HORIZONTAL));

                // Main menu buttons
                mainPanel.addComponent(new Label("Work Order Operations"));

                // Work order buttons
                Panel buttonPanel1 = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(2));
                Button addButton = new Button("Add Work Order", () -> showAddWorkOrderScreen());
                Button removeButton = new Button("Remove Work Order", () -> showRemoveWorkOrderScreen());
                Button updateButton = new Button("Update Work Order", () -> showUpdateWorkOrderScreen());

                buttonPanel1.addComponent(addButton);
                buttonPanel1.addComponent(removeButton);
                buttonPanel1.addComponent(updateButton);
                mainPanel.addComponent(buttonPanel1);

                Panel buttonPanel2 = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(2));
                Button searchButton = new Button("Search Work Order", () -> showSearchWorkOrderScreen());
                Button showAllButton = new Button("Show All Work Orders", () -> client.sendDataRequest("SHOW"));
                Button statsButton = new Button("Show Stats", () -> client.sendDataRequest("STATS"));

                buttonPanel2.addComponent(searchButton);
                buttonPanel2.addComponent(showAllButton);
                buttonPanel2.addComponent(statsButton);
                mainPanel.addComponent(buttonPanel2);
                logger.info("" + client.isAdmin());

                // Add separator
                mainPanel.addComponent(new Separator(Direction.HORIZONTAL));

                // Admin options
                if (client.isAdmin()) {
                    mainPanel.addComponent(new Label("Admin Options"));
                    Button add30ToCache = new Button("Add 30 to cache", () -> {
                        client.sendDataRequest("ADD30");
                        updateStatus("Clearing database...");
                    });
                    Button add100ToDatabase = new Button("Add 100 to database", () -> {
                        client.sendDataRequest("ADD100");
                        updateStatus("Clearing database...");
                    });

                    mainPanel.addComponent(add30ToCache);
                    mainPanel.addComponent(add100ToDatabase);

                    logger.info("Admin options displayed");

                    // Add a separator
                    mainPanel.addComponent(new Separator(Direction.HORIZONTAL));
                }

                // Results area
                mainPanel.addComponent(new Label("Server Response"));
                resultsArea = new TextBox(new TerminalSize(60, 10));
                resultsArea.setReadOnly(true);
                mainPanel.addComponent(resultsArea);

                // Control buttons
                Panel controlPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(5));
                Button logoutButton = new Button("Logout", () -> client.sendLogoutRequest());
                Button exitButton = new Button("Exit", this::shutdown);

                controlPanel.addComponent(logoutButton);
                controlPanel.addComponent(exitButton);
                mainPanel.addComponent(controlPanel.setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));

                // Set initial focus on the Add Work Order button
                mainWindow.setFocusedInteractable(addButton);

                // Force screen refresh to ensure UI updates properly
                if (screen != null) {
                    screen.refresh();
                }

                logger.debug("Work order menu displayed");
            } catch (Exception e) {
                logger.error("Error showing work order menu", e);
            }
        });
    }

    /**
     * Show add work order screen
     */
    private void showAddWorkOrderScreen() {
        logger.info("Showing add work order screen");

        invokeLater(() -> {
            try {
                // Clear existing components
                mainPanel.removeAllComponents();

                // Re-add header
                Label headerLabel = new Label("Add New Work Order")
                        .addStyle(SGR.BOLD).setForegroundColor(TextColor.ANSI.BLUE);
                mainPanel.addComponent(headerLabel);
                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Add status displays
                Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                statusPanel.addComponent(new Label("Status: "));
                statusPanel.addComponent(statusLabel);
                mainPanel.addComponent(statusPanel);

                Panel connectionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                connectionPanel.addComponent(new Label("Connection: "));
                connectionPanel.addComponent(connectionLabel);
                mainPanel.addComponent(connectionPanel);

                // Add a separator
                mainPanel.addComponent(new Separator(Direction.HORIZONTAL));

                // Work order form
                mainPanel.addComponent(new Label("Enter Work Order Details:"));
                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Code field
                Panel codePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                codePanel.addComponent(new Label("Code:       ").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox codeField = new TextBox(new TerminalSize(15, 1));
                codePanel.addComponent(codeField);
                mainPanel.addComponent(codePanel);

                // Name field
                Panel namePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                namePanel.addComponent(new Label("Name:       ").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox nameField = new TextBox(new TerminalSize(30, 1));
                namePanel.addComponent(nameField);
                mainPanel.addComponent(namePanel);

                // Description field
                Panel descPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                descPanel.addComponent(new Label("Description:").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox descField = new TextBox(new TerminalSize(30, 3));
                descPanel.addComponent(descField);
                mainPanel.addComponent(descPanel);

                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Add result area to display server response
                mainPanel.addComponent(new Label("Server Response:"));
                resultsArea = new TextBox(new TerminalSize(60, 5));
                resultsArea.setReadOnly(true);
                mainPanel.addComponent(resultsArea);

                // Buttons panel
                Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));

                // Add button
                Button addButton = new Button("Add Work Order", () -> {
                    String code = codeField.getText().trim();
                    String name = nameField.getText().trim();
                    String description = descField.getText().trim();

                    // Validation
                    if (code.isEmpty() || name.isEmpty() || description.isEmpty()) {
                        showError("All fields are required!");
                        return;
                    }

                    try {
                        // Verify code is numeric
                        int codeNum = Integer.parseInt(code);

                        if (codeNum <= 0) {
                            showError("Work order code must be a positive number!");
                            return;
                        }

                        // Format: ADD|code|name|description
                        String request = String.format("ADD|%s|%s|%s", code, name, description);
                        client.sendDataRequest(request);
                        updateStatus("Adding work order with code: " + code);
                    } catch (NumberFormatException e) {
                        showError("Code must be a number");
                    }
                });

                // Clear button
                Button clearButton = new Button("Clear", () -> {
                    codeField.setText("");
                    nameField.setText("");
                    descField.setText("");
                    resultsArea.setText("");
                    codeField.takeFocus();
                });

                // Back button
                Button backButton = new Button("Back to Menu", this::showWorkOrderMenu);

                buttonPanel.addComponent(addButton);
                buttonPanel.addComponent(clearButton);
                buttonPanel.addComponent(backButton);
                mainPanel.addComponent(buttonPanel.setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));

                // Set initial focus
                mainWindow.setFocusedInteractable(codeField);

                // Force refresh
                if (screen != null) {
                    screen.refresh();
                }

                logger.debug("Add work order screen displayed");
            } catch (Exception e) {
                logger.error("Error showing add work order screen", e);
            }
        });
    }

    /**
     * Show remove work order screen
     */
    private void showRemoveWorkOrderScreen() {
        logger.info("Showing remove work order screen");

        invokeLater(() -> {
            try {
                // Clear existing components
                mainPanel.removeAllComponents();

                // Add header
                Label headerLabel = new Label("Remove Work Order")
                        .addStyle(SGR.BOLD).setForegroundColor(TextColor.ANSI.BLUE);
                mainPanel.addComponent(headerLabel);
                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Add status displays
                Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                statusPanel.addComponent(new Label("Status: "));
                statusPanel.addComponent(statusLabel);
                mainPanel.addComponent(statusPanel);

                Panel connectionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                connectionPanel.addComponent(new Label("Connection: "));
                connectionPanel.addComponent(connectionLabel);
                mainPanel.addComponent(connectionPanel);

                // Add a separator
                mainPanel.addComponent(new Separator(Direction.HORIZONTAL));

                // Work order form
                mainPanel.addComponent(new Label("Enter Work Order Code to Remove:"));
                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Code field
                Panel codePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                codePanel.addComponent(new Label("Code: ").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox codeField = new TextBox(new TerminalSize(15, 1));
                codePanel.addComponent(codeField);
                mainPanel.addComponent(codePanel);

                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Warning message
                Label warningLabel = new Label("Warning: This operation cannot be undone!")
                        .setForegroundColor(TextColor.ANSI.RED);
                mainPanel.addComponent(warningLabel);

                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Add result area to display server response
                mainPanel.addComponent(new Label("Server Response:"));
                resultsArea = new TextBox(new TerminalSize(60, 5));
                resultsArea.setReadOnly(true);
                mainPanel.addComponent(resultsArea);

                // Buttons panel
                Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));

                // Remove button
                Button removeButton = new Button("Remove Work Order", () -> {
                    String code = codeField.getText().trim();

                    // Validation
                    if (code.isEmpty()) {
                        showError("Work order code is required!");
                        return;
                    }

                    try {
                        // Verify code is numeric
                        int codeNum = Integer.parseInt(code);

                        if (codeNum <= 0) {
                            showError("Work order code must be a positive number!");
                            return;
                        }

                        // Show confirmation dialog
                        MessageDialog.showMessageDialog(
                                gui,
                                "Confirm Removal",
                                "Are you sure you want to remove work order #" + code + "?",
                                MessageDialogButton.Yes,
                                MessageDialogButton.No);

                        // Format: REMOVE|code
                        String request = String.format("REMOVE|%s", code);
                        client.sendDataRequest(request);
                        updateStatus("Removing work order with code: " + code);
                    } catch (NumberFormatException e) {
                        showError("Code must be a number");
                    }
                });

                // Clear button
                Button clearButton = new Button("Clear", () -> {
                    codeField.setText("");
                    resultsArea.setText("");
                    codeField.takeFocus();
                });

                // Back button
                Button backButton = new Button("Back to Menu", this::showWorkOrderMenu);

                buttonPanel.addComponent(removeButton);
                buttonPanel.addComponent(clearButton);
                buttonPanel.addComponent(backButton);
                mainPanel.addComponent(buttonPanel.setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));

                // Set initial focus
                mainWindow.setFocusedInteractable(codeField);

                // Force refresh
                if (screen != null) {
                    screen.refresh();
                }

                logger.debug("Remove work order screen displayed");
            } catch (Exception e) {
                logger.error("Error showing remove work order screen", e);
            }
        });
    }

    /**
     * Show update work order screen
     */
    private void showUpdateWorkOrderScreen() {
        logger.info("Showing update work order screen");

        invokeLater(() -> {
            try {
                // Clear existing components
                mainPanel.removeAllComponents();

                // Add header
                Label headerLabel = new Label("Update Work Order")
                        .addStyle(SGR.BOLD).setForegroundColor(TextColor.ANSI.BLUE);
                mainPanel.addComponent(headerLabel);
                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Add status displays
                Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                statusPanel.addComponent(new Label("Status: "));
                statusPanel.addComponent(statusLabel);
                mainPanel.addComponent(statusPanel);

                Panel connectionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                connectionPanel.addComponent(new Label("Connection: "));
                connectionPanel.addComponent(connectionLabel);
                mainPanel.addComponent(connectionPanel);

                // Add a separator
                mainPanel.addComponent(new Separator(Direction.HORIZONTAL));

                // Work order form
                mainPanel.addComponent(new Label("Enter Work Order Details:"));
                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Code field
                Panel codePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                codePanel.addComponent(new Label("Code:       ").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox codeField = new TextBox(new TerminalSize(15, 1));
                codePanel.addComponent(codeField);

                // Lookup button
                Button lookupButton = new Button("Look Up", () -> {
                    String code = codeField.getText().trim();

                    // Validation
                    if (code.isEmpty()) {
                        showError("Work order code is required!");
                        return;
                    }

                    try {
                        // Verify code is numeric
                        int codeNum = Integer.parseInt(code);

                        if (codeNum <= 0) {
                            showError("Work order code must be a positive number!");
                            return;
                        }

                        // Send search request
                        client.sendDataRequest("SEARCH|" + code);
                        updateStatus("Looking up work order: " + code);
                    } catch (NumberFormatException e) {
                        showError("Code must be a number");
                    }
                });
                codePanel.addComponent(lookupButton);
                mainPanel.addComponent(codePanel);

                // Name field
                Panel namePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                namePanel.addComponent(new Label("Name:       ").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox nameField = new TextBox(new TerminalSize(30, 1));
                namePanel.addComponent(nameField);
                mainPanel.addComponent(namePanel);

                // Description field
                Panel descPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                descPanel.addComponent(new Label("Description:").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox descField = new TextBox(new TerminalSize(30, 3));
                descPanel.addComponent(descField);
                mainPanel.addComponent(descPanel);

                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Add result area to display server response
                mainPanel.addComponent(new Label("Server Response:"));
                resultsArea = new TextBox(new TerminalSize(60, 5));
                resultsArea.setReadOnly(true);
                mainPanel.addComponent(resultsArea);

                // Buttons panel
                Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));

                // Update button
                Button updateButton = new Button("Update Work Order", () -> {
                    String code = codeField.getText().trim();
                    String name = nameField.getText().trim();
                    String description = descField.getText().trim();
                    // fix timestamp
                    String timestamp = String.valueOf(System.currentTimeMillis());

                    // Validation
                    if (code.isEmpty() || name.isEmpty() || description.isEmpty()) {
                        showError("All fields are required!");
                        return;
                    }

                    try {
                        // Verify code is numeric
                        int codeNum = Integer.parseInt(code);

                        if (codeNum <= 0) {
                            showError("Work order code must be a positive number!");
                            return;
                        }

                        // Show confirmation dialog
                        MessageDialog.showMessageDialog(
                                gui,
                                "Confirm Update",
                                "Are you sure you want to update work order #" + code + "?",
                                MessageDialogButton.Yes,
                                MessageDialogButton.No);

                        // Format: UPDATE|code|name|description
                        String request = String.format("UPDATE|%s|%s|%s", code, name, description);
                        client.sendDataRequest(request);
                        updateStatus("Updating work order with code: " + code);
                    } catch (NumberFormatException e) {
                        showError("Code must be a number");
                    }
                });

                // Clear button
                Button clearButton = new Button("Clear", () -> {
                    codeField.setText("");
                    nameField.setText("");
                    descField.setText("");
                    resultsArea.setText("");
                    codeField.takeFocus();
                });

                // Back button
                Button backButton = new Button("Back to Menu", this::showWorkOrderMenu);

                buttonPanel.addComponent(updateButton);
                buttonPanel.addComponent(clearButton);
                buttonPanel.addComponent(backButton);
                mainPanel.addComponent(buttonPanel.setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));

                // Set initial focus
                mainWindow.setFocusedInteractable(codeField);

                // Force refresh
                if (screen != null) {
                    screen.refresh();
                }

                logger.debug("Update work order screen displayed");
            } catch (Exception e) {
                logger.error("Error showing update work order screen", e);
            }
        });
    }

    /**
     * Show search work order screen
     */
    private void showSearchWorkOrderScreen() {
        logger.info("Showing search work order screen");

        invokeLater(() -> {
            try {
                // Clear existing components
                mainPanel.removeAllComponents();

                // Add header
                Label headerLabel = new Label("Search Work Order")
                        .addStyle(SGR.BOLD).setForegroundColor(TextColor.ANSI.BLUE);
                mainPanel.addComponent(headerLabel);
                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Add status displays
                Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                statusPanel.addComponent(new Label("Status: "));
                statusPanel.addComponent(statusLabel);
                mainPanel.addComponent(statusPanel);

                // Add a separator
                mainPanel.addComponent(new Separator(Direction.HORIZONTAL));

                // Search options
                mainPanel.addComponent(new Label("Search By:"));

                // Search by code panel
                Panel codePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                codePanel.addComponent(new Label("Code: ").setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));
                TextBox codeField = new TextBox(new TerminalSize(15, 1));
                codePanel.addComponent(codeField);
                mainPanel.addComponent(codePanel);

                mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

                // Buttons
                Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));

                // Search by code button
                Button searchCodeButton = new Button("Search by Code", () -> {
                    String code = codeField.getText().trim();
                    if (code.isEmpty()) {
                        showError("Code cannot be empty");
                        return;
                    }

                    try {
                        // Verify code is numeric
                        int codeNum = Integer.parseInt(code);

                        // Verify code is positive
                        if (codeNum <= 0) {
                            showError("Work order code must be a positive number!");
                            return;
                        }

                        // Send search request
                        client.sendDataRequest("SEARCH|" + code);
                        updateStatus("Searching for work order: " + code);
                    } catch (NumberFormatException e) {
                        showError("Code must be a number");
                    }

                    client.sendDataRequest("SEARCH|" + code);
                });

                // TODO: Add this button
                // Search by name button
                // Button searchNameButton = new Button("Search by Name", () -> {
                // String name = nameField.getText().trim();
                // if (name.isEmpty()) {
                // showError("Name cannot be empty");
                // return;
                // }
                // client.sendDataRequest("SEARCH_NAME|" + name);
                // });

                buttonPanel.addComponent(searchCodeButton);
                // buttonPanel.addComponent(searchNameButton);
                mainPanel.addComponent(buttonPanel);

                // Add separator
                mainPanel.addComponent(new Separator(Direction.HORIZONTAL));

                // Results area
                mainPanel.addComponent(new Label("Search Results"));
                resultsArea = new TextBox(new TerminalSize(60, 10));
                resultsArea.setReadOnly(true);
                resultsArea.isVerticalFocusSwitching();
                resultsArea.takeFocus();
                mainPanel.addComponent(resultsArea);

                // Navigation buttons
                Panel navPanel = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(3));
                Button backButton = new Button("Back to Menu", this::showWorkOrderMenu);
                navPanel.addComponent(backButton);
                mainPanel.addComponent(navPanel.setLayoutData(
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Center)));

                // Set initial focus
                mainWindow.setFocusedInteractable(codeField);

                // Force refresh
                if (screen != null) {
                    screen.refresh();
                }

                logger.debug("Search work order screen displayed");
            } catch (Exception e) {
                logger.error("Error showing search work order screen", e);
            }
        });
    }

    /**
     * Show server response in the UI
     */
    private void showServerResponse(String response) {
        invokeLater(() -> {
            try {
                // Find the results area in the main panel
                for (Component component : mainPanel.getChildren()) {
                    if (component instanceof TextBox && ((TextBox) component).isReadOnly()) {
                        TextBox resultsArea = (TextBox) component;
                        resultsArea.setText(response);
                        return;
                    }
                }

                // If we couldn't find the results area, show a dialog
                MessageDialog.showMessageDialog(gui, "Server Response", response);
            } catch (Exception e) {
                logger.error("Error showing server response", e);
            }
        });
    }

    /**
     * Update the status label
     */
    public void updateStatus(String status) {
        queueUIUpdate(UIUpdate.createStatus(status));
    }

    /**
     * Update the connection status
     */
    public void updateConnectionStatus(String status, boolean connected) {
        invokeLater(() -> {
            try {
                if (connectionLabel != null) {
                    TextColor color = connected ? TextColor.ANSI.GREEN_BRIGHT : TextColor.ANSI.RED;
                    connectionLabel.setForegroundColor(color).setText(status);
                    logger.debug("Updated connection label: {} ({})", status, connected);
                }
            } catch (Exception e) {
                logger.error("Error updating connection status", e);
            }
        });
    }

    /**
     * Update the authentication status
     */
    public void notifyAuthenticationResult(boolean success, String message) {
        queueUIUpdate(UIUpdate.createAuthentication(success, message));
    }

    /**
     * Show a server response
     */
    public void showResponse(String response) {
        queueUIUpdate(UIUpdate.createServerResponse(response));
    }

    /**
     * Show an alert message
     */
    public void showAlert(String message) {
        queueUIUpdate(UIUpdate.createAlert(message));
    }

    /**
     * Show an error message
     */
    public void showError(String message) {
        queueUIUpdate(UIUpdate.createError(message));
    }

    /**
     * Queue a UI update to be processed on the UI thread
     */
    private void queueUIUpdate(UIUpdate update) {
        try {
            uiUpdateQueue.put(update);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while queuing UI update", e);
        }
    }

    /**
     * Clean up resources
     */
    private void cleanup() {
        running.set(false);

        try {
            if (screen != null) {
                screen.stopScreen();
            }

            shutdown();
            logger.info("Lanterna UI resources cleaned up");
        } catch (Exception e) {
            logger.error("Error cleaning up Lanterna UI", e);
        }
    }

    /**
     * Shutdown the UI and client
     */
    public void shutdown() {
        // Guard against recursive calls
        if (!shuttingDown.compareAndSet(false, true)) {
            logger.debug("Shutdown already in progress, ignoring call");
            return;
        }

        logger.info("Shutting down LanternaUI...");
        running.set(false);

        try {
            // Close screen if possible
            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (IOException e) {
                    logger.error("Error closing screen", e);
                }
            }

            // Shut down the client WITHOUT calling back to us
            if (client != null) {
                client.shutdownWithoutUI(); // Call special method that won't call UI.shutdown
            }

            // Force exit after a short delay
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    System.exit(0);
                } catch (InterruptedException e) {
                    System.exit(1);
                }
            }, "exit-thread").start();

        } catch (Exception e) {
            logger.error("Error during UI shutdown", e);
            System.exit(1);
        }
    }

    /**
     * Ensure UI updates happen on the GUI thread
     */
    public void invokeLater(Runnable runnable) {
        if (Thread.currentThread().getName().startsWith("lanterna-")) {
            // Already on GUI thread
            runnable.run();
        } else {
            // Schedule update on GUI thread
            guiUpdateExecutor.execute(() -> {
                try {
                    runnable.run();
                    // Force screen refresh
                    if (screen != null) {
                        screen.refresh();
                    }
                } catch (Exception e) {
                    logger.error("Error in GUI update", e);
                }
            });
        }
    }

    /**
     * Class to represent UI updates that need to be applied
     */
    private static class UIUpdate {
        enum Type {
            STATUS, CONNECTION, AUTHENTICATION, ALERT, ERROR,
            SHOW_LOGIN, SHOW_WORK_ORDERS, SERVER_RESPONSE
        }

        final Type type;
        final String message;
        final boolean connected;

        private UIUpdate(Type type, String message, boolean connected) {
            this.type = type;
            this.message = message;
            this.connected = connected;
        }

        static UIUpdate createStatus(String status) {
            return new UIUpdate(Type.STATUS, status, false);
        }

        static UIUpdate createConnection(String status, boolean connected) {
            return new UIUpdate(Type.CONNECTION, status, connected);
        }

        static UIUpdate createAuthentication(boolean success, String message) {
            return new UIUpdate(Type.AUTHENTICATION, message, success);
        }

        static UIUpdate createAlert(String message) {
            return new UIUpdate(Type.ALERT, message, false);
        }

        static UIUpdate createError(String message) {
            return new UIUpdate(Type.ERROR, message, false);
        }

        static UIUpdate createShowLogin() {
            return new UIUpdate(Type.SHOW_LOGIN, null, false);
        }

        static UIUpdate createShowWorkOrders() {
            return new UIUpdate(Type.SHOW_WORK_ORDERS, null, false);
        }

        static UIUpdate createServerResponse(String response) {
            return new UIUpdate(Type.SERVER_RESPONSE, response, false);
        }
    }

    private void addDebugPanel() {
        if (!"true".equals(System.getProperty("debug.ui"))) {
            return;
        }

        Panel debugPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        debugPanel.addComponent(new Label("DEBUG INFO"));
        Label threadLabel = new Label("Thread: [unknown]");
        debugPanel.addComponent(threadLabel);

        // Update periodically
        new Thread(() -> {
            while (running.get()) {
                try {
                    invokeLater(() -> {
                        threadLabel.setText("Thread: " + Thread.currentThread().getName());
                    });
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "debug-monitor").start();

        mainPanel.addComponent(debugPanel);
    }

    /**
     * Display a response with title and content
     */
    public void displayResponse(String title, String content) {
        invokeLater(() -> {
            try {
                if (resultsArea != null) {
                    resultsArea.setText(content);
                    logger.debug("Updated results area with new content");
                } else {
                    // Fall back to dialog
                    MessageDialog.showMessageDialog(gui, title, content);
                }
            } catch (Exception e) {
                logger.error("Error displaying response", e);
            }
        });
    }

    /**
     * Add a log message to the UI
     */
    public void addLogMessage(String message) {
        // Queue the message to be added to the log
        queueUIUpdate(UIUpdate.createStatus(message));
        logger.debug("Added log message: {}", message);
    }

    /**
     * Show connection lost dialog and ask if user wants to reconnect
     */
    public void showConnectionLostDialog(ImplClient client) {
        invokeLater(() -> {
            try {
                // Update UI to show disconnected status
                updateConnectionStatus("Disconnected", false);
                updateStatus("Connection lost");

                // Show dialog asking if user wants to reconnect
                MessageDialogButton result = MessageDialog.showMessageDialog(
                        gui,
                        "Connection Lost",
                        "Connection to server was lost. Do you want to reconnect?",
                        MessageDialogButton.Yes,
                        MessageDialogButton.No);

                if (result == MessageDialogButton.Yes) {
                    logger.info("User chose to reconnect");
                    updateStatus("Attempting to reconnect...");

                    // Use existing reconnect method
                    client.requestReconnect();
                } else {
                    logger.info("User chose not to reconnect");

                    // Show goodbye message and exit
                    MessageDialog.showMessageDialog(
                            gui,
                            "Goodbye",
                            "The application will now close.",
                            MessageDialogButton.OK);

                    // Shutdown after a short delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            shutdown();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, "shutdown-delay").start();
                }
            } catch (Exception e) {
                logger.error("Error showing connection lost dialog", e);
            }
        });
    }
}
