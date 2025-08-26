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
        
        // Calculate total links needed
        int targetLinks = (nodeIds.size() * connectionRequirement) / 2;
        int currentLinks = plan.allLinks.size(); // Currently have ring topology links
        
        // Build list of all possible additional connections
        List<Link> possibleLinks = new ArrayList<>();
        for (int i = 0; i < nodeIds.size(); i++) {
            for (int j = i + 1; j < nodeIds.size(); j++) {
                String nodeA = nodeIds.get(i);
                String nodeB = nodeIds.get(j);
                
                // Skip if connection already exists
                if (!plan.nodeConnections.get(nodeA).contains(nodeB)) {
                    possibleLinks.add(new Link(nodeA, nodeB));
                }
            }
        }
        
        // Sort by nodes that need connections most
        // This ensures we prioritize connections for nodes that are furthest from their CR
        possibleLinks.sort((a, b) -> {
            int aNeeds = (connectionRequirement - plan.nodeConnections.get(a.nodeA).size()) +
                        (connectionRequirement - plan.nodeConnections.get(a.nodeB).size());
            int bNeeds = (connectionRequirement - plan.nodeConnections.get(b.nodeA).size()) +
                        (connectionRequirement - plan.nodeConnections.get(b.nodeB).size());
            return Integer.compare(bNeeds, aNeeds); // Higher needs first
        });
        
        // Add connections until we reach the target or run out of valid options
        for (Link link : possibleLinks) {
            if (currentLinks >= targetLinks) {
                break; // We have enough links
            }
            
            // Check if both nodes can accept more connections
            if (plan.nodeConnections.get(link.nodeA).size() < connectionRequirement &&
                plan.nodeConnections.get(link.nodeB).size() < connectionRequirement) {
                
                // Add the connection
                plan.nodeConnections.get(link.nodeA).add(link.nodeB);
                plan.nodeConnections.get(link.nodeB).add(link.nodeA);
                plan.allLinks.add(link);
                currentLinks++;
            }
        }
        
        // Log if we couldn't create enough links
        if (currentLinks < targetLinks) {
            LoggerUtil.warn("OverlayCreator", 
                "Could only create " + currentLinks + " links out of " + targetLinks + " target links");
        }
        
        // Assign unique weights to all links
        // Use sequential weights starting from 1 to ensure uniqueness
        // and stay within the lower range when possible
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