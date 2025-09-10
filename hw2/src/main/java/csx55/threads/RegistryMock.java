package csx55.threads;

import java.util.*;

public class RegistryMock {
  private List<ComputeNodeMock> nodes = new ArrayList<>();

  public void registerNode(ComputeNodeMock node) {
    System.out.println("Registry: Registered node " + node.getIpPort());
    nodes.add(node);
  }

  public void setupOverlay() {
    // Arrange nodes in a ring
    for (int i = 0; i < nodes.size(); i++) {
      ComputeNodeMock current = nodes.get(i);
      ComputeNodeMock successor = nodes.get((i + 1) % nodes.size());
      current.setSuccessor(successor);
    }
    System.out.println("Registry: Overlay setup complete.");
  }
}
