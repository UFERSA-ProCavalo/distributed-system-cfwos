package main.shared.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WorkOrder implements Serializable {

    private static final long serialVersionUID = 1L;
    private int code;
    private String name;
    private String description;
    private String timestamp;

    // Make the formatter static and transient so it's not serialized
    private static final transient DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public WorkOrder(int code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.timestamp = LocalDateTime.now().format(formatter);
    }

    public WorkOrder(int code, String name, String description, String timestamp) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.timestamp = timestamp;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        WorkOrder workOrder = (WorkOrder) obj;

        return code == workOrder.code;
    }

    @Override
    public String toString() {
        return "WorkOrder{" +
                "code=" + code +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }

}