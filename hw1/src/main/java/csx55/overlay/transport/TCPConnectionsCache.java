package csx55.overlay.transport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TCPConnectionsCache {
    
    private Map<String, TCPConnection> connections;
    
    public TCPConnectionsCache() {
        this.connections = new ConcurrentHashMap<>();
    }
    
    public void addConnection(String nodeId, TCPConnection connection) {
        connections.put(nodeId, connection);
    }
    
    public TCPConnection getConnection(String nodeId) {
        return connections.get(nodeId);
    }
    
    public void removeConnection(String nodeId) {
        TCPConnection connection = connections.remove(nodeId);
        if (connection != null) {
            connection.close();
        }
    }
    
    public boolean hasConnection(String nodeId) {
        TCPConnection connection = connections.get(nodeId);
        return connection != null && connection.isConnected();
    }
    
    public Map<String, TCPConnection> getAllConnections() {
        return new ConcurrentHashMap<>(connections);
    }
    
    public int size() {
        return connections.size();
    }
    
    public synchronized void closeAll() {
        for (TCPConnection connection : connections.values()) {
            connection.close();
        }
        connections.clear();
    }
    
    public void cleanupDeadConnections() {
        connections.entrySet().removeIf(entry -> !entry.getValue().isConnected());
    }
}