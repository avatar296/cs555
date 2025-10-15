package csx55.pastry.node.peer;

import csx55.pastry.transport.Message;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

public class PeerServer {
  private static final Logger logger = Logger.getLogger(PeerServer.class.getName());

  private MessageHandler messageHandler;
  private ServerSocket serverSocket;
  private Thread serverThread;
  private volatile boolean running;
  private String host;
  private int port;

  public PeerServer(MessageHandler messageHandler) {
    this.messageHandler = messageHandler;
    this.running = true;
  }

  public void setMessageHandler(MessageHandler messageHandler) {
    this.messageHandler = messageHandler;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(0); // random port
    port = serverSocket.getLocalPort();
    host = InetAddress.getLoopbackAddress().getHostAddress();

    serverThread = new Thread(this::run);
    serverThread.setDaemon(false);
    serverThread.start();

    logger.info("Peer server started on " + host + ":" + port);
  }

  public void stop() {
    running = false;
    try {
      if (serverSocket != null) {
        serverSocket.close();
      }
    } catch (IOException e) {
      logger.warning("Error closing server socket: " + e.getMessage());
    }
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  private void run() {
    try {
      while (running) {
        try {
          Socket clientSocket = serverSocket.accept();
          Thread handler = new Thread(() -> handleConnection(clientSocket));
          handler.start();
        } catch (IOException e) {
          if (running) {
            logger.warning("Error accepting peer connection: " + e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      logger.severe("Peer server error: " + e.getMessage());
    }
  }

  private void handleConnection(Socket socket) {
    try (DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

      Message request = Message.read(dis);
      messageHandler.handleMessage(request, dos);

    } catch (IOException e) {
      logger.warning("Error handling peer connection: " + e.getMessage());
    } finally {
      try {
        socket.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }
}
