package csx55.overlay.node;

import csx55.overlay.node.messaging.*;
import csx55.overlay.spanning.RoutingTable;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.NodeValidationHelper;
import csx55.overlay.wireformats.DataMessage;
import csx55.overlay.wireformats.DeregisterRequest;
import csx55.overlay.wireformats.DeregisterResponse;
import csx55.overlay.wireformats.Event;
import csx55.overlay.wireformats.LinkWeights;
import csx55.overlay.wireformats.MessagingNodesList;
import csx55.overlay.wireformats.PeerIdentification;
import csx55.overlay.wireformats.Protocol;
import csx55.overlay.wireformats.RegisterRequest;
import csx55.overlay.wireformats.RegisterResponse;
import csx55.overlay.wireformats.TaskInitiate;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Represents a messaging node in the overlay network. Handles message routing,
 * peer connections,
 * and communication with the registry. Implements the TCPConnectionListener
 * interface to handle
 * network events.
 *
 * <p>
 * This node can send, receive, and relay messages through the overlay network
 * using shortest
 * path routing based on link weights.
 */
public class MessagingNode implements TCPConnection.TCPConnectionListener {
  private final TCPConnectionsCache peerConnections = new TCPConnectionsCache();
  private final ExecutorService executorService = Executors.newCachedThreadPool();
  private final NodeStatisticsService statisticsService;
  private final MessageRoutingService routingService;
  private final ProtocolHandlerService protocolHandler;
  private final TaskExecutionService taskService;
  private final csx55.overlay.cli.MessagingNodeCommandHandler commandHandler;
  private final List<String> allNodes = new ArrayList<>();

  private volatile boolean running = true;
  private String nodeId;
  private String ipAddress;
  private int portNumber;
  private TCPConnection registryConnection;
  private ServerSocket serverSocket;

  /** Constructs a new MessagingNode and initializes all services. */
  public MessagingNode() {
    this.statisticsService = new NodeStatisticsService();
    this.routingService = new MessageRoutingService(peerConnections, statisticsService);
    this.protocolHandler = new ProtocolHandlerService(peerConnections, routingService);
    this.taskService = new TaskExecutionService(routingService, executorService, statisticsService);
    this.commandHandler = new csx55.overlay.cli.MessagingNodeCommandHandler(this);
  }

  /**
   * Starts the messaging node and connects to the registry. Initializes server
   * socket, registers
   * with the registry, and starts listening for peer connections.
   *
   * @param registryHost the hostname of the registry
   * @param registryPort the port number of the registry
   */
  private void start(String registryHost, int registryPort) {
    try {
      serverSocket = new ServerSocket(0);
      this.portNumber = serverSocket.getLocalPort();
      this.ipAddress = InetAddress.getLocalHost().getHostAddress();
      this.nodeId = NodeValidationHelper.createNodeId(ipAddress, portNumber);

      routingService.setNodeId(nodeId);
      protocolHandler.setNodeInfo(nodeId, allNodes);
      taskService.setNodeInfo(nodeId, ipAddress, portNumber, null);

      Socket socket = new Socket(registryHost, registryPort);
      try {
        registryConnection = new TCPConnection(socket, this);
      } catch (Exception e) {
        try {
          socket.close();
        } catch (IOException closeException) {
          LoggerUtil.warn("MessagingNode", "Failed to close socket after connection error", closeException);
        }
        throw e;
      }
      taskService.setNodeInfo(nodeId, ipAddress, portNumber, registryConnection);

      RegisterRequest request = new RegisterRequest(ipAddress, portNumber);
      registryConnection.sendEvent(request);

      executorService.execute(
          () -> {
            while (running) {
              try {
                Socket peerSocket = serverSocket.accept();
                @SuppressWarnings("unused")
                TCPConnection peerConnection = new TCPConnection(peerSocket, this);
              } catch (IOException e) {
                if (running) {
                  LoggerUtil.error("MessagingNode", "Error accepting peer connection", e);
                }
              }
            }
          });

      new Thread(commandHandler::startCommandLoop).start();

      Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

    } catch (IOException e) {
      LoggerUtil.error("MessagingNode", "Failed to start messaging node", e);
      cleanup();
    }
  }

