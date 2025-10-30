/* CS555 Distributed Systems - HW4 */
package csx55.dfs.base;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.ServerInfo;

/**
 * Abstract base class for Controller implementations. Provides common functionality for both
 * erasure coding and replication modes.
 */
public abstract class BaseController {

    protected final int port;
    protected ServerSocket serverSocket;
    protected final Map<String, ServerInfo> chunkServers;
    protected final Map<String, Long> lastHeartbeat;
    protected volatile boolean running = true;

    public BaseController(int port) {
        this.port = port;
        this.chunkServers = new ConcurrentHashMap<>();
        this.lastHeartbeat = new ConcurrentHashMap<>();
    }

    /** Returns the type of controller for logging purposes. */
    protected abstract String getControllerType();

    /** Returns the replication factor (9 for erasure, 3 for replication). */
    protected abstract int getReplicationFactor();

    /**
     * Updates location tracking from heartbeat messages. Erasure: updates fragment map Replication:
     * updates chunk list
     */
    protected abstract void updateLocationsFromHeartbeat(
            String serverId, List<HeartbeatMessage.ChunkInfo> chunks, MessageType type);

    /**
     * Initiates recovery for a failed server. Erasure: reconstructs missing fragments Replication:
     * replicates chunks to new servers
     */
    protected abstract void initiateRecovery(String failedServerId);

    /** Returns all location keys (chunk/fragment keys) for file info queries. */
    protected abstract Set<String> getLocationKeys();

    /** Handles additional message types specific to the controller mode. */
    protected abstract void handleAdditionalMessages(Message message, TCPConnection connection)
            throws Exception;

    /** Starts the controller server. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println(getControllerType() + " started on port " + port);

        startFailureDetectionThread();

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

    /** Handles incoming connections and dispatches messages. */
    private void handleConnection(Socket socket) {
        try (TCPConnection connection = new TCPConnection(socket)) {
            Message message = connection.receiveMessage();

            switch (message.getType()) {
                case MAJOR_HEARTBEAT:
                case MINOR_HEARTBEAT:
                    processHeartbeat((HeartbeatMessage) message, connection);
                    break;

                case REQUEST_CHUNK_SERVERS_FOR_WRITE:
                    processChunkServersRequest((ChunkServersRequest) message, connection);
                    break;

                case REQUEST_FILE_INFO:
                    processFileInfoRequest((FileInfoRequest) message, connection);
                    break;

                default:
                    // Let subclass handle additional message types
                    handleAdditionalMessages(message, connection);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error handling connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Processes heartbeat messages from chunk servers. */
    protected void processHeartbeat(HeartbeatMessage heartbeat, TCPConnection connection)
            throws IOException {
        String serverId = heartbeat.getChunkServerId();

        ServerInfo serverInfo = chunkServers.get(serverId);
        if (serverInfo == null) {
            serverInfo = new ServerInfo(serverId);
            chunkServers.put(serverId, serverInfo);
            System.out.println("New chunk server registered: " + serverId);
        }

        serverInfo.freeSpace = heartbeat.getFreeSpace();
        serverInfo.count = heartbeat.getTotalChunks();
        lastHeartbeat.put(serverId, System.currentTimeMillis());

        // Delegate location updates to subclass
        updateLocationsFromHeartbeat(serverId, heartbeat.getChunks(), heartbeat.getType());

        HeartbeatResponse response = new HeartbeatResponse();
        connection.sendMessage(response);
    }

    /** Processes requests for chunk servers to write to. */
    protected void processChunkServersRequest(ChunkServersRequest request, TCPConnection connection)
            throws IOException {
        List<String> servers =
                selectChunkServersForWrite(request.getFilename(), request.getChunkNumber());
        ChunkServersResponse response = new ChunkServersResponse(servers);
        connection.sendMessage(response);
    }

    /** Processes requests for file metadata (number of chunks). */
    protected void processFileInfoRequest(FileInfoRequest request, TCPConnection connection)
            throws IOException {
        String filename = request.getFilename();
        int maxChunk = 0;

        for (String key : getLocationKeys()) {
            if (key.startsWith(filename + ":")) {
                String[] parts = key.split(":");
                int chunkNum = Integer.parseInt(parts[1]);
                maxChunk = Math.max(maxChunk, chunkNum);
            }
        }

        FileInfoResponse response = new FileInfoResponse(maxChunk);
        connection.sendMessage(response);
    }

    /** Selects chunk servers for writing based on free space. */
    protected List<String> selectChunkServersForWrite(String filename, int chunkNumber) {
        List<ServerInfo> availableServers = new ArrayList<>(chunkServers.values());

        int requiredServers = getReplicationFactor();
        if (availableServers.size() < requiredServers) {
            System.err.println(
                    "Not enough chunk servers available. Need "
                            + requiredServers
                            + ", have "
                            + availableServers.size());
            return new ArrayList<>();
        }

        availableServers.sort((a, b) -> Long.compare(b.freeSpace, a.freeSpace));

        List<String> selected = new ArrayList<>();
        for (int i = 0; i < requiredServers && i < availableServers.size(); i++) {
            selected.add(availableServers.get(i).serverId);
        }

        System.out.println(
                "Selected chunk servers for "
                        + filename
                        + " chunk "
                        + chunkNumber
                        + ": "
                        + selected);
        return selected;
    }

    /** Selects a new server for recovery, excluding existing servers. */
    protected String selectNewServerForRecovery(Collection<String> existingServers) {
        List<ServerInfo> availableServers = new ArrayList<>(chunkServers.values());

        availableServers.removeIf(server -> existingServers.contains(server.serverId));

        if (availableServers.isEmpty()) {
            return null;
        }

        availableServers.sort((a, b) -> Long.compare(b.freeSpace, a.freeSpace));

        return availableServers.get(0).serverId;
    }

    /** Starts the failure detection thread. */
    private void startFailureDetectionThread() {
        Thread failureDetector =
                new Thread(
                        () -> {
                            while (running) {
                                try {
                                    Thread.sleep(10000);
                                    detectFailures();
                                } catch (InterruptedException e) {
                                    break;
                                }
                            }
                        });
        failureDetector.setDaemon(true);
        failureDetector.start();
    }

    /** Detects failed servers and initiates recovery. */
    private void detectFailures() {
        long currentTime = System.currentTimeMillis();
        long failureThreshold = 180000;

        List<String> failedServers = new ArrayList<>();

        for (Map.Entry<String, Long> entry : lastHeartbeat.entrySet()) {
            String serverId = entry.getKey();
            long lastTime = entry.getValue();

            if (currentTime - lastTime > failureThreshold) {
                failedServers.add(serverId);
            }
        }

        for (String failedServerId : failedServers) {
            System.err.println("FAILURE DETECTED: " + failedServerId);
            initiateRecovery(failedServerId);

            chunkServers.remove(failedServerId);
            lastHeartbeat.remove(failedServerId);
        }
    }
}
