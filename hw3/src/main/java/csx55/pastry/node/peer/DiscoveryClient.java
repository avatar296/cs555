package csx55.pastry.node.peer;

import csx55.pastry.transport.Message;
import csx55.pastry.transport.MessageFactory;
import csx55.pastry.transport.MessageType;
import csx55.pastry.util.NodeInfo;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

public class DiscoveryClient {
  private static final Logger logger = Logger.getLogger(DiscoveryClient.class.getName());

  private final String discoverHost;
  private final int discoverPort;

  public DiscoveryClient(String discoverHost, int discoverPort) {
    this.discoverHost = discoverHost;
    this.discoverPort = discoverPort;
  }

  public void register(NodeInfo selfInfo) throws IOException {
    try (Socket socket = new Socket(discoverHost, discoverPort);
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message registerMsg = MessageFactory.createRegisterMessage(selfInfo);
      registerMsg.write(dos);

      Message response = Message.read(dis);
      if (response.getType() == MessageType.REGISTER_RESPONSE) {
        boolean success = MessageFactory.extractRegisterSuccess(response);
        if (success) {
          logger.info("Successfully registered with Discovery node");
        } else {
          logger.severe("Registration failed: ID collision detected");
          System.err.println(
              "Error: ID " + selfInfo.getId() + " is already in use. Please choose another ID.");
          System.exit(1);
        }
      }
    }
  }

  public void deregister(String id) {
    try (Socket socket = new Socket(discoverHost, discoverPort);
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message deregisterMsg = MessageFactory.createDeregisterMessage(id);
      deregisterMsg.write(dos);
      logger.info("Deregistered from Discovery node");

    } catch (IOException e) {
      logger.warning("Failed to deregister: " + e.getMessage());
    }
  }

  public NodeInfo getRandomNode(String excludeId) throws IOException {
    try (Socket socket = new Socket(discoverHost, discoverPort);
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message request = MessageFactory.createGetRandomNodeRequest(excludeId);
      request.write(dos);

      Message response = Message.read(dis);
      return MessageFactory.extractRandomNode(response);
    }
  }
}
