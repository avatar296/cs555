package csx55.overlay.node.messaging;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.wireformats.TaskComplete;
import csx55.overlay.wireformats.TaskInitiate;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;

/**
 * Service responsible for handling task execution in the MessagingNode.
 * Manages the execution of messaging tasks, including sending messages
 * to random nodes and reporting task completion to the registry.
 */
public class TaskExecutionService {
    private final MessageRoutingService routingService;
    private final ExecutorService executorService;
    private final NodeStatisticsService statisticsService;
    private String nodeId;
    private String ipAddress;
    private int portNumber;
    private TCPConnection registryConnection;
    
    /**
     * Constructs a new TaskExecutionService.
     * 
     * @param routingService the service for routing messages
     * @param executorService the executor for running tasks asynchronously
     * @param statisticsService the service for tracking message statistics
     */
    public TaskExecutionService(MessageRoutingService routingService, ExecutorService executorService, NodeStatisticsService statisticsService) {
        this.routingService = routingService;
        this.executorService = executorService;
        this.statisticsService = statisticsService;
    }
    
    /**
     * Sets the node information and registry connection.
     * 
     * @param nodeId the unique identifier for this node
     * @param ipAddress the IP address of this node
     * @param portNumber the port number of this node
     * @param registryConnection the TCP connection to the registry
     */
    public void setNodeInfo(String nodeId, String ipAddress, int portNumber, TCPConnection registryConnection) {
        this.nodeId = nodeId;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.registryConnection = registryConnection;
    }
    
    /**
     * Handles a task initiation request from the registry.
     * Resets statistics and starts the messaging task asynchronously.
     * 
     * @param taskInitiate the task initiation message containing the number of rounds
     * @param allNodes the list of all nodes in the overlay network
     */
    public void handleTaskInitiate(TaskInitiate taskInitiate, List<String> allNodes) {
        int rounds = taskInitiate.getRounds();
        statisticsService.resetCounters();
        executorService.execute(() -> performMessagingTask(rounds, allNodes));
    }
    
    /**
     * Performs the messaging task by sending messages to random nodes.
     * Each round consists of sending 5 messages to randomly selected nodes.
     * Upon completion, sends a task complete message to the registry.
     * 
     * @param rounds the number of rounds to execute
     * @param allNodes the list of all nodes to choose destinations from
     */
    private void performMessagingTask(int rounds, List<String> allNodes) {
        Random rand = new Random();
        for (int e = 0; e < rounds; e++) {
            for (int e2 = 0; e2 < 5; e2++) {
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
            LoggerUtil.error("TaskExecutionService", "Failed to send task complete message", e);
        }
    }
}