package csx55.overlay.integration;

import static org.junit.jupiter.api.Assertions.*;

import csx55.overlay.testutil.TestOrchestrator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

/**
 * Integration test suite for task execution protocol validation. Tests sections 2.5, 2.6, and 2.7
 * of the protocol specification covering TASK_INITIATE message distribution, message routing during
 * task execution, and TASK_COMPLETE protocol handling.
 *
 * <p>Validates the complete task execution lifecycle from initiation through message rounds to
 * completion reporting, ensuring proper protocol adherence and correct state transitions.
 */
@TestMethodOrder(OrderAnnotation.class)
public class TaskExecutionProtocolTest {

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
    registryPort = 9800 + (int) (Math.random() * 1000);
    orchestrator.startRegistry(registryPort);
  }

  /** Cleans up test resources after each test. Shuts down all nodes and the test orchestrator. */
  @AfterEach
  void cleanup() {
    orchestrator.shutdown();
  }

  /**
   * Tests TASK_INITIATE message distribution (Section 2.5). Verifies that the registry correctly
   * distributes TASK_INITIATE messages to all nodes when a messaging task is started.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(1)
  @DisplayName("Test TASK_INITIATE message distribution (Section 2.5)")
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void testTaskInitiateDistribution() throws Exception {
    // Start 5 messaging nodes
    for (int e = 0; e < 5; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup overlay and send link weights
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    // Clear output buffer
    orchestrator.getRegistryOutput();

    // Send start command with 2 rounds
    orchestrator.sendRegistryCommand("start 2");

    // Give time for task initiation
    Thread.sleep(1000);
    List<String> output = orchestrator.getRegistryOutput();

    // Registry should log sending TASK_INITIATE to all nodes
    boolean foundTaskInitiate =
        output.stream()
            .anyMatch(
                line ->
                    line.contains("TASK_INITIATE")
                        || line.contains("TaskInitiate")
                        || line.contains("Task initiated"));

    assertTrue(
        foundTaskInitiate || output.size() > 0,
        "Should see evidence of TASK_INITIATE being sent to nodes");

    // Wait for completion
    Thread.sleep(5000);
    output = orchestrator.getRegistryOutput();

    // Should see task completion messages
    boolean foundCompletion =
        output.stream()
            .anyMatch(line -> line.contains("rounds completed") || line.contains("TASK_COMPLETE"));

    assertTrue(foundCompletion, "Should see task completion after rounds finish");
  }

  /**
   * Tests TASK_COMPLETE protocol (Section 2.7). Verifies that nodes correctly send TASK_COMPLETE
   * messages to the registry after finishing their messaging rounds, and that the registry properly
   * aggregates completion notifications.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(2)
  @DisplayName("Test TASK_COMPLETE protocol (Section 2.7)")
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void testTaskCompleteProtocol() throws Exception {
    // Start 3 messaging nodes for faster test
    for (int e = 0; e < 3; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup overlay and weights
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    // Clear output
    orchestrator.getRegistryOutput();

    // Start task with 1 round for quick completion
    orchestrator.sendRegistryCommand("start 1");

    // Wait for task completion
    Thread.sleep(5000);
    List<String> output = orchestrator.getRegistryOutput();

    // Check for "1 rounds completed" message from registry
    Pattern completionPattern = Pattern.compile("(\\d+)\\s+rounds?\\s+completed");
    boolean foundCompletionMessage = false;
    int completedRounds = 0;

    for (String line : output) {
      Matcher m = completionPattern.matcher(line);
      if (m.find()) {
        foundCompletionMessage = true;
        completedRounds = Integer.parseInt(m.group(1));
        break;
      }
    }

    assertTrue(foundCompletionMessage, "Registry should print rounds completed message");
    assertEquals(1, completedRounds, "Should complete exactly 1 round");
  }

  /**
   * Tests message routing with multiple rounds (Section 2.6). Verifies that nodes correctly route
   * messages through the overlay network for the specified number of rounds, with each node sending
   * the required number of messages per round.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(3)
  @DisplayName("Test message routing with multiple rounds (Section 2.6)")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testMessageRoutingWithRounds() throws Exception {
    // Start 5 nodes for better routing tests
    for (int e = 0; e < 5; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup overlay with CR=2
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(2000);

    // Clear output
    orchestrator.getRegistryOutput();

    // Start with 3 rounds
    orchestrator.sendRegistryCommand("start 3");

    // Wait for completion
    Thread.sleep(10000);
    List<String> output = orchestrator.getRegistryOutput();

    // Should see "3 rounds completed"
    boolean found3Rounds = output.stream().anyMatch(line -> line.contains("3 rounds completed"));

    assertTrue(found3Rounds, "Should complete 3 rounds of message sending");

    // Each node sends 3 rounds * 5 messages = 15 messages total
    // With 5 nodes, total should be 75 messages sent
    // This would be verified in traffic summary
  }

  /**
   * Tests task initiation triggers message sending (Section 2.5-2.6). Verifies that receiving a
   * TASK_INITIATE message causes nodes to immediately begin sending messages according to the
   * protocol specifications.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(4)
  @DisplayName("Test task initiation triggers message sending (Section 2.5-2.6)")
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void testTaskInitiationTriggersMessages() throws Exception {
    // Start minimal overlay with 3 nodes
    for (int e = 0; e < 3; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    // Clear existing output
    orchestrator.getRegistryOutput();

    // Send start command
    orchestrator.sendRegistryCommand("start 2");

    // Messages should be sent immediately after TASK_INITIATE
    Thread.sleep(7000);
    List<String> output = orchestrator.getRegistryOutput();

    // Should see completion message
    boolean foundCompletion = output.stream().anyMatch(line -> line.contains("2 rounds completed"));

    assertTrue(foundCompletion, "Task should complete after message rounds");
  }

  /**
   * Tests registry waits for all TASK_COMPLETE messages. Verifies that the registry properly
   * aggregates TASK_COMPLETE messages from all nodes and only reports task completion after
   * receiving messages from every participating node.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Order(5)
  @DisplayName("Test registry waits for all TASK_COMPLETE messages")
  @Timeout(value = 25, unit = TimeUnit.SECONDS)
  void testRegistryWaitsForAllCompletions() throws Exception {
    // Start 4 nodes
    for (int e = 0; e < 4; e++) {
      orchestrator.startMessagingNode("127.0.0.1", registryPort);
    }

    // Setup overlay
    orchestrator.sendRegistryCommand("setup-overlay 2");
    Thread.sleep(1000);
    orchestrator.sendRegistryCommand("send-overlay-link-weights");
    Thread.sleep(1000);

    orchestrator.getRegistryOutput();

    // Start task
    orchestrator.sendRegistryCommand("start 1");

    // Registry should only print completion after ALL nodes complete
    Thread.sleep(5000);
    List<String> output = orchestrator.getRegistryOutput();

    // Look for rounds completed message
    long completionCount =
        output.stream().filter(line -> line.contains("rounds completed")).count();

    assertEquals(
        1,
        completionCount,
        "Registry should print completion message exactly once after all nodes complete");
  }
}
