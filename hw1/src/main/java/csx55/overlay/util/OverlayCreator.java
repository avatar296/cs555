package csx55.overlay.util;

import csx55.overlay.util.LoggerUtil;
import java.util.*;

public class OverlayCreator {
    
    public static class ConnectionPlan {
        private Map<String, Set<String>> nodeConnections;
        private List<Link> allLinks;
        
        public ConnectionPlan() {
            this.nodeConnections = new HashMap<>();
            this.allLinks = new ArrayList<>();
        }
        
        public Map<String, Set<String>> getNodeConnections() {
            return nodeConnections;
        }
        
        public List<Link> getAllLinks() {
            return allLinks;
        }
    }
    
    public static class Link {
        public final String nodeA;
        public final String nodeB;
        public int weight;
        
        public Link(String nodeA, String nodeB) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.weight = 0;
        }
        
        public Link(String nodeA, String nodeB, int weight) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.weight = weight;
        }
        
        @Override
        public String toString() {
            return nodeA + ", " + nodeB + ", " + weight;
        }
    }
    
    /**
     * Creates an overlay with the specified connection requirement (CR).
     * Ensures that each node has exactly CR connections and the overlay is connected.
     */
    public static ConnectionPlan createOverlay(List<String> nodeIds, int connectionRequirement) {
        if (nodeIds.size() <= connectionRequirement) {
            throw new IllegalArgumentException(
                "Number of nodes (" + nodeIds.size() + ") must be greater than connection requirement (" + connectionRequirement + ")"
            );
        }
        
        if (connectionRequirement < 1) {
            throw new IllegalArgumentException("Connection requirement must be at least 1");
        }
        
        // Check if overlay is possible (each node needs CR connections, total edges = n*CR/2)
        int totalEdgesNeeded = (nodeIds.size() * connectionRequirement) / 2;
        int maxPossibleEdges = (nodeIds.size() * (nodeIds.size() - 1)) / 2;
        
        if (totalEdgesNeeded > maxPossibleEdges) {
            throw new IllegalArgumentException("Impossible to create overlay with given parameters");
        }
        
        // The product of nodes and CR must be even for a regular graph to be possible.
        if ((nodeIds.size() * connectionRequirement) % 2 != 0) {
            LoggerUtil.warn("OverlayCreator", 
                "Configuration (" + nodeIds.size() + " nodes, CR=" + connectionRequirement + ") is not ideal. " +
                "A perfect regular graph cannot be formed.");
        }
        
        ConnectionPlan plan = new ConnectionPlan();
        int n = nodeIds.size();
        
        // Initialize connection sets for each node
        for (String nodeId : nodeIds) {
            plan.nodeConnections.put(nodeId, new HashSet<>());
        }
        
        // Build a k-regular graph by connecting to nearest neighbors.
        // Loop through each node as a starting point.
        for (int i = 0; i < n; i++) {
            // Connect to the k/2 nearest neighbors on each side.
            for (int k = 1; k <= connectionRequirement / 2; k++) {
                String nodeA = nodeIds.get(i);
                String nodeB = nodeIds.get((i + k) % n);
                
                // Add the link if it doesn't already exist.
                if (!plan.nodeConnections.get(nodeA).contains(nodeB)) {
                    plan.nodeConnections.get(nodeA).add(nodeB);
                    plan.nodeConnections.get(nodeB).add(nodeA);
                    plan.allLinks.add(new Link(nodeA, nodeB));
                }
            }
        }
        
        // For an odd connection requirement on an even number of nodes,
        // connect each node to the one directly across the ring.
        if (connectionRequirement % 2 != 0) {
            if (n % 2 == 0) {
                for (int i = 0; i < n / 2; i++) {
                    String nodeA = nodeIds.get(i);
                    String nodeB = nodeIds.get(i + n / 2);
                    
                    if (!plan.nodeConnections.get(nodeA).contains(nodeB)) {
                        plan.nodeConnections.get(nodeA).add(nodeB);
                        plan.nodeConnections.get(nodeB).add(nodeA);
                        plan.allLinks.add(new Link(nodeA, nodeB));
                    }
                }
            }
        }
        
        // Assign unique weights to all created links.
        int weight = 1;
        for (Link link : plan.allLinks) {
            link.weight = weight++;
        }
        
        return plan;
    }
    
    /**
     * Determines which nodes should initiate connections to avoid duplicates.
     * Returns a map where key is the node that should initiate, value is list of target nodes.
     */
    public static Map<String, List<String>> determineConnectionInitiators(ConnectionPlan plan) {
        Map<String, List<String>> initiators = new HashMap<>();
        Set<String> processedLinks = new HashSet<>();
        
        for (Link link : plan.allLinks) {
            String linkKey = createLinkKey(link.nodeA, link.nodeB);
            
            if (!processedLinks.contains(linkKey)) {
                // Arbitrarily choose nodeA as initiator
                initiators.computeIfAbsent(link.nodeA, k -> new ArrayList<>()).add(link.nodeB);
                processedLinks.add(linkKey);
            }
        }
        
        return initiators;
    }
    
    private static String createLinkKey(String nodeA, String nodeB) {
        if (nodeA.compareTo(nodeB) < 0) {
            return nodeA + "-" + nodeB;
        } else {
            return nodeB + "-" + nodeA;
        }
    }
}