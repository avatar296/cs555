package csx55.pastry.routing;

import csx55.pastry.util.NodeInfo;
import java.util.ArrayList;
import java.util.List;

public class LeafSet {
  private final String localId;
  private NodeInfo left;
  private NodeInfo right;

  public LeafSet(String localId) {
    this.localId = localId;
    this.left = null;
    this.right = null;
  }

  public synchronized void addNode(NodeInfo node) {
    if (node == null || node.getId().equals(localId)) {
      return;
    }

    int comparison = compareIds(node.getId(), localId);

    if (comparison < 0) {
      // Node is to the left (smaller ID)
      // Keep the one closest to us (largest among those smaller)
      if (left == null || compareIds(node.getId(), left.getId()) > 0) {
        left = node;
      }
    } else if (comparison > 0) {
      // Node is to the right (larger ID)
      // Keep the one closest to us (smallest among those larger)
      if (right == null || compareIds(node.getId(), right.getId()) < 0) {
        right = node;
      }
    }
  }

  public synchronized void removeNode(String nodeId) {
    if (left != null && left.getId().equals(nodeId)) {
      left = null;
    }
    if (right != null && right.getId().equals(nodeId)) {
      right = null;
    }
  }

  public synchronized NodeInfo getLeft() {
    return left;
  }

  public synchronized NodeInfo getRight() {
    return right;
  }

  public synchronized List<NodeInfo> getAllNodes() {
    List<NodeInfo> nodes = new ArrayList<>();
    if (left != null) {
      nodes.add(left);
    }
    if (right != null) {
      nodes.add(right);
    }
    return nodes;
  }

  public synchronized boolean contains(String nodeId) {
    if (left != null && left.getId().equals(nodeId)) {
      return true;
    }
    if (right != null && right.getId().equals(nodeId)) {
      return true;
    }
    return false;
  }

  public synchronized boolean isInRange(String key) {
    if (left == null && right == null) {
      return true;
    }

    if (left == null) {
      return compareIds(key, localId) <= 0 && compareIds(key, right.getId()) < 0;
    }

    if (right == null) {
      return compareIds(key, localId) >= 0 && compareIds(key, left.getId()) > 0;
    }

    // Check if key is between left and right (inclusive of local)
    String minId = left.getId();
    String maxId = right.getId();

    return compareIds(key, minId) > 0 && compareIds(key, maxId) < 0;
  }

  public synchronized NodeInfo getClosestNode(String key) {
    List<NodeInfo> candidates = new ArrayList<>();
    candidates.add(new NodeInfo(localId, "", 0));
    if (left != null) {
      candidates.add(left);
    }
    if (right != null) {
      candidates.add(right);
    }

    NodeInfo closest = null;
    long minDistance = Long.MAX_VALUE;

    for (NodeInfo node : candidates) {
      long distance = computeDistance(key, node.getId());
      if (distance < minDistance) {
        minDistance = distance;
        closest = node;
      }
    }

    return closest != null && closest.getId().equals(localId) ? null : closest;
  }

  public synchronized String toOutputFormat() {
    List<NodeInfo> nodes = getAllNodes();
    if (nodes.isEmpty()) {
      return "";
    }

    // Sort by ID
    nodes.sort((a, b) -> compareIds(a.getId(), b.getId()));

    StringBuilder sb = new StringBuilder();
    for (NodeInfo node : nodes) {
      sb.append(node.toOutputFormat()).append("\n");
    }
    return sb.toString().trim();
  }

  // Compare two hex IDs numerically
  private int compareIds(String id1, String id2) {
    int val1 = Integer.parseInt(id1, 16);
    int val2 = Integer.parseInt(id2, 16);
    return Integer.compare(val1, val2);
  }

  // Compute circular distance between two IDs
  private long computeDistance(String id1, String id2) {
    long val1 = Integer.parseInt(id1, 16);
    long val2 = Integer.parseInt(id2, 16);
    long diff = Math.abs(val1 - val2);
    long wrapDiff = 0x10000 - diff; // 2^16 - diff
    return Math.min(diff, wrapDiff);
  }

  @Override
  public synchronized String toString() {
    return "LeafSet{left="
        + (left != null ? left.getId() : "null")
        + ", right="
        + (right != null ? right.getId() : "null")
        + "}";
  }
}
