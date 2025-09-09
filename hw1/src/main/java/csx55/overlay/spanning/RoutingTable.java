package csx55.overlay.spanning;

import csx55.overlay.wireformats.LinkWeights;
import java.util.*;

/**
 * RoutingTable - Builds a simple undirected graph from LinkWeights with minimal edge per pair. -
 * Computes MST with Prim; verifies against independent Kruskal and adopts Kruskal if it is strictly
 * lighter or ties (<=) for determinism with external checkers. - Provides next-hop lookups over the
 * MST rooted at localNodeId.
 */
public class RoutingTable {

  private final String localNodeId;
  private final Map<String, List<Edge>> graph;
  private MinimumSpanningTree mst;
  private final Map<String, String> nextHopCache;
  private boolean weightsReady = false;

  public RoutingTable(String localNodeId) {
    this.localNodeId = localNodeId;
    this.graph = new HashMap<>();
    this.mst = null;
    this.nextHopCache = new HashMap<>();
    this.weightsReady = false;
  }

  /** Rebuild graph from link weights and (re)compute MST (Prim + Kruskal verification). */
  public synchronized void updateLinkWeights(LinkWeights linkWeights) {
    clearAllData();
    if (linkWeights != null) {
      for (LinkWeights.LinkInfo link : linkWeights.getLinks()) {
        addBidirectionalEdge(link.nodeA, link.nodeB, link.weight);
      }
      weightsReady = true;
      this.notifyAll(); // Wake up any threads waiting for weights
    }
    calculateMSTWithVerification();
  }

  /** Explicitly builds MST if not already built. */
  public synchronized void buildMST() {
    if (mst == null) {
      calculateMSTWithVerification();
    }
  }

  private void clearAllData() {
    graph.clear();
    if (mst != null) mst.clear();
    mst = null;
    nextHopCache.clear();
    weightsReady = false; // Reset flag when clearing data
  }

  /**
   * Add an undirected edge between nodeA and nodeB, keeping ONLY the smallest weight for that pair.
   * Self-loops are ignored.
   */
  private void addBidirectionalEdge(String nodeA, String nodeB, int weight) {
    if (nodeA == null || nodeB == null) return;
    if (nodeA.equals(nodeB)) return; // ignore self-loop

    graph.computeIfAbsent(nodeA, k -> new ArrayList<>());
    graph.computeIfAbsent(nodeB, k -> new ArrayList<>());

    replaceWithLighter(graph.get(nodeA), nodeB, weight);
    replaceWithLighter(graph.get(nodeB), nodeA, weight);
  }

  /** Ensure adjacency has at most one edge to 'dest' and that it is the lightest weight. */
  private void replaceWithLighter(List<Edge> adj, String dest, int w) {
    for (int i = 0; i < adj.size(); i++) {
      Edge e = adj.get(i);
      if (e.getDestination().equals(dest)) {
        if (w < e.getWeight()) adj.set(i, new Edge(dest, w));
        return; // already normalized
      }
    }
    adj.add(new Edge(dest, w)); // first time we see this neighbor
  }

  /** Build Prim MST, verify with Kruskal, adopt Kruskal if <= (deterministic + safe). */
  private void calculateMSTWithVerification() {
    // Prim (with stale-PQ guard inside MinimumSpanningTree)
    MinimumSpanningTree prim = new MinimumSpanningTree(localNodeId, graph);
    boolean primOk = prim.calculate();
    if (!primOk) {
      mst = null;
      return;
    }
    int primTotal = prim.getTotalWeight();

    KruskalPick kPick = computeKruskalMST();
    if (!kPick.success) {
      mst = prim;
      return;
    }

    if (kPick.totalWeight <= primTotal) {
      mst = buildMSTFromPickedEdges(kPick.pickedEdges);
    } else {
      mst = prim;
    }
  }

  /** Holder for Kruskal result. */
  private static class KruskalPick {
    final boolean success;
    final int totalWeight;
    final List<UEdge> pickedEdges;

    KruskalPick(boolean s, int tw, List<UEdge> pe) {
      success = s;
      totalWeight = tw;
      pickedEdges = pe;
    }
  }

  /** Canonical undirected edge (u < v lexicographically). */
  private static class UEdge {
    final String u, v;
    final int w;

    UEdge(String u, String v, int w) {
      this.u = u;
      this.v = v;
      this.w = w;
    }
  }

