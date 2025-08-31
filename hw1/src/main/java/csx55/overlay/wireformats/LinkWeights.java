package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Message containing the weighted links of the overlay network topology. Sent
 * by the registry to
 * all nodes after overlay setup to enable shortest path routing calculations.
 *
 * Wire format: - int: message type (LINK_WEIGHTS) - int: number of links - For
 * each link: String
 * containing "nodeA nodeB weight"
 */
public class LinkWeights extends AbstractEvent {

  /** Message type identifier */
  private static final int TYPE = Protocol.LINK_WEIGHTS;

  /** Number of links in the overlay */
  private int numberOfLinks;

  /** List of all weighted links in the topology */
  private List<LinkInfo> links;

  /** Represents a weighted link between two nodes in the overlay. */
  public static class LinkInfo {
    /** First node in the link */
    public final String nodeA;

    /** Second node in the link */
    public final String nodeB;

    /** Weight/cost of this link */
    public final int weight;

    /**
     * Constructs a new LinkInfo.
     *
     * @param nodeA  first node identifier
     * @param nodeB  second node identifier
     * @param weight the link weight
     */
    public LinkInfo(String nodeA, String nodeB, int weight) {
      this.nodeA = nodeA;
      this.nodeB = nodeB;
      this.weight = weight;
    }

    /**
     * Returns a string representation of this link.
     *
     * @return formatted string as "nodeA, nodeB, weight"
     */
    @Override
    public String toString() {
      return nodeA + ", " + nodeB + ", " + weight;
    }
  }

  /**
   * Constructs a new LinkWeights message.
   *
   * @param links the list of weighted links in the topology
   */
  public LinkWeights(List<LinkInfo> links) {
    this.links = links;
    this.numberOfLinks = links.size();
  }

  /**
   * Constructs a LinkWeights message by deserializing from bytes.
   *
   * @param marshalledBytes the serialized message data
   * @throws IOException if deserialization fails or message type is invalid
   */
  public LinkWeights(byte[] marshalledBytes) throws IOException {
    deserializeFrom(marshalledBytes);
  }

  /**
   * Gets the message type.
   *
   * @return the protocol message type (LINK_WEIGHTS)
   */
  @Override
  public int getType() {
    return TYPE;
  }

  /**
   * Writes the LinkWeights-specific data to the output stream.
   *
   * @param dout the data output stream
   * @throws IOException if writing fails
   */
  @Override
  protected void writeData(DataOutputStream dout) throws IOException {
    dout.writeInt(numberOfLinks);

    for (LinkInfo link : links) {
      dout.writeUTF(link.nodeA + " " + link.nodeB + " " + link.weight);
    }
  }

  /**
   * Reads the LinkWeights-specific data from the input stream.
   *
   * @param din the data input stream
   * @throws IOException if reading fails
   */
  @Override
  protected void readData(DataInputStream din) throws IOException {
    this.numberOfLinks = din.readInt();
    this.links = new ArrayList<>();

    for (int i = 0; i < numberOfLinks; i++) {
      String linkData = din.readUTF();
      String[] parts = linkData.split(" ");
      if (parts.length == 3) {
        links.add(new LinkInfo(parts[0], parts[1], Integer.parseInt(parts[2])));
      }
    }
  }

  /**
   * Gets the number of links in the topology.
   *
   * @return the number of links
   */
  public int getNumberOfLinks() {
    return numberOfLinks;
  }

  /**
   * Gets the list of weighted links.
   *
   * @return the list of LinkInfo objects
   */
  public List<LinkInfo> getLinks() {
    return links;
  }
}
