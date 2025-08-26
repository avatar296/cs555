package csx55.overlay.node.messaging;

import csx55.overlay.routing.RoutingTable;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
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
        System.out.println(response.getAdditionalInfo());
    }
    
    public boolean handleDeregisterResponse(DeregisterResponse response) {
        if (response.getStatusCode() == 1) {
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
            peerConnections.addConnection(peer, connection);
        }
        System.out.println("All connections are established. Number of connections: " + peerConnections.size());
    }
    
    public void handleLinkWeights(LinkWeights linkWeights) {
        allNodes.clear();
        for (LinkWeights.LinkInfo link : linkWeights.getLinks()) {
            String node1 = link.getNode1();
            String node2 = link.getNode2();
            if (!allNodes.contains(node1)) allNodes.add(node1);
            if (!allNodes.contains(node2)) allNodes.add(node2);
        }
        
        RoutingTable routingTable = new RoutingTable(nodeId);
        // Build routing table from link weights
        for (LinkWeights.LinkInfo link : linkWeights.getLinks()) {
            routingTable.addLink(link.getNode1(), link.getNode2(), link.getWeight());
        }
        routingTable.computeShortestPaths();
        routingService.updateRoutingTable(routingTable);
        
        System.out.println("Link weights received and processed. Ready to send messages.");
    }
    
    public List<String> getAllNodes() {
        return allNodes;
    }
}