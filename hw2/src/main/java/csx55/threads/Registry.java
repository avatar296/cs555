package csx55.threads;

import csx55.threads.core.Stats;
import csx55.threads.registry.RegistryCommands;
import csx55.threads.registry.StatsAggregator;
import csx55.threads.util.Log;
import csx55.threads.util.NetworkUtil;
import csx55.threads.util.NodeId;
import csx55.threads.util.Protocol;
import java.io.*;
import java.net.*;
import java.util.*;

public class Registry {

  private enum State {
    ACCEPTING,
    OVERLAY_BUILT,
    RUNNING,
    SHUTTING_DOWN
  }

  private final int port;
  private final List<String> nodes = new ArrayList<>();
  private final StatsAggregator aggregator = new StatsAggregator();
  private ServerSocket serverSocket;
  private Thread acceptThread;
  private volatile boolean running = true;
  private volatile State state = State.ACCEPTING;

  public Registry(int port) {
    this.port = port;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(port);
    Log.info("Registry listening on port " + port);

    acceptThread =
        new Thread(
            () -> {
              System.out.println("[REGISTRY-START] Accept thread started on port " + port);
              try {
                while (running) {
                  Socket socket = serverSocket.accept();
                  new Thread(() -> handleConnection(socket)).start();
                }
              } catch (IOException e) {
                if (running) {
                  System.out.println("[REGISTRY-ERROR] Accept thread died: " + e.getMessage());
                  e.printStackTrace(System.out);
                }
              }
              System.out.println("[REGISTRY-START] Accept thread exiting");
            });
    acceptThread.start();

    try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
      RegistryCommands.Actions actions =
          new RegistryCommands.Actions() {
            @Override
            public void setupOverlay(int size) {
              Registry.this.setupOverlay(size);
            }

            @Override
            public void startRounds(int rounds) {
              Registry.this.startRounds(rounds);
            }

            @Override
            public void shutdown() {
              Registry.this.shutdown();
            }
          };

      String line;
      System.out.println("[DEBUG] Console reader ready, waiting for commands");
      int loopCount = 0;
      while (true) {
        System.out.println(
            "[DEBUG] Console loop iteration " + (++loopCount) + ", about to read from console...");
        line = console.readLine();
        if (line == null) {
          System.out.println("[DEBUG] Console readLine returned null (EOF detected)");
          break;
        }
        System.out.println("[DEBUG] Console received line: '" + line + "'");
        boolean continueProcessing = false;
        try {
          continueProcessing = RegistryCommands.process(line, actions);
          System.out.println(
              "[DEBUG] Command processed, continue="
                  + continueProcessing
                  + ", current state="
                  + state);
        } catch (Exception e) {
          System.out.println(
              "[DEBUG] Exception in command processing: "
                  + e.getClass().getName()
                  + " - "
                  + e.getMessage());
          e.printStackTrace(System.out);
        }
        if (!continueProcessing) {
          System.out.println("[DEBUG] Breaking out of console loop");
          break;
        }
        System.out.println(
            "[DEBUG] Waiting for next command... (state="
                + state
                + ", accept thread alive="
                + isAcceptThreadAlive()
                + ")");
      }
      System.out.println(
          "[DEBUG] Console loop ended, line=" + (line == null ? "null (EOF)" : "'" + line + "'"));
      System.out.println(
          "[DEBUG] Final state="
              + state
              + ", running="
              + running
              + ", accept thread alive="
              + isAcceptThreadAlive());
    } catch (Exception e) {
      System.out.println(
          "[DEBUG] Exception in console handling: "
              + e.getClass().getName()
              + " - "
              + e.getMessage());
      e.printStackTrace(System.out);
    }
  }

  private void handleConnection(Socket socket) {
    System.out.println(
        "[DEBUG] New connection from " + socket.getRemoteSocketAddress() + " at state=" + state);
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
          System.out.println(
              "[DEBUG] Message received: '"
                  + msg.substring(0, Math.min(msg.length(), 50))
                  + "'"
                  + (msg.length() > 50 ? "..." : "")
                  + " at state="
                  + state);
          if (msg.startsWith(Protocol.REGISTER)) {
            String[] parts = msg.split(" ");
            String ip = parts[1];
            int port = Integer.parseInt(parts[2]);
            String id = NodeId.toId(ip, port);
            synchronized (nodes) {
              if (state != State.ACCEPTING) {
                System.out.println(
                    "[DEBUG] Late registration rejected for " + id + " (overlay already built)");
                Log.warn("Late registration rejected for " + id + " (overlay already built)");
                return;
              }
              if (!nodes.contains(id)) {
                nodes.add(id);
                System.out.println(
                    "[DEBUG] Node registered: " + id + ", Total nodes: " + nodes.size());
              }
            }
            Log.info("Node registered: " + id);
          } else if (msg.startsWith(Protocol.STATS)) {
            String[] parts = msg.split(" ");
            String nodeId = parts[1];
            Stats stats = (Stats) in.readObject();
            aggregator.record(nodeId, stats);
            synchronized (nodes) {
              aggregator.printFinalIfReady(new ArrayList<>(nodes));
            }
          }
        }
      }
    } catch (EOFException e) {
      System.out.println("[DEBUG] EOF on connection");
    } catch (Exception e) {
      System.out.println(
          "[DEBUG] Exception in handleConnection: "
              + e.getClass().getName()
              + " - "
              + e.getMessage());
      e.printStackTrace(System.out);
    } finally {
      System.out.println(
          "[DEBUG] Connection handler completed for " + socket.getRemoteSocketAddress());
    }
  }

  private void setupOverlay(int poolSize) {
    try {
      System.out.println("[DEBUG] Setup-overlay started with pool size: " + poolSize);
      List<String> alive = new ArrayList<>();
      synchronized (nodes) {
        System.out.println(
            "[DEBUG] Current state: " + state + ", Registered nodes: " + nodes.size());
        if (state != State.ACCEPTING) {
          System.out.println("[DEBUG] Overlay already built, ignoring setup-overlay command");
          Log.warn("Overlay already built, ignoring setup-overlay command");
          return;
        }
        for (String node : nodes) {
          try (Socket probe = new Socket()) {
            NodeId nid = NodeId.parse(node);
            probe.connect(new InetSocketAddress(nid.host(), nid.port()), 500);
            alive.add(node);
          } catch (IOException e) {
            Log.warn("Pruning unreachable node: " + node);
          }
        }
        nodes.clear();
        nodes.addAll(alive);
        System.out.println("[DEBUG] Alive nodes after probe: " + nodes.size());

        if (nodes.size() < 2) {
          System.out.println("[DEBUG] Not enough alive nodes: " + nodes.size() + " (need >= 2)");
          Log.warn("Not enough alive nodes to form a ring (need >= 2).");
          return;
        }

        int n = nodes.size();
        for (int i = 0; i < n; i++) {
          String node = nodes.get(i);
          String successor = nodes.get((i + 1) % n);
          String predecessor = nodes.get((i - 1 + n) % n);
          try {
            NetworkUtil.sendString(
                node,
                Protocol.OVERLAY + " " + successor + " " + predecessor + " " + poolSize + " " + n);
          } catch (IOException e) {
            Log.warn(
                "Failed to send OVERLAY to " + node + " (will remain in list): " + e.getMessage());
          }
        }
        state = State.OVERLAY_BUILT;
        System.out.println("[DEBUG] Overlay messages sent to all nodes");
      }
      System.out.println("[DEBUG] Overlay setup complete");
      Log.info("Overlay setup complete with pool size " + poolSize + ". Members:");
      synchronized (nodes) {
        for (String m : nodes) Log.info(" - " + m);
      }
    } catch (Exception e) {
      System.out.println(
          "[ERROR] Setup-overlay failed: " + e.getClass().getName() + " - " + e.getMessage());
      e.printStackTrace(System.out);
    }
  }

  private void startRounds(int rounds) {
    synchronized (nodes) {
      if (state != State.OVERLAY_BUILT) {
        Log.warn("Cannot start rounds - overlay not built");
        return;
      }
      state = State.RUNNING;
    }
    aggregator.clear();

    synchronized (nodes) {
      for (String node : nodes) {
        try {
          NetworkUtil.sendString(node, Protocol.START + " " + rounds);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    Log.info("Start command sent to all nodes for " + rounds + " rounds.");
  }

  private void shutdown() {
    running = false;
    try {
      serverSocket.close();
    } catch (IOException ignored) {
    }
  }

  private boolean isAcceptThreadAlive() {
    return acceptThread != null && acceptThread.isAlive();
  }

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("[DEBUG] Registry shutdown hook triggered!");
                  Thread.dumpStack();
                }));

    if (args.length != 1) {
      System.err.println("Usage: java csx55.threads.Registry <port>");
      System.exit(1);
    }
    int port = Integer.parseInt(args[0]);
    Registry reg = new Registry(port);
    reg.start();
  }
}
