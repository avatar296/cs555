package csx55.overlay.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility class for comprehensive input validation and parameter checking.
 * Provides validation methods for network parameters, data integrity,
 * and security-related input sanitization.
 */
public class ValidationUtil {
    
    // Private constructor to prevent instantiation
    private ValidationUtil() {
        throw new AssertionError("Utility class - do not instantiate");
    }
    
    /** Minimum allowed port number (above well-known ports) */
    public static final int MIN_PORT = 1024;
    
    /** Maximum allowed port number */
    public static final int MAX_PORT = 65535;
    
    /** Minimum number of messaging rounds */
    public static final int MIN_ROUNDS = 1;
    
    /** Maximum number of messaging rounds */
    public static final int MAX_ROUNDS = 100000;
    
    /** Minimum connection requirement */
    public static final int MIN_CR = 1;
    
    /** Maximum message size in bytes (1MB) */
    public static final int MAX_MESSAGE_SIZE = 1024 * 1024;
    
    /**
     * Validates if a port number is within acceptable range.
     * 
     * @param port the port number to validate
     * @return true if port is valid, false otherwise
     */
    public static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }
    
    /**
     * Validates IP address format.
     * Checks for proper IPv4 format with valid octets.
     * 
     * @param ipAddress the IP address string to validate
     * @return true if IP address is valid, false otherwise
     */
    public static boolean isValidIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }
        
        try {
            InetAddress.getByName(ipAddress);
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
     * Validates node ID format.
     * Expected format: IP:port
     * 
     * @param nodeId the node identifier to validate
     * @return true if node ID is valid, false otherwise
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
     * Validates connection requirement for overlay creation.
     * Checks mathematical feasibility and balance constraints.
     * 
     * @param cr the connection requirement
     * @param nodeCount the number of nodes in the overlay
     * @return null if valid, error message otherwise
     */
    public static String validateConnectionRequirement(int cr, int nodeCount) {
        if (cr < MIN_CR) {
            return "Connection requirement must be at least " + MIN_CR;
        }
        
        if (nodeCount <= cr) {
            return "Connection requirement (" + cr + ") must be less than number of nodes (" + nodeCount + ")";
        }
        
        int totalEdgesNeeded = (nodeCount * cr) / 2;
        int maxPossibleEdges = (nodeCount * (nodeCount - 1)) / 2;
        
        if (totalEdgesNeeded > maxPossibleEdges) {
            return "Impossible to create overlay: requires " + totalEdgesNeeded + 
                   " edges but maximum possible is " + maxPossibleEdges;
        }
        
        if ((nodeCount % 2 == 1) && (cr % 2 == 1) && cr > 1) {
            return "Warning: With odd number of nodes (" + nodeCount + 
                   ") and odd CR (" + cr + "), overlay may not be perfectly balanced";
        }
        
        return null;
    }
    
    /**
     * Validates the number of messaging rounds.
     * 
     * @param rounds the number of rounds to validate
     * @return true if rounds is valid, false otherwise
     */
    public static boolean isValidRounds(int rounds) {
        return rounds >= MIN_ROUNDS && rounds <= MAX_ROUNDS;
    }
    
    /**
     * Validates message payload size.
     * 
     * @param data the message data to validate
     * @return true if data size is valid, false otherwise
     */
    public static boolean isValidMessageSize(byte[] data) {
        return data != null && data.length > 0 && data.length <= MAX_MESSAGE_SIZE;
    }
    
    /**
     * Checks if adding two long values would cause overflow.
     * 
     * @param current the current value
     * @param addend the value to add
     * @return true if overflow would occur, false otherwise
     */
    public static boolean willOverflow(long current, long addend) {
        if (addend > 0 && current > Long.MAX_VALUE - addend) {
            return true;
        }
        if (addend < 0 && current < Long.MIN_VALUE - addend) {
            return true;
        }
        return false;
    }
    
    /**
     * Performs safe addition with overflow protection.
     * Caps result at Long.MAX_VALUE if overflow is detected.
     * 
     * @param a first value
     * @param b second value
     * @return sum of a and b, or Long.MAX_VALUE if overflow
     */
    public static long safeAdd(long a, long b) {
        if (willOverflow(a, b)) {
            LoggerUtil.warn("ValidationUtil", 
                "Integer overflow detected in statistics: " + a + " + " + b);
            return Long.MAX_VALUE;
        }
        return a + b;
    }
    
    /**
     * Validates hostname format.
     * Accepts both DNS hostnames and IP addresses.
     * 
     * @param hostname the hostname to validate
     * @return true if hostname is valid, false otherwise
     */
    public static boolean isValidHostname(String hostname) {
        if (hostname == null || hostname.isEmpty() || hostname.length() > 255) {
            return false;
        }
        
        String pattern = "^([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])(\\.([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9]))*$";
        return hostname.matches(pattern) || isValidIpAddress(hostname);
    }
    
    /**
     * Sanitizes string input to prevent injection attacks.
     * Removes control characters and limits length.
     * 
     * @param input the string to sanitize
     * @return sanitized string
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        
        String sanitized = input.replaceAll("[\\p{Cntrl}]", "");
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000);
        }
        
        return sanitized;
    }
    
    /**
     * Validates link weight value.
     * 
     * @param weight the weight to validate
     * @return true if weight is valid, false otherwise
     */
    public static boolean isValidWeight(int weight) {
        return weight >= 1 && weight <= 10;
    }
}