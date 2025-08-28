package csx55.overlay.integration;

import static org.assertj.core.api.Assertions.*;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import csx55.overlay.testutil.TestValidator.TrafficSummaryValidation;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;

/**
 * Integration test suite for correctness verification and output validation. Comprehensive tests
 * for PDF Sections 4.2 (Correctness Verification) and 4.3 (Collecting and printing outputs).
 *
 * <p>Section 4.2: Verifies message counts and summations match exactly across all nodes, ensuring
 * no message corruption or loss during routing.
 *
 * <p>Section 4.3: Validates traffic summary table format, timing requirements, and counter reset
 * functionality after each messaging round.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CorrectnessVerificationTest {

  private TestOrchestrator orchestrator;
  private static final int REGISTRY_PORT = 9500;

  /**
   * Initializes the test orchestrator for managing test nodes.
   *
   * @throws Exception if initialization fails
   */
  @BeforeAll
  void setup() throws Exception {
    orchestrator = new TestOrchestrator();
  }

  /** Cleans up test resources and shuts down all nodes. */
  @AfterAll
  void teardown() {
    if (orchestrator != null) {
      orchestrator.shutdown();
    }
  }

  /** Clears output buffers before each test to ensure clean test runs. */
  @BeforeEach
  void clearOutputs() {
    if (orchestrator != null) {
      orchestrator.clearOutputs();
    }
  }

  /**
   * Tests cumulative sum verification across all nodes (Section 4.2). Verifies that the sum of all
   * sendTracker values equals the sum of all receiveTracker values, ensuring message integrity
   * during routing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(1)
  @DisplayName("Test cumulative sum verification (Section 4.2)")
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void testCumulativeSumVerification() throws Exception {
    orchestrator.startRegistry(REGISTRY_PORT);
    Thread.sleep(2000);

    int nodeCount = 6;
    for (int e = 0; e < nodeCount; e++) {
      orchestrator.startMessagingNode("127.0.0.1", REGISTRY_PORT);
      Thread.sleep(500);
    }
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("setup-overlay 3");
    assertThat(orchestrator.waitForRegistryOutput("setup completed", 10)).isTrue();

    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5)).isTrue();
    Thread.sleep(2000);

    // Run messaging task
    int rounds = 25;
    orchestrator.sendRegistryCommand("start " + rounds);
    assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 60)).isTrue();

    Thread.sleep(20000);

    List<String> output = orchestrator.getLastRegistryOutput(50);

    // Parse individual node statistics
    int totalSent = 0;
    int totalReceived = 0;
    double totalSentSum = 0.0;
    double totalReceivedSum = 0.0;

    for (String line : output) {
      if (line.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+ .*")) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 5) {
          totalSent += Integer.parseInt(parts[1]);
          totalReceived += Integer.parseInt(parts[2]);
          totalSentSum += Double.parseDouble(parts[3]);
          totalReceivedSum += Double.parseDouble(parts[4]);
        }
      }
    }

    // Section 4.2 requirement: cumulative sums must match exactly
    assertThat(totalSent)
        .as("Cumulative sum of sendTracker must equal cumulative sum of receiveTracker")
        .isEqualTo(totalReceived);

    assertThat(totalSentSum)
        .as("Cumulative sum of sendSummation must equal cumulative sum of receiveSummation")
        .isCloseTo(totalReceivedSum, within(0.01));

    // Verify against sum line
    for (String line : output) {
      if (line.startsWith("sum ")) {
        String[] parts = line.split("\\s+");
        assertThat(Integer.parseInt(parts[1]))
            .as("Sum line sent count should match calculated total")
            .isEqualTo(totalSent);
        assertThat(Integer.parseInt(parts[2]))
            .as("Sum line received count should match calculated total")
            .isEqualTo(totalReceived);
      }
    }

    orchestrator.shutdown();
    orchestrator = new TestOrchestrator();
  }

  /**
   * Tests message corruption detection capabilities (Section 4.2). Runs multiple messaging rounds
   * and verifies that no corruption occurs by checking that sent and received sums match exactly.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(2)
  @DisplayName("Test message corruption detection (Section 4.2)")
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void testCorruptionDetection() throws Exception {
    orchestrator.startRegistry(REGISTRY_PORT + 1);
    Thread.sleep(2000);

    int nodeCount = 5;
    for (int e = 0; e < nodeCount; e++) {
      orchestrator.startMessagingNode("127.0.0.1", REGISTRY_PORT + 1);
      Thread.sleep(500);
    }
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("setup-overlay 2");
    assertThat(orchestrator.waitForRegistryOutput("setup completed", 10)).isTrue();

    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5)).isTrue();
    Thread.sleep(2000);

    // Run multiple rounds to verify no corruption
    for (int rounds : new int[] {10, 20, 30}) {
      orchestrator.clearOutputs();
      orchestrator.sendRegistryCommand("start " + rounds);
      assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 90)).isTrue();

      Thread.sleep(20000);

      List<String> output = orchestrator.getLastRegistryOutput(50);
      TrafficSummaryValidation validation = TestValidator.validateTrafficSummary(output);

      // No corruption means sums match exactly
      assertThat(validation.actualSumSent)
          .as("No corruption: send sum must equal receive sum for " + rounds + " rounds")
          .isCloseTo(validation.actualSumReceived, within(0.01));
    }

    orchestrator.shutdown();
    orchestrator = new TestOrchestrator();
  }

  /**
   * Tests exact table format with space separation (Section 4.3). Validates that traffic summary
   * output follows the specified format: "IP:port sent received sentSum.00 receivedSum.00 relayed"
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(3)
  @DisplayName("Test exact table output format (Section 4.3)")
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void testExactTableFormat() throws Exception {
    orchestrator.startRegistry(REGISTRY_PORT + 2);
    Thread.sleep(2000);

    int nodeCount = 4;
    for (int e = 0; e < nodeCount; e++) {
      orchestrator.startMessagingNode("127.0.0.1", REGISTRY_PORT + 2);
      Thread.sleep(500);
    }
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("setup-overlay 2");
    assertThat(orchestrator.waitForRegistryOutput("setup completed", 10)).isTrue();

    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5)).isTrue();
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("start 15");
    assertThat(orchestrator.waitForRegistryOutput("15 rounds completed", 45)).isTrue();

    Thread.sleep(20000);

    List<String> output = orchestrator.getLastRegistryOutput(50);

    // Verify exact format: IP:port sent received sentSum.00 receivedSum.00 relayed
    Pattern exactPattern =
        Pattern.compile(
            "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)\\s+"
                + // IP:port
                "(\\d+)\\s+"
                + // sent count
                "(\\d+)\\s+"
                + // received count
                "(-?\\d+\\.\\d{2})\\s+"
                + // sent sum with .00
                "(-?\\d+\\.\\d{2})\\s+"
                + // received sum with .00
                "(\\d+)$" // relayed count
            );

    int validLines = 0;
    for (String line : output) {
      Matcher m = exactPattern.matcher(line.trim());
      if (m.matches()) {
        validLines++;

        // Verify decimal format
        String sentSum = m.group(4);
        String recvSum = m.group(5);
        assertThat(sentSum).as("Sent sum must have .00 format").matches(".*\\.\\d{2}$");
        assertThat(recvSum).as("Received sum must have .00 format").matches(".*\\.\\d{2}$");
      }
    }

    assertThat(validLines).as("Should have exactly one summary line per node").isEqualTo(nodeCount);

    // Verify sum line format
    Pattern sumPattern =
        Pattern.compile("^sum\\s+(\\d+)\\s+(\\d+)\\s+(-?\\d+\\.\\d{2})\\s+(-?\\d+\\.\\d{2})$");
    boolean foundSumLine = false;

    for (String line : output) {
      if (line.startsWith("sum ")) {
        foundSumLine = true;
        Matcher m = sumPattern.matcher(line.trim());
        assertThat(m.matches()).as("Sum line must match exact format").isTrue();
      }
    }

    assertThat(foundSumLine).as("Must have sum line at end of table").isTrue();

    orchestrator.shutdown();
    orchestrator = new TestOrchestrator();
  }

  /**
   * Tests TASK_COMPLETE message from all nodes requirement (Section 4.3). Verifies that all nodes
   * send TASK_COMPLETE messages and appear in the traffic summary, ensuring proper task completion
   * reporting.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(4)
  @DisplayName("Test TASK_COMPLETE from all nodes (Section 4.3)")
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void testTaskCompleteFromAllNodes() throws Exception {
    orchestrator.startRegistry(REGISTRY_PORT + 3);
    Thread.sleep(2000);

    int nodeCount = 5;
    for (int e = 0; e < nodeCount; e++) {
      orchestrator.startMessagingNode("127.0.0.1", REGISTRY_PORT + 3);
      Thread.sleep(500);
    }
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("setup-overlay 2");
    assertThat(orchestrator.waitForRegistryOutput("setup completed", 10)).isTrue();

    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5)).isTrue();
    Thread.sleep(2000);

    // Start task
    orchestrator.sendRegistryCommand("start 10");

    // Monitor for task completion and PULL_TRAFFIC_SUMMARY timing
    long startTime = System.currentTimeMillis();
    assertThat(orchestrator.waitForRegistryOutput("10 rounds completed", 30)).isTrue();
    long completeTime = System.currentTimeMillis();

    // Wait for traffic summaries - should take ~15 seconds after TASK_COMPLETE
    Thread.sleep(20000);

    List<String> output = orchestrator.getLastRegistryOutput(60);

    // Verify all nodes reported in summary (confirms all sent TASK_COMPLETE)
    int nodesSummaryCount = 0;
    for (String line : output) {
      if (line.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+ .*")) {
        nodesSummaryCount++;
      }
    }

    assertThat(nodesSummaryCount)
        .as("All nodes must send TASK_COMPLETE and appear in summary")
        .isEqualTo(nodeCount);

    orchestrator.shutdown();
    orchestrator = new TestOrchestrator();
  }

  /**
   * Tests sum line calculation accuracy (Section 4.3). Verifies that the sum line at the end of the
   * traffic summary table correctly aggregates all individual node statistics.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(5)
  @DisplayName("Test sum line calculation accuracy (Section 4.3)")
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void testSumLineCalculation() throws Exception {
    orchestrator.startRegistry(REGISTRY_PORT + 4);
    Thread.sleep(2000);

    int nodeCount = 7;
    for (int e = 0; e < nodeCount; e++) {
      orchestrator.startMessagingNode("127.0.0.1", REGISTRY_PORT + 4);
      Thread.sleep(500);
    }
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("setup-overlay 3");
    assertThat(orchestrator.waitForRegistryOutput("setup completed", 10)).isTrue();

    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5)).isTrue();
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("start 20");
    assertThat(orchestrator.waitForRegistryOutput("20 rounds completed", 60)).isTrue();

    Thread.sleep(20000);

    List<String> output = orchestrator.getLastRegistryOutput(60);

    // Calculate expected sums
    int calcSent = 0, calcReceived = 0;
    double calcSentSum = 0.0, calcReceivedSum = 0.0;

    for (String line : output) {
      if (line.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+ .*")) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 5) {
          calcSent += Integer.parseInt(parts[1]);
          calcReceived += Integer.parseInt(parts[2]);
          calcSentSum += Double.parseDouble(parts[3]);
          calcReceivedSum += Double.parseDouble(parts[4]);
        }
      }
    }

    // Find and verify sum line
    for (String line : output) {
      if (line.startsWith("sum ")) {
        String[] parts = line.split("\\s+");
        assertThat(parts.length).isEqualTo(5);

        int sumSent = Integer.parseInt(parts[1]);
        int sumReceived = Integer.parseInt(parts[2]);
        double sumSentSum = Double.parseDouble(parts[3]);
        double sumReceivedSum = Double.parseDouble(parts[4]);

        assertThat(sumSent).as("Sum line sent count must match calculated sum").isEqualTo(calcSent);
        assertThat(sumReceived)
            .as("Sum line received count must match calculated sum")
            .isEqualTo(calcReceived);
        assertThat(sumSentSum)
            .as("Sum line sent summation must match calculated sum")
            .isCloseTo(calcSentSum, within(0.01));
        assertThat(sumReceivedSum)
            .as("Sum line received summation must match calculated sum")
            .isCloseTo(calcReceivedSum, within(0.01));
      }
    }

    orchestrator.shutdown();
    orchestrator = new TestOrchestrator();
  }

  /**
   * Tests table format with negative summations (Section 4.3). Validates that the table correctly
   * formats negative values when random payloads result in negative cumulative sums.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(6)
  @DisplayName("Test table format with negative summations (Section 4.3)")
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void testTableWithNegativeSummations() throws Exception {
    orchestrator.startRegistry(REGISTRY_PORT + 5);
    Thread.sleep(2000);

    int nodeCount = 6;
    for (int e = 0; e < nodeCount; e++) {
      orchestrator.startMessagingNode("127.0.0.1", REGISTRY_PORT + 5);
      Thread.sleep(500);
    }
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("setup-overlay 3");
    assertThat(orchestrator.waitForRegistryOutput("setup completed", 10)).isTrue();

    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5)).isTrue();
    Thread.sleep(2000);

    // Run many rounds to likely get negative summations
    orchestrator.sendRegistryCommand("start 100");
    assertThat(orchestrator.waitForRegistryOutput("100 rounds completed", 150)).isTrue();

    Thread.sleep(25000);

    List<String> output = orchestrator.getLastRegistryOutput(60);

    // Pattern that accepts negative numbers with decimal format
    Pattern negativePattern =
        Pattern.compile(
            "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+\\s+"
                + "\\d+\\s+\\d+\\s+"
                + "(-?\\d+\\.\\d{2})\\s+"
                + // Can be negative
                "(-?\\d+\\.\\d{2})\\s+"
                + // Can be negative
                "\\d+$");

    boolean foundNegative = false;
    for (String line : output) {
      Matcher m = negativePattern.matcher(line.trim());
      if (m.matches()) {
        String sentSum = m.group(1);
        String recvSum = m.group(2);
        if (sentSum.startsWith("-") || recvSum.startsWith("-")) {
          foundNegative = true;
          // Verify format even for negative numbers
          assertThat(sentSum).as("Negative sum must have .00 format").matches("-?\\d+\\.\\d{2}");
          assertThat(recvSum).as("Negative sum must have .00 format").matches("-?\\d+\\.\\d{2}");
        }
      }
    }

    // With 100 rounds and random payloads, we should see negative sums
    assertThat(foundNegative)
        .as("Should have at least some negative summations with random payloads")
        .isTrue();

    orchestrator.shutdown();
    orchestrator = new TestOrchestrator();
  }

  /**
   * Tests verification with minimum node count edge case (Section 4.2). Validates that correctness
   * verification works properly even with the minimum viable overlay configuration (3 nodes, CR=2).
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(7)
  @DisplayName("Test verification with minimum node count (Section 4.2)")
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  void testVerificationMinimumNodes() throws Exception {
    orchestrator.startRegistry(REGISTRY_PORT + 6);
    Thread.sleep(2000);

    // Test with minimum viable overlay (3 nodes, CR=2)
    int nodeCount = 3;
    for (int e = 0; e < nodeCount; e++) {
      orchestrator.startMessagingNode("127.0.0.1", REGISTRY_PORT + 6);
      Thread.sleep(500);
    }
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("setup-overlay 2");
    assertThat(orchestrator.waitForRegistryOutput("setup completed", 10)).isTrue();

    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5)).isTrue();
    Thread.sleep(2000);

    orchestrator.sendRegistryCommand("start 50");
    assertThat(orchestrator.waitForRegistryOutput("50 rounds completed", 90)).isTrue();

    Thread.sleep(20000);

    List<String> output = orchestrator.getLastRegistryOutput(50);
    TrafficSummaryValidation validation = TestValidator.validateTrafficSummary(output);

    // Even with minimum nodes, verification must work
    assertThat(validation.isValid())
        .as("Verification should work with minimum node count")
        .isTrue();

    assertThat(validation.actualTotalSent)
        .as("Total sent with 3 nodes")
        .isEqualTo(50 * 5 * nodeCount);

    assertThat(validation.actualTotalSent)
        .as("Sent must equal received even with minimum nodes")
        .isEqualTo(validation.actualTotalReceived);

    orchestrator.shutdown();
    orchestrator = new TestOrchestrator();
  }
}
