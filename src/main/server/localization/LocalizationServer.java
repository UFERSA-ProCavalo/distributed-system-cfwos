package main.server.localization;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import main.server.proxy.replication.ProxyCacheService;
import main.server.proxy.replication.ProxyRegistryService;
import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;

/**
 * Localization Server
 * 
 * Serves as a connection entry point that redirects clients to available proxy
 * servers
 */
public class LocalizationServer implements ProxyRegistryService, Serializable {
    private static final long serialVersionUID = 1L;
    private final int port;
    private transient final Logger logger;
    private transient ServerSocket serverSocket;
    private volatile boolean running = true;
    private transient final Object lock = new Object();

    // Thread pools
    private transient final ExecutorService connectionAcceptorPool;
    private transient final ExecutorService clientHandlerPool;

    // Client tracking
    private transient final Map<String, LocalizationServerHandler> connectedClients = new ConcurrentHashMap<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);

    // Proxy server registry - maps server ID to connection info (host:port)
    private transient static final Map<String, ProxyInfo> activeProxies = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private transient final MessageBus messageBus;

    // RMI port for proxy registry service
    private int RMI_PORT = 8000;
    private List<String> peerProxies = new ArrayList<>();

    // Add these fields
    private final Set<String> respondedProxies = ConcurrentHashMap.newKeySet();
    private static final long PING_TIMEOUT_MS = 10000; // 10 seconds timeout

    // Simple value class - no complex logic
    private static class ProxyInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        final String id;
        String host;
        String port;
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

        peerProxies = new ArrayList<>();

        // Thread pool for handling client connections
        this.clientHandlerPool = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "localization-client-handler");
                    t.setDaemon(true);
                    return t;
                });

        // Register for message handlers
        run();
    }

    /**
     * Register a proxy server with the localization service
     */
    public static void registerProxyServer(String serverId, String host, String port, Logger logger) {
        // Simply add to active list immediately
        activeProxies.put(serverId, new ProxyInfo(serverId, host, port));
        logger.info("Registered proxy server: {} at {}:{}", serverId, host, port);
    }

    /**
     * Check if a proxy server ID is already registered
     */
    public static boolean isProxyRegistered(String serverId) {
        return activeProxies.containsKey(serverId);
    }

    // make a method to update the proxyList, based on the PONG received from the
    // proxy server

    public void updateProxyServer(String serverId, Object payload) {
        synchronized (lock) {
            ProxyInfo proxy = activeProxies.get(serverId);
            if (proxy != null) {
                // Update the proxy info based on the payload
                // Assuming payload contains new connection info
                if (payload instanceof String[]) {
                    String[] newInfo = (String[]) payload;
                    proxy.host = newInfo[1];
                    proxy.port = newInfo[2];
                    proxy.activeConnections = Integer.parseInt(newInfo[3]);
                }
            } else {
                logger.warning("Proxy server not found for update: {}", serverId);
            }
        }
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
     * Select a proxy server for redirection based on load balancing
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
     * Process PONG from a proxy server
     */
    public synchronized void handleProxyPong(String proxyId, Object payload) {
        logger.info("Processing PONG from proxy: {}", proxyId);

        // Mark as responsive
        respondedProxies.add(proxyId);

        // Update the proxy info if payload contains connection data
        updateProxyServer(proxyId, payload);

        // Notify any waiting threads
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    /**
     * Send PING to all proxies
     */
    public void sendPingToAllProxies(String requestingClientId) {
        logger.info("Sending PINGs to all proxies for {}", requestingClientId);

        for (String proxyId : new ArrayList<>(activeProxies.keySet())) {
            LocalizationServerHandler handler = connectedClients.get(proxyId);
            if (handler != null && handler.isConnected()) {
                try {
                    Message pingMsg = new Message(
                            MessageType.PING,
                            "LocalizationServer",
                            proxyId,
                            System.currentTimeMillis());

                    handler.sendMessage(pingMsg);
                    logger.debug("Sent PING to proxy: {}", proxyId);
                } catch (Exception e) {
                    logger.warning("Failed to send PING to {}", proxyId);
                }
            }
        }
    }

    /**
     * Send PING to all proxy servers and remove unresponsive ones
     */
    public void refreshProxyServers() {
        if (activeProxies.isEmpty()) {
            logger.info("No proxy servers to refresh");
            return;
        }

        logger.info("Refreshing proxy server list with {} servers", activeProxies.size());

        // Clear the responded proxies set
        respondedProxies.clear();

        // Send PINGs to all proxies
        List<String> proxyIds = new ArrayList<>(activeProxies.keySet());
        int totalProxies = proxyIds.size();

        for (String proxyId : proxyIds) {
            LocalizationServerHandler handler = connectedClients.get(proxyId);
            if (handler != null && handler.isConnected()) {
                try {
                    // Send PING through existing connection
                    Message pingMsg = new Message(
                            MessageType.PING,
                            "LocalizationServer",
                            proxyId,
                            System.currentTimeMillis());

                    handler.sendMessage(pingMsg);
                    logger.debug("Sent PING to proxy: {}", proxyId);
                } catch (Exception e) {
                    logger.warning("Failed to send PING to {}, will be removed", proxyId);
                }
            } else {
                logger.warning("No handler or connection dead for proxy {}, will be removed", proxyId);
            }
        }

        // Wait for responses (all proxies or timeout)
        long startTime = System.currentTimeMillis();

        synchronized (lock) {
            while (respondedProxies.size() < totalProxies &&
                    (System.currentTimeMillis() - startTime) < PING_TIMEOUT_MS) {
                try {
                    // Wait with timeout
                    lock.wait(1000); // Wake up every second to check
                    logger.debug("Waiting for PONGs... received {}/{}",
                            respondedProxies.size(), totalProxies);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // Remove unresponsive proxies
        for (String proxyId : proxyIds) {
            if (!respondedProxies.contains(proxyId)) {
                logger.warning("Removing unresponsive proxy: {}", proxyId);
                activeProxies.remove(proxyId);
            } else {
                logger.info("Proxy {} is responsive", proxyId);
            }
        }

        logger.info("Proxy server refresh complete, {} active proxies remain", activeProxies.size());
    }

    public void run() {
        try {
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            registry.rebind("LocalizationServer", this);

            logger.info("RMI registry bound to port {}", RMI_PORT);

            // Start the server socket
            serverSocket = new ServerSocket(port);
            logger.info("Localization server started on port {}", port);

            // Accept connections in a separate thread
            connectionAcceptorPool.submit(this::acceptConnections);

            // Keep the main thread alive until shutdown
            while (running) {
                try {
                    Thread.sleep(30000);
                    logger.debug("Active client connections: {}", connectedClients.size());
                    logger.debug("Active proxy servers: {}", activeProxies.size());

                    // Log active connections per proxy
                    for (ProxyInfo proxy : activeProxies.values()) {
                        logger.debug("Server {} active connections: {}", proxy.id, proxy.activeConnections);
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
     * Update a client ID to a new ID (used for proxy registration)
     */
    public void updateClientId(String oldId, String newId) {
        LocalizationServerHandler handler = connectedClients.remove(oldId);
        if (handler != null) {
            connectedClients.put(newId, handler);
            logger.info("Updated client ID from {} to {}", oldId, newId);
        }
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

    @Override
    public void registerProxy(String proxyId) throws RemoteException {
        if (peerProxies == null) {
            System.out.println("PeerProxies is null, initializing");
            peerProxies = new ArrayList<>();

        }

        if (!peerProxies.contains(proxyId)) {
            peerProxies.add(proxyId);

            System.out.println("Registered proxy: " + proxyId);
            notifyProxys(proxyId, true);
        } else {
            System.out.println("Proxy {} already registered" + proxyId);
        }
    }

    @Override
    public void unregisterProxy(String proxyId) throws RemoteException {
        if (peerProxies.contains(proxyId)) {
            peerProxies.remove(proxyId);
            logger.info("Unregistered proxy: {}", proxyId);
            notifyProxys(proxyId, false);
        } else {
            logger.warning("Proxy {} not found for unregister", proxyId);
        }
    }

    @Override
    public List<String> getRegisteredProxies() throws RemoteException {
        return new ArrayList<>(peerProxies);
    }

    @Override
    public void notifyProxys(String proxyId, boolean operation) throws RemoteException {
        for (String proxy : peerProxies) {
            if (!proxy.equals(proxyId)) {
                try {
                    System.out.println("Notifying proxy: " + proxy + " of registration");
                    ProxyCacheService proxyCache = (ProxyCacheService) Naming.lookup(proxy);
                    if (operation) {
                        proxyCache.addProxy(proxyId);
                        System.out.println("Notified proxy: " + proxy + " of unregistration");
                    } else {
                        proxyCache.checkProxys(proxyId);
                        System.out.println("Notified proxy: " + proxy + " of unregistration");
                    }

                } catch (Exception e) {
                    System.out.println("Failed to notify proxy: " + proxy);
                    e.printStackTrace();

                }
            }
        }
    }

    @Override
    public boolean isAlive(String proxyId) throws RemoteException {
        try {
            ProxyCacheService proxyCache = (ProxyCacheService) Naming.lookup(proxyId);
            return proxyCache.isAlive();

        } catch (Exception e) {
            logger.warning("Proxy {} is not alive", proxyId);
            return false;
        }
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

        // Add shutdown hook for clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down localization server...");
            server.shutdown();
        }));

        System.out.println("Localization server started on port " + port);
        System.out.println("Press Ctrl+C to stop the server");
    }

}