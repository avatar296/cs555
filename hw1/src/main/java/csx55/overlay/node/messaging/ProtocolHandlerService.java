package csx55.overlay.node.messaging;

import csx55.overlay.spanning.RoutingTable;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.MessageRoutingHelper;
import csx55.overlay.wireformats.DeregisterResponse;
import csx55.overlay.wireformats.LinkWeights;
import csx55.overlay.wireformats.MessagingNodesList;
import csx55.overlay.wireformats.PeerIdentification;
import csx55.overlay.wireformats.RegisterResponse;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * Service responsible for handling protocol messages in the MessagingNode. Manages registration
 * responses, peer connections, routing information, and peer identification within the overlay
 * network.
 */
public class ProtocolHandlerService {
  private final TCPConnectionsCache peerConnections;
  private final MessageRoutingService routingService;
  private List<String> allNodes;
  private String nodeId;
  private boolean connectionStatusPrinted = false;

  /** Maximum time to wait for connections to stabilize in milliseconds */
  private static final int MAX_CONNECTION_WAIT_MS = 5000;

  /** Polling interval for checking connection count in milliseconds */
  private static final int CONNECTION_POLL_INTERVAL_MS = 100;

  /** Number of stable iterations required before considering connections established */
  private static final int REQUIRED_STABLE_ITERATIONS = 3;

  /** Maximum polling iterations (MAX_CONNECTION_WAIT_MS / CONNECTION_POLL_INTERVAL_MS) */
  private static final int MAX_POLLING_ITERATIONS =
      MAX_CONNECTION_WAIT_MS / CONNECTION_POLL_INTERVAL_MS;

  /**
   * Constructs a new ProtocolHandlerService.
   *
   * @param peerConnections the cache for managing TCP connections to peer nodes
   * @param routingService the service responsible for message routing
   */
  public ProtocolHandlerService(
      TCPConnectionsCache peerConnections, MessageRoutingService routingService) {
    this.peerConnections = peerConnections;
    this.routingService = routingService;
  }

  /**
   * Sets the node information.
   *
   * @param nodeId the unique identifier for this node
   * @param allNodes the list of all nodes in the overlay network
   */
  public void setNodeInfo(String nodeId, List<String> allNodes) {
    this.nodeId = nodeId;
    this.allNodes = allNodes;
  }

  /**
   * Handles a registration response from the registry.
   *
   * @param response the registration response to process
   */
  public void handleRegisterResponse(RegisterResponse response) {
    LoggerUtil.info("ProtocolHandler", "Registration response: " + response.getAdditionalInfo());
  }

  /**
   * Handles a deregistration response from the registry.
   *
   * @param response the deregistration response to process
   * @return true if the node should exit, false otherwise
   */
  public boolean handleDeregisterResponse(DeregisterResponse response) {
    if (response.getStatusCode() == 1) {
      LoggerUtil.info("ProtocolHandler", "Successfully exited overlay");
      System.out.println("exited overlay");
      return true;
    }
    return false;
  }

  /**
   * Handles the peer list received from the registry. Establishes TCP connections to all peer nodes
   * and sends identification.
   *
   * @param peerList the list of peer nodes to connect to
   * @param listener the TCP connection listener for handling incoming messages
   * @throws IOException if an error occurs while establishing connections
   */
  public void handlePeerList(
      MessagingNodesList peerList, TCPConnection.TCPConnectionListener listener)
      throws IOException {
    for (String peer : peerList.getPeerNodes()) {
      String[] parts = peer.split(":");
      Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
      try {
        TCPConnection connection = new TCPConnection(socket, listener);
        connection.setRemoteNodeId(peer);
        peerConnections.addConnection(peer, connection);

        PeerIdentification identification = new PeerIdentification(nodeId);
        MessageRoutingHelper.sendEventSafely(
            connection, identification, String.format("sending peer identification to %s", peer));
      } catch (Exception e) {
        try {
          socket.close();
        } catch (IOException closeException) {
          LoggerUtil.warn("ProtocolHandler", "Failed to close socket after connection error", closeException);
        }
        throw e;
      }
    }

    if (!connectionStatusPrinted) {
      try {
        // Wait for connection count to stabilize
        int stableCount = peerConnections.size();
        int stableIterations = 0;

        for (int i = 0; i < MAX_POLLING_ITERATIONS; i++) {
          Thread.sleep(CONNECTION_POLL_INTERVAL_MS);
          int currentCount = peerConnections.size();

          if (currentCount == stableCount) {
            stableIterations++;
            if (stableIterations >= REQUIRED_STABLE_ITERATIONS) {
              // Connection count has been stable for REQUIRED_STABLE_ITERATIONS *
              // CONNECTION_POLL_INTERVAL_MS
              break;
            }
          } else {
            // Connection count changed, reset stability counter
            stableCount = currentCount;
            stableIterations = 0;
          }
        }

        LoggerUtil.info(
            "ProtocolHandler",
            "All connections established. Number of connections: " + peerConnections.size());
        System.out.println(
            "All connections are established. Number of connections: " + peerConnections.size());
        connectionStatusPrinted = true;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LoggerUtil.warn("ProtocolHandler", "Interrupted while waiting for connections");
      }
    }
  }

  /**
   * Handles link weight information from the registry. Updates the list of all nodes and configures
   * the routing table.
   *
   * @param linkWeights the link weight information for the overlay network
   */
  public void handleLinkWeights(LinkWeights linkWeights) {
    allNodes.clear();
    for (LinkWeights.LinkInfo link : linkWeights.getLinks()) {
      String node1 = link.nodeA;
      String node2 = link.nodeB;
      if (!allNodes.contains(node1)) allNodes.add(node1);
      if (!allNodes.contains(node2)) allNodes.add(node2);
    }

    LoggerUtil.info(
        "ProtocolHandler",
        "Populated allNodes with " + allNodes.size() + " nodes from link weights");

    RoutingTable routingTable = new RoutingTable(nodeId);
    routingTable.updateLinkWeights(linkWeights);
    routingService.updateRoutingTable(routingTable);

    LoggerUtil.info(
        "ProtocolHandler", "Link weights received and processed. Ready to send messages.");
    System.out.println("Link weights received and processed. Ready to send messages.");
  }

  /**
   * Handles peer identification messages from connected nodes. Updates the connection cache with
   * the correct peer ID mapping.
   *
   * @param identification the peer identification message
   * @param connection the TCP connection from the peer
   */
  public synchronized void handlePeerIdentification(
      PeerIdentification identification, TCPConnection connection) {
    String peerId = identification.getNodeId();

    connection.setRemoteNodeId(peerId);

    TCPConnection existingConnection = peerConnections.getConnection(peerId);
    if (existingConnection != null && existingConnection != connection) {
      LoggerUtil.debug("ProtocolHandler", "Ignoring duplicate connection from peer: " + peerId);
      return;
    }

    boolean found = false;
    for (String key : peerConnections.getAllConnections().keySet()) {
      if (peerConnections.getConnection(key) == connection) {
        found = true;
        if (!key.equals(peerId)) {
          peerConnections.removeConnection(key);
          peerConnections.addConnection(peerId, connection);
        }
        break;
      }
    }

    if (!found) {
      peerConnections.addConnection(peerId, connection);
      LoggerUtil.debug(
          "ProtocolHandler",
          "Peer connection received from "
              + peerId
              + ". Total connections: "
              + peerConnections.size());
    }
  }

  /**
   * Gets the list of all nodes in the overlay network.
   *
   * @return the list of all node identifiers
   */
  public List<String> getAllNodes() {
    return allNodes;
  }
}