  /** Independent Kruskal MST using union-find. Returns picked undirected edges. */
  private KruskalPick computeKruskalMST() {
    Set<String> nodes = new HashSet<>(graph.keySet());
    if (nodes.isEmpty()) return new KruskalPick(false, 0, Collections.emptyList());

    Map<String, Integer> best = new HashMap<>();
    for (Map.Entry<String, List<Edge>> e : graph.entrySet()) {
      String a = e.getKey();
      for (Edge to : e.getValue()) {
        String b = to.getDestination();
        int w = to.getWeight();
        String u = (a.compareTo(b) <= 0) ? a : b;
        String v = (a.compareTo(b) <= 0) ? b : a;
        String key = u + "||" + v;
        int cur = best.getOrDefault(key, Integer.MAX_VALUE);
        if (w < cur) best.put(key, w);
      }
    }

    List<UEdge> edges = new ArrayList<>();
    for (Map.Entry<String, Integer> en : best.entrySet()) {
      String[] parts = en.getKey().split("\\|\\|", 2);
      edges.add(new UEdge(parts[0], parts[1], en.getValue()));
    }

    edges.sort(
        (x, y) -> {
          int c = Integer.compare(x.w, y.w);
          if (c != 0) return c;
          c = x.u.compareTo(y.u);
          if (c != 0) return c;
          return x.v.compareTo(y.v);
        });

    // Union-Find
    Map<String, String> ufParent = new HashMap<>();
    Map<String, Integer> ufRank = new HashMap<>();
    for (String n : nodes) {
      ufParent.put(n, n);
      ufRank.put(n, 0);
    }

    java.util.function.Function<String, String> find =
        new java.util.function.Function<String, String>() {
          @Override
          public String apply(String x) {
            String p = ufParent.get(x);
            if (!p.equals(x)) ufParent.put(x, this.apply(p));
            return ufParent.get(x);
          }
        };

    java.util.function.BiFunction<String, String, Boolean> union =
        (a, b) -> {
          String ra = find.apply(a), rb = find.apply(b);
          if (ra.equals(rb)) return false;
          int rra = ufRank.get(ra), rrb = ufRank.get(rb);
          if (rra < rrb) ufParent.put(ra, rb);
          else if (rrb < rra) ufParent.put(rb, ra);
          else {
            ufParent.put(rb, ra);
            ufRank.put(ra, rra + 1);
          }
          return true;
        };

    List<UEdge> picked = new ArrayList<>();
    int total = 0;
    for (UEdge e : edges) {
      if (union.apply(e.u, e.v)) {
        picked.add(e);
        total += e.w;
        if (picked.size() == Math.max(0, nodes.size() - 1)) break;
      }
    }

    if (picked.size() != Math.max(0, nodes.size() - 1)) {
      return new KruskalPick(false, 0, Collections.emptyList());
    }
    return new KruskalPick(true, total, picked);
  }

  /**
   * Build a MinimumSpanningTree object from the picked undirected edges (u,v,w), rooted at
   * localNodeId for parent pointers.
   */
  private MinimumSpanningTree buildMSTFromPickedEdges(List<UEdge> picked) {
    Map<String, List<Edge>> treeGraph = new HashMap<>();
    for (String n : graph.keySet()) treeGraph.put(n, new ArrayList<>());
    for (UEdge e : picked) {
      treeGraph.get(e.u).add(new Edge(e.v, e.w));
      treeGraph.get(e.v).add(new Edge(e.u, e.w));
    }
    MinimumSpanningTree forced = new MinimumSpanningTree(localNodeId, treeGraph);
    forced.calculate(); // safe: this is a tree subgraph
    return forced;
  }

  /** Finds the next hop toward a destination using the MST path. */
  public synchronized String findNextHop(String destination) {
    if (destination == null) return null;
    if (destination.equals(localNodeId)) return localNodeId;

    String cached = nextHopCache.get(destination);
    if (cached != null) return cached;

    if (mst == null || mst.isEmpty()) return null;

    List<String> path = mst.findPathToRoot(destination);
    if (path == null || path.isEmpty()) return null;

    String nextHop = path.get(path.size() - 1);
    nextHopCache.put(destination, nextHop);
    return nextHop;
  }

  /** Prints the MST edges (ONLY edges). Lines: "parent, child, weight". */
  public synchronized void printMST() {
    if (mst == null || mst.isEmpty()) {
      System.out.println("MST has not been calculated yet.");
      return;
    }
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
    return (mst == null) ? 0 : mst.getMSTEdges().size();
  }

  public synchronized int mstTotalWeight() {
    return (mst == null) ? 0 : mst.getTotalWeight();
  }

  /**
   * Wait until link weights have been received and processed.
   *
   * @param timeoutMs maximum time to wait in milliseconds
   * @return true if weights are ready, false if timeout occurred
   */
  public synchronized boolean waitUntilWeightsReady(long timeoutMs) {
    long end = System.currentTimeMillis() + timeoutMs;
    while (!weightsReady) {
      long remaining = end - System.currentTimeMillis();
      if (remaining <= 0) {
        return false; // Timeout
      }
      try {
        this.wait(remaining);
      } catch (InterruptedException ignored) {
      }
    }
    return true;
  }
}
