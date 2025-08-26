package csx55.overlay.integration;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tests that all command outputs match the exact format specified in the PDF
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CommandOutputTest {
    
    private static TestOrchestrator orchestrator;
    private static final int REGISTRY_PORT = 9092;
    private static final int NODE_COUNT = 5;
    
    @BeforeAll
    static void setup() throws Exception {
        orchestrator = new TestOrchestrator();
        orchestrator.startRegistry(REGISTRY_PORT);
        Thread.sleep(2000);
        
        // Start messaging nodes
        for (int e = 0; e < NODE_COUNT; e++) {
            orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
            Thread.sleep(500);
        }
        Thread.sleep(2000);
    }
    
    @AfterAll
    static void teardown() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Test list-messaging-nodes output format (PDF Section 3.1)")
    void testListMessagingNodesFormat() throws Exception {
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        
        // Pattern for IP:port format as shown in PDF example
        Pattern nodePattern = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+$");
        
        int validNodes = 0;
        for (String line : output) {
            if (nodePattern.matcher(line.trim()).matches()) {
                validNodes++;
            }
        }
        
        assertThat(validNodes)
            .as("Should have correct IP:port format for all nodes")
            .isEqualTo(NODE_COUNT);
    }
    
    @Test
    @Order(2)
    @DisplayName("Test setup-overlay output format (PDF Section 3.1)")
    void testSetupOverlayOutput() throws Exception {
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("setup-overlay 2");
        
        // Wait for setup to complete
        assertThat(orchestrator.waitForRegistryOutput("setup completed with", 10))
            .as("Should output setup completion message")
            .isTrue();
        
        // Verify exact format: "setup completed with <n> connections"
        List<String> output = orchestrator.getRegistryOutput();
        boolean foundCorrectFormat = false;
        for (String line : output) {
            if (line.matches(".*setup completed with \\d+ connections.*")) {
                foundCorrectFormat = true;
                // Verify it says "2 connections" for our CR=2
                assertThat(line).contains("setup completed with 2 connections");
                break;
            }
        }
        
        assertThat(foundCorrectFormat)
            .as("Should output 'setup completed with N connections'")
            .isTrue();
    }
    
    @Test
    @Order(3)
    @DisplayName("Test send-overlay-link-weights output (PDF Section 3.1)")
    void testSendOverlayLinkWeightsOutput() throws Exception {
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        
        // Wait for command to complete
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .as("Should output 'link weights assigned'")
            .isTrue();
        
        // Verify exact output format
        List<String> output = orchestrator.getRegistryOutput();
        boolean foundExactMessage = false;
        for (String line : output) {
            if (line.equals("link weights assigned")) {
                foundExactMessage = true;
                break;
            }
        }
        
        assertThat(foundExactMessage)
            .as("Should output exactly 'link weights assigned'")
            .isTrue();
    }
    
    @Test
    @Order(4)
    @DisplayName("Test list-weights output format (PDF Section 3.1)")
    void testListWeightsFormat() throws Exception {
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-weights");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        
        // Pattern: IP:port, IP:port, weight
        // Example: 192.168.0.10:8080, 192.168.1.25:443, 8
        Pattern weightPattern = Pattern.compile(
            "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d{1,2}$"
        );
        
        int validWeights = 0;
        for (String line : output) {
            if (weightPattern.matcher(line.trim()).matches()) {
                validWeights++;
                
                // Verify weight is between 1-10
                String[] parts = line.split(", ");
                if (parts.length == 3) {
                    int weight = Integer.parseInt(parts[2]);
                    assertThat(weight)
                        .as("Link weight should be between 1-10")
                        .isBetween(1, 10);
                }
            }
        }
        
        // Should have (NODE_COUNT * CR) / 2 edges
        int expectedEdges = (NODE_COUNT * 2) / 2; // CR=2 from previous test
        assertThat(validWeights)
            .as("Should have correct number of link weights")
            .isEqualTo(expectedEdges);
    }
    
    @Test
    @Order(5)
    @DisplayName("Test start command output (PDF Section 3.1)")
    void testStartCommandOutput() throws Exception {
        orchestrator.clearOutputs();
        int rounds = 10;
        orchestrator.sendRegistryCommand("start " + rounds);
        
        // Wait for task completion
        assertThat(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 30))
            .as("Should output 'N rounds completed'")
            .isTrue();
        
        // Verify exact format
        List<String> output = orchestrator.getRegistryOutput();
        boolean foundExactMessage = false;
        for (String line : output) {
            if (line.equals(rounds + " rounds completed")) {
                foundExactMessage = true;
                break;
            }
        }
        
        assertThat(foundExactMessage)
            .as("Should output exactly 'N rounds completed'")
            .isTrue();
    }
    
    @Test
    @Order(6)
    @DisplayName("Test traffic summary table format (PDF Section 4.3)")
    void testTrafficSummaryFormat() throws Exception {
        // Wait for traffic summaries to be collected
        Thread.sleep(20000);
        
        List<String> output = orchestrator.getLastRegistryOutput(50);
        
        // Pattern for traffic summary line:
        // IP:port sent received sentSum receivedSum relayed
        Pattern summaryLinePattern = Pattern.compile(
            "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+ " +
            "\\d+ \\d+ -?\\d+\\.\\d{2} -?\\d+\\.\\d{2} \\d+$"
        );
        
        int validSummaryLines = 0;
        boolean foundSumLine = false;
        
        for (String line : output) {
            if (summaryLinePattern.matcher(line.trim()).matches()) {
                validSummaryLines++;
            }
            // Check for sum line format: "sum sent received sentSum receivedSum"
            if (line.startsWith("sum ")) {
                foundSumLine = true;
                String[] parts = line.split("\\s+");
                assertThat(parts)
                    .as("Sum line should have 5 parts")
                    .hasSize(5);
                assertThat(parts[0]).isEqualTo("sum");
            }
        }
        
        assertThat(validSummaryLines)
            .as("Should have traffic summary for each node")
            .isEqualTo(NODE_COUNT);
        
        assertThat(foundSumLine)
            .as("Should have sum line at end of traffic summary")
            .isTrue();
    }
    
    @Test
    @Order(7)
    @DisplayName("Test print-mst output format (PDF Section 3.2)")
    void testPrintMSTFormat() throws Exception {
        orchestrator.clearOutputs();
        orchestrator.sendNodeCommand(0, "print-mst");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getNodeOutput(0);
        
        // MST output should follow same format as list-weights
        Pattern mstPattern = Pattern.compile(
            "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d{1,2}$"
        );
        
        int mstEdges = 0;
        for (String line : output) {
            if (mstPattern.matcher(line.trim()).matches()) {
                mstEdges++;
            }
        }
        
        // MST should have exactly N-1 edges
        assertThat(mstEdges)
            .as("MST should have N-1 edges")
            .isEqualTo(NODE_COUNT - 1);
    }
    
    @Test
    @Order(8)
    @DisplayName("Test exit-overlay output (PDF Section 3.2)")
    void testExitOverlayOutput() throws Exception {
        // Create a new node to test exit
        int newNodeId = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(2000);
        
        orchestrator.clearOutputs();
        orchestrator.sendNodeCommand(newNodeId, "exit-overlay");
        
        // Wait for exit message
        assertThat(orchestrator.waitForNodeOutput(newNodeId, "exited overlay", 5))
            .as("Should output 'exited overlay'")
            .isTrue();
        
        // Verify exact message
        List<String> output = orchestrator.getNodeOutput(newNodeId);
        boolean foundExactMessage = false;
        for (String line : output) {
            if (line.equals("exited overlay")) {
                foundExactMessage = true;
                break;
            }
        }
        
        assertThat(foundExactMessage)
            .as("Should output exactly 'exited overlay'")
            .isTrue();
    }
    
    @Test
    @Order(9)
    @DisplayName("Test node console messages during setup")
    void testNodeSetupMessages() throws Exception {
        // Restart with fresh nodes to test setup messages
        orchestrator.shutdown();
        orchestrator = new TestOrchestrator();
        orchestrator.startRegistry(REGISTRY_PORT);
        Thread.sleep(2000);
        
        // Start nodes and capture their output
        for (int e = 0; e < 3; e++) {
            orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        
        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 2");
        Thread.sleep(5000);
        
        // Check each node for connection establishment message
        for (int e = 0; e < 3; e++) {
            List<String> output = orchestrator.getNodeOutput(e);
            boolean foundConnectionMessage = false;
            
            for (String line : output) {
                // PDF specifies: "All connections are established. Number of connections: x"
                if (line.matches(".*All connections are established.*Number of connections: \\d+.*")) {
                    foundConnectionMessage = true;
                    break;
                }
            }
            
            assertThat(foundConnectionMessage)
                .as("Node " + e + " should output connection establishment message")
                .isTrue();
        }
        
        // Send link weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        Thread.sleep(5000);
        
        // Check each node for link weights received message
        for (int e = 0; e < 3; e++) {
            List<String> output = orchestrator.getNodeOutput(e);
            boolean foundWeightsMessage = false;
            
            for (String line : output) {
                // PDF specifies: "Link weights received and processed. Ready to send messages."
                if (line.contains("Link weights received and processed")) {
                    foundWeightsMessage = true;
                    break;
                }
            }
            
            assertThat(foundWeightsMessage)
                .as("Node " + e + " should output link weights received message")
                .isTrue();
        }
    }
}