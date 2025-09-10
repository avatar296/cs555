package csx55.threads;

public class NodeInfo {
  private String ip;
  private int port;

  public NodeInfo(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  public String getIp() {
    return ip;
  }

  public int getPort() {
    return port;
  }

  @Override
  public String toString() {
    return ip + ":" + port;
  }
}
