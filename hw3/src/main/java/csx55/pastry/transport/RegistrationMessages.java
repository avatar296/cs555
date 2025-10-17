package csx55.pastry.transport;

import csx55.pastry.util.NodeInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RegistrationMessages {

  public static Message createRegisterMessage(NodeInfo nodeInfo) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(nodeInfo.getId());
    dos.writeUTF(nodeInfo.getHost());
    dos.writeInt(nodeInfo.getPort());
    dos.writeUTF(nodeInfo.getNickname());

    dos.flush();
    return new Message(MessageType.REGISTER, baos.toByteArray());
  }

  public static NodeInfo extractNodeInfo(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    String id = dis.readUTF();
    String host = dis.readUTF();
    int port = dis.readInt();
    String nickname = dis.readUTF();

    return new NodeInfo(id, host, port, nickname);
  }

  public static Message createRegisterResponse(boolean success, String message) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeBoolean(success);
    dos.writeUTF(message != null ? message : "");

    dos.flush();
    return new Message(MessageType.REGISTER_RESPONSE, baos.toByteArray());
  }

  public static boolean extractRegisterSuccess(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);
    return dis.readBoolean();
  }

  public static Message createRandomNodeResponse(NodeInfo nodeInfo) throws IOException {
    if (nodeInfo == null) {
      return new Message(MessageType.RANDOM_NODE_RESPONSE, new byte[0]);
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(nodeInfo.getId());
    dos.writeUTF(nodeInfo.getHost());
    dos.writeInt(nodeInfo.getPort());
    dos.writeUTF(nodeInfo.getNickname());

    dos.flush();
    return new Message(MessageType.RANDOM_NODE_RESPONSE, baos.toByteArray());
  }

  public static NodeInfo extractRandomNode(Message message) throws IOException {
    if (message.getPayload().length == 0) {
      return null;
    }
    return extractNodeInfo(message);
  }

  public static Message createDeregisterMessage(String nodeId) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeUTF(nodeId);
    dos.flush();
    return new Message(MessageType.DEREGISTER, baos.toByteArray());
  }

  public static String extractNodeId(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);
    return dis.readUTF();
  }

  public static Message createGetRandomNodeRequest(String excludeId) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(excludeId != null ? excludeId : "");

    dos.flush();
    return new Message(MessageType.GET_RANDOM_NODE, baos.toByteArray());
  }

  public static String extractExcludeId(Message message) throws IOException {
    if (message.getPayload().length == 0) {
      return null;
    }
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);
    String excludeId = dis.readUTF();
    return excludeId.isEmpty() ? null : excludeId;
  }

  public static Message createListNodesRequest() throws IOException {
    return new Message(MessageType.LIST_NODES, new byte[0]);
  }

  public static Message createListNodesResponse(java.util.List<NodeInfo> nodes) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    // Write number of nodes
    dos.writeInt(nodes != null ? nodes.size() : 0);

    // Write each node
    if (nodes != null) {
      for (NodeInfo node : nodes) {
        dos.writeUTF(node.getId());
        dos.writeUTF(node.getHost());
        dos.writeInt(node.getPort());
        dos.writeUTF(node.getNickname());
      }
    }

    dos.flush();
    return new Message(MessageType.LIST_NODES_RESPONSE, baos.toByteArray());
  }

  public static java.util.List<NodeInfo> extractNodeList(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    int count = dis.readInt();
    java.util.List<NodeInfo> nodes = new java.util.ArrayList<>();

    for (int i = 0; i < count; i++) {
      String id = dis.readUTF();
      String host = dis.readUTF();
      int port = dis.readInt();
      String nickname = dis.readUTF();
      nodes.add(new NodeInfo(id, host, port, nickname));
    }

    return nodes;
  }
}
