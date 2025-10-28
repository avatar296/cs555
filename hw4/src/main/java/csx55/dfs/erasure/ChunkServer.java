package csx55.dfs.erasure;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk Server for the Erasure Coding-based Distributed File System
 *
 * Key differences from replication:
 * - Stores fragments (shards) instead of full chunk replicas
 * - Each 64KB chunk is split into 9 fragments
 * - Uses Reed-Solomon encoding/decoding
 * - Participates in fragment reconstruction when needed
 *
 * Usage: java csx55.dfs.erasure.ChunkServer <controller-ip> <controller-port>
 */
public class ChunkServer {

    private final String controllerHost;
    private final int controllerPort;
    private final String storageRoot = "/tmp/chunk-server";

    private ServerSocket serverSocket;
    private String serverId;

    // Track all fragments stored at this server
    private final Map<String, FragmentMetadata> fragments;
    private final Set<String> newFragments;

    private volatile boolean running = true;

    // Reed-Solomon parameters
    private static final int DATA_SHARDS = 6;
    private static final int PARITY_SHARDS = 3;
    private static final int TOTAL_SHARDS = 9;

    // Chunk size
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    public ChunkServer(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
        this.fragments = new ConcurrentHashMap<>();
        this.newFragments = Collections.synchronizedSet(new HashSet<>());
    }

    /**
     * Start the chunk server
     */
    public void start() throws IOException {
        Files.createDirectories(Paths.get(storageRoot));

        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        String hostname = java.net.InetAddress.getLocalHost().getHostName();
        serverId = hostname + ":" + port;

        System.out.println("Erasure Coding ChunkServer started: " + serverId);
        System.out.println("Connected to Controller: " + controllerHost + ":" + controllerPort);

        // Start heartbeat threads
        startHeartbeatThreads();

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

    /**
     * Handle incoming connections
     */
    private void handleConnection(Socket socket) {
        // TODO: Implement connection handling for erasure coding
        // - Receive fragment storage requests
        // - Handle fragment read requests
        // - Handle reconstruction requests
    }

    /**
     * Store a fragment (shard) to disk
     *
     * @param filename Original file name
     * @param chunkNumber Chunk number
     * @param fragmentNumber Fragment number (0-8)
     * @param data Fragment data
     */
    public void storeFragment(String filename, int chunkNumber, int fragmentNumber, byte[] data)
            throws IOException {
        Path fragmentPath = getFragmentPath(filename, chunkNumber, fragmentNumber);
        Files.createDirectories(fragmentPath.getParent());

        Files.write(fragmentPath, data);

        // Update metadata
        FragmentMetadata metadata = new FragmentMetadata(filename, chunkNumber, fragmentNumber, data.length);
        fragments.put(getFragmentKey(filename, chunkNumber, fragmentNumber), metadata);
        newFragments.add(getFragmentKey(filename, chunkNumber, fragmentNumber));

        System.out.println("Stored fragment: " + filename + "_chunk" + chunkNumber + "_shard" + fragmentNumber);
    }

    /**
     * Read a fragment from disk
     */
    public byte[] readFragment(String filename, int chunkNumber, int fragmentNumber)
            throws IOException {
        Path fragmentPath = getFragmentPath(filename, chunkNumber, fragmentNumber);

        if (!Files.exists(fragmentPath)) {
            throw new FileNotFoundException("Fragment not found: " + filename +
                    "_chunk" + chunkNumber + "_shard" + fragmentNumber);
        }

        return Files.readAllBytes(fragmentPath);
    }

    /**
     * Encode a chunk using Reed-Solomon and return fragments
     * This demonstrates how to use the Reed-Solomon library
     *
     * @param chunkData The 64KB chunk data
     * @return Array of 9 fragments (6 data + 3 parity)
     */
    public byte[][] encodeChunk(byte[] chunkData) throws Exception {
        // TODO: Implement Reed-Solomon encoding
        // Use the provided reed-solomon-erasure-coding.jar
        // See assignment PDF pages 6-7 for code examples

        // Pseudocode:
        // 1. Pad chunk data to make it divisible by DATA_SHARDS (6)
        // 2. Split into DATA_SHARDS pieces
        // 3. Use ReedSolomon.encodeParity() to generate parity shards
        // 4. Return all 9 shards

        return new byte[TOTAL_SHARDS][];
    }

    /**
     * Decode fragments using Reed-Solomon to reconstruct chunk
     *
     * @param fragments Array of 9 fragments (some may be null/missing)
     * @param fragmentsPresent Boolean array indicating which fragments are available
     * @return Reconstructed chunk data
     */
    public byte[] decodeFragments(byte[][] fragments, boolean[] fragmentsPresent) throws Exception {
        // TODO: Implement Reed-Solomon decoding
        // Use the provided reed-solomon-erasure-coding.jar
        // See assignment PDF page 8 for code examples

        // Pseudocode:
        // 1. Check if at least DATA_SHARDS (6) fragments available
        // 2. Use ReedSolomon.decodeMissing() to reconstruct missing fragments
        // 3. Combine data shards to reconstruct original chunk
        // 4. Remove padding and return

        return new byte[0];
    }

    /**
     * Start heartbeat threads
     */
    private void startHeartbeatThreads() {
        // Minor heartbeat every 15 seconds
        Thread minorHeartbeat = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(15000);
                    sendMinorHeartbeat();
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Error sending minor heartbeat: " + e.getMessage());
                }
            }
        });
        minorHeartbeat.setDaemon(true);
        minorHeartbeat.start();

        // Major heartbeat every 60 seconds
        Thread majorHeartbeat = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(60000);
                    sendMajorHeartbeat();
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Error sending major heartbeat: " + e.getMessage());
                }
            }
        });
        majorHeartbeat.setDaemon(true);
        majorHeartbeat.start();
    }

    /**
     * Send major heartbeat to controller
     */
    private void sendMajorHeartbeat() throws IOException {
        // TODO: Implement major heartbeat for erasure coding
    }

    /**
     * Send minor heartbeat to controller
     */
    private void sendMinorHeartbeat() throws IOException {
        // TODO: Implement minor heartbeat for erasure coding
    }

    /**
     * Get file path for a fragment
     */
    private Path getFragmentPath(String filename, int chunkNumber, int fragmentNumber) {
        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }
        return Paths.get(storageRoot, filename + "_chunk" + chunkNumber + "_shard" + fragmentNumber);
    }

    /**
     * Get unique key for a fragment
     */
    private String getFragmentKey(String filename, int chunkNumber, int fragmentNumber) {
        return filename + ":" + chunkNumber + ":" + fragmentNumber;
    }

    /**
     * Inner class to track fragment metadata
     */
    private static class FragmentMetadata {
        String filename;
        int chunkNumber;
        int fragmentNumber;
        int version;
        long timestamp;
        int dataSize;

        public FragmentMetadata(String filename, int chunkNumber, int fragmentNumber, int dataSize) {
            this.filename = filename;
            this.chunkNumber = chunkNumber;
            this.fragmentNumber = fragmentNumber;
            this.version = 1;
            this.timestamp = System.currentTimeMillis();
            this.dataSize = dataSize;
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java csx55.dfs.erasure.ChunkServer <controller-ip> <controller-port>");
            System.exit(1);
        }

        try {
            String controllerHost = args[0];
            int controllerPort = Integer.parseInt(args[1]);

            ChunkServer chunkServer = new ChunkServer(controllerHost, controllerPort);
            chunkServer.start();
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid integer");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error starting chunk server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
