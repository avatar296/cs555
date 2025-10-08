package csx55.pastry.util;

public class NodeInfo {
  private final String id;
  private final String host;
  private final int port;
  private final String nickname;

  public NodeInfo(String id, String host, int port, String nickname) {
    this.id = HexUtil.normalize(id);
    this.host = host;
    this.port = port;
    this.nickname = nickname;
  }

  public NodeInfo(String id, String host, int port) {
    this(id, host, port, "");
  }

  public String getId() {
    return id;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getNickname() {
    return nickname;
  }

  public String getAddress() {
    return host + ":" + port;
  }

  public String toOutputFormat() {
    return host + ":" + port + ", " + id;
  }

  @Override
  public String toString() {
    return "NodeInfo{id=" + id + ", address=" + getAddress() + ", nickname=" + nickname + "}";
  }
}
