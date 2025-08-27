package csx55.overlay.routing;

import csx55.overlay.node.Edge;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.wireformats.LinkWeights;

import java.util.*;

/**
 * Manages routing information for a node in the overlay network.
 * Uses Prim's algorithm to calculate a Minimum Spanning Tree (MST) from link weights,
 * enabling efficient shortest-path routing between nodes.
 * 
 * This class is thread-safe, with all public methods synchronized to ensure
 * consistent state when accessed from multiple threads.
 */
public class RoutingTable {
    
    /** The identifier of the local node that owns this routing table */
    private final String localNodeId;
    
    /** Graph representation as adjacency list for all nodes and their connections */
    private final Map<String, List<Edge>> graph;
    
    /** Minimum Spanning Tree mapping each node to its parent edge */
    private final Map<String, Edge> mst;
    
    /** Cache for next hop lookups to improve performance */
    private final Map<String, String> nextHopCache;
    
    /**
     * Constructs a new RoutingTable for the specified local node.
     * 
     * @param localNodeId the identifier of the local node
     */
    public RoutingTable(String localNodeId) {
        this.localNodeId = localNodeId;
        this.graph = new HashMap<>();
        this.mst = new HashMap<>();
        this.nextHopCache = new HashMap<>();
    }
    
    /**
     * Updates the routing table with new link weight information.
     * Rebuilds the graph representation and recalculates the MST.
     * 
     * @param linkWeights the new link weight information for the overlay
     */
    public synchronized void updateLinkWeights(LinkWeights linkWeights) {
        clearAllData();
        for (LinkWeights.LinkInfo link : linkWeights.getLinks()) {
            addBidirectionalEdge(link.nodeA, link.nodeB, link.weight);
        }
        
        calculateMST();
    }
    
    /**
     * Clears all internal data structures.
     */
    private void clearAllData() {
        graph.clear();
        mst.clear();
        nextHopCache.clear();
    }
    
    /**
     * Adds a bidirectional edge between two nodes in the graph.
     * 
     * @param nodeA the first node
     * @param nodeB the second node
     * @param weight the weight of the edge
     */
    private void addBidirectionalEdge(String nodeA, String nodeB, int weight) {
        graph.computeIfAbsent(nodeA, k -> new ArrayList<>())
            .add(new Edge(nodeB, weight));
        graph.computeIfAbsent(nodeB, k -> new ArrayList<>())
            .add(new Edge(nodeA, weight));
    }
    
    /**
     * Calculates the Minimum Spanning Tree using Prim's algorithm.
     * Uses the local node as the root of the tree.
     * The resulting MST provides optimal paths from the local node to all other nodes.
     */
    private void calculateMST() {
        if (!graph.containsKey(localNodeId)) {
            LoggerUtil.warn("RoutingTable", "Local node " + localNodeId + " not found in graph - cannot calculate MST");
            return;
        }
        
        // MST already cleared in clearAllData() if called from updateLinkWeights
        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> minWeight = new HashMap<>();
        PriorityQueue<Edge> pq = new PriorityQueue<>(Comparator.comparingInt(Edge::getWeight));
        Set<String> inTree = new HashSet<>();
        
        for (String node : graph.keySet()) {
            minWeight.put(node, Integer.MAX_VALUE);
        }
        
        minWeight.put(localNodeId, 0);
        pq.add(new Edge(localNodeId, 0));
        while (!pq.isEmpty()) {
            Edge current = pq.poll();
            String currentNode = current.getDestination();
            
            if (inTree.contains(currentNode)) {
                continue;
            }
            
            inTree.add(currentNode);
            
            if (!currentNode.equals(localNodeId) && parent.containsKey(currentNode)) {
                String parentNode = parent.get(currentNode);
                int weight = minWeight.get(currentNode);
                mst.put(currentNode, new Edge(parentNode, weight));
            }
            
            List<Edge> neighbors = graph.get(currentNode);
            if (neighbors != null) {
                for (Edge neighborEdge : neighbors) {
                    String neighbor = neighborEdge.getDestination();
                    int weight = neighborEdge.getWeight();
                    
                    if (!inTree.contains(neighbor) && weight < minWeight.get(neighbor)) {
                        minWeight.put(neighbor, weight);
                        parent.put(neighbor, currentNode);
                        pq.add(new Edge(neighbor, weight));
                    }
                }
            }
        }
    }
    
