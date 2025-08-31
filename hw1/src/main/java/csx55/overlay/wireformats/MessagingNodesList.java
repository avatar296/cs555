package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Message containing a list of peer nodes that a messaging node should connect
 * to. Sent by the
 * registry after overlay setup to instruct each node which peers to establish
 * connections with
 * based on the overlay topology.
 *
 *
 * Wire format: - int: message type (MESSAGING_NODES_LIST) - int: number of peer
 * nodes - For each
 * peer: String containing node identifier (IP:port)
 */
public class MessagingNodesList extends AbstractEvent {

  /** Message type identifier */
  private static final int TYPE = Protocol.MESSAGING_NODES_LIST;

  /** Number of peer nodes to connect to */
  private int numberOfPeerNodes;

  /** List of peer node identifiers (IP:port format) */
  private List<String> peerNodes;

  /**
   * Constructs a new MessagingNodesList.
   *
   * @param peerNodes the list of peer node identifiers
   */
  public MessagingNodesList(List<String> peerNodes) {
    this.peerNodes = peerNodes;
    this.numberOfPeerNodes = peerNodes.size();
  }

  /**
   * Constructs a MessagingNodesList by deserializing from bytes.
   *
   * @param marshalledBytes the serialized message data
   * @throws IOException if deserialization fails or message type is invalid
   */
  public MessagingNodesList(byte[] marshalledBytes) throws IOException {
    deserializeFrom(marshalledBytes);
  }

  /**
   * Gets the message type.
   *
   * @return the protocol message type (MESSAGING_NODES_LIST)
   */
  @Override
  public int getType() {
    return TYPE;
  }

  /**
   * Writes the MessagingNodesList-specific data to the output stream.
   *
   * @param dout the data output stream
   * @throws IOException if writing fails
   */
  @Override
  protected void writeData(DataOutputStream dout) throws IOException {
    String[] peerArray = peerNodes.toArray(new String[0]);
    writeStringArray(dout, peerArray);
  }

  /**
   * Reads the MessagingNodesList-specific data from the input stream.
   *
   * @param din the data input stream
   * @throws IOException if reading fails
   */
  @Override
  protected void readData(DataInputStream din) throws IOException {
    String[] peerArray = readStringArray(din);
    this.peerNodes = new ArrayList<>(Arrays.asList(peerArray));
    this.numberOfPeerNodes = peerNodes.size();
  }

  /**
   * Gets the number of peer nodes.
   *
   * @return the count of peer nodes in the list
   */
  public int getNumberOfPeerNodes() {
    return numberOfPeerNodes;
  }

  /**
   * Gets the list of peer node identifiers. Each identifier is in the format
   * "IP:port".
   *
   * @return the list of peer node identifiers
   */
  public List<String> getPeerNodes() {
    return peerNodes;
  }
}
