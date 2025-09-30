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
    for (Task t : batch) q.add(t);
  }

  public Task take() throws InterruptedException {
    return q.take();
  }

  public List<Task> removeBatch(int n) {
    List<Task> res = new ArrayList<>(n);
    List<Task> temp = new ArrayList<>(n * 2);
    q.drainTo(temp, n * 2);

    List<Task> alreadyMigrated = new ArrayList<>();
    for (Task t : temp) {
      if (t.isMigrated()) {
        alreadyMigrated.add(t);
      } else if (res.size() < n) {
        res.add(t);
      } else {
        q.add(t);
      }
    }

    for (Task t : alreadyMigrated) {
      q.add(t);
    }

    return res;
  }

  public int size() {
    return q.size();
  }
}
