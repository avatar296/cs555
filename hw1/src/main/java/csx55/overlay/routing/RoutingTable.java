package csx55.overlay.routing;

import csx55.overlay.wireformats.LinkWeights;
import java.util.*;

public class RoutingTable {

  /** The identifier of the local node that owns this routing table */
  private final String localNodeId;

  /** Graph representation as adjacency list for all nodes and their connections */
  private final Map<String, List<Edge>> graph;

  /** Minimum Spanning Tree calculator and storage */
  private MinimumSpanningTree mst;

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
    this.mst = null;
    this.nextHopCache = new HashMap<>();
  }

  /**
   * Updates the routing table with new link weight information. Rebuilds the graph representation
   * and recalculates the MST.
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

  /** Clears all internal data structures. */
  private void clearAllData() {
    graph.clear();
    if (mst != null) {
      mst.clear();
    }
    mst = null;
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
    graph.computeIfAbsent(nodeA, k -> new ArrayList<>()).add(new Edge(nodeB, weight));
    graph.computeIfAbsent(nodeB, k -> new ArrayList<>()).add(new Edge(nodeA, weight));
  }

  /**
   * Calculates the Minimum Spanning Tree using Prim's algorithm. Delegates to the
   * MinimumSpanningTree class for the actual calculation.
   */
  private void calculateMST() {
    mst = new MinimumSpanningTree(localNodeId, graph);
    mst.calculate();
  }

  /**
   * Finds the next hop to reach the specified destination node. Uses the MST to determine the
   * shortest path and returns the immediate next node. Results are cached for improved performance
   * on repeated lookups.
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

    if (mst == null) {
      return null;
    }

    List<String> path = mst.findPathToRoot(destination);
    if (path == null || path.isEmpty()) {
      return null;
    }

    String nextHop = path.get(path.size() - 1);
    nextHopCache.put(destination, nextHop);

    return nextHop;
  }

  /**
   * Prints the Minimum Spanning Tree in breadth-first order. Displays each edge as: parent, child,
   * weight. Output starts from the local node as root.
   */
  public synchronized void printMST() {
    if (mst == null || mst.isEmpty()) {
      System.out.println("MST has not been calculated yet.");
      return;
    }
    mst.print();
  }

  /**
   * Gets all edges in the Minimum Spanning Tree.
   *
   * @return a list of strings representing edges in format "parent, child, weight"
   */
  public synchronized List<String> getMSTEdges() {
    if (mst == null) {
      return new ArrayList<>();
    }
    return mst.getFormattedEdges();
  }

  /**
   * Clears the next hop cache. Useful when the network topology changes but link weights remain the
   * same.
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
