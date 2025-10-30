/* CS555 Distributed Systems - HW4 */
package csx55.dfs.erasure;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.util.DFSConfig;
import csx55.dfs.util.FragmentMetadata;

public class ChunkServer {

    private final String controllerHost;
    private final int controllerPort;
    private final String storageRoot = "/tmp/chunk-server";

    private ServerSocket serverSocket;
    private String serverId;

    private final Map<String, FragmentMetadata> fragments;
    private final Set<String> newFragments;

    private volatile boolean running = true;

    public ChunkServer(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
        this.fragments = new ConcurrentHashMap<>();
        this.newFragments = Collections.synchronizedSet(new HashSet<>());
    }

    public void start() throws IOException {
        Files.createDirectories(Paths.get(storageRoot));

        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        String hostname = java.net.InetAddress.getLocalHost().getHostName();
        serverId = hostname + ":" + port;

        System.out.println("Erasure Coding ChunkServer started: " + serverId);
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
        try (csx55.dfs.transport.TCPConnection connection =
                new csx55.dfs.transport.TCPConnection(socket)) {
            csx55.dfs.protocol.Message message = connection.receiveMessage();

            switch (message.getType()) {
                case STORE_CHUNK_REQUEST:
                    handleStoreFragmentRequest(
                            (csx55.dfs.protocol.StoreChunkRequest) message, connection);
                    break;

                case READ_CHUNK_REQUEST:
                    handleReadFragmentRequest(
                            (csx55.dfs.protocol.ReadChunkRequest) message, connection);
                    break;

                case RECONSTRUCT_FRAGMENT_REQUEST:
                    handleReconstructFragmentRequest(
                            (csx55.dfs.protocol.ReconstructFragmentRequest) message, connection);
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

    private void handleStoreFragmentRequest(
            csx55.dfs.protocol.StoreChunkRequest request,
            csx55.dfs.transport.TCPConnection connection)
            throws Exception {
        String filename = request.getFilename();
        int fragmentNumber = request.getChunkNumber();
        byte[] data = request.getData();

        int chunkNumber = 1;
        if (filename.contains("_chunk")) {
            String[] parts = filename.split("_chunk");
            if (parts.length > 1) {
                chunkNumber = Integer.parseInt(parts[1].split("_")[0]);
                filename = parts[0];
            }
        }

        storeFragment(filename, chunkNumber, fragmentNumber, data);

        csx55.dfs.protocol.StoreChunkResponse response =
                new csx55.dfs.protocol.StoreChunkResponse();
        connection.sendMessage(response);
    }

    private void handleReadFragmentRequest(
            csx55.dfs.protocol.ReadChunkRequest request,
            csx55.dfs.transport.TCPConnection connection)
            throws Exception {
        String filename = request.getFilename();
        int fragmentNumber = request.getChunkNumber();

        int chunkNumber = 1;
        if (filename.contains("_chunk")) {
            String[] parts = filename.split("_chunk");
            if (parts.length > 1) {
                chunkNumber = Integer.parseInt(parts[1].split("_")[0]);
                filename = parts[0];
            }
        }

        try {
            byte[] data = readFragment(filename, chunkNumber, fragmentNumber);
            csx55.dfs.protocol.ChunkDataResponse response =
                    new csx55.dfs.protocol.ChunkDataResponse(filename, fragmentNumber, data);
            connection.sendMessage(response);
        } catch (Exception e) {
            csx55.dfs.protocol.ChunkDataResponse response =
                    new csx55.dfs.protocol.ChunkDataResponse(
                            filename, fragmentNumber, e.getMessage());
            connection.sendMessage(response);
        }
    }

    private void handleReconstructFragmentRequest(
            csx55.dfs.protocol.ReconstructFragmentRequest request,
            csx55.dfs.transport.TCPConnection connection)
            throws Exception {
        String filename = request.getFilename();
        int chunkNumber = request.getChunkNumber();
        int fragmentNumber = request.getFragmentNumber();
        List<String> sourceServers = request.getSourceServers();
        String targetServer = request.getTargetServer();

        try {
            // Retrieve fragments from source servers
            byte[][] fragments = new byte[DFSConfig.TOTAL_SHARDS][];
            boolean[] fragmentsPresent = new boolean[DFSConfig.TOTAL_SHARDS];
            int fragmentSize = 0;
            int retrieved = 0;

            // Try to retrieve fragments from source servers
            for (int i = 0; i < sourceServers.size() && i < DFSConfig.TOTAL_SHARDS; i++) {
                try {
                    String server = sourceServers.get(i);
                    csx55.dfs.transport.TCPConnection.Address addr =
                            csx55.dfs.transport.TCPConnection.Address.parse(server);

                    try (Socket socket = new Socket(addr.host, addr.port);
                            csx55.dfs.transport.TCPConnection fragConnection =
                                    new csx55.dfs.transport.TCPConnection(socket)) {

                        // Request fragment from server
                        String fragmentFilename = filename + "_chunk" + chunkNumber;
                        csx55.dfs.protocol.ReadChunkRequest fragRequest =
                                new csx55.dfs.protocol.ReadChunkRequest(fragmentFilename, i);
                        fragConnection.sendMessage(fragRequest);

                        csx55.dfs.protocol.Message fragResponse = fragConnection.receiveMessage();
                        csx55.dfs.protocol.ChunkDataResponse dataResponse =
                                (csx55.dfs.protocol.ChunkDataResponse) fragResponse;

                        if (dataResponse.isSuccess()) {
                            fragments[i] = dataResponse.getData();
                            fragmentsPresent[i] = true;
                            fragmentSize = fragments[i].length;
                            retrieved++;
                        }
                    }
                } catch (Exception e) {
                    // Fragment not available from this server, continue
                    fragmentsPresent[i] = false;
                }
            }

            if (retrieved < DFSConfig.DATA_SHARDS) {
                throw new Exception(
                        "Not enough fragments retrieved: "
                                + retrieved
                                + "/"
                                + DFSConfig.DATA_SHARDS);
            }

            // Create empty buffers for missing fragments
            for (int i = 0; i < DFSConfig.TOTAL_SHARDS; i++) {
                if (!fragmentsPresent[i]) {
                    fragments[i] = new byte[fragmentSize];
                }
            }

            // Use Reed-Solomon to reconstruct missing fragments
            erasure.ReedSolomon reedSolomon =
                    new erasure.ReedSolomon(DFSConfig.DATA_SHARDS, DFSConfig.PARITY_SHARDS);
            reedSolomon.decodeMissing(fragments, fragmentsPresent, 0, fragmentSize);

            // Send reconstructed fragment to target server
            csx55.dfs.transport.TCPConnection.Address targetAddr =
                    csx55.dfs.transport.TCPConnection.Address.parse(targetServer);
            try (Socket targetSocket = new Socket(targetAddr.host, targetAddr.port);
                    csx55.dfs.transport.TCPConnection targetConnection =
                            new csx55.dfs.transport.TCPConnection(targetSocket)) {

                // Send fragment using chunk protocol
                String fragmentFilename = filename + "_chunk" + chunkNumber;
                csx55.dfs.protocol.StoreChunkRequest storeRequest =
                        new csx55.dfs.protocol.StoreChunkRequest(
                                fragmentFilename,
                                fragmentNumber,
                                fragments[fragmentNumber],
                                new ArrayList<>());

                targetConnection.sendMessage(storeRequest);
                targetConnection.receiveMessage(); // Wait for ack
            }

            // Send success response to Controller
            csx55.dfs.protocol.ReconstructFragmentResponse response =
                    new csx55.dfs.protocol.ReconstructFragmentResponse(true);
            connection.sendMessage(response);

            System.out.println(
                    "Reconstructed "
                            + filename
                            + "_chunk"
                            + chunkNumber
                            + "_shard"
                            + fragmentNumber
                            + " to "
                            + targetServer);

        } catch (Exception e) {
            // Send failure response to Controller
            csx55.dfs.protocol.ReconstructFragmentResponse response =
                    new csx55.dfs.protocol.ReconstructFragmentResponse(false, e.getMessage());
            connection.sendMessage(response);
            System.err.println(
                    "Failed to reconstruct "
                            + filename
                            + "_chunk"
                            + chunkNumber
                            + "_shard"
                            + fragmentNumber
                            + ": "
                            + e.getMessage());
        }
    }

    public void storeFragment(String filename, int chunkNumber, int fragmentNumber, byte[] data)
            throws IOException {
        Path fragmentPath = getFragmentPath(filename, chunkNumber, fragmentNumber);
        Files.createDirectories(fragmentPath.getParent());

        Files.write(fragmentPath, data);

        FragmentMetadata metadata =
                new FragmentMetadata(filename, chunkNumber, fragmentNumber, data.length);
        fragments.put(getFragmentKey(filename, chunkNumber, fragmentNumber), metadata);
        newFragments.add(getFragmentKey(filename, chunkNumber, fragmentNumber));

        System.out.println(
                "Stored fragment: "
                        + filename
                        + "_chunk"
                        + chunkNumber
                        + "_shard"
                        + fragmentNumber);
    }

    public byte[] readFragment(String filename, int chunkNumber, int fragmentNumber)
            throws IOException {
        Path fragmentPath = getFragmentPath(filename, chunkNumber, fragmentNumber);

        if (!Files.exists(fragmentPath)) {
            throw new FileNotFoundException(
                    "Fragment not found: "
                            + filename
                            + "_chunk"
                            + chunkNumber
                            + "_shard"
                            + fragmentNumber);
        }

        return Files.readAllBytes(fragmentPath);
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

    private void sendMajorHeartbeat() throws IOException {
        List<csx55.dfs.protocol.HeartbeatMessage.ChunkInfo> fragmentList = new ArrayList<>();
        for (FragmentMetadata metadata : fragments.values()) {
            fragmentList.add(
                    new csx55.dfs.protocol.HeartbeatMessage.ChunkInfo(
                            metadata.getFilename(),
                            metadata.getChunkNumber(),
                            metadata.getFragmentNumber(),
                            0,
                            0,
                            0,
                            metadata.getDataSize()));
        }

        // Create and send major heartbeat
        csx55.dfs.protocol.HeartbeatMessage heartbeat =
                new csx55.dfs.protocol.HeartbeatMessage(
                        csx55.dfs.protocol.MessageType.MAJOR_HEARTBEAT,
                        serverId,
                        fragments.size(),
                        getFreeSpace(),
                        fragmentList);

        sendHeartbeatToController(heartbeat);
        newFragments.clear();
    }

    private void sendMinorHeartbeat() throws IOException {
        List<csx55.dfs.protocol.HeartbeatMessage.ChunkInfo> fragmentList = new ArrayList<>();
        for (String key : newFragments) {
            FragmentMetadata metadata = fragments.get(key);
            if (metadata != null) {
                fragmentList.add(
                        new csx55.dfs.protocol.HeartbeatMessage.ChunkInfo(
                                metadata.getFilename(),
                                metadata.getChunkNumber(),
                                metadata.getFragmentNumber(),
                                0,
                                0,
                                0,
                                metadata.getDataSize()));
            }
        }

        if (!fragmentList.isEmpty()) {
            csx55.dfs.protocol.HeartbeatMessage heartbeat =
                    new csx55.dfs.protocol.HeartbeatMessage(
                            csx55.dfs.protocol.MessageType.MINOR_HEARTBEAT,
                            serverId,
                            fragments.size(),
                            getFreeSpace(),
                            fragmentList);
            sendHeartbeatToController(heartbeat);
        }
    }

    private void sendHeartbeatToController(csx55.dfs.protocol.HeartbeatMessage heartbeat)
            throws IOException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                csx55.dfs.transport.TCPConnection connection =
                        new csx55.dfs.transport.TCPConnection(socket)) {
            connection.sendMessage(heartbeat);
            connection.receiveMessage(); // Wait for ack
        } catch (Exception e) {
            System.err.println("Error sending heartbeat: " + e.getMessage());
        }
    }

    private long getFreeSpace() {
        try {
            Path path = Paths.get(storageRoot);
            return Files.getFileStore(path).getUsableSpace();
        } catch (IOException e) {
            return 0;
        }
    }

    private Path getFragmentPath(String filename, int chunkNumber, int fragmentNumber) {
        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }
        return Paths.get(
                storageRoot, filename + "_chunk" + chunkNumber + "_shard" + fragmentNumber);
    }

    private String getFragmentKey(String filename, int chunkNumber, int fragmentNumber) {
        return filename + ":" + chunkNumber + ":" + fragmentNumber;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println(
                    "Usage: java csx55.dfs.erasure.ChunkServer <controller-ip> <controller-port>");
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
