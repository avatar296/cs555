package csx55.pastry.node.peer;

import csx55.pastry.routing.LeafSet;
import csx55.pastry.routing.RoutingTable;
import csx55.pastry.transport.FileMessages;
import csx55.pastry.transport.JoinMessages;
import csx55.pastry.transport.LookupMessages;
import csx55.pastry.transport.Message;
import csx55.pastry.transport.MessageFactory;
import csx55.pastry.transport.MessageType;
import csx55.pastry.util.NodeInfo;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

public class MessageHandler {
  private static final Logger logger = Logger.getLogger(MessageHandler.class.getName());

  private final NodeInfo selfInfo;
  private final LeafSet leafSet;
  private final RoutingTable routingTable;
  private final RoutingEngine routingEngine;
  private final FileStorageManager fileStorage;
  private final PeerStatistics statistics;

  public MessageHandler(
      NodeInfo selfInfo,
      LeafSet leafSet,
      RoutingTable routingTable,
      RoutingEngine routingEngine,
      FileStorageManager fileStorage,
      PeerStatistics statistics) {
    this.selfInfo = selfInfo;
    this.leafSet = leafSet;
    this.routingTable = routingTable;
    this.routingEngine = routingEngine;
    this.fileStorage = fileStorage;
    this.statistics = statistics;
  }

  public void handleMessage(Message request, DataOutputStream dos) throws IOException {
    switch (request.getType()) {
      case JOIN_REQUEST:
        handleJoinRequest(request, dos);
        break;
      case JOIN_RESPONSE:
        handleJoinResponse(request);
        break;
      case ROUTING_TABLE_UPDATE:
        handleRoutingTableUpdate(request);
        break;
      case LEAF_SET_UPDATE:
        handleLeafSetUpdate(request);
        break;
      case LOOKUP:
        handleLookup(request);
        break;
      case STORE_FILE:
        handleStoreFile(request, dos);
        break;
      case RETRIEVE_FILE:
        handleRetrieveFile(request, dos);
        break;
      default:
        logger.warning("Unhandled message type: " + request.getType());
    }
  }

  private void handleJoinRequest(Message request, DataOutputStream dos) throws IOException {
    statistics.incrementJoinRequestsHandled();

    JoinMessages.JoinRequestData data = MessageFactory.extractJoinRequest(request);

    int newHopCount = data.hopCount + 1;
    data.path.add(selfInfo.getId());

    logger.info(
        "JOIN request for "
            + data.destination
            + " (hop "
            + newHopCount
            + ", from "
            + data.requester.getId()
            + ")");

    boolean isDestination = routingEngine.isClosestNode(data.destination);

    if (isDestination) {
      NodeInfo left = leafSet.getLeft();
      NodeInfo right = leafSet.getRight();
      sendJoinResponse(data.requester, -1, new NodeInfo[0], left, right);

      leafSet.addNode(data.requester);
      sendUpdateToNode(data.requester, MessageType.LEAF_SET_UPDATE);

    } else {
      int prefixLen = RoutingEngine.getCommonPrefixLength(selfInfo.getId(), data.destination);
      NodeInfo[] row = routingTable.getRow(prefixLen);

      sendJoinResponse(data.requester, prefixLen, row, null, null);

      routingTable.addNode(data.requester);
      sendUpdateToNode(data.requester, MessageType.ROUTING_TABLE_UPDATE);

      NodeInfo nextHop = routingEngine.route(data.destination);

      if (nextHop != null && nextHop.getId().equals(data.requester.getId())) {
        NodeInfo left = leafSet.getLeft();
        NodeInfo right = leafSet.getRight();
        sendJoinResponse(data.requester, -1, new NodeInfo[0], left, right);
        leafSet.addNode(data.requester);
        sendUpdateToNode(data.requester, MessageType.LEAF_SET_UPDATE);
      } else if (nextHop != null) {
        forwardJoinRequest(data.requester, data.destination, newHopCount, data.path, nextHop);
      } else {
        logger.warning("No next hop found for " + data.destination);
      }
    }
  }

  private void sendJoinResponse(
      NodeInfo requester, int rowNum, NodeInfo[] row, NodeInfo left, NodeInfo right) {
    try (Socket socket = new Socket(requester.getHost(), requester.getPort());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message response = MessageFactory.createJoinResponse(rowNum, row, left, right);
      response.write(dos);

      if (rowNum == -1) {
        logger.info(
            "Sent JOIN_RESPONSE to "
                + requester.getId()
                + " with leaf set (left: "
                + (left != null ? left.getId() : "null")
                + ", right: "
                + (right != null ? right.getId() : "null")
                + ")");
      } else {
        logger.info(
            "Sent JOIN_RESPONSE to "
                + requester.getId()
                + " with routing table row "
                + rowNum
                + " (entries: "
                + (row != null ? row.length : 0)
                + ")");
      }

    } catch (IOException e) {
      logger.warning(
          "Failed to send JOIN_RESPONSE to " + requester.getId() + ": " + e.getMessage());
    }
  }

