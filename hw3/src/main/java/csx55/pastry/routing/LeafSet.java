package csx55.pastry.routing;

import csx55.pastry.util.NodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class LeafSet {
  private static final Logger logger = Logger.getLogger(LeafSet.class.getName());
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

    long clockwiseDistance;
    if (nodeVal >= localVal) {
      clockwiseDistance = nodeVal - localVal;
    } else {
      clockwiseDistance = (0x10000 - localVal) + nodeVal;
    }

    long counterClockwiseDistance = 0x10000 - clockwiseDistance;

    boolean isRightNeighbor;
    if (clockwiseDistance == counterClockwiseDistance) {
      // Node is exactly opposite (0x8000 away)
      if (left == null && right != null) {
        isRightNeighbor = false; // Fill empty LEFT slot
      } else if (right == null && left != null) {
        isRightNeighbor = true; // Fill empty RIGHT slot
      } else {
        isRightNeighbor = true;
      }
    } else {
      isRightNeighbor = clockwiseDistance < counterClockwiseDistance;
    }

    logger.info(
        String.format(
            "[%s] Adding node %s: CW=%d, CCW=%d, isRight=%b",
            localId, node.getId(), clockwiseDistance, counterClockwiseDistance, isRightNeighbor));

    if (isRightNeighbor) {
      if (right == null) {
        logger.info(String.format("[%s] Setting RIGHT to %s (was null)", localId, node.getId()));
        right = node;
      } else {
        long rightVal = Long.parseLong(right.getId(), 16);
        long currentClockwise;
        if (rightVal >= localVal) {
          currentClockwise = rightVal - localVal;
        } else {
          currentClockwise = (0x10000 - localVal) + rightVal;
        }

        logger.info(
            String.format(
                "[%s] Comparing RIGHT: current=%s (CW=%d) vs new=%s (CW=%d)",
                localId, right.getId(), currentClockwise, node.getId(), clockwiseDistance));

        if (clockwiseDistance < currentClockwise) {
          logger.info(
              String.format(
                  "[%s] REPLACING RIGHT: %s -> %s", localId, right.getId(), node.getId()));
          right = node;
        } else if (clockwiseDistance == currentClockwise) {
          if (compareIds(node.getId(), right.getId()) > 0) {
            logger.info(
                String.format(
                    "[%s] TIE-BREAK RIGHT: %s -> %s (higher ID)",
                    localId, right.getId(), node.getId()));
            right = node;
          } else {
            logger.info(String.format("[%s] KEEPING RIGHT: %s", localId, right.getId()));
          }
        } else {
          logger.info(String.format("[%s] KEEPING RIGHT: %s", localId, right.getId()));
        }
      }
    } else {
      if (left == null) {
        logger.info(String.format("[%s] Setting LEFT to %s (was null)", localId, node.getId()));
        left = node;
      } else {
        long leftVal = Long.parseLong(left.getId(), 16);
        long currentCounterClockwise;
        if (leftVal <= localVal) {
          currentCounterClockwise = localVal - leftVal;
        } else {
          currentCounterClockwise = localVal + (0x10000 - leftVal);
        }

        logger.info(
            String.format(
                "[%s] Comparing LEFT: current=%s (CCW=%d) vs new=%s (CCW=%d)",
                localId,
                left.getId(),
                currentCounterClockwise,
                node.getId(),
                counterClockwiseDistance));

        if (counterClockwiseDistance < currentCounterClockwise) {
          logger.info(
              String.format("[%s] REPLACING LEFT: %s -> %s", localId, left.getId(), node.getId()));
          left = node;
        } else if (counterClockwiseDistance == currentCounterClockwise) {
          // Tie-breaking: prefer higher identifier
          if (compareIds(node.getId(), left.getId()) > 0) {
            logger.info(
                String.format(
                    "[%s] TIE-BREAK LEFT: %s -> %s (higher ID)",
                    localId, left.getId(), node.getId()));
            left = node;
          } else {
            logger.info(String.format("[%s] KEEPING LEFT: %s", localId, left.getId()));
          }
        } else {
          logger.info(String.format("[%s] KEEPING LEFT: %s", localId, left.getId()));
        }
      }
    }

    logger.info(
        String.format(
            "[%s] FINAL STATE after adding %s: LEFT=%s, RIGHT=%s",
            localId,
            node.getId(),
            left != null ? left.getId() : "null",
            right != null ? right.getId() : "null"));
  }

  public synchronized NodeInfo getLeft() {
    return left;
  }

  public synchronized NodeInfo getRight() {
    return right;
  }

  public synchronized void removeNode(String nodeId, RoutingTable routingTable) {
    logger.info(
        String.format(
            "[%s] BEFORE removeNode(%s): LEFT=%s, RIGHT=%s",
            localId,
            nodeId,
            left != null ? left.getId() : "null",
            right != null ? right.getId() : "null"));

    boolean leftRemoved = false;
    boolean rightRemoved = false;

    if (left != null && left.getId().equals(nodeId)) {
      logger.info(String.format("[%s] Removing LEFT neighbor: %s", localId, nodeId));
      left = null;
      leftRemoved = true;
    }
    if (right != null && right.getId().equals(nodeId)) {
      logger.info(String.format("[%s] Removing RIGHT neighbor: %s", localId, nodeId));
      right = null;
      rightRemoved = true;
    }

    if (leftRemoved || rightRemoved) {
      logger.info(
          String.format("[%s] Calling findReplacements() after removing %s", localId, nodeId));
      findReplacements(routingTable);
    }

    logger.info(
        String.format(
            "[%s] AFTER removeNode(%s): LEFT=%s, RIGHT=%s",
            localId,
            nodeId,
            left != null ? left.getId() : "null",
            right != null ? right.getId() : "null"));
  }

  public synchronized void removeNodeWithoutReplacement(String nodeId) {
    logger.info(
        String.format(
            "[%s] BEFORE removeNodeWithoutReplacement(%s): LEFT=%s, RIGHT=%s",
            localId,
            nodeId,
            left != null ? left.getId() : "null",
            right != null ? right.getId() : "null"));

    if (left != null && left.getId().equals(nodeId)) {
      logger.info(
          String.format("[%s] Removing LEFT neighbor (no auto-replacement): %s", localId, nodeId));
      left = null;
    }
    if (right != null && right.getId().equals(nodeId)) {
      logger.info(
          String.format("[%s] Removing RIGHT neighbor (no auto-replacement): %s", localId, nodeId));
      right = null;
    }

    logger.info(
        String.format(
            "[%s] AFTER removeNodeWithoutReplacement(%s): LEFT=%s, RIGHT=%s",
            localId,
            nodeId,
            left != null ? left.getId() : "null",
            right != null ? right.getId() : "null"));
  }

  public synchronized void findReplacementsIfNeeded(RoutingTable routingTable) {
    if (left == null || right == null) {
      logger.info(String.format("[%s] Finding replacements for vacant leaf slots", localId));
      findReplacements(routingTable);
    }
  }

  private void findReplacements(RoutingTable routingTable) {
    logger.info(String.format("[%s] findReplacements() scanning routing table...", localId));

    int candidatesFound = 0;
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          logger.info(
              String.format(
                  "[%s] findReplacements() found candidate: %s at [%d][%d]",
                  localId, node.getId(), row, col));
          candidatesFound++;
          addNode(node);
        }
      }
    }

    logger.info(
        String.format(
            "[%s] findReplacements() complete: processed %d candidates", localId, candidatesFound));
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

    long keyVal = Long.parseLong(key, 16);
    long localVal = Long.parseLong(localId, 16);

    if (left == null) {
      long rightVal = Long.parseLong(right.getId(), 16);
      long distKeyToLocal = computeCircularDistance(keyVal, localVal);
      long distKeyToRight = computeCircularDistance(keyVal, rightVal);

      return distKeyToLocal < distKeyToRight;
    }

    if (right == null) {
      long leftVal = Long.parseLong(left.getId(), 16);
      long distKeyToLocal = computeCircularDistance(keyVal, localVal);
      long distKeyToLeft = computeCircularDistance(keyVal, leftVal);

      return distKeyToLocal < distKeyToLeft;
    }

    long leftVal = Long.parseLong(left.getId(), 16);
    long rightVal = Long.parseLong(right.getId(), 16);

    long distKeyToLocal = computeCircularDistance(keyVal, localVal);
    long distKeyToLeft = computeCircularDistance(keyVal, leftVal);
    long distKeyToRight = computeCircularDistance(keyVal, rightVal);

    return distKeyToLocal < distKeyToLeft && distKeyToLocal < distKeyToRight;
  }

  private long computeCircularDistance(long val1, long val2) {
    long diff = Math.abs(val1 - val2);
    long wrapDiff = 0x10000 - diff;
    return Math.min(diff, wrapDiff);
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
      } else if (distance == minDistance && closest != null) {
        if (compareIds(node.getId(), closest.getId()) > 0) {
          closest = node;
        }
      }
    }

    return closest != null && closest.getId().equals(localId) ? null : closest;
  }

  public synchronized String toOutputFormat() {
    StringBuilder sb = new StringBuilder();

    if (left != null) {
      sb.append(left.toOutputFormat()).append("\n");
    }

    if (right != null) {
      sb.append(right.toOutputFormat()).append("\n");
    }

    return sb.toString().trim();
  }

  private int compareIds(String id1, String id2) {
    int val1 = Integer.parseInt(id1, 16);
    int val2 = Integer.parseInt(id2, 16);
    return Integer.compare(val1, val2);
  }
}
