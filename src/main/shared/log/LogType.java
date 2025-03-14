package main.shared.log;

public enum LogType {
    DEBUG(0, "DEBUG"),
    INFO(1, "INFO"),
    WARNING(2, "WARNING"),
    ERROR(3, "ERROR"),
    NONE(4, "NONE");

    private final int level;
    private final String label;

    LogType(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int getLevel() {
        return level;
    }

    public String getLabel() {
        return label;
    }

    public boolean isLoggable(LogType minimumLevel) {
        return this.level >= minimumLevel.level;
    }
}