package main.server.application.replication;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

import main.server.application.database.Database;
import main.shared.models.WorkOrder;

/**
 * Remote interface for database replication operations
 */
public interface DatabaseService extends Remote {
    // Replicate individual operations
    void replicateAddWorkOrder(int code, String name, String description) throws RemoteException;

    void replicateRemoveWorkOrder(int code) throws RemoteException;

    void replicateUpdateWorkOrder(int code, String name, String description) throws RemoteException;

    // Full database sync
    void replicateDatabase(Database database) throws RemoteException;

}