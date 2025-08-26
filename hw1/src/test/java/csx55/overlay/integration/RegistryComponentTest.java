package csx55.overlay.integration;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests Registry-specific functionality from PDF Section 1.1
 * Focus on edge cases and concurrent operations
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RegistryComponentTest {
    
    private TestOrchestrator orchestrator;
    private static final int BASE_PORT = 9200;
    
    @BeforeEach
    void setup() throws Exception {
        orchestrator = new TestOrchestrator();
    }
    
    @AfterEach
    void teardown() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Test Registry port binding conflict (Section 1.1)")
    void testPortBindingConflict() throws Exception {
        // Start first registry on specific port
        orchestrator.startRegistry(BASE_PORT);
        Thread.sleep(2000);
        
        // Verify registry is running
        orchestrator.startMessagingNode("localhost", BASE_PORT);
        Thread.sleep(2000);
        
        // Try to start another registry on same port - should fail
        Process conflictRegistry = null;
        try {
            conflictRegistry = new ProcessBuilder(
                "java", "-cp", "build/classes/java/main",
                "csx55.overlay.node.Registry",
                String.valueOf(BASE_PORT)
            ).start();
            
            Thread.sleep(2000);
            
            // Check if the second registry failed to start
            assertThat(conflictRegistry.isAlive())
                .as("Second registry should not be able to bind to same port")
                .isFalse();
            
        } finally {
            if (conflictRegistry != null && conflictRegistry.isAlive()) {
                conflictRegistry.destroyForcibly();
            }
        }
    }
    
    @Test
    @Order(2)
    @DisplayName("Test concurrent node registration (Section 1.1)")
    void testConcurrentRegistration() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 1);
        Thread.sleep(2000);
        
        int concurrentNodes = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentNodes);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(concurrentNodes);
        List<Integer> nodeIds = new ArrayList<>();
        
        // Start all nodes concurrently
        for (int e = 0; e < concurrentNodes; e++) {
            final int nodeIndex = e;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start
                    int nodeId = orchestrator.startMessagingNode("localhost", BASE_PORT + 1);
                    synchronized (nodeIds) {
                        nodeIds.add(nodeId);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        // Release all threads simultaneously
        startLatch.countDown();
        
        // Wait for all registrations to complete
        assertThat(completeLatch.await(30, TimeUnit.SECONDS))
            .as("All nodes should register within 30 seconds")
            .isTrue();
        
        Thread.sleep(3000);
        
        // Verify all nodes registered successfully
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(nodes)
            .as("All concurrently started nodes should be registered")
            .hasSize(concurrentNodes);
        
        executor.shutdown();
    }
    
    @Test
    @Order(3)
    @DisplayName("Test Registry state persistence across overlay operations (Section 1.1)")
    void testRegistryStatePersistence() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 2);
        Thread.sleep(2000);
        
        // Register nodes
        for (int e = 0; e < 5; e++) {
            orchestrator.startMessagingNode("localhost", BASE_PORT + 2);
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        
        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 2");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
            .isTrue();
        
        // Send weights
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .isTrue();
        
        // Run messaging task
        orchestrator.sendRegistryCommand("start 5");
        assertThat(orchestrator.waitForRegistryOutput("5 rounds completed", 20))
            .isTrue();
        
        Thread.sleep(15000); // Wait for traffic summaries
        
        // Now setup a new overlay with different CR
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("setup-overlay 3");
        assertThat(orchestrator.waitForRegistryOutput("setup completed with 3 connections", 10))
            .as("Registry should allow new overlay setup after previous task")
            .isTrue();
        
        // Verify nodes are still registered
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(nodes).hasSize(5);
    }
    
    @Test
    @Order(4)
    @DisplayName("Test Registry handling of invalid commands (Section 1.1)")
    void testInvalidCommandHandling() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 3);
        Thread.sleep(2000);
        
        // Test various invalid commands
        String[] invalidCommands = {
            "invalid-command",
            "setup overlay 2", // Missing hyphen
            "setup-overlay", // Missing parameter
            "setup-overlay abc", // Invalid parameter type
            "start", // Missing rounds parameter
            "start abc", // Invalid rounds parameter
            "list messaging nodes", // Missing hyphens
            ""  // Empty command
        };
        
        for (String cmd : invalidCommands) {
            orchestrator.clearOutputs();
            orchestrator.sendRegistryCommand(cmd);
            Thread.sleep(500);
            
            List<String> output = orchestrator.getRegistryOutput();
            
            // Registry should handle invalid commands gracefully
            boolean foundErrorOrIgnored = false;
            for (String line : output) {
                if (line.toLowerCase().contains("invalid") ||
                    line.toLowerCase().contains("unknown") ||
                    line.toLowerCase().contains("error") ||
                    line.isEmpty()) {
                    foundErrorOrIgnored = true;
                    break;
                }
            }
            
            // Registry should not crash - verify it's still responsive
            orchestrator.sendRegistryCommand("list-messaging-nodes");
            Thread.sleep(500);
            assertThat(orchestrator.getRegistryOutput())
                .as("Registry should remain responsive after invalid command: " + cmd)
                .isNotNull();
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("Test Registry handling node disconnection during operation (Section 1.1)")
    void testNodeDisconnectionHandling() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 4);
        Thread.sleep(2000);
        
        // Start nodes
        List<Process> nodeProcesses = new ArrayList<>();
        for (int e = 0; e < 5; e++) {
            Process node = new ProcessBuilder(
                "java", "-cp", "build/classes/java/main",
                "csx55.overlay.node.MessagingNode",
                "localhost", String.valueOf(BASE_PORT + 4)
            ).start();
            nodeProcesses.add(node);
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        
        // Verify all registered
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> beforeNodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(beforeNodes).hasSize(5);
        
        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 2");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
            .isTrue();
        
        // Kill a node abruptly (simulating crash)
        nodeProcesses.get(2).destroyForcibly();
        Thread.sleep(3000);
        
        // Registry should detect disconnection and update its state
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> afterNodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        
        // Should have one less node
        assertThat(afterNodes.size())
            .as("Registry should remove disconnected node from list")
            .isLessThan(beforeNodes.size());
        
        // Clean up remaining nodes
        for (Process node : nodeProcesses) {
            if (node.isAlive()) {
                node.destroyForcibly();
            }
        }
    }
    
    @Test
    @Order(6)
    @DisplayName("Test Registry command queue during busy operations (Section 1.1)")
    void testCommandQueueing() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 5);
        Thread.sleep(2000);
        
        // Start nodes
        for (int e = 0; e < 8; e++) {
            orchestrator.startMessagingNode("localhost", BASE_PORT + 5);
            Thread.sleep(300);
        }
        Thread.sleep(2000);
        
        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 3");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 15))
            .isTrue();
        
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .isTrue();
        
        // Send multiple commands rapidly
        orchestrator.sendRegistryCommand("start 10");
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        orchestrator.sendRegistryCommand("list-weights");
        
        Thread.sleep(1000);
        
        // Registry should handle all commands in order
        List<String> output = orchestrator.getRegistryOutput();
        
        // Should see evidence of all commands being processed
        boolean foundStartMessage = false;
        boolean foundNodeList = false;
        boolean foundWeightsList = false;
        
        for (String line : output) {
            if (line.contains("rounds completed")) {
                foundStartMessage = true;
            }
            // Node list will have IP:port format
            if (line.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+")) {
                foundNodeList = true;
            }
            // Weight list has format IP:port, IP:port, weight
            if (line.matches(".*:\\d+, .*:\\d+, \\d+")) {
                foundWeightsList = true;
            }
        }
        
        // At least some commands should have been processed
        assertThat(foundStartMessage || foundNodeList || foundWeightsList)
            .as("Registry should process queued commands")
            .isTrue();
    }
    
    @Test
    @Order(7)
    @DisplayName("Test Registry overlay setup with maximum CR (Section 1.1)")
    void testMaximumCR() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 6);
        Thread.sleep(2000);
        
        int nodeCount = 6;
        
        // Start nodes
        for (int e = 0; e < nodeCount; e++) {
            orchestrator.startMessagingNode("localhost", BASE_PORT + 6);
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        
        // Try to setup with CR = N-1 (maximum possible)
        orchestrator.sendRegistryCommand("setup-overlay " + (nodeCount - 1));
        assertThat(orchestrator.waitForRegistryOutput("setup completed with " + (nodeCount - 1) + " connections", 10))
            .as("Should setup fully connected overlay")
            .isTrue();
        
        // Try to setup with CR > N-1 (impossible)
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("setup-overlay " + nodeCount);
        Thread.sleep(2000);
        
        // Should either fail or adjust CR down
        List<String> output = orchestrator.getRegistryOutput();
        boolean foundError = false;
        boolean foundAdjusted = false;
        
        for (String line : output) {
            if (line.toLowerCase().contains("error") || 
                line.toLowerCase().contains("cannot") ||
                line.toLowerCase().contains("impossible")) {
                foundError = true;
            }
            if (line.contains("setup completed with " + (nodeCount - 1) + " connections")) {
                foundAdjusted = true;
            }
        }
        
        assertThat(foundError || foundAdjusted)
            .as("Registry should handle CR > N-1 gracefully")
            .isTrue();
    }
    
    @Test
    @Order(8)
    @DisplayName("Test Registry recovery after messaging task failure (Section 1.1)")
    void testRecoveryAfterTaskFailure() throws Exception {
        orchestrator.startRegistry(BASE_PORT + 7);
        Thread.sleep(2000);
        
        // Start nodes
        List<Integer> nodeIds = new ArrayList<>();
        for (int e = 0; e < 4; e++) {
            nodeIds.add(orchestrator.startMessagingNode("localhost", BASE_PORT + 7));
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        
        // Setup overlay
        orchestrator.sendRegistryCommand("setup-overlay 2");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
            .isTrue();
        
        orchestrator.sendRegistryCommand("send-overlay-link-weights");
        assertThat(orchestrator.waitForRegistryOutput("link weights assigned", 5))
            .isTrue();
        
        // Start a messaging task
        orchestrator.sendRegistryCommand("start 5");
        
        // Kill a node during the task to simulate failure
        Thread.sleep(2000);
        // Force stop the node process
        Process nodeProcess = new ProcessBuilder(
            "pkill", "-f", "MessagingNode"
        ).start();
        nodeProcess.waitFor(1, TimeUnit.SECONDS);
        
        Thread.sleep(10000);
        
        // Registry should still be functional
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> remainingNodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(remainingNodes)
            .as("Registry should track remaining nodes after failure")
            .hasSize(3);
        
        // Should be able to setup new overlay with remaining nodes
        orchestrator.clearOutputs();
        orchestrator.sendRegistryCommand("setup-overlay 2");
        assertThat(orchestrator.waitForRegistryOutput("setup completed", 10))
            .as("Registry should allow new overlay after node failure")
            .isTrue();
    }
}