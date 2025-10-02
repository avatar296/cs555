package csx55.pastry.node;

import csx55.pastry.routing.LeafSet;
import csx55.pastry.routing.RoutingTable;
import csx55.pastry.transport.Message;
import csx55.pastry.transport.MessageFactory;
import csx55.pastry.transport.MessageType;
import csx55.pastry.util.HexUtil;
import csx55.pastry.util.NodeInfo;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

public class Peer {
  private static final Logger logger = Logger.getLogger(Peer.class.getName());

  private final String discoverHost;
  private final int discoverPort;
  private final String id;

  private ServerSocket serverSocket;
  private Thread serverThread;
  private int peerPort;
  private String peerHost;
  private NodeInfo selfInfo;
  private File storageDir;
  private volatile boolean running;

  private LeafSet leafSet;
  private RoutingTable routingTable;
  private java.util.Set<NodeInfo> joinResponders;

  public Peer(String discoverHost, int discoverPort, String id) {
    this.discoverHost = discoverHost;
    this.discoverPort = discoverPort;
    this.id = HexUtil.normalize(id);
    this.running = true;

    // Validate ID
    if (!HexUtil.isValidHexId(this.id)) {
      throw new IllegalArgumentException("Invalid hex ID: " + id + " (must be 4 hex digits)");
    }
  }

