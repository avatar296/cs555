package csx55.threads;

import java.util.*;

public class RegistryMock {
    private final List<ComputeNodeMock> nodes = new ArrayList<>();

    public void registerNode(ComputeNodeMock node) {
        System.out.println("Registry: Registered node " + node.getIpPort());
        nodes.add(node);
    }

    public void setupOverlay() {
        for (int i = 0; i < nodes.size(); i++) {
            ComputeNodeMock current = nodes.get(i);
            ComputeNodeMock successor = nodes.get((i + 1) % nodes.size());
            current.setSuccessor(successor);
        }
        System.out.println("Registry: Overlay setup complete.");
    }

    public void printFinalStatsTable() {
        // compute total completed across all nodes
        int totalCompleted = nodes.stream()
                .mapToInt(n -> n.getStats().getCompletedTasks())
                .sum();

        System.out.println("\n=== Final Stats Table (Mock) ===");
        for (ComputeNodeMock node : nodes) {
            StatsMock stats = node.getStats();
            System.out.printf(
                    "%s %d %d %d %d %.8f%n",
                    node.getIpPort(),
                    stats.getGeneratedTasks(),
                    stats.getPulledTasks(),
                    stats.getPushedTasks(),
                    stats.getCompletedTasks(),
                    stats.getPercentOfTotal(totalCompleted));
        }
    }
}
