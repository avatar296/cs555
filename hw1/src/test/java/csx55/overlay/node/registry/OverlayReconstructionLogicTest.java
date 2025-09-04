package csx55.overlay.node.registry;

import static org.assertj.core.api.Assertions.*;

import csx55.overlay.util.OverlayCreator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the core logic of dynamic overlay reconstruction without complex mocking. Validates that
 * the OverlayCreator produces correct edge counts for the autograder scenario.
 */
public class OverlayReconstructionLogicTest {

  /**
   * Test the exact autograder scenario: verify that a 9-node overlay with CR=4 produces exactly 18
   * edges as expected by the autograder.
   */
  @Test
  void testAutograderExpectedEdgeCount() {
    // Simulate the 9 remaining nodes after disconnections (autograder scenario)
    List<String> remainingNodes =
        Arrays.asList(
            "129.82.44.145:37701",
            "129.82.44.164:46595",
            "129.82.44.135:37421",
            "129.82.44.131:34907",
            "129.82.44.133:46237",
            "129.82.44.136:33073",
            "129.82.44.138:43253",
            "129.82.44.134:46555",
            "129.82.44.171:39117");

    int connectionRequirement = 4; // Autograder default CR

    // Create overlay for remaining nodes (this is what rebuildOverlay() does)
    OverlayCreator.ConnectionPlan rebuiltOverlay =
        OverlayCreator.createOverlay(remainingNodes, connectionRequirement);

    // CRITICAL: Verify edge count matches autograder expectation
    int expectedEdges = (remainingNodes.size() * connectionRequirement) / 2;
    assertThat(expectedEdges).isEqualTo(18); // 9 × 4 ÷ 2 = 18

    assertThat(rebuiltOverlay.getAllLinks())
        .hasSize(expectedEdges)
        .withFailMessage(
            "🚨 AUTOGRADER MISMATCH: Expected %d edges for %d nodes with CR=%d, but got %d edges",
            expectedEdges,
            remainingNodes.size(),
            connectionRequirement,
            rebuiltOverlay.getAllLinks().size());

    // Verify all links are between the provided nodes
    for (OverlayCreator.Link link : rebuiltOverlay.getAllLinks()) {
      assertThat(remainingNodes).contains(link.getNodeA());
      assertThat(remainingNodes).contains(link.getNodeB());
      assertThat(link.getWeight()).isBetween(1, 10);

      // Verify proper format for autograder
      String linkOutput = link.toString();
      assertThat(linkOutput)
          .matches(
              "\\\\d+\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+:\\\\d+, \\\\d+\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+:\\\\d+, \\\\d+");
    }

    // SUCCESS: Dynamic reconstruction produces correct edge count for autograder
    // compliance
  }

  /** Test the original 12-node scenario to ensure we understand the baseline. */
  @Test
  void testOriginal12NodeScenario() {
    List<String> originalNodes =
        Arrays.asList(
            "129.82.44.145:37701",
            "129.82.44.164:46595",
            "129.82.44.135:37421",
            "129.82.44.131:34907",
            "129.82.44.133:46237",
            "129.82.44.136:33073",
            "129.82.44.138:43253",
            "129.82.44.134:46555",
            "129.82.44.171:39117",
            "129.82.44.168:44715",
            "129.82.44.160:33373",
            "129.82.44.140:38255");

    int connectionRequirement = 4;

    OverlayCreator.ConnectionPlan originalOverlay =
        OverlayCreator.createOverlay(originalNodes, connectionRequirement);

    // 12 nodes × CR=4 ÷ 2 = 24 edges
    int expectedEdges = (originalNodes.size() * connectionRequirement) / 2;
    assertThat(expectedEdges).isEqualTo(24);
    assertThat(originalOverlay.getAllLinks()).hasSize(expectedEdges);

    // Original 12-node overlay produces correct edge count
  }

  /** Test that demonstrates why simple filtering fails and reconstruction is necessary. */
  @Test
  void testWhyFilteringFailsReconstructionSucceeds() {
    // Create original 12-node overlay
    List<String> originalNodes =
        Arrays.asList(
            "129.82.44.145:37701",
            "129.82.44.164:46595",
            "129.82.44.135:37421",
            "129.82.44.131:34907",
            "129.82.44.133:46237",
            "129.82.44.136:33073",
            "129.82.44.138:43253",
            "129.82.44.134:46555",
            "129.82.44.171:39117",
            "129.82.44.168:44715",
            "129.82.44.160:33373",
            "129.82.44.140:38255");

    OverlayCreator.ConnectionPlan originalOverlay = OverlayCreator.createOverlay(originalNodes, 4);
    assertThat(originalOverlay.getAllLinks()).hasSize(24);

    // Simulate filtering approach (what we used to do - WRONG!)
    List<String> remainingNodes =
        Arrays.asList(
            "129.82.44.145:37701",
            "129.82.44.164:46595",
            "129.82.44.135:37421",
            "129.82.44.131:34907",
            "129.82.44.133:46237",
            "129.82.44.136:33073",
            "129.82.44.138:43253",
            "129.82.44.134:46555",
            "129.82.44.171:39117");

    // Count how many original links would survive filtering
    int survivingLinks = 0;
    for (OverlayCreator.Link link : originalOverlay.getAllLinks()) {
      if (remainingNodes.contains(link.getNodeA()) && remainingNodes.contains(link.getNodeB())) {
        survivingLinks++;
      }
    }

    // FILTERING approach produces insufficient edges
    assertThat(survivingLinks).isLessThan(18); // This is why filtering fails!

    // Now test reconstruction approach (what we implemented - CORRECT!)
    OverlayCreator.ConnectionPlan reconstructedOverlay =
        OverlayCreator.createOverlay(remainingNodes, 4);

    // RECONSTRUCTION approach produces correct edge count
    assertThat(reconstructedOverlay.getAllLinks())
        .hasSize(18); // This matches autograder expectation!

    // PROOF: Reconstruction fixes the autograder edge count issue!
  }

  /** Test edge cases for overlay reconstruction. */
  @Test
  void testReconstructionEdgeCases() {
    // Test with minimal viable configuration
    List<String> minimalNodes =
        Arrays.asList(
            "10.0.0.1:8001", "10.0.0.2:8002", "10.0.0.3:8003", "10.0.0.4:8004", "10.0.0.5:8005");

    OverlayCreator.ConnectionPlan minimalOverlay = OverlayCreator.createOverlay(minimalNodes, 2);
    int expectedMinimalEdges = (5 * 2) / 2; // 5 edges
    assertThat(minimalOverlay.getAllLinks()).hasSize(expectedMinimalEdges);

    // Test with higher CR
    List<String> moreNodes =
        Arrays.asList(
            "192.168.1.1:8001",
            "192.168.1.2:8002",
            "192.168.1.3:8003",
            "192.168.1.4:8004",
            "192.168.1.5:8005",
            "192.168.1.6:8006",
            "192.168.1.7:8007");

    OverlayCreator.ConnectionPlan higherCROverlay = OverlayCreator.createOverlay(moreNodes, 3);
    int expectedHigherCREdges = (7 * 3) / 2; // 10.5 rounded down = 10 edges
    // Note: The exact count might vary slightly due to k-regular graph constraints
    assertThat(higherCROverlay.getAllLinks().size()).isCloseTo(expectedHigherCREdges, within(2));
  }
}
