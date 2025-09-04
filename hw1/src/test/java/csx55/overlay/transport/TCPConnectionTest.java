package csx55.overlay.transport;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import csx55.overlay.wireformats.Event;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for TCPConnection focusing on connection stability and resource management.
 * These tests address the critical autograder issues with node disconnections.
 */
public class TCPConnectionTest {

  /**
   * Test that socket resources are properly cleaned up when connection creation fails.
   * This addresses the resource leak issue that caused nodes to disappear during testing.
   */
  @Test
  @Timeout(5)
  public void testSocketCleanupOnConnectionFailure() throws Exception {
    AtomicReference<Exception> connectionError = new AtomicReference<>();
    AtomicBoolean listenerCalled = new AtomicBoolean(false);

    TCPConnection.TCPConnectionListener listener =
        new TCPConnection.TCPConnectionListener() {
          @Override
          public void onEvent(Event event, TCPConnection connection) {
            listenerCalled.set(true);
          }

          @Override
          public void onConnectionLost(TCPConnection connection) {
            listenerCalled.set(true);
          }
        };

    // Create a socket that will be closed immediately to simulate connection failure
    Socket socket = new Socket();
    socket.close();

    // Attempt to create TCPConnection with closed socket - should fail cleanly
    assertThatThrownBy(() -> new TCPConnection(socket, listener))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Socket");

    // Verify no listener methods were called due to clean failure
    assertThat(listenerCalled.get()).isFalse();
  }

  /**
   * Test successful connection establishment and proper cleanup.
   * Verifies connections can be established and closed without resource leaks.
   */
  @Test
  @Timeout(10)
  public void testSuccessfulConnectionAndCleanup() throws Exception {
    CountDownLatch connectionLatch = new CountDownLatch(1);
    AtomicBoolean eventReceived = new AtomicBoolean(false);

    TCPConnection.TCPConnectionListener listener =
        new TCPConnection.TCPConnectionListener() {
          @Override
          public void onEvent(Event event, TCPConnection connection) {
            eventReceived.set(true);
          }

          @Override
          public void onConnectionLost(TCPConnection connection) {
            connectionLatch.countDown();
          }
        };

    // Create server socket for test
    ServerSocket serverSocket = new ServerSocket(0);
    int port = serverSocket.getLocalPort();

    // Create connection in separate thread to avoid blocking
    Thread serverThread =
        new Thread(
            () -> {
              try {
                Socket serverSideSocket = serverSocket.accept();
                new TCPConnection(serverSideSocket, listener);
              } catch (IOException e) {
                // Expected when connection is closed
              }
            });
    serverThread.start();

    // Create client connection
    Socket clientSocket = new Socket("localhost", port);
    TCPConnection clientConnection = new TCPConnection(clientSocket, listener);

    // Verify connection is established
    assertThat(clientConnection.isConnected()).isTrue();

    // Close connection and verify cleanup
    clientConnection.close();
    serverSocket.close();

    // Wait for connection lost callback
    boolean connectionLost = connectionLatch.await(3, TimeUnit.SECONDS);
    assertThat(connectionLost).isTrue();

    serverThread.interrupt();
    serverThread.join(1000);
  }

  /**
   * Test multiple connection attempts to verify stability.
   * This addresses the issue where nodes would disappear after multiple connection cycles.
   */
  @Test
  @Timeout(15)
  public void testMultipleConnectionCycles() throws Exception {
    final int connectionCycles = 5;
    
    for (int i = 0; i < connectionCycles; i++) {
      ServerSocket serverSocket = new ServerSocket(0);
      int port = serverSocket.getLocalPort();

      TCPConnection.TCPConnectionListener listener =
          new TCPConnection.TCPConnectionListener() {
            @Override
            public void onEvent(Event event, TCPConnection connection) {}

            @Override
            public void onConnectionLost(TCPConnection connection) {}
          };

      // Create and immediately close connections to test stability
      Thread serverThread =
          new Thread(
              () -> {
                try {
                  Socket serverSideSocket = serverSocket.accept();
                  TCPConnection serverConnection = new TCPConnection(serverSideSocket, listener);
                  Thread.sleep(100); // Brief delay
                  serverConnection.close();
                } catch (Exception e) {
                  // Expected during cleanup
                }
              });
      serverThread.start();

      Socket clientSocket = new Socket("localhost", port);
      TCPConnection clientConnection = new TCPConnection(clientSocket, listener);

      assertThat(clientConnection.isConnected()).isTrue();

      clientConnection.close();
      serverSocket.close();
      serverThread.interrupt();
      serverThread.join(1000);
    }
  }

  /**
   * Test that connection state is correctly maintained.
   * Verifies isConnected() method accuracy for connection stability.
   */
  @Test
  @Timeout(5)
  public void testConnectionStateAccuracy() throws Exception {
    ServerSocket serverSocket = new ServerSocket(0);
    int port = serverSocket.getLocalPort();

    TCPConnection.TCPConnectionListener listener =
        new TCPConnection.TCPConnectionListener() {
          @Override
          public void onEvent(Event event, TCPConnection connection) {}

          @Override
          public void onConnectionLost(TCPConnection connection) {}
        };

    Thread serverThread =
        new Thread(
            () -> {
              try {
                Socket serverSideSocket = serverSocket.accept();
                new TCPConnection(serverSideSocket, listener);
                Thread.sleep(200);
              } catch (Exception e) {
                // Expected during cleanup
              }
            });
    serverThread.start();

    Socket clientSocket = new Socket("localhost", port);
    TCPConnection connection = new TCPConnection(clientSocket, listener);

    // Verify initial state
    assertThat(connection.isConnected()).isTrue();

    // Close and verify state change  
    connection.close();
    assertThat(connection.isConnected()).isFalse();

    serverSocket.close();
    serverThread.interrupt();
    serverThread.join(1000);
  }
}