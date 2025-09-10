package csx55.threads;

import java.io.Serializable;

public class ComputeNodeInfo implements Serializable {
  private final String ip;
  private final int port;
  private ComputeNodeInfo successor;

  public ComputeNodeInfo(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  public String getIp() {
    return ip;
  }

  public int getPort() {
    return port;
  }

  public String getId() {
    return ip + ":" + port;
  }

  public void setSuccessor(ComputeNodeInfo successor) {
    this.successor = successor;
  }

  public ComputeNodeInfo getSuccessor() {
    return successor;
  }

  @Override
  public String toString() {
    return getId();
  }
}
