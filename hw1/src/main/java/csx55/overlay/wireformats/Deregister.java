package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Deregister implements Event {
  private final String ip;
  private final int port;

  public Deregister(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  public Deregister(DataInputStream in) throws IOException {
    this.ip = in.readUTF();
    this.port = in.readInt();
  }

  public String ip() {
    return ip;
  }

  public int port() {
    return port;
  }

  @Override
  public int type() {
    return Protocol.DEREGISTER;
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    out.writeInt(type());
    out.writeUTF(ip);
    out.writeInt(port);
  }
}
