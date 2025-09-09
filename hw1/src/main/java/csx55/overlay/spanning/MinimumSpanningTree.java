package csx55.overlay.spanning;

import csx55.overlay.util.LoggerUtil;
import java.util.*;

/**
 * Implements Prim's algorithm to build a Minimum Spanning Tree (MST) rooted at a given node. Adds a
 * stale-PQ-entry guard so the MST always uses the best (minimal) connecting edge.
 */
public class MinimumSpanningTree {

  private final String rootNodeId;
  private final Map<String, List<Edge>> graph;

  /** child -> edgeToParent (destination=parent, weight=edgeWeight) */
  private final Map<String, Edge> mstEdges;

  public MinimumSpanningTree(String rootNodeId, Map<String, List<Edge>> graph) {
    this.rootNodeId = rootNodeId;
    this.graph = graph;
    this.mstEdges = new HashMap<>();
  }

  /** Calculates the MST using Prim's algorithm. */
  public boolean calculate() {
    if (!graph.containsKey(rootNodeId)) {
      LoggerUtil.warn("MST", "Root node " + rootNodeId + " not found in graph.");
      System.err.println(
          "[DEBUG][MST] Root not found: " + rootNodeId + " graphNodes=" + graph.keySet());
      return false;
    }

    mstEdges.clear();

    final Map<String, Integer> minWeight = new HashMap<>();
    final Map<String, String> parent = new HashMap<>();
    final Set<String> inTree = new HashSet<>();

    for (String node : graph.keySet()) {
      minWeight.put(node, Integer.MAX_VALUE);
      parent.put(node, null);
    }
    minWeight.put(rootNodeId, 0);

    class NodeWeight {
      final String node;
      final int weight;

      NodeWeight(String node, int weight) {
        this.node = node;
        this.weight = weight;
      }
    }

    PriorityQueue<NodeWeight> pq = new PriorityQueue<>(Comparator.comparingInt(nw -> nw.weight));
    pq.add(new NodeWeight(rootNodeId, 0));

    while (!pq.isEmpty()) {
      NodeWeight current = pq.poll();
      String u = current.node;

      if (inTree.contains(u)) continue;

      Integer bestKey = minWeight.get(u);
      if (bestKey == null || current.weight != bestKey) {
        System.err.println(
            "[DEBUG][MST] Stale PQ entry for "
                + u
                + " popped="
                + current.weight
                + " best="
                + bestKey);
        continue;
      }

      inTree.add(u);

      String p = parent.get(u);
      if (p != null) {
        mstEdges.put(u, new Edge(p, bestKey));
      }

      List<Edge> neighbors = graph.get(u);
      if (neighbors != null) {
        for (Edge e : neighbors) {
          String v = e.getDestination();
          int w = e.getWeight();
          if (!inTree.contains(v) && w < minWeight.get(v)) {
            parent.put(v, u);
            minWeight.put(v, w);
            pq.add(new NodeWeight(v, w));
          }
        }
      }
    }

    if (inTree.size() != graph.keySet().size()) {
      LoggerUtil.warn(
          "MST", "MST incomplete: inTree=" + inTree.size() + " graph=" + graph.keySet().size());
      System.err.println(
          "[DEBUG][MST] Graph disconnected. Root="
              + rootNodeId
              + " reachedNodes="
              + inTree
              + " totalGraph="
              + graph.keySet());
      mstEdges.clear();
      return false;
    }

    int totalWeight = getTotalWeight();
    LoggerUtil.debug(
        "MST",
        "Built MST root=" + rootNodeId + " edges=" + mstEdges.size() + " weight=" + totalWeight);
    System.out.println(
        "[DEBUG][MST] Root="
            + rootNodeId
            + " edgeCount="
            + mstEdges.size()
            + " totalWeight="
            + totalWeight);
    return true;
  }

  /** Returns the path from destination back to the root (exclusive of root). */
  public List<String> findPathToRoot(String destination) {
    if (destination.equals(rootNodeId)) return Collections.emptyList();

    List<String> path = new ArrayList<>();
    String current = destination;

    while (current != null && !current.equals(rootNodeId)) {
      path.add(current);
      Edge parentEdge = mstEdges.get(current);
      if (parentEdge == null) {
        System.err.println(
            "[DEBUG][MST] No parent for node "
                + current
                + " when tracing path to root="
                + rootNodeId);
        return null;
      }
      current = parentEdge.getDestination(); // parent
    }

    if (!rootNodeId.equals(current)) {
      System.err.println("[DEBUG][MST] Path trace failed: could not reach root=" + rootNodeId);
      return null;
    }

    return path;
  }

  /** child -> edgeToParent map (copy). */
  public Map<String, Edge> getMSTEdges() {
    return new HashMap<>(mstEdges);
  }

  /** Convert internal MST map to parent-child-weight triples. */
  public List<MSTEdge> extractEdges() {
    List<MSTEdge> edges = new ArrayList<>();
    for (Map.Entry<String, Edge> entry : mstEdges.entrySet()) {
      String child = entry.getKey();
      Edge toParent = entry.getValue();
      String parent = toParent.getDestination();
      edges.add(new MSTEdge(parent, child, toParent.getWeight()));
    }
    return edges;
  }

  /** Print edges as "parent, child, weight" lines. */
  public void print() {
    if (mstEdges.isEmpty()) {
      System.out.println("MST has not been calculated yet.");
      return;
    }
    for (MSTEdge e : extractEdges()) {
      System.out.println(e.parent + ", " + e.child + ", " + e.weight);
    }
  }

  /** Formatted edge strings (for print-mst output validation). */
  public List<String> getFormattedEdges() {
    List<String> out = new ArrayList<>();
    for (MSTEdge e : extractEdges()) {
      out.add(e.parent + ", " + e.child + ", " + e.weight);
    }
    return out;
  }

  public boolean isEmpty() {
    return mstEdges.isEmpty();
  }

  public int getTotalWeight() {
    int total = 0;
    for (Edge e : mstEdges.values()) total += e.getWeight();
    return total;
  }

  public void clear() {
    mstEdges.clear();
  }

  /** Parent-child weighted edge for printing/debugging. */
  public static class MSTEdge {
    public final String parent;
    public final String child;
    public final int weight;

    public MSTEdge(String parent, String child, int weight) {
      this.parent = parent;
      this.child = child;
      this.weight = weight;
    }
  }
}
