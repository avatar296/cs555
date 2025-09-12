package csx55.threads.core;

import java.util.ArrayList;
import java.util.List;

public class ThreadPool {

  private final List<Worker> workers = new ArrayList<>();

  public ThreadPool(int size, TaskQueue queue, Stats stats) {
    if (size < 2 || size > 16) {
      throw new IllegalArgumentException("Thread pool size must be between 2 and 16 inclusive");
    }

    for (int i = 0; i < size; i++) workers.add(new Worker(i, queue, stats));
    for (Worker w : workers) w.start();
  }

  public void shutdown() {
    for (Worker w : workers) w.stopWorker();
    for (Worker w : workers) w.join();
  }
}
