package csx55.overlay.node.messaging;

import csx55.overlay.routing.RoutingTable;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.wireformats.DataMessage;

import java.io.IOException;

/**
 * Service for routing messages through the overlay network
 */
public class MessageRoutingService {
    private RoutingTable routingTable;
    private final TCPConnectionsCache peerConnections;
    private final NodeStatisticsService statisticsService;
    private String nodeId;
    
    public MessageRoutingService(TCPConnectionsCache peerConnections, NodeStatisticsService statisticsService) {
        this.peerConnections = peerConnections;
        this.statisticsService = statisticsService;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
        this.routingTable = new RoutingTable(nodeId);
    }
    
    public void updateRoutingTable(RoutingTable routingTable) {
        this.routingTable = routingTable;
    }
    
    public synchronized void sendMessage(String sinkId, int payload) {
        statisticsService.incrementSendStats(payload);
        
        String nextHop = routingTable.findNextHop(sinkId);
        
        if (nextHop == null) {
            LoggerUtil.warn("MessageRouting", "No route found to destination: " + sinkId);
            return;
        }
        
        TCPConnection connection = peerConnections.getConnection(nextHop);
        if (connection == null) {
            LoggerUtil.warn("MessageRouting", "No connection to next hop: " + nextHop + " for destination: " + sinkId);
            return;
        }
        
        try {
            DataMessage message = new DataMessage(nodeId, sinkId, payload);
            connection.sendEvent(message);
        } catch (IOException e) {
            LoggerUtil.error("MessageRouting", "Failed to send message to " + sinkId + " via " + nextHop, e);
        }
    }
    
    public synchronized void relayMessage(String sourceId, String sinkId, int payload) {
        statisticsService.incrementRelayStats();
        
        String nextHop = routingTable.findNextHop(sinkId);
        
        if (nextHop == null) {
            LoggerUtil.warn("MessageRouting", "No route for relay to destination: " + sinkId);
            return;
        }
        
        TCPConnection connection = peerConnections.getConnection(nextHop);
        if (connection == null) {
            LoggerUtil.warn("MessageRouting", "No connection for relay to next hop: " + nextHop);
            return;
        }
        
        try {
            DataMessage message = new DataMessage(sourceId, sinkId, payload);
            connection.sendEvent(message);
        } catch (IOException e) {
            LoggerUtil.error("MessageRouting", "Failed to relay message from " + sourceId + " to " + sinkId, e);
        }
    }
    
    public void handleDataMessage(DataMessage message) {
        String sourceId = message.getSourceNodeId();
        String sinkId = message.getSinkNodeId();
        int payload = message.getPayload();
        
        if (sinkId.equals(nodeId)) {
            // Message is for us
            statisticsService.incrementReceiveStats(payload);
        } else {
            // Relay the message
            relayMessage(sourceId, sinkId, payload);
        }
    }
    
    public RoutingTable getRoutingTable() {
        return routingTable;
    }
}