package csx55.pastry.transport;

import csx55.pastry.util.NodeInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class MessageFactory {

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
}
