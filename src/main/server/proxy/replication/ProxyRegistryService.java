package main.server.proxy.replication;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Remote interface for proxy registration and discovery
 */
public interface ProxyRegistryService extends Remote {
    // Register this proxy with the registry
    void registerProxy(String proxyId) throws RemoteException;

    // Notify that a proxy has been registered or removed
    void notifyProxys(String proxyId, boolean operation) throws RemoteException;

    // Get all registered proxies
    List<String> getRegisteredProxies() throws RemoteException;

    // Unregister this proxy with the registry
    void unregisterProxy(String proxyId) throws RemoteException;

    // Check if this proxy is alive
    boolean isAlive(String proxyId) throws RemoteException;
}