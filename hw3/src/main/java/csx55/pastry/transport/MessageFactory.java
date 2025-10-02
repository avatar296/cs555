package csx55.pastry.transport;

import csx55.pastry.util.NodeInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Factory for creating common message types with payloads. */
public class MessageFactory {

  /**
   * Creates a REGISTER message with NodeInfo payload.
   *
   * @param nodeInfo the node information to register
   * @return Message
   * @throws IOException if serialization fails
   */
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

  /**
   * Extracts NodeInfo from a REGISTER message payload.
   *
   * @param message the register message
   * @return NodeInfo
   * @throws IOException if deserialization fails
   */
  public static NodeInfo extractNodeInfo(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    String id = dis.readUTF();
    String host = dis.readUTF();
    int port = dis.readInt();
    String nickname = dis.readUTF();

    return new NodeInfo(id, host, port, nickname);
  }

  /**
   * Creates a REGISTER_RESPONSE message.
   *
   * @param success true if registration succeeded, false if collision
   * @param message optional message (e.g., error description)
   * @return Message
   * @throws IOException if serialization fails
   */
  public static Message createRegisterResponse(boolean success, String message) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeBoolean(success);
    dos.writeUTF(message != null ? message : "");

    dos.flush();
    return new Message(MessageType.REGISTER_RESPONSE, baos.toByteArray());
  }

  /**
   * Extracts success status from REGISTER_RESPONSE.
   *
   * @param message the response message
   * @return true if successful, false otherwise
   * @throws IOException if deserialization fails
   */
  public static boolean extractRegisterSuccess(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);
    return dis.readBoolean();
  }

  /**
   * Creates a RANDOM_NODE_RESPONSE message with NodeInfo payload.
   *
   * @param nodeInfo the random node (null if no nodes available)
   * @return Message
   * @throws IOException if serialization fails
   */
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

  /**
   * Extracts NodeInfo from RANDOM_NODE_RESPONSE (returns null if no node available).
   *
   * @param message the response message
   * @return NodeInfo or null
   * @throws IOException if deserialization fails
   */
  public static NodeInfo extractRandomNode(Message message) throws IOException {
    if (message.getPayload().length == 0) {
      return null;
    }
    return extractNodeInfo(message);
  }

  /**
   * Creates a DEREGISTER message with node ID.
   *
   * @param nodeId the ID of the node to deregister
   * @return Message
   * @throws IOException if serialization fails
   */
  public static Message createDeregisterMessage(String nodeId) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeUTF(nodeId);
    dos.flush();
    return new Message(MessageType.DEREGISTER, baos.toByteArray());
  }

  /**
   * Extracts node ID from DEREGISTER message.
   *
   * @param message the deregister message
   * @return node ID
   * @throws IOException if deserialization fails
   */
  public static String extractNodeId(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);
    return dis.readUTF();
  }
}
