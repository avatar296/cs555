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
      // Don't return the target itself as next hop
      if (!nextHop.getId().equals(key)) {
        return nextHop;
      }
    }

    return findCloserNode(key);
  }

  public boolean isClosestNode(String targetId) {
    int selfPrefixLength = getCommonPrefixLength(selfId, targetId);
    long selfDist = computeDistance(selfId, targetId);

    // Check leaf set - prefix matching takes priority over distance
    for (NodeInfo node : leafSet.getAllNodes()) {
      int nodePrefixLength = getCommonPrefixLength(node.getId(), targetId);

      // If node has longer prefix match, it's closer (Pastry DHT spec)
      if (nodePrefixLength > selfPrefixLength) {
        return false;
      }

      // Only compare distance if prefix lengths are equal
      if (nodePrefixLength == selfPrefixLength) {
        long dist = computeDistance(node.getId(), targetId);
        if (dist < selfDist) {
          return false;
        } else if (dist == selfDist) {
          // Tie-breaking: prefer higher identifier
          if (compareIds(node.getId(), selfId) > 0) {
            return false;
          }
        }
      }
      // If nodePrefixLength < selfPrefixLength, self is better, continue
    }

    // Check routing table - same prefix-first logic
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          int nodePrefixLength = getCommonPrefixLength(node.getId(), targetId);

          // If node has longer prefix match, it's closer
          if (nodePrefixLength > selfPrefixLength) {
            return false;
          }

          // Only compare distance if prefix lengths are equal
          if (nodePrefixLength == selfPrefixLength) {
            long dist = computeDistance(node.getId(), targetId);
            if (dist < selfDist) {
              return false;
            } else if (dist == selfDist) {
              if (compareIds(node.getId(), selfId) > 0) {
                return false;
              }
            }
          }
        }
      }
    }

    return true; // closest node
  }

  private int compareIds(String id1, String id2) {
    int val1 = Integer.parseInt(id1, 16);
    int val2 = Integer.parseInt(id2, 16);
    return Integer.compare(val1, val2);
  }

  private NodeInfo findCloserNode(String key) {
    int selfPrefixLength = getCommonPrefixLength(selfId, key);
    long selfDist = computeDistance(selfId, key);

    NodeInfo closest = null;
    int bestPrefixLength = selfPrefixLength;
    long minDist = selfDist;

    // Check leaf set - prefix matching takes priority
    for (NodeInfo node : leafSet.getAllNodes()) {
      // Skip if this node IS the key we're looking for
      if (node.getId().equals(key)) {
        continue;
      }

      int nodePrefixLength = getCommonPrefixLength(node.getId(), key);
      long dist = computeDistance(node.getId(), key);

      // Prefer longer prefix match (Pastry DHT spec)
      if (nodePrefixLength > bestPrefixLength) {
        bestPrefixLength = nodePrefixLength;
        minDist = dist;
        closest = node;
      }
      // If prefix lengths equal, prefer shorter distance
      else if (nodePrefixLength == bestPrefixLength) {
        if (dist < minDist) {
          minDist = dist;
          closest = node;
        } else if (dist == minDist && closest != null) {
          if (compareIds(node.getId(), closest.getId()) > 0) {
            closest = node;
          }
        }
      }
      // If nodePrefixLength < bestPrefixLength, skip (current best is better)
    }

    // Check routing table - same prefix-first logic
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          // Skip if this node IS the key we're looking for
          if (node.getId().equals(key)) {
            continue;
          }

          int nodePrefixLength = getCommonPrefixLength(node.getId(), key);
          long dist = computeDistance(node.getId(), key);

          // Prefer longer prefix match
          if (nodePrefixLength > bestPrefixLength) {
            bestPrefixLength = nodePrefixLength;
            minDist = dist;
            closest = node;
          }
          // If prefix lengths equal, prefer shorter distance
          else if (nodePrefixLength == bestPrefixLength) {
            if (dist < minDist) {
              minDist = dist;
              closest = node;
            } else if (dist == minDist && closest != null) {
              if (compareIds(node.getId(), closest.getId()) > 0) {
                closest = node;
              }
            }
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
