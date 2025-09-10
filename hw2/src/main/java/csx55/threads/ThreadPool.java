package csx55.threads;

import java.util.*;

public class ThreadPool {
  private List<Worker> workers = new ArrayList<>();
  private TaskQueue taskQueue;
  private boolean isShutdown = false;

  public ThreadPool(int size, TaskQueue queue) {
    this.taskQueue = queue;
    // Initialize workers
  }

  public void startWorkers() {
    // Start worker threads
  }

  public void shutdown() {
    isShutdown = true;
  }
}
