package csx55.pastry.node;

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
  private final String id; // 16-bit hex ID (4 hex digits)

  private ServerSocket serverSocket;
  private int peerPort;
  private String peerHost;
  private NodeInfo selfInfo;
  private File storageDir;
  private volatile boolean running;

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
      // Start TCP server on random port
      startPeerServer();

      // Create storage directory
      createStorageDirectory();

      // Register with Discovery Node
      registerWithDiscovery();

      System.out.println("Peer " + id + " is running");
      System.out.println("Listening on: " + peerHost + ":" + peerPort);
      System.out.println("Storage directory: " + storageDir.getAbsolutePath());

      runCommandLoop();

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
    Thread serverThread = new Thread(this::runPeerServer);
    serverThread.setDaemon(true);
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

      // TODO: Handle peer-to-peer messages (JOIN, ROUTING_TABLE_UPDATE, etc.)

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
        // TODO: compute actual hash of filename
        String hash = "0000"; // placeholder
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
