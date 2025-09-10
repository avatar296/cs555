package csx55.threads;

public class TaskMock {
  private final String taskId;
  private final StatsMock stats;

  public TaskMock(String nodeId, int id, StatsMock stats) {
    this.taskId = nodeId + "-T" + id;
    this.stats = stats;
    this.stats.incrementGenerated(); // count at creation
  }

  public void execute() {
    try {
      Thread.sleep(200); // simulate mining
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    stats.incrementCompleted();
    System.out.println("Task " + taskId + " completed by " + Thread.currentThread().getName());
  }
}
