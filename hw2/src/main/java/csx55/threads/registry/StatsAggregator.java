package csx55.threads.registry;

import csx55.threads.core.Stats;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StatsAggregator {
  private final Map<String, Stats> statsByNode = new ConcurrentHashMap<>();
  private final AtomicBoolean printed = new AtomicBoolean(false);

  public void clear() {
    statsByNode.clear();
    printed.set(false);
  }

  public void record(String nodeId, Stats stats) {
    statsByNode.put(nodeId, stats);
  }

  public void printFinalIfReady(List<String> nodes, long startTime) {
    if (printed.get()) return;
    if (statsByNode.size() != nodes.size()) return;
    synchronized (this) {
      if (printed.get()) return;
      if (statsByNode.size() != nodes.size()) return;
      printFinal(nodes, startTime);
      printed.set(true);
    }
  }

  private void printFinal(List<String> nodes, long startTime) {
    long totalGenerated = statsByNode.values().stream().mapToLong(Stats::getGenerated).sum();
    long totalPulled = statsByNode.values().stream().mapToLong(Stats::getPulled).sum();
    long totalPushed = statsByNode.values().stream().mapToLong(Stats::getPushed).sum();
    long totalCompleted = statsByNode.values().stream().mapToLong(Stats::getCompleted).sum();

    for (String node : nodes) {
      Stats s = statsByNode.get(node);
      if (s == null) continue;
      double percent = (totalCompleted == 0) ? 0.0 : (100.0 * s.getCompleted()) / totalCompleted;
      System.out.printf(
          Locale.US,
          "%s %d %d %d %d %.4f%n",
          node,
          s.getGenerated(),
          s.getPulled(),
          s.getPushed(),
          s.getCompleted(),
          percent);
    }

    System.out.printf(
        Locale.US,
        "Total %d %d %d %d %.4f%n",
        totalGenerated,
        totalPulled,
        totalPushed,
        totalCompleted,
        totalCompleted == 0 ? 0.0 : 100.0);

    if (startTime > 0) {
      long endTime = System.currentTimeMillis();
      double durationSeconds = (endTime - startTime) / 1000.0;
      System.out.printf(Locale.US, "%nExecution time: %.2f seconds%n", durationSeconds);
    }
  }
}
