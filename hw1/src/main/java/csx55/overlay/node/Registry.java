package csx55.overlay.node;

import csx55.overlay.node.registry.*;
import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.wireformats.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The central registry node for the overlay network.
 * Manages node registration, overlay topology setup, task orchestration,
 * and statistics collection from all messaging nodes.
 * 
 * This registry acts as the control plane for the distributed system,
 * coordinating overlay construction and messaging tasks.
 */
public class Registry implements TCPConnection.TCPConnectionListener {
    private final TCPConnectionsCache connectionsCache = new TCPConnectionsCache();
    private final NodeRegistrationService registrationService;
    private final OverlayManagementService overlayService;
    private final TaskOrchestrationService taskService;
    private final StatisticsCollectionService statisticsService;
    private final RegistryCommandHandler commandHandler;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    /**
     * Constructs a new Registry and initializes all services.
     */
    public Registry() {
        this.registrationService = new NodeRegistrationService(connectionsCache);
        this.overlayService = new OverlayManagementService(registrationService);
        this.statisticsService = new StatisticsCollectionService();
        this.taskService = new TaskOrchestrationService(registrationService, statisticsService, overlayService);
        this.registrationService.setTaskService(taskService);
        this.commandHandler = new RegistryCommandHandler(this);
    }

    /**
     * Starts the registry server on the specified port.
     * Begins accepting connections from messaging nodes.
     * 
     * @param port the port number to listen on
     */
    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            LoggerUtil.info("Registry", "Registry listening on port: " + port);

            new Thread(commandHandler::startCommandLoop).start();
            
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
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
    
    /**
     * Performs cleanup operations when the registry is shutting down.
     * Closes the server socket and releases resources.
     */
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

    /**
     * Handles incoming events from messaging nodes.
     * Processes registration requests, task completions, and traffic summaries.
     * 
     * @param event the event received from a messaging node
     * @param connection the TCP connection that received the event
     */
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

    /**
     * Handles lost connections to messaging nodes.
     * Removes the disconnected node from the registry.
     * 
     * @param connection the TCP connection that was lost
     */
    @Override
    public void onConnectionLost(TCPConnection connection) {
        LoggerUtil.info("Registry", "Connection lost with: " + connection.getSocket().getInetAddress());
        registrationService.handleConnectionLost(connection);
    }

    /**
     * Lists all registered messaging nodes.
     * Delegates to the registration service.
     */
    void listMessagingNodes() {
        registrationService.listMessagingNodes();
    }

    /**
     * Lists all link weights in the overlay.
     * Delegates to the overlay management service.
     */
    void listWeights() {
        overlayService.listWeights();
    }

    /**
     * Sets up the overlay with the specified connection requirement.
     * 
     * @param cr the number of connections per node
     */
    void setupOverlay(int cr) {
        overlayService.setupOverlay(cr);
    }

    /**
     * Sends link weights to all messaging nodes.
     * Delegates to the overlay management service.
     */
    void sendOverlayLinkWeights() {
        overlayService.sendOverlayLinkWeights();
    }

    /**
     * Starts a messaging task with the specified number of rounds.
     * 
     * @param numberOfRounds the number of rounds for the messaging task
     */
    void startMessaging(int numberOfRounds) {
        taskService.startMessaging(numberOfRounds);
    }

    /**
     * Main entry point for the Registry application.
     * 
     * @param args command line arguments: port-number
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java csx55.overlay.node.Registry <port>");
            return;
        }
        new Registry().start(Integer.parseInt(args[0]));
    }
}