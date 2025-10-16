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
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Peer {
  private static final Logger logger = Logger.getLogger(Peer.class.getName());
  private static volatile boolean gracefulShutdown = false;

  private static void configureLogging(String peerId) {
    try {
      Logger rootLogger = Logger.getLogger("");
      for (Handler handler : rootLogger.getHandlers()) {
        if (handler instanceof ConsoleHandler) {
          rootLogger.removeHandler(handler);
        }
      }

      // Create logs directory in current working directory
      java.io.File logsDir = new java.io.File("logs");
      if (!logsDir.exists()) {
        logsDir.mkdirs();
      }

      FileHandler fileHandler = new FileHandler("logs/peer-" + peerId + ".log", true);
      fileHandler.setFormatter(new SimpleFormatter());
      fileHandler.setLevel(Level.ALL);
      rootLogger.addHandler(fileHandler);
      rootLogger.setLevel(Level.INFO);
    } catch (IOException e) {
      System.err.println("Failed to configure logging: " + e.getMessage());
    }
  }

  private final String discoverHost;
  private final int discoverPort;
  private final String id;

  private NodeInfo selfInfo;
  private LeafSet leafSet;
  private RoutingTable routingTable;

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

  public static void setGracefulShutdown() {
    gracefulShutdown = true;
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
              selfInfo,
              leafSet,
              routingTable,
              fileStorage,
              discoveryClient,
              peerServer,
              statistics);

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      if (!gracefulShutdown) {
                        logger.info("Peer " + id + " shutting down (forced termination)...");
                        discoveryClient.deregister(id);
                        peerServer.stop();
                      }
                      System.out.flush();
                    } catch (Throwable ignore) {
                      // Ignore exceptions during shutdown
                    }
                  },
                  "PeerShutdown"));

      // Register and join network in background thread
      // Command loop starts immediately so peer can respond to commands quickly
      Thread joinThread =
          new Thread(
              () -> {
                try {
                  discoveryClient.register(selfInfo);
                  joinProtocol.joinNetwork();
                  logger.info("Peer " + id + " joined network successfully");
                } catch (Exception e) {
                  logger.warning("Failed to join network: " + e.getMessage());
                }
              },
              "NetworkJoin");
      joinThread.start();

      // Brief pause to ensure registration completes
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

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

    configureLogging(id);

    Peer peer = new Peer(discoverHost, discoverPort, id);
    peer.start();
  }
}
