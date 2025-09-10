package csx55.threads;

public class TestHarness {
  public static void main(String[] args) {
    // --- Setup registry ---
    Registry registry = new Registry();

    // --- Create 3 nodes ---
    ComputeNode node1 = new ComputeNode("192.168.1.1", 5001);
    ComputeNode node2 = new ComputeNode("192.168.1.2", 5002);
    ComputeNode node3 = new ComputeNode("192.168.1.3", 5003);

    // --- Register with registry ---
    registry.registerNode(node1);
    registry.registerNode(node2);
    registry.registerNode(node3);

    // --- Setup overlay and thread pools ---
    registry.setupOverlay(3); // 3 threads per node

    // --- Start computation ---
    registry.startComputation(3); // run 3 rounds

    // --- Wait for everything to finish ---
    try {
      Thread.sleep(15000); // adjust if needed for longer runs
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // --- Print final stats table ---
    registry.printFinalStats();
  }
}
