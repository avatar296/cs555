package csx55.overlay.node;

import csx55.overlay.node.messaging.*;
import csx55.overlay.spanning.RoutingTable;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.NodeValidationHelper;
import csx55.overlay.wireformats.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Represents a messaging node in the overlay network. Handles message routing, peer connections,
 * and communication with the registry. Implements the TCPConnectionListener interface to handle
 * network events.
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

  public MessagingNode() {
    this.statisticsService = new NodeStatisticsService();
    this.routingService = new MessageRoutingService(peerConnections, statisticsService);
    this.protocolHandler = new ProtocolHandlerService(peerConnections, routingService);
    this.taskService = new TaskExecutionService(routingService, executorService, statisticsService);
    this.commandHandler = new csx55.overlay.cli.MessagingNodeCommandHandler(this);
  }

  private void start(String registryHost, int registryPort) {
    try {
      serverSocket = new ServerSocket(0);
      this.portNumber = serverSocket.getLocalPort();

      Socket socket = new Socket(registryHost, registryPort);
      try {
        registryConnection = new TCPConnection(socket, this);
      } catch (Exception e) {
        try {
          socket.close();
        } catch (IOException closeException) {
          LoggerUtil.warn("MessagingNode", "Failed to close socket", closeException);
        }
        throw e;
      }

      this.ipAddress = socket.getLocalAddress().getHostAddress();
      this.nodeId = NodeValidationHelper.createNodeId(ipAddress, portNumber);
      routingService.setNodeId(nodeId);
      protocolHandler.setNodeInfo(nodeId, allNodes);
      taskService.setNodeInfo(nodeId, ipAddress, portNumber, registryConnection);

      RegisterRequest request = new RegisterRequest(ipAddress, portNumber);
      registryConnection.sendEvent(request);

      executorService.execute(
          () -> {
            while (running) {
              try {
                Socket peerSocket = serverSocket.accept();
                new TCPConnection(peerSocket, this);
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

  private void cleanup() {
    running = false;
    try {
      if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
      executorService.shutdownNow();
      peerConnections.closeAll();
      if (registryConnection != null) registryConnection.close();
    } catch (IOException e) {
      LoggerUtil.warn("MessagingNode", "Error during cleanup", e);
    }
  }

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
        LinkWeights lw = (LinkWeights) event;
        protocolHandler.handleLinkWeights(lw);

        String canonicalId = findCanonicalIdFromLinkWeights(lw, portNumber);
        if (canonicalId != null && !canonicalId.equals(nodeId)) {
          LoggerUtil.info(
              "MessagingNode", "Canonicalizing node ID " + nodeId + " → " + canonicalId);
          this.nodeId = canonicalId;
          this.ipAddress = canonicalId.substring(0, canonicalId.lastIndexOf(':'));
          routingService.setNodeId(canonicalId);
          protocolHandler.setNodeInfo(canonicalId, allNodes);
          taskService.setNodeInfo(canonicalId, ipAddress, portNumber, registryConnection);
        }

        RoutingTable newTable = new RoutingTable(nodeId);
        newTable.updateLinkWeights(lw);

        newTable.buildMST();
        routingService.updateRoutingTable(newTable);

        LoggerUtil.info(
            "MessagingNode",
            "Updated allNodes list with "
                + allNodes.size()
                + " nodes after receiving link weights");

        break;
      case Protocol.TASK_INITIATE:
        taskService.handleTaskInitiate((TaskInitiate) event, allNodes);
        break;
      case Protocol.PULL_TRAFFIC_SUMMARY:
        statisticsService.sendTrafficSummary(ipAddress, portNumber, registryConnection);
        statisticsService.resetCounters();
        break;
      case Protocol.DATA_MESSAGE:
        routingService.handleDataMessage((DataMessage) event);
        break;
      case Protocol.PEER_IDENTIFICATION:
        protocolHandler.handlePeerIdentification((PeerIdentification) event, connection);
        break;
    }
  }

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

  private String findCanonicalIdFromLinkWeights(LinkWeights linkWeights, int myPort) {
    for (LinkWeights.LinkInfo link : linkWeights.getLinks()) {
      if (endsWithPort(link.nodeA, myPort)) return link.nodeA;
      if (endsWithPort(link.nodeB, myPort)) return link.nodeB;
    }
    return null;
  }

  private boolean endsWithPort(String nodeId, int port) {
    int idx = nodeId.lastIndexOf(':');
    if (idx < 0) return false;
    try {
      return Integer.parseInt(nodeId.substring(idx + 1)) == port;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public void deregister() {
    try {
      DeregisterRequest request = new DeregisterRequest(ipAddress, portNumber);
      registryConnection.sendEvent(request);
    } catch (IOException e) {
      LoggerUtil.error("MessagingNode", "Failed to deregister", e);
    }
  }

  public void printMinimumSpanningTree() {
    RoutingTable table = routingService.getRoutingTable();
    if (table != null) {

      if (!table.waitUntilWeightsReady(3000)) {
        System.out.println("MST has not been calculated yet.");
        return;
      }

      synchronized (table) {
        table.buildMST();
        table.printMST();
      }
    }
  }

  public static void main(String[] args) {
    if (args.length != 2) {
      System.out.println(
          "Usage: java csx55.overlay.node.MessagingNode <registry-host> <registry-port>");
      return;
    }
    new MessagingNode().start(args[0], Integer.parseInt(args[1]));
  }
}
