package csx55.overlay.transport;

import csx55.overlay.util.LoggerUtil;
import csx55.overlay.wireformats.Event;
import csx55.overlay.wireformats.EventFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

/**
 * Manages a TCP connection between nodes in the overlay network. Handles bidirectional
 * communication with automatic event deserialization and connection lifecycle management.
 *
 * <p>This class provides thread-safe sending operations and uses a dedicated receiver thread for
 * handling incoming events. Connection loss is automatically detected and reported to the listener.
 */
public class TCPConnection {

  private final Socket socket;
  private final DataOutputStream dout;
  private final DataInputStream din;
  private final TCPConnectionListener listener;
  private volatile Thread receiverThread;
  private volatile String remoteNodeId;

  /**
   * Interface for handling TCP connection events. Implementations receive callbacks for incoming
   * events and connection loss.
   */
  public interface TCPConnectionListener {
    /**
     * Called when an event is received on this connection.
     *
     * @param event the received event
     * @param connection the connection that received the event
     */
    void onEvent(Event event, TCPConnection connection);

    /**
     * Called when this connection is lost.
     *
     * @param connection the connection that was lost
     */
    void onConnectionLost(TCPConnection connection);
  }

  /**
   * Creates a new TCP connection using an existing socket. Initializes streams and starts the
   * receiver thread. The remote node ID will be set later when identification is received.
   *
   * @param socket the socket for this connection
   * @param listener the listener for connection events
   * @throws IOException if stream initialization fails
   */
  public TCPConnection(Socket socket, TCPConnectionListener listener) throws IOException {
    this.socket = socket;
    this.listener = listener;
    DataOutputStream tempDout = null;
    DataInputStream tempDin = null;
    try {
      tempDout = new DataOutputStream(socket.getOutputStream());
      tempDin = new DataInputStream(socket.getInputStream());
    } catch (IOException e) {
      LoggerUtil.error("TCPConnection", "Failed to create streams for connection", e);
      if (tempDout != null) {
        try {
          tempDout.close();
        } catch (IOException ignored) {
        }
      }
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      throw e;
    }
    this.dout = tempDout;
    this.din = tempDin;
    this.remoteNodeId = null;

    startReceiver();
  }

  /**
   * Creates a new TCP connection to the specified host and port.
   *
   * @param host the hostname to connect to
   * @param port the port number to connect to
   * @param listener the listener for connection events
   * @throws IOException if connection fails
   */
  public TCPConnection(String host, int port, TCPConnectionListener listener) throws IOException {
    this(new Socket(host, port), listener);
  }

  /**
   * Starts the receiver thread that continuously reads incoming events. The thread runs until the
   * connection is closed or an error occurs.
   */
  private void startReceiver() {
    receiverThread =
        new Thread(
            () -> {
              EventFactory factory = EventFactory.getInstance();

              try {
                while (!socket.isClosed()) {
                  int dataLength = din.readInt();
                  byte[] data = new byte[dataLength];
                  din.readFully(data, 0, dataLength);

                  Event event = factory.createEvent(data);

                  if (listener != null) {
                    listener.onEvent(event, this);
                  }
                }
              } catch (SocketException e) {
                LoggerUtil.debug("TCPConnection", "Socket closed for " + remoteNodeId);
              } catch (IOException e) {
                LoggerUtil.error("TCPConnection", "Error reading from " + remoteNodeId, e);
              } finally {
                if (listener != null) {
                  listener.onConnectionLost(this);
                }
                closeQuietly();
              }
            });
    receiverThread.start();
  }

  /**
   * Sends an event through this connection. This method is synchronized to ensure thread-safe
   * sending.
   *
   * @param event the event to send
   * @throws IOException if sending fails or connection is not active
   */
  public synchronized void sendEvent(Event event) throws IOException {
    if (!isConnected()) {
      throw new IOException("Connection is not active");
    }
    try {
      byte[] marshalledBytes = event.getBytes();
      dout.writeInt(marshalledBytes.length);
      dout.write(marshalledBytes);
      dout.flush();
    } catch (IOException e) {
      LoggerUtil.error("TCPConnection", "Failed to send event to " + remoteNodeId, e);
      closeQuietly();
      throw e;
    }
  }

  /**
   * Gets the identifier of the remote node.
   *
   * @return the remote node ID, or null if not yet identified
   */
  public String getRemoteNodeId() {
    return remoteNodeId;
  }

  /**
   * Sets the identifier of the remote node.
   *
   * @param remoteNodeId the remote node identifier
   */
  public void setRemoteNodeId(String remoteNodeId) {
    this.remoteNodeId = remoteNodeId;
  }

  /** Closes this connection and releases all resources. */
  public synchronized void close() {
    closeQuietly();
  }

  /**
   * Closes all resources quietly, suppressing any exceptions. Used for cleanup in error conditions.
   */
  private synchronized void closeQuietly() {
    try {
      if (din != null) {
        din.close();
      }
    } catch (IOException e) {
      LoggerUtil.debug("TCPConnection", "Error closing input stream: " + e.getMessage());
    }

    try {
      if (dout != null) {
        dout.close();
      }
    } catch (IOException e) {
      LoggerUtil.debug("TCPConnection", "Error closing output stream: " + e.getMessage());
    }

    try {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    } catch (IOException e) {
      LoggerUtil.debug("TCPConnection", "Error closing socket: " + e.getMessage());
    }
  }

  /**
   * Checks if this connection is active.
   *
   * @return true if the connection is active, false otherwise
   */
  public boolean isConnected() {
    return socket != null && socket.isConnected() && !socket.isClosed();
  }

  /**
   * Gets the underlying socket for this connection.
   *
   * @return the socket
   */
  public Socket getSocket() {
    return socket;
  }
}
