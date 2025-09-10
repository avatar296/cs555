package csx55.threads;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single worker thread that continuously takes Tasks from the TaskQueue and mines them. Threads
 * are created once and stay alive until the pool is shut down.
 */
public class Worker implements Runnable {

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
        Task task = queue.take(); // blocks
        if (task == null) continue;

        stats.incInFlight();
        try {
          task.mine();
        } finally {
          stats.decInFlight();
        }

      } catch (InterruptedException e) {
        if (!running.get()) break; // exiting
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
