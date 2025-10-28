/* CS555 Distributed Systems - HW4 */
package csx55.dfs.erasure;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller Node for the Erasure Coding-based Distributed File System
 *
 * <p>Similar to replication Controller but manages erasure-coded fragments instead of replicas.
 *
 * <p>Key differences from replication: - Each chunk is split into k=6 data shards + m=3 parity
 * shards = 9 fragments - Need to track 9 fragment locations per chunk instead of 3 replicas -
 * Recovery requires at least k=6 fragments (any 6 out of 9)
 *
 * <p>Usage: java csx55.dfs.erasure.Controller <port>
 */
public class Controller {

    private final int port;
    private ServerSocket serverSocket;

    // Reed-Solomon parameters
    private static final int DATA_SHARDS = 6;
    private static final int PARITY_SHARDS = 3;
    private static final int TOTAL_SHARDS = 9;

    // Track all registered chunk servers
    private final Map<String, ChunkServerInfo> chunkServers;

    // Track fragment locations: key = "filename:chunkNumber:fragmentNumber", value = server
    // location
    private final Map<String, List<String>> fragmentLocations;

    // Track last heartbeat time for failure detection
    private final Map<String, Long> lastHeartbeat;

    private volatile boolean running = true;

    public Controller(int port) {
        this.port = port;
        this.chunkServers = new ConcurrentHashMap<>();
        this.fragmentLocations = new ConcurrentHashMap<>();
        this.lastHeartbeat = new ConcurrentHashMap<>();
    }

    /** Start the controller and listen for connections */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Erasure Coding Controller started on port " + port);

        // Start failure detection thread
        startFailureDetectionThread();

        // Accept connections
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

    /** Handle incoming connections from chunk servers and clients */
    private void handleConnection(Socket socket) {
        // TODO: Implement connection handling for erasure coding mode
    }

    /**
     * Select 9 chunk servers for storing erasure-coded fragments Each fragment must go to a
     * different server
     *
     * @return List of 9 chunk servers in format ["ip:port", ...]
     */
    public List<String> selectChunkServersForWrite(String filename, int chunkNumber) {
        // TODO: Implement chunk server selection for erasure coding
        // Must return 9 different servers
        return new ArrayList<>();
    }

    /**
     * Get chunk servers that hold fragments for a specific chunk Need at least DATA_SHARDS (6)
     * fragments to reconstruct
     *
     * @return List of available fragment locations
     */
    public List<String> getFragmentLocationsForRead(String filename, int chunkNumber) {
        // TODO: Implement fragment location retrieval
        return new ArrayList<>();
    }

    /** Detect failed chunk servers and initiate recovery */
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

    /** Detect failed chunk servers */
    private void detectFailures() {
        // TODO: Implement failure detection for erasure coding
        // Need to check if enough fragments are still available
        // Minimum k=6 fragments needed per chunk
    }

    /** Initiate recovery for lost fragments */
    private void initiateRecovery(String failedServerId) {
        // TODO: Implement recovery for erasure coding
        // - Find lost fragments
        // - Check if at least k=6 fragments still available
        // - Instruct servers to reconstruct missing fragments
    }

    /** Inner class to track chunk server information */
    private static class ChunkServerInfo {
        String serverId;
        long totalSpace;
        long freeSpace;
        int fragmentCount;

        public ChunkServerInfo(String serverId) {
            this.serverId = serverId;
            this.totalSpace = 1024 * 1024 * 1024; // 1GB
            this.freeSpace = totalSpace;
            this.fragmentCount = 0;
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java csx55.dfs.erasure.Controller <port>");
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
