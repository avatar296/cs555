package csx55.overlay.integration;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for sections 3.1 and 3.2 of the protocol specification:
 * - Section 3.1: Registry commands (list-messaging-nodes, list-weights, setup-overlay, send-overlay-link-weights, start)
 * - Section 3.2: Messaging node commands (print-mst, exit-overlay)
 */
@TestMethodOrder(OrderAnnotation.class)
public class OverlayCommandProtocolTest {
    
    private TestOrchestrator orchestrator;
    private int registryPort;
    
    @BeforeEach
    void setup() throws Exception {
        orchestrator = new TestOrchestrator();
        registryPort = 9700 + (int)(Math.random() * 300);
        orchestrator.startRegistry(registryPort);
        Thread.sleep(1000);
    }
    
    @AfterEach
    void cleanup() {
        orchestrator.shutdown();
    }
    
    // ==================== Section 3.1: Registry Commands ====================
    
    @Test
    @Order(1)
    @DisplayName("Test list-messaging-nodes with no nodes (Section 3.1)")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testListMessagingNodesEmpty() throws Exception {
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        
        // Should have no node entries when no nodes are registered
        Pattern nodePattern = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+$");
        int nodeCount = 0;
        for (String line : output) {
            if (nodePattern.matcher(line.trim()).matches()) {
                nodeCount++;
            }
        }
        
        assertEquals(0, nodeCount, "Should have no nodes listed when registry is empty");
    }
    
