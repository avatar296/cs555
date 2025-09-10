package csx55.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class TaskQueue {
  private final LinkedBlockingQueue<Task> q = new LinkedBlockingQueue<>();

  public void add(Task t) {
    q.add(t);
  }

  public void addBatch(List<Task> batch) {
    if (batch == null || batch.isEmpty()) return;
    for (Task t : batch) q.add(t);
  }

  /** Blocking take used by workers. */
  public Task take() throws InterruptedException {
    return q.take();
  }

  /** Remove up to n tasks (non-blocking). */
  public List<Task> removeBatch(int n) {
    List<Task> res = new ArrayList<>(n);
    q.drainTo(res, n);
    return res;
  }

  public int size() {
    return q.size();
  }
}
