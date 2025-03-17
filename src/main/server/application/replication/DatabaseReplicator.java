package main.server.application.replication;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

import main.shared.models.WorkOrder;

/**
 * Remote interface for database replication operations
 */
public interface DatabaseReplicator extends Remote {
    // Replicate individual operations
    void replicateAddWorkOrder(int code, String name, String description, String timestamp) throws RemoteException;
    void replicateRemoveWorkOrder(int code) throws RemoteException;
    void replicateUpdateWorkOrder(int code, String name, String description, String timestamp) throws RemoteException;
    
    // Full database sync
    void syncFullDatabase(Map<Integer, WorkOrder> database) throws RemoteException;
    
    // Health check
    boolean heartbeat() throws RemoteException;
}