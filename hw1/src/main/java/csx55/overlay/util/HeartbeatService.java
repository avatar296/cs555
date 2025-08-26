package csx55.overlay.util;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for monitoring connection health through heartbeat mechanism
 */
public class HeartbeatService {
    
    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds
    private static final long DEFAULT_TIMEOUT_MS = 90000; // 90 seconds (3 missed heartbeats)
    private static final long INITIAL_DELAY_MS = 10000; // 10 seconds before first check
    
    private final TCPConnectionsCache connectionsCache;
    private final Map<String, Long> lastActivityMap;
    private final ScheduledExecutorService scheduler;
    private final long heartbeatInterval;
    private final long timeoutThreshold;
    private volatile boolean running = false;
    
    public HeartbeatService(TCPConnectionsCache connectionsCache) {
        this(connectionsCache, DEFAULT_HEARTBEAT_INTERVAL_MS, DEFAULT_TIMEOUT_MS);
    }
    
    public HeartbeatService(TCPConnectionsCache connectionsCache, 
                           long heartbeatInterval, long timeoutThreshold) {
        this.connectionsCache = connectionsCache;
        this.lastActivityMap = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.heartbeatInterval = heartbeatInterval;
        this.timeoutThreshold = timeoutThreshold;
    }
    
    /**
     * Start the heartbeat monitoring service
     */
    public void start() {
        if (running) {
            LoggerUtil.warn("HeartbeatService", "Service is already running");
            return;
        }
        
        running = true;
        LoggerUtil.info("HeartbeatService", "Starting heartbeat monitoring service");
        
        // Schedule periodic health checks
        scheduler.scheduleAtFixedRate(
            this::performHealthCheck, 
            INITIAL_DELAY_MS, 
            heartbeatInterval, 
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Stop the heartbeat monitoring service
     */
    public void stop() {
        running = false;
        LoggerUtil.info("HeartbeatService", "Stopping heartbeat monitoring service");
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                LoggerUtil.warn("HeartbeatService", "Forced shutdown of scheduler");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Record activity for a connection
     */
    public void recordActivity(String nodeId) {
        lastActivityMap.put(nodeId, System.currentTimeMillis());
        LoggerUtil.debug("HeartbeatService", "Recorded activity for " + nodeId);
    }
    
    /**
     * Perform health check on all connections
     */
    private void performHealthCheck() {
        if (!running) {
            return;
        }
        
        LoggerUtil.debug("HeartbeatService", "Performing health check on connections");
        long currentTime = System.currentTimeMillis();
        Map<String, TCPConnection> connections = connectionsCache.getAllConnections();
        
        for (Map.Entry<String, TCPConnection> entry : connections.entrySet()) {
            String nodeId = entry.getKey();
            TCPConnection connection = entry.getValue();
            
            // Check if connection is still alive
            if (!connection.isConnected()) {
                LoggerUtil.warn("HeartbeatService", "Dead connection detected: " + nodeId);
                handleDeadConnection(nodeId);
                continue;
            }
            
            // Check for timeout based on last activity
            Long lastActivity = lastActivityMap.get(nodeId);
            if (lastActivity == null) {
                // Initialize activity time for new connections
                recordActivity(nodeId);
            } else {
                long timeSinceActivity = currentTime - lastActivity;
                
                if (timeSinceActivity > timeoutThreshold) {
                    LoggerUtil.warn("HeartbeatService", 
                        String.format("Connection timeout detected: %s (no activity for %dms)", 
                        nodeId, timeSinceActivity));
                    handleTimeoutConnection(nodeId);
                } else if (timeSinceActivity > heartbeatInterval) {
                    // Send heartbeat ping if needed (would require adding PING message type)
                    LoggerUtil.debug("HeartbeatService", 
                        String.format("Connection %s inactive for %dms", nodeId, timeSinceActivity));
                }
            }
        }
        
        // Clean up stale entries from activity map
        lastActivityMap.entrySet().removeIf(entry -> 
            !connections.containsKey(entry.getKey()));
    }
    
    /**
     * Handle a dead connection
     */
    private void handleDeadConnection(String nodeId) {
        LoggerUtil.info("HeartbeatService", "Removing dead connection: " + nodeId);
        connectionsCache.removeConnection(nodeId);
        lastActivityMap.remove(nodeId);
        
        // Could trigger reconnection logic here if needed
        notifyConnectionLoss(nodeId);
    }
    
    /**
     * Handle a timed-out connection
     */
    private void handleTimeoutConnection(String nodeId) {
        LoggerUtil.info("HeartbeatService", "Handling timeout for connection: " + nodeId);
        
        // First try to close gracefully
        TCPConnection connection = connectionsCache.getConnection(nodeId);
        if (connection != null) {
            connection.close();
        }
        
        // Remove from cache
        connectionsCache.removeConnection(nodeId);
        lastActivityMap.remove(nodeId);
        
        // Notify about connection loss
        notifyConnectionLoss(nodeId);
    }
    
    /**
     * Notify interested parties about connection loss
     * This could be extended to support listeners/callbacks
     */
    private void notifyConnectionLoss(String nodeId) {
        LoggerUtil.error("HeartbeatService", "Connection lost: " + nodeId);
        // Future: Add callback mechanism for connection loss events
    }
    
    /**
     * Get connection health status
     */
    public ConnectionHealth getConnectionHealth(String nodeId) {
        TCPConnection connection = connectionsCache.getConnection(nodeId);
        if (connection == null || !connection.isConnected()) {
            return ConnectionHealth.DISCONNECTED;
        }
        
        Long lastActivity = lastActivityMap.get(nodeId);
        if (lastActivity == null) {
            return ConnectionHealth.UNKNOWN;
        }
        
        long timeSinceActivity = System.currentTimeMillis() - lastActivity;
        if (timeSinceActivity > timeoutThreshold) {
            return ConnectionHealth.TIMEOUT;
        } else if (timeSinceActivity > heartbeatInterval) {
            return ConnectionHealth.INACTIVE;
        } else {
            return ConnectionHealth.HEALTHY;
        }
    }
    
    /**
     * Connection health status enum
     */
    public enum ConnectionHealth {
        HEALTHY,      // Recent activity detected
        INACTIVE,     // No recent activity but not timed out
        TIMEOUT,      // Connection has timed out
        DISCONNECTED, // Connection is closed
        UNKNOWN       // No activity data available
    }
}