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
    private final String nodeA; // canonical lower (lexicographically)
    private final String nodeB; // canonical higher (lexicographically)
    private int weight;

    public Link(String a, String b, int weight) {
      if (a == null || b == null) throw new IllegalArgumentException("node id null");
      if (a.equals(b)) throw new IllegalArgumentException("self-link not allowed");

      // canonicalize ordering so (A,B) == (B,A)
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
      // formatting exactly as per spec: "IP:port , IP:port,  weight"
      return nodeA + " , " + nodeB + ",  " + weight;
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
    if (nodeIds == null) throw new IllegalArgumentException("nodeIds null");
    if (nodeIds.size() < 2) throw new IllegalArgumentException("need at least 2 nodes");
    if (connectionRequirement < 1) throw new IllegalArgumentException("connectionRequirement < 1");
    int n = nodeIds.size();
    if (connectionRequirement >= n) {
      // can't connect to self; maximum meaningful CR is n-1
      connectionRequirement = n - 1;
    }

    // adjacency map (ordered by insertion)
    Map<String, Set<String>> adj = new LinkedHashMap<>();
    for (String id : nodeIds) adj.put(id, new LinkedHashSet<>());

    // ring baseline to guarantee connectivity
    for (int i = 0; i < n; i++) {
      String a = nodeIds.get(i);
      String b = nodeIds.get((i + 1) % n);
      adj.get(a).add(b);
      adj.get(b).add(a);
    }

    // add additional nearest neighbors incrementally until each node has CR
    // neighbors or no more possible
    // We'll try symmetric additions when possible.
    for (int dist = 2; dist <= n / 2 && !allHaveDegree(adj, connectionRequirement); dist++) {
      for (int i = 0; i < n && !allHaveDegree(adj, connectionRequirement); i++) {
        String a = nodeIds.get(i);
        String b = nodeIds.get((i + dist) % n);

        if (adj.get(a).size() < connectionRequirement
            && adj.get(b).size() < connectionRequirement
            && !adj.get(a).contains(b)) {
          adj.get(a).add(b);
          adj.get(b).add(a);
        }
      }
    }

    // Final pass: if some nodes still lack degree, greedily connect to any node
    // with spare capacity
    outer:
    while (!allHaveDegree(adj, connectionRequirement)) {
      for (int i = 0; i < n; i++) {
        String a = nodeIds.get(i);
        if (adj.get(a).size() >= connectionRequirement) continue;
        for (int j = 0; j < n; j++) {
          if (i == j) continue;
          String b = nodeIds.get(j);
          if (adj.get(b).size() >= connectionRequirement) continue;
          if (!adj.get(a).contains(b)) {
            adj.get(a).add(b);
            adj.get(b).add(a);
            if (allHaveDegree(adj, connectionRequirement)) break outer;
          }
        }
      }
      // If we made a full scan and can't add more (due to saturated nodes), break to
      // avoid infinite loop
      break;
    }

    // Build unique link set from adjacency (normalized)
    Set<Link> links = new LinkedHashSet<>();
    Random rnd = new Random();
    for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
      String a = e.getKey();
      for (String b : e.getValue()) {
        if (a.compareTo(b) < 0) { // Only add link if a is "less than" b
          // create normalized link; assign a random weight in 1..10
          int weight = 1 + rnd.nextInt(10);
          Link link = new Link(a, b, weight);
          links.add(link); // Set<Link> prevents duplicates thanks to equals/hashCode
        }
      }
    }

    return new ConnectionPlan(adj, links);
  }

  private static boolean allHaveDegree(Map<String, Set<String>> adj, int k) {
    for (Set<String> peers : adj.values()) {
      if (peers.size() < k) return false;
    }
    return true;
  }
}
