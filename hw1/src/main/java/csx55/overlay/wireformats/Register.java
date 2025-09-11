package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Register implements Event {
  private final String ip;
  private final int port;

  public Register(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  public Register(DataInputStream in) throws IOException {
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
    return Protocol.REGISTER;
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    out.writeInt(type());
    out.writeUTF(ip);
    out.writeInt(port);
  }
}
