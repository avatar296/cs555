package csx55.threads.core;

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
    q.addAll(batch);
  }

  public Task take() throws InterruptedException {
    return q.take();
  }

  public List<Task> removeBatch(int n) {
    List<Task> res = new ArrayList<>(n);
    List<Task> temp = new ArrayList<>(n);

    q.drainTo(temp, n);

    for (Task t : temp) {
      if (!t.isMigrated()) {
        res.add(t);
      }
    }

    if (res.size() < n && q.size() > 0) {
      int needed = n - res.size();
      List<Task> additional = new ArrayList<>(needed);
      q.drainTo(additional, needed);
      for (Task t : additional) {
        if (!t.isMigrated()) {
          res.add(t);
        }
      }
    }

    return res;
  }

  public int size() {
    return q.size();
  }
}
