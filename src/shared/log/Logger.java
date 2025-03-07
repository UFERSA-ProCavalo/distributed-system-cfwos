package shared.log;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Logger {

            private static final String LOG_FILE = "app.log";

            // Enum for log levels
            public enum LogLevel {
                        INFO,
                        WARN,
                        ERROR
            }

            // Method to log messages at different log levels
            public static void log(LogLevel level, String message) {
                        String timestamp = java.time.LocalDateTime.now().toString();
                        String logMessage = String.format("[%s] [%s]: %s", timestamp, level, message);

                        // Print to console
                        System.out.println(logMessage);

                        // Write to file
                        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                                    out.println(logMessage);
                        } catch (IOException e) {
                                    System.err.println("Failed to write log to file: " + e.getMessage());
                        }
            }

            // Convenience methods for different log levels
            public static void info(String message) {
                        log(LogLevel.INFO, message);
            }

            public static void warn(String message) {
                        log(LogLevel.WARN, message);
            }

            public static void error(String message) {
                        log(LogLevel.ERROR, message);
            }
}
