package csx55.threads;

public class Worker implements Runnable {
  private final TaskQueue taskQueue;
  private final StatsMock stats;

  public Worker(TaskQueue queue, StatsMock stats) {
    this.taskQueue = queue;
    this.stats = stats;
  }

  @Override
  public void run() {
    try {
      while (true) {
        Task task = taskQueue.take();

        if (task.isPoison()) {
          System.out.println(
              "DEBUG: "
                  + Thread.currentThread().getName()
                  + " consumed poison pill, shutting down. Remaining queue size="
                  + taskQueue.size());
          break;
        }

        System.out.println("DEBUG: " + Thread.currentThread().getName() + " processing " + task);
        task.mine();
        stats.incrementCompleted();
        System.out.println(task);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
