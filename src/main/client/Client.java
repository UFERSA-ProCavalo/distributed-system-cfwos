package main.client;

import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

import main.shared.log.Logger;

/**
 * Main client application that handles server connection and UI initialization
 */
public class Client {
    private final Logger logger = Logger.getLogger();
    private Socket socket;
    private InetAddress inet;
    private final String SERVER_IP = "localhost";
    private final int SERVER_PORT = 11110;
    private ImplClient implClient;
    private boolean useLanterna = false;
    private Scanner scanner;

    /**
     * Create a new client with the specified UI mode
     * 
     * @param useLanterna Whether to use Lanterna GUI (true) or console UI (false)
     */
    public Client(boolean useLanterna) {
        // Clear screen
        System.out.println("\033[2J\033[1;1H");

        // Configure logger
        logger.setHideConsoleOutput(!useLanterna); // Only hide output in Lanterna mode

        // Set UI mode
        this.useLanterna = useLanterna;

        // Create scanner for console input if needed
        if (!useLanterna) {
            scanner = new Scanner(System.in);
        }

        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Client shutting down...");
            disconnect();
            if (scanner != null) {
                scanner.close();
            }
        }));

        logger.info("Client initialized with {} interface", useLanterna ? "graphical" : "console");
        this.run();
    }

    /**
     * Connect to the server and initialize client components
     */
    private void run() {
        try {
            // Connect to server
            logger.info("Connecting to server at {}:{}", SERVER_IP, SERVER_PORT);
            socket = new Socket(SERVER_IP, SERVER_PORT);
            inet = socket.getInetAddress();
            logger.info("Connected to server: {} ({})", inet.getHostAddress(), inet.getHostName());

            // Create client implementation
            implClient = new ImplClient(socket, logger, true);

            // Explicitly wait for the UI to initialize before starting client threads
            if (implClient.getLanternaUI() != null) {
                logger.info("Waiting for UI to initialize...");
                Thread.sleep(500); // Give UI thread time to initialize
            }

            // Start the client main thread
            Thread clientThread = new Thread(implClient, "client-main-thread");
            clientThread.start();

        } catch (Exception e) {
            logger.error("Failed to connect to server", e);

            // If we can't connect, show an error dialog and exit
            if (implClient != null && implClient.getLanternaUI() != null) {
                implClient.getLanternaUI().showError("Failed to connect to server: " + e.getMessage());
            }
        } finally {
            // Check if the socket is NOT connected (should be socket.isClosed() ||
            // !socket.isConnected())
            if (socket != null && socket.isClosed()) {
                logger.error("Socket is closed, cannot connect to server");
            }
        }
    }

    /**
     * Disconnect from the server and clean up resources
     */
    public void disconnect() {
        try {
            if (implClient != null) {
                implClient.shutdown();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("Disconnected from server");
            }
        } catch (Exception e) {
            logger.error("Error while disconnecting", e);
        }
    }

    /**
     * Get the client implementation
     */
    public ImplClient getImplClient() {
        return implClient;
    }

    /**
     * Main entry point - parses command line arguments to determine UI mode
     * 
     * @param args Command line arguments: use --console for console UI,
     *             --gui or no arguments for Lanterna GUI
     */
    public static void main(String[] args) {
        boolean useConsole = false;

        // Parse command line arguments
        if (args.length > 0) {
            for (String arg : args) {
                if (arg.equalsIgnoreCase("--console") || arg.equalsIgnoreCase("-c")) {
                    useConsole = true;
                    break;
                }
            }
        }

        // Add shutdown hook to ensure clean disconnect
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down client...");
        }));

        // Create client with the specified UI mode
        new Client(!useConsole);
    }
}