package main.server.application.replication;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface for server coordination
 */
public interface ServerCoordinator extends Remote {
    // Register as a backup server
    String registerAsBackup(String serverAddress, int port) throws RemoteException;
    
    // Check primary status
    boolean isPrimary() throws RemoteException;
    
    // Get number of registered backups
    int getBackupCount() throws RemoteException;
}