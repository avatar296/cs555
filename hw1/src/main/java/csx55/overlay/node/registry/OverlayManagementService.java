package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.MessageRoutingHelper;
import csx55.overlay.util.OverlayCreator;
import csx55.overlay.util.OverlayCreator.Link;
import csx55.overlay.wireformats.LinkWeights;
import csx55.overlay.wireformats.MessagingNodesList;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry-side overlay management service. Handles overlay construction, link weight assignment,
 * and topology management. Integrates with OverlayCreator for topology construction and handles
 * message broadcasting to all registered nodes.
 */
public class OverlayManagementService {

  private final NodeRegistrationService registrationService;
  private OverlayCreator.ConnectionPlan currentOverlay;
  private int lastConnectionRequirement = -1;
  private final Object overlayLock = new Object();

  public OverlayManagementService(NodeRegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  /**
   * Sets up the overlay network with the specified connection requirement.
   *
   * @param connectionRequirement number of connections per node
   */
  public void setupOverlay(int connectionRequirement) {
    Map<String, TCPConnection> nodes = registrationService.getRegisteredNodes();

    if (nodes.size() < connectionRequirement + 1) {
      LoggerUtil.error(
          "OverlayManagement",
          "Insufficient nodes for overlay. Need at least "
              + (connectionRequirement + 1)
              + ", have "
              + nodes.size());
      System.out.println("Error: Not enough nodes registered for overlay");
      return;
    }

    List<String> nodeIds = new ArrayList<>(nodes.keySet());

    synchronized (overlayLock) {
      currentOverlay = OverlayCreator.buildOverlay(nodeIds, connectionRequirement);
      lastConnectionRequirement = connectionRequirement;
    }

    // Send peer lists to each node
    Map<String, Set<String>> adjacency = currentOverlay.getAdjacency();

    for (Map.Entry<String, Set<String>> entry : adjacency.entrySet()) {
      String nodeId = entry.getKey();
      List<String> peers = new ArrayList<>(entry.getValue());

      TCPConnection connection = nodes.get(nodeId);
      if (connection != null) {
        MessagingNodesList peerList = new MessagingNodesList(peers);
        try {
          connection.sendEvent(peerList);
          LoggerUtil.debug("OverlayManagement", "Sent peer list to " + nodeId + ": " + peers);
        } catch (IOException e) {
          LoggerUtil.error("OverlayManagement", "Failed to send peer list to " + nodeId, e);
        }
      } else {
        LoggerUtil.warn("OverlayManagement", "Skipping peer list for unregistered node: " + nodeId);
      }
    }

    System.out.println("setup completed with " + connectionRequirement + " connections");
  }

  /** Sends link weights to all registered nodes (parameterless version called by Registry). */
  public void sendOverlayLinkWeights() {
    synchronized (overlayLock) {
      if (currentOverlay == null) {
        LoggerUtil.error("OverlayManagement", "Overlay has not been set up. Cannot send weights.");
        System.out.println("Error: Overlay must be setup before sending weights");
        return;
      }
      sendOverlayLinkWeights(currentOverlay);
    }
  }

  /** Lists all link weights in the current overlay. */
  public void listWeights() {
    synchronized (overlayLock) {
      if (currentOverlay == null || currentOverlay.getUniqueLinks().isEmpty()) {
        System.out.println("No weights available - overlay not setup or no links exist");
        return;
      }

      // Get currently registered nodes to filter out stale links
      Map<String, TCPConnection> registeredNodes = registrationService.getRegisteredNodes();

      // Print weights in same format as when broadcasting, filtering out stale nodes
      List<LinkWeights.LinkInfo> linkInfos = convertToLinkInfos(currentOverlay.getUniqueLinks());
      for (LinkWeights.LinkInfo linkInfo : linkInfos) {
        // Only print links where both nodes are currently registered
        if (registeredNodes.containsKey(linkInfo.nodeA)
            && registeredNodes.containsKey(linkInfo.nodeB)) {
          System.out.println(linkInfo.nodeA + " " + linkInfo.nodeB + " " + linkInfo.weight);
        } else {
          LoggerUtil.debug(
              "OverlayManagement",
              "Skipping stale link: " + linkInfo.nodeA + " - " + linkInfo.nodeB);
        }
      }
    }
  }

  /**
   * Accept a ConnectionPlan (from OverlayCreator) and send Link_Weights to all registered nodes.
   * This method will deduplicate undirected edges and ensure weights are in 1..10.
   */
  public void sendOverlayLinkWeights(OverlayCreator.ConnectionPlan plan) {
    if (plan == null) throw new IllegalArgumentException("plan null");

    // Convert Links to LinkInfos
    List<LinkWeights.LinkInfo> linkInfos = convertToLinkInfos(plan.getUniqueLinks());

    LoggerUtil.info("OverlayManagement", "Broadcasting " + linkInfos.size() + " link weights");

    // Broadcast to all nodes
    broadcastLinkWeightsToNodes(linkInfos);

    System.out.println("link weights assigned");
  }

  /** Converts OverlayCreator.Link objects to LinkWeights.LinkInfo objects. */
  private List<LinkWeights.LinkInfo> convertToLinkInfos(Set<Link> links) {
    return links.stream()
        .map(link -> new LinkWeights.LinkInfo(link.getNodeA(), link.getNodeB(), link.getWeight()))
        .collect(Collectors.toList());
  }

  /** Broadcasts link weights to all registered nodes. */
  private void broadcastLinkWeightsToNodes(List<LinkWeights.LinkInfo> linkInfos) {
    Map<String, TCPConnection> nodes = registrationService.getRegisteredNodes();
    LinkWeights linkWeights = new LinkWeights(linkInfos);

    int successfulSends =
        MessageRoutingHelper.broadcastToAllNodes(nodes, linkWeights, "broadcasting link weights");

    if (successfulSends > 0) {
      LoggerUtil.info(
          "OverlayManagement", "Successfully sent link weights to " + successfulSends + " nodes");
    } else {
      LoggerUtil.error("OverlayManagement", "Failed to send link weights to any nodes");
    }
  }

  /**
   * Gets the current overlay plan.
   *
   * @return the current overlay connection plan, or null if not setup
   */
  public OverlayCreator.ConnectionPlan getCurrentOverlay() {
    synchronized (overlayLock) {
      return currentOverlay;
    }
  }

  /**
   * Checks if the overlay network is currently set up.
   *
   * @return true if overlay is setup, false otherwise
   */
  public boolean isOverlaySetup() {
    synchronized (overlayLock) {
      return currentOverlay != null;
    }
  }

  /**
   * Rebuilds the overlay network with the same connection requirement as before. This is used when
   * nodes disconnect and the overlay needs to be reconstructed.
   */
  public void rebuildOverlay() {
    synchronized (overlayLock) {
      if (currentOverlay == null || lastConnectionRequirement == -1) {
        LoggerUtil.warn(
            "OverlayManagement", "Cannot rebuild overlay - no overlay was previously setup");
        return;
      }

      LoggerUtil.info(
          "OverlayManagement", "Rebuilding overlay with CR=" + lastConnectionRequirement);
      setupOverlay(lastConnectionRequirement);
    }
  }
}
