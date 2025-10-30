package csx55.dfs.base;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.ServerInfo;

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

    protected abstract String getControllerType();

    protected abstract int getReplicationFactor();

    protected abstract void updateLocationsFromHeartbeat(
            String serverId, List<HeartbeatMessage.ChunkInfo> chunks, MessageType type);

    protected void initiateRecovery(String failedServerId) {
        System.out.println("Initiating recovery for failed server: " + failedServerId);

        List<String> affectedChunks = findAffectedChunks(failedServerId);

        System.out.println("Found " + affectedChunks.size() + " chunks affected by failure");

        for (String chunkKey : affectedChunks) {
            recoverChunk(chunkKey, failedServerId);
        }
    }

    protected abstract List<String> findAffectedChunks(String failedServerId);

    protected abstract void recoverChunk(String chunkKey, String failedServerId);

    protected abstract Set<String> getLocationKeys();

    protected abstract void handleAdditionalMessages(Message message, TCPConnection connection)
            throws Exception;

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
                    handleAdditionalMessages(message, connection);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error handling connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

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

        updateLocationsFromHeartbeat(serverId, heartbeat.getChunks(), heartbeat.getType());

        HeartbeatResponse response = new HeartbeatResponse();
        connection.sendMessage(response);
    }

    protected void processChunkServersRequest(ChunkServersRequest request, TCPConnection connection)
            throws IOException {
        List<String> servers =
                selectChunkServersForWrite(request.getFilename(), request.getChunkNumber());
        ChunkServersResponse response = new ChunkServersResponse(servers);
        connection.sendMessage(response);
    }

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

    protected String selectNewServerForRecovery(Collection<String> existingServers) {
        List<ServerInfo> availableServers = new ArrayList<>(chunkServers.values());

        availableServers.removeIf(server -> existingServers.contains(server.serverId));

        if (availableServers.isEmpty()) {
            return null;
        }

        availableServers.sort((a, b) -> Long.compare(b.freeSpace, a.freeSpace));

        return availableServers.get(0).serverId;
    }

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
