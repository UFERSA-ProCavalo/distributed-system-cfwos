package main.server.proxy;

import java.net.ServerSocket;
import java.net.Socket;

import main.server.proxy.auth.AuthService;
import main.server.proxy.cache.CacheFIFO;
import main.shared.log.Logger;
import main.shared.models.WorkOrder;

public class ProxyServer {
    private ServerSocket serverSocket;
    private final int SERVER_PORT = 22220;
    private final AuthService authService;
    private final Logger logger;
    private boolean running = true;

    // Cache compartilhada entre todos os handlers
    private final CacheFIFO<WorkOrder> cache;

    public static int connectionCount = 0;
    public static int activeConnections = 0;

    public ProxyServer() {
        System.out.println("\033[2J\033[1;1H"); // Clear screen
        this.logger = Logger.getLogger();
        this.authService = AuthService.getInstance();

        // Inicializa o sistema de cache
        this.cache = new CacheFIFO<WorkOrder>();
        logger.info("Sistema de cache inicializado com política FIFO");

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
                ProxyServerHandler handler = new ProxyServerHandler(clientSocket, authService, logger, cache);
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

    public static void main(String[] args) {
        new ProxyServer();
    }
}