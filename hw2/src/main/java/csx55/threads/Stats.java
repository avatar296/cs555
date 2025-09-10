package csx55.threads;

import java.io.Serializable;

public class Stats implements Serializable {
  private int generated;
  private int pulled;
  private int pushed;
  private int completed;

  public synchronized void incrementGenerated() {
    generated++;
  }

  public synchronized void incrementPulled(int n) {
    pulled += n;
  }

  public synchronized void incrementPushed(int n) {
    pushed += n;
  }

  public synchronized void incrementCompleted() {
    completed++;
  }

  public int getGenerated() {
    return generated;
  }

  public int getPulled() {
    return pulled;
  }

  public int getPushed() {
    return pushed;
  }

  public int getCompleted() {
    return completed;
  }

  @Override
  public String toString() {
    return String.format(
        "Generated=%d Pulled=%d Pushed=%d Completed=%d", generated, pulled, pushed, completed);
  }
}
