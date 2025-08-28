package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Request message sent by a node to register with the overlay network. Contains the node's IP
 * address and port number for the registry to validate and add the node to the overlay topology.
 *
 * <p>Wire format: - int: message type (REGISTER_REQUEST) - String: IP address of the node - int:
 * port number of the node
 */
public class RegisterRequest extends AbstractEvent {

  /** Message type identifier */
  private static final int TYPE = Protocol.REGISTER_REQUEST;

  /** IP address of the node requesting registration */
  private String ipAddress;

  /** Port number of the node requesting registration */
  private int portNumber;

  /**
   * Constructs a new RegisterRequest.
   *
   * @param ipAddress the IP address of the registering node
   * @param portNumber the port number of the registering node
   */
  public RegisterRequest(String ipAddress, int portNumber) {
    this.ipAddress = ipAddress;
    this.portNumber = portNumber;
  }

  /**
   * Constructs a RegisterRequest by deserializing from bytes.
   *
   * @param marshalledBytes the serialized message data
   * @throws IOException if deserialization fails or message type is invalid
   */
  public RegisterRequest(byte[] marshalledBytes) throws IOException {
    deserializeFrom(marshalledBytes);
  }

  /**
   * Gets the message type.
   *
   * @return the protocol message type (REGISTER_REQUEST)
   */
  @Override
  public int getType() {
    return TYPE;
  }

  /**
   * Writes the event-specific data to the output stream.
   *
   * @param dout the data output stream
   * @throws IOException if writing fails
   */
  @Override
  protected void writeData(DataOutputStream dout) throws IOException {
    dout.writeUTF(ipAddress);
    dout.writeInt(portNumber);
  }

  /**
   * Reads the event-specific data from the input stream.
   *
   * @param din the data input stream
   * @throws IOException if reading fails
   */
  @Override
  protected void readData(DataInputStream din) throws IOException {
    this.ipAddress = din.readUTF();
    this.portNumber = din.readInt();
  }

  /**
   * Gets the IP address of the registering node.
   *
   * @return the node's IP address
   */
  public String getIpAddress() {
    return ipAddress;
  }

  /**
   * Gets the port number of the registering node.
   *
   * @return the node's port number
   */
  public int getPortNumber() {
    return portNumber;
  }
}
