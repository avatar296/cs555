package csx55.dfs.replication;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller Node for the Replication-based Distributed File System
 *
 * Responsibilities:
 * - Track all active chunk servers in the system
 * - Receive and process heartbeats (major every 60s, minor every 15s)
 * - Maintain metadata about chunks and their locations (in-memory only)
 * - Detect chunk server failures
 * - Coordinate replication and recovery
 *
 * Usage: java csx55.dfs.replication.Controller <port>
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

    /**
     * Start the controller and listen for connections
     */
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

    /**
     * Handle incoming connections from chunk servers and clients
     */
    private void handleConnection(Socket socket) {
        // TODO: Implement connection handling
        // - Identify if connection is from chunk server or client
        // - Process requests accordingly
    }

    /**
     * Process major heartbeat (every 60 seconds)
     * Contains metadata about ALL chunks maintained at the chunk server
     */
    private void processMajorHeartbeat(String chunkServerId, Object heartbeatData) {
        // TODO: Implement major heartbeat processing
        // - Update chunk server information
        // - Update chunk locations
        // - Update last heartbeat timestamp
    }

    /**
     * Process minor heartbeat (every 15 seconds)
     * Contains information about newly added chunks
     */
    private void processMinorHeartbeat(String chunkServerId, Object heartbeatData) {
        // TODO: Implement minor heartbeat processing
        // - Update newly added chunks
        // - Update last heartbeat timestamp
    }

    /**
     * Select 3 chunk servers for storing a new chunk
     * Should consider:
     * - Free space available
     * - Cannot select same server multiple times
     * - Load balancing
     */
    public List<String> selectChunkServersForWrite(String filename, int chunkNumber) {
        // TODO: Implement chunk server selection logic
        // Return list of 3 chunk servers in format ["ip:port", "ip:port", "ip:port"]
        return new ArrayList<>();
    }

    /**
     * Get a random chunk server that holds the specified chunk
     */
    public String getChunkServerForRead(String filename, int chunkNumber) {
        // TODO: Implement chunk server selection for read
        // Return random server from available replicas
        return null;
    }

    /**
     * Detect failed chunk servers and initiate recovery
     */
    private void startFailureDetectionThread() {
        Thread failureDetector = new Thread(() -> {
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
     * Detect failed chunk servers (no heartbeat received)
     * Threshold: If no heartbeat for 3 major periods (180 seconds)
     */
    private void detectFailures() {
        long currentTime = System.currentTimeMillis();
        long failureThreshold = 180000; // 3 minutes

        // TODO: Implement failure detection
        // - Check last heartbeat times
        // - Mark failed servers
        // - Initiate chunk replication recovery
    }

    /**
     * Initiate recovery for chunks that lost replicas due to server failure
     */
    private void initiateRecovery(String failedServerId) {
        // TODO: Implement recovery logic
        // - Find chunks that were on failed server
        // - Find servers with valid replicas
        // - Instruct servers to replicate chunks to new locations
    }

    /**
     * Inner class to track chunk server information
     */
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
