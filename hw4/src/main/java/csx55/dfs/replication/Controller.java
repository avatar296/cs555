/* CS555 Distributed Systems - HW4 */
package csx55.dfs.replication;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.ServerInfo;

public class Controller {

    private final int port;
    private ServerSocket serverSocket;

    private final Map<String, ServerInfo> chunkServers;

    private final Map<String, List<String>> chunkLocations;

    private final Map<String, Long> lastHeartbeat;

    private volatile boolean running = true;

    public Controller(int port) {
        this.port = port;
        this.chunkServers = new ConcurrentHashMap<>();
        this.chunkLocations = new ConcurrentHashMap<>();
        this.lastHeartbeat = new ConcurrentHashMap<>();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Controller started on port " + port);

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

                case REQUEST_CHUNK_SERVER_FOR_READ:
                    processChunkServerForReadRequest(
                            (ChunkServerForReadRequest) message, connection);
                    break;

                default:
                    System.err.println("Unknown message type: " + message.getType());
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error handling connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processHeartbeat(HeartbeatMessage heartbeat, TCPConnection connection)
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

        if (heartbeat.getType() == MessageType.MAJOR_HEARTBEAT) {
            updateChunkLocationsFromMajorHeartbeat(serverId, heartbeat.getChunks());
        } else {
            updateChunkLocationsFromMinorHeartbeat(serverId, heartbeat.getChunks());
        }

        HeartbeatResponse response = new HeartbeatResponse();
        connection.sendMessage(response);
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

    private void processChunkServersRequest(ChunkServersRequest request, TCPConnection connection)
            throws IOException {
        List<String> servers =
                selectChunkServersForWrite(request.getFilename(), request.getChunkNumber());
        ChunkServersResponse response = new ChunkServersResponse(servers);
        connection.sendMessage(response);
    }

    public List<String> selectChunkServersForWrite(String filename, int chunkNumber) {
        List<ServerInfo> availableServers = new ArrayList<>(chunkServers.values());

        if (availableServers.size() < 3) {
            System.err.println(
                    "Not enough chunk servers available. Need 3, have " + availableServers.size());
            return new ArrayList<>();
        }

        availableServers.sort((a, b) -> Long.compare(b.freeSpace, a.freeSpace));

        List<String> selected = new ArrayList<>();
        for (int i = 0; i < 3 && i < availableServers.size(); i++) {
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

    public String getChunkServerForRead(String filename, int chunkNumber) {
        String key = filename + ":" + chunkNumber;
        List<String> servers = chunkLocations.get(key);
        if (servers == null || servers.isEmpty()) {
            return null;
        }
        Random random = new Random();
        return servers.get(random.nextInt(servers.size()));
    }

    private void processFileInfoRequest(FileInfoRequest request, TCPConnection connection)
            throws IOException {
        String filename = request.getFilename();

        int maxChunk = 0;
        for (String key : chunkLocations.keySet()) {
            if (key.startsWith(filename + ":")) {
                String[] parts = key.split(":");
                int chunkNum = Integer.parseInt(parts[1]);
                maxChunk = Math.max(maxChunk, chunkNum);
            }
        }

        FileInfoResponse response = new FileInfoResponse(maxChunk);
        connection.sendMessage(response);
    }

    private void processChunkServerForReadRequest(
            ChunkServerForReadRequest request, TCPConnection connection) throws IOException {
        String server = getChunkServerForRead(request.getFilename(), request.getChunkNumber());
        ChunkServerForReadResponse response = new ChunkServerForReadResponse(server);
        connection.sendMessage(response);
    }

    private void startFailureDetectionThread() {
        Thread failureDetector =
                new Thread(
                        () -> {
                            while (running) {
                                try {
                                    Thread.sleep(10000); // Check every 10 seconds
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
        long failureThreshold = 180000; // 3 minutes (180 seconds)

        List<String> failedServers = new ArrayList<>();

        // Check each server's last heartbeat time
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

    private void initiateRecovery(String failedServerId) {
        System.out.println("Initiating recovery for failed server: " + failedServerId);

        List<String> affectedChunks = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : chunkLocations.entrySet()) {
            String chunkKey = entry.getKey();
            List<String> servers = entry.getValue();

            if (servers.contains(failedServerId)) {
                affectedChunks.add(chunkKey);
            }
        }

        System.out.println("Found " + affectedChunks.size() + " chunks affected by failure");

        for (String chunkKey : affectedChunks) {
            String[] parts = chunkKey.split(":");
            String filename = parts[0];
            int chunkNumber = Integer.parseInt(parts[1]);

            List<String> remainingServers = chunkLocations.get(chunkKey);
            remainingServers.remove(failedServerId); // Remove failed server

            if (remainingServers.isEmpty()) {
                System.err.println("ERROR: No remaining replicas for " + chunkKey);
                continue;
            }

            String newServer = selectNewServerForReplication(remainingServers);

            if (newServer == null) {
                System.err.println("ERROR: Cannot find new server for " + chunkKey);
                continue;
            }

            String sourceServer = remainingServers.get(0);

            try {
                TCPConnection.Address addr = TCPConnection.Address.parse(sourceServer);
                try (Socket socket = new Socket(addr.host, addr.port);
                        TCPConnection connection = new TCPConnection(socket)) {

                    ReplicateChunkRequest request =
                            new ReplicateChunkRequest(filename, chunkNumber, newServer);
                    connection.sendMessage(request);

                    Message response = connection.receiveMessage();
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
                }
            } catch (Exception e) {
                System.err.println("Error replicating " + chunkKey + ": " + e.getMessage());
            }
        }
    }

    private String selectNewServerForReplication(List<String> existingServers) {
        List<ServerInfo> availableServers = new ArrayList<>(chunkServers.values());

        availableServers.removeIf(server -> existingServers.contains(server.serverId));

        if (availableServers.isEmpty()) {
            return null;
        }

        availableServers.sort((a, b) -> Long.compare(b.freeSpace, a.freeSpace));

        return availableServers.get(0).serverId;
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
