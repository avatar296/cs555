package csx55.pastry.node;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * Discovery Node for Pastry DHT
 *
 * <p>Responsibilities: - Maintain registry of all peers in the system - Return ONE random node for
 * network entry - Detect ID collisions
 */
public class Discover {
  private static final Logger logger = Logger.getLogger(Discover.class.getName());

  private final int port;

  public Discover(int port) {
    this.port = port;
  }

  public void start() {
    logger.info("Discovery Node starting on port " + port);

    // TODO: Start TCP server
    // TODO: Listen for peer registrations
    // TODO: Maintain list of registered peers

    System.out.println("Discovery Node running on port " + port);
    System.out.println("Ready to accept peer registrations");

    runCommandLoop();
  }

  private void runCommandLoop() {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();

        if (line.isEmpty()) continue;

        switch (line) {
          case "list-nodes":
            // TODO: Implement list-nodes command
            System.out.println("No nodes registered yet");
            break;
          case "exit":
            System.out.println("Shutting down Discovery Node");
            System.exit(0);
            break;
          default:
            System.out.println("Unknown command: " + line);
            System.out.println("Available commands: list-nodes, exit");
        }
      }
    } catch (Exception e) {
      logger.severe("Error in command loop: " + e.getMessage());
    }
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Usage: java csx55.pastry.node.Discover <port>");
      System.exit(1);
    }

    int port = Integer.parseInt(args[0]);
    Discover discover = new Discover(port);
    discover.start();
  }
}
