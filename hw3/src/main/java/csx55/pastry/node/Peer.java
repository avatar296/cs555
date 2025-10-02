package csx55.pastry.node;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * Peer Node in Pastry DHT
 *
 * <p>Responsibilities: - Maintain Leaf Set (l=1: 2 neighbors) - Maintain Routing Table (4x16) -
 * Route messages using combined Leaf Set + Routing Table - Store files in /tmp/<peer-id>/ - Handle
 * join protocol
 */
public class Peer {
  private static final Logger logger = Logger.getLogger(Peer.class.getName());

  private final String discoverHost;
  private final int discoverPort;
  private final String id; // 16-bit hex ID (4 hex digits)

  public Peer(String discoverHost, int discoverPort, String id) {
    this.discoverHost = discoverHost;
    this.discoverPort = discoverPort;
    this.id = id.toLowerCase();
  }

  public void start() {
    logger.info("Peer " + id + " starting...");
    logger.info("Discovery Node: " + discoverHost + ":" + discoverPort);

    // TODO: Register with Discovery Node
    // TODO: Get random entry point node
    // TODO: Execute join protocol
    // TODO: Build Leaf Set and Routing Table
    // TODO: Start TCP server for peer-to-peer communication

    System.out.println("Peer " + id + " is running");
    System.out.println("Storage directory: /tmp/" + id + "/");

    runCommandLoop();
  }

  private void runCommandLoop() {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();

        if (line.isEmpty()) continue;

        switch (line) {
          case "id":
            System.out.println(id);
            break;
          case "leaf-set":
            // TODO: Print leaf set
            System.out.println("Leaf set not yet implemented");
            break;
          case "routing-table":
            // TODO: Print routing table
            System.out.println("Routing table not yet implemented");
            break;
          case "list-files":
            // TODO: List files in /tmp/<id>/
            System.out.println("No files stored");
            break;
          case "exit":
            System.out.println("Peer " + id + " exiting...");
            // TODO: Deregister from Discovery Node
            System.exit(0);
            break;
          default:
            System.out.println("Unknown command: " + line);
            System.out.println("Available commands: id, leaf-set, routing-table, list-files, exit");
        }
      }
    } catch (Exception e) {
      logger.severe("Error in command loop: " + e.getMessage());
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
