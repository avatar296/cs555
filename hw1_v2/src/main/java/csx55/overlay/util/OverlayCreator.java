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

    int nodeCount = registeredNodes.size();
    if (nodeCount == 0 || connectionRequirement < 0 || connectionRequirement >= nodeCount) {
      System.out.println("setup completed with " + connectionRequirement + " connections");
      return;
    }

    nodeOrder = new ArrayList<>(registeredNodes.keySet());
    Collections.sort(nodeOrder);
    adjacencyList.clear();

    Map<String, Integer> nodeDegrees = new HashMap<>();
    Set<String> edges = new HashSet<>();

    for (String nodeId : nodeOrder) {
      adjacencyList.put(nodeId, new HashSet<>());
      nodeDegrees.put(nodeId, 0);
    }

    for (int i = 0; i < nodeCount; i++) {
      addEdge(
          nodeOrder.get(i), nodeOrder.get((i + 1) % nodeCount), adjacencyList, nodeDegrees, edges);
    }

    Random random = new Random();
    List<String> needConnections = new ArrayList<>();
    int maxAttempts = 10000;
    int attempts = 0;

    while (attempts < maxAttempts) {
      needConnections.clear();
      for (String nodeId : nodeOrder) {
        if (nodeDegrees.get(nodeId) < connectionRequirement) {
          needConnections.add(nodeId);
        }
      }

      if (needConnections.isEmpty()) {
        break;
      }

      String nodeA = needConnections.get(random.nextInt(needConnections.size()));
      String nodeB = needConnections.get(random.nextInt(needConnections.size()));

      if (!nodeA.equals(nodeB) && !adjacencyList.get(nodeA).contains(nodeB)) {
        addEdge(nodeA, nodeB, adjacencyList, nodeDegrees, edges);
        attempts = 0;
      } else {
        attempts++;
      }
    }

    if (!isFullyConnected()) {
      System.err.println("Warning: Overlay is not fully connected. Attempting to fix...");

      for (int retries = 0; retries < 3; retries++) {
        for (int i = 0; i < nodeCount; i++) {
          for (int j = i + 1; j < nodeCount; j++) {
            String nodeA = nodeOrder.get(i);
            String nodeB = nodeOrder.get(j);
            if (!adjacencyList.get(nodeA).contains(nodeB)
                && nodeDegrees.get(nodeA) < connectionRequirement
                && nodeDegrees.get(nodeB) < connectionRequirement) {
              addEdge(nodeA, nodeB, adjacencyList, nodeDegrees, edges);
            }
          }
        }

        if (isFullyConnected()) {
          System.out.println("Overlay connectivity fixed successfully.");
          break;
        }
      }

      if (!isFullyConnected()) {
        System.err.println("Error: Unable to create a fully connected overlay after retries.");
      }
    }

    Map<String, List<String>> dialerMap = new HashMap<>();
    nodeOrder.forEach(id -> dialerMap.put(id, new ArrayList<>()));

    Random rand = new Random();
    for (String nodeA : nodeOrder) {
      for (String nodeB : adjacencyList.get(nodeA)) {
        if (nodeA.compareTo(nodeB) < 0) {
          String dialer = rand.nextBoolean() ? nodeA : nodeB;
          dialerMap.get(dialer).add(dialer.equals(nodeA) ? nodeB : nodeA);
        }
      }
    }

    sendMessagingNodeLists(dialerMap, outputStreams);
    System.out.println("setup completed with " + connectionRequirement + " connections");
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
          weightedLinks.add(new LinkWeights.Link(nodeA, nodeB, 1 + random.nextInt(10)));
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
