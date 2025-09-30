package csx55.threads;

import csx55.threads.balance.LoadBalancer;
import csx55.threads.balance.RoundAggregator;
import csx55.threads.core.OverlayState;
import csx55.threads.core.Stats;
import csx55.threads.core.Task;
import csx55.threads.core.TaskQueue;
import csx55.threads.core.ThreadPool;
import csx55.threads.util.Config;
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

  private static final int PUSH_THRESHOLD = Config.getInt("cs555.pushThreshold", 2000);
  private static final int PULL_THRESHOLD = Config.getInt("cs555.pullThreshold", 100);
  private static final int MIN_BATCH_SIZE = Config.getInt("cs555.minBatchSize", 20);
  private static final long BALANCE_CHECK_INTERVAL =
      Config.getLong("cs555.balanceCheckInterval", 3000);

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
  private volatile boolean acceptingTasks = true;

  public ComputeNode(String registryHost, int registryPort) {
    this.registryHost = registryHost;
    this.registryPort = registryPort;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(0);
    String ip = InetAddress.getLocalHost().getHostAddress();
    int port = serverSocket.getLocalPort();
    myId = ip + ":" + port;

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    System.out.flush();
                  } catch (Throwable ignore) {
                  }
                },
                "StdoutFlusher"));

    try (Socket sock = new Socket(registryHost, registryPort);
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject(Protocol.REGISTER + " " + ip + " " + port);
      out.flush();
    } catch (IOException e) {
      throw e;
    }

    new Thread(
            () -> {
              try {
                while (running) {
                  Socket sock = serverSocket.accept();
                  new Thread(() -> handleMessage(sock)).start();
                }
              } catch (IOException e) {
                if (running) {
                  e.printStackTrace();
                }
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

        if (msg.startsWith(Protocol.OVERLAY)) {
          String[] parts = msg.split(" ");

          state.setSuccessor(parts[1]);
          state.setPredecessor(parts[2]);
          int newPoolSize = Integer.parseInt(parts[3]);
          state.setRingSize(Integer.parseInt(parts[4]));

          if (pool == null) {
            poolSize = newPoolSize;
            try {
              pool = new ThreadPool(poolSize, taskQueue, stats, myId);
            } catch (IllegalArgumentException e) {
              pool = null;
            } catch (Exception e) {
              e.printStackTrace();
              pool = null;
            }
          } else {

            if (poolSize != newPoolSize) {}
          }

          if (roundAggregator == null) {
            roundAggregator = new RoundAggregator(myId, state);
          }
          if (loadBalancer == null) {
            loadBalancer =
                new LoadBalancer(
                    myId, taskQueue, stats, state, PUSH_THRESHOLD, PULL_THRESHOLD, MIN_BATCH_SIZE);
          }

        } else if (msg.startsWith(Protocol.START)) {
          int rounds = Integer.parseInt(msg.split(" ")[1]);

          if (pool == null && poolSize > 0) {
            try {
              pool = new ThreadPool(poolSize, taskQueue, stats, myId);
            } catch (IllegalArgumentException e) {
              return;
            }
          }

          if (pool == null) {
            return;
          }
          runRounds(rounds);

          acceptingTasks = false;

          WaitUtil.waitUntilDrained(taskQueue, stats);
          WaitUtil.waitForQuiescence(
              () -> taskQueue.size(), () -> stats.getInFlight(), 5000, 60000);

          if (pool != null) {
            pool.shutdown();
            pool = null;
          }

          System.out.flush();

          sendStatsToRegistry();

          acceptingTasks = true;

        } else if (msg.startsWith(Protocol.TASKS)) {
          if (!acceptingTasks) {
            return;
          }
          @SuppressWarnings("unchecked")
          java.util.List<Task> batch = (java.util.List<Task>) in.readObject();
          for (Task t : batch) t.markMigrated();
          taskQueue.addBatch(batch);
          stats.incrementPulled(batch.size());

        } else if (msg.startsWith(Protocol.PULL_REQUEST)) {
          if (!acceptingTasks) {
            return;
          }
          String[] parts = msg.split(" ");
          String requestingNode = parts[1];
          int capacity = Integer.parseInt(parts[2]);
          if (loadBalancer != null) loadBalancer.handlePullRequest(requestingNode, capacity);
        } else if (msg.startsWith(Protocol.GEN)) {
          if (roundAggregator != null) roundAggregator.handleGenMessage(msg);
        }
      }
    } catch (EOFException e) {
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
    String[] idParts = myId.split(":");
    String ip = idParts[0];
    int port = Integer.parseInt(idParts[1]);

    for (int r = 0; r < rounds; r++) {
      int toGen = 1 + rand.nextInt(Math.max(1, MAX_TASKS_PER_ROUND));
      for (int i = 0; i < toGen; i++) {
        int payload = rand.nextInt();
        taskQueue.add(new Task(ip, port, r, payload));
      }
      stats.incrementGenerated(toGen);

      if (roundAggregator != null) {
        roundAggregator.announceRoundGeneration(r, toGen);
      }
    }

    WaitUtil.waitUntilDrained(taskQueue, stats);
    balancer.interrupt();
  }

  private void continuousLoadBalance() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(BALANCE_CHECK_INTERVAL);

        if (!acceptingTasks) {
          continue;
        }

        int queueSize = taskQueue.size();

        if (queueSize > PUSH_THRESHOLD && loadBalancer != null) {
          loadBalancer.balanceThresholdBased();
        }

        if (queueSize < PULL_THRESHOLD && loadBalancer != null) {
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
                  node.shutdown();
                }));

    try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = console.readLine()) != null) {
        if (line.trim().equalsIgnoreCase("quit")) {
          node.shutdown();
          System.exit(0);
        } else if (!line.trim().isEmpty()) {
        }
      }
    }
  }
}
