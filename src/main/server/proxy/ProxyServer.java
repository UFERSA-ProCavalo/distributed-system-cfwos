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

        // Initialize RMI peer manager
        peerManager = new ProxyPeerManager(String.valueOf(SERVER_PORT), cache, logger, rmiPort);
        peerManager.startRMI();
        logger.info("RMI peer manager initialized on port {}", rmiPort);

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

        // Test RMI connection
        testRmiConnection();

        this.run();
    }

    // Add new method to handle proxy peer info messages
    private void handleProxyPeerInfo(Message message) {
        if (message.getType() == MessageType.PROXY_PEER_INFO) {
            try {
                logger.info("Received PROXY_PEER_INFO from {}", message.getSender());

                // Extract peer information
                String[] peerInfo = (String[]) message.getPayload();
                if (peerInfo.length >= 3) {
                    String peerId = peerInfo[0];
                    String host = peerInfo[1];
                    int peerRmiPort = Integer.parseInt(peerInfo[2]);

                    // Don't register ourselves - compare without the "Proxy-" prefix if present
                    String strippedPeerId = peerId.startsWith("Proxy-") ? peerId.substring(6) : peerId;
                    String strippedServerId = serverId.startsWith("Proxy-") ? serverId.substring(6) : serverId;

                    if (strippedPeerId.equals(strippedServerId)) {
                        logger.debug("Ignoring own proxy peer info: {} vs {}", peerId, serverId);
                        return;
                    }

                    // Register this peer
                    logger.info("Registering peer proxy: {} at {}:{}", peerId, host, peerRmiPort);
                    peerManager.registerPeer(host, peerRmiPort, peerId);

                    // Send our info back to the peer if this isn't a reply already
                    if (!"reply".equals(peerInfo.length > 3 ? peerInfo[3] : "")) {
                        sendPeerInfo(message.getSender(), "reply");
                    }
                }

            } catch (Exception e) {
                logger.error("Error handling proxy peer info", e);
            }
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
            
            // Use consistent service name - don't add "Proxy-" prefix if serverId already has it
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

    private void handleRegistrationResponse(Message message) {
        if (message.getType() == MessageType.PROXY_REGISTRATION_RESPONSE) {
            Object status = message.getPayload();

            if ("SUCCESS".equals(status)) {
                logger.info("Successfully registered with localization server as {}", serverId);

                // Mark registration as complete and notify waiting threads
                synchronized (registrationLock) {
                    registrationComplete = true;
                    registrationLock.notifyAll();
                }

            } else if ("ALREADY_TAKEN".equals(status)) {
                // Try with different server ID and port
                handleRegistrationConflict();
            } else {
                logger.error("Unknown registration response: {}", status);

                // Mark registration as failed but complete
                synchronized (registrationLock) {
                    registrationComplete = true;
                    registrationLock.notifyAll();
                }
            }
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

    // Use existing main method
    public static void main(String[] args) {
        SERVER_PORT = 22220;
        serverId = "Proxy-1";
        new ProxyServer(SERVER_PORT, serverId);
    }
}