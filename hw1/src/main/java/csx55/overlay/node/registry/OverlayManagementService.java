package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.OverlayCreator;
import csx55.overlay.wireformats.LinkWeights;
import csx55.overlay.wireformats.MessagingNodesList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Service for managing overlay topology and link weights
 */
public class OverlayManagementService {
    private OverlayCreator.ConnectionPlan currentOverlay = null;
    private int connectionRequirement = 0;
    private final NodeRegistrationService registrationService;
    
    public OverlayManagementService(NodeRegistrationService registrationService) {
        this.registrationService = registrationService;
    }
    
    public void setupOverlay(int cr) {
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        
        synchronized (registeredNodes) {
            if (registeredNodes.size() <= cr) {
                System.out.println("Error: Not enough nodes. Need more than " + cr + " nodes, have " + registeredNodes.size());
                return;
            }
            
            connectionRequirement = cr;
            
            try {
                List<String> nodeIds = new ArrayList<>(registeredNodes.keySet());
                currentOverlay = OverlayCreator.createOverlay(nodeIds, cr);
                
                // Determine who initiates connections
                Map<String, List<String>> initiators = OverlayCreator.determineConnectionInitiators(currentOverlay);
                
                // Send peer lists to each node
                for (String nodeId : nodeIds) {
                    List<String> peers = initiators.getOrDefault(nodeId, new ArrayList<>());
                    TCPConnection connection = registeredNodes.get(nodeId);
                    if (connection != null) {
                        MessagingNodesList message = new MessagingNodesList(peers);
                        connection.sendEvent(message);
                    }
                }
                
                System.out.println("setup completed with " + connectionRequirement + " connections");
                
            } catch (IOException e) {
                System.err.println("Failed to setup overlay: " + e.getMessage());
            }
        }
    }
    
    public void sendOverlayLinkWeights() {
        if (currentOverlay == null || currentOverlay.getAllLinks().isEmpty()) {
            System.out.println("Overlay has not been set up. Cannot send weights.");
            return;
        }
        
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        
        // Assign random weights to links
        Random rand = new Random();
        List<LinkWeights.LinkInfo> linkInfos = new ArrayList<>();
        
        for (OverlayCreator.Link link : currentOverlay.getAllLinks()) {
            int weight = rand.nextInt(10) + 1;
            LinkWeights.LinkInfo linkInfo = new LinkWeights.LinkInfo(
                link.nodeA, link.nodeB, weight
            );
            linkInfos.add(linkInfo);
        }
        
        // Send to all registered nodes
        LinkWeights message = new LinkWeights(linkInfos);
        try {
            for (TCPConnection connection : registeredNodes.values()) {
                connection.sendEvent(message);
            }
            System.out.println("link weights assigned");
        } catch (IOException e) {
            System.err.println("Failed to send link weights: " + e.getMessage());
        }
    }
    
    public void listWeights() {
        if (currentOverlay == null || currentOverlay.getAllLinks().isEmpty()) {
            System.out.println("No overlay has been configured.");
            return;
        }
        
        for (OverlayCreator.Link link : currentOverlay.getAllLinks()) {
            System.out.println(link.toString());
        }
    }
    
    public OverlayCreator.ConnectionPlan getCurrentOverlay() {
        return currentOverlay;
    }
    
    public int getConnectionRequirement() {
        return connectionRequirement;
    }
}