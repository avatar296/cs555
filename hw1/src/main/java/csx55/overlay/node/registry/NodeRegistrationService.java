package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.transport.TCPConnectionsCache;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.ValidationUtil;
import csx55.overlay.wireformats.DeregisterRequest;
import csx55.overlay.wireformats.DeregisterResponse;
import csx55.overlay.wireformats.RegisterRequest;
import csx55.overlay.wireformats.RegisterResponse;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing node registration and deregistration in the overlay network.
 * Maintains a registry of all active nodes, validates registration requests,
 * and handles connection lifecycle events.
 * 
 * This service ensures proper validation of node identities and manages
 * the synchronized access to the registered nodes collection.
 */
public class NodeRegistrationService {
    private final TCPConnectionsCache connectionsCache;
    private final Map<String, TCPConnection> registeredNodes = new ConcurrentHashMap<>();
    private TaskOrchestrationService taskService;
    
    /**
     * Constructs a new NodeRegistrationService.
     * 
     * @param connectionsCache the cache for managing TCP connections
     */
    public NodeRegistrationService(TCPConnectionsCache connectionsCache) {
        this.connectionsCache = connectionsCache;
    }
    
    /**
     * Sets the task orchestration service reference.
     * 
     * @param taskService the task orchestration service to use
     */
    public void setTaskService(TaskOrchestrationService taskService) {
        this.taskService = taskService;
    }
    
    /**
     * Handles a node registration request.
     * Validates the request parameters including IP address and port,
     * verifies the actual connection source, and adds the node to the registry.
     * 
     * @param request the registration request containing node information
     * @param connection the TCP connection from the requesting node
     * @throws IOException if an error occurs while sending the response
     */
    public void handleRegisterRequest(RegisterRequest request, TCPConnection connection) throws IOException {
        if (!ValidationUtil.isValidIpAddress(request.getIpAddress())) {
            LoggerUtil.warn("NodeRegistration", "Invalid IP address in registration: " + request.getIpAddress());
            connection.sendEvent(new RegisterResponse(
                (byte) 0,
                "Registration failed: Invalid IP address format"
            ));
            return;
        }
        
        if (!ValidationUtil.isValidPort(request.getPortNumber())) {
            LoggerUtil.warn("NodeRegistration", "Invalid port in registration: " + request.getPortNumber());
            connection.sendEvent(new RegisterResponse(
                (byte) 0,
                "Registration failed: Invalid port number (must be " + ValidationUtil.MIN_PORT + "-" + ValidationUtil.MAX_PORT + ")"
            ));
            return;
        }
        
        String nodeId = request.getIpAddress() + ":" + request.getPortNumber();
        Socket socket = connection.getSocket();
        String actualAddress = socket.getInetAddress().getHostAddress();
        
        if (!request.getIpAddress().equals(actualAddress)) {
            LoggerUtil.warn("NodeRegistration", "IP mismatch - claimed: " + request.getIpAddress() + ", actual: " + actualAddress);
            connection.sendEvent(new RegisterResponse(
                (byte) 0, 
                "Registration failed: IP mismatch (claimed: " + request.getIpAddress() + ", actual: " + actualAddress + ")"
            ));
            return;
        }
        
        synchronized (registeredNodes) {
            if (registeredNodes.containsKey(nodeId)) {
                connection.sendEvent(new RegisterResponse(
                    (byte) 0,
                    "Registration failed: Node already registered"
                ));
                return;
            }
            
            registeredNodes.put(nodeId, connection);
            connectionsCache.addConnection(nodeId, connection);
            
            String successMsg = String.format(
                "Registration request successful. The number of messaging nodes currently constituting the overlay is (%d)",
                registeredNodes.size()
            );
            connection.sendEvent(new RegisterResponse((byte) 1, successMsg));
            LoggerUtil.info("NodeRegistration", "Registered node: " + nodeId + " (total: " + registeredNodes.size() + ")");
        }
    }
    
    /**
     * Handles a node deregistration request.
     * Validates the request, ensures no task is in progress,
     * and removes the node from the registry.
     * 
     * @param request the deregistration request containing node information
     * @param connection the TCP connection from the requesting node
     * @throws IOException if an error occurs while sending the response
     */
    public void handleDeregisterRequest(DeregisterRequest request, TCPConnection connection) throws IOException {
        String nodeId = request.getIpAddress() + ":" + request.getPortNumber();
        Socket socket = connection.getSocket();
        String actualAddress = socket.getInetAddress().getHostAddress();
        
        if (!request.getIpAddress().equals(actualAddress)) {
            connection.sendEvent(new DeregisterResponse(
                (byte) 0,
                "Deregistration failed: IP mismatch"
            ));
            return;
        }
        
        if (taskService != null && taskService.isTaskInProgress()) {
            connection.sendEvent(new DeregisterResponse(
                (byte) 0,
                "Deregistration failed: Task is in progress. Please wait for completion."
            ));
            return;
        }
        
        synchronized (registeredNodes) {
            if (!registeredNodes.containsKey(nodeId)) {
                connection.sendEvent(new DeregisterResponse(
                    (byte) 0,
                    "Deregistration failed: Node not registered"
                ));
                return;
            }
            
            registeredNodes.remove(nodeId);
            connectionsCache.removeConnection(nodeId);
            
            String successMsg = String.format(
                "Deregistration successful. Remaining nodes: %d",
                registeredNodes.size()
            );
            connection.sendEvent(new DeregisterResponse((byte) 1, successMsg));
            LoggerUtil.info("NodeRegistration", "Deregistered node: " + nodeId + " (remaining: " + registeredNodes.size() + ")");
        }
    }
    
    /**
     * Lists all registered messaging nodes to the console.
     * Displays each node's identifier (IP:port) or a message if no nodes are registered.
     */
    public void listMessagingNodes() {
        synchronized (registeredNodes) {
            if (registeredNodes.isEmpty()) {
                System.out.println("No messaging nodes registered.");
                return;
            }
            
            for (String nodeId : registeredNodes.keySet()) {
                System.out.println(nodeId);
            }
        }
    }
    
    /**
     * Gets a copy of all registered nodes.
     * 
     * @return a new map containing all registered node IDs and their connections
     */
    public Map<String, TCPConnection> getRegisteredNodes() {
        return new ConcurrentHashMap<>(registeredNodes);
    }
    
    /**
     * Gets the count of registered nodes.
     * 
     * @return the number of nodes currently registered
     */
    public int getNodeCount() {
        return registeredNodes.size();
    }
    
    /**
     * Handles the loss of a connection to a registered node.
     * Removes the disconnected node from the registry and connections cache.
     * 
     * @param lostConnection the connection that was lost
     */
    public void handleConnectionLost(TCPConnection lostConnection) {
        synchronized (registeredNodes) {
            String nodeToRemove = null;
            for (Map.Entry<String, TCPConnection> entry : registeredNodes.entrySet()) {
                if (entry.getValue() == lostConnection) {
                    nodeToRemove = entry.getKey();
                    break;
                }
            }
            
            if (nodeToRemove != null) {
                registeredNodes.remove(nodeToRemove);
                connectionsCache.removeConnection(nodeToRemove);
                LoggerUtil.info("NodeRegistration", 
                    "Removed disconnected node: " + nodeToRemove + " (remaining: " + registeredNodes.size() + ")");
            }
        }
    }
}