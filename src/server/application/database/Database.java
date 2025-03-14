package server.application.database;

import shared.utils.tree.TreeAVL;
import shared.models.WorkOrder;

public class Database {
    private TreeAVL<Integer, WorkOrder> database;

    public Database() {
        this.database = new TreeAVL<>();
    }

    public void addWorkOrder(int code, String name, String description) {
        WorkOrder workOrder = new WorkOrder(code, name, description);
        database.Insert(code, workOrder);

    }

    public void addWorkOrder(int code, String name, String description, String timestamp) {
        WorkOrder workOrder = new WorkOrder(code, name, description, timestamp);
        database.Insert(code, workOrder);
    }

    public void removeWorkOrder(int code) {
        database.Remove(code);
    }

    public void updateWorkOrder(int code, String name, String description, String timestamp) {
        WorkOrder temp = database.Search(code);
        temp.setName(name);
        temp.setDescription(description);
        temp.setTimestamp(timestamp);
        // database.Insert(code, workOrder);
    }

    public WorkOrder searchWorkOrder(int code) {
        return database.Search(code);
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
}