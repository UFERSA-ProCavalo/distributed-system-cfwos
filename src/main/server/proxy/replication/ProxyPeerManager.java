package main.server.proxy.replication;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    
    public ProxyPeerManager(String proxyId, CacheFIFO<WorkOrder> cache, Logger logger, int rmiPort) {
        this.proxyId = proxyId;
        this.cache = cache;
        this.logger = logger;
        this.rmiPort = rmiPort;
    }
    
    /**
     * Initialize RMI server
     */
    public void startRMI() {
        try {
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
            registry.rebind("Proxy-" + proxyId, rmiImpl);
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
            
            // Get the registry
            Registry peerRegistry = LocateRegistry.getRegistry(host, port);
            
            // Lookup the proxy
            ProxyRMI peer = (ProxyRMI) peerRegistry.lookup("Proxy-" + peerId);
            
            // Verify connection by getting proxy ID
            String remotePeerId = peer.getProxyId();
            if (!remotePeerId.equals(peerId)) {
                logger.warning("Peer ID mismatch: expected {}, got {}", peerId, remotePeerId);
                return;
            }
            
            // Add to peer list
            peerProxies.put(peerId, peer);
            logger.info("Successfully registered peer proxy: {}", peerId);
            
        } catch (RemoteException | NotBoundException e) {
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
                WorkOrder result = peer.searchCache(workOrderCode);
                if (result != null) {
                    logger.info("Found work order {} in peer cache {}", workOrderCode, peerId);
                    return result;
                }
            } catch (RemoteException e) {
                logger.warning("Error searching peer {} cache: {}", peerId, e.getMessage());
            }
        }
        
        logger.info("Work order {} not found in any peer cache", workOrderCode);
        return null;
    }
    
    /**
     * Shutdown RMI
     */
    public void shutdown() {
        try {
            if (rmiImpl != null) {
                UnicastRemoteObject.unexportObject(rmiImpl, true);
                logger.info("Unexported RMI object");
            }
            
            if (registry != null) {
                registry.unbind("Proxy-" + proxyId);
                logger.info("Unbound from RMI registry");
            }
            
            // Clear peer list
            peerProxies.clear();
            
        } catch (Exception e) {
            logger.error("Error shutting down RMI: {}", e.getMessage());
        }
    }
}