package csx55.threads.core;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public class Stats implements Serializable {
  private static final long serialVersionUID = 1L;

  private final AtomicInteger generated = new AtomicInteger();
  private final AtomicInteger pulled = new AtomicInteger();
  private final AtomicInteger pushed = new AtomicInteger();
  private final AtomicInteger completed = new AtomicInteger();
  private final AtomicInteger inFlight = new AtomicInteger();

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

  // In-flight tracking used by workers
  public void incInFlight() {
    inFlight.incrementAndGet();
  }

  public void decInFlight() {
    inFlight.decrementAndGet();
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

  public int getInFlight() {
    return inFlight.get();
  }
}
