package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.wireformats.PullTrafficSummary;
import csx55.overlay.wireformats.TaskComplete;
import csx55.overlay.wireformats.TaskInitiate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for orchestrating messaging tasks across all nodes in the overlay.
 * Manages task initiation, monitors completion, and triggers statistics collection
 * after task completion.
 * 
 * This service ensures coordinated task execution and handles the complete
 * lifecycle from initiation through statistics gathering.
 */
public class TaskOrchestrationService {
    private int completedNodes = 0;
    private int rounds = 0;
    private boolean taskInProgress = false;
    private final NodeRegistrationService registrationService;
    private final StatisticsCollectionService statisticsService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    /**
     * Constructs a new TaskOrchestrationService.
     * 
     * @param registrationService the node registration service for accessing registered nodes
     * @param statisticsService the statistics collection service for handling traffic summaries
     */
    public TaskOrchestrationService(NodeRegistrationService registrationService,
                                   StatisticsCollectionService statisticsService) {
        this.registrationService = registrationService;
        this.statisticsService = statisticsService;
    }
    
    /**
     * Starts a messaging task with the specified number of rounds.
     * Sends task initiation messages to all registered nodes.
     * 
     * @param numberOfRounds the number of rounds for the messaging task
     */
    public synchronized void startMessaging(int numberOfRounds) {
        if (taskInProgress) {
            LoggerUtil.warn("TaskOrchestration", "Task already in progress. Please wait for completion.");
            return;
        }
        
        if (numberOfRounds <= 0) {
            LoggerUtil.error("TaskOrchestration", "Number of rounds must be greater than 0, received: " + numberOfRounds);
            return;
        }
        
        this.rounds = numberOfRounds;
        this.completedNodes = 0;
        this.taskInProgress = true;
        
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        
        if (registeredNodes.isEmpty()) {
            LoggerUtil.error("TaskOrchestration", "No nodes registered. Cannot start messaging task.");
            taskInProgress = false;
            return;
        }
        
        TaskInitiate message = new TaskInitiate(numberOfRounds);
        try {
            for (TCPConnection connection : registeredNodes.values()) {
                connection.sendEvent(message);
            }
        } catch (IOException e) {
            LoggerUtil.error("TaskOrchestration", "Failed to initiate messaging task with " + numberOfRounds + " rounds", e);
            taskInProgress = false;
        }
    }
    
    /**
     * Handles a task completion notification from a node.
     * Schedules statistics collection when all nodes have completed.
     * 
     * @param taskComplete the task completion message
     * @param connection the TCP connection from the completing node
     */
    public synchronized void handleTaskComplete(TaskComplete taskComplete, TCPConnection connection) {
        completedNodes++;
        
        if (completedNodes == registrationService.getNodeCount()) {
            System.out.println(rounds + " rounds completed");
            scheduler.schedule(this::requestTrafficSummaries, 15, TimeUnit.SECONDS);
            taskInProgress = false;
        }
    }
    
    /**
     * Requests traffic summaries from all registered nodes.
     * Resets the statistics collector and sends pull requests to all nodes.
     */
    private void requestTrafficSummaries() {
        Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();
        
        statisticsService.reset(registeredNodes.size());
        
        PullTrafficSummary message = new PullTrafficSummary();
        
        try {
            for (TCPConnection connection : registeredNodes.values()) {
                connection.sendEvent(message);
            }
        } catch (IOException e) {
            LoggerUtil.error("TaskOrchestration", "Failed to request traffic summaries from nodes", e);
        }
    }
    
    /**
     * Checks if a task is currently in progress.
     * 
     * @return true if a task is in progress, false otherwise
     */
    public synchronized boolean isTaskInProgress() {
        return taskInProgress;
    }
    
    /**
     * Shuts down the task orchestration service.
     * Properly terminates the scheduler thread pool.
     */
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