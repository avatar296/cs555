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
        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> minWeight = new HashMap<>();
        PriorityQueue<Edge> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e.weight));
        Set<String> inTree = new HashSet<>();
        
        // Initialize all nodes with infinite weight
        for (String node : graph.keySet()) {
            minWeight.put(node, Integer.MAX_VALUE);
        }
        
        // Start with local node as root
        minWeight.put(localNodeId, 0);
        pq.add(new Edge(localNodeId, 0));
        
        // Build MST
        while (!pq.isEmpty()) {
            Edge current = pq.poll();
            String currentNode = current.destination;
            
            if (inTree.contains(currentNode)) {
                continue;
            }
            
            // Add node to tree
            inTree.add(currentNode);
            
            // If not root, add to MST
            if (!currentNode.equals(localNodeId) && parent.containsKey(currentNode)) {
                String parentNode = parent.get(currentNode);
                int weight = minWeight.get(currentNode);
                mst.put(currentNode, new Edge(parentNode, weight));
            }
            
            // Update neighbors
            List<Edge> neighbors = graph.get(currentNode);
            if (neighbors != null) {
                for (Edge neighborEdge : neighbors) {
                    String neighbor = neighborEdge.destination;
                    int weight = neighborEdge.weight;
                    
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
        
        // Build path from destination to root
        List<String> path = new ArrayList<>();
        String current = destination;
        
        while (current != null && !current.equals(localNodeId)) {
            path.add(current);
            Edge parentEdge = mst.get(current);
            if (parentEdge == null) {
                // No path found
                return null;
            }
            current = parentEdge.destination;
        }
        
        // Path should end at local node
        if (current == null || !current.equals(localNodeId)) {
            return null;
        }
        
        // The next hop is the last node in the reversed path (closest to root)
        String nextHop = null;
        if (!path.isEmpty()) {
            nextHop = path.get(path.size() - 1);
            // Cache the result
            nextHopCache.put(destination, nextHop);
        }
        
        return nextHop;
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