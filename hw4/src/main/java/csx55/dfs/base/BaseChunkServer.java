package csx55.dfs.base;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.DFSConfig;

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

    protected abstract String getServerType();

    protected abstract void handleConnection(Socket socket);

    protected abstract int getStoredCount();

    protected abstract java.util.List<HeartbeatMessage.ChunkInfo> buildMajorHeartbeatChunks();

    protected abstract java.util.List<HeartbeatMessage.ChunkInfo> buildMinorHeartbeatChunks();

    protected void sendMajorHeartbeat() throws IOException {
        java.util.List<HeartbeatMessage.ChunkInfo> chunkList = buildMajorHeartbeatChunks();

        HeartbeatMessage heartbeat =
                new HeartbeatMessage(
                        MessageType.MAJOR_HEARTBEAT,
                        serverId,
                        getStoredCount(),
                        getFreeSpace(),
                        chunkList);

        sendHeartbeatToController(heartbeat);
    }

    protected void sendMinorHeartbeat() throws IOException {
        java.util.List<HeartbeatMessage.ChunkInfo> chunkList = buildMinorHeartbeatChunks();

        if (!chunkList.isEmpty()) {
            HeartbeatMessage heartbeat =
                    new HeartbeatMessage(
                            MessageType.MINOR_HEARTBEAT,
                            serverId,
                            getStoredCount(),
                            getFreeSpace(),
                            chunkList);

            sendHeartbeatToController(heartbeat);
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        if (System.getProperty("dfs.local.mode") != null) {
            storageRoot = "/tmp/chunk-server-" + port;
        } else {
            storageRoot = "/tmp/chunk_server";
        }
        Files.createDirectories(Paths.get(storageRoot));

        String ipAddress = java.net.InetAddress.getLocalHost().getHostAddress();
        serverId = ipAddress + ":" + port;

        System.out.println(getServerType() + " started: " + serverId);
        System.out.println("Storage directory: " + storageRoot);
        System.out.println("Connected to Controller: " + controllerHost + ":" + controllerPort);

        sendMajorHeartbeat();

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

    protected void sendHeartbeatToController(HeartbeatMessage heartbeat) throws IOException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(heartbeat);
            connection.receiveMessage();
        } catch (Exception e) {
            System.err.println("Error sending heartbeat: " + e.getMessage());
        }
    }

    protected long getFreeSpace() throws IOException {
        long totalSpace = 1024L * 1024L * 1024L;
        long usedSpace = 0;

        usedSpace = calculateUsedSpace();

        return totalSpace - usedSpace;
    }

    protected abstract long calculateUsedSpace();

    protected String normalizeFilename(String filename) {
        if (filename.startsWith("/")) {
            return filename.substring(1);
        }
        return filename;
    }

    protected Path getStoragePath(String... components) {
        return Paths.get(storageRoot, components);
    }
}