  private void forwardJoinRequest(
      NodeInfo requester,
      String destination,
      int hopCount,
      java.util.List<String> path,
      NodeInfo nextHop) {
    try (Socket socket = new Socket(nextHop.getHost(), nextHop.getPort());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message joinMsg = MessageFactory.createJoinRequest(requester, destination, hopCount, path);
      joinMsg.write(dos);
    } catch (IOException e) {
      logger.warning("Failed to forward JOIN: " + e.getMessage());
    }
  }

  private void handleJoinResponse(Message request) throws IOException {
    JoinMessages.JoinResponseData data = MessageFactory.extractJoinResponse(request);

    if (data.rowNum == -1) {
      if (data.left != null) {
        leafSet.addNode(data.left);
      }
      if (data.right != null) {
        leafSet.addNode(data.right);
      }
    } else {
      routingTable.setRow(data.rowNum, data.routingRow);

      for (NodeInfo node : data.routingRow) {
        if (node != null) {
          leafSet.addNode(node);
        }
      }
    }
  }

  private void handleRoutingTableUpdate(Message request) throws IOException {
    statistics.incrementRoutingTableUpdates();
    NodeInfo node = MessageFactory.extractNodeInfo(request);
    routingTable.addNode(node);
  }

  private void handleLeafSetUpdate(Message request) throws IOException {
    statistics.incrementLeafSetUpdates();
    NodeInfo node = MessageFactory.extractNodeInfo(request);
    leafSet.addNode(node);
  }

  private void handleLookup(Message request) throws IOException {
    statistics.incrementLookupsHandled();

    LookupMessages.LookupRequestData data = MessageFactory.extractLookupRequest(request);

    data.path.add(selfInfo.getId());

    logger.info("LOOKUP for " + data.targetId + " (hop " + data.path.size() + ")");

    boolean isDestination = routingEngine.isClosestNode(data.targetId);

    if (isDestination) {
      try (Socket socket = new Socket(data.origin.getHost(), data.origin.getPort());
          DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

        Message response = MessageFactory.createLookupResponse(data.targetId, selfInfo, data.path);
        response.write(dos);
      }
    } else {
      NodeInfo nextHop = routingEngine.route(data.targetId);

      if (nextHop != null) {
        try (Socket socket = new Socket(nextHop.getHost(), nextHop.getPort());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

          Message forwardMsg =
              MessageFactory.createLookupRequest(data.targetId, data.origin, data.path);
          forwardMsg.write(dos);
        }
      } else {
        logger.warning("No next hop found for LOOKUP " + data.targetId);
      }
    }
  }

  private void handleStoreFile(Message request, DataOutputStream dos) throws IOException {
    FileMessages.StoreFileData data = MessageFactory.extractStoreFileRequest(request);

    logger.info("Storing file: " + data.filename + " (" + data.fileData.length + " bytes)");

    try {
      fileStorage.storeFile(data.filename, data.fileData);

      Message ack = MessageFactory.createAck(true, "File stored successfully");
      ack.write(dos);

    } catch (IOException e) {
      logger.warning("Failed to store file " + data.filename + ": " + e.getMessage());

      Message ack = MessageFactory.createAck(false, "Failed to store file: " + e.getMessage());
      ack.write(dos);
    }
  }

  private void handleRetrieveFile(Message request, DataOutputStream dos) throws IOException {
    String filename = MessageFactory.extractRetrieveFileRequest(request);

    logger.info("Retrieving file: " + filename);

    try {
      byte[] fileData = fileStorage.retrieveFile(filename);

      if (fileData == null) {
        logger.warning("File not found: " + filename);
        Message response = MessageFactory.createFileDataResponse(filename, null, false);
        response.write(dos);
        return;
      }

      logger.info("Successfully retrieved file: " + filename + " (" + fileData.length + " bytes)");

      Message response = MessageFactory.createFileDataResponse(filename, fileData, true);
      response.write(dos);

    } catch (IOException e) {
      logger.warning("Failed to retrieve file " + filename + ": " + e.getMessage());

      Message response = MessageFactory.createFileDataResponse(filename, null, false);
      response.write(dos);
    }
  }

  private void sendUpdateToNode(NodeInfo node, MessageType updateType) {
    if (node.getId().equals(selfInfo.getId())) {
      return;
    }

    try (Socket socket = new Socket(node.getHost(), node.getPort());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message updateMsg =
          updateType == MessageType.ROUTING_TABLE_UPDATE
              ? MessageFactory.createRoutingTableUpdate(selfInfo)
              : MessageFactory.createLeafSetUpdate(selfInfo);

      updateMsg.write(dos);
    } catch (IOException e) {
      logger.warning("Failed to send update: " + e.getMessage());
    }
  }
}
