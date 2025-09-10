package csx55.threads;

public class StatsMock {
  private int generatedTasks = 0;
  private int completedTasks = 0;
  private int pulledTasks = 0;
  private int pushedTasks = 0;

  public synchronized void incrementGenerated() {
    generatedTasks++;
  }

  public synchronized void incrementCompleted() {
    completedTasks++;
  }

  public synchronized void incrementPulled(int n) {
    pulledTasks += n;
  }

  public synchronized void incrementPushed(int n) {
    pushedTasks += n;
  }

  public synchronized int getGeneratedTasks() {
    return generatedTasks;
  }

  public synchronized int getCompletedTasks() {
    return completedTasks;
  }

  public synchronized int getPulledTasks() {
    return pulledTasks;
  }

  public synchronized int getPushedTasks() {
    return pushedTasks;
  }

  public synchronized double getPercentOfTotal(int totalAll) {
    return totalAll == 0 ? 0.0 : (completedTasks * 100.0 / totalAll);
  }
}
