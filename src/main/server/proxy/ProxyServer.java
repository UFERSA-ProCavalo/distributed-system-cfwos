package main.server.proxy;

// Keep existing imports
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.rmi.registry.Registry;

import main.server.proxy.auth.AuthService;
import main.server.proxy.cache.CacheFIFO;
import main.server.proxy.replication.ProxyCacheService;
import main.server.proxy.replication.ProxyRegistryService;
import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;
import main.shared.models.WorkOrder;

public class ProxyServer extends UnicastRemoteObject implements ProxyCacheService {
    private ServerSocket serverSocket;
    private final int LOCALIZATION_PORT = 11110;
    private final String LOCALIZATION_IP = "localhost";
    private int SERVER_PORT;
    private final String SERVER_IP = "localhost";
    private final AuthService authService;
    private final Logger logger;
    private boolean running = true;

    // Server identity and messaging
    private static String serverId;
    private MessageBus messageBus;
    private SocketMessageTransport localizationTransport;
    private Thread heartbeatThread;

    // Registration synchronization
    private final Object registrationLock = new Object();
    private volatile boolean registrationComplete = false;

    // Cache compartilhada entre todos os handlers
    public static final CacheFIFO<WorkOrder> cache = new CacheFIFO<WorkOrder>();

    public static int connectionCount = 0;
    public static int activeConnections = 0;

    // RMI
    private int RMI_PORT;
    private final List<ProxyCacheService> peerProxies = new ArrayList<>();

    // Peer logging scheduler
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ProxyServer(int port, String serverId) throws RemoteException {
        System.out.println("\033[2J\033[1;1H"); // Clear screen
        this.logger = Logger.getLogger(serverId);
        this.authService = AuthService.getInstance();
        SERVER_PORT = port;

        // Calculate RMI port based on server port
        this.RMI_PORT = SERVER_PORT + 100;

        // Initialize message bus
        this.messageBus = new MessageBus("ProxyServer-" + SERVER_PORT, logger);

        // Subscribe to message types
        messageBus.subscribe(MessageType.PROXY_REGISTRATION_RESPONSE, this::handleRegistrationResponse);
        messageBus.subscribe(MessageType.PING, this::handlePing);

        // Inicializa o sistema de cache
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

        // Start RMI services
        startRMIServices();

        // Register with localization server before starting
        sendStartSignal();

        // Wait for registration to complete
        waitForRegistration();

        // Discover and connect to peers
        discoverAndConnectToPeers();

        // Start periodic peer logging
        // startPeerLogging();

        // Start accepting connections
        this.run();
    }