  public void start() {
    logger.info("Peer " + id + " starting...");
    logger.info("Discovery Node: " + discoverHost + ":" + discoverPort);

    try {
      // Initialize routing structures
      leafSet = new LeafSet(id);
      routingTable = new RoutingTable(id);
      joinResponders = new java.util.HashSet<>();

      // Start TCP server on random port
      startPeerServer();

      // Create storage directory
      createStorageDirectory();

      // Register with Discovery Node
      registerWithDiscovery();

      // Join the DHT network
      joinNetwork();

      // Wait a bit for any late-arriving updates
      Thread.sleep(3000);

      // Log final routing structures
      logRoutingStructures();

      System.out.println("Peer " + id + " is running");
      System.out.println("Listening on: " + peerHost + ":" + peerPort);
      System.out.println("Storage directory: " + storageDir.getAbsolutePath());

      // Only run command loop if running interactively
      if (System.console() != null) {
        runCommandLoop();
      } else {
        // Running non-interactively (background mode), keep main thread alive
        try {
          serverThread.join();
        } catch (InterruptedException e) {
          logger.info("Main thread interrupted, shutting down");
          shutdown();
        }
      }

    } catch (Exception e) {
      logger.severe("Failed to start peer: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private void startPeerServer() throws IOException {
    serverSocket = new ServerSocket(0); // random port
    peerPort = serverSocket.getLocalPort();
    peerHost = InetAddress.getLocalHost().getHostAddress();

    selfInfo = new NodeInfo(id, peerHost, peerPort, "");

    // Start server thread
    serverThread = new Thread(this::runPeerServer);
    serverThread.setDaemon(false);
    serverThread.start();

    logger.info("Peer server started on " + peerHost + ":" + peerPort);
  }

  private void runPeerServer() {
    try {
      while (running) {
        try {
          Socket clientSocket = serverSocket.accept();
          Thread handler = new Thread(() -> handlePeerConnection(clientSocket));
          handler.start();
        } catch (IOException e) {
          if (running) {
            logger.warning("Error accepting peer connection: " + e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      logger.severe("Peer server error: " + e.getMessage());
    }
  }

  private void handlePeerConnection(Socket socket) {
    try (DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message request = Message.read(dis);
      logger.info("Received peer message: " + request.getType());

      switch (request.getType()) {
        case JOIN_REQUEST:
          handleJoinRequest(request, dos);
          break;
        case JOIN_RESPONSE:
          handleJoinResponse(request);
          break;
        case ROUTING_TABLE_UPDATE:
          handleRoutingTableUpdate(request);
          break;
        case LEAF_SET_UPDATE:
          handleLeafSetUpdate(request);
          break;
        case LOOKUP:
          handleLookup(request);
          break;
        case STORE_FILE:
          handleStoreFile(request, dos);
          break;
        case RETRIEVE_FILE:
          handleRetrieveFile(request, dos);
          break;
        default:
          logger.warning("Unhandled message type: " + request.getType());
      }

    } catch (IOException e) {
      logger.warning("Error handling peer connection: " + e.getMessage());
    } finally {
      try {
        socket.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  private void handleJoinRequest(Message request, DataOutputStream dos) throws IOException {
    MessageFactory.JoinRequestData data = MessageFactory.extractJoinRequest(request);

    // Increment hop count and add self to path
    int newHopCount = data.hopCount + 1;
    data.path.add(id);

    logger.info(
        "JOIN request for "
            + data.destination
            + " (hop "
            + newHopCount
            + ", from "
            + data.requester.getId()
            + ")");

    // Check if we're the destination (closest node)
    boolean isDestination = isClosestNode(data.destination);

    if (isDestination) {
      // We're the destination - send leaf set back to requester
      logger.info("We are destination for " + data.destination);

      NodeInfo left = leafSet.getLeft();
      NodeInfo right = leafSet.getRight();

      sendJoinResponse(data.requester, -1, new NodeInfo[0], left, right);

      // Add requester to our leaf set
      leafSet.addNode(data.requester);
      logger.info("Added " + data.requester.getId() + " to our leaf set");

      // Send update to requester so it knows about us
      sendUpdateToNode(data.requester, MessageType.LEAF_SET_UPDATE);
      logger.info("Sent LEAF_SET_UPDATE to " + data.requester.getId());

    } else {
      // We're intermediate - send our routing table row back to requester and forward
      int prefixLen = getCommonPrefixLength(id, data.destination);
      NodeInfo[] row = routingTable.getRow(prefixLen);

      logger.info("Intermediate node - sending row " + prefixLen + " to requester");

      sendJoinResponse(data.requester, prefixLen, row, null, null);

      // Add requester to our routing table
      routingTable.addNode(data.requester);
      logger.info("Added " + data.requester.getId() + " to our routing table");

      // Send update to requester so it knows about us
      sendUpdateToNode(data.requester, MessageType.ROUTING_TABLE_UPDATE);
      logger.info("Sent ROUTING_TABLE_UPDATE to " + data.requester.getId());

      // Forward JOIN_REQUEST to next hop
      NodeInfo nextHop = route(data.destination);

      // Check if next hop is the requester itself (circular routing)
      if (nextHop != null && nextHop.getId().equals(data.requester.getId())) {
        // We're actually the closest node - treat as destination
        logger.info("Next hop is requester - treating this as destination");
        NodeInfo left = leafSet.getLeft();
        NodeInfo right = leafSet.getRight();
        sendJoinResponse(data.requester, -1, new NodeInfo[0], left, right);
        leafSet.addNode(data.requester);
        logger.info("Added " + data.requester.getId() + " to our leaf set");
        sendUpdateToNode(data.requester, MessageType.LEAF_SET_UPDATE);
        logger.info("Sent LEAF_SET_UPDATE to " + data.requester.getId());
      } else if (nextHop != null) {
        forwardJoinRequest(data.requester, data.destination, newHopCount, data.path, nextHop);
      } else {
        logger.warning("No next hop found for " + data.destination);
      }
    }
  }

  private void sendJoinResponse(
      NodeInfo requester, int rowNum, NodeInfo[] row, NodeInfo left, NodeInfo right) {
    try (Socket socket = new Socket(requester.getHost(), requester.getPort());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message response = MessageFactory.createJoinResponse(rowNum, row, left, right);
      response.write(dos);

      if (rowNum == -1) {
        logger.info(
            "Sent JOIN_RESPONSE to "
                + requester.getId()
                + " with leaf set (left: "
                + (left != null ? left.getId() : "null")
                + ", right: "
                + (right != null ? right.getId() : "null")
                + ")");
      } else {
        logger.info(
            "Sent JOIN_RESPONSE to "
                + requester.getId()
                + " with routing table row "
                + rowNum
                + " (entries: "
                + (row != null ? row.length : 0)
                + ")");
      }

    } catch (IOException e) {
      logger.warning(
          "Failed to send JOIN_RESPONSE to " + requester.getId() + ": " + e.getMessage());
    }
  }

  private boolean isClosestNode(String targetId) {
    long selfDist = computeDistance(id, targetId);

    // Check leaf set
    for (NodeInfo node : leafSet.getAllNodes()) {
      long dist = computeDistance(node.getId(), targetId);
      if (dist < selfDist) {
        return false;
      }
    }

    // Check routing table
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          long dist = computeDistance(node.getId(), targetId);
          if (dist < selfDist) {
            return false;
          }
        }
      }
    }

    return true; // We're closest
  }

  private void forwardJoinRequest(
      NodeInfo requester,
      String destination,
      int hopCount,
      java.util.List<String> path,
      NodeInfo nextHop) {
    try (Socket socket = new Socket(nextHop.getHost(), nextHop.getPort());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message joinMsg = MessageFactory.createJoinRequest(requester, destination, hopCount, path);
      joinMsg.write(dos);

      logger.info("Forwarded JOIN to " + nextHop.getId());

    } catch (IOException e) {
      logger.warning("Failed to forward JOIN to " + nextHop.getId() + ": " + e.getMessage());
    }
  }

  private void handleRoutingTableUpdate(Message request) throws IOException {
    NodeInfo node = MessageFactory.extractNodeInfo(request);
    logger.info("Received ROUTING_TABLE_UPDATE for node " + node.getId());
    routingTable.addNode(node);
    logger.info("Updated routing table with " + node.getId());
  }

  private void handleLeafSetUpdate(Message request) throws IOException {
    NodeInfo node = MessageFactory.extractNodeInfo(request);
    logger.info("Received LEAF_SET_UPDATE for node " + node.getId());
    leafSet.addNode(node);
    logger.info("Updated leaf set with " + node.getId());
  }

  private void handleJoinResponse(Message request) throws IOException {
    MessageFactory.JoinResponseData data = MessageFactory.extractJoinResponse(request);

    if (data.rowNum == -1) {
      // Leaf set response
      logger.info(
          "Received JOIN_RESPONSE with leaf set (left: "
              + (data.left != null ? data.left.getId() : "null")
              + ", right: "
              + (data.right != null ? data.right.getId() : "null")
              + ")");
      if (data.left != null) {
        leafSet.addNode(data.left);
        logger.info("Added to leaf set: " + data.left.getId());
      }
      if (data.right != null) {
        leafSet.addNode(data.right);
        logger.info("Added to leaf set: " + data.right.getId());
      }
    } else {
      // Routing table row response
      logger.info(
          "Received JOIN_RESPONSE with routing table row "
              + data.rowNum
              + " (entries: "
              + (data.routingRow != null ? data.routingRow.length : 0)
              + ")");
      routingTable.setRow(data.rowNum, data.routingRow);
      logger.info("Set routing table row " + data.rowNum);
    }
  }

  private void handleLookup(Message request) throws IOException {
    MessageFactory.LookupRequestData data = MessageFactory.extractLookupRequest(request);

    // Add self to path
    data.path.add(id);

    logger.info("LOOKUP for " + data.targetId + " (hop " + data.path.size() + ")");

    // Check if we're the destination (closest node)
    boolean isDestination = isClosestNode(data.targetId);

    if (isDestination) {
      // We're responsible - send LOOKUP_RESPONSE back to origin
      logger.info("We are responsible for " + data.targetId);

      try (Socket socket = new Socket(data.origin.getHost(), data.origin.getPort());
          DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

        Message response = MessageFactory.createLookupResponse(data.targetId, selfInfo, data.path);
        response.write(dos);

        logger.info("Sent LOOKUP_RESPONSE to " + data.origin.getId());
      }
    } else {
      // Forward to next hop
      NodeInfo nextHop = route(data.targetId);

      if (nextHop != null) {
        try (Socket socket = new Socket(nextHop.getHost(), nextHop.getPort());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

          Message forwardMsg =
              MessageFactory.createLookupRequest(data.targetId, data.origin, data.path);
          forwardMsg.write(dos);

          logger.info("Forwarded LOOKUP to " + nextHop.getId());
        }
      } else {
        logger.warning("No next hop found for LOOKUP " + data.targetId);
      }
    }
  }

  private void handleStoreFile(Message request, DataOutputStream dos) throws IOException {
    MessageFactory.StoreFileData data = MessageFactory.extractStoreFileRequest(request);

    logger.info("Storing file: " + data.filename + " (" + data.fileData.length + " bytes)");

    try {
      // Write file to storage directory
      File file = new File(storageDir, data.filename);
      java.nio.file.Files.write(file.toPath(), data.fileData);

      logger.info("Successfully stored file: " + data.filename);

      // Send ACK
      Message ack = MessageFactory.createAck(true, "File stored successfully");
      ack.write(dos);

    } catch (IOException e) {
      logger.warning("Failed to store file " + data.filename + ": " + e.getMessage());

      // Send error ACK
      Message ack = MessageFactory.createAck(false, "Failed to store file: " + e.getMessage());
      ack.write(dos);
    }
  }

  private void handleRetrieveFile(Message request, DataOutputStream dos) throws IOException {
    String filename = MessageFactory.extractRetrieveFileRequest(request);

    logger.info("Retrieving file: " + filename);

    try {
      // Read file from storage directory
      File file = new File(storageDir, filename);

      if (!file.exists()) {
        logger.warning("File not found: " + filename);
        Message response = MessageFactory.createFileDataResponse(filename, null, false);
        response.write(dos);
        return;
      }

      byte[] fileData = java.nio.file.Files.readAllBytes(file.toPath());

      logger.info("Successfully retrieved file: " + filename + " (" + fileData.length + " bytes)");

      // Send file data
      Message response = MessageFactory.createFileDataResponse(filename, fileData, true);
      response.write(dos);

    } catch (IOException e) {
      logger.warning("Failed to retrieve file " + filename + ": " + e.getMessage());

      // Send error response
      Message response = MessageFactory.createFileDataResponse(filename, null, false);
      response.write(dos);
    }
  }

  private void createStorageDirectory() {
    storageDir = new File("/tmp/" + id);
    if (!storageDir.exists()) {
      if (storageDir.mkdirs()) {
        logger.info("Created storage directory: " + storageDir.getAbsolutePath());
      } else {
        logger.warning("Failed to create storage directory");
      }
    }
  }

  private void registerWithDiscovery() throws IOException {
    try (Socket socket = new Socket(discoverHost, discoverPort);
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      // Send REGISTER message
      Message registerMsg = MessageFactory.createRegisterMessage(selfInfo);
      registerMsg.write(dos);

      // Read response
      Message response = Message.read(dis);
      if (response.getType() == MessageType.REGISTER_RESPONSE) {
        boolean success = MessageFactory.extractRegisterSuccess(response);
        if (success) {
          logger.info("Successfully registered with Discovery node");
        } else {
          logger.severe("Registration failed: ID collision detected");
          System.err.println("Error: ID " + id + " is already in use. Please choose another ID.");
          System.exit(1);
        }
      }
    }
  }

  private void deregisterFromDiscovery() {
    try (Socket socket = new Socket(discoverHost, discoverPort);
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message deregisterMsg = MessageFactory.createDeregisterMessage(id);
      deregisterMsg.write(dos);
      logger.info("Deregistered from Discovery node");

    } catch (IOException e) {
      logger.warning("Failed to deregister: " + e.getMessage());
    }
  }

  private void joinNetwork() throws IOException {
    // Get random entry point from Discovery
    NodeInfo entryPoint = getRandomNodeFromDiscovery();

    if (entryPoint == null) {
      logger.info("We are the first peer in the network");
      logger.info("Leaf set after JOIN: left=null, right=null");
      logger.info("Routing table after JOIN: 0 entries");
      return;
    }

    logger.info("Joining network via entry point: " + entryPoint.getId());

    // Send JOIN_REQUEST to entry point
    try (Socket socket = new Socket(entryPoint.getHost(), entryPoint.getPort());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      java.util.List<String> path = new java.util.ArrayList<>();
      Message joinMsg = MessageFactory.createJoinRequest(selfInfo, id, 0, path);
      joinMsg.write(dos);

      logger.info("Sent JOIN_REQUEST to " + entryPoint.getId());
    }

    // Wait for JOIN_RESPONSE messages to arrive
    // (they will be handled by handleJoinResponse asynchronously)
    try {
      Thread.sleep(2000); // Wait 2 seconds for responses
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Send updates to all nodes in routing table and leaf set
    sendUpdatesToNetwork();

    logger.info("Join protocol complete");

    // Log final routing structures for verification
    NodeInfo left = leafSet.getLeft();
    NodeInfo right = leafSet.getRight();
    logger.info(
        "Leaf set after JOIN: left="
            + (left != null ? left.getId() : "null")
            + ", right="
            + (right != null ? right.getId() : "null"));

    int rtEntries = 0;
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        if (routingTable.getEntry(row, col) != null) {
          rtEntries++;
        }
      }
    }
    logger.info("Routing table after JOIN: " + rtEntries + " entries");
  }

  private NodeInfo getRandomNodeFromDiscovery() throws IOException {
    try (Socket socket = new Socket(discoverHost, discoverPort);
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message request = MessageFactory.createGetRandomNodeRequest(id);
      request.write(dos);

      Message response = Message.read(dis);
      return MessageFactory.extractRandomNode(response);
    }
  }

  private void sendUpdatesToNetwork() {
    // Send ROUTING_TABLE_UPDATE to all nodes in routing table
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          sendUpdateToNode(node, MessageType.ROUTING_TABLE_UPDATE);
        }
      }
    }

    // Send LEAF_SET_UPDATE to all nodes in leaf set
    for (NodeInfo node : leafSet.getAllNodes()) {
      sendUpdateToNode(node, MessageType.LEAF_SET_UPDATE);
    }
  }

  private void sendUpdateToNode(NodeInfo node, MessageType updateType) {
    // Don't send updates to ourselves
    if (node.getId().equals(id)) {
      logger.fine("Skipping update to self");
      return;
    }

    try (Socket socket = new Socket(node.getHost(), node.getPort());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message updateMsg =
          updateType == MessageType.ROUTING_TABLE_UPDATE
              ? MessageFactory.createRoutingTableUpdate(selfInfo)
              : MessageFactory.createLeafSetUpdate(selfInfo);

      updateMsg.write(dos);
      logger.info("Sent " + updateType + " to " + node.getId());

    } catch (IOException e) {
      logger.warning("Failed to send update to " + node.getId() + ": " + e.getMessage());
    }
  }

  // Core routing algorithm: finds next hop for a given key
  private NodeInfo route(String key) {
    // Step 1: Check if key is in leaf set range
    if (leafSet.isInRange(key)) {
      NodeInfo closest = leafSet.getClosestNode(key);
      if (closest != null) {
        return closest;
      }
    }

    // Step 2: Use routing table
    int prefixLength = getCommonPrefixLength(id, key);

    // If key matches our ID completely, we're the destination
    if (prefixLength >= 4) {
      return null;
    }

    // Get next digit in key at position prefixLength
    int nextDigit = Character.digit(key.charAt(prefixLength), 16);

    // Look up in routing table
    NodeInfo nextHop = routingTable.getEntry(prefixLength, nextDigit);
    if (nextHop != null) {
      return nextHop;
    }

    // Step 3: Fallback - find any node numerically closer
    return findCloserNode(key);
  }

  // Helper: find any node closer to key than self
  private NodeInfo findCloserNode(String key) {
    long selfDist = computeDistance(id, key);
    NodeInfo closest = null;
    long minDist = selfDist;

    // Check leaf set
    for (NodeInfo node : leafSet.getAllNodes()) {
      long dist = computeDistance(node.getId(), key);
      if (dist < minDist) {
        minDist = dist;
        closest = node;
      }
    }

    // Check routing table
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          long dist = computeDistance(node.getId(), key);
          if (dist < minDist) {
            minDist = dist;
            closest = node;
          }
        }
      }
    }