    @Test
    @Order(2)
    @DisplayName("Test list-messaging-nodes with multiple nodes (Section 3.1)")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testListMessagingNodesMultiple() throws Exception {
        // Start 5 nodes
        for (int e = 0; e < 5; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        
        // Validate IP:port format as specified in PDF example
        Pattern nodePattern = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+$");
        List<String> nodes = new ArrayList<>();
        
        for (String line : output) {
            if (nodePattern.matcher(line.trim()).matches()) {
                nodes.add(line.trim());
                
                // Verify port is in valid range
                String[] parts = line.split(":");
                int port = Integer.parseInt(parts[1]);
                assertTrue(port >= 1024 && port <= 65535, 
                    "Port should be in valid range: " + port);
            }
        }
        
        assertEquals(5, nodes.size(), "Should list all 5 registered nodes");
        
        // Verify all nodes are unique
        Set<String> uniqueNodes = new HashSet<>(nodes);
        assertEquals(nodes.size(), uniqueNodes.size(), "All nodes should be unique");
    }
    
    @Test
    @Order(3)
    @DisplayName("Test setup-overlay with valid CR (Section 3.1)")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testSetupOverlayValidCR() throws Exception {
        // Start 6 nodes
        for (int e = 0; e < 6; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        orchestrator.clearOutputs();
        int cr = 3;
        orchestrator.sendRegistryCommand("setup-overlay " + cr);
        Thread.sleep(5000);
        
        // Verify output format: "setup completed with <n> connections"
        List<String> output = orchestrator.getRegistryOutput();
        boolean foundCompletion = false;
        for (String line : output) {
            if (line.contains("setup completed with " + cr + " connections")) {
                foundCompletion = true;
                break;
            }
        }
        
        assertTrue(foundCompletion, 
            "Should output 'setup completed with " + cr + " connections'");
        
        // Verify all nodes established connections
        int nodesWithConnections = 0;
        for (int e = 0; e < 6; e++) {
            List<String> nodeOutput = orchestrator.getNodeOutput(e);
            for (String line : nodeOutput) {
                if (line.contains("All connections are established")) {
                    nodesWithConnections++;
                    break;
                }
            }
        }
        
        assertEquals(6, nodesWithConnections, 
            "All nodes should establish connections");
    }
    
    @Test
    @Order(4)
    @DisplayName("Test setup-overlay with CR >= N (Section 3.1)")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testSetupOverlayInvalidCR() throws Exception {
        // Start 3 nodes
        for (int e = 0; e < 3; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("setup-overlay 3"); // CR = N, should fail
        Thread.sleep(3000);
        
        // Should not complete successfully
        List<String> output = orchestrator.getRegistryOutput();
        boolean foundSuccess = false;
        boolean foundError = false;
        
        for (String line : output) {
            if (line.contains("setup completed")) {
                foundSuccess = true;
            }
            if (line.toLowerCase().contains("error") || 
                line.toLowerCase().contains("cannot") ||
                line.toLowerCase().contains("insufficient")) {
                foundError = true;
            }
        }
        
        assertFalse(foundSuccess, "Setup should not complete with CR >= N");
        
        // Nodes should not establish connections
        for (int e = 0; e < 3; e++) {
            boolean established = orchestrator.waitForNodeOutput(e, 
                "All connections are established", 2);
            assertFalse(established, 
                "Node " + e + " should not establish connections with invalid CR");
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("Test list-weights before overlay setup (Section 3.1)")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testListWeightsBeforeSetup() throws Exception {
        // Start nodes but don't setup overlay
        for (int e = 0; e < 3; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-weights");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        
        // Should have no weights listed
        Pattern weightPattern = Pattern.compile(
            "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d+$"
        );
        
        int weightCount = 0;
        for (String line : output) {
            if (weightPattern.matcher(line.trim()).matches()) {
                weightCount++;
            }
        }
        
        assertEquals(0, weightCount, 
            "Should have no weights before overlay is setup");
    }
    
    @Test
    @Order(6)
    @DisplayName("Test list-weights after overlay setup and weights assigned (Section 3.1)")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testListWeightsAfterSetup() throws Exception {
        // Start 4 nodes
        for (int e = 0; e < 4; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        // Setup overlay with CR=2
        orchestrator.sendRegistryCommand("setup-overlay 2");
        Thread.sleep(3000);
        
        // Send weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        Thread.sleep(2000);
        
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-weights");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getRegistryOutput();
        
        // Pattern: IP:port, IP:port, weight
        Pattern weightPattern = Pattern.compile(
            "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+), " +
            "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+), " +
            "(\\d+)$"
        );
        
        List<Integer> weights = new ArrayList<>();
        Set<String> links = new HashSet<>();
        
        for (String line : output) {
            Matcher matcher = weightPattern.matcher(line.trim());
            if (matcher.matches()) {
                String node1 = matcher.group(1);
                String node2 = matcher.group(2);
                int weight = Integer.parseInt(matcher.group(3));
                
                weights.add(weight);
                
                // Create normalized link identifier (smaller IP first)
                String link = node1.compareTo(node2) < 0 ? 
                    node1 + "-" + node2 : node2 + "-" + node1;
                links.add(link);
                
                // Verify weight is in range 1-10
                assertTrue(weight >= 1 && weight <= 10, 
                    "Weight should be between 1-10: " + weight);
            }
        }
        
        // Should have (N * CR) / 2 unique links
        int expectedLinks = (4 * 2) / 2; // 4 nodes, CR=2
        assertEquals(expectedLinks, links.size(), 
            "Should have correct number of unique links");
        
        assertFalse(weights.isEmpty(), "Should have weights listed");
    }
    
    @Test
    @Order(7)
    @DisplayName("Test send-overlay-link-weights before overlay setup (Section 3.1)")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testSendWeightsBeforeSetup() throws Exception {
        // Start nodes
        for (int e = 0; e < 3; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        Thread.sleep(2000);
        
        List<String> output = orchestrator.getRegistryOutput();
        
        // Should not succeed
        boolean weightsAssigned = false;
        for (String line : output) {
            if (line.equals("link weights assigned")) {
                weightsAssigned = true;
            }
        }
        
        assertFalse(weightsAssigned, 
            "Should not assign weights before overlay setup");
        
        // Nodes should not receive weights
        for (int e = 0; e < 3; e++) {
            boolean received = orchestrator.waitForNodeOutput(e, 
                "Link weights received", 2);
            assertFalse(received, 
                "Node " + e + " should not receive weights before overlay setup");
        }
    }
    
    @Test
    @Order(8)
    @DisplayName("Test send-overlay-link-weights after overlay setup (Section 3.1)")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testSendWeightsAfterSetup() throws Exception {
        // Start 5 nodes
        for (int e = 0; e < 5; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 2");
        assertTrue(orchestrator.waitForRegistryOutput("setup completed", 5),
            "Overlay should be setup first");
        
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        Thread.sleep(3000);
        
        // Verify registry output
        List<String> output = orchestrator.getRegistryOutput();
        boolean weightsAssigned = false;
        for (String line : output) {
            if (line.equals("link weights assigned")) {
                weightsAssigned = true;
                break;
            }
        }
        
        assertTrue(weightsAssigned, 
            "Should output 'link weights assigned'");
        
        // Verify all nodes received weights
        int nodesReceivedWeights = 0;
        for (int e = 0; e < 5; e++) {
            List<String> nodeOutput = orchestrator.getNodeOutput(e);
            for (String line : nodeOutput) {
                if (line.contains("Link weights received and processed") ||
                    line.contains("Ready to send messages")) {
                    nodesReceivedWeights++;
                    break;
                }
            }
        }
        
        assertEquals(5, nodesReceivedWeights, 
            "All nodes should receive and process weights");
    }
    
    @Test
    @Order(9)
    @DisplayName("Test start command with valid rounds (Section 3.1)")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testStartCommandValidRounds() throws Exception {
        // Start 3 nodes
        for (int e = 0; e < 3; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        // Setup overlay and weights
        orchestrator.sendRegistryCommand("setup-overlay 2");
        Thread.sleep(3000);
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        Thread.sleep(2000);
        
        orchestrator.clearOutputs();
        int rounds = 5;
        orchestrator.sendRegistryCommand("start " + rounds);
        
        // Wait for completion
        assertTrue(orchestrator.waitForRegistryOutput(rounds + " rounds completed", 15),
            "Should complete " + rounds + " rounds");
        
        // Verify exact output format
        List<String> output = orchestrator.getRegistryOutput();
        boolean foundExactMessage = false;
        for (String line : output) {
            if (line.equals(rounds + " rounds completed")) {
                foundExactMessage = true;
                break;
            }
        }
        
        assertTrue(foundExactMessage, 
            "Should output exactly '" + rounds + " rounds completed'");
    }
    
    @Test
    @Order(10)
    @DisplayName("Test start command with 0 rounds (Section 3.1)")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testStartCommandZeroRounds() throws Exception {
        // Start 2 nodes
        for (int e = 0; e < 2; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        // Setup overlay and weights
        orchestrator.sendRegistryCommand("setup-overlay 1");
        Thread.sleep(3000);
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        Thread.sleep(2000);
        
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("start 0");
        
        // Should handle 0 rounds (might complete immediately or show error)
        Thread.sleep(5000);
        
        List<String> output = orchestrator.getRegistryOutput();
        
        // Either completes with 0 rounds or shows an error
        boolean handled = false;
        for (String line : output) {
            if (line.contains("0 rounds") || 
                line.toLowerCase().contains("invalid") ||
                line.toLowerCase().contains("must be greater")) {
                handled = true;
                break;
            }
        }
        
        assertTrue(handled, "Should handle 0 rounds appropriately");
    }
    
    // ==================== Section 3.2: Messaging Node Commands ====================
    
    @Test
    @Order(11)
    @DisplayName("Test print-mst command format (Section 3.2)")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testPrintMSTFormat() throws Exception {
        // Start 5 nodes
        for (int e = 0; e < 5; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        // Setup overlay and weights
        orchestrator.sendRegistryCommand("setup-overlay 2");
        Thread.sleep(3000);
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        Thread.sleep(2000);
        
        // Clear and test print-mst on first node
        orchestrator.clearOutputs();
        orchestrator.sendNodeCommand(0, "print-mst");
        Thread.sleep(1000);
        
        List<String> output = orchestrator.getNodeOutput(0);
        
        // MST output should follow same format as list-weights
        Pattern mstPattern = Pattern.compile(
            "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+), " +
            "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+), " +
            "(\\d+)$"
        );
        
        int mstEdges = 0;
        Set<String> nodes = new HashSet<>();
        
        for (String line : output) {
            Matcher matcher = mstPattern.matcher(line.trim());
            if (matcher.matches()) {
                mstEdges++;
                nodes.add(matcher.group(1));
                nodes.add(matcher.group(2));
                
                // Verify weight is in valid range
                int weight = Integer.parseInt(matcher.group(3));
                assertTrue(weight >= 1 && weight <= 10,
                    "MST edge weight should be between 1-10: " + weight);
            }
        }
        
        // MST should have exactly N-1 edges
        assertEquals(4, mstEdges, "MST should have exactly N-1 edges (5 nodes -> 4 edges)");
        
        // MST should connect all nodes
        assertEquals(5, nodes.size(), "MST should include all 5 nodes");
    }
    
    @Test
    @Order(12)
    @DisplayName("Test print-mst consistency (Section 3.2)")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testPrintMSTConsistency() throws Exception {
        // Start 4 nodes
        for (int e = 0; e < 4; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        // Setup overlay and weights
        orchestrator.sendRegistryCommand("setup-overlay 2");
        Thread.sleep(3000);
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        Thread.sleep(2000);
        
        // Get MST from same node twice
        orchestrator.clearOutputs();
        orchestrator.sendNodeCommand(0, "print-mst");
        Thread.sleep(1000);
        List<String> firstMST = new ArrayList<>(orchestrator.getNodeOutput(0));
        
        orchestrator.clearOutputs();
        orchestrator.sendNodeCommand(0, "print-mst");
        Thread.sleep(1000);
        List<String> secondMST = new ArrayList<>(orchestrator.getNodeOutput(0));
        
        // Extract MST edges from both outputs
        Pattern mstPattern = Pattern.compile(
            "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d+$"
        );
        
        List<String> firstEdges = new ArrayList<>();
        List<String> secondEdges = new ArrayList<>();
        
        for (String line : firstMST) {
            if (mstPattern.matcher(line.trim()).matches()) {
                firstEdges.add(line.trim());
            }
        }
        
        for (String line : secondMST) {
            if (mstPattern.matcher(line.trim()).matches()) {
                secondEdges.add(line.trim());
            }
        }
        
        // MST should be consistent
        assertEquals(firstEdges.size(), secondEdges.size(),
            "MST should have same number of edges on repeated calls");
        
        // Sort for comparison (order might vary but edges should be same)
        Collections.sort(firstEdges);
        Collections.sort(secondEdges);
        
        assertEquals(firstEdges, secondEdges,
            "MST should be consistent across multiple calls");
    }
    
    @Test
    @Order(13)
    @DisplayName("Test exit-overlay command (Section 3.2)")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testExitOverlayCommand() throws Exception {
        // Start 3 nodes
        for (int e = 0; e < 3; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        // Get initial node count
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> initialNodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertEquals(3, initialNodes.size(), "Should have 3 nodes initially");
        
        // Exit one node
        orchestrator.clearOutputs();
        orchestrator.sendNodeCommand(1, "exit-overlay");
        Thread.sleep(2000);
        
        // Verify exit message
        List<String> nodeOutput = orchestrator.getNodeOutput(1);
        boolean foundExitMessage = false;
        for (String line : nodeOutput) {
            if (line.equals("exited overlay")) {
                foundExitMessage = true;
                break;
            }
        }
        
        assertTrue(foundExitMessage, 
            "Should output exactly 'exited overlay'");
        
        // Verify node is removed from registry
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> remainingNodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        
        assertEquals(2, remainingNodes.size(), 
            "Should have 2 nodes after one exits");
    }
    
    @Test
    @Order(14)
    @DisplayName("Test exit-overlay and re-registration (Section 3.2)")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testExitAndReRegister() throws Exception {
        // Start 2 nodes
        for (int e = 0; e < 2; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        // Get node address before exit
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> initialNodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        String firstNodeAddress = initialNodes.get(0);
        
        // Exit first node
        orchestrator.sendNodeCommand(0, "exit-overlay");
        assertTrue(orchestrator.waitForNodeOutput(0, "exited overlay", 5),
            "Node should exit overlay");
        
        // Verify only 1 node remains
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> afterExit = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertEquals(1, afterExit.size(), "Should have 1 node after exit");
        
        // Re-register a new node
        orchestrator.startMessagingNode("127.0.0.1", registryPort);
        Thread.sleep(2000);
        
        // Verify 2 nodes again
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> afterReRegister = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertEquals(2, afterReRegister.size(), 
            "Should have 2 nodes after re-registration");
    }
    
    @Test
    @Order(15)
    @DisplayName("Test complete command sequence (Sections 3.1 & 3.2)")
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void testCompleteCommandSequence() throws Exception {
        // Start 4 nodes
        for (int e = 0; e < 4; e++) {
            orchestrator.startMessagingNode("127.0.0.1", registryPort);
            Thread.sleep(500);
        }
        Thread.sleep(1000);
        
        // 1. List nodes
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertEquals(4, nodes.size(), "Should list 4 nodes");
        
        // 2. Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 2");
        assertTrue(orchestrator.waitForRegistryOutput("setup completed with 2 connections", 5),
            "Should setup overlay with CR=2");
        
        // 3. Send weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertTrue(orchestrator.waitForRegistryOutput("link weights assigned", 5),
            "Should assign link weights");
        
        // 4. List weights
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-weights");
        Thread.sleep(1000);
        List<String> weightOutput = orchestrator.getRegistryOutput();
        
        Pattern weightPattern = Pattern.compile(
            "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+, " +
            "\\d+$"
        );
        
        int weightCount = 0;
        for (String line : weightOutput) {
            if (weightPattern.matcher(line.trim()).matches()) {
                weightCount++;
            }
        }
        assertTrue(weightCount > 0, "Should list weights");
        
        // 5. Print MST from a node
        orchestrator.clearOutputs();
        orchestrator.sendNodeCommand(0, "print-mst");
        Thread.sleep(1000);
        List<String> mstOutput = orchestrator.getNodeOutput(0);
        
        int mstEdges = 0;
        for (String line : mstOutput) {
            if (weightPattern.matcher(line.trim()).matches()) {
                mstEdges++;
            }
        }
        assertEquals(3, mstEdges, "MST should have 3 edges for 4 nodes");
        
        // 6. Start messaging
        orchestrator.sendRegistryCommand("start 2");
        assertTrue(orchestrator.waitForRegistryOutput("2 rounds completed", 10),
            "Should complete 2 rounds");
        
        // 7. Exit one node
        Thread.sleep(20000); // Wait for traffic summary
        orchestrator.sendNodeCommand(3, "exit-overlay");
        assertTrue(orchestrator.waitForNodeOutput(3, "exited overlay", 5),
            "Node should exit overlay");
        
        // 8. Verify final state
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> finalNodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertEquals(3, finalNodes.size(), "Should have 3 nodes after one exits");
    }
}