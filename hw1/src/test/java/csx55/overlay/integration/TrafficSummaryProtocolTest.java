package csx55.overlay.integration;

import static org.junit.jupiter.api.Assertions.*;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

/**
 * Integration test suite for traffic summary protocol validation. Tests sections 2.8 and 2.9 of the
 * protocol specification covering PULL_TRAFFIC_SUMMARY message timing, TRAFFIC_SUMMARY response
 * format, counter reset behavior, and registry table output formatting.
 *
 * <p>Validates the complete traffic collection and reporting lifecycle, ensuring proper timing (15
 * second delay), correct aggregation, and proper state reset after summary collection.
 */
@TestMethodOrder(OrderAnnotation.class)
public class TrafficSummaryProtocolTest {

  private TestOrchestrator orchestrator;
  private int registryPort;

  /**
   * Sets up the test environment before each test. Initializes the test orchestrator and starts a
   * registry on a random port.
   *
   * @throws Exception if setup fails
   */
  @BeforeEach
  void setup() throws Exception {
    orchestrator = new TestOrchestrator();
    registryPort = 9600 + (int) (Math.random() * 1000);
    orchestrator.startRegistry(registryPort);
  }

  /** Cleans up test resources after each test. Shuts down all nodes and the test orchestrator. */
  @AfterEach
  void cleanup() {
    orchestrator.shutdown();
  }

