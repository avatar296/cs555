package csx55.overlay.node;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class Registry {
    private final Map<String, ServerThread> registeredNodes = new HashMap<>();
    private final List<String[]> overlayLinks = new ArrayList<>();

    private void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Registry listening on port: " + port);
            new Thread(this::handleUserCommands).start();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ServerThread serverThread = new ServerThread(clientSocket, this);
                new Thread(serverThread).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("resource")
    private void handleUserCommands() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Registry> ");
            String[] commandParts = scanner.nextLine().split("\\s+");
            String command = commandParts[0];

            switch (command.toLowerCase()) {
                case "list-messaging-nodes":
                    listMessagingNodes();
                    break;
                case "setup-overlay":
                    if (commandParts.length == 2) {
                        setupOverlay(Integer.parseInt(commandParts[1]));
                    }
                    break;
                case "send-overlay-link-weights":
                    sendOverlayLinkWeights();
                    break;
                default:
                    System.out.println("Unknown command.");
            }
        }
    }

    public synchronized void registerNode(String nodeId, ServerThread serverThread) {
        registeredNodes.put(nodeId, serverThread);
        System.out.println("Registered node: " + nodeId);
    }

    public synchronized void deregisterNode(String nodeId) {
        registeredNodes.remove(nodeId);
        System.out.println("Deregistered node: " + nodeId);
    }

    private void listMessagingNodes() {
        if (registeredNodes.isEmpty()) {
            System.out.println("No messaging nodes registered.");
        } else {
            System.out.println("Registered Nodes (" + registeredNodes.size() + "):");
            registeredNodes.keySet().forEach(System.out::println);
        }
    }

    private void setupOverlay(int cr) {
        synchronized (registeredNodes) {
            if (registeredNodes.size() < cr) {
                System.out.println("Not enough nodes for the specified connection limit.");
                return;
            }
            overlayLinks.clear();
            List<String> nodeIds = new ArrayList<>(registeredNodes.keySet());
            Map<String, List<String>> connectionPlan = new HashMap<>();

            for (int i = 0; i < nodeIds.size(); i++) {
                for (int j = 1; j <= cr / 2; j++) {
                    String node1 = nodeIds.get(i);
                    String node2 = nodeIds.get((i + j) % nodeIds.size());

                    connectionPlan.computeIfAbsent(node1, k -> new ArrayList<>()).add(node2);
                    connectionPlan.computeIfAbsent(node2, k -> new ArrayList<>()).add(node1);
                    overlayLinks.add(new String[] { node1, node2 });
                }
            }

            for (Map.Entry<String, List<String>> entry : connectionPlan.entrySet()) {
                try {
                    registeredNodes.get(entry.getKey()).sendPeerList(entry.getValue());
                } catch (IOException e) {
                    System.err.println("Failed to send peer list to " + entry.getKey());
                }
            }
            System.out.println("Overlay setup complete. " + overlayLinks.size() + " links created.");
        }
    }

    private void sendOverlayLinkWeights() {
        if (overlayLinks.isEmpty()) {
            System.out.println("Overlay not set up.");
            return;
        }

        List<String> weightedLinks = new ArrayList<>();
        Random random = new Random();
        for (String[] link : overlayLinks) {
            int weight = random.nextInt(10) + 1;
            weightedLinks.add(link[0] + " " + link[1] + " " + weight);
        }

        System.out.println("Sending link weights to all " + registeredNodes.size() + " nodes...");
        synchronized (registeredNodes) {
            for (ServerThread thread : registeredNodes.values()) {
                try {
                    thread.sendLinkWeights(weightedLinks);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public int getRegisteredNodeCount() {
        return registeredNodes.size();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java csx55.overlay.node.Registry <port-number>");
            return;
        }
        new Registry().start(Integer.parseInt(args[0]));
    }
}