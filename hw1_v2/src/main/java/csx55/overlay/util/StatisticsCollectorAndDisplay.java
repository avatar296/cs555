package csx55.overlay.util;

import csx55.overlay.wireformats.TaskSummaryRequest;
import csx55.overlay.wireformats.TaskSummaryResponse;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StatisticsCollectorAndDisplay {

  public static class NodeStatistics {
    private String nodeId;
    private int messagesSent;
    private long sumMessagesSent;
    private int messagesReceived;
    private long sumMessagesReceived;
    private int messagesRelayed;

    public NodeStatistics() {}

    public NodeStatistics(
        String nodeId, int sent, long sentSum, int received, long receivedSum, int relayed) {
      this.nodeId = nodeId;
      this.messagesSent = sent;
      this.sumMessagesSent = sentSum;
      this.messagesReceived = received;
      this.sumMessagesReceived = receivedSum;
      this.messagesRelayed = relayed;
    }

    public String getNodeId() {
      return nodeId;
    }

    public void setNodeId(String nodeId) {
      this.nodeId = nodeId;
    }

    public int getMessagesSent() {
      return messagesSent;
    }

    public void setMessagesSent(int messagesSent) {
      this.messagesSent = messagesSent;
    }

    public long getSumMessagesSent() {
      return sumMessagesSent;
    }

    public void setSumMessagesSent(long sumMessagesSent) {
      this.sumMessagesSent = sumMessagesSent;
    }

    public int getMessagesReceived() {
      return messagesReceived;
    }

    public void setMessagesReceived(int messagesReceived) {
      this.messagesReceived = messagesReceived;
    }

    public long getSumMessagesReceived() {
      return sumMessagesReceived;
    }

    public void setSumMessagesReceived(long sumMessagesReceived) {
      this.sumMessagesReceived = sumMessagesReceived;
    }

    public int getMessagesRelayed() {
      return messagesRelayed;
    }

    public void setMessagesRelayed(int messagesRelayed) {
      this.messagesRelayed = messagesRelayed;
    }
  }

  public static class StatisticsRun {
    private final Set<String> expectedNodes;
    private final int rounds;
    private final Set<String> completedNodes;
    private final Queue<NodeStatistics> statistics;

    public StatisticsRun(Set<String> expectedNodes, int rounds) {
      this.expectedNodes = expectedNodes;
      this.rounds = rounds;
      this.completedNodes = Collections.synchronizedSet(new HashSet<>());
      this.statistics = new ConcurrentLinkedQueue<>();
    }

    public Set<String> getExpectedNodes() {
      return expectedNodes;
    }

    public int getRounds() {
      return rounds;
    }

    public Set<String> getCompletedNodes() {
      return completedNodes;
    }

    public Queue<NodeStatistics> getStatistics() {
      return statistics;
    }

    public void markNodeCompleted(String nodeId) {
      completedNodes.add(nodeId);
    }

    public void addStatistics(NodeStatistics stats) {
      statistics.add(stats);
    }

    public boolean isComplete() {
      return completedNodes.size() >= expectedNodes.size();
    }
  }

  public static void requestTrafficSummaries(
      Set<String> nodeIds, Map<String, DataOutputStream> outputStreams) {

    TaskSummaryRequest request = new TaskSummaryRequest();

    for (String nodeId : nodeIds) {
      DataOutputStream outputStream = outputStreams.get(nodeId);
      if (outputStream != null) {
        try {
          synchronized (outputStream) {
            request.write(outputStream);
            outputStream.flush();
          }
        } catch (IOException ignored) {
        }
      }
    }
  }

  public static NodeStatistics processTrafficSummary(TaskSummaryResponse response) {
    return new NodeStatistics(
        response.ip() + ":" + response.port(),
        response.sentCount(),
        response.sentSum(),
        response.recvCount(),
        response.recvSum(),
        response.relayedCount());
  }

  public static void displayStatistics(StatisticsRun run) {
    List<NodeStatistics> sortedStats = new ArrayList<>(run.getStatistics());
    sortedStats.sort(Comparator.comparing(NodeStatistics::getNodeId));

    long totalSent = 0;
    long totalReceived = 0;
    long totalSumSent = 0;
    long totalSumReceived = 0;

    for (NodeStatistics stats : sortedStats) {
      totalSent += stats.getMessagesSent();
      totalReceived += stats.getMessagesReceived();
      totalSumSent += stats.getSumMessagesSent();
      totalSumReceived += stats.getSumMessagesReceived();

      System.out.println(
          stats.getNodeId()
              + " "
              + stats.getMessagesSent()
              + " "
              + stats.getMessagesReceived()
              + " "
              + stats.getSumMessagesSent()
              + " "
              + stats.getSumMessagesReceived()
              + " "
              + stats.getMessagesRelayed());
    }

    System.out.println(
        "sum " + totalSent + " " + totalReceived + " " + totalSumSent + " " + totalSumReceived);

    System.out.println(run.getRounds() + " rounds completed");
  }

  public static boolean waitForCompletion(StatisticsRun run, long timeoutMillis) {
    long startTime = System.currentTimeMillis();

    while (!run.isComplete()) {
      if (System.currentTimeMillis() - startTime > timeoutMillis) {
        return false;
      }

      try {
        Thread.sleep(100);
      } catch (InterruptedException ignored) {
      }
    }

    return true;
  }

  public static void collectAndDisplayStatistics(
      StatisticsRun run, Map<String, DataOutputStream> outputStreams, long delayBeforeCollection) {

    waitForCompletion(run, 60000);

    try {
      Thread.sleep(delayBeforeCollection);
    } catch (InterruptedException ignored) {
    }
    requestTrafficSummaries(run.getExpectedNodes(), outputStreams);

    try {
      Thread.sleep(500);
    } catch (InterruptedException ignored) {
    }

    displayStatistics(run);
  }
}
