package csx55.threads.core;

import csx55.threads.util.Config;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class Worker implements Runnable {

  private static final boolean PRINT_TASKS = Config.getBool("cs555.printTasks", true);
  private static final int BUFFER_SIZE = 50;

  private final TaskQueue queue;
  private final Stats stats;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Thread thread;
  private final MessageDigest sha256;
  private final Random random;
  private final List<String> printBuffer = new ArrayList<>();

  public Worker(int id, TaskQueue queue, Stats stats, String nodeId) {
    this.queue = queue;
    this.stats = stats;
    this.thread = new Thread(this, "Worker-" + id);
    try {
      this.sha256 = MessageDigest.getInstance("SHA3-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    this.random = new Random();
  }

  public void start() {
    thread.start();
  }

  public void stopWorker() {
    running.set(false);
    thread.interrupt();
  }

  public void join() {
    try {
      thread.join();
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void run() {
    while (running.get()) {
      try {
        Task task = queue.take();
        if (task == null)
          continue;

        stats.incInFlight();
        try {
          task.mineWithProvided(sha256, random);

          if (PRINT_TASKS) {
            printBuffer.add(task.toString());
            if (printBuffer.size() >= BUFFER_SIZE) {
              flushBuffer();
            }
          }

          stats.incrementCompleted();
        } finally {
          stats.decInFlight();
        }

      } catch (InterruptedException e) {
        if (!running.get())
          break;
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
    // Flush any remaining buffered output
    if (!printBuffer.isEmpty()) {
      flushBuffer();
    }
  }

  private void flushBuffer() {
    synchronized (System.out) {
      for (String s : printBuffer) {
        System.out.println(s);
      }
    }
    printBuffer.clear();
  }
}
