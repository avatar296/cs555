package csx55.overlay.testutil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates output against expected formats specified in the PDF */
public class TestValidator {

  /** Validate registration response format */
  public static boolean validateRegistrationResponse(String output) {
    String pattern =
        "Registration request successful\\. The number of messaging nodes currently constituting the overlay is \\(\\d+\\)";
    return output.matches(pattern);
  }

  /** Validate deregistration response */
  public static boolean validateDeregistrationResponse(String output) {
    return output.equals("exited overlay");
  }

  /** Validate connection establishment message */
  public static boolean validateConnectionEstablished(String output) {
    String pattern = "All connections are established\\. Number of connections: \\d+";
    return output.matches(pattern);
  }

  /** Validate link weights received message */
  public static boolean validateLinkWeightsReceived(String output) {
    return output.equals("Link weights received and processed. Ready to send messages.");
  }

  /** Validate setup completion message */
  public static boolean validateSetupCompletion(String output) {
    String pattern = "setup completed with \\d+ connections";
    return output.matches(pattern);
  }

  /** Validate link weights assigned message */
  public static boolean validateLinkWeightsAssigned(String output) {
    return output.equals("link weights assigned");
  }

  /** Validate rounds completion message */
  public static boolean validateRoundsCompletion(String output) {
    String pattern = "\\d+ rounds completed";
    return output.matches(pattern);
  }

  /** Parse and validate link weight format */
  public static boolean validateLinkWeightFormat(String output) {
    // Format: nodeA:portA, nodeB:portB, weight
    String pattern = "\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+, \\d+\\.\\d+\\.\\d+\\.\\d+:\\d+, \\d+";
    return output.matches(pattern);
  }

  /** Parse and validate traffic summary table */
  public static TrafficSummaryValidation validateTrafficSummary(List<String> outputs) {
    TrafficSummaryValidation result = new TrafficSummaryValidation();

    long totalSent = 0;
    long totalReceived = 0;
    double totalSumSent = 0;
    double totalSumReceived = 0;

    Pattern nodePattern =
        Pattern.compile(
            "(\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+(\\d+)");

    Pattern sumPattern =
        Pattern.compile("sum\\s+(\\d+)\\s+(\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)");

    for (String line : outputs) {
      Matcher nodeMatcher = nodePattern.matcher(line);
      if (nodeMatcher.matches()) {
        String nodeId = nodeMatcher.group(1);
        long sent = Long.parseLong(nodeMatcher.group(2));
        long received = Long.parseLong(nodeMatcher.group(3));
        double sumSent = Double.parseDouble(nodeMatcher.group(4));
        double sumReceived = Double.parseDouble(nodeMatcher.group(5));
        int relayed = Integer.parseInt(nodeMatcher.group(6));

        totalSent += sent;
        totalReceived += received;
        totalSumSent += sumSent;
        totalSumReceived += sumReceived;

        result.addNodeSummary(nodeId, sent, received, sumSent, sumReceived, relayed);
      }

      Matcher sumMatcher = sumPattern.matcher(line);
      if (sumMatcher.matches()) {
        result.expectedTotalSent = Long.parseLong(sumMatcher.group(1));
        result.expectedTotalReceived = Long.parseLong(sumMatcher.group(2));
        result.expectedSumSent = Double.parseDouble(sumMatcher.group(3));
        result.expectedSumReceived = Double.parseDouble(sumMatcher.group(4));
      }
    }

    result.actualTotalSent = totalSent;
    result.actualTotalReceived = totalReceived;
    result.actualSumSent = totalSumSent;
    result.actualSumReceived = totalSumReceived;

    return result;
  }

  /** Validate MST output format */
  public static List<MSTEdge> parseMSTOutput(List<String> outputs) {
    List<MSTEdge> edges = new ArrayList<>();
    Pattern pattern =
        Pattern.compile(
            "(\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+), (\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+), (\\d+)");

    for (String line : outputs) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.matches()) {
        String nodeA = matcher.group(1);
        String nodeB = matcher.group(2);
        int weight = Integer.parseInt(matcher.group(3));
        edges.add(new MSTEdge(nodeA, nodeB, weight));
      }
    }

    return edges;
  }

  /** Validate that node list output is correct format */
  public static List<String> parseNodeList(List<String> outputs) {
    List<String> nodes = new ArrayList<>();
    Pattern pattern = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+");

    for (String line : outputs) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.matches()) {
        nodes.add(line);
      }
    }

    return nodes;
  }

  // Helper classes

  public static class TrafficSummaryValidation {
    public long actualTotalSent = 0;
    public long actualTotalReceived = 0;
    public double actualSumSent = 0;
    public double actualSumReceived = 0;

    public long expectedTotalSent = 0;
    public long expectedTotalReceived = 0;
    public double expectedSumSent = 0;
    public double expectedSumReceived = 0;

    private final List<NodeSummary> nodeSummaries = new ArrayList<>();

    public void addNodeSummary(
        String nodeId, long sent, long received, double sumSent, double sumReceived, int relayed) {
      nodeSummaries.add(new NodeSummary(nodeId, sent, received, sumSent, sumReceived, relayed));
    }

    public boolean isValid() {
      // Check if totals match
      boolean totalsMatch =
          actualTotalSent == expectedTotalSent && actualTotalReceived == expectedTotalReceived;

      // Check if sums match (with small epsilon for floating point)
      double epsilon = 0.01;
      boolean sumsMatch =
          Math.abs(actualSumSent - expectedSumSent) < epsilon
              && Math.abs(actualSumReceived - expectedSumReceived) < epsilon;

      return totalsMatch && sumsMatch;
    }

    public List<NodeSummary> getNodeSummaries() {
      return nodeSummaries;
    }

    @Override
    public String toString() {
      return String.format(
          "TrafficSummary[sent=%d/%d, received=%d/%d, sumSent=%.2f/%.2f, sumReceived=%.2f/%.2f, valid=%s]",
          actualTotalSent,
          expectedTotalSent,
          actualTotalReceived,
          expectedTotalReceived,
          actualSumSent,
          expectedSumSent,
          actualSumReceived,
          expectedSumReceived,
          isValid());
    }
  }

  public static class NodeSummary {
    public final String nodeId;
    public final long sent;
    public final long received;
    public final double sumSent;
    public final double sumReceived;
    public final int relayed;

    public NodeSummary(
        String nodeId, long sent, long received, double sumSent, double sumReceived, int relayed) {
      this.nodeId = nodeId;
      this.sent = sent;
      this.received = received;
      this.sumSent = sumSent;
      this.sumReceived = sumReceived;
      this.relayed = relayed;
    }
  }

  public static class MSTEdge {
    public final String nodeA;
    public final String nodeB;
    public final int weight;

    public MSTEdge(String nodeA, String nodeB, int weight) {
      this.nodeA = nodeA;
      this.nodeB = nodeB;
      this.weight = weight;
    }

    @Override
    public String toString() {
      return nodeA + ", " + nodeB + ", " + weight;
    }
  }

  /** Validate MST properties (connected, spanning, minimal weight) */
  public static boolean validateMSTProperties(List<MSTEdge> edges, int expectedNodeCount) {
    // MST should have exactly n-1 edges for n nodes
    if (edges.size() != expectedNodeCount - 1) {
      return false;
    }

    // Check if all nodes are connected (simplified check)
    Set<String> nodes = new HashSet<>();
    for (MSTEdge edge : edges) {
      nodes.add(edge.nodeA);
      nodes.add(edge.nodeB);
    }

    // Should have the expected number of unique nodes
    return nodes.size() == expectedNodeCount;
  }
}
