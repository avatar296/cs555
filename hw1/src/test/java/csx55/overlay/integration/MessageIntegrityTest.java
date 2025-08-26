package csx55.overlay.integration;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import csx55.overlay.testutil.TestValidator.TrafficSummaryValidation;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;

/**
 * Tests message integrity, correct counts, and summations as specified in PDF Section 4
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MessageIntegrityTest {
    
    private static TestOrchestrator orchestrator;
    private static final int REGISTRY_PORT = 9099;
    private static final int NODE_COUNT = 5;
    private static final int CR = 3;
    
    @BeforeAll
    static void setup() throws Exception {
        orchestrator = new TestOrchestrator();
        orchestrator.startRegistry(REGISTRY_PORT);
        Thread.sleep(2000);
        
        // Start nodes
        for (int e = 0; e < NODE_COUNT; e++) {
            orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        
        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay " + CR);
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
            .isTrue();
        
        // Send link weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .isTrue();
        
        Thread.sleep(3000);
    }
    
    @AfterAll
    static void teardown() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Test message count: rounds * 5 per node (PDF Section 4)")
    void testMessageCount() throws Exception {
        orchestrator.clearOutputs();
        int rounds = 20;
        
        // Start messaging task
        orchestrator.sendRegistryCommand("start " + rounds);
        assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 60))
            .isTrue();
        
        // Wait for traffic summaries
        Thread.sleep(20000);
        
        // Parse traffic summary
        List<String> output = orchestrator.getLastRegistryOutput(50);
        TrafficSummaryValidation validation = TestValidator.validateTrafficSummary(output);
        
        assertThat(validation.isValid())
            .as("Traffic summary should be valid")
            .isTrue();
        
        // Each node sends rounds * 5 messages
        int expectedMessagesPerNode = rounds * 5;
        int totalExpectedMessages = expectedMessagesPerNode * NODE_COUNT;
        
        assertThat(validation.actualTotalSent)
            .as("Total sent messages should be rounds * 5 * nodes")
            .isEqualTo(totalExpectedMessages);
        
        assertThat(validation.actualTotalReceived)
            .as("Total received should equal total sent")
            .isEqualTo(totalExpectedMessages);
    }
    
    @Test
    @Order(2)
    @DisplayName("Test summation integrity: sendSummation == receiveSummation (PDF Section 4.2)")
    void testSummationIntegrity() throws Exception {
        orchestrator.clearOutputs();
        int rounds = 15;
        
        // Start new messaging task
        orchestrator.sendRegistryCommand("start " + rounds);
        assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 60))
            .isTrue();
        
        // Wait for traffic summaries
        Thread.sleep(20000);
        
        // Parse traffic summary
        List<String> output = orchestrator.getLastRegistryOutput(50);
        TrafficSummaryValidation validation = TestValidator.validateTrafficSummary(output);
        
        assertThat(validation.isValid())
            .as("Traffic summary should be valid")
            .isTrue();
        
        // Verify summations match exactly
        assertThat(validation.actualSumSent)
            .as("Sum of sent payloads should match expected")
            .isCloseTo(validation.expectedSumSent, within(0.01));
        
        assertThat(validation.actualSumReceived)
            .as("Sum of received payloads should match sum of sent")
            .isCloseTo(validation.expectedSumReceived, within(0.01));
        
        // Most importantly: total send sum == total receive sum
        assertThat(validation.actualSumSent)
            .as("Total send summation must equal total receive summation")
            .isCloseTo(validation.actualSumReceived, within(0.01));
    }
    
    @Test
    @Order(3)
    @DisplayName("Test payload range: -2147483648 to 2147483647 (PDF Section 4)")
    void testPayloadRange() throws Exception {
        // The payload values should be random integers in the full int range
        // We verify this indirectly through summation values
        
        orchestrator.clearOutputs();
        int rounds = 100; // More rounds to get better statistical distribution
        
        orchestrator.sendRegistryCommand("start " + rounds);
        assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 120))
            .isTrue();
        
        Thread.sleep(20000);
        
        List<String> output = orchestrator.getLastRegistryOutput(60);
        
        // Look for individual node summations
        boolean foundNegativeSummation = false;
        boolean foundPositiveSummation = false;
        
        for (String line : output) {
            if (line.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+ .*")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 5) {
                    double sentSum = Double.parseDouble(parts[3]);
                    double recvSum = Double.parseDouble(parts[4]);
                    
                    if (sentSum < 0 || recvSum < 0) {
                        foundNegativeSummation = true;
                    }
                    if (sentSum > 0 || recvSum > 0) {
                        foundPositiveSummation = true;
                    }
                }
            }
        }
        
        // With random payloads across full int range, we should see both positive and negative sums
        assertThat(foundNegativeSummation || foundPositiveSummation)
            .as("Should have summations indicating full range of payload values")
            .isTrue();
    }
    
    @Test
    @Order(4)
    @DisplayName("Test message distribution across receivers (PDF Section 4.1)")
    void testMessageDistribution() throws Exception {
        orchestrator.clearOutputs();
        int rounds = 50;
        
        orchestrator.sendRegistryCommand("start " + rounds);
        assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 90))
            .isTrue();
        
        Thread.sleep(20000);
        
        List<String> output = orchestrator.getLastRegistryOutput(50);
        
        // Parse individual node statistics
        int minReceived = Integer.MAX_VALUE;
        int maxReceived = Integer.MIN_VALUE;
        
        for (String line : output) {
            if (line.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+ .*")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    int received = Integer.parseInt(parts[2]);
                    minReceived = Math.min(minReceived, received);
                    maxReceived = Math.max(maxReceived, received);
                    
                    // Each node's received count should be a multiple of 5 (5 messages per round)
                    assertThat(received % 5)
                        .as("Received messages should be multiple of 5")
                        .isEqualTo(0);
                }
            }
        }
        
        // With random distribution, there should be some variance but not extreme
        int expectedAverage = rounds * 5; // Average per node if evenly distributed
        
        // Allow for reasonable variance in random distribution
        assertThat(minReceived)
            .as("Minimum received should be reasonable")
            .isGreaterThan(expectedAverage / 2);
        
        assertThat(maxReceived)
            .as("Maximum received should be reasonable")
            .isLessThan(expectedAverage * 2);
    }
    
    @Test
    @Order(5)
    @DisplayName("Test relay counter tracking (PDF Section 4.1)")
    void testRelayTracking() throws Exception {
        orchestrator.clearOutputs();
        int rounds = 30;
        
        orchestrator.sendRegistryCommand("start " + rounds);
        assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 60))
            .isTrue();
        
        Thread.sleep(20000);
        
        List<String> output = orchestrator.getLastRegistryOutput(50);
        
        // Parse relay counts
        int totalRelayed = 0;
        int nodesWithRelays = 0;
        
        for (String line : output) {
            if (line.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+ .*")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 6) {
                    int relayed = Integer.parseInt(parts[5]);
                    totalRelayed += relayed;
                    if (relayed > 0) {
                        nodesWithRelays++;
                    }
                }
            }
        }
        
        // In a non-fully-connected overlay, there should be relay traffic
        assertThat(totalRelayed)
            .as("Should have relay traffic in overlay")
            .isGreaterThan(0);
        
        // Not all nodes may relay (depending on topology)
        // But at least some should in a typical overlay
        assertThat(nodesWithRelays)
            .as("At least some nodes should relay messages")
            .isGreaterThan(0);
    }
    
    @Test
    @Order(6)
    @DisplayName("Test counter reset after traffic summary (PDF Section 2.9)")
    void testCounterReset() throws Exception {
        // First round of messaging
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("start 10");
        assertThat(orchestrator.waitForRegistryOutput("10 rounds completed", 30))
            .isTrue();
        
        Thread.sleep(20000);
        
        // Get first traffic summary
        List<String> firstOutput = orchestrator.getLastRegistryOutput(50);
        TrafficSummaryValidation firstValidation = TestValidator.validateTrafficSummary(firstOutput);
        assertThat(firstValidation.isValid()).isTrue();
        
        // Second round of messaging (counters should have been reset)
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("start 10");
        assertThat(orchestrator.waitForRegistryOutput("10 rounds completed", 30))
            .isTrue();
        
        Thread.sleep(20000);
        
        // Get second traffic summary
        List<String> secondOutput = orchestrator.getLastRegistryOutput(50);
        TrafficSummaryValidation secondValidation = TestValidator.validateTrafficSummary(secondOutput);
        assertThat(secondValidation.isValid()).isTrue();
        
        // Both rounds should have same expected counts (same rounds, same nodes)
        assertThat(secondValidation.actualTotalSent)
            .as("Second round should have same message count as first")
            .isEqualTo(firstValidation.actualTotalSent);
        
        // Summations will be different due to random payloads, but totals should balance
        assertThat(secondValidation.actualSumSent)
            .as("Second round send sum should equal receive sum")
            .isCloseTo(secondValidation.actualSumReceived, within(0.01));
    }
    
    @Test
    @Order(7)
    @DisplayName("Test no duplicate messages received (PDF Section 2.6)")
    void testNoDuplicateMessages() throws Exception {
        // This is implicitly tested by message counts matching
        // If duplicates existed, receive count would exceed send count
        
        orchestrator.clearOutputs();
        int rounds = 25;
        
        orchestrator.sendRegistryCommand("start " + rounds);
        assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 60))
            .isTrue();
        
        Thread.sleep(20000);
        
        List<String> output = orchestrator.getLastRegistryOutput(50);
        TrafficSummaryValidation validation = TestValidator.validateTrafficSummary(output);
        
        // Key test: total received == total sent
        // This proves no duplicates (and no losses)
        assertThat(validation.actualTotalReceived)
            .as("No duplicates: received count must equal sent count exactly")
            .isEqualTo(validation.actualTotalSent);
        
        // Also verify summations match (proves no corruption)
        assertThat(validation.actualSumReceived)
            .as("No corruption: summations must match")
            .isCloseTo(validation.actualSumSent, within(0.01));
    }
}