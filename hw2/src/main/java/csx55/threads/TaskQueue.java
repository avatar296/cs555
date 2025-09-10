package csx55.threads;

import java.util.*;
import java.util.concurrent.locks.*;

public class TaskQueue {
  private final Queue<Task> queue = new LinkedList<>();
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition notEmpty = lock.newCondition();

  public void add(Task t) {
    lock.lock();
    try {
      queue.add(t);
      notEmpty.signalAll();
    } finally {
      lock.unlock();
    }
  }

  public Task take() throws InterruptedException {
    lock.lock();
    try {
      while (queue.isEmpty()) {
        notEmpty.await();
      }
      return queue.poll();
    } finally {
      lock.unlock();
    }
  }

  // --- NEW: Remove a batch for migration ---
  public List<Task> removeBatch(int batchSize) {
    lock.lock();
    try {
      List<Task> batch = new ArrayList<>();
      while (!queue.isEmpty() && batch.size() < batchSize) {
        Task t = queue.poll();
        if (t != null && !t.isMigrated()) {
          t.markMigrated();
          batch.add(t);
        }
      }
      return batch;
    } finally {
      lock.unlock();
    }
  }

  // --- NEW: Add a batch (pulled tasks) ---
  public void addBatch(List<Task> tasks) {
    lock.lock();
    try {
      for (Task t : tasks) {
        queue.add(t);
      }
      notEmpty.signalAll();
    } finally {
      lock.unlock();
    }
  }

  public int size() {
    lock.lock();
    try {
      return queue.size();
    } finally {
      lock.unlock();
    }
  }
}
