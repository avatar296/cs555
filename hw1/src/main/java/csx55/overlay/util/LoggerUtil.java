package csx55.overlay.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Centralized logging utility for consistent error handling and debugging
 */
public class LoggerUtil {
    
    public enum LogLevel {
        DEBUG(0, "DEBUG"),
        INFO(1, "INFO"),
        WARN(2, "WARN"),
        ERROR(3, "ERROR");
        
        private final int priority;
        private final String label;
        
        LogLevel(int priority, String label) {
            this.priority = priority;
            this.label = label;
        }
        
        public String getLabel() {
            return label;
        }
    }
    
    private static LogLevel currentLevel = LogLevel.INFO;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static boolean includeTimestamp = true;
    
    /**
     * Set the minimum log level to display
     */
    public static void setLogLevel(LogLevel level) {
        currentLevel = level;
    }
    
    /**
     * Enable or disable timestamps in log output
     */
    public static void setIncludeTimestamp(boolean include) {
        includeTimestamp = include;
    }
    
    /**
     * Log a debug message
     */
    public static void debug(String component, String message) {
        log(LogLevel.DEBUG, component, message, null);
    }
    
    /**
     * Log an info message
     */
    public static void info(String component, String message) {
        log(LogLevel.INFO, component, message, null);
    }
    
    /**
     * Log a warning message
     */
    public static void warn(String component, String message) {
        log(LogLevel.WARN, component, message, null);
    }
    
    /**
     * Log a warning message with exception
     */
    public static void warn(String component, String message, Throwable throwable) {
        log(LogLevel.WARN, component, message, throwable);
    }
    
    /**
     * Log an error message
     */
    public static void error(String component, String message) {
        log(LogLevel.ERROR, component, message, null);
    }
    
    /**
     * Log an error message with exception
     */
    public static void error(String component, String message, Throwable throwable) {
        log(LogLevel.ERROR, component, message, throwable);
    }
    
    /**
     * Core logging method
     */
    private static void log(LogLevel level, String component, String message, Throwable throwable) {
        if (level.priority < currentLevel.priority) {
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        
        // Add timestamp if enabled
        if (includeTimestamp) {
            sb.append("[").append(LocalDateTime.now().format(formatter)).append("] ");
        }
        
        // Add log level
        sb.append("[").append(level.getLabel()).append("] ");
        
        // Add component name
        if (component != null && !component.isEmpty()) {
            sb.append("[").append(component).append("] ");
        }
        
        // Add message
        sb.append(message);
        
        // Print to appropriate stream
        if (level == LogLevel.ERROR) {
            System.err.println(sb.toString());
            if (throwable != null) {
                System.err.println("Exception: " + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());
                // Only print stack trace in debug mode
                if (currentLevel == LogLevel.DEBUG) {
                    throwable.printStackTrace(System.err);
                }
            }
        } else {
            System.out.println(sb.toString());
            if (throwable != null && currentLevel == LogLevel.DEBUG) {
                throwable.printStackTrace(System.out);
            }
        }
    }
    
    /**
     * Format a connection identifier for logging
     */
    public static String formatConnection(String nodeId, String remoteNode) {
        return String.format("%s -> %s", nodeId, remoteNode);
    }
    
    /**
     * Format statistics for logging
     */
    public static String formatStats(int sent, int received, int relayed) {
        return String.format("Sent: %d, Received: %d, Relayed: %d", sent, received, relayed);
    }
}