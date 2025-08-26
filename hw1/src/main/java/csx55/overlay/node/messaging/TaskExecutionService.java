package csx55.overlay.node.messaging;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.wireformats.TaskComplete;
import csx55.overlay.wireformats.TaskInitiate;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;

/**
 * Handles task execution for MessagingNode
 */
public class TaskExecutionService {
    private final MessageRoutingService routingService;
    private final ExecutorService executorService;
    private final NodeStatisticsService statisticsService;
    private String nodeId;
    private String ipAddress;
    private int portNumber;
    private TCPConnection registryConnection;
    
    public TaskExecutionService(MessageRoutingService routingService, ExecutorService executorService, NodeStatisticsService statisticsService) {
        this.routingService = routingService;
        this.executorService = executorService;
        this.statisticsService = statisticsService;
    }
    
    public void setNodeInfo(String nodeId, String ipAddress, int portNumber, TCPConnection registryConnection) {
        this.nodeId = nodeId;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.registryConnection = registryConnection;
    }
    
    public void handleTaskInitiate(TaskInitiate taskInitiate, List<String> allNodes) {
        int rounds = taskInitiate.getRounds();
        // Reset statistics at the beginning of a new task
        statisticsService.resetCounters();
        executorService.execute(() -> performMessagingTask(rounds, allNodes));
    }
    
    private void performMessagingTask(int rounds, List<String> allNodes) {
        Random rand = new Random();
        for (int round = 0; round < rounds; round++) {
            for (int msg = 0; msg < 5; msg++) {
                String sink = allNodes.get(rand.nextInt(allNodes.size()));
                while (sink.equals(nodeId)) {
                    sink = allNodes.get(rand.nextInt(allNodes.size()));
                }
                int payload = rand.nextInt();
                routingService.sendMessage(sink, payload);
            }
        }
        
        try {
            TaskComplete complete = new TaskComplete(ipAddress, portNumber);
            registryConnection.sendEvent(complete);
        } catch (IOException e) {
            System.err.println("Failed to send task complete: " + e.getMessage());
        }
    }
}