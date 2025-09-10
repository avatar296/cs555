package csx55.threads;

import java.util.LinkedList;
import java.util.Queue;

public class TaskQueueMock {
  private final Queue<TaskMock> queue = new LinkedList<>();

  public synchronized void add(TaskMock task) {
    queue.add(task);
    notifyAll();
  }

  public synchronized TaskMock take() throws InterruptedException {
    while (queue.isEmpty()) {
      wait();
    }
    return queue.poll();
  }
}
