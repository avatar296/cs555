package csx55.overlay.util;

import java.util.*;

/**
 * Utility class for creating overlay network topologies. Generates k-regular graphs where each node
 * has exactly k connections, ensuring network connectivity and balanced load distribution.
 */
public class OverlayCreator {

  /**
   * Represents a complete connection plan for the overlay network. Contains both the adjacency
   * information and the weighted links.
   */
  public static class ConnectionPlan {
    /** Map of node IDs to their connected peers */
    private final Map<String, Set<String>> nodeConnections;

    /** List of all links in the overlay with weights */
    private final List<Link> allLinks;

    /** Constructs an empty ConnectionPlan. */
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

  /** Represents a weighted bidirectional link between two nodes. */
  public static class Link {
    public final String nodeA;
    public final String nodeB;
    private int weight;

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
     * @param nodeA first node identifier
     * @param nodeB second node identifier
     * @param weight the weight of the link
     */
    public Link(String nodeA, String nodeB, int weight) {
      this.nodeA = nodeA;
      this.nodeB = nodeB;
      this.weight = weight;
    }

    /**
     * Gets the weight of this link.
     *
     * @return the weight
     */
    public int getWeight() {
      return weight;
    }

    /**
     * Sets the weight of this link.
     *
     * @param weight the new weight
     */
    public void setWeight(int weight) {
      this.weight = weight;
    }

    @Override
    public String toString() {
      return nodeA + ", " + nodeB + ", " + getWeight();
    }
  }

  /**
   * Creates an overlay network with the specified connection requirement. Builds a k-regular graph
   * where each node has exactly k connections. Uses a ring-based approach with nearest neighbor
   * connections.
   *
   * @param nodeIds list of node identifiers to include in the overlay
   * @param connectionRequirement number of connections each node should have
   * @return a ConnectionPlan containing the overlay topology
   * @throws IllegalArgumentException if parameters make overlay creation impossible
   */
  public static ConnectionPlan createOverlay(List<String> nodeIds, int connectionRequirement) {
    if (nodeIds.size() <= connectionRequirement) {
      throw new IllegalArgumentException(
          "Number of nodes ("
              + nodeIds.size()
              + ") must be greater than connection requirement ("
              + connectionRequirement
              + ")");
    }

    if (connectionRequirement < 1) {
      throw new IllegalArgumentException("Connection requirement must be at least 1");
    }

    int totalEdgesNeeded = (nodeIds.size() * connectionRequirement) / 2;
    int maxPossibleEdges = (nodeIds.size() * (nodeIds.size() - 1)) / 2;

    if (totalEdgesNeeded > maxPossibleEdges) {
      throw new IllegalArgumentException("Impossible to create overlay with given parameters");
    }

    ConnectionPlan plan = new ConnectionPlan();
    int n = nodeIds.size();

    for (String nodeId : nodeIds) {
      plan.nodeConnections.put(nodeId, new HashSet<>());
    }

    int targetEdges = (nodeIds.size() * connectionRequirement) / 2;
    if (!createKRegularGraph(plan, nodeIds, connectionRequirement)) {
      createApproximateRegularGraph(plan, nodeIds, connectionRequirement, targetEdges);
    }

    java.util.Random random = new java.util.Random();

    for (Link link : plan.allLinks) {
      int weight = random.nextInt(10) + 1;
      link.setWeight(weight);
    }

    return plan;
  }

