package csx55.threads;

import java.util.ArrayList;
import java.util.List;

public class ThreadPool {
  private final List<Worker> workers = new ArrayList<>();

  public ThreadPool(int size, TaskQueue queue, Stats stats, String nodeId) {
    for (int i = 0; i < size; i++) {
      Worker w = new Worker(queue, stats, "Worker-" + i + "@" + nodeId);
      workers.add(w);
      w.start();
    }
  }

  public int getSize() {
    return workers.size();
  }

  public void joinAll() {
    for (Worker w : workers) {
      try {
        w.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
