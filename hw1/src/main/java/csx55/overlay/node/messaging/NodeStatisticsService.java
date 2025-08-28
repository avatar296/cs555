package csx55.overlay.node.messaging;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.MessageRoutingHelper;
import csx55.overlay.wireformats.TrafficSummary;

/**
 * Service responsible for tracking and managing node message statistics. Maintains counters for
 * sent, received, and relayed messages along with summation values for payload data.
 *
 * <p>All methods are synchronized to ensure thread-safe access to statistics.
 */
public class NodeStatisticsService {
  private int sendTracker;
  private int receiveTracker;
  private int relayTracker;
  private long sendSummation;
  private long receiveSummation;

  /**
   * Increments the send statistics with the given payload. Updates both the send counter and the
   * send summation.
   *
   * @param payload the payload value to add to the send summation
   */
  public synchronized void incrementSendStats(int payload) {
    sendTracker++;
    sendSummation += payload;
  }

  /**
   * Increments the receive statistics with the given payload. Updates both the receive counter and
   * the receive summation.
   *
   * @param payload the payload value to add to the receive summation
   */
  public synchronized void incrementReceiveStats(int payload) {
    receiveTracker++;
    receiveSummation += payload;
  }

  /** Increments the relay counter. Called when a message is relayed through this node. */
  public synchronized void incrementRelayStats() {
    relayTracker++;
  }

  /**
   * Sends a traffic summary to the registry. Creates a summary containing all current statistics
   * and transmits it to the registry. Note that counters are not reset after sending.
   *
   * @param nodeIp the IP address of this node
   * @param nodePort the port number of this node
   * @param registryConnection the TCP connection to the registry
   * @return true if the summary was sent successfully, false otherwise
   */
  public synchronized boolean sendTrafficSummary(
      String nodeIp, int nodePort, TCPConnection registryConnection) {
    TrafficSummary summary =
        new TrafficSummary(
            nodeIp,
            nodePort,
            sendTracker,
            sendSummation,
            receiveTracker,
            receiveSummation,
            relayTracker);

    boolean success =
        MessageRoutingHelper.sendEventSafely(
            registryConnection,
            summary,
            String.format("sending traffic summary from %s:%d", nodeIp, nodePort));

    if (success) {
      LoggerUtil.debug("NodeStatistics", "Sent traffic summary to registry");
    }

    return success;
  }

  /** Resets all statistics counters to zero. Should be called when starting a new task. */
  public synchronized void resetCounters() {
    sendTracker = 0;
    receiveTracker = 0;
    relayTracker = 0;
    sendSummation = 0;
    receiveSummation = 0;
  }

  /**
   * Gets the current send counter value.
   *
   * @return the number of messages sent
   */
  public synchronized int getSendTracker() {
    return sendTracker;
  }

  /**
   * Gets the current receive counter value.
   *
   * @return the number of messages received
   */
  public synchronized int getReceiveTracker() {
    return receiveTracker;
  }
}
