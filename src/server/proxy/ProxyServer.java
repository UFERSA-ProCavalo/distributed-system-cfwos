package server.proxy;

import java.net.ServerSocket;
import java.net.Socket;

import server.proxy.auth.AuthService;
import shared.log.Logger;
import shared.messages.*;

public class ProxyServer {
    private ServerSocket serverSocket;
    private final int SERVER_PORT = 11111;
    private final AuthService authService;
    private final Logger logger;
    private boolean running = true;

    public static int connectionCount = 0;
    public static int activeConnections = 0;

    public ProxyServer() {
        this.logger = Logger.getLogger();
        this.authService = AuthService.getInstance();

        // Register message handlers
        // messageBus.registerHandler(new AuthenticationHandler());

        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Proxy Server shutting down...");
                shutdown();
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        logger.info("Proxy Server initialized");
        this.run();
    }

    private void run() {
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            logger.info("Proxy Server listening on port {}", SERVER_PORT);

            // Main server loop
            while (running) {
                logger.debug("Waiting for client connections...");
                Socket clientSocket = serverSocket.accept();

                logger.info("Client connected: {}:{}",
                        clientSocket.getInetAddress().getHostAddress(),
                        clientSocket.getPort());

                // Create handler for this client
                ProxyServerHandler handler = new ProxyServerHandler(clientSocket, authService, logger);
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (Exception e) {
            if (running) {
                logger.error("Error in server main loop", e);
            }
        }
    }

    private void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            logger.error("Error closing server socket", e);
        }
    }

    // Authentication message handler

    public static void main(String[] args) {
        new ProxyServer();
    }
}