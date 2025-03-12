// src/com/logging/LogConfig.java
package shared.logger;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;;

public class LogConfig {
    private LogType consoleLogLevel;
    private LogType fileLogLevel;
    private String componentName;
    private Path logFilePath;
    private boolean appendToFile;
    private boolean includeTimestamp;
    private boolean includeThreadName;

    private static final String LOG_ROOT_DIR = "logs";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    public LogConfig(String componentName, Class<?> componentClass) {
        this.componentName = componentName;
        this.consoleLogLevel = LogType.INFO;
        this.fileLogLevel = LogType.DEBUG;
        this.appendToFile = true;
        this.includeTimestamp = true;
        this.includeThreadName = true;

        // Generate file path with daily folders and execution timestamp
        setupLogFilePath(componentClass);
    }

    private void setupLogFilePath(Class<?> componentClass) {
        LocalDateTime now = LocalDateTime.now();
        String todayDir = now.format(DATE_FORMATTER);
        String timeStamp = now.format(TIME_FORMATTER);

        // Create directory structure
        File rootDir = new File(LOG_ROOT_DIR);
        if (!rootDir.exists()) {
            rootDir.mkdir();
        }

        File dailyDir = new File(rootDir, todayDir);
        if (!dailyDir.exists()) {
            dailyDir.mkdir();
        }

        // Set log file path with timestamp to identify this execution
        this.logFilePath = Path.of(
                LOG_ROOT_DIR,
                todayDir,
                componentName + "_" + timeStamp + ".log");
    }

    // Getters and setters (same as before)
    public LogType getConsoleLogLevel() {
        return consoleLogLevel;
    }

    public void setConsoleLogLevel(LogType level) {
        this.consoleLogLevel = level;
    }

    public LogType getFileLogLevel() {
        return fileLogLevel;
    }

    public void setFileLogLevel(LogType level) {
        this.fileLogLevel = level;
    }

    public String getComponentName() {
        return componentName;
    }

    public Path getLogFilePath() {
        return logFilePath;
    }

    public boolean isAppendToFile() {
        return appendToFile;
    }

    public void setAppendToFile(boolean appendToFile) {
        this.appendToFile = appendToFile;
    }

    public boolean isIncludeTimestamp() {
        return includeTimestamp;
    }

    public void setIncludeTimestamp(boolean includeTimestamp) {
        this.includeTimestamp = includeTimestamp;
    }

    public boolean isIncludeThreadName() {
        return includeThreadName;
    }

    public void setIncludeThreadName(boolean includeThreadName) {
        this.includeThreadName = includeThreadName;
    }
}