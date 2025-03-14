package server.proxy;

import java.net.ServerSocket;
import java.net.Socket;

import server.proxy.auth.AuthService;
import server.proxy.cache.CacheFIFO;
import server.proxy.cache.CacheHandler;
import shared.log.Logger;
import shared.models.WorkOrder;

public class ProxyServer {
    private ServerSocket serverSocket;
    private final int SERVER_PORT = 22220;
    private final AuthService authService;
    private final Logger logger;
    private boolean running = true;

    // Cache compartilhada entre todos os handlers
    private final CacheFIFO<WorkOrder> workOrderCache;
    private final CacheHandler cacheHandler;

    public static int connectionCount = 0;
    public static int activeConnections = 0;

    public ProxyServer() {
        System.out.println("\033[2J\033[1;1H"); // Clear screen
        this.logger = Logger.getLogger();
        this.authService = AuthService.getInstance();

        // Inicializa o sistema de cache
        this.workOrderCache = new CacheFIFO<>();
        this.cacheHandler = new CacheHandler(workOrderCache);
        logger.info("Sistema de cache inicializado com política FIFO");

        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Proxy Server shutting down...");

                // Salva a cache antes de encerrar
                logger.info("Salvando cache no encerramento...");
                cacheHandler.saveCache();

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
                ProxyServerHandler handler = new ProxyServerHandler(clientSocket, authService, logger, workOrderCache, cacheHandler);
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