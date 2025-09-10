package csx55.threads;

public class ThreadPoolMock {
  private final Thread[] workers;

  public ThreadPoolMock(int size, TaskQueueMock queue) {
    workers = new Thread[size];
    for (int i = 0; i < size; i++) {
      workers[i] = new Thread(new WorkerMock(queue), "Worker-" + i);
      workers[i].start();
    }
  }
}
