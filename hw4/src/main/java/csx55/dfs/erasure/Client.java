/* CS555 Distributed Systems - HW4 */
package csx55.dfs.erasure;

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
        return "Erasure Coding Client";
    }

    @Override
    protected void uploadFile(String sourcePath, String destPath) throws Exception {
        validateSourceFile(sourcePath);
        destPath = normalizePath(destPath);

        File sourceFile = new File(sourcePath);

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
            List<String> fragmentServers =
                    getChunkServersForWrite(destPath, chunkNumber, DFSConfig.TOTAL_SHARDS);

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

    @Override
    protected void downloadFile(String sourcePath, String destPath) throws Exception {
        sourcePath = normalizePath(sourcePath);

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
            List<String> fragmentLocations =
                    getChunkServersForWrite(sourcePath, chunkNumber, DFSConfig.TOTAL_SHARDS);

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

    private void sendFragment(
            String filename, int chunkNumber, int fragmentNumber, byte[] data, String server)
            throws Exception {
        TCPConnection.Address addr = TCPConnection.Address.parse(server);

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            String fragmentFilename = filename + "_chunk" + chunkNumber;
            StoreChunkRequest request =
                    new StoreChunkRequest(
                            fragmentFilename, fragmentNumber, data, new ArrayList<>());

            connection.sendMessage(request);
            connection.receiveMessage();
        }
    }

    private byte[] readFragmentFromServer(
            String server, String filename, int chunkNumber, int fragmentNumber) throws Exception {
        TCPConnection.Address addr = TCPConnection.Address.parse(server);

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            String fragmentFilename = filename + "_chunk" + chunkNumber;
            ReadChunkRequest request = new ReadChunkRequest(fragmentFilename, fragmentNumber);
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
