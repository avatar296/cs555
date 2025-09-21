package csx55.threads.util;

import csx55.threads.core.Task;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.List;

public final class NetworkUtil {
  private NetworkUtil() {}

  public static void sendString(String nodeId, String msg) throws IOException {
    System.out.println(
        "[NET-DEBUG] sendString to "
            + nodeId
            + ": '"
            + msg.substring(0, Math.min(msg.length(), 100))
            + "'");
    NodeId nid = NodeId.parse(nodeId);
    try (Socket sock = new Socket(nid.host(), nid.port());
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject(msg);
      out.flush();
      System.out.println("[NET-DEBUG] Message sent successfully to " + nodeId);
    } catch (IOException e) {
      System.out.println("[NET-DEBUG] Failed to send message to " + nodeId + ": " + e.getMessage());
      throw e;
    }
  }

  public static void sendTasks(String nodeId, List<Task> tasks) throws IOException {
    NodeId nid = NodeId.parse(nodeId);
    try (Socket sock = new Socket(nid.host(), nid.port());
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject(Protocol.TASKS);
      out.writeObject(tasks);
      out.flush();
    }
  }

  public static void sendWithObject(String nodeId, String header, Serializable payload)
      throws IOException {
    NodeId nid = NodeId.parse(nodeId);
    try (Socket sock = new Socket(nid.host(), nid.port());
        ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
      out.writeObject(header);
      out.writeObject(payload);
      out.flush();
    }
  }
}
