/* CS555 Distributed Systems - HW4 */
package csx55.dfs.erasure;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.DFSConfig;
import csx55.dfs.util.ServerInfo;

public class Controller {

    private final int port;
    private ServerSocket serverSocket;

    private final Map<String, ServerInfo> chunkServers;
    private final Map<String, Map<Integer, String>> fragmentLocations;
    private final Map<String, Long> lastHeartbeat;

    private volatile boolean running = true;

    public Controller(int port) {
        this.port = port;
        this.chunkServers = new ConcurrentHashMap<>();
        this.fragmentLocations = new ConcurrentHashMap<>();
        this.lastHeartbeat = new ConcurrentHashMap<>();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Erasure Coding Controller started on port " + port);

        startFailureDetectionThread();

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
                case MAJOR_HEARTBEAT:
                case MINOR_HEARTBEAT:
                    processHeartbeat((HeartbeatMessage) message, connection);
                    break;

                case REQUEST_CHUNK_SERVERS_FOR_WRITE:
                    processChunkServersRequest((ChunkServersRequest) message, connection);
                    break;

                case REQUEST_FILE_INFO:
                    processFileInfoRequest((FileInfoRequest) message, connection);
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

    private void processHeartbeat(HeartbeatMessage heartbeat, TCPConnection connection)
            throws IOException {
        String serverId = heartbeat.getChunkServerId();

        ServerInfo serverInfo = chunkServers.get(serverId);
        if (serverInfo == null) {
            serverInfo = new ServerInfo(serverId);
            chunkServers.put(serverId, serverInfo);
            System.out.println("New chunk server registered: " + serverId);
        }

        serverInfo.freeSpace = heartbeat.getFreeSpace();
        serverInfo.count = heartbeat.getTotalChunks();
        lastHeartbeat.put(serverId, System.currentTimeMillis());

        if (heartbeat.getChunks() != null) {
            for (HeartbeatMessage.ChunkInfo chunk : heartbeat.getChunks()) {
                String key = chunk.filename + ":" + chunk.chunkNumber;
                fragmentLocations
                        .computeIfAbsent(key, k -> new HashMap<>())
                        .put(chunk.fragmentNumber, serverId);
            }
        }

        HeartbeatResponse response = new HeartbeatResponse();
        connection.sendMessage(response);
    }

    private void processChunkServersRequest(ChunkServersRequest request, TCPConnection connection)
            throws IOException {
        List<String> servers =
                selectChunkServersForWrite(request.getFilename(), request.getChunkNumber());
        ChunkServersResponse response = new ChunkServersResponse(servers);
        connection.sendMessage(response);
    }

    private void processFileInfoRequest(FileInfoRequest request, TCPConnection connection)
            throws IOException {
        String filename = request.getFilename();
        int maxChunk = 0;

        for (String key : fragmentLocations.keySet()) {
            if (key.startsWith(filename + ":")) {
                String[] parts = key.split(":");
                int chunkNum = Integer.parseInt(parts[1]);
                maxChunk = Math.max(maxChunk, chunkNum);
            }
        }

        FileInfoResponse response = new FileInfoResponse(maxChunk);
        connection.sendMessage(response);
    }

    public List<String> selectChunkServersForWrite(String filename, int chunkNumber) {
        List<ServerInfo> availableServers = new ArrayList<>(chunkServers.values());

        if (availableServers.size() < DFSConfig.TOTAL_SHARDS) {
            System.err.println(
                    "Not enough chunk servers available. Need "
                            + DFSConfig.TOTAL_SHARDS
                            + ", have "
                            + availableServers.size());
            return new ArrayList<>();
        }

        availableServers.sort((a, b) -> Long.compare(b.freeSpace, a.freeSpace));

        List<String> selected = new ArrayList<>();
        for (int i = 0; i < DFSConfig.TOTAL_SHARDS && i < availableServers.size(); i++) {
            selected.add(availableServers.get(i).serverId);
        }

        System.out.println(
                "Selected chunk servers for "
                        + filename
                        + " chunk "
                        + chunkNumber
                        + ": "
                        + selected);
        return selected;
    }

    public List<String> getFragmentLocationsForRead(String filename, int chunkNumber) {
        String key = filename + ":" + chunkNumber;
        Map<Integer, String> fragmentMap = fragmentLocations.get(key);

        if (fragmentMap == null || fragmentMap.isEmpty()) {
            System.err.println("No fragments found for " + filename + " chunk " + chunkNumber);
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>(Collections.nCopies(DFSConfig.TOTAL_SHARDS, null));
        for (Map.Entry<Integer, String> entry : fragmentMap.entrySet()) {
            int fragmentNumber = entry.getKey();
            String serverId = entry.getValue();
            if (fragmentNumber >= 0 && fragmentNumber < DFSConfig.TOTAL_SHARDS) {
                result.set(fragmentNumber, serverId);
            }
        }

        return result;
    }

    private void startFailureDetectionThread() {
        Thread failureDetector =
                new Thread(
                        () -> {
                            while (running) {
                                try {
                                    Thread.sleep(10000);
                                    detectFailures();
                                } catch (InterruptedException e) {
                                    break;
                                }
                            }
                        });
        failureDetector.setDaemon(true);
        failureDetector.start();
    }

    private void detectFailures() {
        long currentTime = System.currentTimeMillis();
        long failureThreshold = 180000;

        List<String> failedServers = new ArrayList<>();

        for (Map.Entry<String, Long> entry : lastHeartbeat.entrySet()) {
            String serverId = entry.getKey();
            long lastTime = entry.getValue();

            if (currentTime - lastTime > failureThreshold) {
                failedServers.add(serverId);
            }
        }

        for (String failedServerId : failedServers) {
            System.err.println("FAILURE DETECTED: " + failedServerId);
            initiateRecovery(failedServerId);

            chunkServers.remove(failedServerId);
            lastHeartbeat.remove(failedServerId);
        }
    }

    private void initiateRecovery(String failedServerId) {
        System.out.println("Initiating recovery for failed server: " + failedServerId);

        List<String> affectedChunks = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, String>> entry : fragmentLocations.entrySet()) {
            String chunkKey = entry.getKey();
            Map<Integer, String> fragmentMap = entry.getValue();

            for (String serverId : fragmentMap.values()) {
                if (serverId.equals(failedServerId)) {
                    affectedChunks.add(chunkKey);
                    break;
                }
            }
        }

        System.out.println("Found " + affectedChunks.size() + " chunks affected by failure");

        for (String chunkKey : affectedChunks) {
            String[] parts = chunkKey.split(":");
            String filename = parts[0];
            int chunkNumber = Integer.parseInt(parts[1]);

            Map<Integer, String> fragmentMap = fragmentLocations.get(chunkKey);

            Set<Integer> missingFragments = new HashSet<>();
            for (int i = 0; i < DFSConfig.TOTAL_SHARDS; i++) {
                if (!fragmentMap.containsKey(i) || fragmentMap.get(i).equals(failedServerId)) {
                    missingFragments.add(i);
                }
            }

            for (int fragmentNumber : missingFragments) {
                if (fragmentMap.containsKey(fragmentNumber)
                        && fragmentMap.get(fragmentNumber).equals(failedServerId)) {
                    fragmentMap.remove(fragmentNumber);
                }
            }

            int availableFragments = DFSConfig.TOTAL_SHARDS - missingFragments.size();

            if (availableFragments < DFSConfig.DATA_SHARDS) {
                System.err.println(
                        "ERROR: Not enough fragments to recover "
                                + chunkKey
                                + " (have "
                                + availableFragments
                                + ", need "
                                + DFSConfig.DATA_SHARDS
                                + ")");
                continue;
            }

            System.out.println(
                    "Recovering " + missingFragments.size() + " missing fragments for " + chunkKey);

            Set<String> sourceServers = new HashSet<>();
            for (Map.Entry<Integer, String> fragEntry : fragmentMap.entrySet()) {
                sourceServers.add(fragEntry.getValue());
            }

            List<String> sourceServerList = new ArrayList<>(sourceServers);

            if (sourceServerList.isEmpty()) {
                System.err.println("ERROR: No source servers available for " + chunkKey);
                continue;
            }

            for (int missingFragmentNumber : missingFragments) {
                String targetServer = selectNewServerForFragment(fragmentMap.values());

                if (targetServer == null) {
                    System.err.println(
                            "ERROR: Cannot find new server for fragment "
                                    + missingFragmentNumber
                                    + " of "
                                    + chunkKey);
                    continue;
                }

                String coordinatorServer = sourceServerList.get(0);

                try {
                    TCPConnection.Address addr = TCPConnection.Address.parse(coordinatorServer);
                    try (Socket socket = new Socket(addr.host, addr.port);
                            TCPConnection connection = new TCPConnection(socket)) {

                        ReconstructFragmentRequest request =
                                new ReconstructFragmentRequest(
                                        filename,
                                        chunkNumber,
                                        missingFragmentNumber,
                                        sourceServerList,
                                        targetServer);
                        connection.sendMessage(request);

                        Message response = connection.receiveMessage();
                        ReconstructFragmentResponse reconstructResponse =
                                (ReconstructFragmentResponse) response;

                        if (reconstructResponse.isSuccess()) {
                            fragmentMap.put(missingFragmentNumber, targetServer);
                            System.out.println(
                                    "Successfully reconstructed fragment "
                                            + missingFragmentNumber
                                            + " of "
                                            + chunkKey
                                            + " to "
                                            + targetServer);
                        } else {
                            System.err.println(
                                    "Failed to reconstruct fragment "
                                            + missingFragmentNumber
                                            + " of "
                                            + chunkKey
                                            + ": "
                                            + reconstructResponse.getErrorMessage());
                        }
                    }
                } catch (Exception e) {
                    System.err.println(
                            "Error reconstructing fragment "
                                    + missingFragmentNumber
                                    + " of "
                                    + chunkKey
                                    + ": "
                                    + e.getMessage());
                }
            }
        }
    }

    private String selectNewServerForFragment(Collection<String> existingServers) {
        List<ServerInfo> availableServers = new ArrayList<>(chunkServers.values());

        availableServers.removeIf(server -> existingServers.contains(server.serverId));

        if (availableServers.isEmpty()) {
            return null;
        }

        availableServers.sort((a, b) -> Long.compare(b.freeSpace, a.freeSpace));

        return availableServers.get(0).serverId;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java csx55.dfs.erasure.Controller <port>");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[0]);
            Controller controller = new Controller(port);
            controller.start();
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid integer");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error starting controller: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
