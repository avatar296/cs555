package csx55.dfs.replication;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.base.BaseChunkServer;
import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.ChunkMetadata;
import csx55.dfs.util.DFSConfig;
import csx55.dfs.util.NetworkUtils;

public class ChunkServer extends BaseChunkServer {

    private final Map<String, ChunkMetadata> chunks;
    private final Set<String> newChunks;

    private static final int SLICE_SIZE = 8 * 1024;
    private static final int SLICES_PER_CHUNK = DFSConfig.CHUNK_SIZE / SLICE_SIZE;

    public ChunkServer(String controllerHost, int controllerPort) {
        super(controllerHost, controllerPort);
        this.chunks = new ConcurrentHashMap<>();
        this.newChunks = Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    protected String getServerType() {
        return "ChunkServer";
    }

    @Override
    protected int getStoredCount() {
        return chunks.size();
    }

    @Override
    protected long calculateUsedSpace() {
        long usedSpace = 0;
        for (ChunkMetadata metadata : chunks.values()) {
            usedSpace += metadata.getDataSize() + (SLICES_PER_CHUNK * 20);
        }
        return usedSpace;
    }

    @Override
    protected void handleConnection(Socket socket) {
        try (TCPConnection connection = new TCPConnection(socket)) {
            Message message = connection.receiveMessage();

            switch (message.getType()) {
                case STORE_CHUNK_REQUEST:
                    handleStoreChunkRequest((StoreChunkRequest) message, connection);
                    break;

                case READ_CHUNK_REQUEST:
                    handleReadChunkRequest((ReadChunkRequest) message, connection);
                    break;

                case REPLICATE_CHUNK_REQUEST:
                    handleReplicateChunkRequest((ReplicateChunkRequest) message, connection);
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

    private void handleStoreChunkRequest(StoreChunkRequest request, TCPConnection connection)
            throws Exception {
        String filename = request.getFilename();
        int chunkNumber = request.getChunkNumber();
        byte[] data = request.getData();
        List<String> nextServers = request.getNextServers();

        storeChunk(filename, chunkNumber, data);

        if (nextServers != null && !nextServers.isEmpty()) {
            forwardChunkToNextServer(filename, chunkNumber, data, nextServers);
        }

        StoreChunkResponse response = new StoreChunkResponse();
        connection.sendMessage(response);
    }

    private void forwardChunkToNextServer(
            String filename, int chunkNumber, byte[] data, List<String> nextServers)
            throws Exception {
        String nextServerAddr = nextServers.get(0);

        List<String> remainingServers = new ArrayList<>(nextServers.subList(1, nextServers.size()));
        StoreChunkRequest forwardRequest =
                new StoreChunkRequest(filename, chunkNumber, data, remainingServers);

        Message response = NetworkUtils.sendRequestToServer(nextServerAddr, forwardRequest);
        if (response.getType() != MessageType.STORE_CHUNK_RESPONSE) {
            System.err.println("Unexpected response from next server: " + response.getType());
        }

        System.out.println("Forwarded chunk " + chunkNumber + " to " + nextServerAddr);
    }

    private void handleReadChunkRequest(ReadChunkRequest request, TCPConnection connection)
            throws Exception {
        String filename = request.getFilename();
        int chunkNumber = request.getChunkNumber();

        try {
            byte[] data = readChunk(filename, chunkNumber);
            ChunkDataResponse response = new ChunkDataResponse(filename, chunkNumber, data);
            connection.sendMessage(response);
        } catch (Exception e) {
            ChunkDataResponse response =
                    new ChunkDataResponse(filename, chunkNumber, e.getMessage());
            connection.sendMessage(response);
        }
    }

    private void handleReplicateChunkRequest(
            ReplicateChunkRequest request, TCPConnection connection) throws Exception {
        String filename = request.getFilename();
        int chunkNumber = request.getChunkNumber();
        String targetServer = request.getTargetServer();

        try {
            byte[] chunkData = readChunk(filename, chunkNumber);

            StoreChunkRequest storeRequest =
                    new StoreChunkRequest(filename, chunkNumber, chunkData, new ArrayList<>());
            NetworkUtils.sendRequestToServer(targetServer, storeRequest);

            ReplicateChunkResponse response = new ReplicateChunkResponse(true);
            connection.sendMessage(response);

            System.out.println(
                    "Replicated " + filename + "_chunk" + chunkNumber + " to " + targetServer);

        } catch (Exception e) {
            ReplicateChunkResponse response = new ReplicateChunkResponse(false, e.getMessage());
            connection.sendMessage(response);
            System.err.println(
                    "Failed to replicate "
                            + filename
                            + "_chunk"
                            + chunkNumber
                            + ": "
                            + e.getMessage());
        }
    }

    public void storeChunk(String filename, int chunkNumber, byte[] data) throws Exception {
        byte[][] checksums = computeSliceChecksums(data);

        Path chunkPath = getChunkPath(filename, chunkNumber);
        Path metadataPath = getMetadataPath(filename, chunkNumber);
        Files.createDirectories(chunkPath.getParent());

        try (FileOutputStream fos = new FileOutputStream(chunkPath.toFile())) {
            fos.write(data);
        }

        try (FileOutputStream fos = new FileOutputStream(metadataPath.toFile())) {
            for (byte[] checksum : checksums) {
                fos.write(checksum);
            }
        }

        ChunkMetadata metadata = new ChunkMetadata(filename, chunkNumber, data.length);
        chunks.put(getChunkKey(filename, chunkNumber), metadata);
        newChunks.add(getChunkKey(filename, chunkNumber));

        System.out.println("Stored chunk: " + filename + "_chunk" + chunkNumber);
    }

    public byte[] readChunk(String filename, int chunkNumber) throws Exception {
        Path chunkPath = getChunkPath(filename, chunkNumber);
        Path metadataPath = getMetadataPath(filename, chunkNumber);

        if (!Files.exists(chunkPath)) {
            throw new FileNotFoundException(
                    "Chunk not found: " + filename + "_chunk" + chunkNumber);
        }

        byte[][] storedChecksums = new byte[SLICES_PER_CHUNK][20];
        try (FileInputStream fis = new FileInputStream(metadataPath.toFile())) {
            for (int i = 0; i < SLICES_PER_CHUNK; i++) {
                fis.read(storedChecksums[i]);
            }
        }

        byte[] data;
        try (FileInputStream fis = new FileInputStream(chunkPath.toFile())) {
            data = fis.readAllBytes();
        }

        verifyChunkIntegrity(data, storedChecksums, filename, chunkNumber);

        return data;
    }

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
                checksums[i] = digest.digest(new byte[0]);
            }
        }

        return checksums;
    }

    private void verifyChunkIntegrity(
            byte[] data, byte[][] storedChecksums, String filename, int chunkNumber)
            throws Exception {
        byte[][] computedChecksums = computeSliceChecksums(data);

        for (int i = 0; i < SLICES_PER_CHUNK; i++) {
            if (!Arrays.equals(storedChecksums[i], computedChecksums[i])) {
                int sliceNumber = i + 1;
                System.err.println(
                        serverId + " " + chunkNumber + " " + sliceNumber + " is corrupted");
                throw new IOException(
                        "Chunk corruption detected: "
                                + filename
                                + "_chunk"
                                + chunkNumber
                                + " slice "
                                + sliceNumber);
            }
        }
    }

    @Override
    protected List<HeartbeatMessage.ChunkInfo> buildMajorHeartbeatChunks() {
        List<HeartbeatMessage.ChunkInfo> chunkList = new ArrayList<>();
        for (ChunkMetadata metadata : chunks.values()) {
            chunkList.add(
                    new HeartbeatMessage.ChunkInfo(
                            metadata.getFilename(),
                            metadata.getChunkNumber(),
                            metadata.getVersion(),
                            metadata.getSequenceNumber(),
                            metadata.getTimestamp(),
                            metadata.getDataSize()));
        }
        newChunks.clear();
        return chunkList;
    }

    @Override
    protected List<HeartbeatMessage.ChunkInfo> buildMinorHeartbeatChunks() {
        List<HeartbeatMessage.ChunkInfo> chunkList = new ArrayList<>();
        for (String chunkKey : newChunks) {
            ChunkMetadata metadata = chunks.get(chunkKey);
            if (metadata != null) {
                chunkList.add(
                        new HeartbeatMessage.ChunkInfo(
                                metadata.getFilename(),
                                metadata.getChunkNumber(),
                                metadata.getVersion(),
                                metadata.getSequenceNumber(),
                                metadata.getTimestamp(),
                                metadata.getDataSize()));
            }
        }
        return chunkList;
    }

    private Path getChunkPath(String filename, int chunkNumber) {
        filename = normalizeFilename(filename);
        return getStoragePath(filename + "_chunk" + chunkNumber);
    }

    private Path getMetadataPath(String filename, int chunkNumber) {
        filename = normalizeFilename(filename);
        return getStoragePath("." + filename + "_chunk" + chunkNumber + ".meta");
    }

    private String getChunkKey(String filename, int chunkNumber) {
        return filename + ":" + chunkNumber;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println(
                    "Usage: java csx55.dfs.replication.ChunkServer <controller-ip>"
                            + " <controller-port>");
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
