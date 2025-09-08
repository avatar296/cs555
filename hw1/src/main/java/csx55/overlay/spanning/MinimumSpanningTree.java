package csx55.overlay.spanning;

import csx55.overlay.util.LoggerUtil;
import java.util.*;

/**
 * Implements Prim's algorithm to build a Minimum Spanning Tree (MST) rooted at a given node. Debug
 * output added to diagnose why edges might be missing.
 */
public class MinimumSpanningTree {

  private final String rootNodeId;
  private final Map<String, List<Edge>> graph;
  private final Map<String, Edge> mstEdges;

  public MinimumSpanningTree(String rootNodeId, Map<String, List<Edge>> graph) {
    this.rootNodeId = rootNodeId;
    this.graph = graph;
    this.mstEdges = new HashMap<>();
  }

  /**
   * Calculates the MST using Prim's algorithm.
   *
   * @return true if a valid MST was calculated, false otherwise
   */
  public boolean calculate() {
    if (!graph.containsKey(rootNodeId)) {
      LoggerUtil.warn("MST", "Root node " + rootNodeId + " not found in graph.");
      System.err.println(
          "[DEBUG][MST] Root not found: " + rootNodeId + " graphNodes=" + graph.keySet());
      return false;
    }

    mstEdges.clear();
    Map<String, Integer> minWeight = new HashMap<>();
    Map<String, String> parent = new HashMap<>();
    Map<String, Integer> edgeWeight = new HashMap<>();
    Set<String> inTree = new HashSet<>();

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

      inTree.add(u);

      if (parent.get(u) != null) {
        mstEdges.put(u, new Edge(parent.get(u), edgeWeight.get(u)));
      }

      List<Edge> neighbors = graph.get(u);
      if (neighbors != null) {
        for (Edge edgeToNeighbor : neighbors) {
          String v = edgeToNeighbor.getDestination();
          int weight = edgeToNeighbor.getWeight();

          if (!inTree.contains(v) && weight < minWeight.get(v)) {
            parent.put(v, u);
            minWeight.put(v, weight);
            edgeWeight.put(v, weight);
            pq.add(new NodeWeight(v, weight));
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

    int totalWeight = 0;
    for (Edge edge : mstEdges.values()) totalWeight += edge.getWeight();

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
      current = parentEdge.getDestination();
    }

    if (!rootNodeId.equals(current)) {
      System.err.println("[DEBUG][MST] Path trace failed: could not reach root=" + rootNodeId);
      return null;
    }

    return path;
  }

  public Map<String, Edge> getMSTEdges() {
    return new HashMap<>(mstEdges);
  }

  public List<MSTEdge> extractEdges() {
    List<MSTEdge> edges = new ArrayList<>();
    for (Map.Entry<String, Edge> entry : mstEdges.entrySet()) {
      String childNode = entry.getKey();
      Edge edgeToParent = entry.getValue();
      String parentNode = edgeToParent.getDestination();
      edges.add(new MSTEdge(parentNode, childNode, edgeToParent.getWeight()));
    }
    return edges;
  }

  public void print() {
    if (mstEdges.isEmpty()) {
      System.out.println("MST has not been calculated yet.");
      return;
    }
    for (MSTEdge edge : extractEdges()) {
      System.out.println(edge.parent + ", " + edge.child + ", " + edge.weight);
    }
  }

  public List<String> getFormattedEdges() {
    List<String> formatted = new ArrayList<>();
    for (MSTEdge edge : extractEdges()) {
      formatted.add(edge.parent + ", " + edge.child + ", " + edge.weight);
    }
    return formatted;
  }

  public boolean isEmpty() {
    return mstEdges.isEmpty();
  }

  public int getTotalWeight() {
    int totalWeight = 0;
    for (Edge edge : mstEdges.values()) {
      totalWeight += edge.getWeight();
    }
    return totalWeight;
  }

  public void clear() {
    mstEdges.clear();
  }

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
