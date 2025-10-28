package csx55.dfs.erasure;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Client for the Erasure Coding-based Distributed File System
 *
 * Key differences from replication:
 * - Each 64KB chunk is erasure coded into 9 fragments
 * - Fragments distributed to 9 different chunk servers
 * - Only need 6 out of 9 fragments to reconstruct chunk
 * - More storage efficient than 3x replication
 *
 * Usage: java csx55.dfs.erasure.Client <controller-ip> <controller-port>
 *
 * Commands are same as replication:
 *   upload <source> <destination>
 *   download <source> <destination>
 */
public class Client {

    private final String controllerHost;
    private final int controllerPort;

    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    // Reed-Solomon parameters
    private static final int DATA_SHARDS = 6;
    private static final int PARITY_SHARDS = 3;
    private static final int TOTAL_SHARDS = 9;

    public Client(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
    }

    /**
     * Start the interactive client shell
     */
    public void start() {
        System.out.println("Erasure Coding Client connected to Controller: " +
                controllerHost + ":" + controllerPort);
        System.out.println("Commands:");
        System.out.println("  upload <source> <destination>");
        System.out.println("  download <source> <destination>");
        System.out.println("  exit");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "upload":
                        if (parts.length != 3) {
                            System.err.println("Usage: upload <source> <destination>");
                        } else {
                            uploadFile(parts[1], parts[2]);
                        }
                        break;

                    case "download":
                        if (parts.length != 3) {
                            System.err.println("Usage: download <source> <destination>");
                        } else {
                            downloadFile(parts[1], parts[2]);
                        }
                        break;

                    case "exit":
                        System.out.println("Exiting...");
                        scanner.close();
                        return;

                    default:
                        System.err.println("Unknown command: " + command);
                        break;
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Upload a file using erasure coding
     *
     * Process:
     * 1. Split file into 64KB chunks
     * 2. For each chunk:
     *    a. Erasure code into 9 fragments (6 data + 3 parity)
     *    b. Get 9 chunk servers from controller
     *    c. Send each fragment to different server
     */
    public void uploadFile(String sourcePath, String destPath) throws Exception {
        File sourceFile = new File(sourcePath);

        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Source file not found: " + sourcePath);
        }

        destPath = normalizeDestPath(destPath);

        byte[] fileData = Files.readAllBytes(sourceFile.toPath());
        int numChunks = (int) Math.ceil((double) fileData.length / CHUNK_SIZE);

        System.out.println("Uploading file with erasure coding: " + sourcePath + " -> " + destPath);
        System.out.println("File size: " + fileData.length + " bytes, Chunks: " + numChunks);

        List<String> allFragmentServers = new ArrayList<>();

        // Upload each chunk
        for (int i = 0; i < numChunks; i++) {
            int chunkNumber = i + 1;
            int offset = i * CHUNK_SIZE;
            int length = Math.min(CHUNK_SIZE, fileData.length - offset);
            byte[] chunkData = Arrays.copyOfRange(fileData, offset, offset + length);

            // Erasure code the chunk into 9 fragments
            byte[][] fragments = encodeChunk(chunkData);

            // Get 9 chunk servers from controller (one for each fragment)
            List<String> fragmentServers = getChunkServersForWrite(destPath, chunkNumber);

            if (fragmentServers.size() != TOTAL_SHARDS) {
                throw new IOException("Controller did not return " + TOTAL_SHARDS + " chunk servers");
            }

            // Send each fragment to its assigned server
            for (int j = 0; j < TOTAL_SHARDS; j++) {
                sendFragment(destPath, chunkNumber, j, fragments[j], fragmentServers.get(j));
            }

            allFragmentServers.addAll(fragmentServers);
        }

        // Print all fragment server locations
        for (String server : allFragmentServers) {
            System.out.println(server);
        }

        System.out.println("Upload completed successfully");
    }

