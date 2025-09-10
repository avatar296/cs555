package csx55.threads;

import java.util.*;
import java.util.concurrent.locks.*;

public class TaskQueue {
  private Queue<Task> queue = new LinkedList<>();
  private ReentrantLock lock = new ReentrantLock();
  private Condition notEmpty = lock.newCondition();

  public void add(Task t) {
    // Lock, add to queue, signal workers
  }

  public Task take() throws InterruptedException {
    // Lock, wait until not empty, then pop
    return null; // placeholder
  }
}
