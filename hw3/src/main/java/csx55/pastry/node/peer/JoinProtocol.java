package csx55.pastry.node.peer;

import csx55.pastry.routing.LeafSet;
import csx55.pastry.routing.RoutingTable;
import csx55.pastry.transport.Message;
import csx55.pastry.transport.MessageFactory;
import csx55.pastry.transport.MessageType;
import csx55.pastry.util.NodeInfo;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class JoinProtocol {
  private static final Logger logger = Logger.getLogger(JoinProtocol.class.getName());

  private final NodeInfo selfInfo;
  private final LeafSet leafSet;
  private final RoutingTable routingTable;
  private final DiscoveryClient discoveryClient;

  public JoinProtocol(
      NodeInfo selfInfo,
      LeafSet leafSet,
      RoutingTable routingTable,
      DiscoveryClient discoveryClient) {
    this.selfInfo = selfInfo;
    this.leafSet = leafSet;
    this.routingTable = routingTable;
    this.discoveryClient = discoveryClient;
  }

  public void joinNetwork() throws IOException {
    NodeInfo entryPoint = discoveryClient.getRandomNode(selfInfo.getId());

    if (entryPoint == null) {
      logger.info("We are the first peer in the network");
      return;
    }

    logger.info("Joining network via entry point: " + entryPoint.getId());

    try (Socket socket = new Socket(entryPoint.getHost(), entryPoint.getPort());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      List<String> path = new ArrayList<>();
      Message joinMsg = MessageFactory.createJoinRequest(selfInfo, selfInfo.getId(), 0, path);
      joinMsg.write(dos);
    }

    // Brief delay to allow JOIN responses to arrive
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    sendUpdatesToNetwork();

    logger.info("Join protocol complete");
  }

  public void sendUpdatesToNetwork() {
    // Send ROUTING_TABLE_UPDATE to all routing table entries
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          sendUpdateToNode(node, MessageType.ROUTING_TABLE_UPDATE);
        }
      }
    }

    // Send LEAF_SET_UPDATE to leaf set neighbors
    for (NodeInfo node : leafSet.getAllNodes()) {
      sendUpdateToNode(node, MessageType.LEAF_SET_UPDATE);
    }

    // ALSO send LEAF_SET_UPDATE to all routing table entries
    // This ensures broader propagation and helps nodes discover closer neighbors
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 16; col++) {
        NodeInfo node = routingTable.getEntry(row, col);
        if (node != null) {
          sendUpdateToNode(node, MessageType.LEAF_SET_UPDATE);
        }
      }
    }
  }

  public void sendUpdateToNode(NodeInfo node, MessageType updateType) {
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
