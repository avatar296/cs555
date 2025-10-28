/* CS555 Distributed Systems - HW4 */
package csx55.dfs.transport;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TCP Server that accepts connections and handles them with a connection handler */
public class TCPServerThread extends Thread {

    private final ServerSocket serverSocket;
    private final ConnectionHandler handler;
    private final ExecutorService threadPool;
    private volatile boolean running = true;

    public TCPServerThread(ServerSocket serverSocket, ConnectionHandler handler) {
        this.serverSocket = serverSocket;
        this.handler = handler;
        this.threadPool = Executors.newCachedThreadPool();
        setDaemon(true);
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(
                        () -> {
                            try {
                                handler.handleConnection(new TCPConnection(clientSocket));
                            } catch (Exception e) {
                                System.err.println("Error handling connection: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    public void shutdown() {
        running = false;
        threadPool.shutdown();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Interface for handling connections */
    public interface ConnectionHandler {
        void handleConnection(TCPConnection connection) throws Exception;
    }
}
