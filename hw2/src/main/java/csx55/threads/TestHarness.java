package csx55.threads;

public class TestHarness {
    public static void main(String[] args) {
        RegistryMock registry = new RegistryMock();

        ComputeNodeMock node1 = new ComputeNodeMock("192.168.1.1", 5001);
        ComputeNodeMock node2 = new ComputeNodeMock("192.168.1.2", 5002);
        ComputeNodeMock node3 = new ComputeNodeMock("192.168.1.3", 5003);

        registry.registerNode(node1);
        registry.registerNode(node2);
        registry.registerNode(node3);
        registry.setupOverlay();

        node1.printOverlay();
        node2.printOverlay();
        node3.printOverlay();

        node1.startThreadPool(2);
        node2.startThreadPool(3);
        node3.startThreadPool(2);

        node1.submitTasks(4);
        node2.submitTasks(6);
        node3.submitTasks(5);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
        }

        // --- Week 3 Mock Load Balancing ---
        // Example: Node2 is overloaded, pushes 2 tasks to Node1
        node2.mockPushTasks(node1, 2);

        // Node3 pushes 1 task to Node1
        node3.mockPushTasks(node1, 1);

        // Print final stats table
        registry.printFinalStatsTable();
    }
}
