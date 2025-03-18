package main.server.proxy;

// Keep existing imports
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.net.InetAddress;

import main.server.localization.LocalizationServerHandler;
import main.server.proxy.auth.AuthService;
import main.server.proxy.cache.CacheFIFO;
import main.server.proxy.replication.ProxyPeerManager;
import main.server.proxy.replication.ProxyRMI;
import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;
import main.shared.models.WorkOrder;

public class ProxyServer {
    // Keep existing fields
    private ServerSocket serverSocket;
    private final int LOCALIZATION_PORT = 11110;
    private final String LOCALIZATION_IP = "localhost";
    private static int SERVER_PORT;
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

    private final Map<String, LocalizationServerHandler> connectedClients = new ConcurrentHashMap<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);

    // Add RMI related fields
    private static final int RMI_PORT_BASE = 44440;
    private int rmiPort;
    private static ProxyPeerManager peerManager;

    public ProxyServer(int port, String serverId) {
        System.out.println("\033[2J\033[1;1H"); // Clear screen
        this.logger = Logger.getLogger(serverId);
        this.authService = AuthService.getInstance();
        SERVER_PORT = port;

        // Calculate RMI port based on server port
        this.rmiPort = RMI_PORT_BASE + (SERVER_PORT % 1000);

        // Initialize message bus
        this.messageBus = new MessageBus("ProxyServer-" + SERVER_PORT, logger);

        // Subscribe to message types
        messageBus.subscribe(MessageType.PROXY_REGISTRATION_RESPONSE, this::handleRegistrationResponse);
        messageBus.subscribe(MessageType.PING, this::handlePing);
        messageBus.subscribe(MessageType.PROXY_PEER_INFO, this::handleProxyPeerInfo);

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

        // Register with localization server before starting
        sendStartSignal();

        // Wait for registration to complete
        waitForRegistration();

        this.run();
    }

    // Add new method to handle proxy peer info messages
    // Add handler method for PROXY_PEER_INFO
    private void handleProxyPeerInfo(Message message) {
        try {
            if (message.getPayload() instanceof Map) {
                Map<?, ?> peerInfo = (Map<?, ?>) message.getPayload();
                String peerId = peerInfo.get("id").toString();

                // Skip if it's our own ID
                if (peerId.equals(serverId)) {
                    return;
                }

                int rmiPort = Integer.parseInt(peerInfo.get("rmiPort").toString());
                String address = peerInfo.get("address").toString();

                // Register this peer with the peer manager
                logger.info("Received peer info for {}, connecting via RMI", peerId);
                peerManager.registerPeer(address, rmiPort, peerId);

                // The registerPeer method will now handle peer discovery automatically
            }
        } catch (Exception e) {
            logger.error("Error handling PROXY_PEER_INFO: {}", e.getMessage());
        }
    }

    // Add new method to send peer info to another proxy
    private void sendPeerInfo(String recipient, String flag) {
        try {
            // Create peer info message
            String[] peerInfo = new String[] {
                    serverId,
                    SERVER_IP,
                    String.valueOf(rmiPort), // Make sure this is the RMI port, not the server port
                    flag
            };

            Message peerInfoMsg = new Message(
                    MessageType.PROXY_PEER_INFO,
                    serverId,
                    recipient,
                    peerInfo);

            // Use existing localization transport to send the message
            localizationTransport.sendMessage(peerInfoMsg);
            logger.info("Sent peer info to {}", recipient);

        } catch (Exception e) {
            logger.error("Error sending peer info", e);
        }
    }

    // Add this method to do a self-test after initializing the peer manager
    // Fix RMI self-test method
    private void testRmiConnection() {
        try {
            logger.info("Testing RMI self-connection");

            // Use consistent service name - don't add "Proxy-" prefix if serverId already
            // has it
            String serviceName = serverId.startsWith("Proxy-") ? serverId : "Proxy-" + serverId;
            logger.info("Looking up RMI service: {}", serviceName);

            Registry selfRegistry = LocateRegistry.getRegistry(SERVER_IP, rmiPort);
            ProxyRMI selfTest = (ProxyRMI) selfRegistry.lookup(serviceName);
            String testId = selfTest.getProxyId();
            logger.info("RMI self-test successful, returned ID: {}", testId);

            // Add a test work order to see if search works
            WorkOrder testOrder = new WorkOrder(9999, "Test Order", "RMI Test Description");
            cache.add(testOrder);

            // Try searching for it
            WorkOrder foundOrder = selfTest.searchCache(9999);
            if (foundOrder != null) {
                logger.info("RMI search test successful, found: {}", foundOrder);
            } else {
                logger.error("RMI search test failed - order not found!");
            }
        } catch (Exception e) {
            logger.error("RMI self-test failed: {}", e.getMessage(), e);
        }
    }

    // Update existing methods

    private void shutdown() {
        running = false;
        try {
            // Stop RMI
            if (peerManager != null) {
                peerManager.shutdown();
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
            peerInfo.put("rmiPort", rmiPort);
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

            // Initialize RMI peer manager
            peerManager = new ProxyPeerManager(String.valueOf(SERVER_PORT), cache, logger, rmiPort);
            peerManager.startRMI();
            logger.info("RMI peer manager initialized on port {}", rmiPort);

            // Test RMI connection
            testRmiConnection();

            // Announce ourselves to the peer network
            announceToPeerNetwork();

            // Send peer info after successful registration
            sendProxyPeerInfo();
        } else if ("ALREADY_TAKEN".equals(status)) {
            logger.error("Registration failed: Server ID already taken");
            // Could implement retry logic with modified server ID
            handleRegistrationConflict();
        } else {
            logger.error("Registration failed: {}", status);
        }
    }

    // Add a getter for the peer manager
    public static ProxyPeerManager getPeerManager() {
        return peerManager;
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
                    new String[] { serverId, SERVER_IP, String.valueOf(SERVER_PORT) });

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

            logger.info("Registration conflict detected. Retrying with new ID {} and port {}",
                    serverId, SERVER_PORT);

            // Reset registration flag
            registrationComplete = false;

            // Send new registration request with consistent format
            Message registrationMsg = new Message(
                    MessageType.PROXY_REGISTRATION_REQUEST,
                    serverId,
                    "LocalizationServer",
                    new String[] { serverId, SERVER_IP, String.valueOf(SERVER_PORT) });

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

    public static void main(String[] args) {
        SERVER_PORT = 22220;
        serverId = "Proxy-3";
        new ProxyServer(SERVER_PORT, serverId);
    }

    // Add a new method to self-announce to the peer network

    /**
     * Announce this proxy to the peer network
     * Called after successful registration with localization server
     */
    private void announceToPeerNetwork() {
        try {
            // Ensure proxy ID has the proper format
            String normalizedServerId = serverId.startsWith("Proxy-") ? serverId : "Proxy-" + serverId;

            // Use the normalized ID for RMI binding
            String myHost = InetAddress.getLocalHost().getHostAddress();

            // Now when we get peer info through localization server,
            // our peer RMI will handle the rest of the peer discovery automatically
            logger.info("Ready to discover peers. Will connect and exchange peer info through RMI.");
        } catch (Exception e) {
            logger.error("Error announcing to peer network: {}", e.getMessage());
        }
    }
}