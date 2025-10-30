/* CS555 Distributed Systems - HW4 */
package csx55.dfs.base;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.DFSConfig;

/**
 * Abstract base class for ChunkServer implementations. Provides common functionality for both
 * erasure coding and replication modes.
 */
public abstract class BaseChunkServer {

    protected final String controllerHost;
    protected final int controllerPort;
    protected String storageRoot;
    protected ServerSocket serverSocket;
    protected String serverId;
    protected volatile boolean running = true;

    public BaseChunkServer(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
    }

    /** Returns the type of chunk server for logging purposes. */
    protected abstract String getServerType();

    /** Handles incoming message connections. Subclasses implement message dispatching. */
    protected abstract void handleConnection(Socket socket);

    /** Sends a major heartbeat with all chunk/fragment metadata. */
    protected abstract void sendMajorHeartbeat() throws IOException;

    /** Sends a minor heartbeat with only new chunk/fragment metadata. */
    protected abstract void sendMinorHeartbeat() throws IOException;

    /** Returns the number of chunks/fragments stored. */
    protected abstract int getStoredCount();

    /** Starts the chunk server. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        storageRoot = "/tmp/chunk-server-" + port;
        Files.createDirectories(Paths.get(storageRoot));

        String hostname = java.net.InetAddress.getLocalHost().getHostName();
        serverId = hostname + ":" + port;

        System.out.println(getServerType() + " started: " + serverId);
        System.out.println("Storage directory: " + storageRoot);
        System.out.println("Connected to Controller: " + controllerHost + ":" + controllerPort);

        startHeartbeatThreads();

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleConnection(clientSocket)).start();
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** Starts the heartbeat threads (minor: 15s, major: 60s). */
    protected void startHeartbeatThreads() {
        Thread minorHeartbeat =
                new Thread(
                        () -> {
                            while (running) {
                                try {
                                    Thread.sleep(DFSConfig.MINOR_HEARTBEAT_INTERVAL);
                                    sendMinorHeartbeat();
                                } catch (InterruptedException e) {
                                    break;
                                } catch (Exception e) {
                                    System.err.println(
                                            "Error sending minor heartbeat: " + e.getMessage());
                                }
                            }
                        });
        minorHeartbeat.setDaemon(true);
        minorHeartbeat.start();

        Thread majorHeartbeat =
                new Thread(
                        () -> {
                            while (running) {
                                try {
                                    Thread.sleep(DFSConfig.MAJOR_HEARTBEAT_INTERVAL);
                                    sendMajorHeartbeat();
                                } catch (InterruptedException e) {
                                    break;
                                } catch (Exception e) {
                                    System.err.println(
                                            "Error sending major heartbeat: " + e.getMessage());
                                }
                            }
                        });
        majorHeartbeat.setDaemon(true);
        majorHeartbeat.start();
    }

    /** Sends a heartbeat message to the controller. */
    protected void sendHeartbeatToController(HeartbeatMessage heartbeat) throws IOException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(heartbeat);
            connection.receiveMessage(); // Wait for ack
        } catch (Exception e) {
            System.err.println("Error sending heartbeat: " + e.getMessage());
        }
    }

    /** Returns free space available for storage. */
    protected long getFreeSpace() throws IOException {
        long totalSpace = 1024L * 1024L * 1024L; // 1GB
        long usedSpace = 0;

        // Subclasses can override to calculate actual usage
        usedSpace = calculateUsedSpace();

        return totalSpace - usedSpace;
    }

    /** Calculates used space. Subclasses implement based on their storage structure. */
    protected abstract long calculateUsedSpace();

    /** Normalizes a filename by removing leading slashes. */
    protected String normalizeFilename(String filename) {
        if (filename.startsWith("/")) {
            return filename.substring(1);
        }
        return filename;
    }

    /** Gets the storage path for a chunk/fragment. */
    protected Path getStoragePath(String... components) {
        return Paths.get(storageRoot, components);
    }
}
