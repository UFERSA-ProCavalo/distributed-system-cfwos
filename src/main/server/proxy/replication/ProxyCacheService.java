package main.server.proxy.replication;

import java.rmi.Remote;
import java.rmi.RemoteException;
import main.shared.models.WorkOrder;

/**
 * Remote interface for cache replication operations between proxies
 */
public interface ProxyCacheService extends Remote {

    // Add proxy to the remote cache
    void addProxy(String proxyId) throws RemoteException;

    // Check if a proxy is in the remote cache
    void checkProxys(String proxyId) throws RemoteException;

    // Search for a work order in this proxy's cache
    WorkOrder lookupWorkOrder(int code) throws RemoteException;

    // Notify that a work order was removed or updated elsewhere
    void notifyCacheInvalidation(int code, String operation, WorkOrder updatedWorkOrder) throws RemoteException;

    // Check if this proxy is alive
    boolean isAlive() throws RemoteException;

}
