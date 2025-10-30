package csx55.dfs.replication;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.base.BaseController;
import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.NetworkUtils;

public class Controller extends BaseController {

    private final Map<String, List<String>> chunkLocations;

    public Controller(int port) {
        super(port);
        this.chunkLocations = new ConcurrentHashMap<>();
    }

    @Override
    protected String getControllerType() {
        return "Controller";
    }

    @Override
    protected int getReplicationFactor() {
        return 3;
    }

    @Override
    protected void updateLocationsFromHeartbeat(
            String serverId, List<HeartbeatMessage.ChunkInfo> chunks, MessageType type) {
        if (type == MessageType.MAJOR_HEARTBEAT) {
            updateChunkLocationsFromMajorHeartbeat(serverId, chunks);
        } else {
            updateChunkLocationsFromMinorHeartbeat(serverId, chunks);
        }
    }

    @Override
    protected Set<String> getLocationKeys() {
        return chunkLocations.keySet();
    }

    @Override
    protected void handleAdditionalMessages(Message message, TCPConnection connection)
            throws Exception {
        if (message.getType() == MessageType.REQUEST_CHUNK_SERVER_FOR_READ) {
            processChunkServerForReadRequest((ChunkServerForReadRequest) message, connection);
        } else {
            System.err.println("Unknown message type: " + message.getType());
        }
    }

    private void updateChunkLocationsFromMajorHeartbeat(
            String serverId, List<HeartbeatMessage.ChunkInfo> chunks) {
        for (Map.Entry<String, List<String>> entry : chunkLocations.entrySet()) {
            entry.getValue().remove(serverId);
        }

        for (HeartbeatMessage.ChunkInfo chunk : chunks) {
            String key = chunk.filename + ":" + chunk.chunkNumber;
            chunkLocations.computeIfAbsent(key, k -> new ArrayList<>()).add(serverId);
        }
    }

    private void updateChunkLocationsFromMinorHeartbeat(
            String serverId, List<HeartbeatMessage.ChunkInfo> chunks) {
        for (HeartbeatMessage.ChunkInfo chunk : chunks) {
            String key = chunk.filename + ":" + chunk.chunkNumber;
            List<String> servers = chunkLocations.computeIfAbsent(key, k -> new ArrayList<>());
            if (!servers.contains(serverId)) {
                servers.add(serverId);
            }
        }
    }

    public String getChunkServerForRead(String filename, int chunkNumber) {
        String key = filename + ":" + chunkNumber;
        List<String> servers = chunkLocations.get(key);
        if (servers == null || servers.isEmpty()) {
            return null;
        }
        Random random = new Random();
        return servers.get(random.nextInt(servers.size()));
    }

    private void processChunkServerForReadRequest(
            ChunkServerForReadRequest request, TCPConnection connection) throws IOException {
        String server = getChunkServerForRead(request.getFilename(), request.getChunkNumber());
        ChunkServerForReadResponse response = new ChunkServerForReadResponse(server);
        connection.sendMessage(response);
    }

    @Override
    protected List<String> findAffectedChunks(String failedServerId) {
        List<String> affectedChunks = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : chunkLocations.entrySet()) {
            String chunkKey = entry.getKey();
            List<String> servers = entry.getValue();

            if (servers.contains(failedServerId)) {
                affectedChunks.add(chunkKey);
            }
        }

        return affectedChunks;
    }

    @Override
    protected void recoverChunk(String chunkKey, String failedServerId) {
        String[] parts = chunkKey.split(":");
        String filename = parts[0];
        int chunkNumber = Integer.parseInt(parts[1]);

        List<String> remainingServers = chunkLocations.get(chunkKey);
        remainingServers.remove(failedServerId);

        if (remainingServers.isEmpty()) {
            System.err.println("ERROR: No remaining replicas for " + chunkKey);
            return;
        }

        String newServer = selectNewServerForRecovery(remainingServers);

        if (newServer == null) {
            System.err.println("ERROR: Cannot find new server for " + chunkKey);
            return;
        }

        String sourceServer = remainingServers.get(0);

        try {
            ReplicateChunkRequest request =
                    new ReplicateChunkRequest(filename, chunkNumber, newServer);

            Message response = NetworkUtils.sendRequestToServer(sourceServer, request);
            ReplicateChunkResponse replicateResponse = (ReplicateChunkResponse) response;

            if (replicateResponse.isSuccess()) {
                remainingServers.add(newServer);
                System.out.println(
                        "Successfully replicated "
                                + chunkKey
                                + " from "
                                + sourceServer
                                + " to "
                                + newServer);
            } else {
                System.err.println(
                        "Failed to replicate "
                                + chunkKey
                                + ": "
                                + replicateResponse.getErrorMessage());
            }
        } catch (Exception e) {
            System.err.println("Error replicating " + chunkKey + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java csx55.dfs.replication.Controller <port>");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[0]);
            Controller controller = new Controller(port);
            controller.start();
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid integer");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error starting controller: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
