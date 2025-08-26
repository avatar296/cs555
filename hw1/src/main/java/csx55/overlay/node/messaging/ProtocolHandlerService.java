package csx55.overlay.node.messaging;

import csx55.overlay.routing.RoutingTable;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.wireformats.*;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * Handles protocol messages for MessagingNode
 */
public class ProtocolHandlerService {
    private final TCPConnectionsCache peerConnections;
    private final MessageRoutingService routingService;
    private List<String> allNodes;
    private String nodeId;
    
    public ProtocolHandlerService(TCPConnectionsCache peerConnections, 
                                 MessageRoutingService routingService) {
        this.peerConnections = peerConnections;
        this.routingService = routingService;
    }
    
    public void setNodeInfo(String nodeId, List<String> allNodes) {
        this.nodeId = nodeId;
        this.allNodes = allNodes;
    }
    
    public void handleRegisterResponse(RegisterResponse response) {
        LoggerUtil.info("ProtocolHandler", "Registration response: " + response.getAdditionalInfo());
    }
    
    public boolean handleDeregisterResponse(DeregisterResponse response) {
        if (response.getStatusCode() == 1) {
            LoggerUtil.info("ProtocolHandler", "Successfully exited overlay");
            System.out.println("exited overlay");
            return true; // Signal to exit
        }
        return false;
    }
    
    public void handlePeerList(MessagingNodesList peerList, TCPConnection.TCPConnectionListener listener) throws IOException {
        for (String peer : peerList.getPeerNodes()) {
            String[] parts = peer.split(":");
            Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
            TCPConnection connection = new TCPConnection(socket, listener);
            // Set the proper remote node ID
            connection.setRemoteNodeId(peer);
            peerConnections.addConnection(peer, connection);
            
            // Send peer identification to the connected node
            PeerIdentification identification = new PeerIdentification(nodeId);
            connection.sendEvent(identification);
        }
        LoggerUtil.info("ProtocolHandler", "All connections established. Number of connections: " + peerConnections.size());
        System.out.println("All connections are established. Number of connections: " + peerConnections.size());
    }
    
    public void handleLinkWeights(LinkWeights linkWeights) {
        allNodes.clear();
        for (LinkWeights.LinkInfo link : linkWeights.getLinks()) {
            String node1 = link.nodeA;
            String node2 = link.nodeB;
            if (!allNodes.contains(node1)) allNodes.add(node1);
            if (!allNodes.contains(node2)) allNodes.add(node2);
        }
        
        RoutingTable routingTable = new RoutingTable(nodeId);
        routingTable.updateLinkWeights(linkWeights);
        routingService.updateRoutingTable(routingTable);
        
        LoggerUtil.info("ProtocolHandler", "Link weights received and processed. Ready to send messages.");
        System.out.println("Link weights received and processed. Ready to send messages.");
    }
    
    public synchronized void handlePeerIdentification(PeerIdentification identification, TCPConnection connection) {
        String peerId = identification.getNodeId();
        
        // Update the connection's remote node ID
        connection.setRemoteNodeId(peerId);
        
        // Check if this connection is already in the cache
        boolean found = false;
        for (String key : peerConnections.getAllConnections().keySet()) {
            if (peerConnections.getConnection(key) == connection) {
                found = true;
                if (!key.equals(peerId)) {
                    // Remove the old entry and add with correct ID
                    peerConnections.removeConnection(key);
                    peerConnections.addConnection(peerId, connection);
                }
                break;
            }
        }
        
        // If not found, this is a new incoming connection - add it to the cache
        if (!found) {
            peerConnections.addConnection(peerId, connection);
        }
    }
    
    public List<String> getAllNodes() {
        return allNodes;
    }
}