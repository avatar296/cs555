package csx55.dfs.erasure;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.base.BaseChunkServer;
import csx55.dfs.protocol.*;
import csx55.dfs.util.DFSConfig;
import csx55.dfs.util.FragmentMetadata;
import csx55.dfs.util.IOUtils;
import csx55.dfs.util.NetworkUtils;

public class ChunkServer extends BaseChunkServer {

    private final Map<String, FragmentMetadata> fragments;
    private final Set<String> newFragments;

    public ChunkServer(String controllerHost, int controllerPort) {
        super(controllerHost, controllerPort);
        this.fragments = new ConcurrentHashMap<>();
        this.newFragments = Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    protected String getServerType() {
        return "Erasure Coding ChunkServer";
    }

    @Override
    protected int getStoredCount() {
        return fragments.size();
    }

    @Override
    protected long calculateUsedSpace() {
        long usedSpace = 0;
        for (FragmentMetadata metadata : fragments.values()) {
            usedSpace += metadata.getDataSize();
        }
        return usedSpace;
    }

    @Override
    protected void handleConnection(Socket socket) {
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

            byte[][] fragments = new byte[DFSConfig.TOTAL_SHARDS][];
            boolean[] fragmentsPresent = new boolean[DFSConfig.TOTAL_SHARDS];
            int fragmentSize = 0;
            int retrieved = 0;

            for (int i = 0; i < sourceServers.size() && i < DFSConfig.TOTAL_SHARDS; i++) {
                try {
                    String server = sourceServers.get(i);
                    csx55.dfs.transport.TCPConnection.Address addr =
                            csx55.dfs.transport.TCPConnection.Address.parse(server);

                    try (Socket socket = new Socket(addr.host, addr.port);
                            csx55.dfs.transport.TCPConnection fragConnection =
                                    new csx55.dfs.transport.TCPConnection(socket)) {

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

            for (int i = 0; i < DFSConfig.TOTAL_SHARDS; i++) {
                if (!fragmentsPresent[i]) {
                    fragments[i] = new byte[fragmentSize];
                }
            }

            erasure.ReedSolomon reedSolomon =
                    new erasure.ReedSolomon(DFSConfig.DATA_SHARDS, DFSConfig.PARITY_SHARDS);
            reedSolomon.decodeMissing(fragments, fragmentsPresent, 0, fragmentSize);

            String fragmentFilename = filename + "_chunk" + chunkNumber;
            StoreChunkRequest storeRequest =
                    new StoreChunkRequest(
                            fragmentFilename,
                            fragmentNumber,
                            fragments[fragmentNumber],
                            new ArrayList<>());

            NetworkUtils.sendRequestToServer(targetServer, storeRequest);

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

        IOUtils.writeWithDirectoryCreation(fragmentPath, data);

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

        return IOUtils.readWithExistenceCheck(
                fragmentPath,
                "Fragment not found: "
                        + filename
                        + "_chunk"
                        + chunkNumber
                        + "_shard"
                        + fragmentNumber);
    }

    @Override
    protected List<csx55.dfs.protocol.HeartbeatMessage.ChunkInfo> buildMajorHeartbeatChunks() {
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
        newFragments.clear();
        return fragmentList;
    }

    @Override
    protected List<csx55.dfs.protocol.HeartbeatMessage.ChunkInfo> buildMinorHeartbeatChunks() {
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
        return fragmentList;
    }

    private Path getFragmentPath(String filename, int chunkNumber, int fragmentNumber) {
        filename = normalizeFilename(filename);
        return getStoragePath(filename + "_chunk" + chunkNumber + "_shard" + fragmentNumber);
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
