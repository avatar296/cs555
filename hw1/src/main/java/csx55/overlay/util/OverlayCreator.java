package csx55.overlay.util;

import csx55.overlay.wireformats.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

public class OverlayCreator {

  private List<String> nodeOrder = new ArrayList<>();
  private final Map<String, Set<String>> adjacencyList = new HashMap<>();
  private final List<LinkWeights.Link> weightedLinks = new ArrayList<>();

  public void setupOverlay(
      int connectionRequirement,
      Map<String, InetSocketAddress> registeredNodes,
      Map<String, DataOutputStream> outputStreams) {

    final int N = registeredNodes.size();

    if (N < 2 || connectionRequirement < 0 || connectionRequirement >= N) {
      return;
    }

    nodeOrder = new ArrayList<>(registeredNodes.keySet());
    Collections.sort(nodeOrder);

    adjacencyList.clear();
    Map<String, Integer> degree = new HashMap<>();
    for (String id : nodeOrder) {
      adjacencyList.put(id, new HashSet<>());
      degree.put(id, 0);
    }

    Set<String> edges = new HashSet<>();
    int half = connectionRequirement / 2;

    for (int i = 0; i < N; i++) {
      String a = nodeOrder.get(i);
      for (int d = 1; d <= half; d++) {
        String b = nodeOrder.get((i + d) % N);
        if (!adjacencyList.get(a).contains(b)) {
          addEdge(a, b, adjacencyList, degree, edges);
        }
      }
    }

    if ((connectionRequirement % 2) == 1 && (N % 2) == 0) {
      int antipode = N / 2;
      for (int i = 0; i < antipode; i++) {
        String a = nodeOrder.get(i);
        String b = nodeOrder.get(i + antipode);
        if (!adjacencyList.get(a).contains(b)) {
          addEdge(a, b, adjacencyList, degree, edges);
        }
      }
    }

    final int targetEdges = (N * connectionRequirement) / 2;
    if (edges.size() != targetEdges) {
      List<String> ids = new ArrayList<>(nodeOrder);
      boolean progressed = true;
      while (edges.size() < targetEdges && progressed) {
        progressed = false;
        ids.sort(Comparator.comparingInt(degree::get));
        outer:
        for (int x = 0; x < ids.size(); x++) {
          String a = ids.get(x);
          if (degree.get(a) >= connectionRequirement) continue;
          for (int y = x + 1; y < ids.size(); y++) {
            String b = ids.get(y);
            if (a.equals(b)) continue;
            if (degree.get(b) >= connectionRequirement) continue;
            if (adjacencyList.get(a).contains(b)) continue;
            addEdge(a, b, adjacencyList, degree, edges);
            progressed = true;
            if (edges.size() >= targetEdges) break outer;
            break;
          }
        }
      }
    }

    if (!isFullyConnected()) {}

    Map<String, List<String>> dialerMap = new HashMap<>();
    for (String id : nodeOrder) dialerMap.put(id, new ArrayList<>());

    for (String a : nodeOrder) {
      for (String b : adjacencyList.get(a)) {
        if (a.compareTo(b) < 0) {
          String dialer = (new Random().nextBoolean()) ? a : b;
          String peer = dialer.equals(a) ? b : a;
          dialerMap.get(dialer).add(peer);
        }
      }
    }

    sendMessagingNodeLists(dialerMap, outputStreams);

    System.out.println("setup completed with " + targetEdges + " connections");
  }

  public void sendLinkWeights(Map<String, DataOutputStream> outputStreams) {
    if (nodeOrder.isEmpty()) {
      System.out.println("link weights assigned");
      return;
    }

    weightedLinks.clear();
    Random random = new Random();

    for (String nodeA : nodeOrder) {
      for (String nodeB : adjacencyList.get(nodeA)) {
        if (nodeA.compareTo(nodeB) < 0) {
          int weight = random.nextInt(10) + 1;
          weightedLinks.add(new LinkWeights.Link(nodeA, nodeB, weight));
        }
      }
    }

    LinkWeights linkWeights = new LinkWeights(weightedLinks);
    for (String nodeId : nodeOrder) {
      DataOutputStream outputStream = outputStreams.get(nodeId);
      if (outputStream != null) {
        try {
          synchronized (outputStream) {
            linkWeights.write(outputStream);
            outputStream.flush();
          }
        } catch (IOException ignored) {
        }
      }
    }
    System.out.println("link weights assigned");
  }

  public Map<String, Set<String>> getAdjacencyList() {
    return new HashMap<>(adjacencyList);
  }

  public List<String> getNodeOrder() {
    return new ArrayList<>(nodeOrder);
  }

  public List<LinkWeights.Link> getWeightedLinks() {
    return new ArrayList<>(weightedLinks);
  }

  private void addEdge(
      String nodeA,
      String nodeB,
      Map<String, Set<String>> adjacency,
      Map<String, Integer> degrees,
      Set<String> edges) {

    String edgeKey = nodeA.compareTo(nodeB) < 0 ? nodeA + "|" + nodeB : nodeB + "|" + nodeA;
    if (edges.contains(edgeKey)) {
      return;
    }

    adjacency.get(nodeA).add(nodeB);
    adjacency.get(nodeB).add(nodeA);
    degrees.put(nodeA, degrees.get(nodeA) + 1);
    degrees.put(nodeB, degrees.get(nodeB) + 1);
    edges.add(edgeKey);
  }

  private void sendMessagingNodeLists(
      Map<String, List<String>> dialerMap, Map<String, DataOutputStream> outputStreams) {

    dialerMap.forEach(
        (nodeId, peers) -> {
          DataOutputStream outputStream = outputStreams.get(nodeId);
          if (outputStream != null) {
            try {
              synchronized (outputStream) {
                new MessagingNodeList(peers).write(outputStream);
                outputStream.flush();
              }
            } catch (IOException ignored) {
            }
          }
        });
  }

  private boolean isFullyConnected() {
    if (nodeOrder.isEmpty()) {
      return true;
    }

    Set<String> visited = new HashSet<>();
    Queue<String> queue = new LinkedList<>();

    String startNode = nodeOrder.get(0);
    queue.add(startNode);
    visited.add(startNode);

    while (!queue.isEmpty()) {
      String current = queue.poll();
      for (String neighbor : adjacencyList.get(current)) {
        if (!visited.contains(neighbor)) {
          visited.add(neighbor);
          queue.add(neighbor);
        }
      }
    }

    return visited.size() == nodeOrder.size();
  }
}
