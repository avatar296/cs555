package csx55.threads;

import java.io.*;
import java.net.*;

public class ComputeNode {
  private String registryHost;
  private int registryPort;
  private ThreadPool threadPool;
  private TaskQueue taskQueue;
  private int numberOfRounds;

  // Stats
  private int generatedTasks = 0;
  private int pulledTasks = 0;
  private int pushedTasks = 0;
  private int completedTasks = 0;

  public ComputeNode(String host, int port) {
    this.registryHost = host;
    this.registryPort = port;
  }

  public void registerWithRegistry() {
    // Connect and send registration info
  }

  public void receiveOverlayInfo() {
    // Receive neighbor IP/Port
  }

  public void runRounds(int rounds) {
    // Generate tasks, enqueue, execute
  }

  public void balanceLoad() {
    // Implement push/pull migration
  }

  public void sendStatsToRegistry() {
    // Report statistics
  }

  public static void main(String[] args) throws Exception {
    String host = args[0];
    int port = Integer.parseInt(args[1]);
    ComputeNode node = new ComputeNode(host, port);
    node.registerWithRegistry();
  }
}
