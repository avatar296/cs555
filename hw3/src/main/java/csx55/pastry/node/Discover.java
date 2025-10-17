package csx55.pastry.node;

import csx55.pastry.transport.Message;
import csx55.pastry.transport.MessageFactory;
import csx55.pastry.util.NodeInfo;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Discover {
  private static final Logger logger = Logger.getLogger(Discover.class.getName());

  private static void configureLogging(int port) {
    try {
      // Get root logger and remove all console handlers
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

      // Add file handler to write logs to logs/discover-<port>.log
      FileHandler fileHandler = new FileHandler("logs/discover-" + port + ".log", true);
      fileHandler.setFormatter(new SimpleFormatter());
      fileHandler.setLevel(Level.ALL);
      rootLogger.addHandler(fileHandler);
      rootLogger.setLevel(Level.INFO);
    } catch (IOException e) {
      System.err.println("Failed to configure logging: " + e.getMessage());
    }
  }

  private final int port;
  private final Map<String, NodeInfo> registeredNodes;
  private final Random random;
  private ServerSocket serverSocket;
  private volatile boolean running;

  public Discover(int port) {
    this.port = port;
    this.registeredNodes = new ConcurrentHashMap<>();
    this.random = new Random();
    this.running = true;
  }

  public void start() {
    logger.info("Discovery Node starting on port " + port);

    Thread serverThread = new Thread(this::runServer);
    serverThread.setDaemon(false);
    serverThread.start();

    System.out.println("Discovery Node running on port " + port);
    System.out.println("Ready to accept peer registrations");

    runCommandLoop();
  }

  private void runServer() {
    try {
      serverSocket = new ServerSocket(port);
      logger.info("TCP server listening on port " + port);

      while (running) {
        try {
          Socket clientSocket = serverSocket.accept();
          Thread handler = new Thread(() -> handleClient(clientSocket));
          handler.start();
        } catch (IOException e) {
          if (running) {
            logger.warning("Error accepting connection: " + e.getMessage());
          }
        }
      }
    } catch (IOException e) {
      logger.severe("Failed to start server: " + e.getMessage());
    }
  }

  private void handleClient(Socket socket) {
    try (DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message request = Message.read(dis);

      switch (request.getType()) {
        case REGISTER:
          handleRegister(request, dos);
          break;
        case GET_RANDOM_NODE:
          handleGetRandomNode(request, dos);
          break;
        case DEREGISTER:
          handleDeregister(request);
          break;
        case LIST_NODES:
          handleListNodes(dos);
          break;
        default:
          logger.warning("Unknown message type: " + request.getType());
      }

    } catch (IOException e) {
      logger.warning("Error handling client: " + e.getMessage());
    } finally {
      try {
        socket.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  private void handleRegister(Message request, DataOutputStream dos) throws IOException {
    NodeInfo nodeInfo = MessageFactory.extractNodeInfo(request);
    String nodeId = nodeInfo.getId();

    // collision check
    if (registeredNodes.containsKey(nodeId)) {
      logger.warning("ID collision detected for: " + nodeId);
      Message response = MessageFactory.createRegisterResponse(false, "ID collision");
      response.write(dos);
    } else {
      registeredNodes.put(nodeId, nodeInfo);
      logger.info("Registered node: " + nodeInfo);
      Message response = MessageFactory.createRegisterResponse(true, "Success");
      response.write(dos);
    }
  }

  private void handleGetRandomNode(Message request, DataOutputStream dos) throws IOException {
    String excludeId = MessageFactory.extractExcludeId(request);
    NodeInfo randomNode = getRandomNode(excludeId);
    Message response = MessageFactory.createRandomNodeResponse(randomNode);
    response.write(dos);
  }

  private void handleDeregister(Message request) throws IOException {
    String nodeId = MessageFactory.extractNodeId(request);
    registeredNodes.remove(nodeId);
  }

  private void handleListNodes(DataOutputStream dos) throws IOException {
    List<NodeInfo> nodes = new ArrayList<>(registeredNodes.values());
    Message response = MessageFactory.createListNodesResponse(nodes);
    response.write(dos);
  }

  private NodeInfo getRandomNode(String excludeId) {
    if (registeredNodes.isEmpty()) {
      return null;
    }

    List<NodeInfo> nodes = new ArrayList<>();
    for (NodeInfo node : registeredNodes.values()) {
      if (excludeId == null || !node.getId().equals(excludeId)) {
        nodes.add(node);
      }
    }

    if (nodes.isEmpty()) {
      return null;
    }

    return nodes.get(random.nextInt(nodes.size()));
  }

  private void runCommandLoop() {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();

        if (line.isEmpty()) continue;

        switch (line) {
          case "list-nodes":
            listNodes();
            break;
          case "exit":
            shutdown();
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

  private void listNodes() {
    if (registeredNodes.isEmpty()) {
      System.out.println("No nodes registered");
      System.out.flush();
      return;
    }

    // Print in format: ip:port, id
    registeredNodes.values().stream()
        .sorted((a, b) -> a.getId().compareTo(b.getId()))
        .forEach(node -> System.out.println(node.toOutputFormat()));
    System.out.flush();
  }

  private void shutdown() {
    System.out.println("Shutting down Discovery Node");
    running = false;
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
    if (args.length != 1) {
      System.err.println("Usage: java csx55.pastry.node.Discover <port>");
      System.exit(1);
    }

    int port = Integer.parseInt(args[0]);

    configureLogging(port);

    Discover discover = new Discover(port);
    discover.start();
  }
}