    private void shutdown() {
        running = false;
        try {
            // Shutdown the peer logging scheduler
            if (scheduler != null) {
                scheduler.shutdownNow();
                logger.info("Peer logging scheduler stopped");
            }

            // Stop heartbeat thread
            if (heartbeatThread != null) {
                heartbeatThread.interrupt();
            }

            // Close localization connection
            if (localizationTransport != null) {
                localizationTransport.close();
            }

            // Shutdown message bus
            if (messageBus != null) {
                messageBus.unsubscribeAll();
                messageBus.shutdown();
            }

            // Unbind RMI services
            try {
                Naming.unbind("rmi://localhost:" + RMI_PORT + "/ProxyCacheService");
                UnicastRemoteObject.unexportObject(this, true);
                logger.info("RMI services unbound and unexported");
            } catch (Exception e) {
                logger.error("Error unbinding RMI services: {}", e.getMessage());
            }

            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    // Add a method to send proxy peer info after successful registration
    private void sendProxyPeerInfo() {
        try {
            // Ensure we have a valid server ID with proper prefix
            String normalizedServerId = serverId.startsWith("Proxy-") ? serverId : "Proxy-" + serverId;

            // Create peer info payload
            Map<String, Object> peerInfo = new HashMap<>();
            peerInfo.put("id", normalizedServerId);
            peerInfo.put("port", SERVER_PORT);
            peerInfo.put("rmiPort", RMI_PORT);
            peerInfo.put("address", InetAddress.getLocalHost().getHostAddress());

            // Create the message
            Message peerInfoMsg = new Message(
                    MessageType.PROXY_PEER_INFO,
                    normalizedServerId,
                    "LocalizationServer",
                    peerInfo);

            // Send through the localization transport
            localizationTransport.sendMessage(peerInfoMsg);
            logger.info("Sent PROXY_PEER_INFO to localization server");
        } catch (Exception e) {
            logger.error("Error sending PROXY_PEER_INFO: {}", e.getMessage());
        }
    }

    // Modify the registration response handler to send peer info on success
    private void handleRegistrationResponse(Message message) {
        String status = message.getPayload().toString();

        if ("SUCCESS".equals(status)) {
            logger.info("Registration successful with localization server");
            registrationComplete = true;

        } else if ("ALREADY_TAKEN".equals(status)) {
            logger.error("Registration failed: Server ID already taken");
            // Could implement retry logic with modified server ID
            handleRegistrationConflict();
        } else {
            logger.error("Registration failed: {}", status);
        }
    }

    private void sendStartSignal() {
        try {
            // Reset registration flag
            registrationComplete = false;

            // Generate server ID based on port
            serverId = "Proxy-" + SERVER_PORT;
            logger.info("Registering with localization server as {} on port {}", serverId, SERVER_PORT);

            // Connect to localization server
            Socket socket = new Socket(LOCALIZATION_IP, LOCALIZATION_PORT);
            localizationTransport = new SocketMessageTransport(socket, messageBus, logger, true);

            // Send registration request message with all required info
            Message registrationMsg = new Message(
                    MessageType.PROXY_REGISTRATION_REQUEST,
                    serverId,
                    "LocalizationServer",
                    new String[] { serverId, SERVER_IP, String.valueOf(SERVER_PORT), String.valueOf(RMI_PORT) });

            localizationTransport.sendMessage(registrationMsg);
            logger.info("Sent registration request to localization server");

        } catch (Exception e) {
            logger.error("Error registering with localization server", e);
        }
    }

    private void waitForRegistration() {
        synchronized (registrationLock) {
            try {
                // Wait for up to 10 seconds for registration response
                long startTime = System.currentTimeMillis();
                long timeout = 10000; // 10 seconds timeout

                while (!registrationComplete && (System.currentTimeMillis() - startTime) < timeout) {
                    logger.debug("Waiting for registration response...");
                    registrationLock.wait(1000); // Wait in 1-second intervals
                }

                if (!registrationComplete) {
                    logger.error("Registration response timeout - localization server may be down");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Registration wait interrupted", e);
            }
        }
    }

    private void handleRegistrationConflict() {
        try {
            // Close existing server socket if already bound
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            // Increment port and update server ID
            SERVER_PORT += 1;
            serverId = "Proxy-" + SERVER_PORT;
            RMI_PORT += 1;

            logger.info("Registration conflict detected. Retrying with new ID {} and port {} (RMI port: {})",
                    serverId, SERVER_PORT, RMI_PORT);

            // Restart RMI services with new port
            startRMIServices();

            // Rest of the method remains the same
            // Reset registration flag
            registrationComplete = false;

            // Send new registration request with consistent format
            Message registrationMsg = new Message(
                    MessageType.PROXY_REGISTRATION_REQUEST,
                    serverId,
                    "LocalizationServer",
                    new String[] { serverId, SERVER_IP, String.valueOf(SERVER_PORT), String.valueOf(RMI_PORT) });

            logger.info("Sent registration request to localization server: {}", registrationMsg.getPayload());
            localizationTransport.sendMessage(registrationMsg);

            // Wait again for the new registration response
            waitForRegistration();

        } catch (Exception e) {
            logger.error("Error handling registration conflict", e);

            // Mark registration as failed but complete to avoid deadlock
            synchronized (registrationLock) {
                registrationComplete = true;
                registrationLock.notifyAll();
            }
        }
    }

    private void handlePing(Message message) {
        if (message.getType() == MessageType.PING) {
            try {
                logger.debug("Received PING from {}", message.getSender());
                // Respond with PONG that includes connection info
                Message pingResponse = new Message(
                        MessageType.PONG,
                        serverId,
                        message.getSender(),
                        new String[] {
                                serverId,
                                SERVER_IP,
                                String.valueOf(SERVER_PORT),
                                String.valueOf(activeConnections)
                        });

                // Use the existing localizationTransport
                localizationTransport.sendMessage(pingResponse);
                logger.debug("Sent PONG to {}", message.getSender());

            } catch (Exception e) {
                logger.error("Error sending PING response", e);
            }
        }
    }

    // Method to handle client disconnection, update active connections
    public static void clientDisconnected() {
        if (activeConnections > 0) {
            activeConnections--;
        }
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

                // Update connection counters
                connectionCount++;
                activeConnections++;

                // Create handler for this client
                ProxyServerHandler handler = new ProxyServerHandler(clientSocket, authService, logger, cache, this);
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (Exception e) {
            if (running) {
                logger.error("Error in server main loop", e);
            }
        }
    }

    private void startRMIServices() throws RemoteException {
        try {
            RMI_PORT = SERVER_PORT + 1;
            logger.info("Starting RMI services on port {}", RMI_PORT);

            // Create registry
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);

            String rmiUrl = "rmi://localhost:" + RMI_PORT + "/ProxyCacheService/" + SERVER_PORT;

            registry.rebind("ProxyCacheService", this);

            peerProxies.add(this);

            logger.info("Proxy server {} bound to RMI registry", rmiUrl);
        } catch (Exception e) {
            logger.error("Failed to start RMI services: {}", e.getMessage());
            throw e;
        }
    }

    public WorkOrder searchPeerCaches(int code) {
        for (ProxyCacheService peer : peerProxies) {
            try {
                WorkOrder workOrder = peer.lookupWorkOrder(code);
                if (workOrder != null) {
                    return workOrder;
                }
            } catch (Exception e) {
                logger.error("Error searching peer cache: {}", e.getMessage());
                // Remove unreachable peer
                this.peerProxies.remove(peer);
            }
        }
        return null;
    }

    public void invalidatePeerCaches(int code, String operation, WorkOrder updatedWorkOrder) {
        for (ProxyCacheService peer : peerProxies) {
            try {
                peer.notifyCacheInvalidation(code, operation, updatedWorkOrder);
            } catch (Exception e) {
                logger.error("Error invalidating peer cache: {}", e.getMessage());
                // Remove unreachable peer
                this.peerProxies.remove(peer);
            }
        }
    }

    /**
     * Start a scheduled task to log connected peers every 5 seconds
     */
    private void startPeerLogging() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                logPeerConnections();
            } catch (Exception e) {
                logger.error("Error in peer logging task", e);
            }
        }, 5, 5, TimeUnit.SECONDS);

        logger.info("Started periodic peer logging task (every 5 seconds)");
    }

    /**
     * Log information about all connected peers
     */
    private void logPeerConnections() {
        if (peerProxies.isEmpty()) {
            logger.info("=== No connected peers ===");
            return;
        }

        logger.info("=== Connected Peers ({} total) ===", peerProxies.size());

        int i = 1;

        for (ProxyCacheService peer : peerProxies) {
            try {
                logger.info("{}. {} - Status: Connected", i++, peer.toString());
            } catch (Exception e) {
                logger.info("{}. {} - Status: Error ({})", i++, peer.toString(), e.getMessage());
                // Consider removing unresponsive peer
                peerProxies.remove(peer);
            }
        }

        logger.info("=====================================");
    }

    private void discoverAndConnectToPeers() {
        try {
            // Use consistent port for RMI registry
            Registry registry = LocateRegistry.getRegistry(LOCALIZATION_IP, 8000);
            logger.debug("Looking up LocalizationServer in registry at {}:{}", LOCALIZATION_IP, 8000);

            ProxyRegistryService registryService = (ProxyRegistryService) registry.lookup("LocalizationServer");
            logger.info("Found registry service: {}", registryService);

            // Try to get registered proxies first (may fail, but continue)
            try {
                List<String> existingProxies = registryService.getRegisteredProxies();
                logger.info("Found {} existing proxies", existingProxies.size());
            } catch (Exception e) {
                logger.warning("Could not get existing proxies: {}", e.getMessage());
            }

            // Register ourselves
            try {
                registryService.registerProxy("Proxy-" + SERVER_PORT);
                logger.info("Registered with proxy registry service");
            } catch (Exception e) {
                logger.error("Failed to register with proxy registry service: {}", e.getMessage());
                e.printStackTrace();
            }

            // Hardcode peer connections for simplicity (remove complex discovery)
            int[] peerPorts = { 22220, 22240, 22260 };
            for (int peerPort : peerPorts) {
                if (peerPort != SERVER_PORT) { // Don't connect to self
                    try {
                        int peerRmiPort = peerPort + 1;
                        logger.debug("Connecting to peer at port {}", peerRmiPort);

                        Registry peerRegistry = LocateRegistry.getRegistry(SERVER_IP, peerRmiPort);
                        ProxyCacheService peer = (ProxyCacheService) peerRegistry.lookup("ProxyCacheService");

                        if (peer != null) {
                            peerProxies.add(peer);
                            logger.info("Connected to peer at port {}", peerPort);
                        }
                    } catch (Exception e) {
                        logger.debug("Peer at port {} not available yet", peerPort);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error discovering peers: {}", e.getMessage());
            e.printStackTrace(); // For debugging
        }
    }

    @Override
    public WorkOrder lookupWorkOrder(int code) throws RemoteException {
        return cache.searchByCode(new WorkOrder(code, null, null));
    }

    @Override
    public void notifyCacheInvalidation(int code, String operation, WorkOrder updatedWorkOrder)
            throws RemoteException {
        WorkOrder existing = cache.searchByCode(new WorkOrder(code, null, null));
        if (existing != null) {
            if ("UPDATE".equals(operation) && updatedWorkOrder != null) {
                cache.remove(existing);
                cache.add(updatedWorkOrder);
            } else if ("REMOVE".equals(operation)) {
                cache.remove(existing);
            }
        }
    }

    @Override
    public void addProxy(String proxyId) throws RemoteException {
        try {
            ProxyCacheService peer = (ProxyCacheService) Naming
                    .lookup("rmi://localhost:" + RMI_PORT + "/ProxyCacheService");
            peerProxies.add(peer);
            logger.info("Peer {} connected", proxyId);
        } catch (Exception e) {
            logger.error("Error adding peer {}: {}", proxyId, e.getMessage());
            try {
                Registry registry = LocateRegistry.getRegistry(8000);
                ProxyRegistryService registryService = (ProxyRegistryService) registry.lookup("LocalizationServer");
                registryService.unregisterProxy(proxyId);
                logger.info("Unregistered peer {}", proxyId);
            } catch (Exception e2) {
                logger.error("Error unregistering peer {}: {}", proxyId, e2.getMessage());
            }
        }
    }

    @Override
    public void checkProxys(String proxyId) throws RemoteException {
        for (ProxyCacheService peer : peerProxies) {
            try {
                peer.isAlive();
            } catch (Exception e) {
                logger.error("Peer {} is not alive", proxyId);
                peerProxies.remove(peer);
            }
        }
    }

    public static void main(String[] args) throws RemoteException {
        // new ProxyServer(22220, "Proxy-1");
        // new ProxyServer(22221, "Proxy-2");
        // new ProxyServer(22222, "Proxy-3");

        int port = 22240;
        String id = "Proxy-2";

        // Parse command line arguments
        if (args.length > 0) {
            // Check for server shorthand arguments
            if ("-sp1".equals(args[0])) {
                port = 22220;
                id = "Proxy-1";
            } else if ("-sp2".equals(args[0])) {
                port = 22221;
                id = "Proxy-2";
            } else if ("-sp3".equals(args[0])) {
                port = 22222;
                id = "Proxy-3";
            }

            System.out.println("Starting Proxy Server with ID: " + id + " on port " + port);
            new ProxyServer(port, id);
        }
        new ProxyServer(port, id);
    }
    
    @Override
    public boolean isAlive() throws RemoteException {
        return true;
    }
}