  /**
   * Tests PULL_TRAFFIC_SUMMARY timing after TASK_COMPLETE (Section 2.8). Verifies that the registry
   * waits approximately 15 seconds after receiving all TASK_COMPLETE messages before sending
   * PULL_TRAFFIC_SUMMARY to nodes.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(1)
  @DisplayName("Test PULL_TRAFFIC_SUMMARY timing after TASK_COMPLETE (Section 2.8)")
  @Timeout(value = 45, unit = TimeUnit.SECONDS)
  void testPullTrafficSummaryTiming() throws Exception {
    // Start 3 messaging nodes
    for (int e = 0; e < 3; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    // Clear output and start task
    orchestrator.clearOutputs();
    orchestrator.sendRegistryCommand("start 1");

    // Wait for rounds completed message
    assertTrue(
        orchestrator.waitForRegistryOutput("1 rounds completed", 10),
        "Should see rounds completed message");

    // Mark the time when rounds completed
    long roundsCompletedTime = System.currentTimeMillis();

    // Wait for traffic summary table to appear (should be after ~15 seconds)
    boolean foundSummary = false;
    long summaryTime = 0;

    for (int e = 0; e < 25; e++) {
      Thread.sleep(1000);
      List<String> output = orchestrator.getRegistryOutput();

      // Look for the sum line which indicates table was printed
      for (String line : output) {
        if (line.startsWith("sum ") && line.contains(" ")) {
          foundSummary = true;
          summaryTime = System.currentTimeMillis();
          break;
        }
      }

      if (foundSummary) break;
    }

    assertTrue(foundSummary, "Should see traffic summary table");

    // Verify timing - should be approximately 15 seconds after rounds completed
    long delay = (summaryTime - roundsCompletedTime) / 1000;
    assertTrue(
        delay >= 14 && delay <= 20,
        "PULL_TRAFFIC_SUMMARY should be sent ~15 seconds after TASK_COMPLETE (was "
            + delay
            + " seconds)");
  }

  /**
   * Tests TRAFFIC_SUMMARY message contains all required fields (Section 2.9). Verifies that each
   * node's TRAFFIC_SUMMARY response includes all required fields: messages sent, received, sum of
   * sent/received, and messages relayed.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(2)
  @DisplayName("Test TRAFFIC_SUMMARY message contains all required fields (Section 2.9)")
  @Timeout(value = 40, unit = TimeUnit.SECONDS)
  void testTrafficSummaryFields() throws Exception {
    // Start 3 nodes
    for (int e = 0; e < 3; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup and run messaging
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    orchestrator.sendRegistryCommand("start 2");

    // Wait for traffic summary
    Thread.sleep(25000);
    List<String> output = orchestrator.getRegistryOutput();

    // Pattern for traffic summary line: IP:port sent received sumSent sumReceived relayed
    Pattern summaryPattern =
        Pattern.compile(
            "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)\\s+"
                + // IP:port
                "(\\d+)\\s+"
                + // messages sent
                "(\\d+)\\s+"
                + // messages received
                "(-?\\d+\\.\\d+)\\s+"
                + // sum sent
                "(-?\\d+\\.\\d+)\\s+"
                + // sum received
                "(\\d+)" // messages relayed
            );

    int nodeCount = 0;
    int totalSent = 0;
    int totalReceived = 0;

    for (String line : output) {
      Matcher m = summaryPattern.matcher(line);
      if (m.matches()) {
        nodeCount++;

        // Verify all fields are present
        assertNotNull(m.group(1), "Should have IP:port");
        assertNotNull(m.group(2), "Should have messages sent count");
        assertNotNull(m.group(3), "Should have messages received count");
        assertNotNull(m.group(4), "Should have sum of sent messages");
        assertNotNull(m.group(5), "Should have sum of received messages");
        assertNotNull(m.group(6), "Should have messages relayed count");

        totalSent += Integer.parseInt(m.group(2));
        totalReceived += Integer.parseInt(m.group(3));
      }
    }

    assertEquals(3, nodeCount, "Should have traffic summary for all 3 nodes");
    assertEquals(totalSent, totalReceived, "Total sent should equal total received");

    // Each node sends 2 rounds * 5 messages = 10 messages
    assertEquals(30, totalSent, "Each of 3 nodes should send 10 messages (2 rounds * 5)");
  }

  /**
   * Tests counter reset after TRAFFIC_SUMMARY (Section 2.9). Verifies that nodes correctly reset
   * their traffic counters after sending TRAFFIC_SUMMARY responses, ensuring clean state for
   * subsequent tasks.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(3)
  @DisplayName("Test counter reset after TRAFFIC_SUMMARY (Section 2.9)")
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void testCounterResetAfterSummary() throws Exception {
    // Start 3 nodes
    for (int e = 0; e < 3; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    // First round
    orchestrator.clearOutputs();
    orchestrator.sendRegistryCommand("start 2");

    // Wait for first summary
    Thread.sleep(25000);
    List<String> firstOutput = orchestrator.getRegistryOutput();

    // Parse first summary
    TestValidator.TrafficSummaryValidation firstValidation =
        TestValidator.validateTrafficSummary(firstOutput);
    assertTrue(firstValidation.isValid(), "First traffic summary should be valid");

    // Second round - counters should have been reset
    orchestrator.clearOutputs();
    orchestrator.sendRegistryCommand("start 2");

    // Wait for second summary
    Thread.sleep(25000);
    List<String> secondOutput = orchestrator.getRegistryOutput();

    // Parse second summary
    TestValidator.TrafficSummaryValidation secondValidation =
        TestValidator.validateTrafficSummary(secondOutput);
    assertTrue(secondValidation.isValid(), "Second traffic summary should be valid");

    // Both rounds should have same message counts (same rounds, same nodes)
    assertEquals(
        firstValidation.expectedTotalSent,
        secondValidation.expectedTotalSent,
        "Both rounds should have same expected message count");
    assertEquals(
        firstValidation.actualTotalSent,
        secondValidation.actualTotalSent,
        "Both rounds should have same actual sent count");
    assertEquals(
        firstValidation.actualTotalReceived,
        secondValidation.actualTotalReceived,
        "Both rounds should have same actual received count");
  }

  /**
   * Tests Registry table format matches specification. Verifies that the registry's traffic summary
   * table follows the required format with node entries and a summary line showing totals.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(4)
  @DisplayName("Test Registry table format matches specification")
  @Timeout(value = 40, unit = TimeUnit.SECONDS)
  void testRegistryTableFormat() throws Exception {
    // Start 4 nodes for better table testing
    for (int e = 0; e < 4; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup and run
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    orchestrator.sendRegistryCommand("start 3");

    // Wait for summary
    Thread.sleep(25000);
    List<String> output = orchestrator.getRegistryOutput();

    // Find the traffic summary table
    boolean foundTable = false;
    boolean foundSumLine = false;
    int nodeLines = 0;

    Pattern nodeLinePattern =
        Pattern.compile(
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+\\s+\\d+\\s+\\d+\\s+-?\\d+\\.\\d+\\s+-?\\d+\\.\\d+\\s+\\d+");
    Pattern sumLinePattern =
        Pattern.compile("sum\\s+\\d+\\s+\\d+\\s+-?\\d+\\.\\d+\\s+-?\\d+\\.\\d+");

    for (String line : output) {
      if (nodeLinePattern.matcher(line).matches()) {
        foundTable = true;
        nodeLines++;
      } else if (sumLinePattern.matcher(line).matches()) {
        foundSumLine = true;
      }
    }

    assertTrue(foundTable, "Should find traffic summary table");
    assertEquals(4, nodeLines, "Should have one line per node");
    assertTrue(foundSumLine, "Should have sum line at end of table");
  }

  /**
   * Tests PULL_TRAFFIC_SUMMARY sent to all nodes. Verifies that the registry sends
   * PULL_TRAFFIC_SUMMARY to all registered nodes and receives responses from each, as evidenced by
   * the complete table.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(5)
  @DisplayName("Test PULL_TRAFFIC_SUMMARY sent to all nodes")
  @Timeout(value = 40, unit = TimeUnit.SECONDS)
  void testPullTrafficSummarySentToAllNodes() throws Exception {
    // Start 3 nodes
    for (int e = 0; e < 3; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    // Run task
    orchestrator.sendRegistryCommand("start 1");

    // Wait for traffic summary (indicates all nodes responded)
    Thread.sleep(25000);
    List<String> output = orchestrator.getRegistryOutput();

    // Count nodes in summary
    int nodeCount = 0;
    Pattern nodePattern =
        Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+\\s+\\d+\\s+\\d+");

    for (String line : output) {
      if (nodePattern.matcher(line).find()) {
        nodeCount++;
      }
    }

    assertEquals(
        3,
        nodeCount,
        "All 3 nodes should appear in traffic summary, indicating they all received PULL_TRAFFIC_SUMMARY");
  }

  /**
   * Tests traffic summary with minimal messages. Verifies that the traffic summary protocol works
   * correctly even with minimal message traffic (single round with small overlay).
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(6)
  @DisplayName("Test traffic summary with minimal messages")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testEmptyTrafficSummary() throws Exception {
    // Start 2 nodes
    for (int e = 0; e < 2; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay 1");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    // Start with 1 round (minimal messages)
    orchestrator.sendRegistryCommand("start 1");

    // Wait for traffic summary
    Thread.sleep(25000);
    List<String> output = orchestrator.getRegistryOutput();

    // Should get a summary table
    Pattern summaryPattern =
        Pattern.compile(
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+\\s+\\d+\\s+\\d+\\s+-?\\d+\\.\\d+\\s+-?\\d+\\.\\d+\\s+\\d+");

    int nodeCount = 0;
    int totalSent = 0;
    for (String line : output) {
      if (summaryPattern.matcher(line).matches()) {
        nodeCount++;
        String[] parts = line.split("\\s+");
        totalSent += Integer.parseInt(parts[1]);
      }
    }

    assertEquals(2, nodeCount, "Should have traffic summary for both nodes");
    assertEquals(10, totalSent, "Each of 2 nodes should send 5 messages (1 round * 5)");
  }
}
