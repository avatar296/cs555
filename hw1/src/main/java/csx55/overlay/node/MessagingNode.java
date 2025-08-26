package csx55.overlay.node;

import csx55.overlay.node.messaging.*;
import csx55.overlay.routing.RoutingTable;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
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
        this.taskService = new TaskExecutionService(routingService, executorService, statisticsService);
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
                        // The peer will identify itself through a PEER_IDENTIFICATION message
                        TCPConnection peerConnection = new TCPConnection(peerSocket, this);
                        // Don't add to cache yet - wait for peer identification
                    } catch (IOException e) {
                        if (running) {
                            LoggerUtil.error("MessagingNode", "Error accepting peer connection", e);
                        }
                    }
                }
            });

            // Start command handler
            new Thread(commandHandler::startCommandLoop).start();
            
            // Add shutdown hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

        } catch (IOException e) {
            LoggerUtil.error("MessagingNode", "Failed to start messaging node", e);
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
            try {
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    LoggerUtil.warn("MessagingNode", "ExecutorService did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            peerConnections.closeAll();
            if (registryConnection != null) {
                registryConnection.close();
            }
        } catch (IOException e) {
            LoggerUtil.warn("MessagingNode", "Error during cleanup", e);
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
                case Protocol.PEER_IDENTIFICATION:
                    protocolHandler.handlePeerIdentification((PeerIdentification) event, connection);
                    break;
            }
        } catch (IOException e) {
            LoggerUtil.error("MessagingNode", "Error handling event type " + event.getType(), e);
        }
    }

    @Override
    public void onConnectionLost(TCPConnection connection) {
        // Check if this is the registry connection
        if (connection == registryConnection) {
            LoggerUtil.error("MessagingNode", "Lost connection to Registry. Shutting down...");
            cleanup();
            System.exit(1);
        }
        
        // Remove from peer connections cache by finding matching connection
        synchronized (peerConnections) {
            for (String nodeId : peerConnections.getAllConnections().keySet()) {
                if (peerConnections.getConnection(nodeId) == connection) {
                    peerConnections.removeConnection(nodeId);
                    LoggerUtil.info("MessagingNode", "Lost connection to peer: " + nodeId);
                    break;
                }
            }
        }
    }

    // Command handler methods
    void deregister() {
        try {
            DeregisterRequest request = new DeregisterRequest(ipAddress, portNumber);
            registryConnection.sendEvent(request);
        } catch (IOException e) {
            LoggerUtil.error("MessagingNode", "Failed to deregister", e);
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
            System.out.println("Usage: java csx55.overlay.node.MessagingNode <registry-host> <registry-port>");
            return;
        }
        new MessagingNode().start(args[0], Integer.parseInt(args[1]));
    }
}