  /**
   * Determines which nodes should initiate connections to avoid duplicates. For each link,
   * arbitrarily selects one node as the initiator.
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
   * Constructs a perfect k-regular graph using systematic edge placement. Guarantees every node has
   * exactly connectionRequirement connections.
   *
   * @param plan the connection plan to populate
   * @param nodeIds list of node identifiers
   * @param k the connection requirement (degree of each node)
   * @return true if successful k-regular graph was created, false otherwise
   */
  private static boolean createKRegularGraph(ConnectionPlan plan, List<String> nodeIds, int k) {
    int n = nodeIds.size();

    // Check if k-regular graph is possible
    if (k >= n || (n * k) % 2 != 0) {
      return false; // Impossible configuration
    }

    // Use adjacency matrix for systematic construction
    boolean[][] adjacent = new boolean[n][n];

    // Method: Connect each node i to nodes (i+1), (i+2), ..., (i+k/2) and
    // (i-1), (i-2), ..., (i-k/2) with wraparound
    // This works when k is even and creates a perfect k-regular graph

    if (k % 2 == 0) {
      // Even k: use symmetric nearest neighbor approach
      int halfK = k / 2;
      for (int i = 0; i < n; i++) {
        for (int j = 1; j <= halfK; j++) {
          int target = (i + j) % n;
          if (!adjacent[i][target]) {
            adjacent[i][target] = true;
            adjacent[target][i] = true;

            String nodeA = nodeIds.get(i);
            String nodeB = nodeIds.get(target);
            plan.nodeConnections.get(nodeA).add(nodeB);
            plan.nodeConnections.get(nodeB).add(nodeA);
            plan.allLinks.add(new Link(nodeA, nodeB));
          }
        }
      }
    } else {
      // Odd k: more complex construction needed
      if (n % 2 != 0) {
        return false; // Cannot create odd-degree regular graph with odd n
      }

      // For odd k, use Hamiltonian path + perfect matching approach
      // First create a k-1 regular graph (even degree)
      int evenK = k - 1;
      int halfEvenK = evenK / 2;

      for (int i = 0; i < n; i++) {
        for (int j = 1; j <= halfEvenK; j++) {
          int target = (i + j) % n;
          if (!adjacent[i][target]) {
            adjacent[i][target] = true;
            adjacent[target][i] = true;

            String nodeA = nodeIds.get(i);
            String nodeB = nodeIds.get(target);
            plan.nodeConnections.get(nodeA).add(nodeB);
            plan.nodeConnections.get(nodeB).add(nodeA);
            plan.allLinks.add(new Link(nodeA, nodeB));
          }
        }
      }

      // Add one perfect matching to make it k-regular
      for (int i = 0; i < n / 2; i++) {
        int a = i;
        int b = (i + n / 2) % n;
        if (!adjacent[a][b]) {
          adjacent[a][b] = true;
          adjacent[b][a] = true;

          String nodeA = nodeIds.get(a);
          String nodeB = nodeIds.get(b);
          plan.nodeConnections.get(nodeA).add(nodeB);
          plan.nodeConnections.get(nodeB).add(nodeA);
          plan.allLinks.add(new Link(nodeA, nodeB));
        }
      }
    }

    // Verify we achieved k-regular property
    for (String nodeId : nodeIds) {
      if (plan.nodeConnections.get(nodeId).size() != k) {
        // Failed to create k-regular graph, clear and return false
        plan.nodeConnections.clear();
        plan.allLinks.clear();
        for (String id : nodeIds) {
          plan.nodeConnections.put(id, new HashSet<>());
        }
        return false;
      }
    }

    return true;
  }

  /**
   * Fallback method for approximate regular graph when perfect k-regular fails. Uses the original
   * ring-based approach but with better balancing.
   */
  private static void createApproximateRegularGraph(
      ConnectionPlan plan, List<String> nodeIds, int connectionRequirement, int targetEdges) {
    int n = nodeIds.size();

    // Phase 1: Create Hamilton circuit for connectivity
    for (int i = 0; i < n; i++) {
      String nodeA = nodeIds.get(i);
      String nodeB = nodeIds.get((i + 1) % n);

      if (!plan.nodeConnections.get(nodeA).contains(nodeB)) {
        plan.nodeConnections.get(nodeA).add(nodeB);
        plan.nodeConnections.get(nodeB).add(nodeA);
        plan.allLinks.add(new Link(nodeA, nodeB));
      }
    }

    // Phase 2: Add remaining edges systematically
    while (plan.allLinks.size() < targetEdges) {
      boolean addedEdge = false;

      // Try to add edges to nodes with fewest connections first
      for (int currentConnections = 1;
          currentConnections < connectionRequirement && !addedEdge;
          currentConnections++) {
        for (int i = 0; i < n && !addedEdge; i++) {
          String nodeA = nodeIds.get(i);

          if (plan.nodeConnections.get(nodeA).size() != currentConnections) continue;

          // Try to connect to nearby nodes with room for more connections
          for (int distance = 2; distance < n && !addedEdge; distance++) {
            String nodeB = nodeIds.get((i + distance) % n);

            if (!plan.nodeConnections.get(nodeA).contains(nodeB)
                && plan.nodeConnections.get(nodeB).size() < connectionRequirement) {

              plan.nodeConnections.get(nodeA).add(nodeB);
              plan.nodeConnections.get(nodeB).add(nodeA);
              plan.allLinks.add(new Link(nodeA, nodeB));
              addedEdge = true;
            }
          }
        }
      }

      if (!addedEdge) break; // Cannot add more edges while maintaining constraints
    }
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
