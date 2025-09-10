package csx55.threads;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class TaskQueue {
  private final Queue<Task> queue = new LinkedList<>();

  public synchronized void add(Task t) {
    queue.add(t);
    notifyAll();
  }

  public synchronized void addBatch(List<Task> tasks) {
    queue.addAll(tasks);
    notifyAll();
  }

  public synchronized Task take() throws InterruptedException {
    while (queue.isEmpty()) wait();
    return queue.poll();
  }

  public synchronized List<Task> removeBatch(int n) {
    List<Task> batch = new LinkedList<>();
    for (int i = 0; i < n && !queue.isEmpty(); i++) {
      batch.add(queue.poll());
    }
    return batch;
  }

  public synchronized int size() {
    return queue.size();
  }
}
