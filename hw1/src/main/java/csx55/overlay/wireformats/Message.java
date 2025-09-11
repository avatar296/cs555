package csx55.overlay.wireformats;

import java.io.*;

public class Message implements Event {
  private final String sourceId;
  private final String sinkId;
  private final int payload;

  public Message(String sourceId, String sinkId, int payload) {
    this.sourceId = sourceId;
    this.sinkId = sinkId;
    this.payload = payload;
  }

  public Message(DataInputStream in) throws IOException {
    this.sourceId = in.readUTF();
    this.sinkId = in.readUTF();
    this.payload = in.readInt();
  }

  public String sourceId() {
    return sourceId;
  }

  public String sinkId() {
    return sinkId;
  }

  public int payload() {
    return payload;
  }

  @Override
  public int type() {
    return Protocol.MESSAGE;
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    out.writeInt(type());
    out.writeUTF(sourceId);
    out.writeUTF(sinkId);
    out.writeInt(payload);
  }
}
