package main.server.application;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import main.server.application.database.Database;
import main.server.application.replication.DatabaseService;
import main.server.application.replication.HeartBeatService;
import main.shared.log.Logger;
import main.shared.models.WorkOrder;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.Naming;
import java.rmi.Remote;

public class ApplicationServer implements DatabaseService, HeartBeatService {
    private final Logger logger = Logger.getLogger();
    private final Database database = new Database();

    private boolean isPrimary;
    private final String primaryServerAddress;
    private int RMI_PORT = 33340;
    private int RMI_BACKUP_PORT = 33350;
    private DatabaseService backupService; // referência para o serviço de backup

    private static final AtomicInteger processConnections = new AtomicInteger(0);
    private static final AtomicInteger activeConnections = new AtomicInteger(0);
    private static boolean running = false;

    public ApplicationServer(boolean isPrimary, String primaryServerAddress, int RMI_PORT) {
        System.out.println("\033[2J\033[1;1H"); // Clear screen
        logger.info("Starting application server...");
        this.isPrimary = isPrimary;
        this.primaryServerAddress = primaryServerAddress;
        this.RMI_PORT = RMI_PORT;

        logger.info("Server starting as {}", isPrimary ? "PRIMARY" : "BACKUP");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Application server shutting down...");
            shutdown();
        }));

        try {
            if (isPrimary) {
                running = true;
                startAsPrimary();
                run();
            } else {
                startAsBackup();
                logger.info("Application server started as backup");
                logger.info("Waiting for updates or primary server to fail...");
            }
        } catch (Exception e) {
            logger.error("Error while starting Application Server: " + e.getMessage());
        }
    }

    private void startAsPrimary() throws RemoteException {
        // Start RMI registry
        try {
            logger.info("Starting RMI registry on port {}", RMI_PORT);
            LocateRegistry.createRegistry(RMI_PORT);

            // Export the remote objects
            Remote RemoteApplication = UnicastRemoteObject.exportObject(this, 0);

            HeartBeatService hbStub = (HeartBeatService) RemoteApplication;
            DatabaseService dbStub = (DatabaseService) RemoteApplication;

            // Bind the remote objects to the registry
            Naming.rebind("rmi://localhost:" + RMI_PORT + "/HeartBeatService", hbStub);
            Naming.rebind("rmi://localhost:" + RMI_PORT + "/DatabaseService", dbStub);

            logger.info("RMI services bound successfully");

            // try to connect to backup
            connectToBackup();

        } catch (Exception e) {
            logger.error("Error while starting RMI registry: " + e.getMessage());
            throw new RemoteException("Error while starting RMI registry", e);
        }
    }

    private void connectToBackup() {
        try {
            backupService = (DatabaseService) Naming
                    .lookup("rmi://" + primaryServerAddress + ":" + RMI_BACKUP_PORT + "/DatabaseService");
            logger.info("Connected to backup server");

        } catch (Exception e) {
            logger.error("Error while connecting to backup server: " + e.getMessage());
        }
    }

    private void startAsBackup() {

        try {
            logger.info("Starting RMI registry on backup server...");
            LocateRegistry.createRegistry(RMI_BACKUP_PORT);

            Remote RemoteApplication = UnicastRemoteObject.exportObject(this, 0);

            Naming.rebind("rmi://localhost:" + RMI_BACKUP_PORT + "/DatabaseService", RemoteApplication);

            logger.info("RMI services bound successfully");
            logger.info("Backup server started successfully");
        } catch (Exception e) {
            logger.error("Error while starting backup server: " + e.getMessage());
            backupService = null;
        }

        new Thread(() -> {
            // TODO Fully test replication operations
            // Caso o start tardio
            boolean firstSync = false;
            while (true) {
                try {
                    HeartBeatService primary = (HeartBeatService) Naming
                            .lookup("rmi://" + primaryServerAddress + ":" + RMI_PORT + "/HeartBeatService");

                    if (primary.isPrimaryAlive()) {
                        logger.info("Primary server is alive");

                        if (!firstSync) {
                            DatabaseService primaryDB = (DatabaseService) Naming
                                    .lookup("rmi://" + primaryServerAddress + ":" + RMI_PORT + "/DatabaseService");

                            Map<Integer, WorkOrder> primaryDatabase = new HashMap<>();
                            primaryDB.syncFullDatabase(primaryDatabase);

                            database.syncFromMap(primaryDatabase);
                            firstSync = true;
                            logger.info("Database synchronized with primary server");
                        }
                    }
                } catch (Exception e) {
                    logger.error("Primary server is down: " + e.getMessage());
                    logger.info("Starting as primary server...");
                    promote();
                    break;
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    logger.error("Error while sleeping: " + e.getMessage());
                }
            }
        }).start();
    }

    private void promote() {
        try {
            LocateRegistry.createRegistry(RMI_PORT);

            // Remote RemoteApplication = UnicastRemoteObject.exportObject(this, 0);

            Naming.rebind("rmi://localhost:" + RMI_PORT + "/HeartBeatService", this);
            Naming.rebind("rmi://localhost:" + RMI_PORT + "/DatabaseService", this);

            isPrimary = true;
            running = true;
            logger.info("Promoted to primary server");

            run();
        } catch (Exception e) {
            logger.error("Error while promoting to primary: " + e.getMessage());
        }
    }

    private void run() {
        logger.info("Application server is running...");
        try {

            ServerSocket serverSocket = new ServerSocket(33330);
            while (running) {

                try {
                    Socket clientSocket = serverSocket.accept();

                    // Increment counters
                    processConnections.incrementAndGet();
                    activeConnections.incrementAndGet();

                    logger.info("Process connections: {}, Active connections: {}",
                            processConnections.get(), activeConnections.get());

                    // Handle client in a separate thread
                    ApplicationServerHandler handler = new ApplicationServerHandler(clientSocket, logger, this);
                    Thread thread = new Thread(handler);
                    thread.start();
                } catch (Exception e) {
                    logger.error("Error while accepting client connection: " + e.getMessage());
                }

            }

        } catch (Exception e) {
            logger.error("Error in application server: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void shutdown() {
        try {
            running = false;
            Naming.unbind("rmi://localhost:" + RMI_PORT + "/HeartBeatService");
            Naming.unbind("rmi://localhost:" + RMI_PORT + "/DatabaseService");
            UnicastRemoteObject.unexportObject(this, true);
            logger.info("RMI services unbound and unexported");

            logger.info("Application server shut down successfully");
        } catch (Exception e) {
            logger.error("Error during server shutdown: " + e.getMessage());
        }
    }

    public static void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    @Override
    public boolean isPrimaryAlive() throws RemoteException {
        return isPrimary;
    }

    @Override
    public void replicateAddWorkOrder(int code, String name, String description)
            throws RemoteException {

        database.addWorkOrder(code, name, description);
        logger.info("{} added work order: {}",
                isPrimary ? "Primary" : "Backup", code);
        if (isPrimary && backupService != null) {
            try {

                backupService.replicateAddWorkOrder(code, name, description);
                logger.info("Replicated add work order: {}", code);
            } catch (Exception e) {

                logger.error("Failed to replicate to backup: {}", e.getMessage());
                backupService = null; // Clear failed backup connection
            }
        }
    }

    @Override
    public void replicateRemoveWorkOrder(int code) throws RemoteException {
        database.removeWorkOrder(code);
        logger.info("Replicated remove work order: {}", code);
    }

    @Override
    public void replicateUpdateWorkOrder(int code, String name, String description, String timestamp)
            throws RemoteException {
        database.updateWorkOrder(code, name, description, timestamp);
        logger.info("Replicated update work order: {}", code);
    }

    @Override
    public void syncFullDatabase(Map<Integer, WorkOrder> database) throws RemoteException {
        this.database.copyToMap(database);
        this.database.syncFromMap(database);
        logger.info("Replicated full database");
    }

    public ApplicationServer getServer() {
        return this;
    }

    public int getRMI_PORT() {
        return this.RMI_PORT;
    }

    public Database getDatabase() {
        return this.database;
    }

    public static void main(String[] args) {

        if (args.length > 0) {
            if ("-prim".equals(args[0])) {
                new ApplicationServer(true, "127.0.0.1", 33340);
            } else if ("-back".equals(args[0])) {
                new ApplicationServer(false, "127.0.0.1", 33340);
            }
        }

        // choose if the server is primary or backup
        System.out.println("Starting application server...");
        Scanner scanner = new Scanner(System.in);
        System.out.println("Is this server the primary server? (y/n)");
        boolean primary = scanner.nextLine().equalsIgnoreCase("y");
        scanner.close();

        new ApplicationServer(primary, "127.0.0.1", 33340);

    }
}
