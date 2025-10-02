package csx55.pastry.util;

import java.util.Objects;

/**
 * Represents information about a peer node in the Pastry DHT.
 *
 * <p>Contains the node's identifier, network location, and optional nickname.
 */
public class NodeInfo {
  private final String id; // 16-bit hex ID (4 characters)
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

  /**
   * Gets the address in format "host:port"
   *
   * @return formatted address string
   */
  public String getAddress() {
    return host + ":" + port;
  }

  /**
   * Formats node info as required by assignment: "ip:port, id"
   *
   * @return formatted string
   */
  public String toOutputFormat() {
    return host + ":" + port + ", " + id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NodeInfo nodeInfo = (NodeInfo) o;
    return Objects.equals(id, nodeInfo.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "NodeInfo{id=" + id + ", address=" + getAddress() + ", nickname=" + nickname + "}";
  }
}
