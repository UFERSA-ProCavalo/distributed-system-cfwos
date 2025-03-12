package shared.log;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Logger {
    private static final Map<String, Logger> loggerInstances = new HashMap<>();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");
    private static PrintStream originalOut;
    private static PrintStream originalErr;

    private final String componentName;
    private final Path logFilePath;
    private PrintWriter fileWriter;
    private LogType consoleLogLevel = LogType.INFO;
    private LogType fileLogLevel = LogType.DEBUG;

    private Logger(String componentName, Path logDir) {
        this.componentName = componentName;

        // Create daily directory structure
        String today = LocalDateTime.now().format(DATE_FORMATTER);
        String timeStamp = LocalDateTime.now().format(TIME_FORMATTER);

        // Full path: [package_path]/logs/[date]/[component]_[time].log
        Path logsDir = logDir.resolve("logs");
        Path dailyDir = logsDir.resolve(today);
        Path debugDir = logsDir.resolve("_debug.log");
        dailyDir.toFile().mkdirs();

        // Set log file path
        this.logFilePath = dailyDir.resolve(componentName + "_" + timeStamp + ".log");
        try {

            debugDir.toFile().createNewFile();

        } catch (Exception e) {
            System.err.println("Failed to create debug log file: " + e.getMessage());
            // TODO: handle exception
        }
        // Initialize log file
        initializeLogFile();
    }

    private void initializeLogFile() {
        try {
            fileWriter = new PrintWriter(new FileWriter(logFilePath.toFile()));
            fileWriter.println("=".repeat(80));
            fileWriter.println("Log started at " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            fileWriter.println("=".repeat(80));
            fileWriter.flush();

            System.out.println("Log file: " + logFilePath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create log file: " + e.getMessage());
        }
    }

    /**
     * Get a logger that automatically determines the component name and path based
     * on the calling class
     */
    public static Logger getLogger() {
        // Find the calling class
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String callerClassName = stack[2].getClassName();

        try {
            // Get the class
            Class<?> callerClass = Class.forName(callerClassName);
            String componentName = callerClass.getSimpleName();

            // Get package directory path
            Path packagePath = getPackagePath(callerClass);

            return getLogger(componentName, packagePath);
        } catch (ClassNotFoundException e) {
            // Fallback to current directory
            return getLogger("Unknown", Paths.get(""));
        }
    }

    /**
     * Get the filesystem path corresponding to the package of the given class
     */
    private static Path getPackagePath(Class<?> clazz) {
        try {
            // Convert package name to path
            String packageName = clazz.getPackage().getName();
            String packagePath = packageName.replace('.', '/');

            // Get path of the class file
            URI uri = clazz.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path basePath;

            String uriPath = uri.getPath();
            if (uriPath.endsWith(".jar")) {
                // If running from a JAR, use the parent directory
                basePath = Paths.get(uri).getParent();
            } else {
                // If running from classes, find the base directory
                basePath = Paths.get(uri);

                // Navigate to the src directory if we're in a bin/classes directory
                if (basePath.endsWith("bin") || basePath.endsWith("classes")) {
                    basePath = basePath.getParent().resolve("src");
                }
            }

            // Combine the base path with the package path
            return basePath.resolve(packagePath);

        } catch (URISyntaxException e) {
            // Fallback to current directory
            System.err.println("Error determining package path: " + e.getMessage());
            return Paths.get("");
        }
    }

    /**
     * Get a logger with explicit component name
     */

    /**
     * Get a logger with explicit component name and log directory
     */
    public static Logger getLogger(String componentName, Path logDir) {
        String key = componentName + "@" + logDir;
        synchronized (loggerInstances) {
            if (!loggerInstances.containsKey(key)) {
                loggerInstances.put(key, new Logger(componentName, logDir));
            }
            return loggerInstances.get(key);
        }
    }

    // Logging methods
    public void debug(String message) {
        log(LogType.DEBUG, message);
    }

    public void info(String message) {
        log(LogType.INFO, message);
    }

    public void warning(String message) {
        log(LogType.WARNING, message);
    }

    public void error(String message) {
        log(LogType.ERROR, message);
    }

    private String getColorForLevel(LogType level) {
        switch (level) {
            case DEBUG:
                return ConsoleColors.CYAN;
            case INFO:
                return ConsoleColors.GREEN;
            case WARNING:
                return ConsoleColors.YELLOW;
            case ERROR:
                return ConsoleColors.RED;
            default:
                return ConsoleColors.RESET;
        }
    }

    private void log(LogType level, String message) {
        // Skip if below thresholds
        if (level.getLevel() < consoleLogLevel.getLevel() &&
                level.getLevel() < fileLogLevel.getLevel()) {
            return;
        }

        // Format the message
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String threadName = Thread.currentThread().getName();

        // Create colored version for console
        String coloredMessage = String.format("%s[%s]%s %s[%s]%s %s[%s]%s [%s] %s",
                ConsoleColors.BLUE, timestamp, ConsoleColors.RESET,
                ConsoleColors.WHITE_BOLD, componentName, ConsoleColors.RESET,
                getColorForLevel(level), level.getLabel(), ConsoleColors.RESET,
                threadName, message);

        // Create plain version for file
        String plainMessage = String.format("[%s] [%s] [%s] [%s] %s",
                timestamp, componentName, level.getLabel(), threadName, message);

        // Console output
        if (level.getLevel() >= consoleLogLevel.getLevel()) {
            if (level == LogType.ERROR) {
                System.err.println(coloredMessage);
            } else {
                System.out.println(coloredMessage);
            }
        }

        // File output (plain text without colors)
        if (fileWriter != null && level.getLevel() >= fileLogLevel.getLevel()) {
            synchronized (this) {
                fileWriter.println(plainMessage);
                fileWriter.flush();
            }
        }
    }

    /**
     * Explicitly writes a message to both console and log file, bypassing any
     * redirection logic
     */
    public void logToFile(LogType level, String message) {
        // Format the message
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String threadName = Thread.currentThread().getName();

        // Create colored version for console
        String coloredMessage = String.format("%s[%s]%s %s[%s]%s %s[%s]%s [%s] %s",
                ConsoleColors.BLUE, timestamp, ConsoleColors.RESET,
                ConsoleColors.WHITE_BOLD, componentName, ConsoleColors.RESET,
                getColorForLevel(level), level.getLabel(), ConsoleColors.RESET,
                threadName, message);

        // Create plain version for file
        String plainMessage = String.format("[%s] [%s] [%s] [%s] %s",
                timestamp, componentName, level.getLabel(), threadName, message);

        synchronized (this) {
            // Write colored version to console
            if (level == LogType.ERROR) {
                originalErr.println(coloredMessage);
            } else {
                originalOut.println(coloredMessage);
            }

            // Write plain version to file
            if (fileWriter != null && level.getLevel() >= fileLogLevel.getLevel()) {
                fileWriter.println(plainMessage);
                fileWriter.flush();
            }
        }
    }

    public void close() {
        synchronized (this) {
            if (fileWriter != null) {
                fileWriter.println("=".repeat(80));
                fileWriter.println("Log closed at " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
                fileWriter.println("=".repeat(80));
                fileWriter.close();
                fileWriter = null;
            }
        }
    }

    // Getters and setters
    public void setConsoleLogLevel(LogType level) {
        this.consoleLogLevel = level;
    }

    public void setFileLogLevel(LogType level) {
        this.fileLogLevel = level;
    }

    private static PrintStream debugStream;

    /**
     * Get a PrintStream that can be used for debug output that won't be recursively
     * logged
     */
    public static synchronized PrintStream getDebugStream() {
        if (debugStream == null) {
            debugStream = new PrintStream(System.out) {
                @Override
                public void println(String x) {
                    super.println("[DEBUG] " + x);
                }
            };
        }
        return debugStream;
    }

    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        boolean escapeNext = false;

        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);

            if (escapeNext) {
                result.append(current);
                escapeNext = false;
                continue;
            }

            if (current == '\\') {
                escapeNext = true;
                continue;
            }

            if (current == '{' && i < message.length() - 1 && message.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    result.append(args[argIndex++]);
                    i++; // Skip the closing brace
                } else {
                    result.append("{}");
                    i++;
                }
            } else {
                result.append(current);
            }
        }

        return result.toString();
    }

    // Update logging methods to accept variable arguments
    public void debug(String message, Object... args) {
        log(LogType.DEBUG, formatMessage(message, args));
    }

    public void info(String message, Object... args) {
        log(LogType.INFO, formatMessage(message, args));
    }

    public void warning(String message, Object... args) {
        log(LogType.WARNING, formatMessage(message, args));
    }

    public void error(String message, Object... args) {
        log(LogType.ERROR, formatMessage(message, args));
    }

    public void error(String message, Throwable throwable) {
        error(message + ": {}", throwable.getMessage());
        for (StackTraceElement element : throwable.getStackTrace()) {
            error("\tat {}", element);
        }
    }

}