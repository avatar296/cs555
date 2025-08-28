package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a data message routed through the overlay network. Contains a payload that is
 * transmitted from a source node to a destination (sink) node, potentially through intermediate
 * relay nodes.
 *
 * <p>Wire format: - int: message type (DATA_MESSAGE) - String: source node ID - String: sink node
 * ID - int: payload value
 */
public class DataMessage extends AbstractEvent {

  /** Message type identifier */
  private static final int TYPE = Protocol.DATA_MESSAGE;

  /** Identifier of the node that originated this message */
  private String sourceNodeId;

  /** Identifier of the destination node */
  private String sinkNodeId;

  /** The data payload being transmitted */
  private int payload;

  /**
   * Constructs a new DataMessage.
   *
   * @param sourceNodeId the identifier of the source node
   * @param sinkNodeId the identifier of the destination node
   * @param payload the data payload to transmit
   */
  public DataMessage(String sourceNodeId, String sinkNodeId, int payload) {
    this.sourceNodeId = sourceNodeId;
    this.sinkNodeId = sinkNodeId;
    this.payload = payload;
  }

  /**
   * Constructs a DataMessage by deserializing from bytes.
   *
   * @param marshalledBytes the serialized message data
   * @throws IOException if deserialization fails or message type is invalid
   */
  public DataMessage(byte[] marshalledBytes) throws IOException {
    deserializeFrom(marshalledBytes);
  }

  /**
   * Gets the message type.
   *
   * @return the protocol message type (DATA_MESSAGE)
   */
  @Override
  public int getType() {
    return TYPE;
  }

  /**
   * Writes the DataMessage-specific data to the output stream.
   *
   * @param dout the data output stream
   * @throws IOException if writing fails
   */
  @Override
  protected void writeData(DataOutputStream dout) throws IOException {
    dout.writeUTF(sourceNodeId);
    dout.writeUTF(sinkNodeId);
    dout.writeInt(payload);
  }

  /**
   * Reads the DataMessage-specific data from the input stream.
   *
   * @param din the data input stream
   * @throws IOException if reading fails
   */
  @Override
  protected void readData(DataInputStream din) throws IOException {
    this.sourceNodeId = din.readUTF();
    this.sinkNodeId = din.readUTF();
    this.payload = din.readInt();
  }

  /**
   * Gets the source node identifier.
   *
   * @return the ID of the node that originated this message
   */
  public String getSourceNodeId() {
    return sourceNodeId;
  }

  /**
   * Gets the destination node identifier.
   *
   * @return the ID of the destination node
   */
  public String getSinkNodeId() {
    return sinkNodeId;
  }

  /**
   * Gets the message payload.
   *
   * @return the data payload
   */
  public int getPayload() {
    return payload;
  }
}
