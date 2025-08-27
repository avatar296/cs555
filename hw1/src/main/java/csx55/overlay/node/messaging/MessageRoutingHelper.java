package csx55.overlay.node.messaging;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.wireformats.Event;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Helper class providing common utilities for message routing operations.
 * Centralizes connection validation and message sending logic to reduce
 * code duplication across messaging services.
 */
public final class MessageRoutingHelper {
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private MessageRoutingHelper() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Validates and retrieves a connection for the specified node.
     * Logs appropriate warnings if the connection is not available.
     * 
     * @param connections the connections cache to search
     * @param nodeId the target node identifier
     * @param context additional context for logging
     * @return Optional containing the connection if found, empty otherwise
     */
    public static Optional<TCPConnection> getValidatedConnection(
            TCPConnectionsCache connections, 
            String nodeId, 
            String context) {
        
        TCPConnection connection = connections.getConnection(nodeId);
        if (connection == null) {
            LoggerUtil.warn("MessageRoutingHelper", 
                String.format("No connection available to %s for %s", nodeId, context));
            return Optional.empty();
        }
        return Optional.of(connection);
    }
    
    /**
     * Safely sends an event through the specified connection.
     * Handles IOException and logs appropriate error messages.
     * 
     * @param connection the TCP connection to use
     * @param event the event to send
     * @param context additional context for error logging
     * @return true if the event was sent successfully, false otherwise
     */
    public static boolean sendEventSafely(
            TCPConnection connection, 
            Event event, 
            String context) {
        
        try {
            connection.sendEvent(event);
            return true;
        } catch (IOException e) {
            LoggerUtil.error("MessageRoutingHelper", 
                String.format("Failed to send event: %s", context), e);
            return false;
        }
    }
    
    /**
     * Validates a routing hop exists for the given destination.
     * Logs a warning if no route is found.
     * 
     * @param nextHop the next hop identifier (may be null)
     * @param destination the final destination
     * @param operation the operation being performed (for logging)
     * @return true if the next hop is valid, false otherwise
     */
    public static boolean validateRoute(String nextHop, String destination, String operation) {
        if (nextHop == null) {
            LoggerUtil.warn("MessageRoutingHelper", 
                String.format("No route found for %s to destination: %s", operation, destination));
            return false;
        }
        return true;
    }
    
    /**
     * Broadcasts an event to all nodes in the provided map.
     * Sends the same message to all connections and logs any failures.
     * 
     * @param nodes map of node IDs to their TCP connections
     * @param event the event to broadcast
     * @param context additional context for logging
     * @return the number of successful sends
     */
    public static int broadcastToAllNodes(
            Map<String, TCPConnection> nodes, 
            Event event, 
            String context) {
        
        int successCount = 0;
        int failureCount = 0;
        
        for (Map.Entry<String, TCPConnection> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            TCPConnection connection = entry.getValue();
            
            if (connection != null) {
                boolean success = sendEventSafely(connection, event, 
                    String.format("%s to node %s", context, nodeId));
                if (success) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
        }
        
        if (failureCount > 0) {
            LoggerUtil.warn("MessageRoutingHelper", 
                String.format("Broadcast %s: %d succeeded, %d failed", 
                    context, successCount, failureCount));
        } else if (successCount > 0) {
            LoggerUtil.debug("MessageRoutingHelper", 
                String.format("Broadcast %s: all %d nodes received message", 
                    context, successCount));
        }
        
        return successCount;
    }
    
    /**
     * Sends an event through the specified connection with automatic retry.
     * Attempts to send once, and if it fails due to IOException, logs the error.
     * This variant throws IOException for callers that need to handle it specifically.
     * 
     * @param connection the TCP connection to use
     * @param event the event to send
     * @param context additional context for error logging
     * @throws IOException if sending fails
     */
    public static void sendEventOrThrow(
            TCPConnection connection, 
            Event event, 
            String context) throws IOException {
        
        try {
            connection.sendEvent(event);
        } catch (IOException e) {
            LoggerUtil.error("MessageRoutingHelper", 
                String.format("Failed to send event: %s", context), e);
            throw e;
        }
    }
}