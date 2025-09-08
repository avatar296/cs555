package csx55.overlay.spanning;

import csx55.overlay.wireformats.LinkWeights;
import java.util.*;

public class RoutingTable {

  private final String localNodeId;
  private final Map<String, List<Edge>> graph;
  private MinimumSpanningTree mst;
  private final Map<String, String> nextHopCache;

  public RoutingTable(String localNodeId) {
    this.localNodeId = localNodeId;
    this.graph = new HashMap<>();
    this.mst = null;
    this.nextHopCache = new HashMap<>();
  }

  /** Updates the routing table with new link weight information. */
  public synchronized void updateLinkWeights(LinkWeights linkWeights) {
    clearAllData();
    for (LinkWeights.LinkInfo link : linkWeights.getLinks()) {
      addBidirectionalEdge(link.nodeA, link.nodeB, link.weight);
    }
    calculateMST();
  }

  /** Explicitly builds MST if not already built. */
  public synchronized void buildMST() {
    if (mst == null) {
      calculateMST();
    }
  }

  private void clearAllData() {
    graph.clear();
    if (mst != null) mst.clear();
    mst = null;
    nextHopCache.clear();
  }

  private void addBidirectionalEdge(String nodeA, String nodeB, int weight) {
    graph.computeIfAbsent(nodeA, k -> new ArrayList<>()).add(new Edge(nodeB, weight));
    graph.computeIfAbsent(nodeB, k -> new ArrayList<>()).add(new Edge(nodeA, weight));
  }

  private void calculateMST() {
    MinimumSpanningTree newMst = new MinimumSpanningTree(localNodeId, graph);
    boolean success = newMst.calculate();
    mst = success ? newMst : null;
  }

  /** Finds the next hop toward a destination using the MST path. */
  public synchronized String findNextHop(String destination) {
    if (destination.equals(localNodeId)) return localNodeId;

    String cached = nextHopCache.get(destination);
    if (cached != null) return cached;

    if (mst == null) return null;

    List<String> path = mst.findPathToRoot(destination);
    if (path == null || path.isEmpty()) return null;

    // The last element before reaching the root is our next hop
    String nextHop = path.get(path.size() - 1);
    nextHopCache.put(destination, nextHop);
    return nextHop;
  }

  /** Prints the MST edges. */
  public synchronized void printMST() {
    if (mst == null || mst.isEmpty()) {
      System.out.println("MST is empty or not available.");
      System.out.println(
          "[DEBUG][RoutingTable] Graph nodes="
              + graph.keySet().size()
              + " containsRoot="
              + graph.containsKey(localNodeId));
      return;
    }

    System.out.println(
        "[DEBUG][RoutingTable] Printing MST with "
            + mst.getFormattedEdges().size()
            + " edges. Total weight="
            + mst.getTotalWeight());

    for (String edge : mst.getFormattedEdges()) {
      System.out.println(edge);
    }
  }

  /** Gets all formatted MST edges. */
  public synchronized List<String> getMSTEdges() {
    if (mst == null) return new ArrayList<>();
    return mst.getFormattedEdges();
  }

  public synchronized void clearCache() {
    nextHopCache.clear();
  }

  public synchronized boolean hasPath(String destination) {
    return findNextHop(destination) != null;
  }

  public synchronized boolean graphContains(String id) {
    return graph.containsKey(id);
  }

  public synchronized int graphSize() {
    return graph.size();
  }

  public synchronized int mstEdgeCount() {
    if (mst == null) return 0;

    int edges = mst.getFormattedEdges().size();

    int expected = graph.size() - 1;
    if (edges != expected) {
      System.err.println(
          "[DEBUG] MST edge mismatch: expected="
              + expected
              + " actual="
              + edges
              + " for localNodeId="
              + localNodeId);
    }

    return edges;
  }

  public synchronized int mstTotalWeight() {
    return (mst == null) ? 0 : mst.getTotalWeight();
  }
}
