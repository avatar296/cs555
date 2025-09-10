package csx55.threads;

import java.io.EOFException;
import java.io.IOException;
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

  // Optional tunable via -D: how many tasks a node generates per round
  private static final int MAX_TASKS_PER_ROUND = Integer.getInteger("cs555.maxTasksPerRound", 1000);

  private final String registryHost;
  private final int registryPort;
  private ServerSocket serverSocket;
  private volatile boolean running = true;

  private String successor; // "<ip>:<port>"
  private int poolSize;
  private ThreadPool pool;
  private final TaskQueue taskQueue = new TaskQueue();

  private final Stats stats = new Stats();
  private String myId; // "<ip>:<port>"

  public ComputeNode(String registryHost, int registryPort) {
    this.registryHost = registryHost;
    this.registryPort = registryPort;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(0); // ephemeral
    String ip = InetAddress.getLocalHost().getHostAddress();
    int port = serverSocket.getLocalPort();
    myId = ip + ":" + port;

    // Register with registry
    try (Socket sock = new Socket(registryHost, registryPort);
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject("REGISTER " + ip + " " + port);
      out.flush();
    }

    // Accept loop
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
      // Guard against empty connects to avoid noisy EOF
      PushbackInputStream pin = new PushbackInputStream(sock.getInputStream());
      int first = pin.read();
      if (first == -1) return;
      pin.unread(first);

      try (ObjectInputStream in = new ObjectInputStream(pin)) {
        Object obj = in.readObject();
        if (!(obj instanceof String)) return;

        String msg = (String) obj;

        if (msg.startsWith("OVERLAY")) {
          // OVERLAY <succIp:succPort> <poolSize>
          String[] parts = msg.split(" ");
          successor = parts[1];
          poolSize = Integer.parseInt(parts[2]);
          if (pool == null) {
            pool = new ThreadPool(poolSize, taskQueue, stats);
          }
          System.out.println("Overlay set. Successor=" + successor + " poolSize=" + poolSize);

        } else if (msg.startsWith("START")) {
          // START <rounds>
          int rounds = Integer.parseInt(msg.split(" ")[1]);
          runRounds(rounds);

          // Final settle: drain + strong quiescence so late migrated tasks finish before
          // reporting
          waitUntilDrained();
          waitForQuiescence(5000, 60000); // idle for 5s, up to 60s max

          sendStatsToRegistry();

        } else if (msg.startsWith("TASKS")) {
          // Received migrated tasks (batch)
          @SuppressWarnings("unchecked")
          List<Task> batch = (List<Task>) in.readObject();
          for (Task t : batch) t.markMigrated(); // prevent re-migration downstream
          taskQueue.addBatch(batch);
          stats.incrementPulled(batch.size());
        }
      }
    } catch (EOFException ignored) {
      // peer closed early; ignore
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
    Random rand = new Random();
    for (int r = 0; r < rounds; r++) {
      // Generate 1..MAX_TASKS_PER_ROUND tasks
      int toGen = 1 + rand.nextInt(Math.max(1, MAX_TASKS_PER_ROUND));
      for (int i = 0; i < toGen; i++) {
        taskQueue.add(new Task(myId));
      }
      stats.incrementGenerated(toGen);

      // Migrate EARLY while backlog exists (realistic pair-wise relief)
      balanceLoad();

      // Then let workers chew through whatever remains
      waitUntilDrained();
    }
  }

  /** Wait until queue is empty AND no tasks are in-flight (workers idle). */
  private void waitUntilDrained() {
    while (taskQueue.size() > 0 || stats.getInFlight() > 0) {
      try {
        Thread.sleep(25);
      } catch (InterruptedException ignored) {
      }
    }
  }

  /**
   * Wait until the node remains idle (queue==0 & inFlight==0) for a continuous stable window. Helps
   * ensure late-arriving migrated tasks are mined before reporting stats.
   */
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

  /**
   * Simple pair-wise push: if local backlog is above a threshold, push a batch to successor. -
   * Moves only tasks still in the queue (not in-flight). - Uses small batches (min 10) to damp
   * oscillations. - Avoids re-migrating tasks that were already migrated.
   */
  private void balanceLoad() {
    int myOutstanding = taskQueue.size();
    if (myOutstanding > 20 && successor != null) {
      int migrateCount = Math.max(10, myOutstanding / 2);
      List<Task> raw = taskQueue.removeBatch(migrateCount);
      if (raw == null || raw.isEmpty()) return;

      List<Task> toSend = new ArrayList<>();
      List<Task> keep = new ArrayList<>();
      for (Task t : raw) {
        if (t.isMigrated()) keep.add(t); // don't re-migrate
        else {
          t.markMigrated();
          toSend.add(t);
        } // mark at send-time to damp oscillation
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
        // If successor unreachable, put tasks back so they still get mined here.
        taskQueue.addBatch(toSend);
        e.printStackTrace();
      }
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

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: java csx55.threads.ComputeNode <registry-host> <registry-port>");
      System.exit(1);
    }
    ComputeNode node = new ComputeNode(args[0], Integer.parseInt(args[1]));
    node.start();
  }
}