    /**
     * Finds the next hop to reach the specified destination node.
     * Uses the MST to determine the shortest path and returns the immediate next node.
     * Results are cached for improved performance on repeated lookups.
     * 
     * @param destination the target node to reach
     * @return the next hop node identifier, or null if no path exists
     */
    public synchronized String findNextHop(String destination) {
        if (destination.equals(localNodeId)) {
            return localNodeId;
        }
        
        String cachedNextHop = nextHopCache.get(destination);
        if (cachedNextHop != null) {
            return cachedNextHop;
        }
        List<String> path = new ArrayList<>();
        String current = destination;
        
        while (current != null && !current.equals(localNodeId)) {
            path.add(current);
            Edge parentEdge = mst.get(current);
            if (parentEdge == null) {
                return null;
            }
            current = parentEdge.getDestination();
        }
        
        if (current == null || !current.equals(localNodeId)) {
            return null;
        }
        
        String nextHop = null;
        if (!path.isEmpty()) {
            nextHop = path.get(path.size() - 1);
            nextHopCache.put(destination, nextHop);
        }
        
        return nextHop;
    }
    
    /**
     * Formats an edge as a string.
     * 
     * @param parent the parent node
     * @param child the child node
     * @param weight the edge weight
     * @return formatted string "parent, child, weight"
     */
    private String formatEdge(String parent, String child, int weight) {
        return parent + ", " + child + ", " + weight;
    }
    
    /**
     * Internal class representing an MST edge with parent-child relationship.
     */
    private static class MSTEdge {
        final String parent;
        final String child;
        final int weight;
        
        MSTEdge(String parent, String child, int weight) {
            this.parent = parent;
            this.child = child;
            this.weight = weight;
        }
    }
    
    /**
     * Extracts all MST edges into a structured format.
     * 
     * @return list of MST edges
     */
    private List<MSTEdge> extractMSTEdges() {
        List<MSTEdge> edges = new ArrayList<>();
        
        for (Map.Entry<String, Edge> entry : mst.entrySet()) {
            String childNode = entry.getKey();
            Edge edgeToParent = entry.getValue();
            String parentNode = edgeToParent.getDestination();
            edges.add(new MSTEdge(parentNode, childNode, edgeToParent.getWeight()));
        }
        
        return edges;
    }
    
    /**
     * Prints the Minimum Spanning Tree in breadth-first order.
     * Displays each edge as: parent, child, weight.
     * Output starts from the local node as root.
     */
    public synchronized void printMST() {
        if (mst.isEmpty()) {
            System.out.println("MST has not been calculated yet.");
            return;
        }
        
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        List<MSTEdge> mstEdges = extractMSTEdges();
        
        queue.add(localNodeId);
        visited.add(localNodeId);
        
        while (!queue.isEmpty()) {
            String currentNode = queue.poll();
            
            for (MSTEdge edge : mstEdges) {
                if (edge.parent.equals(currentNode) && !visited.contains(edge.child)) {
                    System.out.println(formatEdge(edge.parent, edge.child, edge.weight));
                    visited.add(edge.child);
                    queue.add(edge.child);
                }
            }
        }
    }
    
    /**
     * Gets all edges in the Minimum Spanning Tree.
     * 
     * @return a list of strings representing edges in format "parent, child, weight"
     */
    public synchronized List<String> getMSTEdges() {
        List<String> edges = new ArrayList<>();
        
        for (MSTEdge mstEdge : extractMSTEdges()) {
            edges.add(formatEdge(mstEdge.parent, mstEdge.child, mstEdge.weight));
        }
        
        return edges;
    }
    
    /**
     * Clears the next hop cache.
     * Useful when the network topology changes but link weights remain the same.
     */
    public synchronized void clearCache() {
        nextHopCache.clear();
    }
    
    /**
     * Checks if a path exists to the specified destination.
     * 
     * @param destination the target node to check
     * @return true if a path exists, false otherwise
     */
    public synchronized boolean hasPath(String destination) {
        return findNextHop(destination) != null;
    }
}