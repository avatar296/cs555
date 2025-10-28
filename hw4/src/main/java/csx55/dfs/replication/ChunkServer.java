package csx55.dfs.replication;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk Server for the Replication-based Distributed File System
 *
 * Responsibilities:
 * - Store 64KB file chunks on local disk (/tmp/chunk-server/)
 * - Maintain SHA-1 checksums for 8KB slices within each chunk
 * - Send heartbeats to controller (major: 60s, minor: 15s)
 * - Forward chunks to other chunk servers during write pipeline
 * - Detect and report data corruption
 * - Participate in error correction and recovery
 *
 * Usage: java csx55.dfs.replication.ChunkServer <controller-ip> <controller-port>
 */
public class ChunkServer {

    private final String controllerHost;
    private final int controllerPort;
    private final String storageRoot = "/tmp/chunk-server";

    private ServerSocket serverSocket;
    private String serverId; // Will be "ip:port" of this server

    // Track all chunks stored at this server
    private final Map<String, ChunkMetadata> chunks;

    // Track newly added chunks since last major heartbeat
    private final Set<String> newChunks;

    private volatile boolean running = true;

    // Constants
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB
    private static final int SLICE_SIZE = 8 * 1024; // 8KB
    private static final int SLICES_PER_CHUNK = CHUNK_SIZE / SLICE_SIZE; // 8 slices

    public ChunkServer(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
        this.chunks = new ConcurrentHashMap<>();
        this.newChunks = Collections.synchronizedSet(new HashSet<>());
    }

    /**
     * Start the chunk server
     */
    public void start() throws IOException {
        // Create storage directory
        Files.createDirectories(Paths.get(storageRoot));

        // Start server socket on random port
        serverSocket = new ServerSocket(0); // 0 = random available port
        int port = serverSocket.getLocalPort();

        // Determine server ID
        String hostname = java.net.InetAddress.getLocalHost().getHostName();
        serverId = hostname + ":" + port;

        System.out.println("ChunkServer started: " + serverId);
        System.out.println("Connected to Controller: " + controllerHost + ":" + controllerPort);

        // Start heartbeat threads
        startHeartbeatThreads();

        // Accept connections from clients and other chunk servers
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
        // TODO: Implement connection handling
        // - Receive chunk write requests
        // - Forward chunks to next server in pipeline
        // - Handle chunk read requests
        // - Handle replication requests from controller
    }

    /**
     * Store a chunk to disk with integrity information
     *
     * @param filename The original file name
     * @param chunkNumber The chunk number
     * @param data The chunk data (up to 64KB)
     */
    public void storeChunk(String filename, int chunkNumber, byte[] data) throws Exception {
        // Compute checksums for each 8KB slice
        byte[][] checksums = computeSliceChecksums(data);

        // Create chunk file path
        Path chunkPath = getChunkPath(filename, chunkNumber);
        Files.createDirectories(chunkPath.getParent());

        // Write chunk data with checksums
        try (FileOutputStream fos = new FileOutputStream(chunkPath.toFile())) {
            // Write checksums first (8 checksums * 20 bytes = 160 bytes)
            for (byte[] checksum : checksums) {
                fos.write(checksum);
            }
            // Write actual data
            fos.write(data);
        }

        // Update metadata
        ChunkMetadata metadata = new ChunkMetadata(filename, chunkNumber, data.length);
        chunks.put(getChunkKey(filename, chunkNumber), metadata);
        newChunks.add(getChunkKey(filename, chunkNumber));

        System.out.println("Stored chunk: " + filename + "_chunk" + chunkNumber);
    }