  /**
   * Performs cleanup operations when the node is shutting down. Closes all
   * connections, stops
   * services, and releases resources.
   */
  private void cleanup() {
    running = false;
    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
      executorService.shutdownNow();
      try {
        if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
          LoggerUtil.warn("MessagingNode", "ExecutorService did not terminate in time");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      peerConnections.closeAll();
      if (registryConnection != null) {
        registryConnection.close();
      }
    } catch (IOException e) {
      LoggerUtil.warn("MessagingNode", "Error during cleanup", e);
    }
  }

  /**
   * Handles incoming events from the network. Processes various protocol messages
   * and delegates to
   * appropriate services.
   *
   * @param event      the event received from the network
   * @param connection the TCP connection that received the event
   */
  @Override
  public void onEvent(Event event, TCPConnection connection) {
    switch (event.getType()) {
      case Protocol.REGISTER_RESPONSE:
        protocolHandler.handleRegisterResponse((RegisterResponse) event);
        break;
      case Protocol.DEREGISTER_RESPONSE:
        if (protocolHandler.handleDeregisterResponse((DeregisterResponse) event)) {
          cleanup();
          System.exit(0);
        }
        break;
      case Protocol.MESSAGING_NODES_LIST:
        try {
          protocolHandler.handlePeerList((MessagingNodesList) event, this);
        } catch (IOException e) {
          LoggerUtil.error("MessagingNode", "Error handling peer list", e);
        }
        break;
      case Protocol.LINK_WEIGHTS:
        protocolHandler.handleLinkWeights((LinkWeights) event);
        LoggerUtil.info(
            "MessagingNode",
            "Updated allNodes list with "
                + allNodes.size()
                + " nodes after receiving link weights");
        break;
      case Protocol.TASK_INITIATE:
        LoggerUtil.info(
            "MessagingNode", "Handling task initiate with allNodes size: " + allNodes.size());
        taskService.handleTaskInitiate((TaskInitiate) event, allNodes);
        break;
      case Protocol.PULL_TRAFFIC_SUMMARY:
        statisticsService.sendTrafficSummary(ipAddress, portNumber, registryConnection);
        break;
      case Protocol.DATA_MESSAGE:
        routingService.handleDataMessage((DataMessage) event);
        break;
      case Protocol.PEER_IDENTIFICATION:
        protocolHandler.handlePeerIdentification((PeerIdentification) event, connection);
        break;
    }
  }

  /**
   * Handles lost connections to the registry or peer nodes. Shuts down if
   * registry connection is
   * lost, otherwise removes peer from cache.
   *
   * @param connection the TCP connection that was lost
   */
  @Override
  public void onConnectionLost(TCPConnection connection) {
    if (connection == registryConnection) {
      LoggerUtil.error("MessagingNode", "Lost connection to Registry. Shutting down...");
      cleanup();
      System.exit(1);
    }

    synchronized (peerConnections) {
      for (String nodeId : peerConnections.getAllConnections().keySet()) {
        if (peerConnections.getConnection(nodeId) == connection) {
          peerConnections.removeConnection(nodeId);
          LoggerUtil.info("MessagingNode", "Lost connection to peer: " + nodeId);
          break;
        }
      }
    }
  }

  /**
   * Deregisters this node from the overlay network. Sends a deregistration
   * request to the registry.
   */
  public void deregister() {
    try {
      DeregisterRequest request = new DeregisterRequest(ipAddress, portNumber);
      registryConnection.sendEvent(request);
    } catch (IOException e) {
      LoggerUtil.error("MessagingNode", "Failed to deregister", e);
    }
  }

  /**
   * Prints the minimum spanning tree from this node's perspective. Shows the
   * shortest paths to all
   * other nodes in the overlay.
   */
  public void printMinimumSpanningTree() {
    RoutingTable table = routingService.getRoutingTable();
    if (table != null) {
      table.printMST();
    }
  }

  /**
   * Main entry point for the MessagingNode application.
   *
   * @param args command line arguments: registry-host registry-port
   */
  public static void main(String[] args) {
    if (args.length != 2) {
      System.out.println(
          "Usage: java csx55.overlay.node.MessagingNode <registry-host> <registry-port>");
      return;
    }
    new MessagingNode().start(args[0], Integer.parseInt(args[1]));
  }
}
