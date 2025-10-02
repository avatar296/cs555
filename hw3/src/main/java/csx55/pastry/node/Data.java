package csx55.pastry.node;

import java.util.logging.Logger;

public class Data {
  private static final Logger logger = Logger.getLogger(Data.class.getName());

  private final String discoverHost;
  private final int discoverPort;
  private final String mode; // "store" or "retrieve"
  private final String filePath;

  public Data(String discoverHost, int discoverPort, String mode, String filePath) {
    this.discoverHost = discoverHost;
    this.discoverPort = discoverPort;
    this.mode = mode;
    this.filePath = filePath;
  }

  public void execute() {
    logger.info("Data operation: " + mode + " " + filePath);

    // Extract filename from path
    String filename = extractFilename(filePath);

    // TODO: Compute 16-bit hash of filename
    // TODO: Contact Discovery Node to get random entry point
    // TODO: Route lookup(hash) through DHT
    // TODO: Store or retrieve file

    if ("store".equals(mode)) {
      System.out.println("Store operation not yet implemented");
      System.out.println("Would store: " + filePath);
    } else if ("retrieve".equals(mode)) {
      System.out.println("Retrieve operation not yet implemented");
      System.out.println("Would retrieve to: " + filePath);
    }

    // TODO: Print routing path (list of node IDs + final key)
  }

  private String extractFilename(String path) {
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash >= 0) {
      return path.substring(lastSlash + 1);
    }
    return path;
  }

  public static void main(String[] args) {
    if (args.length != 4) {
      System.err.println(
          "Usage: java csx55.pastry.node.Data <discover-host> <discover-port> <mode> <path>");
      System.err.println("  mode: store or retrieve");
      System.exit(1);
    }

    String discoverHost = args[0];
    int discoverPort = Integer.parseInt(args[1]);
    String mode = args[2];
    String path = args[3];

    if (!mode.equals("store") && !mode.equals("retrieve")) {
      System.err.println("Error: mode must be 'store' or 'retrieve'");
      System.exit(1);
    }

    Data data = new Data(discoverHost, discoverPort, mode, path);
    data.execute();
  }
}
