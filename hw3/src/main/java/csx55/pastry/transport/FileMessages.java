package csx55.pastry.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class FileMessages {

  public static class StoreFileData {
    public String filename;
    public byte[] fileData;
  }

  public static class FileDataResponse {
    public boolean success;
    public String filename;
    public byte[] fileData;
  }

  public static class AckData {
    public boolean success;
    public String message;
  }

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

  public static Message createAck(boolean success, String message) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    dos.writeBoolean(success);
    dos.writeUTF(message != null ? message : "");

    dos.flush();
    return new Message(MessageType.ACK, baos.toByteArray());
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
