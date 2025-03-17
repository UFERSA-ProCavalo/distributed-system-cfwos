package main.server.proxy;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import main.server.localization.LocalizationServerHandler;
import main.server.proxy.auth.AuthService;
import main.server.proxy.cache.CacheFIFO;
import main.shared.log.Logger;
import main.shared.messages.Message;
import main.shared.messages.MessageBus;
import main.shared.messages.MessageType;
import main.shared.messages.SocketMessageTransport;
import main.shared.models.WorkOrder;

public class ProxyServer {
    private ServerSocket serverSocket;
    private final int LOCALIZATION_PORT = 11110;
    private final String LOCALIZATION_IP = "localhost";
    private static int SERVER_PORT;
    private final String SERVER_IP = "localhost";
    private final AuthService authService;
    private final Logger logger;
    private boolean running = true;

    // Server identity and messaging
    private String serverId;
    private MessageBus messageBus;
    private SocketMessageTransport localizationTransport;
    private Thread heartbeatThread;

    // Cache compartilhada entre todos os handlers
    public static final CacheFIFO<WorkOrder> cache = new CacheFIFO<WorkOrder>();

    public static int connectionCount = 0;
    public static int activeConnections = 0;

    private final Map<String, LocalizationServerHandler> connectedClients = new ConcurrentHashMap<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);

    public ProxyServer(int port) {
        System.out.println("\033[2J\033[1;1H"); // Clear screen
        this.logger = Logger.getLogger();
        this.authService = AuthService.getInstance();
        SERVER_PORT = port;

        // Initialize message bus
        this.messageBus = new MessageBus("ProxyServer-" + SERVER_PORT, logger);

        // Subscribe to message types
        messageBus.subscribe(MessageType.PROXY_REGISTRATION_RESPONSE, this::handleRegistrationResponse);
        messageBus.subscribe(MessageType.HEARTBEAT_REQUEST, this::handleHeartbeatRequest);

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

        // Monitor localization connection
        startLocalizationConnectionMonitor();

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

    private void sendStartSignal() {
        try {
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

    private void handleRegistrationResponse(Message message) {
        if (message.getType() == MessageType.PROXY_REGISTRATION_RESPONSE) {
            Object status = message.getPayload();

            if ("SUCCESS".equals(status)) {
                logger.info("Successfully registered with localization server as {}", serverId);
            } else if ("ALREADY_TAKEN".equals(status)) {
                // Try with different server ID and port
                handleRegistrationConflict();
            } else {
                logger.error("Unknown registration response: {}", status);
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

            // Send new registration request with consistent format
            Message registrationMsg = new Message(
                    MessageType.PROXY_REGISTRATION_REQUEST,
                    serverId,
                    "LocalizationServer",
                    new String[] { serverId, SERVER_IP, String.valueOf(SERVER_PORT) });

            logger.info("Sent registration request to localization server: {}", registrationMsg.getPayload());
            localizationTransport.sendMessage(registrationMsg);

        } catch (Exception e) {
            logger.error("Error handling registration conflict", e);
        }
    }

    private void handleHeartbeatRequest(Message message) {
        if (message.getType() == MessageType.HEARTBEAT_REQUEST) {
            try {
                // Respond on the SAME connection, don't create a new one
                Message heartbeatResponse = new Message(
                        MessageType.HEARTBEAT_RESPONSE,
                        serverId,
                        message.getSender(),
                        new String[] {
                                serverId,
                                String.valueOf(SERVER_PORT),
                                String.valueOf(activeConnections)
                        });

                // Use the existing localizationTransport
                localizationTransport.sendMessage(heartbeatResponse);
                logger.debug("Sent heartbeat response to {}", message.getSender());

            } catch (Exception e) {
                logger.error("Error sending heartbeat response", e);
            }
        }
    }

    private void startLocalizationConnectionMonitor() {
        heartbeatThread = new Thread(() -> {
            int reconnectAttempts = 0;
            int maxReconnectAttempts = 5;

            while (running) {
                try {
                    // Only check connection every 30 seconds
                    Thread.sleep(30000);

                    // Check if we really lost connection
                    boolean connectionLost = false;

                    if (localizationTransport == null) {
                        connectionLost = true;
                    } else {
                        try {
                            // Send a simple ping to test connection
                            Message pingMsg = new Message(
                                    MessageType.PING,
                                    serverId,
                                    "LocalizationServer",
                                    System.currentTimeMillis());

                            localizationTransport.sendMessage(pingMsg);
                            // Reset reconnect attempts if successful
                            reconnectAttempts = 0;
                        } catch (Exception e) {
                            // Failed to send message, connection probably lost
                            connectionLost = true;
                            logger.warning("Connection check failed: {}", e.getMessage());
                        }
                    }

                    if (connectionLost) {
                        reconnectAttempts++;

                        if (reconnectAttempts <= maxReconnectAttempts) {
                            logger.info("Lost connection to localization server (attempt {}/{}), reconnecting...",
                                    reconnectAttempts, maxReconnectAttempts);
                            sendStartSignal();
                        } else {
                            logger.error("Failed to reconnect after {} attempts", maxReconnectAttempts);
                            // Either quit or wait longer between reconnect attempts
                            Thread.sleep(30000); // Wait 30 seconds before trying again
                            reconnectAttempts = 0; // Reset counter for a fresh attempt cycle
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in localization connection monitor", e);
                }
            }
        }, "localization-connection-monitor");

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void shutdown() {
        running = false;
        try {
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

    public static void main(String[] args) {
        SERVER_PORT = 22220;
        new ProxyServer(SERVER_PORT);
    }

    // Method to handle client disconnection, update active connections
    public static void clientDisconnected() {
        if (activeConnections > 0) {
            activeConnections--;
        }
    }
}