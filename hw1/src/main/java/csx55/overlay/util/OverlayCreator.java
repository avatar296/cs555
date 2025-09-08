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

    public String getNodeA() {
      return nodeA;
    }

    public String getNodeB() {
      return nodeB;
    }

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

    public Set<Link> getUniqueLinks() {
      return uniqueLinks;
    }
  }

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

    if ((n * connectionRequirement) % 2 != 0) {
      LoggerUtil.warn(
          "OverlayCreator",
          "Cannot create perfect "
              + connectionRequirement
              + "-regular graph with "
              + n
              + " nodes. Approximating.");
    }

    if (connectionRequirement >= n) {
      connectionRequirement = n - 1;
    }

    Map<String, Set<String>> adj = new LinkedHashMap<>();
    for (String id : nodeIds) {
      adj.put(id, new LinkedHashSet<>());
    }

    // Ring-based neighbor connections
    for (int i = 0; i < n; i++) {
      for (int j = 1; j <= connectionRequirement / 2; j++) {
        String nodeA = nodeIds.get(i);
        String nodeB = nodeIds.get((i + j) % n);

        if (adj.get(nodeA).size() < connectionRequirement
            && adj.get(nodeB).size() < connectionRequirement
            && !adj.get(nodeA).contains(nodeB)) {
          adj.get(nodeA).add(nodeB);
          adj.get(nodeB).add(nodeA);
        }
      }
    }

    // If k is odd and n is even, connect opposite nodes
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

    // Fill missing edges
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
          if (adj.get(nodeA).size() >= connectionRequirement) break;
        }
      }
    }

    // Verify
    for (Map.Entry<String, Set<String>> entry : adj.entrySet()) {
      if (entry.getValue().size() != connectionRequirement) {
        LoggerUtil.warn(
            "OverlayCreator", "Node " + entry.getKey() + " has degree " + entry.getValue().size());
      }
    }

    // Generate unique links with weights in [1,10]
    Set<Link> links = new LinkedHashSet<>();
    Random rnd = new Random();
    for (String nodeA : nodeIds) {
      for (String nodeB : adj.get(nodeA)) {
        if (nodeA.compareTo(nodeB) < 0) {
          int weight = rnd.nextInt(10) + 1;
          Link link = new Link(nodeA, nodeB, weight);
          links.add(link);

          // 🔎 Debug print each edge as it's created
          System.out.println(
              "[OverlayCreator] Added link: "
                  + link.getNodeA()
                  + " <-> "
                  + link.getNodeB()
                  + " weight="
                  + link.getWeight());
        }
      }
    }

    System.out.println(
        "[OverlayCreator] Overlay built with "
            + links.size()
            + " unique edges across "
            + nodeIds.size()
            + " nodes.");

    return new ConnectionPlan(adj, links);
  }
}
