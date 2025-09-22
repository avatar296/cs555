package csx55.threads;

import csx55.threads.balance.LoadBalancer;
import csx55.threads.balance.RoundAggregator;
import csx55.threads.core.OverlayState;
import csx55.threads.core.Stats;
import csx55.threads.core.Task;
import csx55.threads.core.TaskQueue;
import csx55.threads.core.ThreadPool;
import csx55.threads.util.Config;
import csx55.threads.util.Log;
import csx55.threads.util.NetworkUtil;
import csx55.threads.util.NodeId;
import csx55.threads.util.Protocol;
import csx55.threads.util.WaitUtil;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PushbackInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class ComputeNode {

  private static final int MAX_TASKS_PER_ROUND = Config.getInt("cs555.maxTasksPerRound", 1000);

  private static final int PUSH_THRESHOLD = Config.getInt("cs555.pushThreshold", 100);
  private static final int PULL_THRESHOLD = Config.getInt("cs555.pullThreshold", 30);
  private static final int MIN_BATCH_SIZE = Config.getInt("cs555.minBatchSize", 50);
  private static final long BALANCE_CHECK_INTERVAL =
      Config.getLong("cs555.balanceCheckInterval", 100);

  private final String registryHost;
  private final int registryPort;
  private ServerSocket serverSocket;
  private volatile boolean running = true;

  private int poolSize;
  private ThreadPool pool;
  private final TaskQueue taskQueue = new TaskQueue();

  private final Stats stats = new Stats();
  private String myId;
  private final OverlayState state = new OverlayState();
  private volatile RoundAggregator roundAggregator;
  private volatile LoadBalancer loadBalancer;

  public ComputeNode(String registryHost, int registryPort) {
    this.registryHost = registryHost;
    this.registryPort = registryPort;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(0);
    String ip = InetAddress.getLocalHost().getHostAddress();
    int port = serverSocket.getLocalPort();
    myId = ip + ":" + port;
    System.out.println("[NODE-START] ComputeNode started at " + myId);

    System.out.println(
        "[NODE-START] Registering with Registry at " + registryHost + ":" + registryPort);
    try (Socket sock = new Socket(registryHost, registryPort);
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject(Protocol.REGISTER + " " + ip + " " + port);
      out.flush();
      System.out.println("[NODE-START] Registration sent successfully");
    } catch (IOException e) {
      System.out.println("[NODE-ERROR] Failed to register with Registry: " + e.getMessage());
      throw e;
    }

    new Thread(
            () -> {
              System.out.println("[NODE-START] Accept thread started for " + myId);
              try {
                while (running) {
                  Socket sock = serverSocket.accept();
                  new Thread(() -> handleMessage(sock)).start();
                }
              } catch (IOException e) {
                if (running) {
                  System.out.println("[NODE-ERROR] Accept thread died: " + e.getMessage());
                  e.printStackTrace(System.out);
                }
              }
              System.out.println("[NODE-START] Accept thread exiting for " + myId);
            },
            "ComputeNode-AcceptLoop")
        .start();
  }

  private void handleMessage(Socket sock) {
    try {
      PushbackInputStream pin = new PushbackInputStream(sock.getInputStream());
      int first = pin.read();
      if (first == -1) return;
      pin.unread(first);

      try (ObjectInputStream in = new ObjectInputStream(pin)) {
        Object obj = in.readObject();
        if (!(obj instanceof String)) return;

        String msg = (String) obj;

        if (msg.startsWith(Protocol.OVERLAY)) {
          String[] parts = msg.split(" ");
          state.setSuccessor(parts[1]);
          // Expected: OVERLAY <successor> <predecessor> <poolSize> <ringSize>
          if (parts.length >= 5) {
            state.setPredecessor(parts[2]);
            int newPoolSize = Integer.parseInt(parts[3]);
            state.setRingSize(Integer.parseInt(parts[4]));

            if (pool == null) {
              poolSize = newPoolSize;
              System.out.println(
                  "[NODE-DEBUG] Creating ThreadPool with size " + poolSize + " for node " + myId);
              try {
                pool = new ThreadPool(poolSize, taskQueue, stats, myId);
                System.out.println("[NODE-DEBUG] ThreadPool created successfully");
                Log.info(
                    "Overlay set. Successor="
                        + state.getSuccessor()
                        + " Predecessor="
                        + state.getPredecessor()
                        + " poolSize="
                        + poolSize
                        + " ringSize="
                        + state.getRingSize());
              } catch (IllegalArgumentException e) {
                System.out.println("[NODE-DEBUG] ThreadPool creation failed: " + e.getMessage());
                Log.error("Invalid pool size: " + e.getMessage());
                pool = null; // Ensure pool is null if creation failed
              } catch (Exception e) {
                System.out.println(
                    "[NODE-DEBUG] Unexpected error creating ThreadPool: "
                        + e.getClass().getName()
                        + " - "
                        + e.getMessage());
                e.printStackTrace(System.out);
                pool = null;
              }
            } else {
              if (poolSize != newPoolSize) {
                Log.warn(
                    "Overlay requested pool size change from "
                        + poolSize
                        + " to "
                        + newPoolSize
                        + "; ignoring to preserve single thread-pool lifecycle.");
              }
              Log.info(
                  "Overlay updated. Successor="
                      + state.getSuccessor()
                      + " Predecessor="
                      + state.getPredecessor()
                      + " (pool size locked: "
                      + poolSize
                      + ") ringSize="
                      + state.getRingSize());
            }

            // Initialize load balancer and round aggregator after pool is set up
            if (roundAggregator == null) {
              roundAggregator = new RoundAggregator(myId, state);
            }
            if (loadBalancer == null) {
              loadBalancer =
                  new LoadBalancer(
                      myId,
                      taskQueue,
                      stats,
                      state,
                      PUSH_THRESHOLD,
                      PULL_THRESHOLD,
                      MIN_BATCH_SIZE);
              Log.info(
                  "[INIT] LoadBalancer initialized with pushThreshold="
                      + PUSH_THRESHOLD
                      + ", pullThreshold="
                      + PULL_THRESHOLD);
            }
            System.out.println(
                "[NODE-OVERLAY] OVERLAY processing complete for "
                    + myId
                    + ", pool="
                    + (pool != null ? "initialized" : "null"));
          } else {
            // Backward compatibility: OVERLAY <successor> <maybe-predecessor-or-pool> <maybe-pool>
            state.setPredecessor(parts.length > 3 ? parts[2] : null);
            int newPoolSize = Integer.parseInt(parts.length > 3 ? parts[3] : parts[2]);

            if (pool == null) {
              poolSize = newPoolSize;
              try {
                pool = new ThreadPool(poolSize, taskQueue, stats, myId);
                Log.info(
                    "Overlay set. Successor="
                        + state.getSuccessor()
                        + " Predecessor="
                        + state.getPredecessor()
                        + " poolSize="
                        + poolSize);
              } catch (IllegalArgumentException e) {
                Log.error("Invalid pool size: " + e.getMessage());
                pool = null; // Ensure pool is null if creation failed
              }
            } else {
              if (poolSize != newPoolSize) {
                Log.warn(
                    "Overlay requested pool size change from "
                        + poolSize
                        + " to "
                        + newPoolSize
                        + "; ignoring to preserve single thread-pool lifecycle.");
              }
              Log.info(
                  "Overlay updated. Successor="
                      + state.getSuccessor()
                      + " Predecessor="
                      + state.getPredecessor()
                      + " (pool size locked: "
                      + poolSize
                      + ")");
            }
          }

        } else if (msg.startsWith(Protocol.START)) {
          int rounds = Integer.parseInt(msg.split(" ")[1]);
          if (pool == null) {
            Log.error("Cannot start rounds: thread pool not initialized (invalid pool size?)");
            return;
          }
          runRounds(rounds);

          WaitUtil.waitUntilDrained(taskQueue, stats);
          WaitUtil.waitForQuiescence(
              () -> taskQueue.size(), () -> stats.getInFlight(), 5000, 60000);

          sendStatsToRegistry();

        } else if (msg.startsWith(Protocol.TASKS)) {
          @SuppressWarnings("unchecked")
          java.util.List<Task> batch = (java.util.List<Task>) in.readObject();
          for (Task t : batch) t.markMigrated();
          taskQueue.addBatch(batch);
          stats.incrementPulled(batch.size());

        } else if (msg.startsWith(Protocol.PULL_REQUEST)) {
          String[] parts = msg.split(" ");
          String requestingNode = parts[1];
          int capacity = Integer.parseInt(parts[2]);
          if (loadBalancer != null) loadBalancer.handlePullRequest(requestingNode, capacity);
        } else if (msg.startsWith(Protocol.GEN)) {
          if (roundAggregator != null) roundAggregator.handleGenMessage(msg);
        }
      }
    } catch (EOFException e) {
      System.out.println("[NODE-ERROR] EOF exception in handleMessage: " + e.getMessage());
    } catch (Exception e) {
      System.out.println(
          "[NODE-ERROR] Exception in handleMessage: "
              + e.getClass().getName()
              + " - "
              + e.getMessage());
      e.printStackTrace(System.out);
    } finally {
      try {
        sock.close();
      } catch (IOException ignore) {
      }
    }
  }

  private void runRounds(int rounds) {
    Thread balancer = new Thread(() -> continuousLoadBalance(), "LoadBalancer");
    balancer.setDaemon(true);
    balancer.start();

    Random rand = new Random();
    // Parse IP and port from myId (format: "ip:port")
    String[] idParts = myId.split(":");
    String ip = idParts[0];
    int port = Integer.parseInt(idParts[1]);

    for (int r = 0; r < rounds; r++) {
      int toGen = 1 + rand.nextInt(Math.max(1, MAX_TASKS_PER_ROUND));
      for (int i = 0; i < toGen; i++) {
        int payload = rand.nextInt(); // Random payload for each task
        taskQueue.add(new Task(ip, port, r, payload));
      }
      stats.incrementGenerated(toGen);

      if (roundAggregator != null) {
        roundAggregator.announceRoundGeneration(r, toGen);
        int totalForRound = roundAggregator.awaitRoundTotal(r, 5000);
        if (totalForRound > 0 && state.getRingSize() > 0 && loadBalancer != null) {
          int target = (int) Math.round((double) totalForRound / (double) state.getRingSize());
          loadBalancer.rebalanceTowardsTarget(target);
        } else if (loadBalancer != null) {
          loadBalancer.balanceThresholdBased();
        }
      }

      // Removed waitUntilDrained to allow tasks to migrate between rounds
      // WaitUtil.waitUntilDrained(taskQueue, stats);
    }

    // Wait for all tasks to complete at the end of all rounds
    WaitUtil.waitUntilDrained(taskQueue, stats);
    balancer.interrupt();
  }

  private void continuousLoadBalance() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(BALANCE_CHECK_INTERVAL);

        int queueSize = taskQueue.size();
        int inFlight = stats.getInFlight();

        Log.info(
            "[BALANCE_CHECK] Queue: "
                + queueSize
                + ", InFlight: "
                + inFlight
                + ", PushThreshold: "
                + PUSH_THRESHOLD
                + ", PullThreshold: "
                + PULL_THRESHOLD
                + ", LoadBalancer: "
                + (loadBalancer != null ? "initialized" : "null"));

        if (queueSize > PUSH_THRESHOLD && loadBalancer != null) {
          Log.info(
              "[BALANCE_CHECK] Triggering push - queue "
                  + queueSize
                  + " > threshold "
                  + PUSH_THRESHOLD);
          loadBalancer.balanceThresholdBased();
        }

        if (queueSize < PULL_THRESHOLD && inFlight < poolSize / 2 && loadBalancer != null) {
          Log.info(
              "[BALANCE_CHECK] Triggering pull - queue "
                  + queueSize
                  + " < threshold "
                  + PULL_THRESHOLD);
          loadBalancer.sendPullRequestIfIdle();
        }
      } catch (InterruptedException e) {
        break;
      }
    }
  }

  private void sendStatsToRegistry() {
    String regId = NodeId.toId(registryHost, registryPort);
    try {
      NetworkUtil.sendWithObject(regId, Protocol.STATS + " " + myId, stats);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void shutdown() {
    Log.info("Shutting down ComputeNode...");
    running = false;

    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    } catch (IOException e) {
    }

    if (pool != null) {
      WaitUtil.waitUntilDrained(taskQueue, stats);
      pool.shutdown();
    }

    Log.info("ComputeNode shutdown complete.");
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: java csx55.threads.ComputeNode <registry-host> <registry-port>");
      System.exit(1);
    }
    ComputeNode node = new ComputeNode(args[0], Integer.parseInt(args[1]));
    node.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("[NODE-DEBUG] ComputeNode shutdown hook triggered!");
                  Thread.dumpStack();
                  Log.info("Shutdown signal received.");
                  node.shutdown();
                }));

    Log.info("ComputeNode started. Type 'quit' to shutdown gracefully.");
    try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = console.readLine()) != null) {
        if (line.trim().equalsIgnoreCase("quit")) {
          node.shutdown();
          System.exit(0);
        } else if (!line.trim().isEmpty()) {
          Log.warn("Unknown command: " + line + " | Available: quit");
        }
      }
    }
  }
}
