package main.server.proxy.replication;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import main.server.proxy.cache.CacheFIFO;
import main.shared.log.Logger;
import main.shared.models.WorkOrder;

/**
 * Manages RMI connections to other proxy servers
 */
public class ProxyPeerManager {
    private final String proxyId;
    private final CacheFIFO<WorkOrder> cache;
    private final Logger logger;
    private final int rmiPort;
    private Registry registry;
    private ProxyRMIImpl rmiImpl;

    // Map of proxy ID to RMI interface
    private final Map<String, ProxyRMI> peerProxies = new ConcurrentHashMap<>();

    // Scheduled executor for periodic peer status logging
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ProxyPeerManager(String proxyId, CacheFIFO<WorkOrder> cache, Logger logger, int rmiPort) {
        this.proxyId = proxyId;
        this.cache = cache;
        this.logger = logger;
        this.rmiPort = rmiPort;

        // Schedule the periodic peer status logging
        scheduler.scheduleAtFixedRate(
                this::logPeerConnections,
                5, // Initial delay
                5, // Period
                TimeUnit.SECONDS // Time unit
        );
    }

    /**
     * Log the current peer connections
     */
    private void logPeerConnections() {
        try {
            // Log cache stats
            int cacheSize = cache.getSize();
            String cacheContents = cache.getCacheContentsAsString();
            logger.info("Local cache stats: size={}, contents=[\n{}\n]", cacheSize, cacheContents);

            // Rest of the method remains the same
            if (peerProxies.isEmpty()) {
                logger.info("No peer connections established");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Current peer connections (").append(peerProxies.size()).append("):");

            for (Map.Entry<String, ProxyRMI> entry : peerProxies.entrySet()) {
                String peerId = entry.getKey();
                ProxyRMI peer = entry.getValue();

                // Test if peer is still alive
                boolean isActive = false;
                try {
                    // Try to get the peer ID to verify connection is active
                    String remotePeerId = peer.getProxyId();
                    isActive = true;
                    sb.append("\n  - ").append(peerId).append(" (active, id: ").append(remotePeerId).append(")");
                } catch (RemoteException e) {
                    sb.append("\n  - ").append(peerId).append(" (connection error: ").append(e.getMessage())
                            .append(")");

                    // Remove dead peer
                    peerProxies.remove(peerId);
                    logger.warning("Removed dead peer connection: {}", peerId);
                }
            }

            logger.info(sb.toString());

        } catch (Exception e) {
            logger.error("Error while logging peer connections: {}", e.getMessage());
        }
    }

    /**
     * Initialize RMI server
     */
    public void startRMI() {
        try {
            // Set security manager with permissions if not already set
            if (System.getSecurityManager() == null) {
                System.setProperty("java.security.policy", "server.policy");
            }

            // Create RMI registry
            try {
                registry = LocateRegistry.createRegistry(rmiPort);
                logger.info("Created RMI registry on port {}", rmiPort);
            } catch (RemoteException e) {
                logger.info("RMI registry already exists, getting reference");
                registry = LocateRegistry.getRegistry(rmiPort);
            }

            // Create and register RMI implementation
            rmiImpl = new ProxyRMIImpl(proxyId, cache, logger);

            // Use consistent service name - don't add "Proxy-" prefix if proxyId already has it
            String serviceName = proxyId.startsWith("Proxy-") ? proxyId : "Proxy-" + proxyId;
            registry.rebind(serviceName, rmiImpl);
            logger.info("Registered proxy {} with RMI on port {}", proxyId, rmiPort);

        } catch (RemoteException e) {
            logger.error("Error starting RMI server: {}", e.getMessage());
        }
    }

    /**
     * Register a peer proxy
     */
    public void registerPeer(String host, int port, String peerId) {
        try {
            // Check if already registered
            if (peerProxies.containsKey(peerId)) {
                logger.debug("Peer {} already registered", peerId);
                return;
            }

            logger.info("Attempting to connect to peer {} at {}:{}", peerId, host, port);

            // Get the registry
            Registry peerRegistry = LocateRegistry.getRegistry(host, port);

            // Use consistent service name - don't add "Proxy-" prefix if peerId already has it
            String serviceName = peerId.startsWith("Proxy-") ? peerId : "Proxy-" + peerId;

            logger.info("Looking up RMI service: {}", serviceName);

            // Lookup the proxy with error handling
            ProxyRMI peer = null;
            try {
                peer = (ProxyRMI) peerRegistry.lookup(serviceName);
            } catch (NotBoundException e) {
                logger.error("RMI service '{}' not bound on host {}:{}", serviceName, host, port);
                return;
            }

            if (peer == null) {
                logger.error("Could not locate RMI service for peer {}", peerId);
                return;
            }

            // Try a test call to verify connection
            try {
                String remotePeerId = peer.getProxyId();
                logger.info("Successfully connected to peer {} (reported ID: {})", peerId, remotePeerId);

                // Add to peer list
                peerProxies.put(peerId, peer);
                logger.info("Registered peer proxy: {}", peerId);

                // Immediately log the current peer connections after adding a new one
                logPeerConnections();
            } catch (RemoteException e) {
                logger.error("Error communicating with peer {}: {}", peerId, e.getMessage());
            }

        } catch (RemoteException e) {
            logger.error("Error connecting to peer {}: {}", peerId, e.getMessage());
        }
    }

    /**
     * Search all peer caches for a work order
     */
    public WorkOrder searchPeerCaches(int workOrderCode) {
        for (Map.Entry<String, ProxyRMI> entry : peerProxies.entrySet()) {
            String peerId = entry.getKey();
            ProxyRMI peer = entry.getValue();

            try {
                logger.info("Sending search request to peer {} for code {}", peerId, workOrderCode);
                WorkOrder result = peer.searchCache(workOrderCode);
                if (result != null) {
                    logger.info("Received result from peer {}: {}", peerId, result);
                    // Print all fields to verify data
                    logger.info("Result details: code={}, name={}, desc={}",
                            result.getCode(), result.getName(), result.getDescription());
                    return result;
                }
            } catch (RemoteException e) {
                logger.warning("Error searching peer {} cache: {}", peerId, e.getMessage());

                // If we can't reach the peer, remove it from our list
                peerProxies.remove(peerId);
                logger.warning("Removed unreachable peer: {}", peerId);
            }
        }

        logger.info("Work order {} not found in any peer cache", workOrderCode);
        return null;
    }

    /**
     * Get the number of connected peers
     */
    public int getPeerCount() {
        return peerProxies.size();
    }

    /**
     * Shutdown RMI
     */
    public void shutdown() {
        try {
            // Stop the scheduler
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            if (rmiImpl != null) {
                UnicastRemoteObject.unexportObject(rmiImpl, true);
                logger.info("Unexported RMI object");
            }

            if (registry != null) {
                try {
                    registry.unbind("Proxy-" + proxyId);
                    logger.info("Unbound from RMI registry");
                } catch (Exception e) {
                    logger.warning("Error unbinding from registry: {}", e.getMessage());
                }
            }

            // Clear peer list
            peerProxies.clear();

        } catch (Exception e) {
            logger.error("Error shutting down RMI: {}", e.getMessage());
        }
    }
}