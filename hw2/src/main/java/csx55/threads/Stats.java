package csx55.threads;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/** Stats for each node. */
public class Stats implements Serializable {
  private static final long serialVersionUID = 1L;

  private final AtomicInteger generated = new AtomicInteger();
  private final AtomicInteger pulled = new AtomicInteger();
  private final AtomicInteger pushed = new AtomicInteger();
  private final AtomicInteger completed = new AtomicInteger();

  public void incrementGenerated(int n) {
    generated.addAndGet(n);
  }

  public void incrementPulled(int n) {
    pulled.addAndGet(n);
  }

  public void incrementPushed(int n) {
    pushed.addAndGet(n);
  }

  public void incrementCompleted() {
    completed.incrementAndGet();
  }

  public int getGenerated() {
    return generated.get();
  }

  public int getPulled() {
    return pulled.get();
  }

  public int getPushed() {
    return pushed.get();
  }

  public int getCompleted() {
    return completed.get();
  }
}
