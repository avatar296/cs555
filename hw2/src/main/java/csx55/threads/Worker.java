package csx55.threads;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker threads take tasks from the queue and mine them. Credit completion to THIS node (via
 * Stats) AFTER mining.
 */
public class Worker implements Runnable {

  private static final boolean PRINT_TASKS =
      Boolean.parseBoolean(System.getProperty("cs555.printTasks", "true"));

  private final int id;
  private final TaskQueue queue;
  private final Stats stats;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Thread thread;

  public Worker(int id, TaskQueue queue, Stats stats) {
    this.id = id;
    this.queue = queue;
    this.stats = stats;
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
    while (running.get()) {
      try {
        Task task = queue.take(); // blocking
        if (task == null) continue;

        stats.incInFlight();
        try {
          task.mine(); // do the work
          stats.incrementCompleted(); // CREDIT goes to THIS node
          if (PRINT_TASKS) {
            System.out.println(task); // print mined task at this node
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

  @Override
  public String toString() {
    return "Worker{id=" + id + ", name=" + Thread.currentThread().getName() + "}";
  }
}
