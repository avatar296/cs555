package csx55.pastry.transport;

import csx55.pastry.util.NodeInfo;
import java.io.IOException;

public class MessageFactory {

  public static Message createRegisterMessage(NodeInfo nodeInfo) throws IOException {
    return RegistrationMessages.createRegisterMessage(nodeInfo);
  }

  public static NodeInfo extractNodeInfo(Message message) throws IOException {
    return RegistrationMessages.extractNodeInfo(message);
  }

  public static Message createRegisterResponse(boolean success, String message) throws IOException {
    return RegistrationMessages.createRegisterResponse(success, message);
  }

  public static boolean extractRegisterSuccess(Message message) throws IOException {
    return RegistrationMessages.extractRegisterSuccess(message);
  }

  public static Message createRandomNodeResponse(NodeInfo nodeInfo) throws IOException {
    return RegistrationMessages.createRandomNodeResponse(nodeInfo);
  }

  public static NodeInfo extractRandomNode(Message message) throws IOException {
    return RegistrationMessages.extractRandomNode(message);
  }

  public static Message createDeregisterMessage(String nodeId) throws IOException {
    return RegistrationMessages.createDeregisterMessage(nodeId);
  }

  public static String extractNodeId(Message message) throws IOException {
    return RegistrationMessages.extractNodeId(message);
  }

  public static Message createGetRandomNodeRequest(String excludeId) throws IOException {
    return RegistrationMessages.createGetRandomNodeRequest(excludeId);
  }

  public static String extractExcludeId(Message message) throws IOException {
    return RegistrationMessages.extractExcludeId(message);
  }

  public static Message createJoinRequest(
      NodeInfo requester, String destination, int hopCount, java.util.List<String> path)
      throws IOException {
    return JoinMessages.createJoinRequest(requester, destination, hopCount, path);
  }

  public static JoinMessages.JoinRequestData extractJoinRequest(Message message)
      throws IOException {
    return JoinMessages.extractJoinRequest(message);
  }

  public static Message createJoinResponse(
      int rowNum, NodeInfo[] routingRow, NodeInfo left, NodeInfo right) throws IOException {
    return JoinMessages.createJoinResponse(rowNum, routingRow, left, right);
  }

  public static JoinMessages.JoinResponseData extractJoinResponse(Message message)
      throws IOException {
    return JoinMessages.extractJoinResponse(message);
  }

  public static Message createRoutingTableUpdate(NodeInfo node) throws IOException {
    return UpdateMessages.createRoutingTableUpdate(node);
  }

  public static Message createLeafSetUpdate(NodeInfo node) throws IOException {
    return UpdateMessages.createLeafSetUpdate(node);
  }

  public static Message createLeaveNotification(NodeInfo node) throws IOException {
    return UpdateMessages.createLeaveNotification(node);
  }

  public static Message createLookupRequest(
      String targetId, NodeInfo origin, java.util.List<String> path) throws IOException {
    return LookupMessages.createLookupRequest(targetId, origin, path);
  }

  public static LookupMessages.LookupRequestData extractLookupRequest(Message message)
      throws IOException {
    return LookupMessages.extractLookupRequest(message);
  }

  public static Message createLookupResponse(
      String targetId, NodeInfo responsible, java.util.List<String> path) throws IOException {
    return LookupMessages.createLookupResponse(targetId, responsible, path);
  }

  public static LookupMessages.LookupResponseData extractLookupResponse(Message message)
      throws IOException {
    return LookupMessages.extractLookupResponse(message);
  }

  public static Message createStoreFileRequest(String filename, byte[] fileData)
      throws IOException {
    return FileMessages.createStoreFileRequest(filename, fileData);
  }

  public static FileMessages.StoreFileData extractStoreFileRequest(Message message)
      throws IOException {
    return FileMessages.extractStoreFileRequest(message);
  }

  public static Message createRetrieveFileRequest(String filename) throws IOException {
    return FileMessages.createRetrieveFileRequest(filename);
  }

  public static String extractRetrieveFileRequest(Message message) throws IOException {
    return FileMessages.extractRetrieveFileRequest(message);
  }

  public static Message createFileDataResponse(String filename, byte[] fileData, boolean success)
      throws IOException {
    return FileMessages.createFileDataResponse(filename, fileData, success);
  }

  public static FileMessages.FileDataResponse extractFileDataResponse(Message message)
      throws IOException {
    return FileMessages.extractFileDataResponse(message);
  }

  public static Message createAck(boolean success, String message) throws IOException {
    return FileMessages.createAck(success, message);
  }

  public static FileMessages.AckData extractAck(Message message) throws IOException {
    return FileMessages.extractAck(message);
  }
}
