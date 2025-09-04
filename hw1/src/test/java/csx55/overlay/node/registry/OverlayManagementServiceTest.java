package csx55.overlay.node.registry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.OverlayCreator;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Integration tests for OverlayManagementService focusing on list-weights command output.
 * These tests address the critical autograder issue with missing IP addresses in output.
 */
public class OverlayManagementServiceTest {

  @Mock private NodeRegistrationService mockRegistrationService;
  @Mock private TCPConnection mockConnection1;
  @Mock private TCPConnection mockConnection2;

  private OverlayManagementService overlayService;
  private ByteArrayOutputStream outputCapture;
  private PrintStream originalOut;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    overlayService = new OverlayManagementService(mockRegistrationService);
    
    // Capture System.out for testing console output
    originalOut = System.out;
    outputCapture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputCapture));
  }

  @AfterEach
  public void tearDown() {
    System.setOut(originalOut);
  }

  /**
   * Test list-weights command output format matches autograder expectations.
   * This directly addresses the "Missing ip address in list-weights output" error.
   */
  @Test
  public void testListWeightsOutputFormat() {
    // Setup mock registered nodes with realistic IP:port format
    Map<String, TCPConnection> registeredNodes = new HashMap<>();
    registeredNodes.put("129.82.44.145:37701", mockConnection1);
    registeredNodes.put("129.82.44.164:46595", mockConnection2);
    registeredNodes.put("129.82.44.135:37421", mockConnection1);
    registeredNodes.put("129.82.44.131:34907", mockConnection2);

    when(mockRegistrationService.getRegisteredNodes()).thenReturn(registeredNodes);

    // Setup overlay with connection requirement 2
    overlayService.setupOverlay(2);
    
    // Execute list-weights command
    overlayService.listWeights();
    
    String output = outputCapture.toString();
    
    // Verify output contains IP addresses
    assertThat(output).contains("129.82.44.145");
    assertThat(output).contains("129.82.44.164");
    assertThat(output).contains("37701");
    assertThat(output).contains("46595");
    
    // Verify output contains weights
    assertThat(output).containsPattern("\\d+$"); // Ends with number (weight)
    
    // Verify proper format: "IP:PORT, IP:PORT, WEIGHT"
    assertThat(output).containsPattern("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+, \\d+\\.\\d+\\.\\d+\\.\\d+:\\d+, \\d+");
  }

  /**
   * Test that list-weights handles empty overlay gracefully.
   */
  @Test 
  public void testListWeightsWithNoOverlay() {
    overlayService.listWeights();
    
    String output = outputCapture.toString();
    assertThat(output).contains("ERROR: No overlay configured");
  }

  /**
   * Test overlay setup with realistic autograder node count.
   * Verifies proper overlay creation and weight assignment.
   */
  @Test
  public void testOverlaySetupWithRealisticNodeCount() {
    // Setup 12 nodes like in autograder test case
    Map<String, TCPConnection> registeredNodes = new HashMap<>();
    String[] nodeIds = {
      "129.82.44.145:37701", "129.82.44.164:46595", "129.82.44.135:37421",
      "129.82.44.131:34907", "129.82.44.133:46237", "129.82.44.136:33073",
      "129.82.44.138:43253", "129.82.44.134:46555", "129.82.44.171:39117", 
      "129.82.44.168:44715", "129.82.44.160:33373", "129.82.44.140:38255"
    };
    
    for (String nodeId : nodeIds) {
      registeredNodes.put(nodeId, mockConnection1);
    }
    
    when(mockRegistrationService.getRegisteredNodes()).thenReturn(registeredNodes);

    // Setup overlay with CR=4 (default connection requirement)
    overlayService.setupOverlay(4);
    
    // Verify overlay was created
    assertThat(overlayService.isOverlaySetup()).isTrue();
    
    // Test list-weights output
    overlayService.listWeights();
    String output = outputCapture.toString();
    
    // Verify output contains multiple links
    String[] lines = output.split("\n");
    long linkLines = java.util.Arrays.stream(lines)
        .filter(line -> line.matches("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+, \\d+\\.\\d+\\.\\d+\\.\\d+:\\d+, \\d+"))
        .count();
    
    assertThat(linkLines).isGreaterThan(0);
  }

  /**
   * Test that overlay setup creates correct number of connections.
   * Verifies the connection plan generation is working properly.
   */
  @Test
  public void testOverlayConnectionCount() {
    Map<String, TCPConnection> registeredNodes = new HashMap<>();
    registeredNodes.put("10.0.0.1:8001", mockConnection1);
    registeredNodes.put("10.0.0.2:8002", mockConnection2);
    registeredNodes.put("10.0.0.3:8003", mockConnection1);
    registeredNodes.put("10.0.0.4:8004", mockConnection2);
    registeredNodes.put("10.0.0.5:8005", mockConnection1);

    when(mockRegistrationService.getRegisteredNodes()).thenReturn(registeredNodes);

    overlayService.setupOverlay(2);
    
    // Verify setup completion message
    String output = outputCapture.toString();
    assertThat(output).contains("setup completed with 2 connections");
  }

  /**
   * Test error handling for insufficient nodes.
   */
  @Test
  public void testOverlaySetupInsufficientNodes() {
    Map<String, TCPConnection> registeredNodes = new HashMap<>();
    registeredNodes.put("10.0.0.1:8001", mockConnection1);
    registeredNodes.put("10.0.0.2:8002", mockConnection2);

    when(mockRegistrationService.getRegisteredNodes()).thenReturn(registeredNodes);

    overlayService.setupOverlay(4); // More than available nodes
    
    // Should not create overlay
    assertThat(overlayService.isOverlaySetup()).isFalse();
  }

  /**
   * Test that weight assignment produces sequential weights starting from 1.
   * This verifies the fix for the weight assignment issue.
   */
  @Test
  public void testSequentialWeightAssignment() {
    Map<String, TCPConnection> registeredNodes = new HashMap<>();
    registeredNodes.put("192.168.1.1:9001", mockConnection1);
    registeredNodes.put("192.168.1.2:9002", mockConnection2);
    registeredNodes.put("192.168.1.3:9003", mockConnection1);

    when(mockRegistrationService.getRegisteredNodes()).thenReturn(registeredNodes);

    overlayService.setupOverlay(2);
    overlayService.listWeights();
    
    String output = outputCapture.toString();
    
    // Should contain weight 1 
    assertThat(output).containsPattern(", 1$");
    
    // Verify weights are positive integers
    assertThat(output).containsPattern(", \\d+$");
  }

  /**
   * Test debugging output includes proper logging information.
   * Verifies enhanced logging we added for troubleshooting.
   */
  @Test
  public void testDebuggingOutput() {
    Map<String, TCPConnection> registeredNodes = new HashMap<>();
    registeredNodes.put("test1:1001", mockConnection1);
    registeredNodes.put("test2:1002", mockConnection2);

    when(mockRegistrationService.getRegisteredNodes()).thenReturn(registeredNodes);

    overlayService.setupOverlay(1);
    overlayService.listWeights();
    
    String output = outputCapture.toString();
    
    // Verify contains link output (may be in debug logs or console output)
    assertThat(output).isNotEmpty();
    assertThat(output).containsPattern("test1:1001|test2:1002");
  }
}