package csx55.threads;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single worker thread that continuously takes Tasks from the TaskQueue and mines them. Threads
 * are created once and stay alive until the pool is shut down.
 */
public class Worker implements Runnable {

  private final int id;
  private final TaskQueue queue;

  @SuppressWarnings("unused")
  private final Stats stats; // kept for future instrumentation if needed

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

  /** Signal the worker to stop and interrupt the thread so a blocking take() unblocks. */
  public void stopWorker() {
    running.set(false);
    thread.interrupt();
  }

  /** Wait for the worker to finish. */
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
        // Blocking retrieval; unblocks on interrupt during shutdown.
        Task task = queue.take();
        if (task == null) {
          // Defensive: continue if queue returns null (shouldn't happen with a blocking
          // queue)
          continue;
        }
        // Mine the task (Task is responsible for printing itself when mined)
        task.mine();

        // After mining completes successfully, the worker will loop and take the next
        // task.
        // Concurrency limit is governed by the number of Worker instances in the pool.

      } catch (InterruptedException e) {
        // If we're shutting down, exit; otherwise, continue.
        if (!running.get()) {
          break;
        }
        // spurious interrupt while still running; continue loop
      } catch (Throwable t) {
        // Be robust against unexpected exceptions so the worker stays alive.
        t.printStackTrace();
      }
    }
  }

  @Override
  public String toString() {
    return "Worker{id=" + id + ", name=" + Thread.currentThread().getName() + "}";
  }
}
