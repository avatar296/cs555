package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.MessageRoutingHelper;
import csx55.overlay.util.OverlayCreator;
import csx55.overlay.wireformats.LinkWeights;
import csx55.overlay.wireformats.MessagingNodesList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for managing the overlay network topology and link weights. Handles overlay
 * setup with specified connection requirements, distributes peer lists to nodes, and manages link
 * weight assignments.
 *
 * <p>This service coordinates the creation of the overlay structure and ensures proper connectivity
 * between nodes based on the connection requirement.
 */
public class OverlayManagementService {
  private final NodeRegistrationService registrationService;
  private volatile OverlayCreator.ConnectionPlan currentOverlay;
  private volatile int connectionRequirement;

  /**
   * Constructs a new OverlayManagementService.
   *
   * @param registrationService the node registration service to use
   */
  public OverlayManagementService(NodeRegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  /**
   * Sets up the overlay network with the specified connection requirement. Creates the overlay
   * topology and sends peer lists to all nodes.
   *
   * @param cr the connection requirement (number of connections per node)
   */
  public void setupOverlay(int cr) {
    if (cr <= 0) {
      LoggerUtil.error(
          "OverlayManagement", "Connection requirement must be greater than 0, received: " + cr);
      return;
    }

    Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();

    synchronized (registeredNodes) {
      if (registeredNodes.size() <= cr) {
        LoggerUtil.error(
            "OverlayManagement",
            "Not enough nodes for CR="
                + cr
                + ". Need more than "
                + cr
                + " nodes, have "
                + registeredNodes.size());
        return;
      }

      connectionRequirement = cr;

      List<String> nodeIds = new ArrayList<>(registeredNodes.keySet());
      LoggerUtil.info("OverlayManagement", "Creating overlay with nodes: " + nodeIds);
      currentOverlay = OverlayCreator.createOverlay(nodeIds, cr);
      LoggerUtil.info(
          "OverlayManagement",
          "Created overlay with " + currentOverlay.getAllLinks().size() + " links");

      Map<String, List<String>> initiators =
          OverlayCreator.determineConnectionInitiators(currentOverlay);

      for (String nodeId : nodeIds) {
        List<String> peers = initiators.getOrDefault(nodeId, new ArrayList<>());
        TCPConnection connection = registeredNodes.get(nodeId);
        if (connection != null) {
          MessagingNodesList message = new MessagingNodesList(peers);
          MessageRoutingHelper.sendEventSafely(
              connection, message, String.format("sending peer list to %s", nodeId));
        }
      }

      LoggerUtil.info(
          "OverlayManagement",
          "Overlay setup completed with CR=" + connectionRequirement + " connections per node");
      System.out.println("setup completed with " + connectionRequirement + " connections");
    }
  }

  /**
   * Sends overlay link weights to all registered nodes. Distributes the weighted link information
   * needed for routing decisions.
   */
  public void sendOverlayLinkWeights() {
    if (currentOverlay == null || currentOverlay.getAllLinks().isEmpty()) {
      LoggerUtil.error("OverlayManagement", "Overlay has not been set up. Cannot send weights.");
      return;
    }

    Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();

    List<LinkWeights.LinkInfo> linkInfos = new ArrayList<>();

    for (OverlayCreator.Link link : currentOverlay.getAllLinks()) {
      LinkWeights.LinkInfo linkInfo =
          new LinkWeights.LinkInfo(link.nodeA, link.nodeB, link.getWeight());
      linkInfos.add(linkInfo);
    }

    LinkWeights message = new LinkWeights(linkInfos);
    int sent =
        MessageRoutingHelper.broadcastToAllNodes(registeredNodes, message, "sending link weights");
    if (sent > 0) {
      LoggerUtil.info("OverlayManagement", "Link weights successfully assigned to all nodes");
      System.out.println("link weights assigned");
    }
  }

  /**
   * Lists all link weights in the current overlay to the console. Displays each link with its
   * associated weight. After dynamic reconstruction, all links in currentOverlay are valid.
   */
  public void listWeights() {
    if (currentOverlay == null || currentOverlay.getAllLinks().isEmpty()) {
      LoggerUtil.warn("OverlayManagement", "No overlay has been configured to list weights");
      System.out.println("ERROR: No overlay configured");
      return;
    }

    LoggerUtil.info(
        "OverlayManagement", "Listing " + currentOverlay.getAllLinks().size() + " overlay links");

    for (OverlayCreator.Link link : currentOverlay.getAllLinks()) {
      String output = link.toString();
      LoggerUtil.info("OverlayManagement", "Link output: '" + output + "'");
      System.out.println(output);
    }
  }

  /**
   * Checks if the overlay has been set up.
   *
   * @return true if overlay is configured, false otherwise
   */
  public boolean isOverlaySetup() {
    return currentOverlay != null && !currentOverlay.getAllLinks().isEmpty();
  }

  /**
   * Gets the current overlay connection plan.
   *
   * @return the current overlay structure, or null if not configured
   */
  public OverlayCreator.ConnectionPlan getCurrentOverlay() {
    return currentOverlay;
  }

  /**
   * Gets the connection requirement for the overlay.
   *
   * @return the number of connections per node
   */
  public int getConnectionRequirement() {
    return connectionRequirement;
  }

  /**
   * Rebuilds the overlay network for the current set of registered nodes. This method is called
   * when nodes disconnect to maintain the proper overlay topology and connection requirements.
   */
  public void rebuildOverlay() {
    Map<String, TCPConnection> currentNodes = registrationService.getRegisteredNodes();

    if (currentNodes.size() <= connectionRequirement) {
      LoggerUtil.warn(
          "OverlayManagement",
          "Too few nodes remaining for CR="
              + connectionRequirement
              + ". Need more than "
              + connectionRequirement
              + " nodes, have "
              + currentNodes.size());
      return;
    }

    // Rebuild overlay with remaining nodes
    List<String> remainingNodeIds = new ArrayList<>(currentNodes.keySet());
    LoggerUtil.info(
        "OverlayManagement", "Rebuilding overlay for remaining nodes: " + remainingNodeIds);

    currentOverlay = OverlayCreator.createOverlay(remainingNodeIds, connectionRequirement);

    LoggerUtil.info(
        "OverlayManagement",
        "Rebuilt overlay: "
            + remainingNodeIds.size()
            + " nodes, "
            + currentOverlay.getAllLinks().size()
            + " links");
  }
}
