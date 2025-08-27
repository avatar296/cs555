package csx55.overlay.node.messaging;

import csx55.overlay.routing.RoutingTable;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.wireformats.DataMessage;

import java.util.Optional;

/**
 * Service responsible for routing messages through the overlay network.
 * Handles both direct message sending and message relay operations while
 * maintaining statistics for all routing activities.
 * 
 * This service uses a routing table to determine the next hop for messages
 * and leverages TCP connections for reliable message delivery.
 */
public class MessageRoutingService {
    private final TCPConnectionsCache peerConnections;
    private final NodeStatisticsService statisticsService;
    private RoutingTable routingTable;
    private String nodeId;
    
    /**
     * Constructs a new MessageRoutingService.
     * 
     * @param peerConnections the cache of TCP connections to peer nodes
     * @param statisticsService the service for tracking message statistics
     */
    public MessageRoutingService(TCPConnectionsCache peerConnections, NodeStatisticsService statisticsService) {
        this.peerConnections = peerConnections;
        this.statisticsService = statisticsService;
    }
    
    /**
     * Sets the node ID and initializes the routing table.
     * 
     * @param nodeId the unique identifier for this node
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
        this.routingTable = new RoutingTable(nodeId);
    }
    
    /**
     * Updates the routing table with new routing information.
     * 
     * @param routingTable the new routing table to use
     */
    public void updateRoutingTable(RoutingTable routingTable) {
        this.routingTable = routingTable;
    }
    
    /**
     * Sends a message to the specified destination node.
     * This method increments send statistics and routes the message
     * through the appropriate next hop.
     * 
     * @param sinkId the destination node identifier
     * @param payload the message payload to send
     */
    public synchronized void sendMessage(String sinkId, int payload) {
        statisticsService.incrementSendStats(payload);
        routeMessageInternal(nodeId, sinkId, payload, "send");
    }
    
    /**
     * Relays a message from a source node to a destination node.
     * This method is used when this node acts as an intermediate hop
     * in the message routing path.
     * 
     * @param sourceId the original source node identifier
     * @param sinkId the final destination node identifier
     * @param payload the message payload to relay
     */
    public synchronized void relayMessage(String sourceId, String sinkId, int payload) {
        statisticsService.incrementRelayStats();
        routeMessageInternal(sourceId, sinkId, payload, "relay");
    }
    
    /**
     * Internal method to handle common message routing logic.
     * Validates the route, retrieves the connection, and sends the message.
     * 
     * @param sourceId the source node identifier
     * @param sinkId the destination node identifier
     * @param payload the message payload
     * @param operation the operation type ("send" or "relay") for logging
     */
    private void routeMessageInternal(String sourceId, String sinkId, int payload, String operation) {
        String nextHop = routingTable.findNextHop(sinkId);
        
        if (!MessageRoutingHelper.validateRoute(nextHop, sinkId, operation)) {
            return;
        }
        
        Optional<TCPConnection> connectionOpt = MessageRoutingHelper.getValidatedConnection(
            peerConnections, nextHop, 
            String.format("%s to destination: %s", operation, sinkId));
        
        if (!connectionOpt.isPresent()) {
            return;
        }
        
        DataMessage message = new DataMessage(sourceId, sinkId, payload);
        String errorContext = operation.equals("send") 
            ? String.format("sending message to %s via %s", sinkId, nextHop)
            : String.format("relaying message from %s to %s via %s", sourceId, sinkId, nextHop);
        
        MessageRoutingHelper.sendEventSafely(connectionOpt.get(), message, errorContext);
    }
    
    /**
     * Handles incoming data messages.
     * If the message is destined for this node, it updates receive statistics.
     * Otherwise, it relays the message to the next hop.
     * 
     * @param message the data message to handle
     */
    public void handleDataMessage(DataMessage message) {
        String sourceId = message.getSourceNodeId();
        String sinkId = message.getSinkNodeId();
        int payload = message.getPayload();
        
        if (sinkId.equals(nodeId)) {
            statisticsService.incrementReceiveStats(payload);
        } else {
            relayMessage(sourceId, sinkId, payload);
        }
    }
    
    /**
     * Gets the current routing table.
     * 
     * @return the routing table used by this service
     */
    public RoutingTable getRoutingTable() {
        return routingTable;
    }
}