    return closest;
  }

  // Helper: compute length of common prefix between two IDs
  private int getCommonPrefixLength(String id1, String id2) {
    int matches = 0;
    int minLen = Math.min(id1.length(), id2.length());

    for (int i = 0; i < minLen; i++) {
      if (id1.charAt(i) == id2.charAt(i)) {
        matches++;
      } else {
        break;
      }
    }

    return matches;
  }

  // Helper: compute circular distance between two IDs
  private long computeDistance(String id1, String id2) {
    long val1 = Long.parseLong(id1, 16);
    long val2 = Long.parseLong(id2, 16);
    long diff = Math.abs(val1 - val2);
    long wrapDiff = 0x10000 - diff; // 2^16 - diff
    return Math.min(diff, wrapDiff);
  }

  private void logRoutingStructures() {
    NodeInfo left = leafSet.getLeft();
    NodeInfo right = leafSet.getRight();
    logger.info(
        "FINAL Leaf set: left="
            + (left != null ? left.getId() : "null")
            + ", right="
            + (right != null ? right.getId() : "null"));

    int rtEntries = 0;
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        if (routingTable.getEntry(row, col) != null) {
          rtEntries++;
        }
      }
    }
    logger.info("FINAL Routing table: " + rtEntries + " entries");
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
            printLeafSet();
            break;
          case "routing-table":
            printRoutingTable();
            break;
          case "list-files":
            listFiles();
            break;
          case "exit":
            shutdown();
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

  private void printLeafSet() {
    String output = leafSet.toOutputFormat();
    if (output.isEmpty()) {
      System.out.println("Leaf set is empty");
    } else {
      System.out.println(output);
    }
  }

  private void printRoutingTable() {
    System.out.println(routingTable.toOutputFormat());
  }

  private void listFiles() {
    File[] files = storageDir.listFiles();
    if (files == null || files.length == 0) {
      System.out.println("No files stored");
      return;
    }

    // Print in format: filename, hash
    for (File file : files) {
      if (file.isFile()) {
        String filename = file.getName();
        String hash = csx55.pastry.util.HashUtil.hashFilename(filename);
        System.out.println(filename + ", " + hash);
      }
    }
  }

  private void shutdown() {
    System.out.println("Peer " + id + " exiting...");
    running = false;

    // Deregister from Discovery
    deregisterFromDiscovery();

    // Close server socket
    try {
      if (serverSocket != null) {
        serverSocket.close();
      }
    } catch (IOException e) {
      logger.warning("Error closing server socket: " + e.getMessage());
    }

    System.exit(0);
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
