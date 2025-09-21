package csx55.threads.core;

import csx55.threads.util.Config;
import java.util.concurrent.atomic.AtomicBoolean;

public class Worker implements Runnable {

  private static final boolean PRINT_TASKS = Config.getBool("cs555.printTasks", true);

  private final TaskQueue queue;
  private final Stats stats;
  private final String nodeId;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Thread thread;

  public Worker(int id, TaskQueue queue, Stats stats, String nodeId) {
    this.queue = queue;
    this.stats = stats;
    this.nodeId = nodeId;
    this.thread = new Thread(this, "Worker-" + id);
  }

  public void start() {
    thread.start();
  }

  public void stopWorker() {
    running.set(false);
    thread.interrupt();
  }

  public void join() {
    try {
      thread.join();
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void run() {
    System.out.println("[WORKER-START] " + thread.getName() + " started for node " + nodeId);
    while (running.get()) {
      try {
        Task task = queue.take();
        if (task == null) continue;

        stats.incInFlight();
        try {
          task.setMinedAt(nodeId); // Set the mining node before mining
          task.mine();
          stats.incrementCompleted();
          if (PRINT_TASKS) {
            System.out.println(task);
          }
        } finally {
          stats.decInFlight();
        }

      } catch (InterruptedException e) {
        if (!running.get()) break;
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
  }
}
