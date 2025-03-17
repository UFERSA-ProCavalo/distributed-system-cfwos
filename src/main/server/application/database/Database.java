package main.server.application.database;

import main.shared.models.WorkOrder;
import main.shared.utils.tree.ItemFormatter;
import main.shared.utils.tree.TreeAVL;

import java.util.Map;

public class Database {
    private static final Object lock = new Object();
    private TreeAVL<Integer, WorkOrder> database;
    // Formatador para WorkOrders
    private final ItemFormatter<WorkOrder> workOrderFormatter;

    public Database() {
        this.database = new TreeAVL<>();
        this.workOrderFormatter = this::formatWorkOrder;
    }

    public void addWorkOrder(int code, String name, String description) {
        synchronized (lock) {
            WorkOrder workOrder = new WorkOrder(code, name, description);
            database.Insert(code, workOrder);
        }
    }

    public void addWorkOrder(int code, String name, String description, String timestamp) {
        synchronized (lock) {
            WorkOrder workOrder = new WorkOrder(code, name, description, timestamp);
            database.Insert(code, workOrder);
        }
    }

    public void removeWorkOrder(int code) {
        synchronized (lock) {
            database.Remove(code);
        }
    }

    public void updateWorkOrder(int code, String name, String description, String timestamp) {
        synchronized (lock) {
            WorkOrder temp = database.Search(code);
            temp.setName(name);
            temp.setDescription(description);
            temp.setTimestamp(timestamp);
        }
        // database.Insert(code, workOrder);
    }

    public WorkOrder searchWorkOrder(int code) {
        synchronized (lock) {
            return database.Search(code);
        }
    }

    public void showDatabase() {
        database.Show();
    }

    public void showDatabaseReverse() {
        database.ShowReverse();
    }

    public int getTreeHeight() {
        return database.getTreeHeight();
    }

    public int getSize() {
        return database.getSize();
    }

    public int getBalanceCounter() {
        return database.getBalanceCounter();
    }

    /**
     * Retorna o conteúdo do banco de dados como uma string formatada
     */
    public String getDatabaseContent() {
        return database.getFormattedContent(workOrderFormatter);
    }

    /**
     * Retorna o conteúdo do banco de dados em ordem reversa como uma string
     * formatada
     */
    public String getDatabaseContentReverse() {
        return database.getFormattedContentReverse(workOrderFormatter);
    }

    /**
     * Formata uma ordem de serviço individual
     */
    private String formatWorkOrder(WorkOrder wo) {
        return String.format("Code: %d | Name: %s | Description: %s | Timestamp: %s",
                wo.getCode(), wo.getName(), wo.getDescription(), wo.getTimestamp());
    }

    /**
     * Clear the database
     */
    public void clearDatabase() {
        synchronized (lock) {
            database = new TreeAVL<>();
        }
    }

    /**
     * Copy database contents to a map (for replication)
     */
    public void copyToMap(Map<Integer, WorkOrder> targetMap) {
        synchronized (lock) {
            database.populateMap(targetMap);
        }
    }

    /**
     * Sync database from a map (for replication)
     */
    public void syncFromMap(Map<Integer, WorkOrder> sourceMap) {
        // Clear existing database
        clearDatabase();
        // Synchronize the database with the provided map

        // Add all work orders from the map
        for (WorkOrder order : sourceMap.values()) {
            addWorkOrder(order.getCode(), order.getName(), order.getDescription(), order.getTimestamp());
        }
    }
}