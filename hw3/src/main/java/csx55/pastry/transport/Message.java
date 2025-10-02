package csx55.pastry.transport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Base message class for Pastry DHT protocol.
 *
 * <p>Wire format: [type:int][payloadLength:int][payload:bytes]
 */
public class Message {
  private final MessageType type;
  private final byte[] payload;

  public Message(MessageType type, byte[] payload) {
    this.type = type;
    this.payload = payload != null ? payload : new byte[0];
  }

  public Message(MessageType type) {
    this(type, null);
  }

  public MessageType getType() {
    return type;
  }

  public byte[] getPayload() {
    return payload;
  }

  /**
   * Marshals the message to bytes for network transmission.
   *
   * @return byte array representation
   * @throws IOException if marshaling fails
   */
  public byte[] marshal() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(baos));

    dos.writeInt(type.ordinal());
    dos.writeInt(payload.length);
    dos.write(payload);

    dos.flush();
    return baos.toByteArray();
  }

  /**
   * Unmarshals a message from bytes.
   *
   * @param data byte array to unmarshal
   * @return Message object
   * @throws IOException if unmarshaling fails
   */
  public static Message unmarshal(byte[] data) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    DataInputStream dis = new DataInputStream(new BufferedInputStream(bais));

    int typeOrdinal = dis.readInt();
    int payloadLength = dis.readInt();

    byte[] payload = new byte[payloadLength];
    dis.readFully(payload);

    MessageType type = MessageType.values()[typeOrdinal];
    return new Message(type, payload);
  }

  /**
   * Reads a message from a DataInputStream.
   *
   * @param dis input stream
   * @return Message object
   * @throws IOException if read fails
   */
  public static Message read(DataInputStream dis) throws IOException {
    int typeOrdinal = dis.readInt();
    int payloadLength = dis.readInt();

    byte[] payload = new byte[payloadLength];
    dis.readFully(payload);

    MessageType type = MessageType.values()[typeOrdinal];
    return new Message(type, payload);
  }

  /**
   * Writes this message to a DataOutputStream.
   *
   * @param dos output stream
   * @throws IOException if write fails
   */
  public void write(DataOutputStream dos) throws IOException {
    dos.writeInt(type.ordinal());
    dos.writeInt(payload.length);
    dos.write(payload);
    dos.flush();
  }

  @Override
  public String toString() {
    return "Message{type=" + type + ", payloadLength=" + payload.length + "}";
  }
}
