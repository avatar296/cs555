package csx55.threads;

public class Worker extends Thread {
  private final TaskQueue queue;
  private final Stats stats;

  public Worker(TaskQueue queue, Stats stats, String name) {
    super(name);
    this.queue = queue;
    this.stats = stats;
  }

  @Override
  public void run() {
    try {
      while (true) {
        Task t = queue.take();
        if (t.isPoison()) {
          System.out.println(getName() + " shutting down");
          break;
        }
        t.mine();
        stats.incrementCompleted();
        System.out.println(getName() + " completed " + t);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
