package csx55.threads;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Registry {
  private final List<ComputeNodeInfo> nodes = new ArrayList<>();
  private final int port;

  public Registry(int port) {
    this.port = port;
  }

  public void start() {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      System.out.println("Registry listening on port " + port);

      while (nodes.size() < 3) { // assume 3 nodes
        Socket socket = serverSocket.accept();
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
          Object obj = in.readObject();
          if (obj instanceof ComputeNodeInfo) {
            ComputeNodeInfo info = (ComputeNodeInfo) obj;
            nodes.add(info);
            System.out.println("Registered " + info);
          } else if (obj instanceof Stats) {
            Stats s = (Stats) obj;
            System.out.println("Got stats: " + s);
          }
        }
      }

      setupOverlay();
      broadcast("START 3");

      // later, gather stats again and then shutdown
      Thread.sleep(5000);
      broadcast("SHUTDOWN");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void setupOverlay() {
    int n = nodes.size();
    for (int i = 0; i < n; i++) {
      ComputeNodeInfo current = nodes.get(i);
      ComputeNodeInfo succ = nodes.get((i + 1) % n);
      current.setSuccessor(succ);
      System.out.println(current + " successor -> " + succ);
    }
  }

  private void broadcast(String msg) {
    for (ComputeNodeInfo node : nodes) {
      try (Socket socket = new Socket(node.getIp(), node.getPort());
          ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
        out.writeObject(msg);
        out.flush();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    System.out.println("Broadcast: " + msg);
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Usage: java csx55.threads.Registry <port>");
      return;
    }
    new Registry(Integer.parseInt(args[0])).start();
  }
}
