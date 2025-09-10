package csx55.threads;

import java.util.List;
import java.util.Random;

public class ComputeNode {
  private final String ip;
  private final int port;
  private ComputeNode successor;

  private final TaskQueue taskQueue = new TaskQueue();
  private ThreadPool pool;
  private final StatsMock stats = new StatsMock();
  private final Random rand = new Random();

  public ComputeNode(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  public void setSuccessor(ComputeNode successor) {
    this.successor = successor;
  }

  public String getIpPort() {
    return ip + ":" + port;
  }

  public void startThreadPool(int poolSize) {
    pool = new ThreadPool(poolSize, taskQueue, stats);
  }

  public void submitTasks(int n) {
    String nodeId = getIpPort();
    for (int i = 1; i <= n; i++) {
      Task t = new Task(nodeId + "-T" + i);
      stats.incrementGenerated();
      taskQueue.add(t);
    }
  }

  public StatsMock getStats() {
    return stats;
  }

  public void runRounds(int numberOfRounds) {
    for (int round = 1; round <= numberOfRounds; round++) {
      // keep test runs fast: 5–10 tasks
      int numTasks = rand.nextInt(6) + 5;
      submitTasks(numTasks);
      System.out.println(getIpPort() + " generated " + numTasks + " tasks in round " + round);

      if (successor != null && taskQueue.size() > 2000) {
        migrateTasks(successor, 50);
      }

      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    System.out.println("DEBUG: " + getIpPort() + " finished generating all rounds.");
  }

  public void migrateTasks(ComputeNode target, int numTasks) {
    List<Task> batch = this.taskQueue.removeBatch(numTasks);
    if (!batch.isEmpty()) {
      this.stats.incrementPushed(batch.size());
      target.taskQueue.addBatch(batch);
      target.stats.incrementPulled(batch.size());
      System.out.println(
          "Migrated "
              + batch.size()
              + " tasks from "
              + this.getIpPort()
              + " to "
              + target.getIpPort());
    }
  }

  public void shutdown() {
    int poolSize = pool.getSize();
    System.out.println(
        "DEBUG: "
            + getIpPort()
            + " injecting "
            + poolSize
            + " poison pills. Queue size before="
            + taskQueue.size());
    for (int i = 0; i < poolSize; i++) {
      taskQueue.add(Task.poisonPill());
    }
    System.out.println(
        "DEBUG: " + getIpPort() + " poison pills injected. Queue size after=" + taskQueue.size());
  }

  public void awaitWorkers() {
    if (pool != null) {
      pool.joinAll();
      System.out.println("DEBUG: " + getIpPort() + " finished all work.");
    }
  }

  public int getQueueSize() {
    return taskQueue.size();
  }
}
