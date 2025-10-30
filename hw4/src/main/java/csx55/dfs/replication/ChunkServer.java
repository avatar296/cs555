/* CS555 Distributed Systems - HW4 */
package csx55.dfs.replication;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.ChunkMetadata;
import csx55.dfs.util.DFSConfig;

public class ChunkServer {

    private final String controllerHost;
    private final int controllerPort;
    private String storageRoot;

    private ServerSocket serverSocket;
    private String serverId;

    private final Map<String, ChunkMetadata> chunks;

    private final Set<String> newChunks;

    private volatile boolean running = true;

    private static final int SLICE_SIZE = 8 * 1024; // 8KB
    private static final int SLICES_PER_CHUNK = DFSConfig.CHUNK_SIZE / SLICE_SIZE; // 8 slices

    public ChunkServer(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
        this.chunks = new ConcurrentHashMap<>();
        this.newChunks = Collections.synchronizedSet(new HashSet<>());
    }

    public void start() throws IOException {

        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        storageRoot = "/tmp/chunk-server-" + port;
        Files.createDirectories(Paths.get(storageRoot));

        String hostname = java.net.InetAddress.getLocalHost().getHostName();
        serverId = hostname + ":" + port;

        System.out.println("ChunkServer started: " + serverId);
        System.out.println("Storage directory: " + storageRoot);
        System.out.println("Connected to Controller: " + controllerHost + ":" + controllerPort);

        startHeartbeatThreads();

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

    private void handleConnection(Socket socket) {
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
        TCPConnection.Address addr = TCPConnection.Address.parse(nextServerAddr);

        List<String> remainingServers = new ArrayList<>(nextServers.subList(1, nextServers.size()));
        StoreChunkRequest forwardRequest =
                new StoreChunkRequest(filename, chunkNumber, data, remainingServers);

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(forwardRequest);

            Message response = connection.receiveMessage();
            if (response.getType() != MessageType.STORE_CHUNK_RESPONSE) {
                System.err.println("Unexpected response from next server: " + response.getType());
            }
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

            TCPConnection.Address addr = TCPConnection.Address.parse(targetServer);
            try (Socket targetSocket = new Socket(addr.host, addr.port);
                    TCPConnection targetConnection = new TCPConnection(targetSocket)) {

                StoreChunkRequest storeRequest =
                        new StoreChunkRequest(filename, chunkNumber, chunkData, new ArrayList<>());
                targetConnection.sendMessage(storeRequest);

                targetConnection.receiveMessage();
            }

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
        Files.createDirectories(chunkPath.getParent());

        try (FileOutputStream fos = new FileOutputStream(chunkPath.toFile())) {
            for (byte[] checksum : checksums) {
                fos.write(checksum);
            }
            fos.write(data);
        }

        ChunkMetadata metadata = new ChunkMetadata(filename, chunkNumber, data.length);
        chunks.put(getChunkKey(filename, chunkNumber), metadata);
        newChunks.add(getChunkKey(filename, chunkNumber));

        System.out.println("Stored chunk: " + filename + "_chunk" + chunkNumber);
    }

    public byte[] readChunk(String filename, int chunkNumber) throws Exception {
        Path chunkPath = getChunkPath(filename, chunkNumber);

        if (!Files.exists(chunkPath)) {
            throw new FileNotFoundException(
                    "Chunk not found: " + filename + "_chunk" + chunkNumber);
        }

        try (FileInputStream fis = new FileInputStream(chunkPath.toFile())) {
            byte[][] storedChecksums = new byte[SLICES_PER_CHUNK][20];
            for (int i = 0; i < SLICES_PER_CHUNK; i++) {
                fis.read(storedChecksums[i]);
            }

            byte[] data = fis.readAllBytes();

            verifyChunkIntegrity(data, storedChecksums, filename, chunkNumber);

            return data;
        }
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

    private void startHeartbeatThreads() {
        Thread minorHeartbeat =
                new Thread(
                        () -> {
                            while (running) {
                                try {
                                    Thread.sleep(15000);
                                    sendMinorHeartbeat();
                                } catch (InterruptedException e) {
                                    break;
                                } catch (Exception e) {
                                    System.err.println(
                                            "Error sending minor heartbeat: " + e.getMessage());
                                }
                            }
                        });
        minorHeartbeat.setDaemon(true);
        minorHeartbeat.start();

        Thread majorHeartbeat =
                new Thread(
                        () -> {
                            while (running) {
                                try {
                                    Thread.sleep(60000);
                                    sendMajorHeartbeat();
                                } catch (InterruptedException e) {
                                    break;
                                } catch (Exception e) {
                                    System.err.println(
                                            "Error sending major heartbeat: " + e.getMessage());
                                }
                            }
                        });
        majorHeartbeat.setDaemon(true);
        majorHeartbeat.start();
    }

    private void sendMajorHeartbeat() throws IOException, ClassNotFoundException {
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

        HeartbeatMessage heartbeat =
                new HeartbeatMessage(
                        MessageType.MAJOR_HEARTBEAT,
                        serverId,
                        chunks.size(),
                        getFreeSpace(),
                        chunkList);

        sendHeartbeatToController(heartbeat);

        newChunks.clear();
    }

    private void sendMinorHeartbeat() throws IOException, ClassNotFoundException {
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

        HeartbeatMessage heartbeat =
                new HeartbeatMessage(
                        MessageType.MINOR_HEARTBEAT,
                        serverId,
                        chunks.size(),
                        getFreeSpace(),
                        chunkList);

        sendHeartbeatToController(heartbeat);
    }

    private void sendHeartbeatToController(HeartbeatMessage heartbeat)
            throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(heartbeat);

            Message response = connection.receiveMessage();
            if (response.getType() != MessageType.HEARTBEAT_RESPONSE) {
                System.err.println("Unexpected response to heartbeat: " + response.getType());
            }
        }
    }

    private Path getChunkPath(String filename, int chunkNumber) {
        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }
        return Paths.get(storageRoot, filename + "_chunk" + chunkNumber);
    }

    private String getChunkKey(String filename, int chunkNumber) {
        return filename + ":" + chunkNumber;
    }

    private long getFreeSpace() throws IOException {
        long totalSpace = 1024L * 1024L * 1024L; // 1GB
        long usedSpace = 0;

        for (ChunkMetadata metadata : chunks.values()) {
            usedSpace += metadata.getDataSize() + (SLICES_PER_CHUNK * 20);
        }

        return totalSpace - usedSpace;
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
