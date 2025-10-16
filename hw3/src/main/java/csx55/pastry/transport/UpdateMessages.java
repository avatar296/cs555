package csx55.pastry.transport;

import csx55.pastry.util.NodeInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class UpdateMessages {

  public static class LeaveNotificationData {
    public NodeInfo departingNode;
    public NodeInfo replacementNeighbor; // can be null

    public LeaveNotificationData(NodeInfo departingNode, NodeInfo replacementNeighbor) {
      this.departingNode = departingNode;
      this.replacementNeighbor = replacementNeighbor;
    }
  }

  public static Message createRoutingTableUpdate(NodeInfo node) throws IOException {
    return createNodeInfoMessage(MessageType.ROUTING_TABLE_UPDATE, node);
  }

  public static Message createLeafSetUpdate(NodeInfo node) throws IOException {
    return createNodeInfoMessage(MessageType.LEAF_SET_UPDATE, node);
  }

  public static Message createLeaveNotification(
      NodeInfo departingNode, NodeInfo replacementNeighbor) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    // Write departing node info
    dos.writeUTF(departingNode.getId());
    dos.writeUTF(departingNode.getHost());
    dos.writeInt(departingNode.getPort());
    dos.writeUTF(departingNode.getNickname() != null ? departingNode.getNickname() : "");

    // Write replacement neighbor info (null if none)
    boolean hasReplacement = (replacementNeighbor != null);
    dos.writeBoolean(hasReplacement);

    if (hasReplacement) {
      dos.writeUTF(replacementNeighbor.getId());
      dos.writeUTF(replacementNeighbor.getHost());
      dos.writeInt(replacementNeighbor.getPort());
      dos.writeUTF(
          replacementNeighbor.getNickname() != null ? replacementNeighbor.getNickname() : "");
    }

    dos.flush();
    return new Message(MessageType.LEAVE, baos.toByteArray());
  }

  public static LeaveNotificationData extractLeaveNotification(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    // Read departing node
    String id = dis.readUTF();
    String host = dis.readUTF();
    int port = dis.readInt();
    String nickname = dis.readUTF();
    NodeInfo departingNode = new NodeInfo(id, host, port, nickname.isEmpty() ? null : nickname);

    // Read replacement neighbor (if present)
    boolean hasReplacement = dis.readBoolean();
    NodeInfo replacementNeighbor = null;

    if (hasReplacement) {
      String replId = dis.readUTF();
      String replHost = dis.readUTF();
      int replPort = dis.readInt();
      String replNickname = dis.readUTF();
      replacementNeighbor =
          new NodeInfo(replId, replHost, replPort, replNickname.isEmpty() ? null : replNickname);
    }

    return new LeaveNotificationData(departingNode, replacementNeighbor);
  }

  private static Message createNodeInfoMessage(MessageType type, NodeInfo node) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(node.getId());
    dos.writeUTF(node.getHost());
    dos.writeInt(node.getPort());
    dos.writeUTF(node.getNickname() != null ? node.getNickname() : "");

    dos.flush();
    return new Message(type, baos.toByteArray());
  }
}
