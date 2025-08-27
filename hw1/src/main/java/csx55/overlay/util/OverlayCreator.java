package csx55.overlay.util;

import java.util.*;

/**
 * Utility class for creating overlay network topologies.
 * Generates k-regular graphs where each node has exactly k connections,
 * ensuring network connectivity and balanced load distribution.
 */
public class OverlayCreator {

    /**
     * Represents a complete connection plan for the overlay network.
     * Contains both the adjacency information and the weighted links.
     */
    public static class ConnectionPlan {
        /** Map of node IDs to their connected peers */
        private Map<String, Set<String>> nodeConnections;

        /** List of all links in the overlay with weights */
        private List<Link> allLinks;

        /**
         * Constructs an empty ConnectionPlan.
         */
        public ConnectionPlan() {
            this.nodeConnections = new HashMap<>();
            this.allLinks = new ArrayList<>();
        }

        /**
         * Gets the node connections map.
         * 
         * @return map of node IDs to their connected peers
         */
        public Map<String, Set<String>> getNodeConnections() {
            return nodeConnections;
        }

        /**
         * Gets all links in the overlay.
         * 
         * @return list of all weighted links
         */
        public List<Link> getAllLinks() {
            return allLinks;
        }
    }

    /**
     * Represents a weighted bidirectional link between two nodes.
     */
    public static class Link {
        public final String nodeA;
        public final String nodeB;
        public int weight;

        /**
         * Constructs a link with zero weight.
         * 
         * @param nodeA first node identifier
         * @param nodeB second node identifier
         */
        public Link(String nodeA, String nodeB) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.weight = 0;
        }

        /**
         * Constructs a link with specified weight.
         * 
         * @param nodeA  first node identifier
         * @param nodeB  second node identifier
         * @param weight the weight of the link
         */
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
     * Creates an overlay network with the specified connection requirement.
     * Builds a k-regular graph where each node has exactly k connections.
     * Uses a ring-based approach with nearest neighbor connections.
     * 
     * @param nodeIds               list of node identifiers to include in the
     *                              overlay
     * @param connectionRequirement number of connections each node should have
     * @return a ConnectionPlan containing the overlay topology
     * @throws IllegalArgumentException if parameters make overlay creation
     *                                  impossible
     */
    public static ConnectionPlan createOverlay(List<String> nodeIds, int connectionRequirement) {
        if (nodeIds.size() <= connectionRequirement) {
            throw new IllegalArgumentException(
                    "Number of nodes (" + nodeIds.size() + ") must be greater than connection requirement ("
                            + connectionRequirement + ")");
        }

        if (connectionRequirement < 1) {
            throw new IllegalArgumentException("Connection requirement must be at least 1");
        }

        int totalEdgesNeeded = (nodeIds.size() * connectionRequirement) / 2;
        int maxPossibleEdges = (nodeIds.size() * (nodeIds.size() - 1)) / 2;

        if (totalEdgesNeeded > maxPossibleEdges) {
            throw new IllegalArgumentException("Impossible to create overlay with given parameters");
        }

        if ((nodeIds.size() * connectionRequirement) % 2 != 0) {
            LoggerUtil.warn("OverlayCreator",
                    "Configuration (" + nodeIds.size() + " nodes, CR=" + connectionRequirement + ") is not ideal. " +
                            "A perfect regular graph cannot be formed.");
        }

        ConnectionPlan plan = new ConnectionPlan();
        int n = nodeIds.size();

        for (String nodeId : nodeIds) {
            plan.nodeConnections.put(nodeId, new HashSet<>());
        }

        for (int i = 0; i < n; i++) {
            for (int k = 1; k <= connectionRequirement / 2; k++) {
                String nodeA = nodeIds.get(i);
                String nodeB = nodeIds.get((i + k) % n);

                if (!plan.nodeConnections.get(nodeA).contains(nodeB)) {
                    plan.nodeConnections.get(nodeA).add(nodeB);
                    plan.nodeConnections.get(nodeB).add(nodeA);
                    plan.allLinks.add(new Link(nodeA, nodeB));
                }
            }
        }

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

        int weight = 1;
        for (Link link : plan.allLinks) {
            link.weight = weight++;
        }

        return plan;
    }

    /**
     * Determines which nodes should initiate connections to avoid duplicates.
     * For each link, arbitrarily selects one node as the initiator.
     * 
     * @param plan the connection plan to process
     * @return map where keys are initiating nodes and values are their targets
     */
    public static Map<String, List<String>> determineConnectionInitiators(ConnectionPlan plan) {
        Map<String, List<String>> initiators = new HashMap<>();
        Set<String> processedLinks = new HashSet<>();

        for (Link link : plan.allLinks) {
            String linkKey = createLinkKey(link.nodeA, link.nodeB);

            if (!processedLinks.contains(linkKey)) {
                initiators.computeIfAbsent(link.nodeA, k -> new ArrayList<>()).add(link.nodeB);
                processedLinks.add(linkKey);
            }
        }

        return initiators;
    }

    /**
     * Creates a unique key for a link regardless of node order.
     * 
     * @param nodeA first node identifier
     * @param nodeB second node identifier
     * @return a consistent key for the link
     */
    private static String createLinkKey(String nodeA, String nodeB) {
        if (nodeA.compareTo(nodeB) < 0) {
            return nodeA + "-" + nodeB;
        } else {
            return nodeB + "-" + nodeA;
        }
    }
}