package csx55.overlay.node;

import csx55.overlay.node.registry.*;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
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

    public Registry() {
        this.registrationService = new NodeRegistrationService(connectionsCache);
        this.overlayService = new OverlayManagementService(registrationService);
        this.statisticsService = new StatisticsCollectionService();
        this.taskService = new TaskOrchestrationService(registrationService, statisticsService);
        this.commandHandler = new RegistryCommandHandler(this);
    }

    public void start(int port) {
        try {
            @SuppressWarnings("resource")
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Registry listening on port: " + port);

            // Start command handler
            new Thread(commandHandler::startCommandLoop).start();

            // Accept incoming connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                try {
                    new TCPConnection(clientSocket, this);
                } catch (IOException e) {
                    System.err.println("Failed to create connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start registry: " + e.getMessage());
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
                    System.out.println("Unknown event type: " + event.getType());
            }
        } catch (IOException e) {
            System.err.println("Error handling event: " + e.getMessage());
        }
    }

    @Override
    public void onConnectionLost(TCPConnection connection) {
        System.out.println("Connection lost with: " + connection.getSocket().getInetAddress());
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
            System.out.println("Usage: java Registry <port>");
            return;
        }
        new Registry().start(Integer.parseInt(args[0]));
    }
}