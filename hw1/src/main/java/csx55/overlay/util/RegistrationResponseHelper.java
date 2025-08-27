package csx55.overlay.util;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.wireformats.DeregisterResponse;
import csx55.overlay.wireformats.RegisterResponse;

/**
 * Helper class for handling registration and deregistration responses.
 * Centralizes response creation and sending logic to reduce code duplication
 * in the NodeRegistrationService.
 */
public final class RegistrationResponseHelper {
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private RegistrationResponseHelper() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Sends a registration failure response to the client.
     * 
     * @param connection the TCP connection to send the response to
     * @param reason the reason for the failure
     * @return true if the response was sent successfully, false otherwise
     */
    public static boolean sendRegistrationFailure(TCPConnection connection, String reason) {
        RegisterResponse response = new RegisterResponse((byte) 0, 
            "Registration failed: " + reason);
        return MessageRoutingHelper.sendEventSafely(connection, response, 
            "sending registration failure response");
    }
    
    /**
     * Sends a registration success response to the client.
     * 
     * @param connection the TCP connection to send the response to
     * @param nodeId the ID of the registered node
     * @param totalNodes the total number of nodes in the overlay
     * @return true if the response was sent successfully, false otherwise
     */
    public static boolean sendRegistrationSuccess(TCPConnection connection, String nodeId, int totalNodes) {
        String message = String.format(
            "Registration request successful. The number of messaging nodes currently constituting the overlay is (%d)",
            totalNodes
        );
        RegisterResponse response = new RegisterResponse((byte) 1, message);
        return MessageRoutingHelper.sendEventSafely(connection, response, 
            String.format("sending registration success for %s", nodeId));
    }
    
    /**
     * Sends a deregistration failure response to the client.
     * 
     * @param connection the TCP connection to send the response to
     * @param reason the reason for the failure
     * @return true if the response was sent successfully, false otherwise
     */
    public static boolean sendDeregistrationFailure(TCPConnection connection, String reason) {
        DeregisterResponse response = new DeregisterResponse((byte) 0, 
            "Deregistration failed: " + reason);
        return MessageRoutingHelper.sendEventSafely(connection, response, 
            "sending deregistration failure response");
    }
    
    /**
     * Sends a deregistration success response to the client.
     * 
     * @param connection the TCP connection to send the response to
     * @param remainingNodes the number of nodes remaining in the overlay
     * @return true if the response was sent successfully, false otherwise
     */
    public static boolean sendDeregistrationSuccess(TCPConnection connection, int remainingNodes) {
        String message = String.format(
            "Deregistration successful. Remaining nodes: %d",
            remainingNodes
        );
        DeregisterResponse response = new DeregisterResponse((byte) 1, message);
        return MessageRoutingHelper.sendEventSafely(connection, response, 
            "sending deregistration success response");
    }
}