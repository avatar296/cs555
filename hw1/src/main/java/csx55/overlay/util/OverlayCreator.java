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
        
        ConnectionPlan plan = new ConnectionPlan();
        
        // Initialize connection sets for each node
        for (String nodeId : nodeIds) {
            plan.nodeConnections.put(nodeId, new HashSet<>());
        }
        
        // Create a ring topology first to ensure connectivity
        for (int e = 0; e < nodeIds.size(); e++) {
            String currentNode = nodeIds.get(e);
            String nextNode = nodeIds.get((e + 1) % nodeIds.size());
            
            plan.nodeConnections.get(currentNode).add(nextNode);
            plan.nodeConnections.get(nextNode).add(currentNode);
            plan.allLinks.add(new Link(currentNode, nextNode));
        }
        
        // Add additional connections to reach the connection requirement
        Random random = new Random();
        
        for (String nodeId : nodeIds) {
            while (plan.nodeConnections.get(nodeId).size() < connectionRequirement) {
                // Find a node to connect to
                List<String> candidates = new ArrayList<>();
                
                for (String candidate : nodeIds) {
                    if (!candidate.equals(nodeId) && 
                        !plan.nodeConnections.get(nodeId).contains(candidate) &&
                        plan.nodeConnections.get(candidate).size() < connectionRequirement) {
                        candidates.add(candidate);
                    }
                }
                
                if (candidates.isEmpty()) {
                    // This can happen if we can't satisfy the connection requirement
                    // Log this for debugging
                    LoggerUtil.warn("OverlayCreator", 
                        "Cannot find more candidates for node " + nodeId + 
                        " (current connections: " + plan.nodeConnections.get(nodeId).size() + ")");
                    break;
                }
                
                // Select a random candidate
                String selectedNode = candidates.get(random.nextInt(candidates.size()));
                
                // Add the connection
                plan.nodeConnections.get(nodeId).add(selectedNode);
                plan.nodeConnections.get(selectedNode).add(nodeId);
                plan.allLinks.add(new Link(nodeId, selectedNode));
            }
        }
        
        // Assign unique weights to all links
        Set<Integer> usedWeights = new HashSet<>();
        for (Link link : plan.allLinks) {
            int weight;
            do {
                // Use a wider range to ensure uniqueness
                weight = random.nextInt(1000) + 1;
            } while (usedWeights.contains(weight));
            usedWeights.add(weight);
            link.weight = weight;
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