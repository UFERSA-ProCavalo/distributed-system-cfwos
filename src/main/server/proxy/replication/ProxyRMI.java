package main.server.proxy.replication;

import java.rmi.Remote;
import java.rmi.RemoteException;
import main.shared.models.WorkOrder;

/**
 * RMI interface for proxy server communication
 */
public interface ProxyRMI extends Remote {
    /**
     * Search for a work order in this proxy's cache
     * @param code The work order code to search for
     * @return The work order if found, null otherwise
     */
    WorkOrder searchCache(int code) throws RemoteException;
    
    /**
     * Get the proxy server's ID
     * @return The proxy server ID
     */
    String getProxyId() throws RemoteException;
}
