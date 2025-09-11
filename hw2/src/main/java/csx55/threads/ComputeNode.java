package csx55.threads;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ComputeNode {

  private static final int MAX_TASKS_PER_ROUND = Integer.getInteger("cs555.maxTasksPerRound", 1000);

  private static final int PUSH_THRESHOLD = Integer.getInteger("cs555.pushThreshold", 20);
  private static final int PULL_THRESHOLD = Integer.getInteger("cs555.pullThreshold", 5);
  private static final int MIN_BATCH_SIZE = Integer.getInteger("cs555.minBatchSize", 10);
  private static final long BALANCE_CHECK_INTERVAL =
      Long.getLong("cs555.balanceCheckInterval", 100);

  private final String registryHost;
  private final int registryPort;
  private ServerSocket serverSocket;
  private volatile boolean running = true;

  private String successor; // "<ip>:<port>"
  private String predecessor; // "<ip>:<port>" for pull requests
  private int poolSize;
  private ThreadPool pool;
  private final TaskQueue taskQueue = new TaskQueue();

  private final Stats stats = new Stats();
  private String myId;

  public ComputeNode(String registryHost, int registryPort) {
    this.registryHost = registryHost;
    this.registryPort = registryPort;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(0);
    String ip = InetAddress.getLocalHost().getHostAddress();
    int port = serverSocket.getLocalPort();
    myId = ip + ":" + port;

    try (Socket sock = new Socket(registryHost, registryPort);
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject("REGISTER " + ip + " " + port);
      out.flush();
    }

    new Thread(
            () -> {
              try {
                while (running) {
                  Socket sock = serverSocket.accept();
                  new Thread(() -> handleMessage(sock)).start();
                }
              } catch (IOException e) {
                if (running) e.printStackTrace();
              }
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

        if (msg.startsWith("OVERLAY")) {
          String[] parts = msg.split(" ");
          successor = parts[1];
          predecessor = parts.length > 3 ? parts[2] : null;
          int newPoolSize = Integer.parseInt(parts.length > 3 ? parts[3] : parts[2]);

          if (pool == null || poolSize != newPoolSize) {
            if (pool != null) {
              pool.shutdown();
            }
            poolSize = newPoolSize;
            try {
              pool = new ThreadPool(poolSize, taskQueue, stats);
              System.out.println(
                  "Overlay set. Successor="
                      + successor
                      + " Predecessor="
                      + predecessor
                      + " poolSize="
                      + poolSize);
            } catch (IllegalArgumentException e) {
              System.err.println("Invalid pool size: " + e.getMessage());
              pool = null; // Ensure pool is null if creation failed
            }
          } else {
            System.out.println(
                "Overlay updated. Successor="
                    + successor
                    + " Predecessor="
                    + predecessor
                    + " (pool size unchanged: "
                    + poolSize
                    + ")");
          }

        } else if (msg.startsWith("START")) {
          int rounds = Integer.parseInt(msg.split(" ")[1]);
          if (pool == null) {
            System.err.println(
                "Cannot start rounds: thread pool not initialized (invalid pool size?)");
            return;
          }
          runRounds(rounds);

          waitUntilDrained();
          waitForQuiescence(5000, 60000);

          sendStatsToRegistry();

        } else if (msg.startsWith("TASKS")) {
          @SuppressWarnings("unchecked")
          List<Task> batch = (List<Task>) in.readObject();
          for (Task t : batch) t.markMigrated();
          taskQueue.addBatch(batch);
          stats.incrementPulled(batch.size());

        } else if (msg.startsWith("PULL_REQUEST")) {
          String[] parts = msg.split(" ");
          String requestingNode = parts[1];
          int capacity = Integer.parseInt(parts[2]);
          handlePullRequest(requestingNode, capacity);
        }
      }
    } catch (EOFException ignored) {
    } catch (Exception e) {
      e.printStackTrace();
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
    for (int r = 0; r < rounds; r++) {
      int toGen = 1 + rand.nextInt(Math.max(1, MAX_TASKS_PER_ROUND));
      for (int i = 0; i < toGen; i++) {
        taskQueue.add(new Task(myId));
      }
      stats.incrementGenerated(toGen);

      balanceLoad();

      waitUntilDrained();
    }

    balancer.interrupt();
  }

  private void continuousLoadBalance() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(BALANCE_CHECK_INTERVAL);

        int queueSize = taskQueue.size();
        int inFlight = stats.getInFlight();

        if (queueSize > PUSH_THRESHOLD) {
          balanceLoad();
        }

        if (queueSize < PULL_THRESHOLD && inFlight < poolSize / 2) {
          sendPullRequest();
        }
      } catch (InterruptedException e) {
        break;
      }
    }
  }

  private void waitUntilDrained() {
    while (taskQueue.size() > 0 || stats.getInFlight() > 0) {
      try {
        Thread.sleep(25);
      } catch (InterruptedException ignored) {
      }
    }
  }

  private void waitForQuiescence(long stableMillis, long maxWaitMillis) {
    long deadline = System.currentTimeMillis() + maxWaitMillis;
    long stableSince = -1;
    int lastQ = -1, lastF = -1;

    while (System.currentTimeMillis() < deadline) {
      int q = taskQueue.size();
      int f = stats.getInFlight();
      boolean idle = (q == 0 && f == 0);
      boolean unchanged = (q == lastQ && f == lastF);

      if (idle && unchanged) {
        if (stableSince < 0) stableSince = System.currentTimeMillis();
        if (System.currentTimeMillis() - stableSince >= stableMillis) return;
      } else {
        stableSince = -1;
      }

      lastQ = q;
      lastF = f;
      try {
        Thread.sleep(25);
      } catch (InterruptedException ignored) {
      }
    }
  }

  private void balanceLoad() {
    int myOutstanding = taskQueue.size();
    if (myOutstanding > PUSH_THRESHOLD && successor != null) {
      int migrateCount = Math.max(MIN_BATCH_SIZE, myOutstanding / 2);
      List<Task> raw = taskQueue.removeBatch(migrateCount);
      if (raw == null || raw.isEmpty()) return;

      List<Task> toSend = new ArrayList<>();
      List<Task> keep = new ArrayList<>();
      for (Task t : raw) {
        if (t.isMigrated()) keep.add(t); // don't re-migrate
        else {
          t.markMigrated();
          toSend.add(t);
        }
      }
      if (!keep.isEmpty()) taskQueue.addBatch(keep);
      if (toSend.isEmpty()) return;

      String[] parts = successor.split(":");
      try (Socket sock = new Socket(parts[0], Integer.parseInt(parts[1]));
          ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
        out.writeObject("TASKS");
        out.writeObject(toSend);
        out.flush();
        stats.incrementPushed(toSend.size());
      } catch (IOException e) {
        taskQueue.addBatch(toSend);
        e.printStackTrace();
      }
    }
  }

  private void handlePullRequest(String requestingNode, int capacity) {
    int myQueue = taskQueue.size();
    if (myQueue > PULL_THRESHOLD) {
      int shareCount = Math.min(capacity, Math.min(MIN_BATCH_SIZE, (myQueue - PULL_THRESHOLD) / 2));
      if (shareCount > 0) {
        List<Task> toShare = taskQueue.removeBatch(shareCount);
        if (toShare != null && !toShare.isEmpty()) {
          for (Task t : toShare) {
            if (!t.isMigrated()) t.markMigrated();
          }
          sendTasksToNode(requestingNode, toShare);
          stats.incrementPushed(toShare.size());
        }
      }
    }
  }

  private void sendPullRequest() {
    if (predecessor != null && taskQueue.size() < PULL_THRESHOLD) {
      String[] parts = predecessor.split(":");
      try (Socket sock = new Socket(parts[0], Integer.parseInt(parts[1]));
          ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
        int capacity = Math.max(MIN_BATCH_SIZE, PUSH_THRESHOLD - taskQueue.size());
        out.writeObject("PULL_REQUEST " + myId + " " + capacity);
        out.flush();
      } catch (IOException e) {
      }
    }
  }

  private void sendTasksToNode(String nodeId, List<Task> tasks) {
    String[] parts = nodeId.split(":");
    try (Socket sock = new Socket(parts[0], Integer.parseInt(parts[1]));
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject("TASKS");
      out.writeObject(tasks);
      out.flush();
    } catch (IOException e) {
      taskQueue.addBatch(tasks);
    }
  }

  private void sendStatsToRegistry() {
    try (Socket sock = new Socket(registryHost, registryPort);
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject("STATS " + myId);
      out.writeObject(stats);
      out.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void shutdown() {
    System.out.println("Shutting down ComputeNode...");
    running = false;

    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    } catch (IOException e) {
    }

    if (pool != null) {
      waitUntilDrained();
      pool.shutdown();
    }

    System.out.println("ComputeNode shutdown complete.");
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
                  System.out.println("\nShutdown signal received.");
                  node.shutdown();
                }));

    System.out.println("ComputeNode started. Type 'quit' to shutdown gracefully.");
    try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = console.readLine()) != null) {
        if (line.trim().equalsIgnoreCase("quit")) {
          node.shutdown();
          System.exit(0);
        } else if (!line.trim().isEmpty()) {
          System.out.println("Unknown command: " + line);
          System.out.println("Available commands: quit");
        }
      }
    }
  }
}
