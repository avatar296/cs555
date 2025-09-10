package csx55.threads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple fixed-size thread pool using our own Worker threads and a shared TaskQueue. Threads are
 * created once at startup and remain alive until shutdown().
 */
public class ThreadPool {

  private final int size;
  private final TaskQueue queue;

  @SuppressWarnings("unused")
  private final Stats stats; // kept for future instrumentation if needed

  private final List<Worker> workers = new ArrayList<>();

  public ThreadPool(int size, TaskQueue queue, Stats stats) {
    if (size < 2 || size > 16) {
      throw new IllegalArgumentException("Thread pool size must be between 2 and 16 inclusive");
    }
    this.size = size;
    this.queue = queue;
    this.stats = stats;

    // Create workers once
    for (int i = 0; i < size; i++) {
      workers.add(new Worker(i, queue, stats));
    }
    // Start workers once
    for (Worker w : workers) {
      w.start();
    }
  }

  /** Submit a single task to the shared queue. */
  public void submit(Task task) {
    queue.add(task);
  }

  /** Submit a batch of tasks to the shared queue. */
  public void submitBatch(List<Task> tasks) {
    if (tasks == null || tasks.isEmpty()) return;
    queue.addBatch(tasks);
  }

  /**
   * Remove a batch of tasks from the head of the queue (FIFO), for migration. Returns an immutable
   * empty list if none available.
   */
  public List<Task> removeBatch(int n) {
    List<Task> batch = queue.removeBatch(n);
    return batch == null ? Collections.emptyList() : batch;
  }

  /** Current number of queued (not-yet-started) tasks. */
  public int queuedSize() {
    return queue.size();
  }

  /**
   * Stop all workers and wait for them to finish. Note: This does not drain the queue; call only
   * when the node is done producing work.
   */
  public void shutdown() {
    for (Worker w : workers) {
      w.stopWorker();
    }
    for (Worker w : workers) {
      w.join();
    }
  }

  public int size() {
    return size;
  }
}
