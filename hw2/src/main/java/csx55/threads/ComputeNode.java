package csx55.threads;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * ComputeNode: registers with registry, runs tasks in rounds, participates in ring load balancing,
 * and reports stats.
 */
public class ComputeNode {

  private final String registryHost;
  private final int registryPort;
  private ServerSocket serverSocket;
  private volatile boolean running = true;

  private String successor;
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
    serverSocket = new ServerSocket(0); // pick ephemeral port
    String ip = InetAddress.getLocalHost().getHostAddress();
    int port = serverSocket.getLocalPort();
    myId = ip + ":" + port;

    // register with registry
    try (Socket sock = new Socket(registryHost, registryPort);
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject("REGISTER " + ip + " " + port);
      out.flush();
    }

    // accept loop
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
            })
        .start();
  }

  private void handleMessage(Socket sock) {
    try (ObjectInputStream in = new ObjectInputStream(sock.getInputStream())) {
      Object obj = in.readObject();
      if (obj instanceof String) {
        String msg = (String) obj;
        if (msg.startsWith("OVERLAY")) {
          String[] parts = msg.split(" ");
          successor = parts[1];
          poolSize = Integer.parseInt(parts[2]);
          pool = new ThreadPool(poolSize, taskQueue, stats);
          System.out.println("Overlay set. Successor=" + successor + " poolSize=" + poolSize);
        } else if (msg.startsWith("START")) {
          int rounds = Integer.parseInt(msg.split(" ")[1]);
          runRounds(rounds);
          sendStatsToRegistry();
        } else if (msg.startsWith("TASKS")) {
          // received migrated tasks
          @SuppressWarnings("unchecked")
          List<Task> batch = (List<Task>) in.readObject();
          for (Task t : batch) {
            t.markMigrated();
          }
          taskQueue.addBatch(batch);
          stats.incrementPulled(batch.size());
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void runRounds(int rounds) {
    Random rand = new Random();
    for (int r = 0; r < rounds; r++) {
      int numTasks = 1 + rand.nextInt(1000);
      for (int i = 0; i < numTasks; i++) {
        taskQueue.add(new Task(myId, stats));
      }
      stats.incrementGenerated(numTasks);

      // wait for local tasks to drain
      while (taskQueue.size() > 0) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }
      }

      // do simple balancing with successor
      balanceLoad();
    }
  }

  private void balanceLoad() {
    int myOutstanding = taskQueue.size();
    if (myOutstanding > 20) {
      int migrateCount = Math.min(10, myOutstanding / 2);
      List<Task> batch = taskQueue.removeBatch(migrateCount);
      if (!batch.isEmpty()) {
        String[] parts = successor.split(":");
        try (Socket sock = new Socket(parts[0], Integer.parseInt(parts[1]));
            ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
          out.writeObject("TASKS");
          out.writeObject(batch);
          out.flush();
          stats.incrementPushed(batch.size());
        } catch (IOException e) {
          e.printStackTrace();
        }
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
