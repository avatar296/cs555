package csx55.threads;

import java.io.*;
import java.net.*;
import java.util.*;

public class Registry {
  private ServerSocket serverSocket;
  private Map<String, NodeInfo> registeredNodes = new HashMap<>();

  public Registry(int port) throws IOException {
    // Initialize server socket
  }

  public void start() {
    // Accept node registrations
  }

  private void registerNode(Socket socket) {
    // Handle registration from a compute node
  }

  public void setupOverlay(int threadPoolSize) {
    // Send neighbor info to each node
  }

  public void startComputation(int numberOfRounds) {
    // Notify all nodes to begin rounds
  }

  public void collectResults() {
    // Gather stats from nodes and print final table
  }

  public static void main(String[] args) throws Exception {
    int portNum = Integer.parseInt(args[0]);
    Registry registry = new Registry(portNum);
    registry.start();
  }
}
