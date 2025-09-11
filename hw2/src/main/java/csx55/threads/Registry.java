package csx55.threads;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Registry {

  private final int port;
  private final List<String> nodes = new ArrayList<>();
  private final Map<String, Stats> nodeStats = new ConcurrentHashMap<>();
  private ServerSocket serverSocket;
  private volatile boolean running = true;
  private volatile boolean statsPrinted = false;

  public Registry(int port) {
    this.port = port;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(port);
    System.out.println("Registry listening on port " + port);

    new Thread(
            () -> {
              try {
                while (running) {
                  Socket socket = serverSocket.accept();
                  new Thread(() -> handleConnection(socket)).start();
                }
              } catch (IOException e) {
                if (running) e.printStackTrace();
              }
            })
        .start();

    try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = console.readLine()) != null) {
        if (line.startsWith("setup-overlay")) {
          String[] parts = line.trim().split("\\s+");
          if (parts.length != 2) {
            System.out.println("Usage: setup-overlay <thread-pool-size>");
            continue;
          }
          int poolSize = Integer.parseInt(parts[1]);
          setupOverlay(poolSize);
        } else if (line.startsWith("start")) {
          String[] parts = line.trim().split("\\s+");
          if (parts.length != 2) {
            System.out.println("Usage: start <number-of-rounds>");
            continue;
          }
          int rounds = Integer.parseInt(parts[1]);
          startRounds(rounds);
        } else if (line.equals("quit")) {
          shutdown();
          break;
        } else {
          System.out.println("Unknown command: " + line);
        }
      }
    }
  }

  private void handleConnection(Socket socket) {
    try {
      PushbackInputStream pin = new PushbackInputStream(socket.getInputStream());
      int first = pin.read();
      if (first == -1) {
        socket.close();
        return;
      }
      pin.unread(first);

      try (ObjectInputStream in = new ObjectInputStream(pin)) {
        Object obj = in.readObject();
        if (obj instanceof String) {
          String msg = (String) obj;
          if (msg.startsWith("REGISTER")) {
            String[] parts = msg.split(" ");
            String ip = parts[1];
            int port = Integer.parseInt(parts[2]);
            String id = ip + ":" + port;
            synchronized (nodes) {
              if (!nodes.contains(id)) nodes.add(id);
            }
            System.out.println("Node registered: " + id);
          } else if (msg.startsWith("STATS")) {
            String[] parts = msg.split(" ");
            String nodeId = parts[1];
            Stats stats = (Stats) in.readObject();
            nodeStats.put(nodeId, stats);

            if (nodeStats.size() == nodes.size()) {
              printFinalStats();
            }
          }
        }
      }
    } catch (EOFException e) {
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void setupOverlay(int poolSize) {
    List<String> alive = new ArrayList<>();
    synchronized (nodes) {
      for (String node : nodes) {
        String[] np = node.split(":");
        try (Socket probe = new Socket()) {
          probe.connect(new InetSocketAddress(np[0], Integer.parseInt(np[1])), 500);
          alive.add(node);
        } catch (IOException e) {
          System.out.println("Pruning unreachable node: " + node);
        }
      }
      nodes.clear();
      nodes.addAll(alive);

      if (nodes.size() < 2) {
        System.out.println("Not enough alive nodes to form a ring (need >= 2).");
        return;
      }

      int n = nodes.size();
      for (int i = 0; i < n; i++) {
        String node = nodes.get(i);
        String successor = nodes.get((i + 1) % n);
        String predecessor = nodes.get((i - 1 + n) % n);
        String[] parts = node.split(":");
        try (Socket sock = new Socket(parts[0], Integer.parseInt(parts[1]));
            ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
          out.writeObject("OVERLAY " + successor + " " + predecessor + " " + poolSize);
          out.flush();
        } catch (IOException e) {
          System.out.println(
              "Failed to send OVERLAY to " + node + " (will remain in list): " + e.getMessage());
        }
      }
    }
    System.out.println("Overlay setup complete with pool size " + poolSize + ". Members:");
    synchronized (nodes) {
      for (String m : nodes) System.out.println(" - " + m);
    }
  }

  private void startRounds(int rounds) {
    nodeStats.clear();
    statsPrinted = false;

    synchronized (nodes) {
      for (String node : nodes) {
        String[] parts = node.split(":");
        try (Socket sock = new Socket(parts[0], Integer.parseInt(parts[1]));
            ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
          out.writeObject("START " + rounds);
          out.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    System.out.println("Start command sent to all nodes for " + rounds + " rounds.");
  }

  private void printFinalStats() {
    if (statsPrinted) return;
    statsPrinted = true;

    long totalCompleted = nodeStats.values().stream().mapToLong(Stats::getCompleted).sum();

    for (String node : nodes) {
      Stats s = nodeStats.get(node);
      double percent = (100.0 * s.getCompleted()) / totalCompleted;
      System.out.printf(
          Locale.US,
          "%s %d %d %d %d %.8f%n",
          node,
          s.getGenerated(),
          s.getPulled(),
          s.getPushed(),
          s.getCompleted(),
          percent);
    }
    System.out.println("Total completed: " + totalCompleted);
  }

  private void shutdown() {
    running = false;
    try {
      serverSocket.close();
    } catch (IOException ignored) {
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: java csx55.threads.Registry <port>");
      System.exit(1);
    }
    int port = Integer.parseInt(args[0]);
    Registry reg = new Registry(port);
    reg.start();
  }
}
