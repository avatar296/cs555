/* CS555 Distributed Systems - HW4 */
package csx55.dfs.replication;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;

/**
 * Client for the Replication-based Distributed File System
 *
 * <p>Responsibilities: - Split files into 64KB chunks for upload - Coordinate with controller to
 * get chunk server locations - Write chunks to chunk servers using pipeline (A → B → C) - Retrieve
 * and assemble chunks during download - Handle corruption detection and recovery during reads
 *
 * <p>Usage: java csx55.dfs.replication.Client <controller-ip> <controller-port>
 *
 * <p>Commands: upload <source> <destination> - Upload file to DFS download <source> <destination> -
 * Download file from DFS
 */
public class Client {

    private final String controllerHost;
    private final int controllerPort;

    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    public Client(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
    }

    /** Start the interactive client shell */
    public void start() {
        System.out.println("Connected to Controller: " + controllerHost + ":" + controllerPort);
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
     * Upload a file to the distributed file system
     *
     * @param sourcePath Local file path
     * @param destPath DFS file path
     */
    public void uploadFile(String sourcePath, String destPath) throws Exception {
        File sourceFile = new File(sourcePath);

        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Source file not found: " + sourcePath);
        }

        // Normalize destination path
        destPath = normalizeDestPath(destPath);

        // Read file and split into chunks
        byte[] fileData = Files.readAllBytes(sourceFile.toPath());
        int numChunks = (int) Math.ceil((double) fileData.length / CHUNK_SIZE);

        System.out.println("Uploading file: " + sourcePath + " -> " + destPath);
        System.out.println("File size: " + fileData.length + " bytes, Chunks: " + numChunks);

        List<String> allChunkServers = new ArrayList<>();

        // Upload each chunk
        for (int i = 0; i < numChunks; i++) {
            int chunkNumber = i + 1; // 1-indexed
            int offset = i * CHUNK_SIZE;
            int length = Math.min(CHUNK_SIZE, fileData.length - offset);
            byte[] chunkData = Arrays.copyOfRange(fileData, offset, offset + length);

            // Get 3 chunk servers from controller
            List<String> chunkServers = getChunkServersForWrite(destPath, chunkNumber);

            if (chunkServers.size() != 3) {
                throw new IOException("Controller did not return 3 chunk servers");
            }

            // Write chunk using pipeline (Client → A → B → C)
            writeChunkPipeline(destPath, chunkNumber, chunkData, chunkServers);

            // Track all chunk server locations for output
            allChunkServers.addAll(chunkServers);
        }

        // Print chunk server locations as required
        for (String server : allChunkServers) {
            System.out.println(server);
        }

        System.out.println("Upload completed successfully");
    }

    /**
     * Download a file from the distributed file system
     *
     * @param sourcePath DFS file path
     * @param destPath Local file path
     */
    public void downloadFile(String sourcePath, String destPath) throws Exception {
        // Normalize source path
        sourcePath = normalizeSourcePath(sourcePath);

        System.out.println("Downloading file: " + sourcePath + " -> " + destPath);

        // Get file metadata from controller (number of chunks)
        int numChunks = getFileChunkCount(sourcePath);

        if (numChunks == 0) {
            throw new FileNotFoundException("File not found: " + sourcePath);
        }

        ByteArrayOutputStream fileOutput = new ByteArrayOutputStream();
        List<String> chunkServersUsed = new ArrayList<>();
        List<String> corruptedChunks = new ArrayList<>();
        List<String> failedServers = new ArrayList<>();

        // Download each chunk
        for (int i = 0; i < numChunks; i++) {
            int chunkNumber = i + 1; // 1-indexed

            // Get a chunk server for this chunk
            String chunkServer = getChunkServerForRead(sourcePath, chunkNumber);

            if (chunkServer == null) {
                failedServers.add("Failed to locate chunk " + chunkNumber);
                throw new IOException("Cannot locate chunk " + chunkNumber);
            }

            try {
                // Read chunk from server
                byte[] chunkData = readChunkFromServer(chunkServer, sourcePath, chunkNumber);
                fileOutput.write(chunkData);
                chunkServersUsed.add(chunkServer);

            } catch (Exception e) {
                // Check if it's corruption or failure
                if (e.getMessage() != null && e.getMessage().contains("corruption")) {
                    // Extract slice number from error message
                    // Format: "Chunk corruption detected: /test/file.txt_chunk1 slice 3"
                    String errorMsg = e.getMessage();
                    int sliceNumber = -1;

                    if (errorMsg.contains(" slice ")) {
                        try {
                            String sliceStr = errorMsg.substring(errorMsg.indexOf(" slice ") + 7);
                            sliceNumber = Integer.parseInt(sliceStr.trim());
                        } catch (Exception parseEx) {
                            // If parsing fails, use -1
                        }
                    }

                    // Create formatted corruption message and embed in exception
                    String formattedMsg =
                            chunkServer
                                    + " "
                                    + chunkNumber
                                    + " "
                                    + sliceNumber
                                    + " is corrupted\n"
                                    + e.getMessage();

                    // Try to recover from another replica
                    // TODO: Implement recovery logic
                    throw new IOException(formattedMsg);
                } else {
                    failedServers.add(chunkServer + " has failed");
                    throw e;
                }
            }
        }

        // Print failed servers first if any
        for (String failure : failedServers) {
            System.out.println(failure);
        }

        // Print corrupted chunks if any
        for (String corruption : corruptedChunks) {
            System.out.println(corruption);
        }

        // Print chunk servers used
        for (String server : chunkServersUsed) {
            System.out.println(server);
        }

        // Write assembled file to disk
        Files.write(Paths.get(destPath), fileOutput.toByteArray());

        System.out.println("Download completed successfully");
    }

