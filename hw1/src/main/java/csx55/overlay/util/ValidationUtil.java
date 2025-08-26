package csx55.overlay.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility class for input validation and parameter checking
 */
public class ValidationUtil {
    
    // Constants for validation bounds
    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;
    public static final int MIN_ROUNDS = 1;
    public static final int MAX_ROUNDS = 100000;
    public static final int MIN_CR = 1;
    public static final int MAX_MESSAGE_SIZE = 1024 * 1024; // 1MB max message size
    
    /**
     * Validate port number
     */
    public static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }
    
    /**
     * Validate IP address format
     */
    public static boolean isValidIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }
        
        try {
            InetAddress.getByName(ipAddress);
            // Additional check for valid IP format
            String[] parts = ipAddress.split("\\.");
            if (parts.length != 4) {
                return false;
            }
            for (String part : parts) {
                try {
                    int value = Integer.parseInt(part);
                    if (value < 0 || value > 255) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    /**
     * Validate node ID format (IP:port)
     */
    public static boolean isValidNodeId(String nodeId) {
        if (nodeId == null || !nodeId.contains(":")) {
            return false;
        }
        
        String[] parts = nodeId.split(":");
        if (parts.length != 2) {
            return false;
        }
        
        try {
            int port = Integer.parseInt(parts[1]);
            return isValidIpAddress(parts[0]) && isValidPort(port);
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validate connection requirement
     */
    public static String validateConnectionRequirement(int cr, int nodeCount) {
        if (cr < MIN_CR) {
            return "Connection requirement must be at least " + MIN_CR;
        }
        
        if (nodeCount <= cr) {
            return "Connection requirement (" + cr + ") must be less than number of nodes (" + nodeCount + ")";
        }
        
        // Check if overlay is mathematically possible
        int totalEdgesNeeded = (nodeCount * cr) / 2;
        int maxPossibleEdges = (nodeCount * (nodeCount - 1)) / 2;
        
        if (totalEdgesNeeded > maxPossibleEdges) {
            return "Impossible to create overlay: requires " + totalEdgesNeeded + 
                   " edges but maximum possible is " + maxPossibleEdges;
        }
        
        // Check if CR is even when node count is odd
        if ((nodeCount % 2 == 1) && (cr % 2 == 1) && cr > 1) {
            return "Warning: With odd number of nodes (" + nodeCount + 
                   ") and odd CR (" + cr + "), overlay may not be perfectly balanced";
        }
        
        return null; // Valid
    }
    
    /**
     * Validate number of rounds
     */
    public static boolean isValidRounds(int rounds) {
        return rounds >= MIN_ROUNDS && rounds <= MAX_ROUNDS;
    }
    
    /**
     * Validate message payload size
     */
    public static boolean isValidMessageSize(byte[] data) {
        return data != null && data.length > 0 && data.length <= MAX_MESSAGE_SIZE;
    }
    
    /**
     * Check for integer overflow in statistics
     */
    public static boolean willOverflow(long current, long addend) {
        if (addend > 0 && current > Long.MAX_VALUE - addend) {
            return true; // Will overflow
        }
        if (addend < 0 && current < Long.MIN_VALUE - addend) {
            return true; // Will underflow
        }
        return false;
    }
    
    /**
     * Safe addition with overflow check
     */
    public static long safeAdd(long a, long b) {
        if (willOverflow(a, b)) {
            LoggerUtil.warn("ValidationUtil", 
                "Integer overflow detected in statistics: " + a + " + " + b);
            return Long.MAX_VALUE; // Cap at max value
        }
        return a + b;
    }
    
    /**
     * Validate hostname
     */
    public static boolean isValidHostname(String hostname) {
        if (hostname == null || hostname.isEmpty() || hostname.length() > 255) {
            return false;
        }
        
        // Check for valid hostname pattern
        String pattern = "^([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])(\\.([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9]))*$";
        return hostname.matches(pattern) || isValidIpAddress(hostname);
    }
    
    /**
     * Sanitize string input to prevent injection attacks
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        
        // Remove control characters and limit length
        String sanitized = input.replaceAll("[\\p{Cntrl}]", "");
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000);
        }
        
        return sanitized;
    }
    
    /**
     * Validate weight value for links
     */
    public static boolean isValidWeight(int weight) {
        return weight >= 1 && weight <= 10;
    }
}