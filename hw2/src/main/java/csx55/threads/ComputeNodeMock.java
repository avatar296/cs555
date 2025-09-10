package csx55.threads;

public class ComputeNodeMock {
    private final String ip;
    private final int port;
    private ComputeNodeMock successor;

    private final TaskQueueMock taskQueue = new TaskQueueMock();
    private ThreadPoolMock pool;
    private final StatsMock stats = new StatsMock();

    public ComputeNodeMock(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void setSuccessor(ComputeNodeMock successor) {
        this.successor = successor;
    }

    public String getIpPort() {
        return ip + ":" + port;
    }

    public void printOverlay() {
        String succ = (successor != null) ? successor.getIpPort() : "none";
        System.out.println("Node " + getIpPort() + " successor → " + succ);
    }

    public void startThreadPool(int poolSize) {
        pool = new ThreadPoolMock(poolSize, taskQueue);
    }

    public void submitTasks(int n) {
        String nodeId = getIpPort();
        for (int i = 1; i <= n; i++) {
            taskQueue.add(new TaskMock(nodeId, i, stats));
        }
    }

    public StatsMock getStats() {
        return stats;
    }

    // --- Week 3 Mock Load Balancing ---
    public void mockPushTasks(ComputeNodeMock target, int numTasks) {
        this.stats.incrementPushed(numTasks);
        target.getStats().incrementPulled(numTasks);
        System.out.println("Node " + this.getIpPort() + " pushed " + numTasks +
                " tasks to " + target.getIpPort());
    }
}
