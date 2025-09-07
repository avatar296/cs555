package csx55.overlay.util;

import java.util.*;

/**
 * Utility for constructing k-regular overlay topologies (where possible). Produces a ConnectionPlan
 * that contains adjacency + a canonical set of undirected Links.
 *
 * <p>This class focuses on topology construction and link normalization so the registry and
 * autograder see exactly one entry per undirected edge.
 */
public final class OverlayCreator {

  private OverlayCreator() {}

  /**
   * Represents a (normalized) undirected link between two node ids (ip:port strings). Weight is an
   * integer in 1..10. Two Link objects are equal if they connect the same unordered pair of nodes
   * (weight not considered in equality/hash).
   */
  public static final class Link {
    private final String nodeA;
    private final String nodeB;
    private int weight;

    public Link(String a, String b, int weight) {
      if (a == null || b == null) throw new IllegalArgumentException("node id null");
      if (a.equals(b)) throw new IllegalArgumentException("self-link not allowed");

      if (a.compareTo(b) <= 0) {
        this.nodeA = a;
        this.nodeB = b;
      } else {
        this.nodeA = b;
        this.nodeB = a;
      }
      setWeight(weight);
    }

    public Link(String a, String b) {
      this(a, b, 1); // Default weight
    }

    public String getNodeA() {
      return nodeA;
    }

    public String getNodeB() {
      return nodeB;
    }

    /** Ensure weight is in [1,10]. If provided out-of-range, clamp into range. */
    public int getWeight() {
      return weight;
    }

