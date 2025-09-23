package csx55.threads.registry;

import csx55.threads.core.Stats;
import csx55.threads.util.Log;
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

    int expected = stats.getGenerated() + stats.getPulled() - stats.getPushed();
    System.out.println("[DEBUG] Stats received from " + nodeId + ":");
    System.out.println("  Generated: " + stats.getGenerated());
    System.out.println("  Pulled: " + stats.getPulled());
    System.out.println("  Pushed: " + stats.getPushed());
    System.out.println("  Completed: " + stats.getCompleted());
    System.out.println("  Expected (gen+pulled-pushed): " + expected);
    System.out.println("  Match: " + (stats.getCompleted() == expected));
  }

  public int size() {
    return statsByNode.size();
  }

  public void printFinalIfReady(List<String> nodes) {
    if (printed.get()) return;
    if (statsByNode.size() != nodes.size()) return;
    synchronized (this) {
      if (printed.get()) return;
      if (statsByNode.size() != nodes.size()) return;
      printFinal(nodes);
      printed.set(true);
    }
  }

  private void printFinal(List<String> nodes) {
    long totalGenerated = statsByNode.values().stream().mapToLong(Stats::getGenerated).sum();
    long totalPulled = statsByNode.values().stream().mapToLong(Stats::getPulled).sum();
    long totalPushed = statsByNode.values().stream().mapToLong(Stats::getPushed).sum();
    long totalCompleted = statsByNode.values().stream().mapToLong(Stats::getCompleted).sum();

    long totalExpected = totalGenerated + totalPulled - totalPushed;
    System.out.println("[DEBUG] Final stats verification:");
    System.out.println("  Total generated: " + totalGenerated);
    System.out.println("  Total pulled: " + totalPulled);
    System.out.println("  Total pushed: " + totalPushed);
    System.out.println("  Total completed: " + totalCompleted);
    System.out.println("  Total expected: " + totalExpected);
    System.out.println("  Totals match: " + (totalCompleted == totalExpected));

    for (String node : nodes) {
      Stats s = statsByNode.get(node);
      if (s == null) continue;
      int expectedCompleted = s.getGenerated() + s.getPulled() - s.getPushed();
      if (s.getCompleted() != expectedCompleted) {
        Log.warn(
            "Stats invariant mismatch for "
                + node
                + ": completed="
                + s.getCompleted()
                + ", expected="
                + expectedCompleted);
      }
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
  }
}
