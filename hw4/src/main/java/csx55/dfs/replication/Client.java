/* CS555 Distributed Systems - HW4 */
package csx55.dfs.replication;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import csx55.dfs.base.BaseClient;
import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.DFSConfig;

public class Client extends BaseClient {

    public Client(String controllerHost, int controllerPort) {
        super(controllerHost, controllerPort);
    }

    @Override
    protected String getClientType() {
        return "Client";
    }

    @Override
    protected void uploadFile(String sourcePath, String destPath) throws Exception {
        validateSourceFile(sourcePath);
        destPath = normalizePath(destPath);

        File sourceFile = new File(sourcePath);

        // Read file and split into chunks
        byte[] fileData = Files.readAllBytes(sourceFile.toPath());
        int numChunks = (int) Math.ceil((double) fileData.length / DFSConfig.CHUNK_SIZE);

        System.out.println("Uploading file: " + sourcePath + " -> " + destPath);
        System.out.println("File size: " + fileData.length + " bytes, Chunks: " + numChunks);

        List<String> allChunkServers = new ArrayList<>();

        // Upload each chunk
        for (int i = 0; i < numChunks; i++) {
            int chunkNumber = i + 1; // 1-indexed
            int offset = i * DFSConfig.CHUNK_SIZE;
            int length = Math.min(DFSConfig.CHUNK_SIZE, fileData.length - offset);
            byte[] chunkData = Arrays.copyOfRange(fileData, offset, offset + length);

            // Get 3 chunk servers from controller
            List<String> chunkServers = getChunkServersForWrite(destPath, chunkNumber, 3);

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

    @Override
    protected void downloadFile(String sourcePath, String destPath) throws Exception {
        sourcePath = normalizePath(sourcePath);

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
            boolean chunkSucceeded = false;
            byte[] chunkData = null;

            // Retry loop: try up to 3 replicas in case of corruption
            for (int attempt = 0; attempt < 3; attempt++) {
                // Get a chunk server for this chunk
                String chunkServer = getChunkServerForRead(sourcePath, chunkNumber);

                if (chunkServer == null) {
                    failedServers.add("Failed to locate chunk " + chunkNumber);
                    throw new IOException("Cannot locate chunk " + chunkNumber);
                }

                try {
                    // Read chunk from server
                    chunkData = readChunkFromServer(chunkServer, sourcePath, chunkNumber);
                    chunkServersUsed.add(chunkServer);
                    chunkSucceeded = true;
                    break; // Success! Exit retry loop

                } catch (Exception e) {
                    // Check if it's corruption or failure
                    if (e.getMessage() != null && e.getMessage().contains("corruption")) {
                        // Extract slice number from error message
                        // Format: "Chunk corruption detected: /test/file.txt_chunk1 slice 3"
                        String errorMsg = e.getMessage();
                        int sliceNumber = -1;

                        if (errorMsg.contains(" slice ")) {
                            try {
                                String sliceStr =
                                        errorMsg.substring(errorMsg.indexOf(" slice ") + 7);
                                sliceNumber = Integer.parseInt(sliceStr.trim());
                            } catch (Exception parseEx) {
                                // If parsing fails, use -1
                            }
                        }

                        // Create formatted corruption message
                        String corruptionMsg =
                                chunkServer
                                        + " "
                                        + chunkNumber
                                        + " "
                                        + sliceNumber
                                        + " is corrupted";
                        corruptedChunks.add(corruptionMsg);

                        // If this was the last attempt, throw exception
                        if (attempt == 2) {
                            throw new IOException("All replicas failed for chunk " + chunkNumber);
                        }
                        // Otherwise, continue to next attempt (retry with different server)

                    } else {
                        // Non-corruption error (server failure, network issue, etc.)
                        failedServers.add(chunkServer + " has failed");
                        throw e; // Don't retry for non-corruption errors
                    }
                }
            }

            // Write the successfully retrieved chunk
            if (chunkSucceeded && chunkData != null) {
                fileOutput.write(chunkData);
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
