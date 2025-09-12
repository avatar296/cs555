package csx55.threads.balance;

import csx55.threads.core.OverlayState;
import csx55.threads.core.Stats;
import csx55.threads.core.Task;
import csx55.threads.core.TaskQueue;
import csx55.threads.util.NetworkUtil;
import csx55.threads.util.Protocol;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LoadBalancer {
  private final TaskQueue taskQueue;
  private final Stats stats;
  private final OverlayState state;
  private final String myId;

  private final int pushThreshold;
  private final int pullThreshold;
  private final int minBatchSize;

  public LoadBalancer(
      String myId,
      TaskQueue taskQueue,
      Stats stats,
      OverlayState state,
      int pushThreshold,
      int pullThreshold,
      int minBatchSize) {
    this.myId = myId;
    this.taskQueue = taskQueue;
    this.stats = stats;
    this.state = state;
    this.pushThreshold = pushThreshold;
    this.pullThreshold = pullThreshold;
    this.minBatchSize = minBatchSize;
  }

  public void balanceThresholdBased() {
    int myOutstanding = taskQueue.size();
    String successor = state.getSuccessor();
    if (myOutstanding > pushThreshold && successor != null) {
      int migrateCount = Math.max(minBatchSize, myOutstanding / 2);
      List<Task> raw = taskQueue.removeBatch(migrateCount);
      if (raw == null || raw.isEmpty()) return;

      List<Task> toSend = new ArrayList<>();
      List<Task> keep = new ArrayList<>();
      for (Task t : raw) {
        if (t.isMigrated()) keep.add(t);
        else {
          t.markMigrated();
          toSend.add(t);
        }
      }
      if (!keep.isEmpty()) taskQueue.addBatch(keep);
      if (toSend.isEmpty()) return;

      try {
        NetworkUtil.sendTasks(successor, toSend);
        stats.incrementPushed(toSend.size());
      } catch (IOException e) {
        taskQueue.addBatch(toSend);
      }
    }
  }

  public void rebalanceTowardsTarget(int targetQueue) {
    int currentOutstanding = taskQueue.size();
    String successor = state.getSuccessor();
    String predecessor = state.getPredecessor();

    if (successor != null && currentOutstanding > targetQueue) {
      int excess = currentOutstanding - targetQueue;
      int migrateCount = Math.max(minBatchSize, excess);
      List<Task> raw = taskQueue.removeBatch(migrateCount);
      if (raw != null && !raw.isEmpty()) {
        List<Task> toSend = new ArrayList<>();
        List<Task> keep = new ArrayList<>();
        for (Task t : raw) {
          if (t.isMigrated()) keep.add(t);
          else {
            t.markMigrated();
            toSend.add(t);
          }
        }
        if (!keep.isEmpty()) taskQueue.addBatch(keep);
        if (!toSend.isEmpty()) {
          try {
            NetworkUtil.sendTasks(successor, toSend);
            stats.incrementPushed(toSend.size());
          } catch (IOException e) {
            taskQueue.addBatch(toSend);
          }
        }
      }
    } else if (predecessor != null && currentOutstanding < targetQueue) {
      int deficit = targetQueue - currentOutstanding;
      int capacity = Math.max(minBatchSize, deficit);
      sendPullRequestWithCapacity(capacity);
    }
  }

  public void handlePullRequest(String requestingNode, int capacity) {
    int myQueue = taskQueue.size();
    if (myQueue > pullThreshold) {
      int shareCount =
          Math.min(capacity, Math.min(minBatchSize, Math.max(0, (myQueue - pullThreshold) / 2)));
      if (shareCount > 0) {
        List<Task> toShare = taskQueue.removeBatch(shareCount);
        if (toShare != null && !toShare.isEmpty()) {
          for (Task t : toShare) {
            if (!t.isMigrated()) t.markMigrated();
          }
          try {
            NetworkUtil.sendTasks(requestingNode, toShare);
            stats.incrementPushed(toShare.size());
          } catch (IOException e) {
            taskQueue.addBatch(toShare);
          }
        }
      }
    }
  }

  public void sendPullRequestIfIdle() {
    String predecessor = state.getPredecessor();
    if (predecessor != null && taskQueue.size() < pullThreshold) {
      int capacity = Math.max(minBatchSize, Math.max(0, pushThreshold - taskQueue.size()));
      try {
        NetworkUtil.sendString(predecessor, Protocol.PULL_REQUEST + " " + myId + " " + capacity);
      } catch (IOException ignored) {
      }
    }
  }

  public void sendPullRequestWithCapacity(int capacity) {
    String predecessor = state.getPredecessor();
    if (predecessor != null) {
      try {
        NetworkUtil.sendString(predecessor, Protocol.PULL_REQUEST + " " + myId + " " + capacity);
      } catch (IOException ignored) {
      }
    }
  }
}
