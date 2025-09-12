package csx55.threads.util;

import csx55.threads.core.Stats;
import csx55.threads.core.TaskQueue;
import java.util.function.IntSupplier;

public final class WaitUtil {
  private WaitUtil() {}

  public static void waitUntilDrained(TaskQueue queue, Stats stats) {
    while (queue.size() > 0 || stats.getInFlight() > 0) {
      try {
        Thread.sleep(25);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  public static void waitForQuiescence(
      IntSupplier queueSize, IntSupplier inFlight, long stableMillis, long maxWaitMillis) {
    long deadline = System.currentTimeMillis() + maxWaitMillis;
    long stableSince = -1;
    int lastQ = -1, lastF = -1;

    while (System.currentTimeMillis() < deadline) {
      int q = queueSize.getAsInt();
      int f = inFlight.getAsInt();
      boolean idle = (q == 0 && f == 0);
      boolean unchanged = (q == lastQ && f == lastF);

      if (idle && unchanged) {
        if (stableSince < 0) stableSince = System.currentTimeMillis();
        if (System.currentTimeMillis() - stableSince >= stableMillis) return;
      } else {
        stableSince = -1;
      }

      lastQ = q;
      lastF = f;
      try {
        Thread.sleep(25);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }
}
