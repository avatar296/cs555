package csx55.overlay.transport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for managing multiple TCP connections.
 * Provides centralized storage and management of connections to various nodes
 * in the overlay network.
 * 
 * This class uses a ConcurrentHashMap for thread-safe operations and provides
 * utilities for connection lifecycle management including cleanup of dead connections.
 */
public class TCPConnectionsCache {
    
    /** Thread-safe map of node IDs to their TCP connections */
    private final Map<String, TCPConnection> connections;
    
    /**
     * Constructs a new TCPConnectionsCache.
     * Initializes with an empty concurrent hash map.
     */
    public TCPConnectionsCache() {
        this.connections = new ConcurrentHashMap<>();
    }
    
    /**
     * Adds a connection to the cache.
     * 
     * @param nodeId the identifier of the remote node
     * @param connection the TCP connection to cache
     */
    public void addConnection(String nodeId, TCPConnection connection) {
        connections.put(nodeId, connection);
    }
    
    /**
     * Retrieves a connection from the cache.
     * 
     * @param nodeId the identifier of the remote node
     * @return the TCP connection, or null if not found
     */
    public TCPConnection getConnection(String nodeId) {
        return connections.get(nodeId);
    }
    
    /**
     * Removes and closes a connection from the cache.
     * 
     * @param nodeId the identifier of the remote node
     */
    public void removeConnection(String nodeId) {
        TCPConnection connection = connections.remove(nodeId);
        if (connection != null) {
            connection.close();
        }
    }
    
    /**
     * Checks if an active connection exists for the specified node.
     * 
     * @param nodeId the identifier of the remote node
     * @return true if an active connection exists, false otherwise
     */
    public boolean hasConnection(String nodeId) {
        TCPConnection connection = connections.get(nodeId);
        return connection != null && connection.isConnected();
    }
    
    /**
     * Gets a copy of all connections in the cache.
     * 
     * @return a new map containing all cached connections
     */
    public Map<String, TCPConnection> getAllConnections() {
        return new ConcurrentHashMap<>(connections);
    }
    
    /**
     * Gets the number of connections in the cache.
     * 
     * @return the number of cached connections
     */
    public int size() {
        return connections.size();
    }
    
    /**
     * Closes all connections and clears the cache.
     * This method is synchronized to ensure proper cleanup.
     */
    public synchronized void closeAll() {
        for (TCPConnection connection : connections.values()) {
            connection.close();
        }
        connections.clear();
    }
    
    /**
     * Removes all inactive connections from the cache.
     * Useful for periodic maintenance to prevent memory leaks.
     */
    public void cleanupDeadConnections() {
        connections.entrySet().removeIf(entry -> !entry.getValue().isConnected());
    }
}