package csx55.overlay.util;

import csx55.overlay.wireformats.TrafficSummary;
import java.util.*;

/**
 * Collects and aggregates traffic statistics from all nodes in the overlay. Thread-safe
 * implementation for concurrent summary collection. Displays formatted statistics once all nodes
 * have reported.
 */
public class StatisticsCollector {

  /** Map of node IDs to their traffic summaries */
  private final Map<String, TrafficSummary> summaries;

  /** Number of nodes expected to report statistics */
  private final int expectedNodes;

  /**
   * Constructs a StatisticsCollector for the specified number of nodes.
   *
   * @param expectedNodes the number of nodes expected to report
   */
  public StatisticsCollector(int expectedNodes) {
    this.expectedNodes = expectedNodes;
    this.summaries = new HashMap<>();
  }

  /**
   * Adds a traffic summary from a node. Thread-safe method for concurrent summary collection.
   *
   * @param summary the traffic summary to add
   */
  public synchronized void addSummary(TrafficSummary summary) {
    summaries.put(summary.getNodeId(), summary);
  }

  /**
   * Checks if all expected summaries have been received.
   *
   * @return true if all nodes have reported, false otherwise
   */
  public synchronized boolean hasAllSummaries() {
    return summaries.size() == expectedNodes;
  }

  /** Resets the collector by clearing all summaries. */
  public synchronized void reset() {
    summaries.clear();
  }

  /**
   * Prints aggregated statistics for all nodes. Displays individual node statistics followed by
   * totals. Output format: nodeId sent received sumSent sumReceived relayed
   */
  public synchronized void printStatistics() {
    if (summaries.isEmpty()) {
      System.out.println("No statistics available.");
      return;
    }
    List<String> sortedNodeIds = new ArrayList<>(summaries.keySet());
    Collections.sort(sortedNodeIds);

    long totalSent = 0;
    long totalReceived = 0;
    long totalSumSent = 0;
    long totalSumReceived = 0;
    for (String nodeId : sortedNodeIds) {
      TrafficSummary summary = summaries.get(nodeId);

      System.out.printf(
          "%s %d %d %.2f %.2f %d%n",
          nodeId,
          summary.getMessagesSent(),
          summary.getMessagesReceived(),
          (double) summary.getSumSentMessages(),
          (double) summary.getSumReceivedMessages(),
          summary.getMessagesRelayed());

      totalSent += summary.getMessagesSent();
      totalReceived += summary.getMessagesReceived();
      totalSumSent += summary.getSumSentMessages();
      totalSumReceived += summary.getSumReceivedMessages();
    }

    System.out.printf(
        "sum %d %d %.2f %.2f%n",
        totalSent, totalReceived, (double) totalSumSent, (double) totalSumReceived);
  }

  /**
   * Gets a copy of all collected summaries.
   *
   * @return a new map containing all traffic summaries
   */
  public synchronized Map<String, TrafficSummary> getSummaries() {
    return new HashMap<>(summaries);
  }
}
