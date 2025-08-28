package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Response message sent by the registry after processing a deregistration request. Contains a
 * status code indicating success or failure and additional information about the deregistration
 * result.
 *
 * <p>Wire format: - int: message type (DEREGISTER_RESPONSE) - byte: status code (1 for success, 0
 * for failure) - String: additional information or error message
 */
public class DeregisterResponse extends AbstractEvent {

  /** Message type identifier */
  private static final int TYPE = Protocol.DEREGISTER_RESPONSE;

  /** Status code: 1 for success, 0 for failure */
  private byte statusCode;

  /** Additional information or error message */
  private String additionalInfo;

  /**
   * Constructs a new DeregisterResponse.
   *
   * @param statusCode the status code (1 for success, 0 for failure)
   * @param additionalInfo additional information or error message
   */
  public DeregisterResponse(byte statusCode, String additionalInfo) {
    this.statusCode = statusCode;
    this.additionalInfo = additionalInfo;
  }

  /**
   * Constructs a DeregisterResponse by deserializing from bytes.
   *
   * @param marshalledBytes the serialized message data
   * @throws IOException if deserialization fails or message type is invalid
   */
  public DeregisterResponse(byte[] marshalledBytes) throws IOException {
    deserializeFrom(marshalledBytes);
  }

  /**
   * Gets the message type.
   *
   * @return the protocol message type (DEREGISTER_RESPONSE)
   */
  @Override
  public int getType() {
    return TYPE;
  }

  /**
   * Serializes this message to bytes for network transmission.
   *
   * @return the serialized message as a byte array
   * @throws IOException if serialization fails
   */
  @Override
  protected void writeData(DataOutputStream dout) throws IOException {
    dout.writeByte(statusCode);
    dout.writeUTF(additionalInfo);
  }

  @Override
  protected void readData(DataInputStream din) throws IOException {
    this.statusCode = din.readByte();
    this.additionalInfo = din.readUTF();
  }

  /**
   * Gets the status code.
   *
   * @return 1 for success, 0 for failure
   */
  public byte getStatusCode() {
    return statusCode;
  }

  /**
   * Gets the additional information.
   *
   * @return additional information or error message
   */
  public String getAdditionalInfo() {
    return additionalInfo;
  }

  /**
   * Checks if the deregistration was successful.
   *
   * @return true if successful (status code = 1), false otherwise
   */
  public boolean isSuccess() {
    return statusCode == 1;
  }
}
