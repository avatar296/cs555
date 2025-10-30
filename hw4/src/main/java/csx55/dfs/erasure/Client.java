/* CS555 Distributed Systems - HW4 */
package csx55.dfs.erasure;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import csx55.dfs.util.DFSConfig;
import csx55.dfs.util.PathUtil;

public class Client {

    private final String controllerHost;
    private final int controllerPort;

    public Client(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
    }

    public void start() {
        System.out.println(
                "Erasure Coding Client connected to Controller: "
                        + controllerHost
                        + ":"
                        + controllerPort);
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

    public void uploadFile(String sourcePath, String destPath) throws Exception {
        File sourceFile = new File(sourcePath);

        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Source file not found: " + sourcePath);
        }

        destPath = PathUtil.normalize(destPath);

        byte[] fileData = Files.readAllBytes(sourceFile.toPath());
        int numChunks = (int) Math.ceil((double) fileData.length / DFSConfig.CHUNK_SIZE);

        System.out.println("Uploading file with erasure coding: " + sourcePath + " -> " + destPath);
        System.out.println("File size: " + fileData.length + " bytes, Chunks: " + numChunks);

        List<String> allFragmentServers = new ArrayList<>();

        // Upload each chunk
        for (int i = 0; i < numChunks; i++) {
            int chunkNumber = i + 1;
            int offset = i * DFSConfig.CHUNK_SIZE;
            int length = Math.min(DFSConfig.CHUNK_SIZE, fileData.length - offset);
            byte[] chunkData = Arrays.copyOfRange(fileData, offset, offset + length);

            // Erasure code the chunk into 9 fragments
            byte[][] fragments = encodeChunk(chunkData);

            // Get 9 chunk servers from controller (one for each fragment)
            List<String> fragmentServers = getChunkServersForWrite(destPath, chunkNumber);

            if (fragmentServers.size() != DFSConfig.TOTAL_SHARDS) {
                throw new IOException(
                        "Controller did not return " + DFSConfig.TOTAL_SHARDS + " chunk servers");
            }

            // Send each fragment to its assigned server
            for (int j = 0; j < DFSConfig.TOTAL_SHARDS; j++) {
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

    public void downloadFile(String sourcePath, String destPath) throws Exception {
        sourcePath = PathUtil.normalize(sourcePath);

        System.out.println(
                "Downloading file with erasure coding: " + sourcePath + " -> " + destPath);

        int numChunks = getFileChunkCount(sourcePath);

        if (numChunks == 0) {
            throw new FileNotFoundException("File not found: " + sourcePath);
        }

        ByteArrayOutputStream fileOutput = new ByteArrayOutputStream();
        List<String> fragmentServersUsed = new ArrayList<>();

        // Download and reconstruct each chunk
        for (int i = 0; i < numChunks; i++) {
            int chunkNumber = i + 1;

            // Get available fragment locations (list of 9, index = fragment number)
            List<String> fragmentLocations = getChunkServersForWrite(sourcePath, chunkNumber);

            // Count non-null fragments
            int availableFragments = 0;
            for (String server : fragmentLocations) {
                if (server != null) {
                    availableFragments++;
                }
            }

            if (availableFragments < DFSConfig.DATA_SHARDS) {
                throw new IOException(
                        "Not enough fragments available to reconstruct chunk "
                                + chunkNumber
                                + " (have "
                                + availableFragments
                                + ", need "
                                + DFSConfig.DATA_SHARDS
                                + ")");
            }

            // Retrieve fragments and reconstruct chunk
            byte[][] fragments = new byte[DFSConfig.TOTAL_SHARDS][];
            boolean[] fragmentsPresent = new boolean[DFSConfig.TOTAL_SHARDS];
            int fragmentSize = 0;

            // Try to get at least DFSConfig.DATA_SHARDS fragments
            int retrieved = 0;
            for (int j = 0; j < DFSConfig.TOTAL_SHARDS && j < fragmentLocations.size(); j++) {
                String server = fragmentLocations.get(j);
                if (server == null) {
                    fragmentsPresent[j] = false;
                    continue;
                }

                try {
                    byte[] fragmentData =
                            readFragmentFromServer(server, sourcePath, chunkNumber, j);

                    fragments[j] = fragmentData;
                    fragmentsPresent[j] = true;
                    fragmentSize = fragmentData.length;
                    retrieved++;
                    fragmentServersUsed.add(server);

                } catch (Exception e) {
                    fragmentsPresent[j] = false;
                    // Continue trying other fragments
                }
            }

            if (retrieved < DFSConfig.DATA_SHARDS) {
                throw new IOException(
                        "Could not retrieve enough fragments ("
                                + retrieved
                                + "/"
                                + DFSConfig.DATA_SHARDS
                                + ") for chunk "
                                + chunkNumber);
            }

            // Ensure all fragments have same size for Reed-Solomon
            for (int j = 0; j < DFSConfig.TOTAL_SHARDS; j++) {
                if (!fragmentsPresent[j]) {
                    fragments[j] = new byte[fragmentSize]; // Empty placeholder
                }
            }

            // Reconstruct chunk using Reed-Solomon
            // For simplicity, use DFSConfig.CHUNK_SIZE (last chunk may have padding)
            byte[] chunkData = decodeFragments(fragments, fragmentsPresent, DFSConfig.CHUNK_SIZE);
            fileOutput.write(chunkData);
        }

        // Print fragment servers used
        for (String server : fragmentServersUsed) {
            System.out.println(server);
        }

        // Write file
        Files.write(Paths.get(destPath), fileOutput.toByteArray());

        System.out.println("Download completed successfully");
    }

    private byte[][] encodeChunk(byte[] chunkData) throws Exception {
        erasure.ReedSolomon reedSolomon =
                new erasure.ReedSolomon(DFSConfig.DATA_SHARDS, DFSConfig.PARITY_SHARDS);

        int shardSize = (chunkData.length + DFSConfig.DATA_SHARDS - 1) / DFSConfig.DATA_SHARDS;
        byte[][] shards = new byte[DFSConfig.TOTAL_SHARDS][shardSize];

        for (int i = 0; i < DFSConfig.DATA_SHARDS; i++) {
            for (int j = 0; j < shardSize; j++) {
                int dataIndex = i * shardSize + j;
                if (dataIndex < chunkData.length) {
                    shards[i][j] = chunkData[dataIndex];
                }
            }
        }

        reedSolomon.encodeParity(shards, 0, shardSize);

        return shards;
    }

    private byte[] decodeFragments(byte[][] fragments, boolean[] fragmentsPresent, int originalSize)
            throws Exception {
        erasure.ReedSolomon reedSolomon =
                new erasure.ReedSolomon(DFSConfig.DATA_SHARDS, DFSConfig.PARITY_SHARDS);

        int shardSize = fragments[0].length;
        reedSolomon.decodeMissing(fragments, fragmentsPresent, 0, shardSize);

        byte[] reconstructed = new byte[originalSize];
        int pos = 0;

        for (int i = 0; i < DFSConfig.DATA_SHARDS && pos < originalSize; i++) {
            int toCopy = Math.min(shardSize, originalSize - pos);
            System.arraycopy(fragments[i], 0, reconstructed, pos, toCopy);
            pos += toCopy;
        }

        return reconstructed;
    }

    private List<String> getChunkServersForWrite(String filename, int chunkNumber)
            throws Exception {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                csx55.dfs.transport.TCPConnection connection =
                        new csx55.dfs.transport.TCPConnection(socket)) {

            csx55.dfs.protocol.ChunkServersRequest request =
                    new csx55.dfs.protocol.ChunkServersRequest(
                            filename, chunkNumber, DFSConfig.TOTAL_SHARDS);
            connection.sendMessage(request);

            csx55.dfs.protocol.Message response = connection.receiveMessage();
            return ((csx55.dfs.protocol.ChunkServersResponse) response).getChunkServers();
        }
    }

    private void sendFragment(
            String filename, int chunkNumber, int fragmentNumber, byte[] data, String server)
            throws Exception {
        csx55.dfs.transport.TCPConnection.Address addr =
                csx55.dfs.transport.TCPConnection.Address.parse(server);

        try (Socket socket = new Socket(addr.host, addr.port);
                csx55.dfs.transport.TCPConnection connection =
                        new csx55.dfs.transport.TCPConnection(socket)) {

            String fragmentFilename = filename + "_chunk" + chunkNumber;
            csx55.dfs.protocol.StoreChunkRequest request =
                    new csx55.dfs.protocol.StoreChunkRequest(
                            fragmentFilename, fragmentNumber, data, new ArrayList<>());

            connection.sendMessage(request);
            connection.receiveMessage();
        }
    }

    private int getFileChunkCount(String filename) throws Exception {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                csx55.dfs.transport.TCPConnection connection =
                        new csx55.dfs.transport.TCPConnection(socket)) {

            csx55.dfs.protocol.FileInfoRequest request =
                    new csx55.dfs.protocol.FileInfoRequest(filename);
            connection.sendMessage(request);

            csx55.dfs.protocol.Message response = connection.receiveMessage();
            return ((csx55.dfs.protocol.FileInfoResponse) response).getNumChunks();
        }
    }

    private byte[] readFragmentFromServer(
            String server, String filename, int chunkNumber, int fragmentNumber) throws Exception {
        csx55.dfs.transport.TCPConnection.Address addr =
                csx55.dfs.transport.TCPConnection.Address.parse(server);

        try (Socket socket = new Socket(addr.host, addr.port);
                csx55.dfs.transport.TCPConnection connection =
                        new csx55.dfs.transport.TCPConnection(socket)) {

            String fragmentFilename = filename + "_chunk" + chunkNumber;
            csx55.dfs.protocol.ReadChunkRequest request =
                    new csx55.dfs.protocol.ReadChunkRequest(fragmentFilename, fragmentNumber);
            connection.sendMessage(request);

            csx55.dfs.protocol.Message response = connection.receiveMessage();
            csx55.dfs.protocol.ChunkDataResponse dataResponse =
                    (csx55.dfs.protocol.ChunkDataResponse) response;

            if (!dataResponse.isSuccess()) {
                throw new IOException(dataResponse.getErrorMessage());
            }

            return dataResponse.getData();
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println(
                    "Usage: java csx55.dfs.erasure.Client <controller-ip> <controller-port>");
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
