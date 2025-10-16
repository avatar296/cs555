package csx55.pastry.transport;

import csx55.pastry.util.NodeInfo;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class UpdateMessages {

  public static Message createRoutingTableUpdate(NodeInfo node) throws IOException {
    return createNodeInfoMessage(MessageType.ROUTING_TABLE_UPDATE, node);
  }

  public static Message createLeafSetUpdate(NodeInfo node) throws IOException {
    return createNodeInfoMessage(MessageType.LEAF_SET_UPDATE, node);
  }

  public static Message createLeaveNotification(NodeInfo node) throws IOException {
    return createNodeInfoMessage(MessageType.LEAVE, node);
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
