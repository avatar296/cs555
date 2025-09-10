package csx55.threads;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Random;

public class ComputeNode {
  private final String ip;
  private final int port;
  private ComputeNodeInfo successor;
  private final TaskQueue taskQueue = new TaskQueue();
  private final Stats stats = new Stats();
  private ThreadPool pool;
  private final Random rand = new Random();

  public ComputeNode(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  public void connectToRegistry(String registryIp, int registryPort) {
    try (Socket socket = new Socket(registryIp, registryPort);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
      out.writeObject(new ComputeNodeInfo(ip, port));
      out.flush();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void startServer() {
    Thread t =
        new Thread(
            () -> {
              try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Node server on " + ip + ":" + port);
                while (true) {
                  Socket socket = serverSocket.accept();
                  try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                    Object msg = in.readObject();
                    if (msg instanceof String) {
                      handleControlMessage((String) msg);
                    } else if (msg instanceof List) {
                      @SuppressWarnings("unchecked")
                      List<Task> batch = (List<Task>) msg;
                      taskQueue.addBatch(batch);
                      stats.incrementPulled(batch.size());
                    }
                  }
                }
              } catch (Exception e) {
                e.printStackTrace();
              }
            },
            "Server-" + ip + ":" + port);
    t.setDaemon(false);
    t.start();
  }

  private void handleControlMessage(String msg) {
    if (msg.startsWith("START")) {
      int rounds = Integer.parseInt(msg.split(" ")[1]);
      startThreadPool(3);
      runRounds(rounds);
      sendStats();
    } else if (msg.equals("SHUTDOWN")) {
      shutdown();
    }
  }

  private void startThreadPool(int size) {
    pool = new ThreadPool(size, taskQueue, stats, getId());
  }

  private void runRounds(int rounds) {
    for (int r = 1; r <= rounds; r++) {
      int num = rand.nextInt(6) + 5;
      for (int i = 0; i < num; i++) {
        Task t = new Task(getId() + "-T" + r + "-" + i);
        stats.incrementGenerated();
        taskQueue.add(t);
      }
      System.out.println(getId() + " generated " + num + " tasks in round " + r);
    }
  }

  private void sendStats() {
    try (Socket socket = new Socket("127.0.0.1", 6000); // Registry assumed local
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
      out.writeObject(stats);
      out.flush();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void shutdown() {
    for (int i = 0; i < pool.getSize(); i++) {
      taskQueue.add(Task.poisonPill());
    }
    pool.joinAll();
    System.out.println(getId() + " shut down cleanly.");
    System.exit(0);
  }

  private String getId() {
    return ip + ":" + port;
  }

  public static void main(String[] args) {
    if (args.length != 4) {
      System.err.println(
          "Usage: java csx55.threads.ComputeNode <ip> <port> <registry-ip> <registry-port>");
      return;
    }
    ComputeNode node = new ComputeNode(args[0], Integer.parseInt(args[1]));
    node.connectToRegistry(args[2], Integer.parseInt(args[3]));
    node.startServer();
  }
}
