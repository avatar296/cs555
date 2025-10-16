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

    long localVal = Long.parseLong(localId, 16);
    long nodeVal = Long.parseLong(node.getId(), 16);

    // Update right neighbor (next in increasing ID order, with wraparound)
    if (nodeVal > localVal) {
      // Node is numerically greater - normal right neighbor candidate
      if (right == null) {
        right = node;
      } else {
        long rightVal = Long.parseLong(right.getId(), 16);
        if (rightVal < localVal) {
          // Current right has wrapped around (< localVal), replace with non-wrapped
          right = node;
        } else if (nodeVal < rightVal) {
          // Both non-wrapped, keep closer (smaller) one
          right = node;
        }
      }
    } else {
      // nodeVal < localVal - wrapped right neighbor candidate
      if (right == null) {
        right = node;
      } else {
        long rightVal = Long.parseLong(right.getId(), 16);
        if (rightVal > localVal) {
          // Current right is non-wrapped, don't replace with wrapped candidate
        } else if (nodeVal < rightVal) {
          // Both wrapped, keep smaller (closer when wrapping) one
          right = node;
        }
      }
    }

    // Update left neighbor (next in decreasing ID order, with wraparound)
    if (nodeVal < localVal) {
      // Node is numerically smaller - normal left neighbor candidate
      if (left == null) {
        left = node;
      } else {
        long leftVal = Long.parseLong(left.getId(), 16);
        if (leftVal > localVal) {
          // Current left has wrapped around (> localVal), replace with non-wrapped
          left = node;
        } else if (nodeVal > leftVal) {
          // Both non-wrapped, keep closer (larger) one
          left = node;
        }
      }
    } else {
      // nodeVal > localVal - wrapped left neighbor candidate
      if (left == null) {
        left = node;
      } else {
        long leftVal = Long.parseLong(left.getId(), 16);
        if (leftVal < localVal) {
          // Current left is non-wrapped, don't replace with wrapped candidate
        } else if (nodeVal > leftVal) {
          // Both wrapped, keep larger (closer when wrapping) one
          left = node;
        }
      }
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
      long distance = csx55.pastry.node.peer.RoutingEngine.computeDistance(key, node.getId());
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

    nodes.sort((a, b) -> compareIds(a.getId(), b.getId()));

    StringBuilder sb = new StringBuilder();
    for (NodeInfo node : nodes) {
      sb.append(node.toOutputFormat()).append("\n");
    }
    return sb.toString().trim();
  }

  private int compareIds(String id1, String id2) {
    int val1 = Integer.parseInt(id1, 16);
    int val2 = Integer.parseInt(id2, 16);
    return Integer.compare(val1, val2);
  }
}
