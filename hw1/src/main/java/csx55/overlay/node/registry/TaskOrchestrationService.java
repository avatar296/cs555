package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.wireformats.PullTrafficSummary;
import csx55.overlay.wireformats.TaskComplete;
import csx55.overlay.wireformats.TaskInitiate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for orchestrating messaging tasks across nodes
 */
public class TaskOrchestrationService {
    private int completedNodes = 0;
    private int rounds = 0;
    private final NodeRegistrationService registrationService;
    private final StatisticsCollectionService statisticsService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public TaskOrchestrationService(NodeRegistrationService registrationService,
                                   StatisticsCollectionService statisticsService) {
        this.registrationService = registrationService;
        this.statisticsService = statisticsService;
    }
    
    public void startMessaging(int numberOfRounds) {
        this.rounds = numberOfRounds;
        this.completedNodes = 0;
        
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        
        if (registeredNodes.isEmpty()) {
            System.out.println("No nodes registered. Cannot start messaging.");
            return;
        }
        
        statisticsService.reset(registeredNodes.size());
        
        TaskInitiate message = new TaskInitiate(numberOfRounds);
        try {
            for (TCPConnection connection : registeredNodes.values()) {
                connection.sendEvent(message);
            }
            System.out.println("Initiated messaging task for " + numberOfRounds + " rounds");
        } catch (IOException e) {
            System.err.println("Failed to initiate messaging task: " + e.getMessage());
        }
    }
    
    public synchronized void handleTaskComplete(TaskComplete taskComplete, TCPConnection connection) {
        String nodeId = taskComplete.getNodeIp() + ":" + taskComplete.getNodePort();
        System.out.println("Task completed by node: " + nodeId);
        
        completedNodes++;
        
        if (completedNodes == registrationService.getNodeCount()) {
            System.out.println(rounds + " rounds completed");
            scheduler.schedule(this::requestTrafficSummaries, 15, TimeUnit.SECONDS);
        }
    }
    
    private void requestTrafficSummaries() {
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        PullTrafficSummary message = new PullTrafficSummary();
        
        try {
            for (TCPConnection connection : registeredNodes.values()) {
                connection.sendEvent(message);
            }
            System.out.println("Requested traffic summaries from all nodes");
        } catch (IOException e) {
            System.err.println("Failed to request traffic summaries: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}