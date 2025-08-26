package csx55.overlay.node;

import csx55.overlay.node.registry.*;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.wireformats.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Registry implements TCPConnection.TCPConnectionListener {
    private final TCPConnectionsCache connectionsCache = new TCPConnectionsCache();
    private final NodeRegistrationService registrationService;
    private final OverlayManagementService overlayService;
    private final TaskOrchestrationService taskService;
    private final StatisticsCollectionService statisticsService;
    private final RegistryCommandHandler commandHandler;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public Registry() {
        this.registrationService = new NodeRegistrationService(connectionsCache);
        this.overlayService = new OverlayManagementService(registrationService);
        this.statisticsService = new StatisticsCollectionService();
        this.taskService = new TaskOrchestrationService(registrationService, statisticsService);
        this.registrationService.setTaskService(taskService);
        this.commandHandler = new RegistryCommandHandler(this);
    }

    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            LoggerUtil.info("Registry", "Registry listening on port: " + port);

            // Start command handler
            new Thread(commandHandler::startCommandLoop).start();
            
            // Add shutdown hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

            // Accept incoming connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    new TCPConnection(clientSocket, this);
                } catch (IOException e) {
                    if (running) {
                        LoggerUtil.error("Registry", "Failed to create connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LoggerUtil.error("Registry", "Failed to start registry on port " + port, e);
            cleanup();
        }
    }
    
    private void cleanup() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LoggerUtil.warn("Registry", "Error during cleanup: " + e.getMessage());
        }
    }

    @Override
    public void onEvent(Event event, TCPConnection connection) {
        try {
            switch (event.getType()) {
                case Protocol.REGISTER_REQUEST:
                    registrationService.handleRegisterRequest((RegisterRequest) event, connection);
                    break;
                case Protocol.DEREGISTER_REQUEST:
                    registrationService.handleDeregisterRequest((DeregisterRequest) event, connection);
                    break;
                case Protocol.TASK_COMPLETE:
                    taskService.handleTaskComplete((TaskComplete) event, connection);
                    break;
                case Protocol.TRAFFIC_SUMMARY:
                    statisticsService.handleTrafficSummary((TrafficSummary) event, connection);
                    break;
                default:
                    LoggerUtil.warn("Registry", "Unknown event type received: " + event.getType());
            }
        } catch (IOException e) {
            LoggerUtil.error("Registry", "Error handling event type " + event.getType(), e);
        }
    }

    @Override
    public void onConnectionLost(TCPConnection connection) {
        LoggerUtil.info("Registry", "Connection lost with: " + connection.getSocket().getInetAddress());
        // Remove the disconnected node from registered nodes
        registrationService.handleConnectionLost(connection);
    }

    // Command handler delegates
    void listMessagingNodes() {
        registrationService.listMessagingNodes();
    }

    void listWeights() {
        overlayService.listWeights();
    }

    void setupOverlay(int cr) {
        overlayService.setupOverlay(cr);
    }

    void sendOverlayLinkWeights() {
        overlayService.sendOverlayLinkWeights();
    }

    void startMessaging(int numberOfRounds) {
        taskService.startMessaging(numberOfRounds);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java csx55.overlay.node.Registry <port>");
            return;
        }
        new Registry().start(Integer.parseInt(args[0]));
    }
}