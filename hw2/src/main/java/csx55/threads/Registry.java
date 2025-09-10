package csx55.threads;

import java.util.ArrayList;
import java.util.List;

public class Registry {
  private final List<ComputeNode> nodes = new ArrayList<>();
  private final List<Thread> nodeThreads = new ArrayList<>();

  public void registerNode(ComputeNode node) {
    nodes.add(node);
    System.out.println("Registry: Registered node " + node.getIpPort());
  }

  public void setupOverlay(int threadPoolSize) {
    int n = nodes.size();
    for (int i = 0; i < n; i++) {
      ComputeNode current = nodes.get(i);
      ComputeNode successor = nodes.get((i + 1) % n);
      current.setSuccessor(successor);
      current.startThreadPool(threadPoolSize);
    }
    System.out.println(
        "Registry: Overlay setup complete with " + threadPoolSize + " threads per node.");
  }

  public void startComputation(int rounds) {
    nodeThreads.clear();
    for (ComputeNode node : nodes) {
      Thread t = new Thread(() -> node.runRounds(rounds));
      nodeThreads.add(t);
      t.start();
    }
    System.out.println("Registry: Started computation for " + rounds + " rounds.");

    // 1. Wait for all nodes to finish generating tasks
    for (Thread t : nodeThreads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // DEBUG: Show queue sizes after generation
    System.out.println("=== DEBUG: All rounds finished. Queue sizes ===");
    for (ComputeNode node : nodes) {
      System.out.println("DEBUG: " + node.getIpPort() + " queue size=" + node.getQueueSize());
    }

    // 2. Send poison pills to all nodes
    for (ComputeNode node : nodes) {
      node.shutdown();
      System.out.println("DEBUG: Shutdown sent to " + node.getIpPort());
    }

    // 3. Wait for worker pools to finish
    for (ComputeNode node : nodes) {
      node.awaitWorkers();
    }

    // 4. Print final stats
    printFinalStats();
  }

  public void printFinalStats() {
    int totalCompleted = nodes.stream().mapToInt(n -> n.getStats().getCompletedTasks()).sum();

    System.out.println("\n=== Final Stats Table ===");
    for (ComputeNode node : nodes) {
      StatsMock s = node.getStats();

      // Verify invariant
      int expectedCompleted = s.getGeneratedTasks() + s.getPulledTasks() - s.getPushedTasks();
      if (expectedCompleted != s.getCompletedTasks()) {
        System.out.println(
            "⚠️ Invariant failed for "
                + node.getIpPort()
                + ": expected "
                + expectedCompleted
                + " but got "
                + s.getCompletedTasks());
      }

      System.out.printf(
          "%s %d %d %d %d %.8f%n",
          node.getIpPort(),
          s.getGeneratedTasks(),
          s.getPulledTasks(),
          s.getPushedTasks(),
          s.getCompletedTasks(),
          s.getPercentOfTotal(totalCompleted));
    }
  }
}
