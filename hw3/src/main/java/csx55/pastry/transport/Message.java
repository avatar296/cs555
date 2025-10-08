package csx55.pastry.transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

// Wire format: [type:int][payloadLength:int][payload:bytes]
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

  public static Message read(DataInputStream dis) throws IOException {
    int typeOrdinal = dis.readInt();
    int payloadLength = dis.readInt();

    byte[] payload = new byte[payloadLength];
    dis.readFully(payload);

    MessageType type = MessageType.values()[typeOrdinal];
    return new Message(type, payload);
  }

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
