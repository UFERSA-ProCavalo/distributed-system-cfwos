package main.server.application.replication;

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

import main.server.application.database.Database;
import main.shared.log.Logger;
import main.shared.models.WorkOrder;

/**
 * Manages replication between primary and backup servers
 */
public class ReplicationManager implements DatabaseReplicator, ServerCoordinator {
    private final Logger logger;
    private final Database database;
    private final String serverAddress;
    private final int serverPort;
    private final int rmiPort;
    private boolean isPrimary = false;

    private Registry registry;
    private final Map<String, DatabaseReplicator> backupServers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Create a replication manager
     * 
     * @param serverAddress Local server address
     * @param serverPort    Local server port
     * @param rmiPort       RMI registry port
     * @param database      Reference to the database to replicate
     * @param logger        System logger
     */
    public ReplicationManager(String serverAddress, int serverPort, int rmiPort, Database database, Logger logger) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.rmiPort = rmiPort;
        this.database = database;
        this.logger = logger;
    }

    /**
     * Initialize as primary server
     */
    public void initAsPrimary() throws Exception {
        logger.info("Initializing as PRIMARY application server at {}:{}", serverAddress, serverPort);
        isPrimary = true;

        // Create and start RMI registry
        try {
            registry = LocateRegistry.createRegistry(rmiPort);
            logger.info("RMI Registry created at port {}", rmiPort);

            // Export this object
            DatabaseReplicator replicatorStub = (DatabaseReplicator) UnicastRemoteObject.exportObject(this, 0);
            ServerCoordinator coordinatorStub = (ServerCoordinator) UnicastRemoteObject.exportObject(this, 0);

            // Bind to registry
            registry.rebind("DatabaseReplicator", replicatorStub);
            registry.rebind("ServerCoordinator", coordinatorStub);

            logger.info("Replication services registered successfully");

            // Start backup health check
            startBackupHealthCheck();

        } catch (RemoteException e) {
            isPrimary = false;
            logger.error("Failed to initialize as primary", e);
            throw e;
        }
    }

    /**
     * Initialize as backup server
     */
    public void initAsBackup(String primaryAddress, int primaryRmiPort) throws Exception {
        logger.info("Initializing as BACKUP application server at {}:{}", serverAddress, serverPort);
        isPrimary = false;

        try {
            // Lookup primary server
            Registry primaryRegistry = LocateRegistry.getRegistry(primaryAddress, primaryRmiPort);
            ServerCoordinator coordinator = (ServerCoordinator) primaryRegistry.lookup("ServerCoordinator");

            // Register with primary
            String backupId = coordinator.registerAsBackup(serverAddress, serverPort);
            logger.info("Registered as backup with ID: {}", backupId);

            // Export this object to receive updates
            DatabaseReplicator stub = (DatabaseReplicator) UnicastRemoteObject.exportObject(this, 0);

            // Create local registry so primary can connect back
            registry = LocateRegistry.createRegistry(rmiPort);
            registry.rebind("DatabaseReplicator", stub);

            // Get initial database
            DatabaseReplicator primaryReplicator = (DatabaseReplicator) primaryRegistry.lookup("DatabaseReplicator");
            Map<Integer, WorkOrder> initialDb = new HashMap<>(); // Create a map to receive the database
            primaryReplicator.syncFullDatabase(initialDb);

            // Start primary health check
            startPrimaryHealthCheck(primaryAddress, primaryRmiPort);

        } catch (Exception e) {
            logger.error("Failed to initialize as backup", e);
            throw e;
        }
    }

    /**
     * Start primary server health check
     */
    private void startPrimaryHealthCheck(String primaryAddress, int primaryRmiPort) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Registry primaryRegistry = LocateRegistry.getRegistry(primaryAddress, primaryRmiPort);
                ServerCoordinator coordinator = (ServerCoordinator) primaryRegistry.lookup("ServerCoordinator");

                // Check if primary is alive
                coordinator.isPrimary();

                logger.debug("Primary server health check: OK");
            } catch (Exception e) {
                logger.warning("Primary server health check failed: {}", e.getMessage());

                // Attempt to become the primary
                tryBecomePrimary();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Start backup servers health check
     */
    private void startBackupHealthCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            // Copy to avoid ConcurrentModificationException
            Map<String, DatabaseReplicator> backups = new HashMap<>(backupServers);

            for (Map.Entry<String, DatabaseReplicator> entry : backups.entrySet()) {
                String backupId = entry.getKey();
                DatabaseReplicator backup = entry.getValue();

                try {
                    // Check if backup is alive
                    backup.heartbeat();
                } catch (Exception e) {
                    logger.warning("Backup {} failed health check: {}", backupId, e.getMessage());

                    // Remove dead backup
                    backupServers.remove(backupId);
                    logger.info("Removed inactive backup: {}", backupId);
                }
            }

            logger.debug("Active backup servers: {}", backupServers.size());
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Try to become the primary server
     */
    private synchronized void tryBecomePrimary() {
        if (isPrimary)
            return;

        try {
            logger.info("Attempting to become PRIMARY server");
            initAsPrimary();
            logger.info("Successfully promoted to PRIMARY server");
        } catch (Exception e) {
            logger.error("Failed to become primary server", e);
        }
    }

    // DatabaseReplicator interface methods

    @Override
    public void replicateAddWorkOrder(int code, String name, String description, String timestamp)
            throws RemoteException {
        if (isPrimary) {
            logger.warning("Primary received replication request - ignoring");
            return;
        }

        try {
            logger.info("Replicating ADD operation: code={}", code);
            database.addWorkOrder(code, name, description, timestamp);
        } catch (Exception e) {
            logger.error("Failed to replicate ADD operation", e);
            throw new RemoteException("Replication failed", e);
        }
    }

    @Override
    public void replicateRemoveWorkOrder(int code) throws RemoteException {
        if (isPrimary) {
            logger.warning("Primary received replication request - ignoring");
            return;
        }

        try {
            logger.info("Replicating REMOVE operation: code={}", code);
            database.removeWorkOrder(code);
        } catch (Exception e) {
            logger.error("Failed to replicate REMOVE operation", e);
            throw new RemoteException("Replication failed", e);
        }
    }

    @Override
    public void replicateUpdateWorkOrder(int code, String name, String description, String timestamp)
            throws RemoteException {
        if (isPrimary) {
            logger.warning("Primary received replication request - ignoring");
            return;
        }

        try {
            logger.info("Replicating UPDATE operation: code={}", code);
            database.updateWorkOrder(code, name, description, timestamp);
        } catch (Exception e) {
            logger.error("Failed to replicate UPDATE operation", e);
            throw new RemoteException("Replication failed", e);
        }
    }

    @Override
    public void syncFullDatabase(Map<Integer, WorkOrder> databaseCopy) throws RemoteException {
        if (!isPrimary) {
            // Backup receiving a full sync from primary
            try {
                logger.info("Receiving full database sync");
                database.syncFromMap(databaseCopy);
                logger.info("Database sync complete, {} records", databaseCopy.size());
            } catch (Exception e) {
                logger.error("Failed to sync database", e);
                throw new RemoteException("Database sync failed", e);
            }
        } else {
            // Primary sending full database to a backup
            try {
                logger.info("Sending full database sync, {} records", database.getSize());
                database.copyToMap(databaseCopy);
            } catch (Exception e) {
                logger.error("Failed to prepare database for sync", e);
                throw new RemoteException("Database copy failed", e);
            }
        }
    }

    @Override
    public boolean heartbeat() throws RemoteException {
        // Simple heartbeat method
        return true;
    }

    // ServerCoordinator interface methods

    @Override
    public String registerAsBackup(String serverAddress, int port) throws RemoteException {
        if (!isPrimary) {
            throw new RemoteException("Not primary server");
        }

        String backupId = serverAddress + ":" + port;

        try {
            logger.info("New backup server registering: {}", backupId);

            // Connect to the backup's replicator
            Registry backupRegistry = LocateRegistry.getRegistry(serverAddress, port);
            DatabaseReplicator backupReplicator = (DatabaseReplicator) backupRegistry.lookup("DatabaseReplicator");

            // Store the remote reference
            backupServers.put(backupId, backupReplicator);

            // Send full database sync
            backupReplicator.syncFullDatabase(new HashMap<>());

            logger.info("Backup server registered: {}", backupId);
            return backupId;
        } catch (Exception e) {
            logger.error("Failed to register backup server", e);
            throw new RemoteException("Registration failed", e);
        }
    }

    @Override
    public boolean isPrimary() throws RemoteException {
        return isPrimary;
    }

    @Override
    public int getBackupCount() throws RemoteException {
        return backupServers.size();
    }

    /**
     * Propagate a database change to all backup servers
     */
    public void propagateAddWorkOrder(int code, String name, String description, String timestamp) {
        if (!isPrimary)
            return;

        for (Map.Entry<String, DatabaseReplicator> entry : backupServers.entrySet()) {
            String backupId = entry.getKey();
            DatabaseReplicator backup = entry.getValue();

            try {
                backup.replicateAddWorkOrder(code, name, description, timestamp);
                logger.debug("Propagated ADD to backup: {}", backupId);
            } catch (Exception e) {
                logger.error("Failed to propagate ADD to backup: {}", backupId, e);
            }
        }
    }

    /**
     * Propagate a remove operation to all backup servers
     */
    public void propagateRemoveWorkOrder(int code) {
        if (!isPrimary)
            return;

        for (Map.Entry<String, DatabaseReplicator> entry : backupServers.entrySet()) {
            String backupId = entry.getKey();
            DatabaseReplicator backup = entry.getValue();

            try {
                backup.replicateRemoveWorkOrder(code);
                logger.debug("Propagated REMOVE to backup: {}", backupId);
            } catch (Exception e) {
                logger.error("Failed to propagate REMOVE to backup: {}", backupId, e);
            }
        }
    }

    /**
     * Propagate an update operation to all backup servers
     */
    public void propagateUpdateWorkOrder(int code, String name, String description, String timestamp) {
        if (!isPrimary)
            return;

        for (Map.Entry<String, DatabaseReplicator> entry : backupServers.entrySet()) {
            String backupId = entry.getKey();
            DatabaseReplicator backup = entry.getValue();

            try {
                backup.replicateUpdateWorkOrder(code, name, description, timestamp);
                logger.debug("Propagated UPDATE to backup: {}", backupId);
            } catch (Exception e) {
                logger.error("Failed to propagate UPDATE to backup: {}", backupId, e);
            }
        }
    }

    /**
     * Shutdown replication system
     */
    public void shutdown() {
        logger.info("Shutting down replication manager");

        try {
            scheduler.shutdown();

            // Unexport remote objects
            if (isPrimary) {
                try {
                    UnicastRemoteObject.unexportObject(this, true);
                    registry.unbind("DatabaseReplicator");
                    registry.unbind("ServerCoordinator");
                } catch (Exception e) {
                    logger.error("Error unexporting objects", e);
                }
            }

            logger.info("Replication manager shutdown complete");
        } catch (Exception e) {
            logger.error("Error during replication manager shutdown", e);
        }
    }
}