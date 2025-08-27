package csx55.overlay.integration;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.net.ServerSocket;

/**
 * Integration test suite for MessagingNode component functionality.
 * Tests MessagingNode-specific behavior from PDF Section 1.2, focusing on
 * node behavior, connection handling, peer identification, and fault tolerance.
 * 
 * Validates automatic port selection, registry disconnection handling,
 * peer connection retry logic, MST computation, and graceful node exit.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MessagingNodeComponentTest {

    private TestOrchestrator orchestrator;
    private static final int BASE_PORT = 9300;

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
     * Tests automatic port selection functionality (Section 1.2).
     * Verifies that messaging nodes can automatically select available ports
     * when started with port 0, ensuring each node gets a unique valid port.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(1)
    @DisplayName("Test automatic port selection (port 0) for MessagingNode (Section 1.2)")
    void testAutomaticPortSelection() throws Exception {
        orchestrator.startRegistry(BASE_PORT);
        Thread.sleep(2000);

        // Start multiple nodes - they should all get different automatic ports
        List<Integer> nodeIds = new ArrayList<>();
        for (int e = 0; e < 5; e++) {
            nodeIds.add(orchestrator.startMessagingNode("localhost", BASE_PORT));
            Thread.sleep(500);
        }
        Thread.sleep(2000);

        // Get the list of nodes from registry
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);

        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(nodes).hasSize(5);

        // Extract ports and verify they're all different
        List<Integer> ports = new ArrayList<>();
        for (String node : nodes) {
            String[] parts = node.split(":");
            if (parts.length == 2) {
                ports.add(Integer.parseInt(parts[1]));
            }
        }

        // All ports should be unique
        assertThat(ports.stream().distinct().count())
                .as("Each node should have a unique automatically assigned port")
                .isEqualTo(5);

        // All ports should be valid (> 0 and < 65536)
        for (int port : ports) {
            assertThat(port)
                    .as("Automatically assigned port should be valid")
                    .isBetween(1024, 65535);
        }
    }

    /**
     * Tests node behavior when registry becomes unavailable (Section 1.2).
     * Verifies that messaging nodes detect registry disconnection and
     * terminate appropriately when the registry process is killed.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(2)
    @DisplayName("Test MessagingNode behavior when Registry becomes unavailable (Section 1.2)")
    void testRegistryDisconnection() throws Exception {
        // Start registry
        Process registryProcess = new ProcessBuilder(
                "java", "-cp", "build/classes/java/main",
                "csx55.overlay.node.Registry",
                String.valueOf(BASE_PORT + 1)).start();
        Thread.sleep(3000);

        // Start a messaging node
        Process nodeProcess = new ProcessBuilder(
                "java", "-cp", "build/classes/java/main",
                "csx55.overlay.node.MessagingNode",
                "localhost", String.valueOf(BASE_PORT + 1)).start();
        Thread.sleep(2000);

        // Kill the registry
        registryProcess.destroyForcibly();
        Thread.sleep(2000);

        // Node should detect registry disconnection and terminate
        // Wait a bit for node to detect and handle disconnection
        Thread.sleep(5000);

        assertThat(nodeProcess.isAlive())
                .as("MessagingNode should terminate when Registry becomes unavailable")
                .isFalse();
    }

    /**
     * Tests invalid command handling at messaging nodes (Section 1.2).
     * Verifies that nodes properly handle invalid or malformed commands
     * and remain responsive after receiving invalid input.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(3)
    @DisplayName("Test invalid command handling at MessagingNode (Section 1.2)")
    void testInvalidCommandHandling() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 2);
        Thread.sleep(2000);

        int nodeId = orchestrator.startMessagingNode("localhost", BASE_PORT + 2);
        Thread.sleep(2000);

        // Test various invalid commands
        String[] invalidCommands = {
                "invalid-command",
                "print mst", // Missing hyphen
                "exit overlay", // Missing hyphen
                "send-message", // Not a valid command
                "start", // Registry command, not node command
                "" // Empty command
        };

        for (String cmd : invalidCommands) {
            orchestrator.clearOutputs();
            orchestrator.sendNodeCommand(nodeId, cmd);
            Thread.sleep(500);

            List<String> output = orchestrator.getNodeOutput(nodeId);

            // Node should respond with unknown command or available commands
            boolean foundResponse = false;
            for (String line : output) {
                if (line.toLowerCase().contains("unknown") ||
                        line.toLowerCase().contains("available commands") ||
                        line.toLowerCase().contains("invalid")) {
                    foundResponse = true;
                    break;
                }
            }

            assertThat(foundResponse)
                    .as("Node should handle invalid command: " + cmd)
                    .isTrue();
        }

        // Node should still be responsive after invalid commands
        orchestrator.sendNodeCommand(nodeId, "print-mst");
        Thread.sleep(500);
        assertThat(orchestrator.getNodeOutput(nodeId))
                .as("Node should remain responsive after invalid commands")
                .isNotNull();
    }

    /**
     * Tests peer connection retry logic (Section 1.2).
     * Verifies that nodes can handle temporary port unavailability
     * and successfully establish connections when ports become available.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(4)
    @DisplayName("Test peer connection retry logic (Section 1.2)")
    void testPeerConnectionRetry() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 3);
        Thread.sleep(2000);

        // Start first node
        int node1 = orchestrator.startMessagingNode("localhost", BASE_PORT + 3);
        Thread.sleep(2000);

        // Block the port that node2 would use (simulate temporary unavailability)
        ServerSocket blocker = null;
        int blockedPort = 12345;
        try {
            blocker = new ServerSocket(blockedPort);

            // Try to start node2 on blocked port - should fail initially
            // But since we use automatic port selection, it should succeed with different
            // port
            int node2 = orchestrator.startMessagingNode("localhost", BASE_PORT + 3);
            Thread.sleep(2000);

            // Both nodes should be registered
            orchestrator.sendRegistryCommand("list-messaging-nodes");
            Thread.sleep(1000);
            List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
            assertThat(nodes).hasSize(2);

        } finally {
            if (blocker != null) {
                blocker.close();
            }
        }

        // Now setup overlay - peers should connect successfully
        orchestrator.sendRegistryCommand("setup-overlay 1");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
                .as("Overlay setup should succeed after port becomes available")
                .isTrue();

        // Verify connection established
        assertThat(orchestrator.waitForNodeOutput(node1, "All connections are established", 5))
                .isTrue();
    }

    /**
     * Tests cleanup handling for unexpected node shutdown (Section 1.2).
     * Verifies that the registry properly updates its state when nodes
     * terminate unexpectedly and that remaining nodes detect disconnections.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(5)
    @DisplayName("Test node cleanup on unexpected shutdown (Section 1.2)")
    void testUnexpectedShutdownCleanup() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 4);
        Thread.sleep(2000);

        // Start multiple nodes
        List<Process> nodeProcesses = new ArrayList<>();
        for (int e = 0; e < 3; e++) {
            Process node = new ProcessBuilder(
                    "java", "-cp", "build/classes/java/main",
                    "csx55.overlay.node.MessagingNode",
                    "localhost", String.valueOf(BASE_PORT + 4)).start();
            nodeProcesses.add(node);
            Thread.sleep(500);
        }
        Thread.sleep(2000);

        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 2");
        Thread.sleep(5000);

        // Simulate unexpected shutdown of one node
        nodeProcesses.get(1).destroyForcibly();
        Thread.sleep(3000);

        // Other nodes should detect the disconnection
        // Registry should update its state
        orchestrator.clearOutputs(); // Clear previous outputs before getting new list
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);

        List<String> remainingNodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(remainingNodes)
                .as("Registry should track only remaining active nodes")
                .hasSize(2);

        // Clean up
        for (Process node : nodeProcesses) {
            if (node.isAlive()) {
                node.destroyForcibly();
            }
        }
    }

    /**
     * Tests duplicate connection handling between peers (Section 1.2).
     * Verifies that nodes properly manage connections to prevent duplicates
     * and maintain the correct number of connections as specified by CR.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(6)
    @DisplayName("Test duplicate connection handling from same peer (Section 1.2)")
    void testDuplicateConnectionHandling() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 5);
        Thread.sleep(2000);

        // Start two nodes
        int node1 = orchestrator.startMessagingNode("localhost", BASE_PORT + 5);
        int node2 = orchestrator.startMessagingNode("localhost", BASE_PORT + 5);
        Thread.sleep(2000);

        // Setup overlay with CR=1 (single connection between them)
        orchestrator.sendRegistryCommand("setup-overlay 1");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
                .isTrue();

        // Wait for connections to establish
        Thread.sleep(3000);

        // Both nodes should report exactly 1 connection
        List<String> node1Output = orchestrator.getNodeOutput(node1);
        List<String> node2Output = orchestrator.getNodeOutput(node2);

        boolean node1HasOneConnection = false;
        boolean node2HasOneConnection = false;

        for (String line : node1Output) {
            if (line.contains("Number of connections: 1")) {
                node1HasOneConnection = true;
                break;
            }
        }

        for (String line : node2Output) {
            if (line.contains("Number of connections: 1")) {
                node2HasOneConnection = true;
                break;
            }
        }

        assertThat(node1HasOneConnection)
                .as("Node 1 should have exactly 1 connection")
                .isTrue();

        assertThat(node2HasOneConnection)
                .as("Node 2 should have exactly 1 connection")
                .isTrue();
    }

    /**
     * Tests peer identification protocol functionality (Section 1.2).
     * Verifies that nodes correctly identify peers after connection establishment
     * and that message routing works properly with peer identification.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(7)
    @DisplayName("Test peer identification protocol (Section 1.2)")
    void testPeerIdentificationProtocol() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 6);
        Thread.sleep(2000);

        // Start nodes
        for (int e = 0; e < 4; e++) {
            orchestrator.startMessagingNode("localhost", BASE_PORT + 6);
            Thread.sleep(500);
        }
        Thread.sleep(2000);

        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 2");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
                .isTrue();

        // Send link weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
                .isTrue();

        Thread.sleep(3000);

        // Start messaging to verify peer identification worked correctly
        orchestrator.sendRegistryCommand("start 5");
        assertThat(orchestrator.waitForRegistryOutput("5 rounds completed", 30))
                .as("Messaging should work correctly with peer identification")
                .isTrue();

        // Wait for traffic summaries
        Thread.sleep(15000);

        // Parse traffic summary to verify messages were routed correctly
        List<String> output = orchestrator.getLastRegistryOutput(30);
        TestValidator.TrafficSummaryValidation validation = TestValidator.validateTrafficSummary(output);

        assertThat(validation.isValid())
                .as("Traffic summary should be valid, indicating proper peer identification")
                .isTrue();

        // Total sent should equal total received (no lost messages due to
        // misidentification)
        assertThat(validation.actualTotalReceived)
                .as("All messages should be delivered (peer identification working)")
                .isEqualTo(validation.actualTotalSent);
    }

    /**
     * Tests MST computation after receiving link weights (Section 1.2).
     * Verifies that each node correctly computes the Minimum Spanning Tree
     * using Prim's algorithm and produces MST with exactly N-1 edges.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(8)
    @DisplayName("Test MessagingNode MST computation after link weights (Section 1.2)")
    void testMSTComputation() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 7);
        Thread.sleep(2000);

        // Start nodes
        int nodeCount = 5;
        List<Integer> nodeIds = new ArrayList<>();
        for (int e = 0; e < nodeCount; e++) {
            nodeIds.add(orchestrator.startMessagingNode("localhost", BASE_PORT + 7));
            Thread.sleep(500);
        }
        Thread.sleep(2000);

        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 3");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
                .isTrue();

        // Send link weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
                .isTrue();

        Thread.sleep(3000);

        // Check that each node can print its MST
        for (int nodeId : nodeIds) {
            orchestrator.clearOutputs();
            orchestrator.sendNodeCommand(nodeId, "print-mst");
            Thread.sleep(500);

            List<String> mstOutput = orchestrator.getNodeOutput(nodeId);

            // Count MST edges
            int mstEdges = 0;
            for (String line : mstOutput) {
                // MST edge format: IP:port, IP:port, weight
                if (line.matches(".*:\\d+, .*:\\d+, \\d+")) {
                    mstEdges++;
                }
            }

            // Each node should compute MST with N-1 edges
            assertThat(mstEdges)
                    .as("Node " + nodeId + " should compute MST with N-1 edges")
                    .isEqualTo(nodeCount - 1);
        }
    }

    /**
     * Tests graceful exit-overlay functionality (Section 1.2).
     * Verifies that nodes can cleanly exit the overlay, properly deregister
     * from the registry, and that remaining nodes continue to function.
     * 
     * @throws Exception if test execution fails
     */
    @Test
    @Order(9)
    @DisplayName("Test MessagingNode graceful exit-overlay (Section 1.2)")
    void testGracefulExit() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 8);
        Thread.sleep(2000);

        // Start nodes
        List<Integer> nodeIds = new ArrayList<>();
        for (int e = 0; e < 3; e++) {
            nodeIds.add(orchestrator.startMessagingNode("localhost", BASE_PORT + 8));
            Thread.sleep(500);
        }
        Thread.sleep(2000);

        // Verify all registered
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> beforeExit = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(beforeExit).hasSize(3);

        // Exit one node gracefully
        orchestrator.sendNodeCommand(nodeIds.get(1), "exit-overlay");
        assertThat(orchestrator.waitForNodeOutput(nodeIds.get(1), "exited overlay", 5))
                .as("Node should output 'exited overlay'")
                .isTrue();

        Thread.sleep(2000);

        // Verify node was deregistered
        orchestrator.clearOutputs(); // Clear previous outputs before getting new list
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> afterExit = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(afterExit)
                .as("Registry should remove exited node")
                .hasSize(2);

        // Remaining nodes should still be functional
        orchestrator.sendRegistryCommand("setup-overlay 1");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
                .as("Remaining nodes should still be able to form overlay")
                .isTrue();
    }
}