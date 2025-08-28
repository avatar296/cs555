package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.MessageRoutingHelper;
import csx55.overlay.wireformats.PullTrafficSummary;
import csx55.overlay.wireformats.TaskComplete;
import csx55.overlay.wireformats.TaskInitiate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for orchestrating messaging tasks across all nodes in the overlay. Manages
 * task initiation, monitors completion, and triggers statistics collection after task completion.
 *
 * <p>This service ensures coordinated task execution and handles the complete lifecycle from
 * initiation through statistics gathering.
 */
public class TaskOrchestrationService {
  private final NodeRegistrationService registrationService;
  private final StatisticsCollectionService statisticsService;
  private final OverlayManagementService overlayService;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private volatile int completedNodes;
  private volatile int rounds;
  private volatile boolean taskInProgress;
  private Set<String> taskParticipants = new HashSet<>();
  private Set<String> completedNodeIds = new HashSet<>();

  /**
   * Constructs a new TaskOrchestrationService.
   *
   * @param registrationService the node registration service for accessing registered nodes
   * @param statisticsService the statistics collection service for handling traffic summaries
   * @param overlayService the overlay management service for checking overlay status
   */
  public TaskOrchestrationService(
      NodeRegistrationService registrationService,
      StatisticsCollectionService statisticsService,
      OverlayManagementService overlayService) {
    this.registrationService = registrationService;
    this.statisticsService = statisticsService;
    this.overlayService = overlayService;
  }

  /**
   * Starts a messaging task with the specified number of rounds. Sends task initiation messages to
   * all registered nodes.
   *
   * @param numberOfRounds the number of rounds for the messaging task
   */
  public synchronized void startMessaging(int numberOfRounds) {
    if (taskInProgress) {
      LoggerUtil.warn("TaskOrchestration", "Task already in progress. Please wait for completion.");
      return;
    }

    if (numberOfRounds <= 0) {
      LoggerUtil.error(
          "TaskOrchestration",
          "Number of rounds must be greater than 0, received: " + numberOfRounds);
      return;
    }

    if (!overlayService.isOverlaySetup()) {
      LoggerUtil.error(
          "TaskOrchestration", "Overlay has not been set up. Cannot start messaging task.");
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

    // Store snapshot of participating nodes
    taskParticipants.clear();
    completedNodeIds.clear();
    taskParticipants.addAll(registeredNodes.keySet());

    LoggerUtil.info(
        "TaskOrchestration",
        "Starting messaging task with "
            + numberOfRounds
            + " rounds for "
            + taskParticipants.size()
            + " nodes");

    TaskInitiate message = new TaskInitiate(numberOfRounds);
    int sent =
        MessageRoutingHelper.broadcastToAllNodes(
            registeredNodes, message, "initiating task with " + numberOfRounds + " rounds");

    if (sent > 0) {
      LoggerUtil.info("TaskOrchestration", "Task initiate messages sent to all " + sent + " nodes");
    } else {
      LoggerUtil.error(
          "TaskOrchestration",
          "Failed to initiate messaging task with " + numberOfRounds + " rounds");
      taskInProgress = false;
    }
  }

  /**
   * Handles a task completion notification from a node. Schedules statistics collection when all
   * nodes have completed.
   *
   * @param taskComplete the task completion message
   * @param connection the TCP connection from the completing node
   */
  public synchronized void handleTaskComplete(TaskComplete taskComplete, TCPConnection connection) {
    String nodeId = taskComplete.getNodeIpAddress() + ":" + taskComplete.getNodePortNumber();

    if (!taskParticipants.contains(nodeId)) {
      LoggerUtil.warn(
          "TaskOrchestration", "Received task complete from non-participant node: " + nodeId);
      return;
    }

    if (completedNodeIds.contains(nodeId)) {
      LoggerUtil.warn("TaskOrchestration", "Duplicate task complete from node: " + nodeId);
      return;
    }

    completedNodeIds.add(nodeId);
    completedNodes++;
    LoggerUtil.info(
        "TaskOrchestration",
        "Received task complete from node "
            + nodeId
            + " ("
            + completedNodes
            + "/"
            + taskParticipants.size()
            + " completed)");

    if (completedNodeIds.size() == taskParticipants.size()) {
      System.out.println(rounds + " rounds completed");
      LoggerUtil.info(
          "TaskOrchestration",
          "All "
              + taskParticipants.size()
              + " participating nodes completed task with "
              + rounds
              + " rounds");
      scheduler.schedule(this::requestTrafficSummaries, 15, TimeUnit.SECONDS);
      taskInProgress = false;
    }
  }

  /**
   * Requests traffic summaries from all registered nodes. Resets the statistics collector and sends
   * pull requests to all nodes.
   */
  private void requestTrafficSummaries() {
    Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();

    statisticsService.reset(registeredNodes.size());

    PullTrafficSummary message = new PullTrafficSummary();

    int sent =
        MessageRoutingHelper.broadcastToAllNodes(
            registeredNodes, message, "requesting traffic summaries");

    if (sent == 0) {
      LoggerUtil.error("TaskOrchestration", "Failed to request traffic summaries from nodes");
    }
  }

  /**
   * Handles when a participant node disconnects during a task. Updates the participant list and
   * checks if task can still complete.
   *
   * @param nodeId the ID of the disconnected node
   */
  public synchronized void handleParticipantDisconnected(String nodeId) {
    if (!taskInProgress || !taskParticipants.contains(nodeId)) {
      return;
    }

    LoggerUtil.warn("TaskOrchestration", "Task participant disconnected: " + nodeId);

    taskParticipants.remove(nodeId);

    // If node hadn't completed yet, check if all remaining nodes have completed
    if (!completedNodeIds.contains(nodeId)) {
      LoggerUtil.info(
          "TaskOrchestration", "Removed incomplete participant. Checking task completion...");

      // Check if all remaining participants have completed
      if (taskParticipants.equals(completedNodeIds)) {
        System.out.println(
            rounds
                + " rounds completed (with "
                + (taskParticipants.size() - completedNodeIds.size())
                + " nodes disconnected)");
        LoggerUtil.info(
            "TaskOrchestration",
            "All remaining " + taskParticipants.size() + " nodes completed task");
        scheduler.schedule(this::requestTrafficSummaries, 15, TimeUnit.SECONDS);
        taskInProgress = false;
      }
    } else {
      // Node had already completed, just update the count
      completedNodeIds.remove(nodeId);
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

  /** Shuts down the task orchestration service. Properly terminates the scheduler thread pool. */
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
