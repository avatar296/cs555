package csx55.threads.util;

public final class NodeId {
  private final String host;
  private final int port;

  private NodeId(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public static NodeId parse(String id) {
    String[] parts = id.split(":");
    if (parts.length != 2) throw new IllegalArgumentException("Invalid node id: " + id);
    return new NodeId(parts[0], Integer.parseInt(parts[1]));
  }

  public static String toId(String host, int port) {
    return host + ":" + port;
  }

  public String host() {
    return host;
  }

  public int port() {
    return port;
  }
}
