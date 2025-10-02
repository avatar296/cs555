package csx55.pastry.routing;

import csx55.pastry.util.NodeInfo;

public class RoutingTable {
  private static final int ROWS = 4; // 16-bit ID = 4 hex digits
  private static final int COLS = 16; // 0-F

  private final String localId;
  private final NodeInfo[][] table;

  public RoutingTable(String localId) {
    this.localId = localId;
    this.table = new NodeInfo[ROWS][COLS];
  }

  public synchronized void setEntry(int row, int col, NodeInfo node) {
    if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
      return;
    }

    // Don't add entries that match local ID
    if (node != null && node.getId().equals(localId)) {
      return;
    }

    table[row][col] = node;
  }

  public synchronized NodeInfo getEntry(int row, int col) {
    if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
      return null;
    }
    return table[row][col];
  }

  public synchronized NodeInfo[] getRow(int row) {
    if (row < 0 || row >= ROWS) {
      return new NodeInfo[0];
    }
    NodeInfo[] rowCopy = new NodeInfo[COLS];
    System.arraycopy(table[row], 0, rowCopy, 0, COLS);
    return rowCopy;
  }

  public synchronized void setRow(int row, NodeInfo[] entries) {
    if (row < 0 || row >= ROWS || entries == null) {
      return;
    }

    for (int col = 0; col < COLS && col < entries.length; col++) {
      setEntry(row, col, entries[col]);
    }
  }

  public synchronized NodeInfo route(String key) {
    // Find longest common prefix with key
    int matchingDigits = getMatchingPrefixLength(localId, key);

    if (matchingDigits >= ROWS) {
      return null; // Key matches local ID completely
    }

    // Get the next digit in key
    int nextDigit = Character.digit(key.charAt(matchingDigits), 16);

    // Look up in routing table
    NodeInfo nextHop = getEntry(matchingDigits, nextDigit);
    return nextHop;
  }

  public synchronized void addNode(NodeInfo node) {
    if (node == null || node.getId().equals(localId)) {
      return;
    }

    String nodeId = node.getId();
    int matchingDigits = getMatchingPrefixLength(localId, nodeId);

    if (matchingDigits < ROWS) {
      int nextDigit = Character.digit(nodeId.charAt(matchingDigits), 16);
      setEntry(matchingDigits, nextDigit, node);
    }
  }

  public synchronized void removeNode(String nodeId) {
    for (int row = 0; row < ROWS; row++) {
      for (int col = 0; col < COLS; col++) {
        if (table[row][col] != null && table[row][col].getId().equals(nodeId)) {
          table[row][col] = null;
        }
      }
    }
  }

  public synchronized String toOutputFormat() {
    StringBuilder sb = new StringBuilder();

    for (int row = 0; row < ROWS; row++) {
      for (int col = 0; col < COLS; col++) {
        NodeInfo node = table[row][col];

        if (node != null) {
          sb.append(node.getId()).append("-").append(node.getAddress());
        }

        if (col < COLS - 1) {
          sb.append(", ");
        }
      }
      if (row < ROWS - 1) {
        sb.append("\n");
      }
    }

    return sb.toString();
  }

  private int getMatchingPrefixLength(String id1, String id2) {
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

  @Override
  public synchronized String toString() {
    int nonEmpty = 0;
    for (int row = 0; row < ROWS; row++) {
      for (int col = 0; col < COLS; col++) {
        if (table[row][col] != null) {
          nonEmpty++;
        }
      }
    }
    return "RoutingTable{entries=" + nonEmpty + "}";
  }
}