    /**
     * Get chunk servers for writing a chunk (from controller)
     *
     * @return List of 3 chunk servers in format ["ip:port", "ip:port", "ip:port"]
     */
    private List<String> getChunkServersForWrite(String filename, int chunkNumber)
            throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            ChunkServersRequest request = new ChunkServersRequest(filename, chunkNumber, 3);
            connection.sendMessage(request);

            Message response = connection.receiveMessage();
            return ((ChunkServersResponse) response).getChunkServers();
        }
    }

    /**
     * Write chunk using pipeline: Client → A → B → C Client only writes to first server, which
     * forwards to next, etc.
     */
    private void writeChunkPipeline(
            String filename, int chunkNumber, byte[] data, List<String> chunkServers)
            throws Exception {
        TCPConnection.Address addr = TCPConnection.Address.parse(chunkServers.get(0));

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            List<String> nextServers =
                    new ArrayList<>(chunkServers.subList(1, chunkServers.size()));
            StoreChunkRequest request =
                    new StoreChunkRequest(filename, chunkNumber, data, nextServers);

            connection.sendMessage(request);
            connection.receiveMessage(); // Wait for ack
        }
    }

    /**
     * Get chunk server for reading a chunk (from controller) Controller returns a random server
     * from available replicas
     */
    private String getChunkServerForRead(String filename, int chunkNumber)
            throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            ChunkServerForReadRequest request =
                    new ChunkServerForReadRequest(filename, chunkNumber);
            connection.sendMessage(request);

            Message response = connection.receiveMessage();
            return ((ChunkServerForReadResponse) response).getChunkServer();
        }
    }

    /** Read chunk from a specific chunk server */
    private byte[] readChunkFromServer(String chunkServer, String filename, int chunkNumber)
            throws Exception {
        TCPConnection.Address addr = TCPConnection.Address.parse(chunkServer);

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            ReadChunkRequest request = new ReadChunkRequest(filename, chunkNumber);
            connection.sendMessage(request);

            Message response = connection.receiveMessage();
            ChunkDataResponse dataResponse = (ChunkDataResponse) response;

            if (!dataResponse.isSuccess()) {
                throw new IOException(dataResponse.getErrorMessage());
            }

            return dataResponse.getData();
        }
    }

    /** Get the number of chunks for a file (from controller) */
    private int getFileChunkCount(String filename) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            FileInfoRequest request = new FileInfoRequest(filename);
            connection.sendMessage(request);

            Message response = connection.receiveMessage();
            return ((FileInfoResponse) response).getNumChunks();
        }
    }

    /** Normalize destination path (remove leading ./) */
    private String normalizeDestPath(String path) {
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    /** Normalize source path (remove leading ./) */
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
            System.err.println(
                    "Usage: java csx55.dfs.replication.Client <controller-ip> <controller-port>");
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
