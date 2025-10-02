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

  // JOIN_REQUEST: requester, destination, hop count, path
  public static Message createJoinRequest(
      NodeInfo requester, String destination, int hopCount, java.util.List<String> path)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    // Write requester info
    dos.writeUTF(requester.getId());
    dos.writeUTF(requester.getHost());
    dos.writeInt(requester.getPort());

    // Write destination and hop count
    dos.writeUTF(destination);
    dos.writeInt(hopCount);

    // Write path
    dos.writeInt(path.size());
    for (String nodeId : path) {
      dos.writeUTF(nodeId);
    }

    dos.flush();
    return new Message(MessageType.JOIN_REQUEST, baos.toByteArray());
  }

  public static class JoinRequestData {
    public NodeInfo requester;
    public String destination;
    public int hopCount;
    public java.util.List<String> path;
  }

  public static JoinRequestData extractJoinRequest(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    JoinRequestData data = new JoinRequestData();

    // Read requester info
    String id = dis.readUTF();
    String host = dis.readUTF();
    int port = dis.readInt();
    data.requester = new NodeInfo(id, host, port);

    // Read destination and hop count
    data.destination = dis.readUTF();
    data.hopCount = dis.readInt();

    // Read path
    int pathSize = dis.readInt();
    data.path = new java.util.ArrayList<>();
    for (int i = 0; i < pathSize; i++) {
      data.path.add(dis.readUTF());
    }

    return data;
  }

  // JOIN_RESPONSE: row number, routing table row, leaf set left/right
  public static Message createJoinResponse(
      int rowNum, NodeInfo[] routingRow, NodeInfo left, NodeInfo right) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeInt(rowNum);

    // Write routing row (16 entries, may be null)
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

    // Write leaf set left and right
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

  public static class JoinResponseData {
    public int rowNum;
    public NodeInfo[] routingRow;
    public NodeInfo left;
    public NodeInfo right;
  }

  public static JoinResponseData extractJoinResponse(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    JoinResponseData data = new JoinResponseData();

    data.rowNum = dis.readInt();

    // Read routing row
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

    // Read leaf set
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

  // ROUTING_TABLE_UPDATE and LEAF_SET_UPDATE both just carry NodeInfo
  public static Message createRoutingTableUpdate(NodeInfo node) throws IOException {
    return createNodeInfoMessage(MessageType.ROUTING_TABLE_UPDATE, node);
  }

  public static Message createLeafSetUpdate(NodeInfo node) throws IOException {
    return createNodeInfoMessage(MessageType.LEAF_SET_UPDATE, node);
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

  // GET_RANDOM_NODE with exclude ID
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

  // LOOKUP protocol
  public static class LookupRequestData {
    public String targetId;
    public NodeInfo origin;
    public java.util.List<String> path;
  }

  public static Message createLookupRequest(
      String targetId, NodeInfo origin, java.util.List<String> path) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(targetId);

    // Write origin node info
    dos.writeUTF(origin.getId());
    dos.writeUTF(origin.getHost());
    dos.writeInt(origin.getPort());
    dos.writeUTF(origin.getNickname() != null ? origin.getNickname() : "");

    // Write path
    dos.writeInt(path.size());
    for (String nodeId : path) {
      dos.writeUTF(nodeId);
    }

    dos.flush();
    return new Message(MessageType.LOOKUP, baos.toByteArray());
  }

  public static LookupRequestData extractLookupRequest(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    LookupRequestData data = new LookupRequestData();

    data.targetId = dis.readUTF();

    // Read origin
    String originId = dis.readUTF();
    String originHost = dis.readUTF();
    int originPort = dis.readInt();
    String originNickname = dis.readUTF();
    data.origin = new NodeInfo(originId, originHost, originPort, originNickname);

    // Read path
    int pathSize = dis.readInt();
    data.path = new java.util.ArrayList<>();
    for (int i = 0; i < pathSize; i++) {
      data.path.add(dis.readUTF());
    }

    return data;
  }

  public static class LookupResponseData {
    public String targetId;
    public NodeInfo responsible;
    public java.util.List<String> path;
  }

  public static Message createLookupResponse(
      String targetId, NodeInfo responsible, java.util.List<String> path) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(targetId);

    // Write responsible node info
    dos.writeUTF(responsible.getId());
    dos.writeUTF(responsible.getHost());
    dos.writeInt(responsible.getPort());
    dos.writeUTF(responsible.getNickname() != null ? responsible.getNickname() : "");

    // Write path
    dos.writeInt(path.size());
    for (String nodeId : path) {
      dos.writeUTF(nodeId);
    }

    dos.flush();
    return new Message(MessageType.LOOKUP_RESPONSE, baos.toByteArray());
  }

  public static LookupResponseData extractLookupResponse(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    LookupResponseData data = new LookupResponseData();

    data.targetId = dis.readUTF();

    // Read responsible node
    String nodeId = dis.readUTF();
    String host = dis.readUTF();
    int port = dis.readInt();
    String nickname = dis.readUTF();
    data.responsible = new NodeInfo(nodeId, host, port, nickname);

    // Read path
    int pathSize = dis.readInt();
    data.path = new java.util.ArrayList<>();
    for (int i = 0; i < pathSize; i++) {
      data.path.add(dis.readUTF());
    }

    return data;
  }

  // STORE_FILE protocol
  public static Message createStoreFileRequest(String filename, byte[] fileData)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(filename);
    dos.writeInt(fileData.length);
    dos.write(fileData);

    dos.flush();
    return new Message(MessageType.STORE_FILE, baos.toByteArray());
  }

  public static class StoreFileData {
    public String filename;
    public byte[] fileData;
  }

  public static StoreFileData extractStoreFileRequest(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    StoreFileData data = new StoreFileData();
    data.filename = dis.readUTF();
    int length = dis.readInt();
    data.fileData = new byte[length];
    dis.readFully(data.fileData);

    return data;
  }

  // RETRIEVE_FILE protocol
  public static Message createRetrieveFileRequest(String filename) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(filename);

    dos.flush();
    return new Message(MessageType.RETRIEVE_FILE, baos.toByteArray());
  }

  public static String extractRetrieveFileRequest(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    return dis.readUTF();
  }

  // FILE_DATA (response for retrieve)
  public static Message createFileDataResponse(String filename, byte[] fileData, boolean success)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeBoolean(success);
    dos.writeUTF(filename);
    if (success && fileData != null) {
      dos.writeInt(fileData.length);
      dos.write(fileData);
    } else {
      dos.writeInt(0);
    }

    dos.flush();
    return new Message(MessageType.FILE_DATA, baos.toByteArray());
  }

  public static class FileDataResponse {
    public boolean success;
    public String filename;
    public byte[] fileData;
  }

  public static FileDataResponse extractFileDataResponse(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    FileDataResponse data = new FileDataResponse();
    data.success = dis.readBoolean();
    data.filename = dis.readUTF();
    int length = dis.readInt();
    if (length > 0) {
      data.fileData = new byte[length];
      dis.readFully(data.fileData);
    }

    return data;
  }

  // ACK for store
  public static Message createAck(boolean success, String message) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeBoolean(success);
    dos.writeUTF(message != null ? message : "");

    dos.flush();
    return new Message(MessageType.ACK, baos.toByteArray());
  }

  public static class AckData {
    public boolean success;
    public String message;
  }

  public static AckData extractAck(Message message) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(message.getPayload());
    DataInputStream dis = new DataInputStream(bais);

    AckData data = new AckData();
    data.success = dis.readBoolean();
    data.message = dis.readUTF();

    return data;
  }
}
