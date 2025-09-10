package csx55.threads;

import java.util.ArrayList;
import java.util.List;

public class ThreadPool {
  private final List<Thread> workers = new ArrayList<>();

  public ThreadPool(int size, TaskQueue queue, StatsMock stats) {
    for (int i = 0; i < size; i++) {
      Thread t = new Thread(new Worker(queue, stats), "Worker-" + i);
      workers.add(t);
      t.start();
    }
  }

  public int getSize() {
    return workers.size();
  }

  public void joinAll() {
    for (Thread t : workers) {
      try {
        t.join();
        System.out.println("DEBUG: " + t.getName() + " has terminated.");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
