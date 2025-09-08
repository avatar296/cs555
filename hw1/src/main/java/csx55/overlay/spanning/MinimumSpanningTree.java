package csx55.overlay.spanning;

import csx55.overlay.util.LoggerUtil;
import java.util.*;

public class MinimumSpanningTree {

  private final String rootNodeId;
  private final Map<String, List<Edge>> graph;
  private final Map<String, Edge> mstEdges;

  /**
   * Constructs a new MinimumSpanningTree calculator.
   *
   * @param rootNodeId the root node for the MST
   * @param graph      the graph to build the MST from
   */
  public MinimumSpanningTree(String rootNodeId, Map<String, List<Edge>> graph) {
    this.rootNodeId = rootNodeId;
    this.graph = graph;
    this.mstEdges = new HashMap<>();
  }

  /**
   * Calculates the Minimum Spanning Tree using Prim's algorithm. The resulting
   * MST provides optimal
   * paths from the root node to all other nodes.
   *
   * @return true if MST was calculated successfully, false otherwise
   */
  public boolean calculate() {
    if (!graph.containsKey(rootNodeId)) {
      LoggerUtil.warn(
          "MinimumSpanningTree",
          "Root node " + rootNodeId + " not found in graph - cannot calculate MST");
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

      if (inTree.contains(u)) {
        continue;
      }

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

    // Calculate and log total MST weight for debugging
    int totalWeight = 0;
    for (Edge edge : mstEdges.values()) {
      totalWeight += edge.getWeight();
    }
    LoggerUtil.debug(
        "MinimumSpanningTree",
        "MST calculated for root "
            + rootNodeId
            + " with total weight: "
            + totalWeight
            + " and "
            + mstEdges.size()
            + " edges");

    return true;
  }

  /**
   * Finds the path from a destination node back to the root.
   *
   * @param destination the target node
   * @return list of nodes in the path from destination to root (exclusive of
   *         root), or null if no
   *         path exists
   */
  public List<String> findPathToRoot(String destination) {
    if (destination.equals(rootNodeId)) {
      return Collections.emptyList();
    }

    List<String> path = new ArrayList<>();
    String current = destination;

    while (current != null && !current.equals(rootNodeId)) {
      path.add(current);
      Edge parentEdge = mstEdges.get(current);
      if (parentEdge == null) {
        return null; // No path exists
      }
      current = parentEdge.getDestination();
    }

    if (!rootNodeId.equals(current)) {
      return null; // Path doesn't reach root
    }

    return path;
  }

  /**
   * Gets all edges in the Minimum Spanning Tree.
   *
   * @return map of child nodes to their parent edges
   */
  public Map<String, Edge> getMSTEdges() {
    return new HashMap<>(mstEdges);
  }

  /**
   * Extracts all MST edges into a structured format.
   *
   * @return list of MST edges with parent-child relationships
   */
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

  /**
   * Prints the Minimum Spanning Tree in breadth-first order. Displays each edge
   * as: parent, child,
   * weight.
   */
  public void print() {
    if (mstEdges.isEmpty()) {
      System.out.println("MST has not been calculated yet.");
      return;
    }

    Queue<String> queue = new LinkedList<>();
    Set<String> visited = new HashSet<>();
    List<MSTEdge> edges = extractEdges();

    queue.add(rootNodeId);
    visited.add(rootNodeId);

    while (!queue.isEmpty()) {
      String currentNode = queue.poll();

      for (MSTEdge edge : edges) {
        if (edge.parent.equals(currentNode) && !visited.contains(edge.child)) {
          System.out.println(formatEdge(edge.parent, edge.child, edge.weight));
          visited.add(edge.child);
          queue.add(edge.child);
        }
      }
    }
  }

  /**
   * Formats an edge as a string.
   *
   * @param parent the parent node
   * @param child  the child node
   * @param weight the edge weight
   * @return formatted string "parent, child, weight" (matches link-weights
   *         format)
   */
  private String formatEdge(String parent, String child, int weight) {
    return parent + ", " + child + ", " + weight;
  }

  /**
   * Gets formatted strings for all MST edges.
   *
   * @return list of formatted edge strings
   */
  public List<String> getFormattedEdges() {
    List<String> formatted = new ArrayList<>();
    for (MSTEdge edge : extractEdges()) {
      formatted.add(formatEdge(edge.parent, edge.child, edge.weight));
    }
    return formatted;
  }

  /**
   * Checks if the MST has been calculated.
   *
   * @return true if MST exists, false otherwise
   */
  public boolean isEmpty() {
    return mstEdges.isEmpty();
  }

  /**
   * Gets the total weight of the MST.
   *
   * @return the sum of all edge weights in the MST
   */
  public int getTotalWeight() {
    int totalWeight = 0;
    for (Edge edge : mstEdges.values()) {
      totalWeight += edge.getWeight();
    }
    return totalWeight;
  }

  /** Clears the calculated MST. */
  public void clear() {
    mstEdges.clear();
  }

  /** Internal class representing an MST edge with parent-child relationship. */
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
