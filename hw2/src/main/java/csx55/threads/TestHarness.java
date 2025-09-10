package csx55.threads;

import java.util.*;

public class TestHarness {
  public static void main(String[] args) {
    // --- Start Registry ---
    RegistryMock registry = new RegistryMock();

    // --- Create 2 ComputeNodes ---
    ComputeNodeMock node1 = new ComputeNodeMock("192.168.1.1", 5001);
    ComputeNodeMock node2 = new ComputeNodeMock("192.168.1.2", 5002);

    // --- Register Nodes ---
    registry.registerNode(node1);
    registry.registerNode(node2);

    // --- Setup Overlay ---
    registry.setupOverlay();

    // --- Print Overlay Assignments ---
    node1.printOverlay();
    node2.printOverlay();
  }
}
