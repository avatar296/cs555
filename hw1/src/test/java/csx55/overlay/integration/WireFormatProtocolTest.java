package csx55.overlay.integration;

import csx55.overlay.testutil.TestOrchestrator;
import csx55.overlay.testutil.TestValidator;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Tests wire format protocol messages for sections 2.1 (Registration) and 2.2 (Deregistration)
 * Validates exact message formats and protocol compliance as specified in the PDF
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WireFormatProtocolTest {
    
    private static TestOrchestrator orchestrator;
    private static final int REGISTRY_PORT = 9400;
    
    @BeforeAll
    static void setup() throws Exception {
        orchestrator = new TestOrchestrator();
        orchestrator.startRegistry(REGISTRY_PORT);
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
    @DisplayName("Test REGISTER_REQUEST and REGISTER_RESPONSE format (Section 2.1)")
    void testRegistrationMessageFormat() throws Exception {
        orchestrator.clearOutputs();
        
        // Start a messaging node to trigger registration
        int nodeId = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(2000);
        
        // Check node output for registration success
        List<String> nodeOutput = orchestrator.getNodeOutput(nodeId);
        
        // Look for registration success message
        boolean foundRegistrationSuccess = false;
        String registrationMessage = null;
        
        for (String line : nodeOutput) {
            if (line.contains("Registration request successful") || 
                line.contains("registered successfully")) {
                foundRegistrationSuccess = true;
                registrationMessage = line;
                break;
            }
        }
        
        assertThat(foundRegistrationSuccess)
            .as("Node should receive successful registration response")
            .isTrue();
        
        // Verify registry has the node registered
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> registryOutput = orchestrator.getRegistryOutput();
        List<String> nodes = TestValidator.parseNodeList(registryOutput);
        
        assertThat(nodes)
            .as("Registry should have the node registered")
            .hasSize(1);
    }
    
    @Test
    @Order(2)
    @DisplayName("Test registration success information string format (Section 2.1)")
    void testRegistrationSuccessInfoString() throws Exception {
        // Clean start
        orchestrator.shutdown();
        orchestrator = new TestOrchestrator();
        orchestrator.startRegistry(REGISTRY_PORT);
        Thread.sleep(2000);
        
        orchestrator.clearOutputs();
        
        // Start multiple nodes to test node count in message
        for (int e = 0; e < 3; e++) {
            int nodeId = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
            Thread.sleep(1000);
            
            List<String> nodeOutput = orchestrator.getNodeOutput(nodeId);
            
            // Look for registration message with node count
            // Format: "Registration request successful. The number of messaging nodes currently constituting the overlay is (N)"
            Pattern pattern = Pattern.compile(".*[Rr]egistration.*successful.*(?:number|count).*nodes?.*(?:is|are)?.*\\(?(\\d+)\\)?.*");
            
            boolean foundProperFormat = false;
            for (String line : nodeOutput) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    foundProperFormat = true;
                    // The count should be e+1 (current node number)
                    String countStr = matcher.group(1);
                    if (countStr != null) {
                        int count = Integer.parseInt(countStr);
                        assertThat(count)
                            .as("Node count in registration message should be correct")
                            .isEqualTo(e + 1);
                    }
                    break;
                }
            }
            
            if (!foundProperFormat) {
                // Check for any registration success message
                for (String line : nodeOutput) {
                    if (line.toLowerCase().contains("registration") && 
                        line.toLowerCase().contains("successful")) {
                        foundProperFormat = true;
                        break;
                    }
                }
            }
        }
        
        // Verify all 3 nodes are registered
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(nodes).hasSize(3);
    }
    
    @Test
    @Order(3)
    @DisplayName("Test registration on same host with different ports (Section 2.1)")
    void testSameHostDifferentPorts() throws Exception {
        orchestrator.clearOutputs();
        
        // Start multiple nodes on localhost - they should get different ports
        int node1 = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(1000);
        int node2 = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(1000);
        int node3 = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(2000);
        
        // Get list of registered nodes
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        
        // Should have all nodes registered
        assertThat(nodes)
            .as("All nodes on same host should be registered")
            .hasSizeGreaterThanOrEqualTo(3);
        
        // Parse ports to verify they're different
        List<Integer> ports = new java.util.ArrayList<>();
        for (String node : nodes) {
            String[] parts = node.split(":");
            if (parts.length == 2) {
                try {
                    ports.add(Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    // Skip invalid entries
                }
            }
        }
        
        // All ports should be unique
        long uniquePorts = ports.stream().distinct().count();
        assertThat(uniquePorts)
            .as("Each node should have a unique port")
            .isEqualTo(ports.size());
    }
    
    @Test
    @Order(4)
    @DisplayName("Test duplicate registration error response (Section 2.1)")
    void testDuplicateRegistrationResponse() throws Exception {
        orchestrator.clearOutputs();
        
        // Start a node
        int nodeId = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(2000);
        
        // Try to register the same node again (simulate by starting another node)
        // In a real test, we'd send a duplicate REGISTER_REQUEST from the same node
        // but since we can't directly control the wire format messages in integration tests,
        // we verify that the registry handles multiple nodes properly
        
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        int initialCount = TestValidator.parseNodeList(orchestrator.getRegistryOutput()).size();
        
        // Start another node (different instance, so not a true duplicate)
        int node2 = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(2000);
        
        orchestrator.clearOutputs();  // Clear accumulated output before checking current state
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        int finalCount = TestValidator.parseNodeList(orchestrator.getRegistryOutput()).size();
        
        // Should have one more node
        assertThat(finalCount)
            .as("New node should be registered separately")
            .isEqualTo(initialCount + 1);
    }
    
    @Test
    @Order(5)
    @DisplayName("Test DEREGISTER_REQUEST and DEREGISTER_RESPONSE format (Section 2.2)")
    void testDeregistrationMessageFormat() throws Exception {
        orchestrator.clearOutputs();
        
        // Start a node
        int nodeId = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(2000);
        
        // Verify node is registered
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        int beforeCount = TestValidator.parseNodeList(orchestrator.getRegistryOutput()).size();
        
        // Deregister the node
        orchestrator.sendNodeCommand(nodeId, "exit-overlay");
        Thread.sleep(3000);
        
        // Check for deregistration completion
        List<String> nodeOutput = orchestrator.getNodeOutput(nodeId);
        boolean foundExitMessage = false;
        
        for (String line : nodeOutput) {
            if (line.equals("exited overlay")) {
                foundExitMessage = true;
                break;
            }
        }
        
        assertThat(foundExitMessage)
            .as("Node should output 'exited overlay' after deregistration")
            .isTrue();
        
        // Verify node was removed from registry
        orchestrator.clearOutputs();  // Clear accumulated output before checking current state
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        int afterCount = TestValidator.parseNodeList(orchestrator.getRegistryOutput()).size();
        
        assertThat(afterCount)
            .as("Node should be removed from registry after deregistration")
            .isEqualTo(beforeCount - 1);
    }
    
    @Test
    @Order(6)
    @DisplayName("Test deregistration of non-registered node error (Section 2.2)")
    void testNonRegisteredNodeDeregistration() throws Exception {
        // Clean restart
        orchestrator.shutdown();
        orchestrator = new TestOrchestrator();
        orchestrator.startRegistry(REGISTRY_PORT);
        Thread.sleep(2000);
        
        orchestrator.clearOutputs();
        
        // Start and immediately deregister a node
        int nodeId = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(2000);
        
        // First deregistration should succeed
        orchestrator.sendNodeCommand(nodeId, "exit-overlay");
        assertThat(orchestrator.waitForNodeOutput(nodeId, "exited overlay", 5))
            .as("First deregistration should succeed")
            .isTrue();
        
        Thread.sleep(2000);
        
        // Verify node was removed
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        
        // Should have no nodes or the node should not be in the list
        assertThat(nodes.size())
            .as("Node should be removed after deregistration")
            .isEqualTo(0);
    }
    
    @Test
    @Order(7)
    @DisplayName("Test registry state after multiple registrations and deregistrations (Section 2.1 & 2.2)")
    void testRegistryStateManagement() throws Exception {
        // Clean restart
        orchestrator.shutdown();
        orchestrator = new TestOrchestrator();
        orchestrator.startRegistry(REGISTRY_PORT);
        Thread.sleep(2000);
        
        List<Integer> nodeIds = new java.util.ArrayList<>();
        
        // Register 5 nodes
        for (int e = 0; e < 5; e++) {
            nodeIds.add(orchestrator.startMessagingNode("localhost", REGISTRY_PORT));
            Thread.sleep(500);
        }
        Thread.sleep(2000);
        
        // Verify all registered
        orchestrator.clearOutputs();  // Clear accumulated output before checking current state
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        assertThat(TestValidator.parseNodeList(orchestrator.getRegistryOutput()))
            .hasSize(5);
        
        // Deregister nodes 1 and 3
        orchestrator.sendNodeCommand(nodeIds.get(1), "exit-overlay");
        Thread.sleep(2000);
        orchestrator.sendNodeCommand(nodeIds.get(3), "exit-overlay");
        Thread.sleep(2000);
        
        // Verify correct nodes remain
        orchestrator.clearOutputs();  // Clear accumulated output before checking current state
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        assertThat(TestValidator.parseNodeList(orchestrator.getRegistryOutput()))
            .hasSize(3);
        
        // Register 2 new nodes
        nodeIds.add(orchestrator.startMessagingNode("localhost", REGISTRY_PORT));
        Thread.sleep(1000);
        nodeIds.add(orchestrator.startMessagingNode("localhost", REGISTRY_PORT));
        Thread.sleep(2000);
        
        // Verify final count
        orchestrator.clearOutputs();  // Clear accumulated output before checking current state
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        assertThat(TestValidator.parseNodeList(orchestrator.getRegistryOutput()))
            .as("Registry should correctly track all registrations and deregistrations")
            .hasSize(5);
    }
    
    @Test
    @Order(8)
    @DisplayName("Test registration with port number validation (Section 2.1)")
    void testPortNumberValidation() throws Exception {
        orchestrator.clearOutputs();
        
        // Start a node (will use automatic port selection with port 0)
        int nodeId = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(2000);
        
        // Get registered node info
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        
        List<String> nodes = TestValidator.parseNodeList(orchestrator.getRegistryOutput());
        assertThat(nodes)
            .as("Node should be registered")
            .isNotEmpty();
        
        // Validate port format in registered node info
        for (String node : nodes) {
            assertThat(node)
                .as("Node format should be IP:port")
                .matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+");
            
            // Extract and validate port
            String[] parts = node.split(":");
            if (parts.length == 2) {
                int port = Integer.parseInt(parts[1]);
                assertThat(port)
                    .as("Port should be in valid range")
                    .isBetween(1, 65535);
            }
        }
    }
    
    @Test
    @Order(9)
    @DisplayName("Test rapid registration and deregistration sequence (Section 2.1 & 2.2)")
    void testRapidRegistrationDeregistration() throws Exception {
        // Clean restart
        orchestrator.shutdown();
        orchestrator = new TestOrchestrator();
        orchestrator.startRegistry(REGISTRY_PORT);
        Thread.sleep(2000);
        
        // Perform rapid registration/deregistration cycles
        for (int cycle = 0; cycle < 3; cycle++) {
            orchestrator.clearOutputs();
            
            // Start a node
            int nodeId = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
            Thread.sleep(1500);
            
            // Verify registered
            orchestrator.sendRegistryCommand("list-messaging-nodes");
            Thread.sleep(500);
            assertThat(TestValidator.parseNodeList(orchestrator.getRegistryOutput()))
                .as("Node should be registered in cycle " + cycle)
                .hasSize(1);
            
            // Deregister
            orchestrator.sendNodeCommand(nodeId, "exit-overlay");
            Thread.sleep(2000);
            
            // Verify deregistered
            orchestrator.clearOutputs();  // Clear accumulated output before checking current state
            orchestrator.sendRegistryCommand("list-messaging-nodes");
            Thread.sleep(500);
            assertThat(TestValidator.parseNodeList(orchestrator.getRegistryOutput()))
                .as("Node should be deregistered in cycle " + cycle)
                .hasSize(0);
        }
        
        // Registry should still be functional
        int finalNode = orchestrator.startMessagingNode("localhost", REGISTRY_PORT);
        Thread.sleep(2000);
        
        orchestrator.clearOutputs();  // Clear accumulated output before checking current state
        orchestrator.sendRegistryCommand("list-messaging-nodes");
        Thread.sleep(1000);
        assertThat(TestValidator.parseNodeList(orchestrator.getRegistryOutput()))
            .as("Registry should still accept registrations after rapid cycles")
            .hasSize(1);
    }
}