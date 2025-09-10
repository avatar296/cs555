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
  private final Stats stats;
  private final List<Worker> workers = new ArrayList<>();

  public ThreadPool(int size, TaskQueue queue, Stats stats) {
    if (size < 2 || size > 16) {
      throw new IllegalArgumentException("Thread pool size must be between 2 and 16 inclusive");
    }
    this.size = size;
    this.queue = queue;
    this.stats = stats;

    for (int i = 0; i < size; i++) workers.add(new Worker(i, queue, stats));
    for (Worker w : workers) w.start();
  }

  public void submit(Task task) {
    queue.add(task);
  }

  public void submitBatch(List<Task> tasks) {
    if (tasks == null || tasks.isEmpty()) return;
    queue.addBatch(tasks);
  }

  public List<Task> removeBatch(int n) {
    List<Task> batch = queue.removeBatch(n);
    return batch == null ? Collections.emptyList() : batch;
  }

  public int queuedSize() {
    return queue.size();
  }

  public void shutdown() {
    for (Worker w : workers) w.stopWorker();
    for (Worker w : workers) w.join();
  }

  public int size() {
    return size;
  }
}
