package main.server.localization;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;

/**
 * Localization Server
 * 
 * Serves as a connection entry point that redirects clients to available proxy
 * servers
 */
public class LocalizationServer implements Runnable {
    private final int port;
    private final Logger logger;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    // Thread pools
    private final ExecutorService connectionAcceptorPool;
    private final ExecutorService clientHandlerPool;
    private final ScheduledExecutorService heartbeatScheduler;

    // Client tracking
    private final Map<String, LocalizationServerHandler> connectedClients = new ConcurrentHashMap<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);

    // Proxy server registry - maps server ID to connection info (host:port)
    private static final Map<String, ProxyInfo> activeProxies = new ConcurrentHashMap<>();
    private static final long PROXY_TIMEOUT_MS = 15000; // 15 seconds timeout for proxy servers
    private static final long HEARTBEAT_INTERVAL_MS = 60000; // 60 seconds between heartbeats
    private final Random random = new Random();
    private final MessageBus messageBus;

    // Simple value class - no complex logic
    private static class ProxyInfo {
        final String id;
        final String host;
        final String port;
        volatile int activeConnections;

        public ProxyInfo(String id, String host, String port) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.activeConnections = 0;
        }

        public String[] getConnectionInfo() {
            return new String[] { host, port };
        }
    }

    /**
     * Creates a new localization server listening on the specified port
     */
    public LocalizationServer(int port) {
        System.out.println("\033[2J\033[1;1H"); // Clear screen
        this.port = port;
        this.logger = Logger.getLogger();
        this.messageBus = new MessageBus("LocalizationServer", logger);

        logger.info("Initializing Localization Server on port {}", port);

        // Single thread for accepting connections
        this.connectionAcceptorPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "localization-acceptor");
            t.setDaemon(true);
            return t;
        });

        // Thread pool for handling client connections
        this.clientHandlerPool = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "localization-client-handler");
                    t.setDaemon(true);
                    return t;
                });

        // Heartbeat scheduler for proxy server health checks
        this.heartbeatScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "heartbeat-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Register for message handlers - only keep heartbeat response handling
        // in the main server class
        messageBus.subscribe(MessageType.HEARTBEAT_RESPONSE, this::handleHeartbeatResponse);
        // Proxy registration is now handled by the LocalizationServerHandler
    }

    /**
     * Register a proxy server with the localization service
     */
    public static synchronized void registerProxyServer(String serverId, String host, String port, Logger logger) {
        // Simply add to active list immediately
        activeProxies.put(serverId, new ProxyInfo(serverId, host, port));
        logger.info("Registered proxy server: {} at {}:{}", serverId, host, port);
    }

    /**
     * Check if a proxy server ID is already registered
     */
    public static synchronized boolean isProxyRegistered(String serverId) {
        return activeProxies.containsKey(serverId);
    }

    /**
     * Unregister a proxy server
     */
    public static synchronized void unregisterProxyServer(String serverId, Logger logger) {
        if (activeProxies.remove(serverId) != null) {
            logger.info("Unregistered proxy server: {}", serverId);
        }
    }

    /**
     * Select a proxy server for redirection
     */
    public String[] selectProxyServer() {
        if (activeProxies.isEmpty()) {
            return null;
        }

        // Find proxy with least connections
        ProxyInfo selectedProxy = null;
        int minConnections = Integer.MAX_VALUE;

        for (ProxyInfo proxy : activeProxies.values()) {
            if (proxy.activeConnections < minConnections) {
                minConnections = proxy.activeConnections;
                selectedProxy = proxy;
            }
        }

        // If all have equal connections or other issue, pick randomly
        if (selectedProxy == null) {
            List<ProxyInfo> proxies = new ArrayList<>(activeProxies.values());
            if (!proxies.isEmpty()) {
                selectedProxy = proxies.get(random.nextInt(proxies.size()));
            }
        }

        return selectedProxy != null ? selectedProxy.getConnectionInfo() : null;
    }

    /**
     * Send heartbeats to all active proxies
     */
    private void sendHeartbeats() {
        logger.debug("Sending heartbeats to {} proxy servers", activeProxies.size());

        // Loop through active handlers and send heartbeats
        for (LocalizationServerHandler handler : connectedClients.values()) {
            String clientId = handler.getClientId();

            // Skip non-proxy clients
            if (!clientId.startsWith("Proxy-")) {
                continue;
            }

            // Check if this proxy is in our active list
            if (activeProxies.containsKey(clientId) && handler.isConnected()) {
                try {
                    // Send heartbeat through existing connection
                    Message heartbeatMsg = new Message(
                            MessageType.HEARTBEAT_REQUEST,
                            "LocalizationServer",
                            clientId,
                            System.currentTimeMillis());

                    handler.sendMessage(heartbeatMsg);
                    logger.debug("Sent heartbeat to proxy: {}", clientId);
                } catch (Exception e) {
                    logger.warning("Failed to send heartbeat to {}, removing from active list", clientId);
                    // If we can't even send a heartbeat, remove it immediately
                    activeProxies.remove(clientId);
                }
            } else if (!handler.isConnected()) {
                // Handler connection is dead, remove proxy
                logger.info("Proxy {} disconnected, removing from active list", clientId);
                activeProxies.remove(clientId);
            }
        }
    }

    /**
     * Handle heartbeat responses from proxy servers
     */
    private void handleHeartbeatResponse(Message message) {
        if (message.getType() == MessageType.HEARTBEAT_RESPONSE) {
            String senderId = message.getSender();
            ProxyInfo proxy = activeProxies.get(senderId);

            if (proxy != null) {
                // Update connection count if provided
                if (message.getPayload() instanceof String[] && ((String[]) message.getPayload()).length >= 3) {
                    try {
                        int connections = Integer.parseInt(((String[]) message.getPayload())[2]);
                        proxy.activeConnections = connections;
                        logger.debug("Updated active connections for {}: {}", senderId, connections);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid active connections value from {}", senderId);
                    }
                }

                logger.debug("Received heartbeat response from {}", senderId);
            }
        }
    }

    /**
     * Force a refresh of the proxy server list
     * Used when a client requests RECONNECT
     */
    public void refreshProxyServers() {
        sendHeartbeats();

        // Wait a bit for responses to come in
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        try {
            // Start the server socket
            serverSocket = new ServerSocket(port);
            logger.info("Localization server started on port {}", port);

            // Accept connections in a separate thread
            connectionAcceptorPool.submit(this::acceptConnections);

            // Schedule regular heartbeats
            heartbeatScheduler.scheduleAtFixedRate(
                    this::sendHeartbeats,
                    0, // start immediately
                    HEARTBEAT_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);

            // Keep the main thread alive until shutdown
            while (running) {
                try {
                    Thread.sleep(30000);
                    logger.debug("Active client connections: {}", connectedClients.size());
                    logger.debug("Active proxy servers: {}", activeProxies.size());

                    // Log active connections per proxy
                    for (ProxyInfo proxy : activeProxies.values()) {
                        logger.debug("Server {} active connections: {}",
                                proxy.id, proxy.activeConnections);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Error starting localization server", e);
        } finally {
            shutdown();
        }
    }

    /**
     * Continuously accept new client connections
     */
    private void acceptConnections() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientId = "Client-" + nextClientId.getAndIncrement();

                logger.info("New client connected: {} from {}",
                        clientId, clientSocket.getRemoteSocketAddress());

                // Create and start a handler for this client
                LocalizationServerHandler handler = new LocalizationServerHandler(clientSocket, clientId, this);
                connectedClients.put(clientId, handler);

                clientHandlerPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting client connection", e);
                }
            }
        }
    }

    /**
     * Remove client from tracking
     */
    public void removeClient(String clientId) {
        connectedClients.remove(clientId);
        logger.debug("Removed client from tracking: {}", clientId);
    }

    /**
     * Shutdown the server and all resources
     */
    public void shutdown() {
        if (!running)
            return;

        running = false;
        logger.info("Shutting down localization server...");

        // Close all client connections
        for (LocalizationServerHandler handler : connectedClients.values()) {
            handler.close();
        }
        connectedClients.clear();

        // Close the server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }

        // Shutdown thread pools
        shutdownThreadPool(connectionAcceptorPool, "Connection Acceptor");
        shutdownThreadPool(clientHandlerPool, "Client Handler");
        shutdownThreadPool(heartbeatScheduler, "Heartbeat Scheduler");

        // Clean up message bus
        if (messageBus != null) {
            messageBus.unsubscribeAll();
            messageBus.shutdown();
        }

        logger.info("Localization server shutdown complete");
    }

    private void shutdownThreadPool(ExecutorService pool, String poolName) {
        try {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("{} thread pool did not terminate in time, forcing shutdown", poolName);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("{} thread pool shutdown interrupted", poolName);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get the logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Run the localization server with the specified port
     */
    public static void main(String[] args) {
        int port = 11110; // Default port

        // Allow port override from command line
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + port);
            }
        }

        LocalizationServer server = new LocalizationServer(port);
        new Thread(server, "localization-server-main").start();

        // Add shutdown hook for clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down localization server...");
            server.shutdown();
        }));

        System.out.println("Localization server started on port " + port);
        System.out.println("Press Ctrl+C to stop the server");
    }
}