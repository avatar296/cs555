package csx55.overlay.integration;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import csx55.overlay.testutil.TestValidator.TrafficSummaryValidation;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;

/**
 * End-to-end integration test suite for the overlay network.
 * Validates complete overlay functionality including node registration,
 * overlay setup, link weight distribution, message routing, and node deregistration.
 * 
 * Tests the entire workflow from startup to shutdown, ensuring all components
 * work together correctly as specified in the assignment PDF.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OverlayIntegrationTest {
    
    private static TestOrchestrator orchestrator;
    private static final int REGISTRY_PORT = 9090;
    private static final int NODE_COUNT = 10;
    private static final int CONNECTION_REQUIREMENT = 4;
    
    /**
     * Sets up the complete test system before all tests.
     * Starts the registry and multiple messaging nodes for integration testing.
     * 
     * @throws Exception if system setup fails
     */
    @BeforeAll
    static void setupSystem() throws Exception {
        orchestrator = new TestOrchestrator();
        
        // Start Registry
        orchestrator.startRegistry(REGISTRY_PORT);
        assertThat(orchestrator.waitForRegistryOutput("Registry listening on port", 5))
            .as("Registry should start successfully")
            .isTrue();
        
        // Start MessagingNodes
        for (int e = 0; e < NODE_COUNT; e++) {
            orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
            Thread.sleep(500); // Small delay between node starts
        }
        
        // Wait for all registrations
        Thread.sleep(2000);
    }
    
    /**
     * Tears down the test system after all tests complete.
     * Shuts down all nodes and the registry.
     */
    @AfterAll
    static void teardownSystem() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
    }
    
    /**
     * Tests node registration functionality.
     * Verifies that all started nodes successfully register with the registry.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(1)
    @DisplayName("Verify all nodes registered successfully")
    void testNodeRegistration() throws Exception {
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        List<String> nodes = TestValidator.parseNodeList(output);
        
        assertThat(nodes)
            .as("Should have all nodes registered")
            .hasSize(NODE_COUNT);
    }
    
    /**
     * Tests overlay setup with specified connection requirement.
     * Verifies that the overlay is established correctly and all nodes
     * successfully establish their required connections.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(2)
    @DisplayName("Setup overlay with specified connection requirement")
    void testOverlaySetup() throws Exception {
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("setup-overlay " + CONNECTION_REQUIREMENT);
        
        // Wait for setup to complete
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
            .as("Overlay setup should complete")
            .isTrue();
        
        // Verify all nodes established connections
        for (int e = 0; e < NODE_COUNT; e++) {
            assertThat(orchestrator.waitForNodeOutput(e, 
                "All connections are established", 10))
                .as("Node " + e + " should establish connections")
                .isTrue();
        }
        
        // Verify setup message format
        List<String> output = orchestrator.getRegistryOutput();
        boolean foundSetupMessage = false;
        for (String line : output) {
            if (TestValidator.validateSetupCompletion(line)) {
                foundSetupMessage = true;
                break;
            }
        }
        assertThat(foundSetupMessage)
            .as("Should output correct setup completion message")
            .isTrue();
    }
    
    /**
     * Tests link weight distribution and verification.
     * Validates that link weights are correctly assigned, distributed to all nodes,
     * and follow the expected format and count.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(3)
    @DisplayName("Send and verify link weights")
    void testLinkWeights() throws Exception {
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        
        // Wait for weights to be sent
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .as("Link weights should be assigned")
            .isTrue();
        
        // Verify all nodes received weights
        for (int e = 0; e < NODE_COUNT; e++) {
            assertThat(orchestrator.waitForNodeOutput(e, 
                "Link weights received and processed", 10))
                .as("Node " + e + " should receive link weights")
                .isTrue();
        }
        
        // Verify link weight format
        orchestrator.sendRegistryCommand("list-weights");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getLastRegistryOutput(50);
        int validWeights = 0;
        for (String line : output) {
            if (TestValidator.validateLinkWeightFormat(line)) {
                validWeights++;
            }
        }
        
        // Each node has CR connections, total edges = (nodes * CR) / 2
        int expectedEdges = (NODE_COUNT * CONNECTION_REQUIREMENT) / 2;
        assertThat(validWeights)
            .as("Should have correct number of link weights")
            .isEqualTo(expectedEdges);
    }
    
    /**
     * Tests messaging task execution and traffic verification.
     * Validates that nodes correctly send and receive messages, and that
     * traffic summaries show correct counts and summations.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(4)
    @DisplayName("Execute messaging task and verify traffic")
    void testMessagingTask() throws Exception {
        orchestrator.clearOutputs();
        int rounds = 10; // Small number for testing
        
        // Start messaging
        orchestrator.sendRegistryCommand("start " + rounds);
        
        // Wait for task completion
        assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 60))
            .as("Messaging task should complete")
            .isTrue();
        
        // Wait for traffic summaries (15 seconds delay + processing)
        Thread.sleep(20000);
        
        // Parse and validate traffic summary
        List<String> output = orchestrator.getLastRegistryOutput(50);
        TrafficSummaryValidation validation = TestValidator.validateTrafficSummary(output);
        
        assertThat(validation.isValid())
            .as("Traffic summary should be valid: " + validation)
            .isTrue();
        
        // Each node sends rounds * 5 messages
        int expectedMessagesPerNode = rounds * 5;
        int totalExpectedMessages = expectedMessagesPerNode * NODE_COUNT;
        
        assertThat(validation.actualTotalSent)
            .as("Total sent messages")
            .isEqualTo(totalExpectedMessages);
        
        assertThat(validation.actualTotalReceived)
            .as("Total received messages should match sent")
            .isEqualTo(totalExpectedMessages);
        
        // Verify summations match
        assertThat(validation.actualSumSent)
            .as("Sum of sent payloads")
            .isCloseTo(validation.expectedSumSent, within(0.01));
        
        assertThat(validation.actualSumReceived)
            .as("Sum of received payloads should match sent")
            .isCloseTo(validation.expectedSumReceived, within(0.01));
    }
    
    /**
     * Tests MST computation at individual nodes.
     * Verifies that nodes correctly compute the Minimum Spanning Tree
     * with exactly N-1 edges and valid properties.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(5)
    @DisplayName("Verify MST computation at each node")
    void testMSTComputation() throws Exception {
        // Test MST at first node
        orchestrator.clearOutputs();
        orchestrator.sendNodeCommand(0, "print-mst");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getNodeOutput(0);
        List<TestValidator.MSTEdge> mstEdges = TestValidator.parseMSTOutput(output);
        
        // MST should have exactly n-1 edges
        assertThat(mstEdges)
            .as("MST should have correct number of edges")
            .hasSize(NODE_COUNT - 1);
        
        // Verify MST properties
        assertThat(TestValidator.validateMSTProperties(mstEdges, NODE_COUNT))
            .as("MST should be valid")
            .isTrue();
    }
    
    /**
     * Tests node deregistration functionality.
     * Verifies that nodes can cleanly exit the overlay and that
     * the registry correctly updates its node list.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(6)
    @DisplayName("Test node deregistration")
    void testDeregistration() throws Exception {
        orchestrator.clearOutputs();
        
        // Deregister first node
        orchestrator.sendNodeCommand(0, "exit-overlay");
        
        // Verify deregistration message
        assertThat(orchestrator.waitForNodeOutput(0, "exited overlay", 5))
            .as("Node should exit overlay successfully")
            .isTrue();
        
        // Verify node count decreased
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        List<String> nodes = TestValidator.parseNodeList(output);
        
        assertThat(nodes)
            .as("Should have one less node after deregistration")
            .hasSize(NODE_COUNT - 1);
    }
}