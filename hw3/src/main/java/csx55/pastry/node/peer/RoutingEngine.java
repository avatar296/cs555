package csx55.pastry.node.peer;

import csx55.pastry.routing.LeafSet;
import csx55.pastry.routing.RoutingTable;
import csx55.pastry.util.NodeInfo;

public class RoutingEngine {
  private final String selfId;
  private final LeafSet leafSet;
  private final RoutingTable routingTable;

  public RoutingEngine(String selfId, LeafSet leafSet, RoutingTable routingTable) {
    this.selfId = selfId;
    this.leafSet = leafSet;
    this.routingTable = routingTable;
  }

  public NodeInfo route(String key) {
    if (leafSet.isInRange(key)) {
      NodeInfo closest = leafSet.getClosestNode(key);
      if (closest != null) {
        return closest;
      }
    }

    int prefixLength = getCommonPrefixLength(selfId, key);

    if (prefixLength >= 4) {
      return null;
    }

    int nextDigit = Character.digit(key.charAt(prefixLength), 16);

    NodeInfo nextHop = routingTable.getEntry(prefixLength, nextDigit);
    if (nextHop != null) {
      return nextHop;
    }

    return findCloserNode(key);
  }

  public boolean isClosestNode(String targetId) {
    long selfDist = computeDistance(selfId, targetId);

    for (NodeInfo node : leafSet.getAllNodes()) {
      long dist = computeDistance(node.getId(), targetId);
      if (dist < selfDist) {
        return false;
      }
    }

    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          long dist = computeDistance(node.getId(), targetId);
          if (dist < selfDist) {
            return false;
          }
        }
      }
    }

    return true; // closest node
  }

  private NodeInfo findCloserNode(String key) {
    long selfDist = computeDistance(selfId, key);
    NodeInfo closest = null;
    long minDist = selfDist;

    for (NodeInfo node : leafSet.getAllNodes()) {
      long dist = computeDistance(node.getId(), key);
      if (dist < minDist) {
        minDist = dist;
        closest = node;
      }
    }

    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          long dist = computeDistance(node.getId(), key);
          if (dist < minDist) {
            minDist = dist;
            closest = node;
          }
        }
      }
    }

    return closest;
  }

  public static int getCommonPrefixLength(String id1, String id2) {
    int matches = 0;
    int minLen = Math.min(id1.length(), id2.length());

    for (int i = 0; i < minLen; i++) {
      if (id1.charAt(i) == id2.charAt(i)) {
        matches++;
      } else {
        break;
      }
    }

    return matches;
  }

  public static long computeDistance(String id1, String id2) {
    long val1 = Long.parseLong(id1, 16);
    long val2 = Long.parseLong(id2, 16);
    long diff = Math.abs(val1 - val2);
    long wrapDiff = 0x10000 - diff; // 2^16 - diff
    return Math.min(diff, wrapDiff);
  }
}
