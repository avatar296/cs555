package csx55.overlay.util;

import csx55.overlay.transport.TCPConnection;
import java.net.Socket;
import java.util.Map;

/**
 * Helper class for common node validation operations. Centralizes validation logic to reduce code
 * duplication in the NodeRegistrationService.
 */
public final class NodeValidationHelper {

  /** Private constructor to prevent instantiation of utility class. */
  private NodeValidationHelper() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Creates a node identifier from IP address and port.
   *
   * @param ipAddress the IP address of the node
   * @param port the port number of the node
   * @return the node identifier in format "ip:port"
   */
  public static String createNodeId(String ipAddress, int port) {
    return ipAddress + ":" + port;
  }

  /**
   * Validates that the claimed IP address matches the actual connection source.
   *
   * @param claimedIp the IP address claimed in the request
   * @param socket the actual socket connection
   * @return ValidationResult containing success status and error message if failed
   */
  public static ValidationResult validateIpMatch(String claimedIp, Socket socket) {
    String actualAddress = socket.getInetAddress().getHostAddress();

    if (!claimedIp.equals(actualAddress)) {
      String errorMsg =
          String.format("IP mismatch (claimed: %s, actual: %s)", claimedIp, actualAddress);
      LoggerUtil.warn("NodeValidation", errorMsg);
      return new ValidationResult(false, errorMsg);
    }

    return new ValidationResult(true, null);
  }

  /**
   * Validates that the IP address format is correct.
   *
   * @param ipAddress the IP address to validate
   * @return ValidationResult containing success status and error message if failed
   */
  public static ValidationResult validateIpFormat(String ipAddress) {
    if (!ValidationUtil.isValidIpAddress(ipAddress)) {
      String errorMsg = "Invalid IP address format";
      LoggerUtil.warn("NodeValidation", "Invalid IP address: " + ipAddress);
      return new ValidationResult(false, errorMsg);
    }
    return new ValidationResult(true, null);
  }

  /**
   * Validates that the port number is within valid range.
   *
   * @param port the port number to validate
   * @return ValidationResult containing success status and error message if failed
   */
  public static ValidationResult validatePort(int port) {
    if (!ValidationUtil.isValidPort(port)) {
      String errorMsg =
          String.format(
              "Invalid port number (must be %d-%d)",
              ValidationUtil.MIN_PORT, ValidationUtil.MAX_PORT);
      LoggerUtil.warn("NodeValidation", "Invalid port: " + port);
      return new ValidationResult(false, errorMsg);
    }
    return new ValidationResult(true, null);
  }

  /**
   * Validates that a node exists in the registry.
   *
   * @param nodeId the node identifier to check
   * @param registeredNodes the map of registered nodes
   * @return ValidationResult containing success status and error message if not found
   */
  public static ValidationResult validateNodeExists(
      String nodeId, Map<String, TCPConnection> registeredNodes) {
    if (!registeredNodes.containsKey(nodeId)) {
      return new ValidationResult(false, "Node not registered");
    }
    return new ValidationResult(true, null);
  }

  /**
   * Validates that a node does not exist in the registry.
   *
   * @param nodeId the node identifier to check
   * @param registeredNodes the map of registered nodes
   * @return ValidationResult containing success status and error message if already exists
   */
  public static ValidationResult validateNodeDoesNotExist(
      String nodeId, Map<String, TCPConnection> registeredNodes) {
    if (registeredNodes.containsKey(nodeId)) {
      return new ValidationResult(false, "Node already registered");
    }
    return new ValidationResult(true, null);
  }

  /** Result class for validation operations. Contains success status and optional error message. */
  public static class ValidationResult {
    private final boolean success;
    private final String errorMessage;

    public ValidationResult(boolean success, String errorMessage) {
      this.success = success;
      this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
      return success;
    }

    public String getErrorMessage() {
      return errorMessage;
    }
  }
}
