package csx55.overlay.wireformats;

import java.io.*;

public class TaskSummaryResponse implements Event {
  private final String ip;
  private final int port;
  private final int sentCount;
  private final long sentSum;
  private final int recvCount;
  private final long recvSum;
  private final int relayedCount;

  public TaskSummaryResponse(
      String ip,
      int port,
      int sentCount,
      long sentSum,
      int recvCount,
      long recvSum,
      int relayedCount) {
    this.ip = ip;
    this.port = port;
    this.sentCount = sentCount;
    this.sentSum = sentSum;
    this.recvCount = recvCount;
    this.recvSum = recvSum;
    this.relayedCount = relayedCount;
  }

  public TaskSummaryResponse(DataInputStream in) throws IOException {
    this.ip = in.readUTF();
    this.port = in.readInt();
    this.sentCount = in.readInt();
    this.sentSum = in.readLong();
    this.recvCount = in.readInt();
    this.recvSum = in.readLong();
    this.relayedCount = in.readInt();
  }

  public String ip() {
    return ip;
  }

  public int port() {
    return port;
  }

  public int sentCount() {
    return sentCount;
  }

  public long sentSum() {
    return sentSum;
  }

  public int recvCount() {
    return recvCount;
  }

  public long recvSum() {
    return recvSum;
  }

  public int relayedCount() {
    return relayedCount;
  }

  @Override
  public int type() {
    return Protocol.TRAFFIC_SUMMARY;
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    out.writeInt(type());
    out.writeUTF(ip);
    out.writeInt(port);
    out.writeInt(sentCount);
    out.writeLong(sentSum);
    out.writeInt(recvCount);
    out.writeLong(recvSum);
    out.writeInt(relayedCount);
  }
}
