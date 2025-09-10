package csx55.threads;

public class WorkerMock implements Runnable {
  private final TaskQueueMock queue;

  public WorkerMock(TaskQueueMock queue) {
    this.queue = queue;
  }

  @Override
  public void run() {
    try {
      while (true) {
        TaskMock task = queue.take();
        task.execute();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
