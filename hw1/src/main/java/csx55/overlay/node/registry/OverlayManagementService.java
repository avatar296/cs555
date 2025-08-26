package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.OverlayCreator;
import csx55.overlay.wireformats.LinkWeights;
import csx55.overlay.wireformats.MessagingNodesList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        if (cr <= 0) {
            LoggerUtil.error("OverlayManagement", "Connection requirement must be greater than 0, received: " + cr);
            return;
        }
        
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        
        synchronized (registeredNodes) {
            if (registeredNodes.size() <= cr) {
                LoggerUtil.error("OverlayManagement", "Not enough nodes for CR=" + cr + ". Need more than " + cr + " nodes, have " + registeredNodes.size());
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
                
                LoggerUtil.info("OverlayManagement", "Overlay setup completed with CR=" + connectionRequirement + " connections per node");
                System.out.println("setup completed with " + connectionRequirement + " connections");
                
            } catch (IOException e) {
                LoggerUtil.error("OverlayManagement", "Failed to setup overlay with CR=" + cr, e);
            }
        }
    }
    
    public void sendOverlayLinkWeights() {
        if (currentOverlay == null || currentOverlay.getAllLinks().isEmpty()) {
            LoggerUtil.error("OverlayManagement", "Overlay has not been set up. Cannot send weights.");
            return;
        }
        
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        
        // Use existing weights from links (they were set during overlay creation)
        List<LinkWeights.LinkInfo> linkInfos = new ArrayList<>();
        
        for (OverlayCreator.Link link : currentOverlay.getAllLinks()) {
            // Use the weight that was already assigned during overlay creation
            LinkWeights.LinkInfo linkInfo = new LinkWeights.LinkInfo(
                link.nodeA, link.nodeB, link.weight
            );
            linkInfos.add(linkInfo);
        }
        
        // Send to all registered nodes
        LinkWeights message = new LinkWeights(linkInfos);
        try {
            for (TCPConnection connection : registeredNodes.values()) {
                connection.sendEvent(message);
            }
            LoggerUtil.info("OverlayManagement", "Link weights successfully assigned to all nodes");
            System.out.println("link weights assigned");
        } catch (IOException e) {
            LoggerUtil.error("OverlayManagement", "Failed to send link weights to nodes", e);
        }
    }
    
    public void listWeights() {
        if (currentOverlay == null || currentOverlay.getAllLinks().isEmpty()) {
            LoggerUtil.warn("OverlayManagement", "No overlay has been configured to list weights");
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