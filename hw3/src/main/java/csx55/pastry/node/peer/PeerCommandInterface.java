package csx55.pastry.node.peer;

import csx55.pastry.routing.LeafSet;
import csx55.pastry.routing.RoutingTable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Logger;

public class PeerCommandInterface {
  private static final Logger logger = Logger.getLogger(PeerCommandInterface.class.getName());

  private final String id;
  private final LeafSet leafSet;
  private final RoutingTable routingTable;
  private final FileStorageManager fileStorage;
  private final DiscoveryClient discoveryClient;
  private final PeerServer peerServer;
  private final PeerStatistics statistics;

  public PeerCommandInterface(
      String id,
      LeafSet leafSet,
      RoutingTable routingTable,
      FileStorageManager fileStorage,
      DiscoveryClient discoveryClient,
      PeerServer peerServer,
      PeerStatistics statistics) {
    this.id = id;
    this.leafSet = leafSet;
    this.routingTable = routingTable;
    this.fileStorage = fileStorage;
    this.discoveryClient = discoveryClient;
    this.peerServer = peerServer;
    this.statistics = statistics;
  }

  public void runCommandLoop() {
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
            printLeafSet();
            break;
          case "routing-table":
            printRoutingTable();
            break;
          case "list-files":
            listFiles();
            break;
          case "stats":
            printStats();
            break;
          case "exit":
            shutdown();
            break;
          default:
            System.out.println("Unknown command: " + line);
            System.out.println(
                "Available commands: id, leaf-set, routing-table, list-files, stats, exit");
        }
      }
    } catch (Exception e) {
      logger.severe("Error in command loop: " + e.getMessage());
    }
  }

  private void printLeafSet() {
    String output = leafSet.toOutputFormat();
    // Print nothing if empty, otherwise print the output
    if (!output.isEmpty()) {
      System.out.println(output);
    }
  }

  private void printRoutingTable() {
    System.out.println(routingTable.toOutputFormat());
  }

  private void listFiles() {
    String[] files = fileStorage.listFilesWithHashes();
    if (files.length == 0) {
      System.out.println("No files stored");
      return;
    }

    for (String fileInfo : files) {
      System.out.println(fileInfo);
    }
  }

  private void printStats() {
    System.out.println(statistics.toOutputFormat());
  }

  private void shutdown() {
    System.out.println("Peer " + id + " exiting...");

    discoveryClient.deregister(id);
    peerServer.stop();

    System.exit(0);
  }
}
