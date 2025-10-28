/* CS555 Distributed Systems - HW4 */
package csx55.dfs.replication;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;

/**
 * Controller Node for the Replication-based Distributed File System
 *
 * <p>Responsibilities: - Track all active chunk servers in the system - Receive and process
 * heartbeats (major every 60s, minor every 15s) - Maintain metadata about chunks and their
 * locations (in-memory only) - Detect chunk server failures - Coordinate replication and recovery
 *
 * <p>Usage: java csx55.dfs.replication.Controller <port>
 */
public class Controller {

    private final int port;
    private ServerSocket serverSocket;

    // Track all registered chunk servers: key = "ip:port", value = ChunkServerInfo
    private final Map<String, ChunkServerInfo> chunkServers;

    // Track file chunks: key = "filename:chunkNumber", value = list of chunk server locations
    private final Map<String, List<String>> chunkLocations;

    // Track last heartbeat time for failure detection
    private final Map<String, Long> lastHeartbeat;

    private volatile boolean running = true;

    public Controller(int port) {
        this.port = port;
        this.chunkServers = new ConcurrentHashMap<>();
        this.chunkLocations = new ConcurrentHashMap<>();
        this.lastHeartbeat = new ConcurrentHashMap<>();
    }

    /** Start the controller and listen for connections */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Controller started on port " + port);

        // Start failure detection thread
        startFailureDetectionThread();

        // Accept connections from chunk servers and clients
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Handle connection in a new thread
                new Thread(() -> handleConnection(clientSocket)).start();
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** Handle incoming connections from chunk servers and clients */
    private void handleConnection(Socket socket) {
        try (TCPConnection connection = new TCPConnection(socket)) {
            // Receive message
            Message message = connection.receiveMessage();

            // Process based on message type
            switch (message.getType()) {
                case MAJOR_HEARTBEAT:
                case MINOR_HEARTBEAT:
                    processHeartbeat((HeartbeatMessage) message, connection);
                    break;

                case REQUEST_CHUNK_SERVERS_FOR_WRITE:
                    processChunkServersRequest((ChunkServersRequest) message, connection);
                    break;

                case REQUEST_FILE_INFO:
                    // TODO: Implement file info request
                    break;

                case REQUEST_CHUNK_SERVER_FOR_READ:
                    // TODO: Implement chunk server for read request
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

    /** Process heartbeat (major or minor) from chunk server */
    private void processHeartbeat(HeartbeatMessage heartbeat, TCPConnection connection)
            throws IOException {
        String serverId = heartbeat.getChunkServerId();

        // Register or update chunk server info
        ChunkServerInfo serverInfo = chunkServers.get(serverId);
        if (serverInfo == null) {
            serverInfo = new ChunkServerInfo(serverId);
            chunkServers.put(serverId, serverInfo);
            System.out.println("New chunk server registered: " + serverId);
        }

        // Update server statistics
        serverInfo.freeSpace = heartbeat.getFreeSpace();
        serverInfo.chunkCount = heartbeat.getTotalChunks();

        // Update last heartbeat time
        lastHeartbeat.put(serverId, System.currentTimeMillis());

        // Update chunk location mappings
        if (heartbeat.getType() == MessageType.MAJOR_HEARTBEAT) {
            // Major heartbeat: clear old mappings for this server and rebuild
            updateChunkLocationsFromMajorHeartbeat(serverId, heartbeat.getChunks());
        } else {
            // Minor heartbeat: add new chunks only
            updateChunkLocationsFromMinorHeartbeat(serverId, heartbeat.getChunks());
        }

        // Send acknowledgment
        HeartbeatResponse response = new HeartbeatResponse();
        connection.sendMessage(response);
    }

    /** Update chunk locations from major heartbeat (all chunks) */
    private void updateChunkLocationsFromMajorHeartbeat(
            String serverId, List<HeartbeatMessage.ChunkInfo> chunks) {
        // Remove this server from all current chunk locations
        for (Map.Entry<String, List<String>> entry : chunkLocations.entrySet()) {
            entry.getValue().remove(serverId);
        }

        // Add all chunks from this heartbeat
        for (HeartbeatMessage.ChunkInfo chunk : chunks) {
            String key = chunk.filename + ":" + chunk.chunkNumber;
            chunkLocations.computeIfAbsent(key, k -> new ArrayList<>()).add(serverId);
        }
    }

    /** Update chunk locations from minor heartbeat (new chunks only) */
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

    /** Process chunk servers request from client */
    private void processChunkServersRequest(ChunkServersRequest request, TCPConnection connection)
            throws IOException {
        List<String> servers =
                selectChunkServersForWrite(request.getFilename(), request.getChunkNumber());
        ChunkServersResponse response = new ChunkServersResponse(servers);
        connection.sendMessage(response);
    }

    /**
     * Select 3 chunk servers for storing a new chunk Should consider: - Free space available -
     * Cannot select same server multiple times - Load balancing
     */
    public List<String> selectChunkServersForWrite(String filename, int chunkNumber) {
        List<ChunkServerInfo> availableServers = new ArrayList<>(chunkServers.values());

        if (availableServers.size() < 3) {
            System.err.println(
                    "Not enough chunk servers available. Need 3, have " + availableServers.size());
            return new ArrayList<>();
        }

        // Sort by free space (descending) for load balancing
        availableServers.sort((a, b) -> Long.compare(b.freeSpace, a.freeSpace));

        // Select top 3 servers with most free space
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

    /** Get a random chunk server that holds the specified chunk */
    public String getChunkServerForRead(String filename, int chunkNumber) {
        // TODO: Implement chunk server selection for read
        // Return random server from available replicas
        return null;
    }

    /** Detect failed chunk servers and initiate recovery */
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

    /**
     * Detect failed chunk servers (no heartbeat received) Threshold: If no heartbeat for 3 major
     * periods (180 seconds)
     */
    private void detectFailures() {
        long currentTime = System.currentTimeMillis();
        long failureThreshold = 180000; // 3 minutes

        // TODO: Implement failure detection
        // - Check last heartbeat times
        // - Mark failed servers
        // - Initiate chunk replication recovery
    }

    /** Initiate recovery for chunks that lost replicas due to server failure */
    private void initiateRecovery(String failedServerId) {
        // TODO: Implement recovery logic
        // - Find chunks that were on failed server
        // - Find servers with valid replicas
        // - Instruct servers to replicate chunks to new locations
    }

    /** Inner class to track chunk server information */
    private static class ChunkServerInfo {
        String serverId; // "ip:port"
        long totalSpace;
        long freeSpace;
        int chunkCount;

        public ChunkServerInfo(String serverId) {
            this.serverId = serverId;
            this.totalSpace = 1024 * 1024 * 1024; // 1GB
            this.freeSpace = totalSpace;
            this.chunkCount = 0;
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
