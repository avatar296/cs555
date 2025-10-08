package csx55.pastry.transport;

import csx55.pastry.util.NodeInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class LookupMessages {

  public static class LookupRequestData {
    public String targetId;
    public NodeInfo origin;
    public java.util.List<String> path;
  }

  public static class LookupResponseData {
    public String targetId;
    public NodeInfo responsible;
    public java.util.List<String> path;
  }

  public static Message createLookupRequest(
      String targetId, NodeInfo origin, java.util.List<String> path) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(targetId);

    dos.writeUTF(origin.getId());
    dos.writeUTF(origin.getHost());
    dos.writeInt(origin.getPort());
    dos.writeUTF(origin.getNickname() != null ? origin.getNickname() : "");

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

    String originId = dis.readUTF();
    String originHost = dis.readUTF();
    int originPort = dis.readInt();
    String originNickname = dis.readUTF();
    data.origin = new NodeInfo(originId, originHost, originPort, originNickname);

    int pathSize = dis.readInt();
    data.path = new java.util.ArrayList<>();
    for (int i = 0; i < pathSize; i++) {
      data.path.add(dis.readUTF());
    }

    return data;
  }

  public static Message createLookupResponse(
      String targetId, NodeInfo responsible, java.util.List<String> path) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeUTF(targetId);

    dos.writeUTF(responsible.getId());
    dos.writeUTF(responsible.getHost());
    dos.writeInt(responsible.getPort());
    dos.writeUTF(responsible.getNickname() != null ? responsible.getNickname() : "");
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

    String nodeId = dis.readUTF();
    String host = dis.readUTF();
    int port = dis.readInt();
    String nickname = dis.readUTF();
    data.responsible = new NodeInfo(nodeId, host, port, nickname);

    int pathSize = dis.readInt();
    data.path = new java.util.ArrayList<>();
    for (int i = 0; i < pathSize; i++) {
      data.path.add(dis.readUTF());
    }

    return data;
  }
}
