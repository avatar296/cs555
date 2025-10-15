package csx55.pastry.node;

import csx55.pastry.node.peer.DiscoveryClient;
import csx55.pastry.node.peer.FileStorageManager;
import csx55.pastry.node.peer.JoinProtocol;
import csx55.pastry.node.peer.MessageHandler;
import csx55.pastry.node.peer.PeerCommandInterface;
import csx55.pastry.node.peer.PeerServer;
import csx55.pastry.node.peer.PeerStatistics;
import csx55.pastry.node.peer.RoutingEngine;
import csx55.pastry.routing.LeafSet;
import csx55.pastry.routing.RoutingTable;
import csx55.pastry.util.HexUtil;
import csx55.pastry.util.NodeInfo;
import java.util.logging.Logger;

public class Peer {
  private static final Logger logger = Logger.getLogger(Peer.class.getName());

  private final String discoverHost;
  private final int discoverPort;
  private final String id;

  private NodeInfo selfInfo;
  private LeafSet leafSet;
  private RoutingTable routingTable;

  // Helper classes
  private DiscoveryClient discoveryClient;
  private FileStorageManager fileStorage;
  private RoutingEngine routingEngine;
  private JoinProtocol joinProtocol;
  private MessageHandler messageHandler;
  private PeerServer peerServer;
  private PeerCommandInterface commandInterface;
  private PeerStatistics statistics;

  public Peer(String discoverHost, int discoverPort, String id) {
    this.discoverHost = discoverHost;
    this.discoverPort = discoverPort;
    this.id = HexUtil.normalize(id);

    if (!HexUtil.isValidHexId(this.id)) {
      throw new IllegalArgumentException("Invalid hex ID: " + id + " (must be 4 hex digits)");
    }
  }

  public void start() {
    logger.info("Peer " + id + " starting...");
    logger.info("Discovery Node: " + discoverHost + ":" + discoverPort);

    try {
      leafSet = new LeafSet(id);
      routingTable = new RoutingTable(id);
      statistics = new PeerStatistics();

      discoveryClient = new DiscoveryClient(discoverHost, discoverPort);
      fileStorage = new FileStorageManager(id, statistics);
      routingEngine = new RoutingEngine(id, leafSet, routingTable);

      peerServer = new PeerServer(null);
      peerServer.start();

      selfInfo = new NodeInfo(id, peerServer.getHost(), peerServer.getPort(), "");

      messageHandler =
          new MessageHandler(
              selfInfo, leafSet, routingTable, routingEngine, fileStorage, statistics);
      peerServer.setMessageHandler(messageHandler);

      joinProtocol = new JoinProtocol(selfInfo, leafSet, routingTable, discoveryClient);
      commandInterface =
          new PeerCommandInterface(
              id, leafSet, routingTable, fileStorage, discoveryClient, peerServer, statistics);

      discoveryClient.register(selfInfo);
      joinProtocol.joinNetwork();

      Thread.sleep(3000);

      logger.info("Peer " + id + " is running");
      logger.info("Listening on: " + peerServer.getHost() + ":" + peerServer.getPort());
      logger.info("Storage directory: " + fileStorage.getStorageDir().getAbsolutePath());

      commandInterface.runCommandLoop();

    } catch (Exception e) {
      logger.severe("Failed to start peer: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void main(String[] args) {
    if (args.length != 3) {
      System.err.println("Usage: java csx55.pastry.node.Peer <discover-host> <discover-port> <id>");
      System.exit(1);
    }

    String discoverHost = args[0];
    int discoverPort = Integer.parseInt(args[1]);
    String id = args[2];

    Peer peer = new Peer(discoverHost, discoverPort, id);
    peer.start();
  }
}