    /**
     * Download a file using erasure coding
     *
     * Process:
     * 1. For each chunk:
     *    a. Get available fragment locations from controller
     *    b. Retrieve at least 6 fragments (any 6 out of 9)
     *    c. Use Reed-Solomon to reconstruct chunk
     * 2. Assemble chunks into file
     */
    public void downloadFile(String sourcePath, String destPath) throws Exception {
        sourcePath = normalizeSourcePath(sourcePath);

        System.out.println("Downloading file with erasure coding: " + sourcePath + " -> " + destPath);

        int numChunks = getFileChunkCount(sourcePath);

        if (numChunks == 0) {
            throw new FileNotFoundException("File not found: " + sourcePath);
        }

        ByteArrayOutputStream fileOutput = new ByteArrayOutputStream();
        List<String> fragmentServersUsed = new ArrayList<>();
        List<String> corruptedFragments = new ArrayList<>();
        List<String> failedServers = new ArrayList<>();

        // Download and reconstruct each chunk
        for (int i = 0; i < numChunks; i++) {
            int chunkNumber = i + 1;

            // Get available fragment locations
            List<String> fragmentLocations = getFragmentLocationsForRead(sourcePath, chunkNumber);

            if (fragmentLocations.size() < DATA_SHARDS) {
                throw new IOException("Not enough fragments available to reconstruct chunk " + chunkNumber);
            }

            // Retrieve fragments and reconstruct chunk
            byte[][] fragments = new byte[TOTAL_SHARDS][];
            boolean[] fragmentsPresent = new boolean[TOTAL_SHARDS];

            // Try to get at least DATA_SHARDS fragments
            int retrieved = 0;
            for (int j = 0; j < fragmentLocations.size() && retrieved < DATA_SHARDS; j++) {
                // TODO: Retrieve fragment from server
                // Handle corruption and failures
            }

            // Reconstruct chunk using Reed-Solomon
            byte[] chunkData = decodeFragments(fragments, fragmentsPresent);
            fileOutput.write(chunkData);
        }

        // Print failed servers
        for (String failure : failedServers) {
            System.out.println(failure);
        }

        // Print corrupted fragments
        for (String corruption : corruptedFragments) {
            System.out.println(corruption);
        }

        // Print fragment servers used
        for (String server : fragmentServersUsed) {
            System.out.println(server);
        }

        // Write file
        Files.write(Paths.get(destPath), fileOutput.toByteArray());

        System.out.println("Download completed successfully");
    }

    /**
     * Encode a chunk using Reed-Solomon
     */
    private byte[][] encodeChunk(byte[] chunkData) throws Exception {
        // TODO: Implement Reed-Solomon encoding
        // Use code from assignment PDF pages 6-7
        return new byte[TOTAL_SHARDS][];
    }

    /**
     * Decode fragments using Reed-Solomon to reconstruct chunk
     */
    private byte[] decodeFragments(byte[][] fragments, boolean[] fragmentsPresent) throws Exception {
        // TODO: Implement Reed-Solomon decoding
        // Use code from assignment PDF page 8
        return new byte[0];
    }

    /**
     * Get chunk servers for storing fragments (from controller)
     */
    private List<String> getChunkServersForWrite(String filename, int chunkNumber) throws IOException {
        // TODO: Implement protocol to request 9 chunk servers from controller
        return new ArrayList<>();
    }

    /**
     * Get fragment locations for reading (from controller)
     */
    private List<String> getFragmentLocationsForRead(String filename, int chunkNumber) throws IOException {
        // TODO: Implement protocol to get fragment locations
        return new ArrayList<>();
    }

    /**
     * Send a fragment to a chunk server
     */
    private void sendFragment(String filename, int chunkNumber, int fragmentNumber,
                             byte[] data, String server) throws IOException {
        // TODO: Implement fragment sending
    }

    /**
     * Get file chunk count from controller
     */
    private int getFileChunkCount(String filename) throws IOException {
        // TODO: Implement
        return 0;
    }

    /**
     * Normalize paths
     */
    private String normalizeDestPath(String path) {
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private String normalizeSourcePath(String path) {
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java csx55.dfs.erasure.Client <controller-ip> <controller-port>");
            System.exit(1);
        }

        try {
            String controllerHost = args[0];
            int controllerPort = Integer.parseInt(args[1]);

            Client client = new Client(controllerHost, controllerPort);
            client.start();
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid integer");
            System.exit(1);
        }
    }
}
