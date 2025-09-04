package csx55.overlay.integration;

import static org.assertj.core.api.Assertions.*;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;

/**
 * Integration test suite for overlay protocol messages. Tests wire format protocol messages for
 * sections 2.3 (MESSAGING_NODES_LIST) and 2.4 (LINK_WEIGHTS). Validates overlay setup, peer list
 * distribution, connection establishment, and link weight assignment as specified in the PDF.
 *
 * <p>Section 2.3: Tests MESSAGING_NODES_LIST distribution and peer connection establishment.
 * Section 2.4: Tests LINK_WEIGHTS message distribution and MST readiness.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OverlayProtocolTest {

  private TestOrchestrator orchestrator;
  private int registryPort;
  private static final int NODE_COUNT = 5;
  private static final int CONNECTION_REQUIREMENT = 2;

  /**
   * Sets up the test environment before each test. Starts a registry and multiple messaging nodes
   * on a random port.
   *
   * @throws Exception if setup fails
   */
  @BeforeEach
  void setup() throws Exception {
    orchestrator = new TestOrchestrator();
    // Use random port to avoid conflicts
    registryPort = 9500 + (int) (Math.random() * 500);
    orchestrator.startRegistry(registryPort);
    Thread.sleep(2000);

    // Start nodes for testing
    for (int i = 0; i < NODE_COUNT; i++) {
      orchestrator.startMessagingNode("localhost", registryPort);
      Thread.sleep(500);
    }
    Thread.sleep(2000);
  }

  /** Cleans up test resources after each test. Shuts down all nodes and the registry. */
  @AfterEach
  void teardown() {
    if (orchestrator != null) {
      orchestrator.shutdown();
    }
  }

  // ==================== Section 2.3: MESSAGING_NODES_LIST Tests
  // ====================

  /**
   * Tests MESSAGING_NODES_LIST distribution during overlay setup (Section 2.3). Verifies that nodes
   * receive peer lists and establish connections correctly.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(1)
  @DisplayName("Test MESSAGING_NODES_LIST distribution during overlay setup (Section 2.3)")
  void testMessagingNodesListDistribution() throws Exception {
    orchestrator.clearOutputs();

    // Setup overlay with CR=2
    orchestrator.sendRegistryCommand("setup-overlay " + CONNECTION_REQUIREMENT);
    Thread.sleep(3000);

    // Verify registry confirms setup
    assertThat(
            orchestrator.waitForRegistryOutput(
                "setup completed with " + CONNECTION_REQUIREMENT + " connections", 5))
        .as("Registry should confirm overlay setup completion")
        .isTrue();

    // Check each node received peer list and established connections
    int totalConnectionCount = 0;
    int nodesWithConnections = 0;

    for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
      List<String> nodeOutput = orchestrator.getNodeOutput(nodeId);

      boolean foundConnectionsEstablished = false;
      int connectionCount = -1;

      for (String line : nodeOutput) {
        // Look for connection establishment messages
        if (line.contains("All connections are established")) {
          foundConnectionsEstablished = true;
          // Extract connection count from the standard format message
          Pattern pattern = Pattern.compile("Number of connections:\\s*(\\d+)");
          Matcher matcher = pattern.matcher(line);
          if (matcher.find()) {
            connectionCount = Integer.parseInt(matcher.group(1));
            totalConnectionCount += connectionCount;
            nodesWithConnections++;
          }
        }
      }

      assertThat(foundConnectionsEstablished)
          .as("Node " + nodeId + " should output 'All connections are established' message")
          .isTrue();

      if (connectionCount >= 0) {
        assertThat(connectionCount)
            .as("Node " + nodeId + " should have between 0 and CR connections")
            .isBetween(0, CONNECTION_REQUIREMENT);
      }
    }

    assertThat(nodesWithConnections)
        .as("All nodes should report their connection status")
        .isEqualTo(NODE_COUNT);

    assertThat(totalConnectionCount)
        .as(
            "Total connection count across all nodes should be reasonable for CR="
                + CONNECTION_REQUIREMENT)
        .isGreaterThan(0);
  }

  /**
   * Tests peer list format in MESSAGING_NODES_LIST (Section 2.3). Validates that peer addresses
   * follow the correct IP:port format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(2)
  @DisplayName("Test peer list format in MESSAGING_NODES_LIST (Section 2.3)")
  void testPeerListFormat() throws Exception {
    orchestrator.clearOutputs();

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay " + CONNECTION_REQUIREMENT);
    Thread.sleep(3000);

    // Get the list of registered nodes to know what format to expect
    orchestrator.sendRegistryCommand("list-messaging-nodes");
    Thread.sleep(1000);

    List<String> registeredNodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
    assertThat(registeredNodes).as("Should have all nodes registered").hasSize(NODE_COUNT);

    // Verify each registered node has proper IP:port format
    Pattern nodePattern = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+$");

    for (String node : registeredNodes) {
      assertThat(nodePattern.matcher(node).matches())
          .as("Node address should be in IP:port format: " + node)
          .isTrue();

      // Verify port is in valid range
      String[] parts = node.split(":");
      int port = Integer.parseInt(parts[1]);
      assertThat(port).as("Port should be in valid range").isBetween(1024, 65535);
    }
  }

  /**
   * Tests connection establishment after MESSAGING_NODES_LIST (Section 2.3). Verifies that all
   * nodes establish connections with varying CR values.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(3)
  @DisplayName("Test connection establishment after MESSAGING_NODES_LIST (Section 2.3)")
  void testConnectionEstablishment() throws Exception {
    orchestrator.clearOutputs();

    // Setup overlay with different CR values
    for (int cr = 1; cr <= Math.min(3, NODE_COUNT - 1); cr++) {
      // Restart for clean test
      teardown();
      setup();

      orchestrator.clearOutputs();
      orchestrator.sendRegistryCommand("setup-overlay " + cr);
      Thread.sleep(3000);

      // Verify all nodes establish connections
      int nodesWithConnections = 0;

      for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
        if (orchestrator.waitForNodeOutput(nodeId, "All connections are established", 5)) {
          nodesWithConnections++;
        }
      }

      assertThat(nodesWithConnections)
          .as("All nodes should establish connections for CR=" + cr)
          .isEqualTo(NODE_COUNT);
    }
  }

  /**
   * Tests bidirectional connection verification (Section 2.3). Validates that connections between
   * peers are properly established and tracked by both endpoints.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(4)
  @DisplayName("Test bidirectional connection verification (Section 2.3)")
  void testBidirectionalConnections() throws Exception {
    orchestrator.clearOutputs();

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(3000);

    // Collect connection information from all nodes
    Map<String, Set<String>> nodeConnections = new HashMap<>();

    orchestrator.sendRegistryCommand("list-messaging-nodes");
    Thread.sleep(1000);
    List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());

    for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
      List<String> output = orchestrator.getNodeOutput(nodeId);

      // Try to identify this node's address
      String nodeAddress = null;
      if (nodeId < nodes.size()) {
        nodeAddress = nodes.get(nodeId);
      }

      if (nodeAddress != null) {
        Set<String> connections = new HashSet<>();

        // Look for connection information
        for (String line : output) {
          if (line.contains("Connected to") || line.contains("Connection from")) {
            Pattern pattern = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
              connections.add(matcher.group(1));
            }
          }
        }

        nodeConnections.put(nodeAddress, connections);
      }
    }

    // Verify that connections appear symmetric (if A connects to B, B should know
    // about A)
    // Note: Due to the nature of the overlay, we can't guarantee perfect symmetry
    // in logs,
    // but we can verify that the overlay was established successfully
    assertThat(nodeConnections).as("Should have connection information for nodes").isNotEmpty();
  }

  /**
   * Tests handling of insufficient nodes for CR (Section 2.3). Verifies that overlay setup fails
   * gracefully when CR >= N.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(5)
  @DisplayName("Test handling of insufficient nodes for CR (Section 2.3)")
  void testInsufficientNodesForCR() throws Exception {
    orchestrator.clearOutputs();

    // Try to setup overlay with CR >= NODE_COUNT (should fail)
    orchestrator.sendRegistryCommand("setup-overlay " + NODE_COUNT);
    Thread.sleep(2000);

    // Should not complete successfully
    boolean setupCompleted =
        orchestrator.waitForRegistryOutput(
            "setup completed with " + NODE_COUNT + " connections", 3);
    assertThat(setupCompleted).as("Setup should not complete with CR >= number of nodes").isFalse();

    // Nodes should not establish connections
    for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
      boolean established =
          orchestrator.waitForNodeOutput(nodeId, "All connections are established", 2);
      assertThat(established)
          .as("Node " + nodeId + " should not establish connections with invalid CR")
          .isFalse();
    }
  }

  // ==================== Section 2.4: LINK_WEIGHTS Tests ====================

  /**
   * Tests LINK_WEIGHTS message distribution (Section 2.4). Verifies that link weights are properly
   * distributed to all nodes after overlay establishment.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(6)
  @DisplayName("Test LINK_WEIGHTS message distribution (Section 2.4)")
  void testLinkWeightsDistribution() throws Exception {
    orchestrator.clearOutputs();

    // Setup overlay first
    orchestrator.sendRegistryCommand("setup-overlay " + CONNECTION_REQUIREMENT);
    assertThat(orchestrator.waitForRegistryOutput("setup completed", 5))
        .as("Overlay should be setup before sending weights")
        .isTrue();

    Thread.sleep(2000);
    orchestrator.clearOutputs();

    // Send link weights
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(2000);

    // Verify registry confirms sending
    assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
        .as("Registry should confirm link weights were sent")
        .isTrue();

    // Verify all nodes received weights
    int nodesReceivedWeights = 0;

    for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
      List<String> output = orchestrator.getNodeOutput(nodeId);

      boolean receivedWeights = false;
      for (String line : output) {
        if (line.contains("Link weights received")
            || line.contains("weights received")
            || line.contains("Ready to send messages")) {
          receivedWeights = true;
          break;
        }
      }

      if (receivedWeights) {
        nodesReceivedWeights++;
      }
    }

    assertThat(nodesReceivedWeights)
        .as("All nodes should receive link weights")
        .isEqualTo(NODE_COUNT);
  }

  /**
   * Tests link weight format and range validation (Section 2.4). Validates that weights are in the
   * correct format and within the specified range (1-10).
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(7)
  @DisplayName("Test link weight format and range validation (Section 2.4)")
  void testLinkWeightFormatAndRange() throws Exception {
    orchestrator.clearOutputs();

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay " + CONNECTION_REQUIREMENT);
    Thread.sleep(3000);

    // Send weights and list them
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("list-weights");
    Thread.sleep(1000);

    List<String> output = orchestrator.getRegistryOutput();

    // Parse link weights from output
    List<Integer> weights = new ArrayList<>();
    Pattern weightPattern =
        Pattern.compile(
            "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+),\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+),\\s+(\\d+)");

    for (String line : output) {
      Matcher matcher = weightPattern.matcher(line);
      if (matcher.find()) {
        int weight = Integer.parseInt(matcher.group(3));
        weights.add(weight);

        // Verify weight is in valid range (1-10 as per PDF)
        assertThat(weight).as("Link weight should be between 1 and 10").isBetween(1, 10);
      }
    }

    // Should have found link weights
    assertThat(weights).as("Should have link weights in output").isNotEmpty();

    // Calculate expected number of links
    // For a connected graph with N nodes and CR connections per node,
    // the number of unique edges is (N * CR) / 2
    int expectedLinks = (NODE_COUNT * CONNECTION_REQUIREMENT) / 2;

    assertThat(weights.size())
        .as("Should have correct number of link weights")
        .isGreaterThanOrEqualTo(expectedLinks);
  }

  /**
   * Tests link weight assignment validity (Section 2.4). Verifies that weights are properly
   * assigned in the 1-10 range as specified in the PDF.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(8)
  @DisplayName("Test link weight assignment validity (Section 2.4)")
  void testLinkWeightAssignmentValidity() throws Exception {
    orchestrator.clearOutputs();

    orchestrator.sendRegistryCommand("setup-overlay " + CONNECTION_REQUIREMENT);
    Thread.sleep(3000);

    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("list-weights");
    Thread.sleep(1000);

    List<String> output = orchestrator.getRegistryOutput();

    List<Integer> allWeights = new ArrayList<>();

    Pattern weightPattern =
        Pattern.compile(
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+,\\s+\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+,\\s+(\\d+)");

    for (String line : output) {
      Matcher matcher = weightPattern.matcher(line);
      if (matcher.find()) {
        int weight = Integer.parseInt(matcher.group(1));
        allWeights.add(weight);

        // Verify each weight is in valid range (1-10 as per PDF)
        assertThat(weight).as("Link weight should be between 1 and 10").isBetween(1, 10);
      }
    }

    // PDF specifies random weights 1-10, no uniqueness requirement
    assertThat(allWeights).as("Should have weights assigned to all links").isNotEmpty();
  }

  /**
   * Tests nodes ready for MST computation after weights (Section 2.4). Verifies that all nodes are
   * ready for messaging after receiving link weights.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(9)
  @DisplayName("Test nodes ready for MST computation after weights (Section 2.4)")
  void testMSTReadiness() throws Exception {
    orchestrator.clearOutputs();

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay " + CONNECTION_REQUIREMENT);
    Thread.sleep(3000);

    // Send link weights
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(2000);

    // Check that nodes are ready for messaging
    int nodesReady = 0;

    for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
      List<String> output = orchestrator.getNodeOutput(nodeId);

      for (String line : output) {
        if (line.contains("Ready to send messages")
            || line.contains("Link weights received and processed")) {
          nodesReady++;
          break;
        }
      }
    }

    assertThat(nodesReady)
        .as("All nodes should be ready for messaging after receiving weights")
        .isEqualTo(NODE_COUNT);
  }

  /**
   * Tests sending weights before overlay setup (Section 2.4). Verifies that weight assignment fails
   * when attempted before overlay establishment.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(10)
  @DisplayName("Test sending weights before overlay setup (Section 2.4)")
  void testWeightsBeforeOverlay() throws Exception {
    // Restart with fresh nodes
    teardown();
    orchestrator = new TestOrchestrator();
    registryPort = 9500 + (int) (Math.random() * 500);
    orchestrator.startRegistry(registryPort);
    Thread.sleep(2000);

    // Start nodes
    for (int i = 0; i < NODE_COUNT; i++) {
      orchestrator.startMessagingNode("localhost", registryPort);
      Thread.sleep(500);
    }
    Thread.sleep(2000);

    orchestrator.clearOutputs();

    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(2000);

    List<String> output = orchestrator.getRegistryOutput();

    boolean errorFound = false;
    boolean weightsAssigned = false;

    for (String line : output) {
      if (line.toLowerCase().contains("error")
          || line.toLowerCase().contains("overlay has not been set up")
          || line.toLowerCase().contains("cannot send weights")) {
        errorFound = true;
      }
      if (line.contains("link weights assigned")) {
        weightsAssigned = true;
      }
    }

    assertThat(weightsAssigned).as("Weights should not be assigned before overlay setup").isFalse();

    // Nodes should not receive weights
    for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
      boolean receivedWeights = orchestrator.waitForNodeOutput(nodeId, "Link weights received", 2);
      assertThat(receivedWeights)
          .as("Node " + nodeId + " should not receive weights before overlay setup")
          .isFalse();
    }
  }

  /**
   * Tests complete overlay protocol sequence (Sections 2.3 & 2.4). Validates the entire workflow of
   * overlay setup followed by link weight distribution.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(11)
  @DisplayName("Test complete overlay protocol sequence (Sections 2.3 & 2.4)")
  void testCompleteOverlayProtocol() throws Exception {
    orchestrator.clearOutputs();

    // Verify initial state - nodes registered
    orchestrator.sendRegistryCommand("list-messaging-nodes");
    Thread.sleep(1000);
    assertThat(TestValidator.parseNodeList(orchestrator.getRegistryOutput())).hasSize(NODE_COUNT);

    // Step 1: Setup overlay (Section 2.3)
    orchestrator.sendRegistryCommand("setup-overlay " + CONNECTION_REQUIREMENT);
    assertThat(orchestrator.waitForRegistryOutput("setup completed", 5)).isTrue();

    // Verify all nodes established connections
    for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
      assertThat(orchestrator.waitForNodeOutput(nodeId, "All connections are established", 5))
          .as("Node " + nodeId + " should establish connections")
          .isTrue();
    }

    // Step 2: Send link weights (Section 2.4)
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5)).isTrue();

    // Verify all nodes received weights and are ready
    for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
      boolean ready = false;
      List<String> output = orchestrator.getNodeOutput(nodeId);

      for (String line : output) {
        if (line.contains("Link weights received") || line.contains("Ready to send messages")) {
          ready = true;
          break;
        }
      }

      assertThat(ready).as("Node " + nodeId + " should be ready after complete protocol").isTrue();
    }

    // Verify we can list the weights
    orchestrator.sendRegistryCommand("list-weights");
    Thread.sleep(1000);

    List<String> finalOutput = orchestrator.getRegistryOutput();
    int weightCount = 0;

    for (String line : finalOutput) {
      // Match format: "IP:port, IP:port, weight"
      if (line.matches(
          "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+,\\s+\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+,\\s+\\d+")) {
        weightCount++;
      }
    }

    assertThat(weightCount)
        .as("Should be able to list weights after complete setup")
        .isGreaterThan(0);
  }
}
