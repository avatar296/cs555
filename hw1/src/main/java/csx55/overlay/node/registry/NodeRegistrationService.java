package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.NodeValidationHelper;
import csx55.overlay.util.RegistrationResponseHelper;
import csx55.overlay.wireformats.DeregisterRequest;
import csx55.overlay.wireformats.RegisterRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for managing node registration and deregistration in the
 * overlay network.
 * Maintains a registry of all active nodes, validates registration requests,
 * and handles connection
 * lifecycle events.
 *
 * This service ensures proper validation of node identities and manages the
 * synchronized access
 * to the registered nodes collection.
 */
public class NodeRegistrationService {
  private final TCPConnectionsCache connectionsCache;
  private final Map<String, TCPConnection> registeredNodes = new HashMap<>();
  private final Object registrationLock = new Object();
  private TaskOrchestrationService taskService;

  /**
   * Constructs a new NodeRegistrationService.
   *
   * @param connectionsCache the cache for managing TCP connections
   */
  public NodeRegistrationService(TCPConnectionsCache connectionsCache) {
    this.connectionsCache = connectionsCache;
  }

  /**
   * Sets the task orchestration service reference.
   *
   * @param taskService the task orchestration service to use
   */
  public void setTaskService(TaskOrchestrationService taskService) {
    this.taskService = taskService;
  }

  /**
   * Handles a node registration request. Validates the request parameters
   * including IP address and
   * port, verifies the actual connection source, and adds the node to the
   * registry.
   *
   * @param request    the registration request containing node information
   * @param connection the TCP connection from the requesting node
   * @throws IOException if an error occurs while sending the response
   */
  public void handleRegisterRequest(RegisterRequest request, TCPConnection connection)
      throws IOException {
    // Validate IP format
    NodeValidationHelper.ValidationResult ipFormatResult = NodeValidationHelper
        .validateIpFormat(request.getIpAddress());
    if (!ipFormatResult.isSuccess()) {
      RegistrationResponseHelper.sendRegistrationFailure(
          connection, ipFormatResult.getErrorMessage());
      return;
    }

    // Validate port
    NodeValidationHelper.ValidationResult portResult = NodeValidationHelper.validatePort(request.getPortNumber());
    if (!portResult.isSuccess()) {
      RegistrationResponseHelper.sendRegistrationFailure(connection, portResult.getErrorMessage());
      return;
    }

    // Validate IP match
    NodeValidationHelper.ValidationResult ipMatchResult = NodeValidationHelper.validateIpMatch(request.getIpAddress(),
        connection.getSocket());
    if (!ipMatchResult.isSuccess()) {
      RegistrationResponseHelper.sendRegistrationFailure(
          connection, ipMatchResult.getErrorMessage());
      return;
    }

    String nodeId = NodeValidationHelper.createNodeId(request.getIpAddress(), request.getPortNumber());

    synchronized (registrationLock) {
      // Check if node already exists
      NodeValidationHelper.ValidationResult existsResult = NodeValidationHelper.validateNodeDoesNotExist(nodeId,
          registeredNodes);
      if (!existsResult.isSuccess()) {
        RegistrationResponseHelper.sendRegistrationFailure(
            connection, existsResult.getErrorMessage());
        return;
      }

      registeredNodes.put(nodeId, connection);
      connectionsCache.addConnection(nodeId, connection);

      RegistrationResponseHelper.sendRegistrationSuccess(
          connection, nodeId, registeredNodes.size());
      LoggerUtil.info(
          "NodeRegistration",
          "Registered node: " + nodeId + " (total: " + registeredNodes.size() + ")");
    }
  }

  /**
   * Handles a node deregistration request. Validates the request, ensures no task
   * is in progress,
   * and removes the node from the registry.
   *
   * @param request    the deregistration request containing node information
   * @param connection the TCP connection from the requesting node
   * @throws IOException if an error occurs while sending the response
   */
  public void handleDeregisterRequest(DeregisterRequest request, TCPConnection connection)
      throws IOException {
    String nodeId = NodeValidationHelper.createNodeId(request.getIpAddress(), request.getPortNumber());

    // Validate IP match
    NodeValidationHelper.ValidationResult ipMatchResult = NodeValidationHelper.validateIpMatch(request.getIpAddress(),
        connection.getSocket());
    if (!ipMatchResult.isSuccess()) {
      RegistrationResponseHelper.sendDeregistrationFailure(connection, "IP mismatch");
      return;
    }

    if (taskService != null && taskService.isTaskInProgress()) {
      RegistrationResponseHelper.sendDeregistrationFailure(
          connection, "Task is in progress. Please wait for completion.");
      return;
    }

    synchronized (registrationLock) {
      // Check if node exists
      NodeValidationHelper.ValidationResult existsResult = NodeValidationHelper.validateNodeExists(nodeId,
          registeredNodes);
      if (!existsResult.isSuccess()) {
        RegistrationResponseHelper.sendDeregistrationFailure(
            connection, existsResult.getErrorMessage());
        return;
      }

      // Double-check the connection matches before removal
      TCPConnection registeredConnection = registeredNodes.get(nodeId);
      if (registeredConnection != connection) {
        RegistrationResponseHelper.sendDeregistrationFailure(
            connection, "Connection mismatch for deregistration");
        return;
      }

      registeredNodes.remove(nodeId);
      connectionsCache.removeConnection(nodeId);

      RegistrationResponseHelper.sendDeregistrationSuccess(connection, registeredNodes.size());
      LoggerUtil.info(
          "NodeRegistration",
          "Deregistered node: " + nodeId + " (remaining: " + registeredNodes.size() + ")");
    }
  }

  /**
   * Lists all registered messaging nodes to the console. Displays each node's
   * identifier (IP:port)
   * or a message if no nodes are registered.
   */
  public void listMessagingNodes() {
    synchronized (registrationLock) {
      if (registeredNodes.isEmpty()) {
        System.out.println("No messaging nodes registered.");
        return;
      }

      for (String nodeId : registeredNodes.keySet()) {
        System.out.println(nodeId);
      }
    }
  }

  /**
   * Gets a copy of all registered nodes.
   *
   * @return a new map containing all registered node IDs and their connections
   */
  public Map<String, TCPConnection> getRegisteredNodes() {
    synchronized (registrationLock) {
      return new HashMap<>(registeredNodes);
    }
  }

  /**
   * Gets the count of registered nodes.
   *
   * @return the number of nodes currently registered
   */
  public int getNodeCount() {
    synchronized (registrationLock) {
      return registeredNodes.size();
    }
  }

  /**
   * Handles the loss of a connection to a registered node. Removes the
   * disconnected node from the
   * registry and connections cache.
   *
   * @param lostConnection the connection that was lost
   */
  public void handleConnectionLost(TCPConnection lostConnection) {
    synchronized (registrationLock) {
      String nodeToRemove = null;
      for (Map.Entry<String, TCPConnection> entry : registeredNodes.entrySet()) {
        if (entry.getValue() == lostConnection) {
          nodeToRemove = entry.getKey();
          break;
        }
      }

      if (nodeToRemove != null) {
        registeredNodes.remove(nodeToRemove);
        connectionsCache.removeConnection(nodeToRemove);
        LoggerUtil.info(
            "NodeRegistration",
            "Removed disconnected node: "
                + nodeToRemove
                + " (remaining: "
                + registeredNodes.size()
                + ")");
      } else {
        LoggerUtil.debug(
            "NodeRegistration", "Connection lost for unregistered or already removed node");
      }
    }
  }
}
