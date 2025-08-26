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
 * Service for handling node registration and deregistration
 */
public class NodeRegistrationService {
    private final Map<String, TCPConnection> registeredNodes = new ConcurrentHashMap<>();
    private final TCPConnectionsCache connectionsCache;
    private TaskOrchestrationService taskService;
    
    public NodeRegistrationService(TCPConnectionsCache connectionsCache) {
        this.connectionsCache = connectionsCache;
    }
    
    public void setTaskService(TaskOrchestrationService taskService) {
        this.taskService = taskService;
    }
    
    public void handleRegisterRequest(RegisterRequest request, TCPConnection connection) throws IOException {
        // Validate input parameters
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
        
        // Check if a task is in progress
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
    
    public Map<String, TCPConnection> getRegisteredNodes() {
        return new ConcurrentHashMap<>(registeredNodes);
    }
    
    public int getNodeCount() {
        return registeredNodes.size();
    }
}