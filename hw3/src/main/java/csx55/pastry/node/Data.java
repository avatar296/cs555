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

    try {
      // Extract filename from path
      String filename = extractFilename(filePath);

      // Compute 16-bit hash of filename
      String targetId = csx55.pastry.util.HashUtil.hashFilename(filename);
      System.out.println("Target ID for '" + filename + "': " + targetId);

      // Get random entry point from Discovery
      csx55.pastry.util.NodeInfo entryPoint = getRandomEntryPoint();
      if (entryPoint == null) {
        System.err.println("Error: No peers available in the network");
        return;
      }

      System.out.println("Entry point: " + entryPoint.getId());

      // Perform LOOKUP to find responsible peer
      LookupResult lookupResult = performLookup(targetId, entryPoint);
      if (lookupResult == null) {
        System.err.println("Error: LOOKUP failed");
        return;
      }

      // Print routing path
      System.out.print("Routing path: ");
      for (int i = 0; i < lookupResult.path.size(); i++) {
        System.out.print(lookupResult.path.get(i));
        if (i < lookupResult.path.size() - 1) {
          System.out.print(" -> ");
        }
      }
      System.out.println(" -> " + targetId);

      System.out.println("Responsible peer: " + lookupResult.responsible.getId());

      // Perform store or retrieve operation
      if ("store".equals(mode)) {
        performStore(filename, lookupResult.responsible);
      } else if ("retrieve".equals(mode)) {
        performRetrieve(filename, lookupResult.responsible);
      }

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static class LookupResult {
    csx55.pastry.util.NodeInfo responsible;
    java.util.List<String> path;
  }

  private csx55.pastry.util.NodeInfo getRandomEntryPoint() {
    try (java.net.Socket socket = new java.net.Socket(discoverHost, discoverPort);
        java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream());
        java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream())) {

      csx55.pastry.transport.Message request =
          csx55.pastry.transport.MessageFactory.createGetRandomNodeRequest(null);
      request.write(dos);

      csx55.pastry.transport.Message response = csx55.pastry.transport.Message.read(dis);
      return csx55.pastry.transport.MessageFactory.extractRandomNode(response);

    } catch (Exception e) {
      logger.warning("Failed to get entry point: " + e.getMessage());
      return null;
    }
  }

  private LookupResult performLookup(String targetId, csx55.pastry.util.NodeInfo entryPoint) {
    try (java.net.Socket socket = new java.net.Socket(entryPoint.getHost(), entryPoint.getPort());
        java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream());
        java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream())) {

      // Create a temporary NodeInfo for this Data process (for receiving response)
      // Use a random high port for the response
      java.net.ServerSocket tempServer = new java.net.ServerSocket(0);
      int tempPort = tempServer.getLocalPort();
      String tempHost = java.net.InetAddress.getLocalHost().getHostAddress();
      csx55.pastry.util.NodeInfo origin =
          new csx55.pastry.util.NodeInfo("data", tempHost, tempPort, "");

      // Send LOOKUP request
      java.util.List<String> path = new java.util.ArrayList<>();
      csx55.pastry.transport.Message lookupMsg =
          csx55.pastry.transport.MessageFactory.createLookupRequest(targetId, origin, path);
      lookupMsg.write(dos);

      // Wait for LOOKUP_RESPONSE on temp server
      java.net.Socket responseSocket = tempServer.accept();
      java.io.DataInputStream responseDis =
          new java.io.DataInputStream(responseSocket.getInputStream());

      csx55.pastry.transport.Message response = csx55.pastry.transport.Message.read(responseDis);
      csx55.pastry.transport.MessageFactory.LookupResponseData data =
          csx55.pastry.transport.MessageFactory.extractLookupResponse(response);

      responseSocket.close();
      tempServer.close();

      LookupResult result = new LookupResult();
      result.responsible = data.responsible;
      result.path = data.path;

      return result;

    } catch (Exception e) {
      logger.warning("LOOKUP failed: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  private void performStore(String filename, csx55.pastry.util.NodeInfo responsiblePeer) {
    try {
      // Read file from disk
      java.io.File file = new java.io.File(filePath);
      if (!file.exists()) {
        System.err.println("Error: File not found: " + filePath);
        return;
      }

      byte[] fileData = java.nio.file.Files.readAllBytes(file.toPath());
      System.out.println("Read file: " + filename + " (" + fileData.length + " bytes)");

      // Send STORE_FILE request
      try (java.net.Socket socket =
              new java.net.Socket(responsiblePeer.getHost(), responsiblePeer.getPort());
          java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream());
          java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream())) {

        csx55.pastry.transport.Message storeMsg =
            csx55.pastry.transport.MessageFactory.createStoreFileRequest(filename, fileData);
        storeMsg.write(dos);

        // Wait for ACK
        csx55.pastry.transport.Message response = csx55.pastry.transport.Message.read(dis);
        csx55.pastry.transport.MessageFactory.AckData ack =
            csx55.pastry.transport.MessageFactory.extractAck(response);

        if (ack.success) {
          System.out.println("File stored successfully at peer " + responsiblePeer.getId());
        } else {
          System.err.println("Failed to store file: " + ack.message);
        }
      }

    } catch (Exception e) {
      System.err.println("Error storing file: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void performRetrieve(String filename, csx55.pastry.util.NodeInfo responsiblePeer) {
    try {
      // Send RETRIEVE_FILE request
      try (java.net.Socket socket =
              new java.net.Socket(responsiblePeer.getHost(), responsiblePeer.getPort());
          java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream());
          java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream())) {

        csx55.pastry.transport.Message retrieveMsg =
            csx55.pastry.transport.MessageFactory.createRetrieveFileRequest(filename);
        retrieveMsg.write(dos);

        // Wait for FILE_DATA response
        csx55.pastry.transport.Message response = csx55.pastry.transport.Message.read(dis);
        csx55.pastry.transport.MessageFactory.FileDataResponse fileData =
            csx55.pastry.transport.MessageFactory.extractFileDataResponse(response);

        if (fileData.success && fileData.fileData != null) {
          // Write file to disk
          java.io.File outputFile = new java.io.File(filePath);
          java.nio.file.Files.write(outputFile.toPath(), fileData.fileData);

          System.out.println("File retrieved successfully from peer " + responsiblePeer.getId());
          System.out.println("Saved to: " + filePath + " (" + fileData.fileData.length + " bytes)");
        } else {
          System.err.println(
              "Failed to retrieve file: File not found at peer " + responsiblePeer.getId());
        }
      }

    } catch (Exception e) {
      System.err.println("Error retrieving file: " + e.getMessage());
      e.printStackTrace();
    }
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
