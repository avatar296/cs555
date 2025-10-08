package csx55.pastry.transport;

import csx55.pastry.util.NodeInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Factory methods for join protocol messages. */
public class JoinMessages {

  public static class JoinRequestData {
    public NodeInfo requester;
    public String destination;
    public int hopCount;
    public java.util.List<String> path;
  }

  public static class JoinResponseData {
    public int rowNum;
    public NodeInfo[] routingRow;
    public NodeInfo left;
    public NodeInfo right;
  }

  public static Message createJoinRequest(
      NodeInfo requester, String destination, int hopCount, java.util.List<String> path)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(requester.getId());
    dos.writeUTF(requester.getHost());
    dos.writeInt(requester.getPort());

    dos.writeUTF(destination);
    dos.writeInt(hopCount);

    dos.writeInt(path.size());
    for (String nodeId : path) {
      dos.writeUTF(nodeId);
    }

    dos.flush();
    return new Message(MessageType.JOIN_REQUEST, baos.toByteArray());
  }

  public static JoinRequestData extractJoinRequest(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    JoinRequestData data = new JoinRequestData();

    String id = dis.readUTF();
    String host = dis.readUTF();
    int port = dis.readInt();
    data.requester = new NodeInfo(id, host, port);

    data.destination = dis.readUTF();
    data.hopCount = dis.readInt();

    int pathSize = dis.readInt();
    data.path = new java.util.ArrayList<>();
    for (int i = 0; i < pathSize; i++) {
      data.path.add(dis.readUTF());
    }

    return data;
  }

  public static Message createJoinResponse(
      int rowNum, NodeInfo[] routingRow, NodeInfo left, NodeInfo right) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeInt(rowNum);

    for (int i = 0; i < 16; i++) {
      NodeInfo node = (i < routingRow.length) ? routingRow[i] : null;
      if (node != null) {
        dos.writeBoolean(true);
        dos.writeUTF(node.getId());
        dos.writeUTF(node.getHost());
        dos.writeInt(node.getPort());
      } else {
        dos.writeBoolean(false);
      }
    }

    if (left != null) {
      dos.writeBoolean(true);
      dos.writeUTF(left.getId());
      dos.writeUTF(left.getHost());
      dos.writeInt(left.getPort());
    } else {
      dos.writeBoolean(false);
    }

    if (right != null) {
      dos.writeBoolean(true);
      dos.writeUTF(right.getId());
      dos.writeUTF(right.getHost());
      dos.writeInt(right.getPort());
    } else {
      dos.writeBoolean(false);
    }

    dos.flush();
    return new Message(MessageType.JOIN_RESPONSE, baos.toByteArray());
  }

  public static JoinResponseData extractJoinResponse(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    JoinResponseData data = new JoinResponseData();

    data.rowNum = dis.readInt();

    data.routingRow = new NodeInfo[16];
    for (int i = 0; i < 16; i++) {
      boolean hasNode = dis.readBoolean();
      if (hasNode) {
        String id = dis.readUTF();
        String host = dis.readUTF();
        int port = dis.readInt();
        data.routingRow[i] = new NodeInfo(id, host, port);
      }
    }

    boolean hasLeft = dis.readBoolean();
    if (hasLeft) {
      String id = dis.readUTF();
      String host = dis.readUTF();
      int port = dis.readInt();
      data.left = new NodeInfo(id, host, port);
    }

    boolean hasRight = dis.readBoolean();
    if (hasRight) {
      String id = dis.readUTF();
      String host = dis.readUTF();
      int port = dis.readInt();
      data.right = new NodeInfo(id, host, port);
    }

    return data;
  }
}
