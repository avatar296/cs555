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
  private volatile boolean running = true;
  private volatile State state = State.ACCEPTING;

  public Registry(int port) {
    this.port = port;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(port);
    Log.info("Registry listening on port " + port);

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
      while ((line = console.readLine()) != null) {
        if (!RegistryCommands.process(line, actions)) {
          break;
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
          if (msg.startsWith(Protocol.REGISTER)) {
            String[] parts = msg.split(" ");
            String ip = parts[1];
            int port = Integer.parseInt(parts[2]);
            String id = NodeId.toId(ip, port);
            synchronized (nodes) {
              if (state != State.ACCEPTING) {
                Log.warn("Late registration rejected for " + id + " (overlay already built)");
                return;
              }
              if (!nodes.contains(id)) nodes.add(id);
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
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void setupOverlay(int poolSize) {
    List<String> alive = new ArrayList<>();
    synchronized (nodes) {
      if (state != State.ACCEPTING) {
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

      if (nodes.size() < 2) {
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
    }
    Log.info("Overlay setup complete with pool size " + poolSize + ". Members:");
    synchronized (nodes) {
      for (String m : nodes) Log.info(" - " + m);
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
