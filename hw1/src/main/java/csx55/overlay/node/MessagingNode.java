package csx55.overlay.node;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;

public class MessagingNode {
    private String nodeId;
    private final Map<String, List<Edge>> graph = new HashMap<>();
    private final Map<String, Edge> mst = new HashMap<>();

    private void start(String registryHost, int registryPort) {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            this.nodeId = serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort();
            System.out.println("Node (" + nodeId + ") listening on port: " + serverSocket.getLocalPort());

            new Thread(() -> connectAndListenToRegistry(registryHost, registryPort, serverSocket.getLocalPort()))
                    .start();
            new Thread(() -> listenForPeers(serverSocket)).start();

            handleUserCommands();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectAndListenToRegistry(String registryHost, int registryPort, int listeningPort) {
        try (Socket socket = new Socket(registryHost, registryPort)) {
            DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
            DataInputStream din = new DataInputStream(socket.getInputStream());

            dout.writeInt(1); // REGISTER_REQUEST
            dout.writeUTF(socket.getLocalAddress().getHostAddress());
            dout.writeInt(listeningPort);
            dout.flush();

            while (!socket.isClosed()) {
                int messageType = din.readInt();
                switch (messageType) {
                    case 2:
                        handleRegistrationResponse(din);
                        break;
                    case 3:
                        handlePeerList(din);
                        break;
                    case 4:
                        handleLinkWeights(din);
                        calculateMST();
                        break;
                    default:
                        System.out.println("Unknown message type from Registry: " + messageType);
                }
            }
        } catch (IOException e) {
            System.out.println("Connection to Registry lost.");
        }
    }

    private void handleRegistrationResponse(DataInputStream din) throws IOException {
        din.readByte(); // status
        System.out.println("Registry response: " + din.readUTF());
    }

    private void handlePeerList(DataInputStream din) throws IOException {
        int numberOfPeers = din.readInt();
        for (int i = 0; i < numberOfPeers; i++) {
            String peerInfo = din.readUTF();
            String[] parts = peerInfo.split(":");
            try {
                Socket peerSocket = new Socket(parts[0], Integer.parseInt(parts[1]));
                System.out.println("Established connection to peer: " + peerInfo);
                new Thread(new PeerConnectionHandler(peerSocket, this)).start();
            } catch (IOException e) {
                System.err.println("Failed to connect to peer: " + peerInfo);
            }
        }
    }

    private void listenForPeers(ServerSocket serverSocket) {
        try {
            while (true) {
                Socket peerSocket = serverSocket.accept();
                System.out.println("Accepted connection from a peer: " + peerSocket.getInetAddress().getHostAddress());
                new Thread(new PeerConnectionHandler(peerSocket, this)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleLinkWeights(DataInputStream din) throws IOException {
        int numberOfLinks = din.readInt();
        synchronized (graph) {
            graph.clear();
            for (int i = 0; i < numberOfLinks; i++) {
                String[] parts = din.readUTF().split(" ");
                String nodeA = parts[0];
                String nodeB = parts[1];
                int weight = Integer.parseInt(parts[2]);
                graph.computeIfAbsent(nodeA, k -> new ArrayList<>()).add(new Edge(nodeB, weight));
                graph.computeIfAbsent(nodeB, k -> new ArrayList<>()).add(new Edge(nodeA, weight));
            }
        }
        System.out.println("Link weights processed and graph constructed.");
    }

    private void calculateMST() {
        synchronized (graph) {
            mst.clear();
            if (graph.isEmpty() || !graph.containsKey(nodeId))
                return;

            Map<String, Edge> cheapestEdgeToNode = new HashMap<>();
            PriorityQueue<Edge> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e.weight));
            Set<String> inTree = new HashSet<>();

            inTree.add(nodeId);

            for (Edge edge : graph.get(nodeId)) {
                pq.add(edge);
                cheapestEdgeToNode.put(edge.destination, new Edge(this.nodeId, edge.weight));
            }

            while (!pq.isEmpty() && inTree.size() < graph.size()) {
                Edge currentEdge = pq.poll();
                String nextNode = currentEdge.destination;

                if (inTree.contains(nextNode))
                    continue;

                inTree.add(nextNode);
                mst.put(nextNode, cheapestEdgeToNode.get(nextNode));

                for (Edge neighborEdge : graph.get(nextNode)) {
                    if (!inTree.contains(neighborEdge.destination)) {
                        if (!cheapestEdgeToNode.containsKey(neighborEdge.destination)
                                || cheapestEdgeToNode.get(neighborEdge.destination).weight > neighborEdge.weight) {
                            pq.add(neighborEdge);
                            cheapestEdgeToNode.put(neighborEdge.destination, new Edge(nextNode, neighborEdge.weight));
                        }
                    }
                }
            }
        }
        System.out.println("Minimum Spanning Tree calculated.");
    }

    private void printMinimumSpanningTree() {
        System.out.println("--- Minimum Spanning Tree ---");
        synchronized (mst) {
            if (mst.isEmpty()) {
                System.out.println("MST has not been calculated yet.");
                return;
            }

            Queue<String> queue = new LinkedList<>();
            Set<String> visited = new HashSet<>();
            int totalWeight = 0;

            queue.add(this.nodeId);
            visited.add(this.nodeId);

            while (!queue.isEmpty()) {
                String currentNode = queue.poll();

                // Find children of currentNode in the MST
                for (Map.Entry<String, Edge> entry : mst.entrySet()) {
                    String childNode = entry.getKey();
                    Edge edgeToParent = entry.getValue();
                    String parentNode = edgeToParent.destination;

                    if (parentNode.equals(currentNode) && !visited.contains(childNode)) {
                        System.out.println(parentNode + ", " + childNode + ", " + edgeToParent.weight);
                        totalWeight += edgeToParent.weight;
                        visited.add(childNode);
                        queue.add(childNode);
                    }
                }
            }
            System.out.println("Total Weight: " + totalWeight);
        }
        System.out.println("-----------------------------");
    }

    @SuppressWarnings("resource")
    private void handleUserCommands() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print(this.nodeId + "> ");
            String command = scanner.nextLine();
            if ("print-mst".equalsIgnoreCase(command)) {
                printMinimumSpanningTree();
            } else if ("exit-overlay".equalsIgnoreCase(command)) {
                // TODO: Implement deregistration
                System.exit(0);
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java csx55.overlay.node.MessagingNode <registry-host> <registry-port>");
            return;
        }
        new MessagingNode().start(args[0], Integer.parseInt(args[1]));
    }
}