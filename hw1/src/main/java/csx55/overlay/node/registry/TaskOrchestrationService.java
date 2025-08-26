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
    private boolean taskInProgress = false;
    private final NodeRegistrationService registrationService;
    private final StatisticsCollectionService statisticsService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public TaskOrchestrationService(NodeRegistrationService registrationService,
                                   StatisticsCollectionService statisticsService) {
        this.registrationService = registrationService;
        this.statisticsService = statisticsService;
    }
    
    public synchronized void startMessaging(int numberOfRounds) {
        if (taskInProgress) {
            System.out.println("Task already in progress. Please wait for completion.");
            return;
        }
        
        if (numberOfRounds <= 0) {
            System.out.println("Error: Number of rounds must be greater than 0");
            return;
        }
        
        this.rounds = numberOfRounds;
        this.completedNodes = 0;
        this.taskInProgress = true;
        
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        
        if (registeredNodes.isEmpty()) {
            System.out.println("No nodes registered. Cannot start messaging.");
            taskInProgress = false;
            return;
        }
        
        // Don't reset statistics here - they should be reset after printing the results
        
        TaskInitiate message = new TaskInitiate(numberOfRounds);
        try {
            for (TCPConnection connection : registeredNodes.values()) {
                connection.sendEvent(message);
            }
        } catch (IOException e) {
            System.err.println("Failed to initiate messaging task: " + e.getMessage());
            taskInProgress = false;
        }
    }
    
    public synchronized void handleTaskComplete(TaskComplete taskComplete, TCPConnection connection) {
        completedNodes++;
        
        if (completedNodes == registrationService.getNodeCount()) {
            System.out.println(rounds + " rounds completed");
            scheduler.schedule(this::requestTrafficSummaries, 15, TimeUnit.SECONDS);
            taskInProgress = false;
        }
    }
    
    private void requestTrafficSummaries() {
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        
        // Reset statistics collector to prepare for new summaries
        statisticsService.reset(registeredNodes.size());
        
        PullTrafficSummary message = new PullTrafficSummary();
        
        try {
            for (TCPConnection connection : registeredNodes.values()) {
                connection.sendEvent(message);
            }
        } catch (IOException e) {
            System.err.println("Failed to request traffic summaries: " + e.getMessage());
        }
    }
    
    public synchronized boolean isTaskInProgress() {
        return taskInProgress;
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