    public void setWeight(int weight) {
      if (weight < 1) weight = 1;
      else if (weight > 10) weight = 10;
      this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Link)) return false;
      Link other = (Link) o;
      return nodeA.equals(other.nodeA) && nodeB.equals(other.nodeB);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeA, nodeB);
    }

    @Override
    public String toString() {
      return nodeA + " " + nodeB + " " + weight;
    }
  }

  /**
   * ConnectionPlan contains the adjacency map (for node->peers) and the canonical unique link set.
   * Use getUniqueLinks() when printing or sending Link_Weights to avoid duplicates.
   */
  public static final class ConnectionPlan {
    private final Map<String, Set<String>> adjacency;
    private final Set<Link> uniqueLinks;

    public ConnectionPlan(Map<String, Set<String>> adjacency, Set<Link> uniqueLinks) {
      this.adjacency = Collections.unmodifiableMap(new LinkedHashMap<>(adjacency));
      this.uniqueLinks = Collections.unmodifiableSet(new LinkedHashSet<>(uniqueLinks));
    }

    public Map<String, Set<String>> getAdjacency() {
      return adjacency;
    }

    public Map<String, Set<String>> getNodeConnections() {
      return adjacency;
    }

    public List<Link> getAllLinks() {
      return new ArrayList<>(uniqueLinks);
    }

    public Set<Link> getUniqueLinks() {
      return uniqueLinks;
    }
  }

  /**
   * Build a simple k-regular-ish overlay using a ring + nearest neighbor approach. If exact
   * k-regularity cannot be achieved for small N, this will attempt to get as close as possible
   * while maintaining connectivity.
   *
   * @param nodeIds list of node identifiers (IP:port)
   * @param connectionRequirement CR (k)
   * @return ConnectionPlan with adjacency + canonical unique links (with weights 1..10)
   */
  public static ConnectionPlan createOverlay(List<String> nodeIds, int connectionRequirement) {
    return buildOverlay(nodeIds, connectionRequirement);
  }

  public static ConnectionPlan buildOverlay(List<String> nodeIds, int connectionRequirement) {
    if (nodeIds == null || nodeIds.size() < 2) {
      throw new IllegalArgumentException("Need at least 2 nodes");
    }
    if (connectionRequirement < 1) {
      throw new IllegalArgumentException("connectionRequirement must be at least 1");
    }

    int n = nodeIds.size();

    // A k-regular graph is impossible if n*k is odd (total degree must be even)
    if ((n * connectionRequirement) % 2 != 0) {
      LoggerUtil.warn(
          "OverlayCreator",
          "Cannot create perfect "
              + connectionRequirement
              + "-regular graph with "
              + n
              + " nodes (n*k must be even). Will approximate.");
    }

    // Clamp k to a realistic maximum
    if (connectionRequirement >= n) {
      connectionRequirement = n - 1;
    }

    Map<String, Set<String>> adj = new LinkedHashMap<>();
    for (String id : nodeIds) {
      adj.put(id, new LinkedHashSet<>());
    }

    // This algorithm adds edges symmetrically, connecting each node to its k/2 nearest
    // neighbors on each side of the ring (circulant graph approach)
    for (int i = 0; i < n; i++) {
      for (int j = 1; j <= connectionRequirement / 2; j++) {
        String nodeA = nodeIds.get(i);
        String nodeB = nodeIds.get((i + j) % n);

        // Add the symmetric edge if it doesn't exceed the requirement
        if (adj.get(nodeA).size() < connectionRequirement
            && adj.get(nodeB).size() < connectionRequirement
            && !adj.get(nodeA).contains(nodeB)) {
          adj.get(nodeA).add(nodeB);
          adj.get(nodeB).add(nodeA);
        }
      }
    }

    // If k is odd, the logic above leaves one connection missing.
    // For even n, connect to the node at the opposite side of the ring
    if (connectionRequirement % 2 != 0 && n % 2 == 0) {
      int half = n / 2;
      for (int i = 0; i < half; i++) {
        String nodeA = nodeIds.get(i);
        String nodeB = nodeIds.get(i + half);
        if (adj.get(nodeA).size() < connectionRequirement
            && adj.get(nodeB).size() < connectionRequirement
            && !adj.get(nodeA).contains(nodeB)) {
          adj.get(nodeA).add(nodeB);
          adj.get(nodeB).add(nodeA);
        }
      }
    }

    // For odd k and odd n, or other edge cases, try to complete connections
    // This is a more controlled approach than the previous aggressive loop
    for (int i = 0; i < n; i++) {
      String nodeA = nodeIds.get(i);
      if (adj.get(nodeA).size() >= connectionRequirement) continue;

      for (int j = i + 1; j < n; j++) {
        String nodeB = nodeIds.get(j);
        if (adj.get(nodeA).size() < connectionRequirement
            && adj.get(nodeB).size() < connectionRequirement
            && !adj.get(nodeA).contains(nodeB)) {
          adj.get(nodeA).add(nodeB);
          adj.get(nodeB).add(nodeA);

          // Check if we've reached the requirement for nodeA
          if (adj.get(nodeA).size() >= connectionRequirement) break;
        }
      }
    }

    // Verify k-regularity
    boolean isKRegular = true;
    for (Map.Entry<String, Set<String>> entry : adj.entrySet()) {
      if (entry.getValue().size() != connectionRequirement) {
        isKRegular = false;
        LoggerUtil.warn(
            "OverlayCreator",
            "Node "
                + entry.getKey()
                + " has degree "
                + entry.getValue().size()
                + " instead of "
                + connectionRequirement);
      }
    }

    if (isKRegular) {
      LoggerUtil.info(
          "OverlayCreator",
          "Successfully created " + connectionRequirement + "-regular graph with " + n + " nodes");
    }

    // Generate the canonical set of links for the connection plan
    Set<Link> links = new LinkedHashSet<>();
    Random rnd = new Random();
    for (String nodeA : nodeIds) {
      for (String nodeB : adj.get(nodeA)) {
        // To avoid duplicates, only add the link if nodeA is "smaller"
        if (nodeA.compareTo(nodeB) < 0) {
          int weight = 1 + rnd.nextInt(10);
          links.add(new Link(nodeA, nodeB, weight));
        }
      }
    }

    return new ConnectionPlan(adj, links);
  }
}
