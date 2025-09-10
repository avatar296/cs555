package csx55.threads;

public class Worker implements Runnable {
  private TaskQueue taskQueue;
  private boolean running = true;

  public Worker(TaskQueue queue) {
    this.taskQueue = queue;
  }

  @Override
  public void run() {
    // Continuously take tasks and execute mine()
  }

  public void stop() {
    running = false;
  }
}
