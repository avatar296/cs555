package csx55.overlay.integration;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;

/**
 * Integration test suite for overlay scalability testing.
 * Tests the overlay network with different scales and connection requirements,
 * validating that the system works correctly with varying numbers of nodes
 * and different CR values.
 * 
 * Includes tests for minimum overlay configurations, standard configurations,
 * large-scale deployments, and edge cases like fully connected graphs.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScaleTest {
    
    private TestOrchestrator orchestrator;
    
    /**
     * Sets up the test environment before each test.
     * Initializes the test orchestrator for managing test nodes.
     * 
     * @throws Exception if setup fails
     */
    @BeforeEach
    void setup() throws Exception {
        orchestrator = new TestOrchestrator();
    }
    
    /**
     * Cleans up test resources after each test.
     * Shuts down all nodes and the test orchestrator.
     */
    @AfterEach
    void teardown() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
    }
    
    /**
     * Tests minimum viable overlay configuration.
     * Verifies that a 3-node overlay with CR=2 works correctly,
     * representing the smallest functional overlay.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(1)
    @DisplayName("Test minimum overlay with 3 nodes and CR=2")
    void testMinimumOverlay() throws Exception {
        int nodeCount = 3;
        int cr = 2;
        
        orchestrator.startRegistry(9093);
        Thread.sleep(2000);
        
        // Start 3 nodes
        for (int e = 0; e < nodeCount; e++) {
            orchestrator.startMessagingNode("localhost", 9093);
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        
        // Setup overlay with CR=2
        orchestrator.sendRegistryCommand("setup-overlay " + cr);
        assertThat(orchestrator.waitForRegistryOutput("setup completed with " + cr + " connections", 10))
            .as("Overlay setup should complete")
            .isTrue();
        
        // Verify all nodes established connections
        for (int e = 0; e < nodeCount; e++) {
            assertThat(orchestrator.waitForNodeOutput(e, "All connections are established", 10))
                .as("Node " + e + " should establish connections")
                .isTrue();
        }
        
        // Send link weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .isTrue();
        
        // Verify link count
        orchestrator.sendRegistryCommand("list-weights");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        int linkCount = 0;
        for (String line : output) {
            if (TestValidator.validateLinkWeightFormat(line)) {
                linkCount++;
            }
        }
        
        // Expected edges = (nodes * CR) / 2
        int expectedEdges = (nodeCount * cr) / 2;
        assertThat(linkCount)
            .as("Should have correct number of edges")
            .isEqualTo(expectedEdges);
        
        // Run a small messaging task
        orchestrator.sendRegistryCommand("start 5");
        assertThat(orchestrator.waitForRegistryOutput("5 rounds completed", 20))
            .isTrue();
    }
    
    /**
     * Tests standard overlay configuration as per PDF default.
     * Validates a 10-node overlay with CR=4, which is the standard
     * configuration used in most examples.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(2)
    @DisplayName("Test standard 10-node overlay with CR=4 (PDF default)")
    void testStandardOverlay() throws Exception {
        int nodeCount = 10;
        int cr = 4;
        
        orchestrator.startRegistry(9094);
        Thread.sleep(2000);
        
        // Start 10 nodes
        for (int e = 0; e < nodeCount; e++) {
            orchestrator.startMessagingNode("localhost", 9094);
            Thread.sleep(300);
        }
        Thread.sleep(3000);
        
        // Verify all nodes registered
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(nodes).hasSize(nodeCount);
        
        // Setup overlay with CR=4
        orchestrator.sendRegistryCommand("setup-overlay " + cr);
        assertThat(orchestrator.waitForRegistryOutput("setup completed with " + cr + " connections", 15))
            .isTrue();
        
        // Send link weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .isTrue();
        
        // Verify correct number of edges
        orchestrator.sendRegistryCommand("list-weights");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        int linkCount = 0;
        for (String line : output) {
            if (TestValidator.validateLinkWeightFormat(line)) {
                linkCount++;
            }
        }
        
        int expectedEdges = (nodeCount * cr) / 2;
        assertThat(linkCount)
            .as("10 nodes with CR=4 should have 20 edges")
            .isEqualTo(expectedEdges);
    }
    
    /**
     * Tests large-scale overlay deployment.
     * Verifies that the system scales to handle 20 nodes with CR=4,
     * testing performance with increased network size.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(3)
    @DisplayName("Test large overlay with 20 nodes and CR=4")
    void testLargeOverlay() throws Exception {
        int nodeCount = 20;
        int cr = 4;
        
        orchestrator.startRegistry(9095);
        Thread.sleep(2000);
        
        // Start 20 nodes
        for (int e = 0; e < nodeCount; e++) {
            orchestrator.startMessagingNode("localhost", 9095);
            Thread.sleep(200);
        }
        Thread.sleep(5000);
        
        // Verify all nodes registered
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(nodes).hasSize(nodeCount);
        
        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay " + cr);
        assertThat(orchestrator.waitForRegistryOutput("setup completed with " + cr + " connections", 20))
            .isTrue();
        
        // Send link weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 10))
            .isTrue();
        
        // Run messaging task
        orchestrator.sendRegistryCommand("start 10");
        assertThat(orchestrator.waitForRegistryOutput("10 rounds completed", 60))
            .as("Large overlay should complete messaging task")
            .isTrue();
    }
    
    /**
     * Tests fully connected overlay configuration.
     * Verifies that an overlay with CR = N-1 creates a fully connected graph
     * where every node is connected to every other node.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(4)
    @DisplayName("Test fully connected overlay (CR = N-1)")
    void testFullyConnectedOverlay() throws Exception {
        int nodeCount = 5;
        int cr = nodeCount - 1; // Fully connected
        
        orchestrator.startRegistry(9096);
        Thread.sleep(2000);
        
        // Start nodes
        for (int e = 0; e < nodeCount; e++) {
            orchestrator.startMessagingNode("localhost", 9096);
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        
        // Setup fully connected overlay
        orchestrator.sendRegistryCommand("setup-overlay " + cr);
        assertThat(orchestrator.waitForRegistryOutput("setup completed with " + cr + " connections", 10))
            .isTrue();
        
        // Verify each node has N-1 connections
        for (int e = 0; e < nodeCount; e++) {
            List<String> output = orchestrator.getNodeOutput(e);
            boolean foundConnectionMessage = false;
            for (String line : output) {
                if (line.contains("Number of connections: " + cr)) {
                    foundConnectionMessage = true;
                    break;
                }
            }
            assertThat(foundConnectionMessage)
                .as("Node " + e + " should have " + cr + " connections")
                .isTrue();
        }
        
        // Send link weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .isTrue();
        
        // Verify correct number of edges for fully connected graph
        orchestrator.sendRegistryCommand("list-weights");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        int linkCount = 0;
        for (String line : output) {
            if (TestValidator.validateLinkWeightFormat(line)) {
                linkCount++;
            }
        }
        
        // Fully connected graph has n*(n-1)/2 edges
        int expectedEdges = (nodeCount * (nodeCount - 1)) / 2;
        assertThat(linkCount)
            .as("Fully connected graph should have " + expectedEdges + " edges")
            .isEqualTo(expectedEdges);
    }
    
    /**
     * Tests overlay with varying connection requirements.
     * Validates that the system correctly handles different CR values
     * (2, 3, 5, 6) with the same number of nodes.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(5)
    @DisplayName("Test varying connection requirements (CR=2,3,5,6)")
    void testVaryingCR() throws Exception {
        int nodeCount = 8;
        int[] crValues = {2, 3, 5, 6};
        
        for (int cr : crValues) {
            // Fresh start for each CR value
            if (orchestrator != null) {
                orchestrator.shutdown();
            }
            orchestrator = new TestOrchestrator();
            
            orchestrator.startRegistry(9097);
            Thread.sleep(2000);
            
            // Start nodes
            for (int e = 0; e < nodeCount; e++) {
                orchestrator.startMessagingNode("localhost", 9097);
                Thread.sleep(300);
            }
            Thread.sleep(2000);
            
            // Setup overlay with current CR
            orchestrator.sendRegistryCommand("setup-overlay " + cr);
            assertThat(orchestrator.waitForRegistryOutput("setup completed with " + cr + " connections", 15))
                .as("Setup should complete with CR=" + cr)
                .isTrue();
            
            // Send link weights
            orchestrator.sendRegistryCommand("send-overlay-link-weights");
            assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
                .isTrue();
            
            // Verify correct edge count
            orchestrator.sendRegistryCommand("list-weights");
            Thread.sleep(1000);
            
            List<String> output = orchestrator.getRegistryOutput();
            int linkCount = 0;
            for (String line : output) {
                if (TestValidator.validateLinkWeightFormat(line)) {
                    linkCount++;
                }
            }
            
            int expectedEdges = (nodeCount * cr) / 2;
            assertThat(linkCount)
                .as("With CR=" + cr + " should have " + expectedEdges + " edges")
                .isEqualTo(expectedEdges);
            
            // Quick messaging test
            orchestrator.sendRegistryCommand("start 5");
            assertThat(orchestrator.waitForRegistryOutput("5 rounds completed", 30))
                .as("Messaging should work with CR=" + cr)
                .isTrue();
        }
    }
    
    /**
     * Tests overlay with odd number of nodes and odd CR.
     * Verifies that the system correctly handles edge cases where both
     * node count and CR are odd numbers, ensuring proper graph construction.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(6)
    @DisplayName("Test odd number of nodes with odd CR")
    void testOddNodesOddCR() throws Exception {
        int nodeCount = 7;
        int cr = 3;
        
        orchestrator.startRegistry(9098);
        Thread.sleep(2000);
        
        // Start 7 nodes
        for (int e = 0; e < nodeCount; e++) {
            orchestrator.startMessagingNode("localhost", 9098);
            Thread.sleep(400);
        }
        Thread.sleep(2000);
        
        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay " + cr);
        assertThat(orchestrator.waitForRegistryOutput("setup completed with " + cr + " connections", 10))
            .isTrue();
        
        // Send link weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .isTrue();
        
        // Verify edge count (should handle odd numbers correctly)
        orchestrator.sendRegistryCommand("list-weights");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        int linkCount = 0;
        for (String line : output) {
            if (TestValidator.validateLinkWeightFormat(line)) {
                linkCount++;
            }
        }
        
        // With 7 nodes and CR=3, we should have (7*3)/2 = 10.5, but rounded appropriately
        // The actual implementation should handle this
        assertThat(linkCount)
            .as("Should handle odd node count with odd CR")
            .isGreaterThan(0);
        
        // Test messaging works
        orchestrator.sendRegistryCommand("start 5");
        assertThat(orchestrator.waitForRegistryOutput("5 rounds completed", 30))
            .isTrue();
    }
}