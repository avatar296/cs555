package csx55.overlay.routing;

import csx55.overlay.node.Edge;
import csx55.overlay.wireformats.LinkWeights;

import java.util.*;

public class RoutingTable {
    
    private final String localNodeId;
    private Map<String, List<Edge>> graph;
    private Map<String, Edge> mst;
    private Map<String, String> nextHopCache;
    
    public RoutingTable(String localNodeId) {
        this.localNodeId = localNodeId;
        this.graph = new HashMap<>();
        this.mst = new HashMap<>();
        this.nextHopCache = new HashMap<>();
    }
    
    public synchronized void updateLinkWeights(LinkWeights linkWeights) {
        graph.clear();
        mst.clear();
        nextHopCache.clear();
        
        // Build adjacency list from link weights
        for (LinkWeights.LinkInfo link : linkWeights.getLinks()) {
            graph.computeIfAbsent(link.nodeA, k -> new ArrayList<>())
                .add(new Edge(link.nodeB, link.weight));
            graph.computeIfAbsent(link.nodeB, k -> new ArrayList<>())
                .add(new Edge(link.nodeA, link.weight));
        }
        
        // Calculate MST
        calculateMST();
    }
    
    /**
     * Calculate MST using Prim's algorithm with the local node as root
     */
    private void calculateMST() {
        if (!graph.containsKey(localNodeId)) {
            System.err.println("Warning: Local node " + localNodeId + " not found in graph");
            return;
        }
        
        mst.clear();
        Map<String, Edge> cheapestEdgeToNode = new HashMap<>();
        PriorityQueue<Edge> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e.weight));
        Set<String> inTree = new HashSet<>();
        
        // Start with local node as root
        inTree.add(localNodeId);
        
        // Add all edges from root to priority queue
        List<Edge> rootEdges = graph.get(localNodeId);
        if (rootEdges != null) {
            for (Edge edge : rootEdges) {
                pq.add(edge);
                cheapestEdgeToNode.put(edge.destination, new Edge(localNodeId, edge.weight));
            }
        }
        
        // Build MST
        while (!pq.isEmpty() && inTree.size() < graph.size()) {
            Edge currentEdge = pq.poll();
            String nextNode = currentEdge.destination;
            
            if (inTree.contains(nextNode)) {
                continue;
            }
            
            // Add node to tree
            inTree.add(nextNode);
            mst.put(nextNode, cheapestEdgeToNode.get(nextNode));
            
            // Update priority queue with edges from new node
            List<Edge> neighborEdges = graph.get(nextNode);
            if (neighborEdges != null) {
                for (Edge neighborEdge : neighborEdges) {
                    if (!inTree.contains(neighborEdge.destination)) {
                        Edge currentCheapest = cheapestEdgeToNode.get(neighborEdge.destination);
                        if (currentCheapest == null || currentCheapest.weight > neighborEdge.weight) {
                            pq.add(neighborEdge);
                            cheapestEdgeToNode.put(neighborEdge.destination, 
                                new Edge(nextNode, neighborEdge.weight));
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Find the next hop to reach the destination node
     */
    public synchronized String findNextHop(String destination) {
        if (destination.equals(localNodeId)) {
            return localNodeId;
        }
        
        // Check cache first
        String cachedNextHop = nextHopCache.get(destination);
        if (cachedNextHop != null) {
            return cachedNextHop;
        }
        
        // Trace path from destination back to root (local node)
        String current = destination;
        String previousNode = null;
        
        while (current != null && !current.equals(localNodeId)) {
            Edge parentEdge = mst.get(current);
            if (parentEdge == null) {
                // No path found
                return null;
            }
            
            previousNode = current;
            current = parentEdge.destination;
        }
        
        // Cache the result
        if (previousNode != null) {
            nextHopCache.put(destination, previousNode);
        }
        
        return previousNode;
    }
    
    /**
     * Print the MST in BFS order
     */
    public synchronized void printMST() {
        if (mst.isEmpty()) {
            System.out.println("MST has not been calculated yet.");
            return;
        }
        
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.add(localNodeId);
        visited.add(localNodeId);
        
        while (!queue.isEmpty()) {
            String currentNode = queue.poll();
            
            // Find all children of current node in MST
            for (Map.Entry<String, Edge> entry : mst.entrySet()) {
                String childNode = entry.getKey();
                Edge edgeToParent = entry.getValue();
                String parentNode = edgeToParent.destination;
                
                if (parentNode.equals(currentNode) && !visited.contains(childNode)) {
                    System.out.println(parentNode + ", " + childNode + ", " + edgeToParent.weight);
                    visited.add(childNode);
                    queue.add(childNode);
                }
            }
        }
    }
    
    /**
     * Get all edges in the MST
     */
    public synchronized List<String> getMSTEdges() {
        List<String> edges = new ArrayList<>();
        
        for (Map.Entry<String, Edge> entry : mst.entrySet()) {
            String childNode = entry.getKey();
            Edge edgeToParent = entry.getValue();
            String parentNode = edgeToParent.destination;
            edges.add(parentNode + ", " + childNode + ", " + edgeToParent.weight);
        }
        
        return edges;
    }
    
    public synchronized void clearCache() {
        nextHopCache.clear();
    }
    
    public synchronized boolean hasPath(String destination) {
        return findNextHop(destination) != null;
    }
}