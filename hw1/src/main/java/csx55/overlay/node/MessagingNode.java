package csx55.overlay.node;

import csx55.overlay.node.messaging.*;
import csx55.overlay.routing.RoutingTable;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.wireformats.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessagingNode implements TCPConnection.TCPConnectionListener {
    private String nodeId;
    private String ipAddress;
    private int portNumber;
    private TCPConnection registryConnection;
    private final TCPConnectionsCache peerConnections = new TCPConnectionsCache();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    // Services
    private final NodeStatisticsService statisticsService;
    private final MessageRoutingService routingService;
    private final ProtocolHandlerService protocolHandler;
    private final TaskExecutionService taskService;
    private final MessagingNodeCommandHandler commandHandler;

    private List<String> allNodes = new ArrayList<>();

    public MessagingNode() {
        this.statisticsService = new NodeStatisticsService();
        this.routingService = new MessageRoutingService(peerConnections, statisticsService);
        this.protocolHandler = new ProtocolHandlerService(peerConnections, routingService);
        this.taskService = new TaskExecutionService(routingService, executorService);
        this.commandHandler = new MessagingNodeCommandHandler(this);
    }

    private void start(String registryHost, int registryPort) {
        try {
            serverSocket = new ServerSocket(0);
            this.portNumber = serverSocket.getLocalPort();
            this.ipAddress = InetAddress.getLocalHost().getHostAddress();
            this.nodeId = ipAddress + ":" + portNumber;

            // Initialize services with node info
            routingService.setNodeId(nodeId);
            protocolHandler.setNodeInfo(nodeId, allNodes);
            taskService.setNodeInfo(nodeId, ipAddress, portNumber, null);

            // Connect to registry
            Socket socket = new Socket(registryHost, registryPort);
            registryConnection = new TCPConnection(socket, this);
            taskService.setNodeInfo(nodeId, ipAddress, portNumber, registryConnection);

            RegisterRequest request = new RegisterRequest(ipAddress, portNumber);
            registryConnection.sendEvent(request);

            // Listen for peer connections
            executorService.execute(() -> {
                while (running) {
                    try {
                        Socket peerSocket = serverSocket.accept();
                        // Create connection for incoming peer
                        // The peer's actual node ID will be identified through messages
                        TCPConnection peerConnection = new TCPConnection(peerSocket, this);
                        // Store temporarily with socket info - will be updated when we know the real node ID
                        String tempId = peerSocket.getInetAddress().getHostAddress() + ":" + peerSocket.getPort();
                        peerConnections.addConnection(tempId, peerConnection);
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("Error accepting peer: " + e.getMessage());
                        }
                    }
                }
            });

            // Start command handler
            new Thread(commandHandler::startCommandLoop).start();
            
            // Add shutdown hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

        } catch (IOException e) {
            System.err.println("Failed to start messaging node: " + e.getMessage());
            cleanup();
        }
    }
    
    private void cleanup() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executorService.shutdownNow();
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    @Override
    public void onEvent(Event event, TCPConnection connection) {
        try {
            switch (event.getType()) {
                case Protocol.REGISTER_RESPONSE:
                    protocolHandler.handleRegisterResponse((RegisterResponse) event);
                    break;
                case Protocol.DEREGISTER_RESPONSE:
                    if (protocolHandler.handleDeregisterResponse((DeregisterResponse) event)) {
                        cleanup();
                        System.exit(0);
                    }
                    break;
                case Protocol.MESSAGING_NODES_LIST:
                    protocolHandler.handlePeerList((MessagingNodesList) event, this);
                    break;
                case Protocol.LINK_WEIGHTS:
                    protocolHandler.handleLinkWeights((LinkWeights) event);
                    this.allNodes = protocolHandler.getAllNodes();
                    break;
                case Protocol.TASK_INITIATE:
                    taskService.handleTaskInitiate((TaskInitiate) event, allNodes);
                    break;
                case Protocol.PULL_TRAFFIC_SUMMARY:
                    statisticsService.sendTrafficSummary(ipAddress, portNumber, registryConnection);
                    break;
                case Protocol.DATA_MESSAGE:
                    routingService.handleDataMessage((DataMessage) event);
                    break;
            }
        } catch (IOException e) {
            System.err.println("Error handling event: " + e.getMessage());
        }
    }

    @Override
    public void onConnectionLost(TCPConnection connection) {
        // Remove from cache by finding matching connection
        for (String nodeId : peerConnections.getAllConnections().keySet()) {
            if (peerConnections.getConnection(nodeId) == connection) {
                peerConnections.removeConnection(nodeId);
                break;
            }
        }
    }

    // Command handler methods
    void deregister() {
        try {
            DeregisterRequest request = new DeregisterRequest(ipAddress, portNumber);
            registryConnection.sendEvent(request);
        } catch (IOException e) {
            System.err.println("Failed to deregister: " + e.getMessage());
        }
    }

    void printMinimumSpanningTree() {
        RoutingTable table = routingService.getRoutingTable();
        if (table != null) {
            table.printMST();
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java MessagingNode <registry-host> <registry-port>");
            return;
        }
        new MessagingNode().start(args[0], Integer.parseInt(args[1]));
    }
}