    /**
     * Read a chunk from disk and verify integrity
     *
     * @param filename The original file name
     * @param chunkNumber The chunk number
     * @return The chunk data (without checksums)
     * @throws Exception if chunk is corrupted
     */
    public byte[] readChunk(String filename, int chunkNumber) throws Exception {
        Path chunkPath = getChunkPath(filename, chunkNumber);

        if (!Files.exists(chunkPath)) {
            throw new FileNotFoundException("Chunk not found: " + filename + "_chunk" + chunkNumber);
        }

        try (FileInputStream fis = new FileInputStream(chunkPath.toFile())) {
            // Read checksums first (8 checksums * 20 bytes = 160 bytes)
            byte[][] storedChecksums = new byte[SLICES_PER_CHUNK][20];
            for (int i = 0; i < SLICES_PER_CHUNK; i++) {
                fis.read(storedChecksums[i]);
            }

            // Read chunk data
            byte[] data = fis.readAllBytes();

            // Verify integrity
            verifyChunkIntegrity(data, storedChecksums, filename, chunkNumber);

            return data;
        }
    }

    /**
     * Compute SHA-1 checksums for each 8KB slice
     */
    private byte[][] computeSliceChecksums(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[][] checksums = new byte[SLICES_PER_CHUNK][];

        for (int i = 0; i < SLICES_PER_CHUNK; i++) {
            int offset = i * SLICE_SIZE;
            int length = Math.min(SLICE_SIZE, data.length - offset);

            if (length > 0) {
                digest.update(data, offset, length);
                checksums[i] = digest.digest();
            } else {
                // Empty slice - hash empty array
                checksums[i] = digest.digest(new byte[0]);
            }
        }

        return checksums;
    }

    /**
     * Verify chunk integrity by comparing checksums
     */
    private void verifyChunkIntegrity(byte[] data, byte[][] storedChecksums,
                                     String filename, int chunkNumber) throws Exception {
        byte[][] computedChecksums = computeSliceChecksums(data);

        for (int i = 0; i < SLICES_PER_CHUNK; i++) {
            if (!Arrays.equals(storedChecksums[i], computedChecksums[i])) {
                int sliceNumber = i + 1; // 1-indexed for output
                System.err.println(serverId + " " + chunkNumber + " " + sliceNumber + " is corrupted");
                throw new IOException("Chunk corruption detected: " + filename +
                                    "_chunk" + chunkNumber + " slice " + sliceNumber);
            }
        }
    }

    /**
     * Start heartbeat threads (major and minor)
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
     * Send major heartbeat to controller (all chunks metadata)
     */
    private void sendMajorHeartbeat() throws IOException {
        // TODO: Implement major heartbeat
        // - Send all chunk metadata
        // - Send total chunks and free space
        // - Clear newChunks set after sending
    }

    /**
     * Send minor heartbeat to controller (only new chunks)
     */
    private void sendMinorHeartbeat() throws IOException {
        // TODO: Implement minor heartbeat
        // - Send only newly added chunks
        // - Send total chunks and free space
    }

    /**
     * Get the file path for a chunk
     */
    private Path getChunkPath(String filename, int chunkNumber) {
        // If filename starts with /, remove it
        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }
        return Paths.get(storageRoot, filename + "_chunk" + chunkNumber);
    }

    /**
     * Get the unique key for a chunk
     */
    private String getChunkKey(String filename, int chunkNumber) {
        return filename + ":" + chunkNumber;
    }

    /**
     * Calculate free space (1GB - space used)
     */
    private long getFreeSpace() throws IOException {
        // TODO: Calculate actual space used by chunks
        long totalSpace = 1024L * 1024L * 1024L; // 1GB
        long usedSpace = 0;
        // Calculate used space...
        return totalSpace - usedSpace;
    }

    /**
     * Inner class to track chunk metadata
     */
    private static class ChunkMetadata {
        String filename;
        int chunkNumber;
        int version;
        int sequenceNumber;
        long timestamp;
        int dataSize;

        public ChunkMetadata(String filename, int chunkNumber, int dataSize) {
            this.filename = filename;
            this.chunkNumber = chunkNumber;
            this.version = 1;
            this.sequenceNumber = chunkNumber;
            this.timestamp = System.currentTimeMillis();
            this.dataSize = dataSize;
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java csx55.dfs.replication.ChunkServer <controller-ip> <controller-port>");
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
